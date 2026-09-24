package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sections 73 and 92: how fast a line of turning parts runs, and whether it is strong enough to.
 *
 * <p>A line is everything connected to everything else by axles, gear teeth and belts. Each part
 * turns at a fixed ratio of every other — a gear of half the size turns twice as fast and the other
 * way — so the whole line has one speed, measured at any one of its parts, and the rest follow.
 *
 * <p>Sources set that speed. When several drive the same line, the fastest one does; if they drive
 * it in opposite directions, or the line's own gearing contradicts itself (two paths from one gear
 * to another that disagree), the line is <b>jammed</b> and stands still.
 *
 * <p>Sources also give the line strength — stress units — and machines take it: each one takes its
 * impact times the speed it is driven at. Gearing a machine up therefore makes it both faster and
 * heavier to turn, which is the whole of torque against speed in one rule. A line asked for more
 * strength than its sources give is <b>overstressed</b> and stops.
 *
 * <p>Lines are worked out when something about them changes — a part placed or broken, a crank
 * turned or let go, a wheel's water rising — not every tick. Bounded to {@link #MAX_PARTS} parts.
 */
public final class Kinetics {
    public static final int MAX_PARTS = 256;
    private static final float RATIO_TOLERANCE = 1e-3f;

    public enum Status { STILL, RUNNING, OVERSTRESSED, JAMMED }

    /**
     * One worked-out line: every part with its ratio to the first, and what the line is doing.
     *
     * @param base     the speed of the part the line was walked from; every other part runs at
     *                 {@code base * ratio}
     * @param stress   what the machines on it take at that speed
     * @param capacity what its sources give
     */
    public record Network(Map<BlockPos, Float> ratios, Status status, float base, float stress, float capacity) {
        public float speedAt(BlockPos pos) {
            Float ratio = ratios.get(pos);
            return ratio == null ? 0.0f : base * ratio;
        }
    }

    private Kinetics() { }

    /** How fast the part at this position runs, as last worked out; 0 where it is no turning part. */
    public static float speed(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticHolder holder ? holder.kineticSpeed() : 0.0f;
    }

    /** Walks and works out the line this block belongs to, without changing anything. */
    public static Network solve(Level level, BlockPos start) {
        BlockState first = level.getBlockState(start);
        if (!(first.getBlock() instanceof KineticBlock)) return null;
        Map<BlockPos, Float> ratios = new LinkedHashMap<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        ratios.put(start.immutable(), 1.0f);
        pending.add(start.immutable());
        boolean[] jammed = { false };

        while (!pending.isEmpty() && ratios.size() <= MAX_PARTS) {
            BlockPos here = pending.poll();
            BlockState state = level.getBlockState(here);
            if (!(state.getBlock() instanceof KineticBlock part)) continue;
            float ratio = ratios.get(here);
            for (Direction face : Direction.values()) {
                float mine = part.port(state, face);
                if (mine == 0.0f) continue;
                BlockPos next = here.relative(face);
                BlockState beyond = level.getBlockState(next);
                if (!(beyond.getBlock() instanceof KineticBlock other)) continue;
                float theirs = other.port(beyond, face.getOpposite());
                if (theirs == 0.0f) continue;
                // One axle, one speed: mine * this = theirs * that.
                offer(ratios, pending, jammed, next, ratio * mine / theirs);
            }
            part.links(level, here, state, (other, factor) -> {
                BlockState beyond = level.getBlockState(other);
                if (beyond.getBlock() instanceof KineticBlock) offer(ratios, pending, jammed, other, ratio * factor);
            });
        }

        float capacity = 0.0f;
        float impactPerBase = 0.0f;
        float base = 0.0f;
        boolean forward = false;
        boolean backward = false;
        for (Map.Entry<BlockPos, Float> entry : ratios.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof KineticBlock part)) continue;
            float ratio = entry.getValue();
            impactPerBase += part.impact(level, pos, state) * Math.abs(ratio);
            float drive = part.drive(level, pos, state);
            if (drive == 0.0f) continue;
            capacity += part.capacity(level, pos, state);
            float implied = drive / ratio;
            if (implied > 0) forward = true;
            else backward = true;
            if (Math.abs(implied) > Math.abs(base)) base = implied;
        }
        if (jammed[0] || (forward && backward)) {
            return new Network(ratios, Status.JAMMED, 0.0f, 0.0f, capacity);
        }
        if (base == 0.0f) return new Network(ratios, Status.STILL, 0.0f, 0.0f, capacity);
        float stress = impactPerBase * Math.abs(base);
        if (stress > capacity) return new Network(ratios, Status.OVERSTRESSED, 0.0f, stress, capacity);
        return new Network(ratios, Status.RUNNING, base, stress, capacity);
    }

    private static void offer(Map<BlockPos, Float> ratios, Deque<BlockPos> pending, boolean[] jammed,
                              BlockPos pos, float ratio) {
        Float known = ratios.get(pos);
        if (known == null) {
            ratios.put(pos.immutable(), ratio);
            pending.add(pos.immutable());
        } else if (Math.abs(known - ratio) > RATIO_TOLERANCE * Math.max(1.0f, Math.abs(ratio))) {
            jammed[0] = true;
        }
    }

    /**
     * Works out again every line that touches this position. Called after anything that could have
     * changed a line: a part placed or broken, a source starting, stopping or changing its speed.
     * Looks at the block itself and everything around it, diagonals included, because gears mesh
     * across corners.
     */
    public static void update(Level level, BlockPos origin) {
        if (level.isClientSide()) return;
        Set<BlockPos> done = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    refresh(level, origin.offset(dx, dy, dz), done);
                }
            }
        }
    }

    /** Works out again the line this one block belongs to, unless it was already done this round. */
    public static void refresh(Level level, BlockPos pos, Set<BlockPos> done) {
        if (level.isClientSide() || done.contains(pos)) return;
        Network network = solve(level, pos);
        if (network == null) return;
        done.addAll(network.ratios().keySet());
        apply(level, network);
    }

    public static void refresh(Level level, BlockPos pos) {
        refresh(level, pos, new HashSet<>());
    }

    private static void apply(Level level, Network network) {
        for (Map.Entry<BlockPos, Float> entry : network.ratios().entrySet()) {
            BlockPos pos = entry.getKey();
            float speed = network.base() * entry.getValue();
            if (level.getBlockEntity(pos) instanceof KineticHolder holder) holder.setKineticSpeed(speed);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ShaftBlock && state.getValue(ShaftBlock.DRIVEN) != (speed != 0.0f)) {
                // Clients only: the state says nothing the block entity does not, it is kept for the model.
                level.setBlock(pos, state.setValue(ShaftBlock.DRIVEN, speed != 0.0f), Block.UPDATE_CLIENTS);
            }
        }
    }
}
