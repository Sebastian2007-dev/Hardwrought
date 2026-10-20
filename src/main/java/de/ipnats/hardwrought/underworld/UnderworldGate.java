package de.ipnats.hardwrought.underworld;

import com.mojang.serialization.MapCodec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The way into the Underworld and out of it (see docs/underworld.md).
 *
 * <p><b>Down:</b> in the abyss, below {@link #ABYSS_BELOW}, the <b>seal stone</b> with four deepslate pillars
 * at its corners, two blocks out, each with a soul lantern on it. A nether star set into the stone breaks
 * the ground open: the floor cracks ring by ring around it, glowing soul-blue, the rock groans, and a
 * <b>shaft</b> of darkness opens where the stone was. It stays open, for everyone. Stepping into it is
 * the way down: dark, souls rushing up past, and the Underworld.
 *
 * <p><b>Up:</b> the <b>ascent shafts</b> in the Underworld's ruins, gone dim. A pale shard rekindles one,
 * for good; standing in its light for {@link #ASCENT_TICKS} carries a player up to the shaft they came
 * down through.
 */
public final class UnderworldGate {
    /** The abyss: the seal only takes below this height in the overworld. */
    public static final int ABYSS_BELOW = -180;
    /** How long the opening takes, and how far the cracks spread. */
    static final int OPENING_TICKS = 100;
    static final int CRACK_RADIUS = 4;
    /** How long a player stands in an ascent shaft's light before it carries them up. */
    public static final int ASCENT_TICKS = 40;

    /** How far below the floor the shaft lies, at the bottom of the pit the seal leaves. */
    public static final int PIT_DEPTH = 3;
    /** How often the shaft swallows the water that has run into the pit. */
    static final int SWALLOW_EVERY = 10;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public static final Block SEAL_STONE = register("seal_stone", SealStone::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_DEEPSLATE).strength(6.0f, 1200f).lightLevel(state -> 3));
    public static final Block SEAL_CRACK = register("seal_crack", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).lightLevel(state -> 7).mapColor(MapColor.COLOR_CYAN));
    public static final Block SHAFT = register("underworld_shaft", Shaft::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollision().strength(-1.0f, 3600000.0f)
                    .noLootTable().pushReaction(PushReaction.IMMOVEABLE).lightLevel(state -> 1));
    public static final Block ASCENT_SHAFT = register("ascent_shaft", AscentShaft::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_TILES).strength(-1.0f, 3600000.0f).noLootTable()
                    .lightLevel(state -> state.getValue(LIT) ? 12 : 0));

    /** What the client draws the view down into the shaft for. */
    public static final net.minecraft.world.level.block.entity.BlockEntityType<ShaftEntity> SHAFT_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("underworld_shaft"),
            new net.minecraft.world.level.block.entity.BlockEntityType<>(ShaftEntity::new, Set.of(SHAFT)));

    /** The shaft a player came down through, to go back up to. */
    public static final AttachmentType<BlockPos> DESCENT = AttachmentRegistry.create(Hardwrought.id("underworld_descent"),
            builder -> builder.persistent(BlockPos.CODEC).copyOnDeath());
    /** Where in the Underworld a player went up from, to come back down to. */
    public static final AttachmentType<BlockPos> RETURN = AttachmentRegistry.create(Hardwrought.id("underworld_return"),
            builder -> builder.persistent(BlockPos.CODEC).copyOnDeath());

    static final MapCodec<AscentRuin> ASCENT_RUIN_TYPE = Registry.register(BuiltInRegistries.FEATURE_TYPE,
            Hardwrought.id("ascent_ruin"), MapCodec.unit(AscentRuin.INSTANCE));

    private record Pending(long at, Runnable action) { }

    private static final List<Pending> PENDING = new ArrayList<>();
    /** How long each player has stood on a lit ascent shaft without stepping off. */
    private static final java.util.Map<java.util.UUID, Integer> STANDING = new java.util.HashMap<>();

    private UnderworldGate() { }

    public static void initialize() {
        de.ipnats.hardwrought.knowledge.LootSources.inWorld(() -> ASCENT_SHAFT.asItem());
        for (Block block : List.of(SEAL_STONE, ASCENT_SHAFT)) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
            BlockItem item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
            item.registerBlocks(Item.BY_BLOCK, item);
            Registry.register(BuiltInRegistries.ITEM, key, item);
        }
        ServerTickEvents.END_SERVER_TICK.register(UnderworldGate::runPending);
        ServerTickEvents.END_SERVER_TICK.register(UnderworldGate::ascents);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDING.clear();
            STANDING.clear();
        });
    }

    public static List<Item> items() {
        return List.of(SEAL_STONE.asItem(), ASCENT_SHAFT.asItem());
    }

    // ------------------------------------------------------------------------------------ the seal

    /** What is still wanting for the seal at this stone, or null when it would take. */
    public static Component wanting(Level level, BlockPos stone) {
        if (level.dimension() != Level.OVERWORLD || stone.getY() > ABYSS_BELOW) {
            return Component.translatable("message.hardwrought.seal.not_abyss", ABYSS_BELOW);
        }
        for (int dx : new int[] {-2, 2}) {
            for (int dz : new int[] {-2, 2}) {
                BlockPos pillar = stone.offset(dx, 0, dz);
                if (!pillar(level.getBlockState(pillar))) return Component.translatable("message.hardwrought.seal.pillar");
                if (!level.getBlockState(pillar.above()).is(Blocks.SOUL_LANTERN)) {
                    return Component.translatable("message.hardwrought.seal.lantern");
                }
            }
        }
        return null;
    }

    /** A pillar of the seal: any worked deepslate. */
    static boolean pillar(BlockState state) {
        return state.is(Blocks.CHISELED_DEEPSLATE) || state.is(Blocks.POLISHED_DEEPSLATE) || state.is(Blocks.DEEPSLATE_BRICKS)
                || state.is(Blocks.DEEPSLATE_TILES) || state.is(Blocks.CRACKED_DEEPSLATE_BRICKS)
                || state.is(Blocks.CRACKED_DEEPSLATE_TILES);
    }

    /** The nether star burns out, and the ground opens over {@link #OPENING_TICKS}. */
    public static void open(ServerLevel level, BlockPos stone) {
        MinecraftServer server = level.getServer();
        long now = server.getTickCount();
        Vec3 center = Vec3.atCenterOf(stone);
        level.playSound(null, stone, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0f, 0.4f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y + 0.6, center.z, 40, 0.3, 0.3, 0.3, 0.05);
        for (int ring = 1; ring <= CRACK_RADIUS; ring++) {
            final int r = ring;
            PENDING.add(new Pending(now + ring * (OPENING_TICKS / (CRACK_RADIUS + 1)), () -> crack(level, stone, r)));
        }
        for (int t = 10; t < OPENING_TICKS; t += 10) {
            final int tick = t;
            PENDING.add(new Pending(now + t, () -> {
                level.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 12 + tick / 5, 2.5, 0.3, 2.5, 0.03);
                level.playSound(null, stone, tick % 20 == 0 ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.SCULK_SHRIEKER_SHRIEK,
                        SoundSource.BLOCKS, 1.5f, 0.5f);
            }));
        }
        PENDING.add(new Pending(now + OPENING_TICKS, () -> {
            // The ground falls in: a pit three wide, walled with the cracked seal, the shaft at its bottom.
            for (int dy = 0; dy >= -PIT_DEPTH; dy--) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = stone.offset(dx, dy, dz);
                        boolean inside = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                        if (inside) level.setBlockAndUpdate(pos, dy == -PIT_DEPTH ? SHAFT.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        else if (dy < 0) level.setBlockAndUpdate(pos, SEAL_CRACK.defaultBlockState());
                    }
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) level.setBlockAndUpdate(stone.offset(dx, -PIT_DEPTH - 1, dz), Blocks.BEDROCK.defaultBlockState());
            }
            level.playSound(null, stone, SoundEvents.WARDEN_EMERGE, SoundSource.BLOCKS, 3.0f, 0.5f);
            level.playSound(null, stone, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 2.0f, 0.5f);
            level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 0.5, center.z, 80, 1.5, 0.5, 1.5, 0.08);
        }));
    }

    /** One ring of the floor around the seal breaks into glowing cracks. */
    private static void crack(ServerLevel level, BlockPos stone, int ring) {
        RandomSource random = level.getRandom();
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                // Not every block of the outer rings: cracks, not a pavement.
                if (ring > 2 && random.nextInt(ring) > 1) continue;
                BlockPos floor = stone.offset(dx, -1, dz);
                BlockState there = level.getBlockState(floor);
                if (there.isAir() || !there.getFluidState().isEmpty()) continue;
                level.setBlockAndUpdate(floor, SEAL_CRACK.defaultBlockState());
                level.sendParticles(ParticleTypes.SCULK_SOUL, floor.getX() + 0.5, floor.getY() + 1.05, floor.getZ() + 0.5, 3, 0.3, 0, 0.3, 0.02);
            }
        }
        level.playSound(null, stone, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.0f, 0.4f);
        level.playSound(null, stone, SoundEvents.AMBIENT_CAVE.value(), SoundSource.BLOCKS, 2.0f, 0.5f);
    }

    // ------------------------------------------------------------------------------------ travelling

    /** Down the shaft: dark, souls rushing up, and the Underworld. */
    static void descend(ServerPlayer player, BlockPos shaft) {
        ServerLevel underworld = player.level().getServer().getLevel(Underworld.LEVEL);
        if (underworld == null) return;
        player.setAttached(DESCENT, shaft);
        // Back to where they last went up, beside that ascent shaft rather than in its light; the first time,
        // under the seal.
        BlockPos back = player.getAttached(RETURN);
        BlockPos near = back != null ? nearest(underworld, back, 6) : null;
        BlockPos landing = near != null ? near : ground(underworld, back != null ? back : new BlockPos(shaft.getX(), 60, shaft.getZ()), 40);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
        player.teleportTo(underworld, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, Set.of(), player.getYRot(),
                player.getXRot(), true);
        underworld.sendParticles(ParticleTypes.SOUL, landing.getX() + 0.5, landing.getY() + 1, landing.getZ() + 0.5, 60, 0.6, 2.0, 0.6, 0.15);
        underworld.playSound(null, landing, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.5f);
        underworld.playSound(null, landing, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0f, 0.6f);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 60, 30));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("title.hardwrought.underworld").withStyle(ChatFormatting.DARK_GRAY)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("subtitle.hardwrought.underworld").withStyle(ChatFormatting.GRAY)));
    }

    /** Up the ascent shaft, to beside the shaft they came down through, or straight up into the abyss. */
    static void ascend(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        player.setAttached(RETURN, player.blockPosition());
        BlockPos descent = player.getAttached(DESCENT);
        BlockPos around = descent != null ? descent : new BlockPos(player.getBlockX(), ABYSS_BELOW - 20, player.getBlockZ());
        BlockPos landing = ground(overworld, around.offset(3, 0, 0), 12);
        player.teleportTo(overworld, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, Set.of(), player.getYRot(),
                player.getXRot(), true);
        overworld.sendParticles(ParticleTypes.END_ROD, landing.getX() + 0.5, landing.getY() + 1, landing.getZ() + 0.5, 40, 0.4, 1.5, 0.4, 0.05);
        overworld.playSound(null, landing, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
    }

    /** The nearest block a player can stand in near a position, searching up and down; carved out if there is none. */
    public static BlockPos ground(ServerLevel level, BlockPos around, int radius) {
        level.getChunk(SectionPos.blockToSectionCoord(around.getX()), SectionPos.blockToSectionCoord(around.getZ()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 0; dy < 80; dy++) {
                        for (int sign : new int[] {-1, 1}) {
                            int y = around.getY() + dy * sign;
                            if (y <= level.getMinY() + 6 || y >= level.getMaxY() - 6) continue;
                            pos.set(around.getX() + dx, y, around.getZ() + dz);
                            if (standable(level, pos)) return pos.immutable();
                        }
                    }
                }
            }
        }
        // Nowhere to stand: a pocket cut into the rock.
        BlockPos pocket = around.atY(Math.clamp(around.getY(), level.getMinY() + 8, level.getMaxY() - 8));
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) level.setBlockAndUpdate(pocket.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(pocket.below(), Blocks.DEEPSLATE.defaultBlockState());
        return pocket;
    }

    /** The closest block a player can stand in, nearest first in every direction alike, or null within the radius. */
    public static BlockPos nearest(ServerLevel level, BlockPos around, int radius) {
        level.getChunk(SectionPos.blockToSectionCoord(around.getX()), SectionPos.blockToSectionCoord(around.getZ()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= radius; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    for (int dz = -d; dz <= d; dz++) {
                        if (Math.max(Math.abs(dy), Math.max(Math.abs(dx), Math.abs(dz))) != d) continue;
                        pos.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                        if (standable(level, pos)) return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    static boolean standable(Level level, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below());
        return floor.isFaceSturdy(level, feet.below(), Direction.UP) && floor.getFluidState().isEmpty()
                && !floor.is(Blocks.MAGMA_BLOCK) && !floor.is(SHAFT) && !(floor.is(ASCENT_SHAFT) && floor.getValue(LIT))
                && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

    /**
     * Standing in an ascent shaft's light: the longer, the more of it gathers round them, until it
     * carries them up. Stepping off starts it over.
     */
    private static void ascents(MinecraftServer server) {
        ServerLevel underworld = server.getLevel(Underworld.LEVEL);
        if (underworld == null) return;
        for (ServerPlayer player : List.copyOf(underworld.players())) {
            BlockState below = underworld.getBlockState(player.blockPosition().below());
            if (player.isSpectator() || !player.onGround() || !below.is(ASCENT_SHAFT) || !below.getValue(LIT)) {
                STANDING.remove(player.getUUID());
                continue;
            }
            int stood = STANDING.merge(player.getUUID(), 1, Integer::sum);
            if (stood % 4 == 0) {
                underworld.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(),
                        1 + stood / 8, 0.3, 0.6, 0.3, 0.04);
            }
            if (stood == ASCENT_TICKS / 2) {
                underworld.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.6f);
            }
            if (stood >= ASCENT_TICKS) {
                STANDING.remove(player.getUUID());
                ascend(player);
            }
        }
    }

    private static void runPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.at() <= server.getTickCount()) {
                due.add(pending.action());
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
                                                BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    // ------------------------------------------------------------------------------------ the blocks

    /** Takes a nether star, if the seal around it is whole and it stands in the abyss. */
    public static class SealStone extends Block {
        public SealStone(Properties properties) {
            super(properties);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
            if (!stack.is(Items.NETHER_STAR)) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            Component wanting = wanting(level, pos);
            if (wanting != null) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(wanting.copy().withStyle(ChatFormatting.DARK_AQUA), true);
                }
                server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.01);
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            open(server, pos);
            return InteractionResult.SUCCESS;
        }
    }

    /** The open shaft: whatever player steps into it goes down. */
    public static class Shaft extends net.minecraft.world.level.block.BaseEntityBlock {
        public Shaft(Properties properties) {
            super(properties);
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ShaftEntity(pos, state);
        }

        @Override
        protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
            return net.minecraft.world.level.block.RenderShape.INVISIBLE;
        }

        @Override
        public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
                Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
            return level.isClientSide() ? null : createTickerHelper(type, SHAFT_ENTITY, (l, pos, s, shaft) -> swallow((ServerLevel) l, pos));
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.block();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects,
                                    boolean intersects) {
            if (level instanceof ServerLevel && entity instanceof ServerPlayer player && !player.isSpectator()
                    && player.getPortalCooldown() == 0) {
                player.setPortalCooldown(100);
                descend(player, pos);
            }
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SOUL, pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(),
                        0, 0.06 + random.nextDouble() * 0.05, 0);
            }
            if (random.nextInt(80) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.AMBIENT_CAVE.value(),
                        SoundSource.BLOCKS, 0.4f, 0.5f, false);
            }
        }
    }

    /**
     * Water that runs into the pit pours down the shaft and is gone: groundwater seeping into the abyss
     * would otherwise fill the pit and drown the way down. Twice a second, the column over each shaft
     * block is emptied, with a splash and the sound of it falling away.
     */
    static void swallow(ServerLevel level, BlockPos shaft) {
        if ((level.getGameTime() + shaft.asLong()) % SWALLOW_EVERY != 0) return;
        boolean any = false;
        for (int up = 1; up <= PIT_DEPTH + 1; up++) {
            BlockPos pos = shaft.above(up);
            int amount = de.ipnats.hardwrought.water.WaterStorage.amount(level, pos);
            if (amount <= 0) continue;
            de.ipnats.hardwrought.water.WaterStorage.setAmount(level, pos, 0);
            de.ipnats.hardwrought.water.WaterFlow.disturb(level, pos);
            any = true;
        }
        if (!any) return;
        level.sendParticles(ParticleTypes.SPLASH, shaft.getX() + 0.5, shaft.getY() + 1.05, shaft.getZ() + 0.5, 12, 0.35, 0.05, 0.35, 0.1);
        level.sendParticles(ParticleTypes.SOUL, shaft.getX() + 0.5, shaft.getY() + 1.1, shaft.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.02);
        if (level.getRandom().nextInt(4) == 0) {
            level.playSound(null, shaft, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.6f, 0.5f);
        }
    }

    /** The shaft's floor: holds nothing, there only so the client draws the view down into it. */
    public static class ShaftEntity extends net.minecraft.world.level.block.entity.BlockEntity {
        public ShaftEntity(BlockPos pos, BlockState state) {
            super(SHAFT_ENTITY, pos, state);
        }
    }

    /**
     * An ascent shaft: dim until a pale shard rekindles it, and then for good. Lit, a column of pale light
     * rises from it; a player standing in it long enough is carried up.
     */
    public static class AscentShaft extends Block {
        public AscentShaft(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
            if (state.getValue(LIT) || !stack.is(Underworld.PALE_SHARD)) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            server.setBlockAndUpdate(pos, state.setValue(LIT, true));
            server.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5f, 1.4f);
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.2, 2.0, 0.2, 0.05);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(LIT)) {
                // Near a dim shaft: whispers, at the edge of hearing, out of the rock round it.
                if (random.nextInt(140) == 0) {
                    level.playLocalSound(pos.getX() + 0.5 + random.nextInt(9) - 4, pos.getY() + 1.5, pos.getZ() + 0.5 + random.nextInt(9) - 4,
                            SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), SoundSource.AMBIENT, 0.3f, 0.6f + random.nextFloat() * 0.2f, false);
                }
                return;
            }
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                        pos.getY() + 1.0 + random.nextDouble() * 4.0, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.08, 0);
            }
        }
    }


    // ------------------------------------------------------------------------------------ the ruins

    /**
     * A ruin with an ascent shaft: a broken ring of deepslate bricks on a cave floor, the dim shaft in its
     * middle. Rare — the way home has to be found.
     */
    record AscentRuin() implements Feature {
        static final AscentRuin INSTANCE = new AscentRuin();

        @Override
        public MapCodec<AscentRuin> codec() {
            return ASCENT_RUIN_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4, z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int attempt = 0; attempt < 24; attempt++) {
                pos.set(x0 + 4 + random.nextInt(8), level.getMinY() + 16 + random.nextInt(level.getHeight() - 48), z0 + 4 + random.nextInt(8));
                if (!floor(level, pos)) continue;
                build(level, pos.immutable(), random);
                return true;
            }
            return false;
        }

        /** Open space over solid floor, wide enough for the ring. */
        private static boolean floor(WorldGenLevel level, BlockPos center) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos at = center.offset(dx, 0, dz);
                    if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) return false;
                    if (!level.getBlockState(at.below()).isSolidRender()) return false;
                }
            }
            return true;
        }

        private static void build(WorldGenLevel level, BlockPos center, RandomSource random) {
            BlockState[] stones = {Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState()};
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dz));
                    BlockPos at = center.offset(dx, 0, dz);
                    // The floor of the ruin, worn through in places.
                    if (distance <= 2 && random.nextInt(5) > 0) {
                        level.setBlock(at.below(), stones[2 + random.nextInt(2)], Block.UPDATE_CLIENTS);
                    }
                    // Its wall, broken: from nothing to three high.
                    if (distance == 3) {
                        int high = random.nextInt(4);
                        for (int y = 0; y < high; y++) level.setBlock(at.above(y), stones[random.nextInt(2)], Block.UPDATE_CLIENTS);
                    }
                }
            }
            level.setBlock(center.below(), ASCENT_SHAFT.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (random.nextInt(3) == 0) level.setBlock(center.offset(2, 0, 2), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
