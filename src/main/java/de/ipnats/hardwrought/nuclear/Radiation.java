package de.ipnats.hardwrought.nuclear;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModDamageTypes;
import de.ipnats.hardwrought.core.registry.ModEffects;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Ionising radiation and what it does to a body.
 *
 * <p>Once a second every player is given the dose of where they stand: what they carry, a running or
 * shut-down reactor, spent fuel lying about, the corium and debris of a meltdown. Everything outside
 * the player reaches them as the square of the distance falls off, and through whatever stands between,
 * block by block (see {@link Radioactivity#passes}).
 *
 * <p>The dose adds up on the player in sieverts. The body mends slowly — the dose that counts halves
 * over {@value #HALF_LIFE_SECONDS} seconds — so what matters is a large dose taken quickly, as it does:
 * <ul>
 *   <li>from {@value #NAUSEA} Sv, nausea now and then;</li>
 *   <li>from {@value #SICK} Sv, radiation sickness: weak and hungry;</li>
 *   <li>from {@value #SEVERE} Sv, it costs health;</li>
 *   <li>from {@value #LETHAL} Sv, it kills within minutes.</li>
 * </ul>
 * The dose is not kept through death.
 */
public final class Radiation {
    public static final double NAUSEA = 0.5, SICK = 1.0, SEVERE = 3.0, LETHAL = 6.0;
    static final int HALF_LIFE_SECONDS = 2400;
    /** How far a source reaches at all, in blocks: past this it is lost in the background. */
    static final int RANGE = 48;
    /** How far round a player blocks and dropped items are looked at. */
    private static final int NEAR = 6;

    /** What a player has taken, in sieverts. */
    public static final AttachmentType<Double> DOSE = AttachmentRegistry.create(Hardwrought.id("radiation_dose"), builder ->
            builder.persistent(Codec.DOUBLE).initializer(() -> 0.0));

    /** The sources that are not blocks of their own — reactors — by place, in Sv/h at a metre. */
    private static final Map<ServerLevel, Map<BlockPos, Double>> SOURCES = new WeakHashMap<>();
    /** What each player was given in the last second, in Sv/h, for the counter. */
    private static final Map<java.util.UUID, Double> RATE = new HashMap<>();

    private Radiation() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(Radiation::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SOURCES.clear();
            RATE.clear();
        });
    }

    /** Sets what a source at this place gives at a metre, in Sv/h; zero takes it away. */
    public static void source(ServerLevel level, BlockPos pos, double svPerHour) {
        Map<BlockPos, Double> sources = SOURCES.computeIfAbsent(level, ignored -> new HashMap<>());
        if (svPerHour <= 0) sources.remove(pos);
        else sources.put(pos.immutable(), svPerHour);
    }

    public static double dose(Player player) {
        return player.getAttachedOrElse(DOSE, 0.0);
    }

    public static void setDose(Player player, double sievert) {
        player.setAttached(DOSE, Math.max(0.0, sievert));
    }

    /** The dose rate the player was in a second ago, in Sv/h. */
    public static double rate(Player player) {
        return RATE.getOrDefault(player.getUUID(), 0.0);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) second(player);
    }

    /** One second of a player's life in radiation: the dose of where they stand, the body's mending, the harm. */
    public static void second(ServerPlayer player) {
        if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
            RATE.remove(player.getUUID());
            return;
        }
        double rate = rateAt(player);
        RATE.put(player.getUUID(), rate);
        double dose = dose(player) * Math.pow(0.5, 1.0 / HALF_LIFE_SECONDS) + rate / 3600.0;
        setDose(player, dose < 1e-9 ? 0.0 : dose);
        harm(player, dose);
        GeigerCounterItem.listen(player, rate);
    }

    /** The dose rate where the player stands, in Sv/h. */
    public static double rateAt(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        double rate = 0.0;
        for (ItemStack stack : player.getInventory()) rate += Radioactivity.of(stack);
        Map<BlockPos, Double> sources = SOURCES.get(level);
        if (sources != null) {
            for (Map.Entry<BlockPos, Double> source : sources.entrySet()) {
                rate += reaching(level, Vec3.atCenterOf(source.getKey()), eye, source.getValue());
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(NEAR))) {
            double each = Radioactivity.of(item.getItem());
            if (each > 0) rate += reaching(level, item.position().add(0, 0.2, 0), eye, each);
        }
        rate += blocksNear(level, player.blockPosition(), eye);
        return rate;
    }

    /** What a source gives at a point: its strength at a metre, over the square of the distance, through what is between. */
    public static double reaching(ServerLevel level, Vec3 from, Vec3 to, double svPerHour) {
        double distance = from.distanceTo(to);
        if (distance > RANGE) return 0.0;
        double rate = svPerHour / Math.max(1.0, distance * distance);
        return rate * shielding(level, from, to);
    }

    /** The share that gets through every block on the straight line between two points. */
    public static double shielding(ServerLevel level, Vec3 from, Vec3 to) {
        BlockPos start = BlockPos.containing(from), end = BlockPos.containing(to);
        Set<BlockPos> crossed = new LinkedHashSet<>();
        Vec3 step = to.subtract(from);
        int steps = (int) Math.ceil(step.length() / 0.25);
        for (int i = 1; i < steps; i++) {
            BlockPos at = BlockPos.containing(from.add(step.scale(i / (double) steps)));
            if (!at.equals(start) && !at.equals(end)) crossed.add(at);
        }
        double passes = 1.0;
        for (BlockPos at : crossed) {
            passes *= Radioactivity.passes(level, at);
            if (passes < 1e-9) return 0.0;
        }
        return passes;
    }

    /** Corium and debris near the player: looked for only in sections that hold any. */
    private static double blocksNear(ServerLevel level, BlockPos centre, Vec3 eye) {
        double rate = 0.0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int sx = (centre.getX() - NEAR) >> 4; sx <= (centre.getX() + NEAR) >> 4; sx++) {
            for (int sz = (centre.getZ() - NEAR) >> 4; sz <= (centre.getZ() + NEAR) >> 4; sz++) {
                if (!(level.getChunkSource().getChunkNow(sx, sz) instanceof LevelChunk chunk)) continue;
                for (int sy = (centre.getY() - NEAR) >> 4; sy <= (centre.getY() + NEAR) >> 4; sy++) {
                    int index = level.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) continue;
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> Radioactivity.of(state) > 0)) continue;
                    for (int x = Math.max(sx << 4, centre.getX() - NEAR); x <= Math.min((sx << 4) + 15, centre.getX() + NEAR); x++) {
                        for (int y = Math.max(sy << 4, centre.getY() - NEAR); y <= Math.min((sy << 4) + 15, centre.getY() + NEAR); y++) {
                            for (int z = Math.max(sz << 4, centre.getZ() - NEAR); z <= Math.min((sz << 4) + 15, centre.getZ() + NEAR); z++) {
                                double each = Radioactivity.of(section.getBlockState(x & 15, y & 15, z & 15));
                                if (each > 0) rate += reaching(level, Vec3.atCenterOf(at.set(x, y, z)), eye, each);
                            }
                        }
                    }
                }
            }
        }
        return rate;
    }

    /** What the dose does: the worse it is, the more. */
    private static void harm(ServerPlayer player, double dose) {
        if (dose < NAUSEA) return;
        long second = player.level().getGameTime() / 20;
        if (dose >= SICK) {
            int stage = dose >= LETHAL ? 2 : dose >= SEVERE ? 1 : 0;
            player.addEffect(new MobEffectInstance(ModEffects.RADIATION_SICKNESS, 60, stage, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, stage, false, false, false));
            player.causeFoodExhaustion(0.05f * (stage + 1));
            int every = stage == 2 ? 4 : stage == 1 ? 30 : 0;
            if (every > 0 && second % every == 0) {
                player.hurtServer(player.level(), player.damageSources().source(ModDamageTypes.RADIATION), 1.0f);
            }
        }
        if (second % 45 == 0 && player.getRandom().nextDouble() < Math.min(0.9, dose / SEVERE)) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0, false, false, true));
        }
    }
}
