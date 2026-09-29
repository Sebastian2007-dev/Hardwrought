package de.ipnats.hardwrought.building;

import de.ipnats.hardwrought.core.registry.MaterialDefinition.StructuralProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Section 39: simplified structural physics for what players build. Pure arithmetic on a view of the
 * world, so it can be reasoned about and tested without a server.
 *
 * <p>Two questions are asked of every built block:
 * <ol>
 *   <li><b>Is it supported?</b> Support flows out of the ground. A block resting on natural terrain
 *   has full support; stacking passes it up unchanged; every block spanned sideways costs
 *   {@code 1 / (support distance + 1)} of it, so a material can reach out as many blocks as its
 *   support distance says. A block can hang below another only if it has tensile strength. Whatever
 *   no path of support reaches is unsupported.</li>
 *   <li><b>Can it carry its load?</b> Each block passes its own weight and everything resting on it
 *   down the path its support came from. A block pressed by more than its compressive strength over
 *   one square metre — or pulled by more than its tensile strength, if it hangs — is overloaded.
 *   This is what keeps a castle off a single dirt block.</li>
 * </ol>
 * Bending is not modelled: the support distance stands in for it.
 */
public final class Statics {
    /** Natural terrain is anchored; built blocks are members; everything else carries nothing. */
    public sealed interface Cell permits Anchor, Member, Empty { }
    public record Anchor() implements Cell { }
    public record Empty() implements Cell { }
    /**
     * A built block: what it is made of, what it weighs in newtons, and whether a structural anchor
     * ties it to its neighbours.
     */
    public record Member(StructuralProperties material, double weightN, boolean anchored) implements Cell {
        public Member(StructuralProperties material, double weightN) {
            this(material, weightN, false);
        }

        public Member withAnchor() { return new Member(material, weightN, true); }
    }

    public static final Cell ANCHOR = new Anchor();
    public static final Cell EMPTY = new Empty();

    @FunctionalInterface
    public interface View {
        Cell at(BlockPos pos);
    }

    /** How a block is held: by what it rests on, what it is set into, or what it hangs from. */
    public enum Held { GROUND, BELOW, SIDE, WALL, ABOVE, CEILING, ANCHORED, NONE }

    /**
     * What one structural anchor holds, in newtons: about five tonnes, an iron tie bolted through.
     * An anchored block takes its neighbour's support whole, without the cost of a span, so a chain
     * of anchors could reach out for ever — but every block of it hangs on the first anchor, and that
     * one gives way long before.
     */
    public static final double ANCHOR_CAPACITY_N = 50_000;

    /**
     * The verdict for one block. Capacity is infinite where no single strength governs (a span held
     * from the side); {@code support} is zero for an unsupported block.
     */
    public record Result(double support, Held held, double loadN, double capacityN) {
        public boolean supported() { return held != Held.NONE; }

        /** Load over capacity; above 1 the block is overloaded. */
        public double utilisation() { return capacityN == Double.POSITIVE_INFINITY ? 0 : loadN / capacityN; }
    }

    private static final double EPSILON = 1e-9;
    private static final double PA_PER_MPA = 1_000_000;

    private Statics() { }

    /** How much support a block of this material loses for every block it spans sideways or hangs. */
    public static double spanCost(StructuralProperties material) {
        return 1.0 / (material.supportDistanceBlocks() + 1.0);
    }

    /**
     * Gathers the built blocks connected to {@code starts} and judges each. Returns null when the
     * structure has more than {@code limit} blocks: it is then left alone rather than judged by part.
     */
    public static Map<BlockPos, Result> solve(View view, Collection<BlockPos> starts, int limit) {
        Map<BlockPos, Member> members = gather(view, starts, limit);
        if (members == null) return null;

        Map<BlockPos, Double> best = new HashMap<>();
        Map<BlockPos, Held> held = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        PriorityQueue<Map.Entry<BlockPos, Double>> open =
                new PriorityQueue<>((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (var entry : members.entrySet()) {
            BlockPos pos = entry.getKey();
            StructuralProperties material = entry.getValue().material();
            double support = 0;
            Held how = Held.NONE;
            if (view.at(pos.below()) instanceof Anchor) {
                support = 1;
                how = Held.GROUND;
            } else {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (view.at(pos.relative(side)) instanceof Anchor) {
                        support = 1 - spanCost(material);
                        how = Held.WALL;
                        break;
                    }
                }
                if (how == Held.NONE && material.tensionStrengthMpa() > 0
                        && view.at(pos.above()) instanceof Anchor) {
                    support = 1 - spanCost(material);
                    how = Held.CEILING;
                }
                if (entry.getValue().anchored()) {
                    for (Direction direction : Direction.values()) {
                        if (view.at(pos.relative(direction)) instanceof Anchor) {
                            support = 1;
                            how = Held.ANCHORED;
                            break;
                        }
                    }
                }
            }
            if (support > EPSILON) {
                best.put(pos, support);
                held.put(pos, how);
                open.add(Map.entry(pos, support));
            }
        }

        // Strongest support first: once a block is taken from the queue its support is final.
        List<BlockPos> order = new ArrayList<>();
        java.util.Set<BlockPos> done = new java.util.HashSet<>();
        while (!open.isEmpty()) {
            var next = open.poll();
            BlockPos pos = next.getKey();
            if (!done.add(pos)) continue;
            order.add(pos);
            double support = next.getValue();
            for (Direction direction : Direction.values()) {
                BlockPos other = pos.relative(direction);
                Member member = members.get(other);
                if (member == null || done.contains(other)) continue;
                double offered;
                Held how;
                if (direction == Direction.UP) {
                    offered = support;
                    how = Held.BELOW;
                } else if (direction == Direction.DOWN) {
                    offered = member.material().tensionStrengthMpa() > 0 ? support - spanCost(member.material()) : 0;
                    how = Held.ABOVE;
                } else {
                    offered = support - spanCost(member.material());
                    how = Held.SIDE;
                }
                // The anchor only helps where the block could not hold as well by itself.
                if (member.anchored() && support > offered + EPSILON) {
                    offered = support;
                    how = Held.ANCHORED;
                }
                if (offered > EPSILON && offered > best.getOrDefault(other, 0.0) + EPSILON) {
                    best.put(other, offered);
                    held.put(other, how);
                    parent.put(other, pos);
                    open.add(Map.entry(other, offered));
                }
            }
        }

        // Loads flow back down the paths the support came up: the last block reached is outermost.
        Map<BlockPos, Double> load = new HashMap<>();
        members.forEach((pos, member) -> load.put(pos, member.weightN()));
        for (int i = order.size() - 1; i >= 0; i--) {
            BlockPos pos = order.get(i);
            BlockPos from = parent.get(pos);
            if (from != null) load.merge(from, load.get(pos), Double::sum);
        }

        Map<BlockPos, Result> results = new HashMap<>();
        for (var entry : members.entrySet()) {
            BlockPos pos = entry.getKey();
            StructuralProperties material = entry.getValue().material();
            Held how = held.getOrDefault(pos, Held.NONE);
            if (!done.contains(pos)) how = Held.NONE;
            double capacity = switch (how) {
                case GROUND, BELOW -> material.compressionStrengthMpa() * PA_PER_MPA;
                case ABOVE, CEILING -> material.tensionStrengthMpa() * PA_PER_MPA;
                case ANCHORED -> ANCHOR_CAPACITY_N;
                case SIDE, WALL, NONE -> Double.POSITIVE_INFINITY;
            };
            results.put(pos, new Result(how == Held.NONE ? 0 : best.get(pos), how, load.get(pos), capacity));
        }
        return results;
    }

    private static Map<BlockPos, Member> gather(View view, Collection<BlockPos> starts, int limit) {
        Map<BlockPos, Member> members = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : starts) {
            if (!members.containsKey(start) && view.at(start) instanceof Member member) {
                members.put(start.immutable(), member);
                queue.add(start.immutable());
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos other = pos.relative(direction);
                if (members.containsKey(other)) continue;
                if (view.at(other) instanceof Member member) {
                    if (members.size() >= limit) return null;
                    members.put(other, member);
                    queue.add(other);
                }
            }
        }
        return members;
    }
}
