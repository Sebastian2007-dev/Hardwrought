package de.ipnats.hardwrought.ritual;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import de.ipnats.hardwrought.mixin.ServerGamePacketListenerAccessor;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The ritual at the end of the game (endgame § 5): begun by the dragon's heart laid on the altar of a
 * standing temple with every offering on its pedestal, and about a minute long.
 *
 * <ol>
 *   <li>Night falls over the temple. One by one the pedestals kindle, and a beam of light runs from each
 *   to the altar.</li>
 *   <li>The heart rises over the altar; rings of light rise round it, one after another.</li>
 *   <li>A pillar of light goes up through the open roof into the sky; lightning strikes round the temple;
 *   light streams in to the altar from all round.</li>
 *   <li>The offerings burn up in light. Silence, and a heartbeat, quickening.</li>
 *   <li>Then everything at once: a wave of light over the land, fireworks, dawn.</li>
 *   <li>Whoever was in the ritual chamber rises from it, up through the open roof into the sky, the camera
 *   following (the client's cutscene, {@code RitualCutscene}); a golden outline kindles round them, and at
 *   the height of it light breaks out of them — they are set free of the game's rules: creative mode, and
 *   the game says thank you.</li>
 * </ol>
 *
 * <p>Once begun there is no leaving the chamber: whoever is in it, or comes into it, is held there by a
 * ward at its wall and under its roof until they rise.
 */
public final class Ritual {
    public static final int KINDLE = 20, KINDLE_EVERY = 25, RISE = 240, SKY = 480, STRIKES = 300, CONVERGE = 620, CONSUME = 900,
            HEARTBEAT = 960, FINALE = 1100, ASCEND = 1120, CLIMAX = ASCEND + 170, THANKS = CLIMAX + 72, END = ASCEND + 250;
    /** How high over the altar the chamber holds those in it, and how far out. */
    static final double CEILING = 10, WALL = Temple.CHAMBER - 1.0;

    private static final List<Ritual> RUNNING = new ArrayList<>();

    private final ServerLevel level;
    private final BlockPos altar;
    private final Temple.Theme theme;
    private final RandomSource random = RandomSource.create();
    /** Everyone held in the chamber: who rises at the end. */
    private final Set<UUID> bound = new LinkedHashSet<>();
    private final Map<UUID, Integer> warded = new HashMap<>();
    private int age;

    /** To every client near: these players rise from the chamber round this altar, beginning now. */
    public record Ascension(BlockPos altar, List<UUID> players, int color) implements CustomPacketPayload {
        public static final Type<Ascension> TYPE = new Type<>(de.ipnats.hardwrought.Hardwrought.id("ritual_ascension"));
        public static final StreamCodec<ByteBuf, Ascension> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Ascension::altar, UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), Ascension::players,
                ByteBufCodecs.INT, Ascension::color, Ascension::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Ritual(ServerLevel level, BlockPos altar, Temple.Theme theme) {
        this.level = level;
        this.altar = altar;
        this.theme = theme;
    }

    static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(Ascension.TYPE, Ascension.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(Ritual::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNNING.clear());
    }

    public static boolean running(ServerLevel level, BlockPos altar) {
        for (Ritual ritual : RUNNING) if (ritual.level == level && ritual.altar.equals(altar)) return true;
        return false;
    }

    static void begin(ServerLevel level, RitualAltar.Entity entity) {
        if (running(level, entity.getBlockPos())) return;
        RUNNING.add(new Ritual(level, entity.getBlockPos(), entity.theme()));
    }

    /** The colour of the light of the ritual: gold-green, amber or violet, as the temple. */
    public static int color(Temple.Theme theme) {
        return switch (theme) {
            case PLAINS -> 0xFFE89A;
            case DESERT -> 0xFFB040;
            case STONY -> 0xC070FF;
        };
    }

    private static void tick(MinecraftServer server) {
        RUNNING.removeIf(Ritual::step);
    }

    /** One tick of it; true when it is over. */
    private boolean step() {
        if (!(level.getBlockEntity(altar) instanceof RitualAltar.Entity entity)) return true;
        age++;
        Vec3 top = Vec3.atBottomCenterOf(altar).add(0, 1.2, 0);
        int color = color(theme);
        List<BlockPos> pedestals = Temple.pedestals(altar);
        if (age == 1) {
            Fx.play(level, FxEffect.RITUAL_IGNITE, top, top, color, 3, null);
            level.playSound(null, altar, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 3.0f, 0.5f);
            level.playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0f, 0.5f);
        }
        nightfall();
        if (age < ASCEND) confine();
        if (age == ASCEND) ascend(color);
        if (age > ASCEND && age < END) carry();
        if (age == CLIMAX) climax();
        if (age == THANKS) thank();
        if (age == END) release();
        // The pedestals kindle, one by one, each sending its beam to the altar.
        int kindled = age - KINDLE;
        if (kindled >= 0 && kindled % KINDLE_EVERY == 0 && kindled / KINDLE_EVERY < pedestals.size()) {
            BlockPos pedestal = pedestals.get(kindled / KINDLE_EVERY);
            Vec3 from = Vec3.atBottomCenterOf(pedestal).add(0, 1.4, 0);
            Fx.play(level, FxEffect.RITUAL_IGNITE, from, from, color, 1, null);
            Fx.play(level, FxEffect.RITUAL_BEAM, from, top, color, CONSUME - age, null);
            level.playSound(null, pedestal, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 2.0f, 0.6f + 0.1f * (kindled / KINDLE_EVERY));
            level.playSound(null, pedestal, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5f, 0.8f);
        }
        if (age == RISE) {
            entity.raise(level, 3.5, 1.5f);
            level.playSound(null, altar, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 4.0f, 0.5f);
        }
        if (age >= RISE && age < CONSUME && (age - RISE) % 40 == 0) {
            Fx.play(level, FxEffect.RITUAL_RING, top.add(0, 2.5, 0), top, color, 4 + (age - RISE) / 80f, null);
            level.playSound(null, altar, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0f, 0.5f + (age - RISE) / 1300f);
        }
        if (age == SKY) {
            Fx.play(level, FxEffect.RITUAL_SKYBEAM, top.add(0, 3, 0), top.add(0, 160, 0), color, FINALE - SKY, null);
            level.playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4.0f, 0.4f);
            level.playSound(null, altar, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 4.0f, 0.6f);
        }
        if (age >= STRIKES && age < CONSUME && age % 22 == 0) strike();
        if (age == CONVERGE) Fx.play(level, FxEffect.RITUAL_CONVERGE, top.add(0, 3.5, 0), top, color, CONSUME - CONVERGE, null);
        if (age == CONSUME) {
            for (BlockPos pedestal : pedestals) {
                if (level.getBlockEntity(pedestal) instanceof RitualPedestal.Entity entry) entry.consume(level);
                Vec3 at = Vec3.atBottomCenterOf(pedestal).add(0, 1.4, 0);
                Fx.play(level, FxEffect.RITUAL_DISSOLVE, at, top, color, 1, null);
            }
            level.playSound(null, altar, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.BLOCKS, 3.0f, 0.5f);
        }
        if (age >= HEARTBEAT && age < FINALE) {
            int every = Math.max(6, 24 - (age - HEARTBEAT) / 8);
            if ((age - HEARTBEAT) % every == 0) level.playSound(null, altar, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 4.0f, 0.6f);
        }
        if (age == FINALE) finale(entity, top, color);
        if (age > FINALE && age < END && age % 4 == 0) firework(top);
        return age >= END;
    }

    /** The light over the temple falling away to night, where there is a sky with time in it. */
    private void nightfall() {
        if (age > RISE) return;
        var clock = clock();
        if (clock == null) return;
        long time = level.getServer().clockManager().getInstance(clock).totalTicks() % 24000;
        if (Math.abs(time - 18000) < 60) return;
        level.getServer().clockManager().addTicks(clock, 60);
    }

    /** The clock of the day, where the temple stands under one: the overworld's. */
    private net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock() {
        if (level.dimensionType().hasFixedTime()) return null;
        return level.getServer().registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD).orElse(null);
    }

    /** Lightning round the temple: seen and heard, harming nothing. */
    private void strike() {
        double a = random.nextDouble() * Math.PI * 2, d = 18 + random.nextDouble() * 16;
        BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                altar.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d)));
        LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(Vec3.atBottomCenterOf(at));
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    private void firework(Vec3 top) {
        double a = random.nextDouble() * Math.PI * 2, d = 6 + random.nextDouble() * 24;
        Vec3 at = top.add(Math.cos(a) * d, 2, Math.sin(a) * d);
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        int[] colors = {color(theme), 0xFFFFFF, 0xC070FF, 0x6FD8FF};
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + random.nextInt(2), List.of(new FireworkExplosion(
                random.nextBoolean() ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR,
                it.unimi.dsi.fastutil.ints.IntList.of(colors[random.nextInt(colors.length)], colors[random.nextInt(colors.length)]),
                it.unimi.dsi.fastutil.ints.IntList.of(0xFFFFFF), true, true))));
        level.addFreshEntity(new FireworkRocketEntity(level, at.x, at.y, at.z, rocket));
    }

    /** Everything at once, and whoever stands in the chamber set free. */
    private void finale(RitualAltar.Entity entity, Vec3 top, int color) {
        Fx.play(level, FxEffect.RITUAL_FINALE, top.add(0, 3.5, 0), top, color, 1, null);
        level.playSound(null, altar, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 4.0f, 0.8f);
        level.playSound(null, altar, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 4.0f, 1.2f);
        level.playSound(null, altar, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0f, 0.4f);
        // Dawn: the clock run on to the next morning.
        var clock = clock();
        if (clock != null) {
            long time = level.getServer().clockManager().getInstance(clock).totalTicks() % 24000;
            level.getServer().clockManager().addTicks(clock, (int) (24000 - time));
        }
        entity.finish();
        entity.raise(level, 2.4, 1.2f);
    }

    /** The players held in the chamber who are still here to rise: in this level, alive, not watching. */
    private List<ServerPlayer> bound() {
        List<ServerPlayer> found = new ArrayList<>();
        for (UUID id : bound) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level && player.isAlive() && !player.isSpectator()) found.add(player);
        }
        return found;
    }

    /**
     * No leaving the chamber while the ritual runs: whoever is in it is bound to it, and whoever would go
     * out through the wall, or up under the roof, meets the ward there and is set back inside.
     */
    private void confine() {
        for (ServerPlayer player : inChamber()) bound.add(player.getUUID());
        double cx = altar.getX() + 0.5, cz = altar.getZ() + 0.5;
        for (ServerPlayer player : bound()) {
            double dx = player.getX() - cx, dz = player.getZ() - cz, flat = Math.hypot(dx, dz);
            double up = player.getY() - altar.getY();
            if (flat <= WALL && up <= CEILING && up >= -2) continue;
            double scale = flat > WALL - 0.8 ? (WALL - 0.8) / flat : 1;
            double y = Math.clamp(player.getY(), altar.getY() - 1, altar.getY() + CEILING - 1);
            player.stopRiding();
            player.teleportTo(level, cx + dx * scale, y, cz + dz * scale, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
            player.setDeltaMovement(flat > 0.01 ? new Vec3(-dx / flat * 0.4, 0.1, -dz / flat * 0.4) : new Vec3(0, -0.3, 0));
            player.needsSync = true;
            player.fallDistance = 0;
            Integer last = warded.get(player.getUUID());
            if (last != null && age - last < 8) continue;
            warded.put(player.getUUID(), age);
            // Where it was met: the wall before the player, or the roof over them.
            Vec3 at = flat > WALL - 0.8 && flat > 0.01
                    ? new Vec3(cx + dx / flat * (WALL + 0.4), player.getEyeY(), cz + dz / flat * (WALL + 0.4))
                    : new Vec3(player.getX(), altar.getY() + CEILING + 1.6, player.getZ());
            Vec3 facing = flat > WALL - 0.8 && flat > 0.01 ? new Vec3(-dx / flat, 0, -dz / flat) : new Vec3(0, -1, 0);
            Fx.play(level, FxEffect.RITUAL_WARD, at, at.add(facing), color(theme), 1, null);
            level.playSound(null, BlockPos.containing(at), SoundEvents.SHIELD_BLOCK.value(), SoundSource.BLOCKS, 1.0f, 0.6f);
            level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.5f, 0.5f);
            player.sendOverlayMessage(Component.translatable("message.hardwrought.ritual.bound").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Those bound to the chamber begin to rise from it; every client near sees it, and theirs follows them up. */
    private void ascend(int color) {
        List<ServerPlayer> rising = bound();
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer player : rising) {
            ids.add(player.getUUID());
            player.stopRiding();
            player.setPermanentlyInvulnerable(true);
        }
        Ascension payload = new Ascension(altar, ids, color);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(altar)) < 256 * 256 && ServerPlayNetworking.canSend(player, Ascension.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
        level.playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4.0f, 1.4f);
        level.playSound(null, altar, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 4.0f, 0.5f);
    }

    /** While they rise: in the air of their own will, not falling, not hurt. */
    private void carry() {
        for (ServerPlayer player : bound()) {
            player.fallDistance = 0;
            if (player.connection != null) ((ServerGamePacketListenerAccessor) player.connection).hardwrought$setAboveGroundTickCount(0);
        }
        int risen = age - ASCEND;
        if (risen % 30 == 15 && risen < CLIMAX - ASCEND) {
            level.playSound(null, altar.above(20), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 3.0f, 0.6f + risen / 300f);
        }
    }

    /** At the height of it light breaks out of them: set free of the game's rules. */
    private void climax() {
        for (ServerPlayer player : bound()) {
            Vec3 at = player.position().add(0, 1, 0);
            Fx.play(level, FxEffect.RITUAL_APOTHEOSIS, at, at, 0xFFD24A, 1, null);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 3.0f, 0.8f);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 3.0f, 1.0f);
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0f, 1.3f);
            level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 3.0f, 1.5f);
        }
    }

    /** Once the light has died down, and the camera turns back: the game says thank you. */
    private void thank() {
        for (ServerPlayer player : bound()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 140, 40));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.hardwrought.ritual.thanks.title")
                    .withStyle(ChatFormatting.GOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.hardwrought.ritual.thanks.subtitle")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)));
            for (int line = 1; line <= 4; line++) {
                player.sendSystemMessage(Component.translatable("message.hardwrought.ritual.thanks." + line)
                        .withStyle(line == 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            }
        }
    }

    /** Down on the ground of their own again. */
    private void release() {
        for (ServerPlayer player : bound()) player.setPermanentlyInvulnerable(false);
    }

    /** Whether this player is held in a running ritual's chamber; for tests. */
    public static boolean boundForTesting(ServerLevel level, BlockPos altar, ServerPlayer player) {
        for (Ritual ritual : RUNNING) if (ritual.level == level && ritual.altar.equals(altar)) return ritual.bound.contains(player.getUUID());
        return false;
    }

    /** Runs a ritual on so many ticks; for tests. */
    public static void stepForTesting(ServerLevel level, BlockPos altar, int ticks) {
        for (Ritual ritual : RUNNING) {
            if (ritual.level == level && ritual.altar.equals(altar)) {
                for (int i = 0; i < ticks; i++) ritual.step();
                return;
            }
        }
    }

    /** Everyone standing in the ritual chamber round this altar. */
    List<ServerPlayer> inChamber() {
        return inChamber(level, altar);
    }

    public static List<ServerPlayer> inChamber(ServerLevel level, BlockPos altar) {
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            double flat = Math.hypot(player.getX() - (altar.getX() + 0.5), player.getZ() - (altar.getZ() + 0.5));
            double up = player.getY() - altar.getY();
            if (flat <= Temple.CHAMBER && up >= -2 && up <= 28) inside.add(player);
        }
        return inside;
    }

    // ------------------------------------------------------------------------------------ for tests

    /** Runs a ritual to its end at once; for tests. */
    public static void runForTesting(ServerLevel level, BlockPos altar) {
        for (Ritual ritual : RUNNING) {
            if (ritual.level == level && ritual.altar.equals(altar)) {
                while (!ritual.step()) { }
                RUNNING.remove(ritual);
                return;
            }
        }
    }
}
