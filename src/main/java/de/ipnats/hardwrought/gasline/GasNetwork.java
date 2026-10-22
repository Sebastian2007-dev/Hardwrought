package de.ipnats.hardwrought.gasline;

import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.Gases;
import de.ipnats.hardwrought.machinery.ConnectedPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * One gas line, worked once a tick: the stores on it even out their pressure, the higher giving to the
 * lower, and a line with a store on it above what its weakest pipe holds bursts there.
 *
 * <p>Gas goes from the store that stands highest to the one that stands lowest and will take it, as much
 * as the line carries for that difference — the narrowest pipe on it decides — but never so much that
 * the two cross over. That is repeated a few times a tick, so a line with several stores settles quickly.
 * A shut {@link GasValveBlock} ends a line, so the pipes on either side of it are two lines.
 */
public final class GasNetwork {
    private static final int LIMIT = 512;
    /** How many give-and-take steps a line takes in a tick. */
    private static final int STEPS = 4;
    /** How much gas a burst pipe lets out at once, in units, from the store that burst it. */
    private static final int BURST_RELEASE = 16;
    /** A line over pressure bursts on a random second, not at once: about one second in four. */
    private static final int BURST_ODDS = 4;

    private record Worked(long tick, Set<Long> lines) { }

    private static final Map<ServerLevel, Worked> WORKED = new WeakHashMap<>();

    private GasNetwork() { }

    /** Works the line the gas pipe at start is part of, once in this tick whichever of its pipes asks. */
    static void tick(ServerLevel level, BlockPos start) {
        Worked worked = WORKED.get(level);
        if (worked == null || worked.tick() != level.getGameTime()) {
            worked = new Worked(level.getGameTime(), new HashSet<>());
            WORKED.put(level, worked);
        }
        List<BlockPos> pipes = new ArrayList<>();
        List<GasStorage> stores = new ArrayList<>();
        long id = collect(level, start, pipes, stores);
        if (!worked.lines().add(id)) return;
        if (pipes.isEmpty()) return;
        GasLineTier narrowest = GasLineTier.TITANIUM;
        BlockPos weakest = pipes.getFirst();
        for (BlockPos pipe : pipes) {
            GasLineTier tier = ((GasLineBlock) level.getBlockState(pipe).getBlock()).tier();
            if (tier.ordinal() < narrowest.ordinal()) {
                narrowest = tier;
                weakest = pipe;
            }
        }
        if (level.getGameTime() % 20 == 0 && burst(level, weakest, narrowest, stores)) return;
        for (int step = 0; step < STEPS; step++) {
            if (!even(stores, narrowest.flow() / STEPS)) break;
        }
    }

    /** One step: the highest store gives to the lowest that takes its gas. Whether anything moved. */
    private static boolean even(List<GasStorage> stores, double flow) {
        GasStorage high = null;
        for (GasStorage store : stores) {
            if (store.kind() != null && (high == null || store.pressure() > high.pressure())) high = store;
        }
        if (high == null) return false;
        GasStorage low = null;
        for (GasStorage store : stores) {
            if (store != high && store.accepts(high.kind()) && (low == null || store.pressure() < low.pressure())) low = store;
        }
        if (low == null) return false;
        double difference = high.pressure() - low.pressure();
        if (difference <= 1e-4) return false;
        // As much as would bring them level, and no more than the line carries for that difference.
        double perUnitHigh = high.rating() / high.capacity(), perUnitLow = low.rating() / low.capacity();
        double level = difference / (perUnitHigh + perUnitLow);
        double moved = Math.min(level, flow * difference);
        GasKind kind = high.kind();
        double taken = high.extract(moved);
        double put = low.insert(kind, taken);
        // What the low store could not take after all goes back where it came from.
        if (put < taken) high.insert(kind, taken - put);
        return put > 1e-6;
    }

    /**
     * A store above what the weakest pipe holds bursts it, now and then: the pipe is gone and the gas of
     * the store behind it rushes out there, firedamp to choke on and to catch fire.
     */
    private static boolean burst(ServerLevel level, BlockPos weakest, GasLineTier tier, List<GasStorage> stores) {
        GasStorage worst = null;
        for (GasStorage store : stores) if (worst == null || store.pressure() > worst.pressure()) worst = store;
        if (worst == null || worst.pressure() <= tier.burstBar() || level.getRandom().nextInt(BURST_ODDS) != 0) return false;
        level.setBlock(weakest, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        GasKind kind = worst.kind();
        double escaped = worst.extract(BURST_RELEASE);
        // What burns comes out as firedamp; steam scalds whoever stands by; chlorine has no gas of its own
        // in the world and is simply lost.
        if (kind == null || kind.burns()) Gases.emit(level, weakest, Gas.METHANE, (int) Math.ceil(escaped));
        if (kind == GasKind.STEAM) de.ipnats.hardwrought.steam.Scalding.burst(level, weakest, escaped);
        level.playSound(null, weakest, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.5f);
        level.playSound(null, weakest, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.8f);
        level.sendParticles(ParticleTypes.CLOUD, weakest.getX() + 0.5, weakest.getY() + 0.5, weakest.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.1);
        return true;
    }

    /** The pipes of the line and the stores on it; the line's name is its lowest position. */
    static long collect(ServerLevel level, BlockPos start, List<BlockPos> pipes, List<GasStorage> stores) {
        Set<BlockPos> seen = new HashSet<>();
        Set<GasStorage> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        long id = start.asLong();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() <= LIMIT) {
            BlockPos at = queue.poll();
            BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof GasLineBlock)) continue;
            // A shut valve is where the line ends, from whichever side it is reached.
            if (state.getBlock() instanceof GasValveBlock && !GasValveBlock.passes(state)) continue;
            pipes.add(at);
            id = Math.min(id, at.asLong());
            for (Direction side : Direction.values()) {
                if (!ConnectedPipeBlock.link(state, side).joined()) continue;
                BlockPos next = at.relative(side);
                if (level.getBlockState(next).getBlock() instanceof GasLineBlock) {
                    if (seen.add(next)) queue.add(next);
                    continue;
                }
                GasStorage store = GasStorage.SIDED.find(level, next, side.getOpposite());
                if (store != null && found.add(store)) stores.add(store);
            }
        }
        return id;
    }
}
