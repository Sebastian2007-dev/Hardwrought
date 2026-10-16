package de.ipnats.hardwrought.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModEffects;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Crossing into the Nether and back (see docs/endgame.md).
 *
 * <ul>
 *   <li><b>An unstable portal</b> ({@link PortalStability}) takes the traveller through, but what they
 *       carry stays behind on the side they left: it is kept for them, and given back when they come
 *       back to that side. And it opens no portal on the other side. A traveller lands on the nearest safe ground instead, and anything else that is
 *       not a player does not go through at all — otherwise a thrown stick would open the way home.
 *   <li><b>Arriving in the Nether</b> sets the spawn point there, so dying does not send a player home
 *       to their bed and chests. The spawn point they had is kept, and given back when they come home
 *       through a portal.
 *   <li><b>Heat resistance</b> on arriving, and after every respawn in the Nether, so a player does not
 *       overheat on the spot and has time to get their bearings.
 * </ul>
 */
public final class NetherCrossing {
    /** How long the heat resistance on arriving or respawning lasts: fifteen minutes. */
    public static final int HEAT_RESISTANCE_TICKS = 15 * 60 * 20;
    /** How far from where the portal would have been an unstable crossing looks for ground. */
    static final int LANDING_RADIUS = 24;
    /** Below the Nether's bedrock roof. */
    static final int CEILING_MAX_Y = 120;

    /** The spawn point a player had before the Nether — none at all being the world spawn. */
    public record HomeSpawn(Optional<ServerPlayer.RespawnConfig> config) {
        public static final Codec<HomeSpawn> CODEC = ServerPlayer.RespawnConfig.CODEC.optionalFieldOf("config")
                .codec().xmap(HomeSpawn::new, HomeSpawn::config);
    }

    public static final AttachmentType<HomeSpawn> HOME_SPAWN = AttachmentRegistry.create(Hardwrought.id("home_spawn"),
            builder -> builder.persistent(HomeSpawn.CODEC).copyOnDeath());

    /** One stack left behind, and the inventory slot it came out of — armour goes back on. */
    public record KeptStack(int slot, ItemStack stack) {
        public static final Codec<KeptStack> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(KeptStack::slot),
                ItemStack.CODEC.fieldOf("stack").forGetter(KeptStack::stack)
        ).apply(i, KeptStack::new));
    }

    /** What a player left behind going through an unstable portal, by the dimension they left it in. */
    public static final AttachmentType<Map<ResourceKey<Level>, List<KeptStack>>> LEFT_BEHIND = AttachmentRegistry.create(
            Hardwrought.id("left_behind"),
            builder -> builder.persistent(Codec.unboundedMap(Level.RESOURCE_KEY_CODEC, KeptStack.CODEC.listOf())).copyOnDeath());

    private NetherCrossing() { }

    public static void initialize() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && newPlayer.level().dimension() == Level.NETHER) heatResistance(newPlayer);
        });
    }

    /**
     * Where an unstable crossing leads, when no portal is waiting on the other side: a traveller to
     * safe ground near where the portal would have formed, anything else nowhere.
     */
    public static TeleportTransition unstableLanding(ServerLevel newLevel, Entity entity, BlockPos approximateExit) {
        if (!(entity instanceof ServerPlayer)) return null;
        // Into the Nether, the traveller is set down in an Ash Grove if there is one within reach:
        // a start over with nothing needs water and wood before anything else.
        BlockPos around = approximateExit;
        if (newLevel.dimension() == Level.NETHER) {
            BlockPos grove = AshGrove.nearest(newLevel, approximateExit);
            if (grove != null) around = grove;
        }
        BlockPos ground = findGround(newLevel, around, LANDING_RADIUS);
        if (ground == null) ground = makeGround(newLevel, approximateExit);
        return new TeleportTransition(newLevel, Vec3.atBottomCenterOf(ground), Vec3.ZERO, entity.getYRot(), 0,
                TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
    }

    /**
     * A player is about to go through a portal: through an unstable one, everything they carry is left
     * lying beside it. Their arrival is then seen to, whichever kind it was.
     */
    public static TeleportTransition crossing(ServerLevel from, ServerPlayer player, BlockPos portal, TeleportTransition to) {
        boolean stable = PortalStability.isStable(from, portal);
        if (!stable && !player.isCreative() && !player.isSpectator()) leaveBehind(from, player, portal);
        ResourceKey<Level> left = from.dimension();
        return new TeleportTransition(to.newLevel(), to.position(), to.deltaMovement(), to.yRot(), to.xRot(),
                to.missingRespawnBlock(), to.asPassenger(), to.relatives(),
                to.postTeleportTransition().then(entity -> {
                    if (entity instanceof ServerPlayer arrived) arrive(arrived, left, stable);
                }));
    }

    /** What goes through an unstable portal all the same: a backpack, with what is in it, and the gloves. */
    public static final TagKey<Item> KEPT = TagKey.create(Registries.ITEM, Hardwrought.id("kept_through_unstable_portals"));

    /**
     * Everything this player carries, armour included, left behind in {@code level}: kept for them
     * until they come back to it (see {@link #takeBack}). Only what is tagged {@link #KEPT} comes along.
     */
    public static void leaveBehind(ServerLevel level, ServerPlayer player, BlockPos portal) {
        Inventory inventory = player.getInventory();
        List<KeptStack> kept = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty() || inventory.getItem(slot).is(KEPT)) continue;
            kept.add(new KeptStack(slot, inventory.removeItemNoUpdate(slot)));
        }
        if (kept.isEmpty()) return;
        Map<ResourceKey<Level>, List<KeptStack>> all = new HashMap<>(player.getAttachedOrElse(LEFT_BEHIND, Map.of()));
        List<KeptStack> there = new ArrayList<>(all.getOrDefault(level.dimension(), List.of()));
        there.addAll(kept);
        all.put(level.dimension(), there);
        player.setAttached(LEFT_BEHIND, all);
        player.containerMenu.broadcastChanges();
        // Seen to stay behind: torn off at the portal's edge in smoke.
        Vec3 at = player.position();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.8, at.z, 25, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 40, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    /**
     * What this player left behind in the dimension they are now in, given back: each stack into the
     * slot it came from if that is free, else wherever there is room, else at their feet.
     *
     * @return whether there was anything to give back
     */
    public static boolean takeBack(ServerPlayer player) {
        Map<ResourceKey<Level>, List<KeptStack>> all = player.getAttached(LEFT_BEHIND);
        ResourceKey<Level> here = player.level().dimension();
        if (all == null || !all.containsKey(here)) return false;
        Inventory inventory = player.getInventory();
        for (KeptStack kept : all.get(here)) {
            ItemStack stack = kept.stack().copy();
            if (kept.slot() < inventory.getContainerSize() && inventory.getItem(kept.slot()).isEmpty()) {
                inventory.setItem(kept.slot(), stack);
            } else if (!inventory.add(stack)) {
                player.spawnAtLocation(player.level(), stack);
            }
        }
        Map<ResourceKey<Level>, List<KeptStack>> rest = new HashMap<>(all);
        rest.remove(here);
        if (rest.isEmpty()) player.removeAttached(LEFT_BEHIND);
        else player.setAttached(LEFT_BEHIND, rest);
        player.containerMenu.broadcastChanges();
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        return true;
    }

    private static void arrive(ServerPlayer player, ResourceKey<Level> left, boolean stable) {
        ServerLevel level = player.level();
        if (level.dimension() == Level.NETHER && left != Level.NETHER) {
            ServerPlayer.RespawnConfig current = player.getRespawnConfig();
            boolean alreadyHere = current != null && current.respawnData().dimension() == Level.NETHER;
            if (!alreadyHere) player.setAttached(HOME_SPAWN, new HomeSpawn(Optional.ofNullable(current)));
            BlockPos spot = findGround(level, player.blockPosition(), 8);
            if (spot == null) spot = player.blockPosition();
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                    LevelData.RespawnData.of(Level.NETHER, spot, player.getYRot(), 0), true), true);
            heatResistance(player);
        } else if (left == Level.NETHER && level.dimension() != Level.NETHER) {
            HomeSpawn home = player.getAttached(HOME_SPAWN);
            if (home != null) {
                player.setRespawnPosition(home.config().orElse(null), false);
                player.removeAttached(HOME_SPAWN);
            }
        }
        if (!stable && !player.isCreative() && !player.isSpectator()) {
            // Big and in the middle of the screen, long enough to read; the whole of it stays in the chat.
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("message.hardwrought.portal.unstable.title").withStyle(ChatFormatting.LIGHT_PURPLE)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("message.hardwrought.portal.unstable.subtitle").withStyle(ChatFormatting.GRAY)));
            player.sendSystemMessage(Component.translatable("message.hardwrought.portal.unstable")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (stable && !player.isCreative() && !player.isSpectator()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("message.hardwrought.portal.stable.title").withStyle(ChatFormatting.AQUA)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("message.hardwrought.portal.stable.subtitle").withStyle(ChatFormatting.GRAY)));
        }
        if (takeBack(player)) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.portal.taken_back")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    public static void heatResistance(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.HEAT_RESISTANCE, HEAT_RESISTANCE_TICKS, 0, false, false, true));
    }

    /**
     * The nearest block a player can stand in near {@code around}: solid, safe ground under it and room
     * for their head. Under a roof, as in the Nether, each column is searched up and down from the
     * height asked for; under the open sky, the surface is taken.
     */
    public static BlockPos findGround(ServerLevel level, BlockPos around, int radius) {
        boolean roofed = level.dimensionType().hasCeiling();
        int minY = level.getMinY() + 5;
        int maxY = roofed ? CEILING_MAX_Y : level.getMaxY() - 2;
        int startY = Math.clamp(around.getY(), minY, maxY);
        for (int ring = 0; ring <= radius; ring++) {
            BlockPos best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = around.getX() + dx, z = around.getZ() + dz;
                    BlockPos found = roofed ? searchColumn(level, x, z, startY, minY, maxY) : surface(level, x, z);
                    if (found == null) continue;
                    int distance = dx * dx + dz * dz + (found.getY() - startY) * (found.getY() - startY);
                    if (distance < bestDistance) {
                        best = found;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static BlockPos searchColumn(ServerLevel level, int x, int z, int startY, int minY, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int offset = 0; offset <= maxY - minY; offset++) {
            for (int y : new int[] {startY - offset, startY + offset}) {
                if (y < minY || y > maxY) continue;
                pos.set(x, y, z);
                if (standable(level, pos)) return pos.immutable();
                if (offset == 0) break;
            }
        }
        return null;
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        BlockPos pos = new BlockPos(x, height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        return standable(level, pos) ? pos : null;
    }

    /**
     * The surface height of a column, its chunk generated first: a chunk nobody has been to yet has an
     * empty heightmap, and would answer the bottom of the world — a traveller set down on bedrock.
     */
    private static int height(ServerLevel level, Heightmap.Types type, int x, int z) {
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return level.getHeight(type, x, z);
    }

    /** Room for a player in this block and the one above, on safe solid ground. */
    public static boolean standable(Level level, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP) || !floor.getFluidState().isEmpty()
                || floor.is(Blocks.MAGMA_BLOCK) || floor.is(BlockTags.FIRE)) {
            return false;
        }
        return free(level.getBlockState(feet)) && free(level.getBlockState(feet.above()));
    }

    private static boolean free(BlockState state) {
        if (state.is(Blocks.NETHER_PORTAL) || state.is(BlockTags.FIRE) || !state.getFluidState().isEmpty()) return false;
        return state.isAir() || state.canBeReplaced();
    }

    /** Nowhere safe within reach — over a lava sea, say: a small floor of netherrack, as vanilla's portals build one of obsidian. */
    private static BlockPos makeGround(ServerLevel level, BlockPos around) {
        int y = level.dimensionType().hasCeiling()
                ? Math.clamp(around.getY(), 40, CEILING_MAX_Y - 10)
                : height(level, Heightmap.Types.MOTION_BLOCKING, around.getX(), around.getZ());
        BlockPos feet = new BlockPos(around.getX(), y, around.getZ());
        BlockState floor = level.dimension() == Level.NETHER ? Blocks.NETHERRACK.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(feet.offset(dx, -1, dz), floor);
                for (int dy = 0; dy <= 2; dy++) level.setBlockAndUpdate(feet.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        }
        return feet;
    }
}
