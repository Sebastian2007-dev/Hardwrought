package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.core.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Section 76: voltage, current, and what they do to wire and to flesh.
 *
 * <p>A network is every point joined to every other by strung wire. The earth is the way back: every
 * dynamo drives its voltage against the ground and every lamp lets current through to it, so one
 * wire is a circuit. Each wire has a resistance by its length and thickness, and from those the
 * voltage at every point and the current in every wire are worked out exactly — a long thin line
 * loses voltage on the way, and the lamp at its far end burns dimmer than the one beside the dynamo.
 *
 * <p>Three things follow from the numbers and nothing else:
 * <ul>
 *   <li>a wire carrying more than its {@link Gauge#amps} heats, glows, and burns through — and what
 *       falls from it sets fire to what lies below;</li>
 *   <li>bare wire at {@link #SHOCK_VOLTS} or more hurts whatever touches it, more the higher the
 *       voltage. A line belongs above head height;</li>
 *   <li>water on a point lets current creep to the ground: the line loses power, and whatever stands
 *       in that water is shocked.</li>
 * </ul>
 *
 * <p>Networks with a dynamo in them are worked out again every {@link #PULSE_TICKS}, and at once when
 * anything about them changes. Bounded to {@link #MAX_NODES} points.
 */
public final class Electricity {
    public static final int MAX_NODES = 128;
    public static final int PULSE_TICKS = 10;
    /** Below this a wire can be handled; from here on it bites. */
    public static final double SHOCK_VOLTS = 30.0;
    /** What a point standing in water, or rained on, lets through to the ground. */
    public static final double WATER_OHMS = 20.0, RAIN_OHMS = 150.0;
    /** How far through water a wet point reaches. */
    public static final double WATER_REACH = 4.0;
    /** How much heat a wire stands before it parts; see {@link #cook} for how fast it gathers. */
    public static final float BURN_HEAT = 8.0f;
    /** A strung wire is drawn, and touched, as this many straight pieces along its sag. */
    public static final int SEGMENTS = 8;

    /** Why two points cannot be joined. */
    public enum Refusal { NOT_A_POINT, SAME, ALREADY, FULL, TOO_FAR, BLOCKED, NEEDS_COIL }

    /** What a coil is between its wires and the block it sits on: next to nothing. */
    public static final double JOINT_OHMS = 0.01;

    /**
     * The current in one wire, from one point of the network to another — or, where the gauge is null,
     * through the joint between a coil and the block it sits on.
     */
    public record Flow(int from, int to, Gauge gauge, double length, double amps) { }

    /**
     * One worked-out network.
     *
     * @param generated what its dynamos put into it, in watts
     * @param delivered what arrives in its lamps
     * @param leaked    what creeps away through wet points
     * @param lost      what its wires turn into heat
     */
    public record Network(List<ElectricBlockEntity> nodes, double[] volts, double[] amps, boolean[] wet,
                          List<Flow> flows, double generated, double delivered, double leaked, double lost) {
        public int indexOf(BlockPos pos) {
            for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).getBlockPos().equals(pos)) return i;
            return -1;
        }
    }

    private record Stamp(long time, Set<BlockPos> done) { }

    private static final Map<ServerLevel, Stamp> PULSED = new WeakHashMap<>();

    private Electricity() { }

    // --- geometry ---

    /** How far a wire of this length hangs down in its middle. */
    public static double sag(double length) {
        return Math.min(1.2, length * 0.045);
    }

    /** The point a share {@code t} of the way along a wire strung from {@code a} to {@code b}. */
    public static Vec3 along(Vec3 a, Vec3 b, double t) {
        double drop = 4.0 * sag(a.distanceTo(b)) * t * (1.0 - t);
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t - drop, a.z + (b.z - a.z) * t);
    }

    private static boolean clear(ServerLevel level, ElectricBlockEntity a, ElectricBlockEntity b) {
        Vec3 from = a.terminal(), to = b.terminal();
        int steps = Math.max(2, (int) Math.ceil(from.distanceTo(to) * 4));
        for (int i = 1; i < steps; i++) {
            BlockPos at = BlockPos.containing(along(from, to, i / (double) steps));
            if (at.equals(a.getBlockPos()) || at.equals(b.getBlockPos())) continue;
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return false;
        }
        return true;
    }

    // --- stringing and cutting ---

    /** The point of a network this block stands for: itself, or for the foot of a mast the head of it. Null where it is none. */
    public static BlockPos point(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos at = state.getBlock() instanceof MastBlock ? MastBlock.head(pos, state) : pos;
        return level.getBlockEntity(at) instanceof ElectricBlockEntity ? at : null;
    }

    /** Whether a wire of this gauge may be strung between these two points; null where it may. */
    public static Refusal refusal(ServerLevel level, BlockPos first, BlockPos second, Gauge gauge) {
        if (first.equals(second)) return Refusal.SAME;
        if (!(level.getBlockEntity(first) instanceof ElectricBlockEntity a)
                || !(level.getBlockEntity(second) instanceof ElectricBlockEntity b)) return Refusal.NOT_A_POINT;
        if (!a.takesWires() || !b.takesWires()) return Refusal.NEEDS_COIL;
        if (a.wireTo(second) != null) return Refusal.ALREADY;
        if (a.wires().size() >= ElectricBlockEntity.MAX_WIRES || b.wires().size() >= ElectricBlockEntity.MAX_WIRES) {
            return Refusal.FULL;
        }
        if (a.terminal().distanceTo(b.terminal()) > gauge.span()) return Refusal.TOO_FAR;
        return clear(level, a, b) ? null : Refusal.BLOCKED;
    }

    /** Strings a wire. The caller has asked {@link #refusal} first. */
    public static void connect(ServerLevel level, BlockPos first, BlockPos second, Gauge gauge) {
        if (!(level.getBlockEntity(first) instanceof ElectricBlockEntity a)
                || !(level.getBlockEntity(second) instanceof ElectricBlockEntity b)) return;
        a.attach(second, gauge);
        b.attach(first, gauge);
        update(level, first);
    }

    /** Takes one wire down and gives nothing back for it: a wire that burnt through. */
    public static void disconnect(ServerLevel level, BlockPos first, BlockPos second) {
        if (level.getBlockEntity(first) instanceof ElectricBlockEntity a) a.detach(second);
        if (level.getBlockEntity(second) instanceof ElectricBlockEntity b) b.detach(first);
        update(level, first);
        update(level, second);
    }

    /** How many coils a wire between these two points takes. */
    public static int coils(ServerLevel level, BlockPos first, BlockPos second, Gauge gauge) {
        if (level.getBlockEntity(first) instanceof ElectricBlockEntity a
                && level.getBlockEntity(second) instanceof ElectricBlockEntity b) {
            return gauge.coils(a.terminal().distanceTo(b.terminal()));
        }
        return 1;
    }

    /** Takes every wire off this point and hands the coils to the player. True where there was any. */
    public static boolean cut(ServerLevel level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ElectricBlockEntity node) || node.wires().isEmpty()) return false;
        for (ElectricBlockEntity.Wire wire : node.wires()) {
            if (level.getBlockEntity(wire.other()) instanceof ElectricBlockEntity partner) {
                ItemStack coils = new ItemStack(wire.gauge().item(),
                        wire.gauge().coils(node.terminal().distanceTo(partner.terminal())));
                if (player == null || !player.getInventory().add(coils)) {
                    net.minecraft.world.level.block.Block.popResource(level, pos, coils);
                }
                partner.detach(pos);
            }
            node.detach(wire.other());
            update(level, wire.other());
        }
        update(level, pos);
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.7f, 1.4f);
        return true;
    }

    // --- working a network out ---

    /** Whether water (2) or rain (1) is on this point, or neither (0). */
    static int wetness(ServerLevel level, ElectricBlockEntity node) {
        BlockPos pos = node.getBlockPos();
        if (level.getFluidState(pos).is(FluidTags.WATER)) return 2;
        for (Direction side : Direction.values()) {
            if (level.getFluidState(pos.relative(side)).is(FluidTags.WATER)) return 2;
        }
        return node.mindsRain() && level.isRainingAt(pos.above()) ? 1 : 0;
    }

    /**
     * What this point is joined to without a wire: for a coil the block it sits on, for a block that
     * takes its current through coils every coil sitting on it.
     */
    static List<ElectricBlockEntity> joints(ServerLevel level, ElectricBlockEntity node) {
        BlockPos pos = node.getBlockPos();
        BlockState state = node.getBlockState();
        if (state.getBlock() instanceof CoilBlock) {
            BlockPos seat = CoilBlock.seat(pos, state);
            return level.isLoaded(seat) && level.getBlockEntity(seat) instanceof ElectricBlockEntity fed && !fed.takesWires()
                    ? List.of(fed) : List.of();
        }
        if (node.takesWires()) return List.of();
        List<ElectricBlockEntity> coils = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos beside = pos.relative(side);
            if (!level.isLoaded(beside)) continue;
            BlockState there = level.getBlockState(beside);
            if (there.getBlock() instanceof CoilBlock && there.getValue(CoilBlock.FACING) == side
                    && level.getBlockEntity(beside) instanceof ElectricBlockEntity coil) coils.add(coil);
        }
        return coils;
    }

    /** Walks and works out the network this point belongs to, without changing anything. Null where it is no point. */
    public static Network solve(ServerLevel level, BlockPos start) {
        if (!(level.getBlockEntity(start) instanceof ElectricBlockEntity first)) return null;
        List<ElectricBlockEntity> nodes = new ArrayList<>();
        Map<BlockPos, Integer> index = new HashMap<>();
        Deque<ElectricBlockEntity> pending = new ArrayDeque<>();
        nodes.add(first);
        index.put(first.getBlockPos(), 0);
        pending.add(first);
        record Edge(int from, int to, Gauge gauge, double length) {
            double ohms() {
                return gauge == null ? JOINT_OHMS : length * gauge.ohmsPerBlock();
            }
        }
        List<Edge> edges = new ArrayList<>();
        while (!pending.isEmpty()) {
            ElectricBlockEntity here = pending.poll();
            int mine = index.get(here.getBlockPos());
            for (ElectricBlockEntity.Wire wire : here.wires()) {
                if (!level.isLoaded(wire.other())
                        || !(level.getBlockEntity(wire.other()) instanceof ElectricBlockEntity there)) continue;
                Integer theirs = index.get(wire.other());
                if (theirs == null) {
                    if (nodes.size() >= MAX_NODES) continue;
                    theirs = nodes.size();
                    nodes.add(there);
                    index.put(there.getBlockPos(), theirs);
                    pending.add(there);
                }
                // Both ends remember the wire; it is counted from the end that comes first.
                if (mine < theirs) {
                    edges.add(new Edge(mine, theirs, wire.gauge(),
                            Math.max(0.5, here.terminal().distanceTo(there.terminal()))));
                }
            }
            for (ElectricBlockEntity there : joints(level, here)) {
                Integer theirs = index.get(there.getBlockPos());
                if (theirs == null) {
                    if (nodes.size() >= MAX_NODES) continue;
                    theirs = nodes.size();
                    nodes.add(there);
                    index.put(there.getBlockPos(), theirs);
                    pending.add(there);
                }
                if (mine < theirs) edges.add(new Edge(mine, theirs, null, 0.0));
            }
        }

        int n = nodes.size();
        double[] volts = new double[n];
        double[] amps = new double[n];
        boolean[] wet = new boolean[n];
        double[] leak = new double[n];
        double[] emfs = new double[n];
        for (int i = 0; i < n; i++) {
            int wetness = wetness(level, nodes.get(i));
            wet[i] = wetness > 0;
            leak[i] = wetness == 2 ? 1.0 / WATER_OHMS : wetness == 1 ? 1.0 / RAIN_OHMS : 0.0;
            emfs[i] = nodes.get(i).emf();
        }
        List<Flow> flows = new ArrayList<>();
        // A source that would have to do what it cannot — a dynamo take current, an empty battery give
        // it, a full one take more — stands aside, and the rest is worked out again without it.
        for (int round = 0; ; round++) {
            boolean driven = false;
            for (double emf : emfs) if (emf > 0.0) driven = true;
            if (!driven) {
                for (Edge edge : edges) flows.add(new Flow(edge.from(), edge.to(), edge.gauge(), edge.length(), 0.0));
                return new Network(nodes, new double[n], amps, wet, flows, 0.0, 0.0, 0.0, 0.0);
            }
            // Kirchhoff at every point: what flows in along the wires and out of a source is what flows to ground.
            double[][] matrix = new double[n][n + 1];
            for (int i = 0; i < n; i++) {
                ElectricBlockEntity node = nodes.get(i);
                matrix[i][i] += node.conductance() + leak[i];
                if (emfs[i] > 0.0) {
                    matrix[i][i] += 1.0 / node.sourceOhms();
                    matrix[i][n] += emfs[i] / node.sourceOhms();
                }
            }
            for (Edge edge : edges) {
                double g = 1.0 / edge.ohms();
                matrix[edge.from()][edge.from()] += g;
                matrix[edge.to()][edge.to()] += g;
                matrix[edge.from()][edge.to()] -= g;
                matrix[edge.to()][edge.from()] -= g;
            }
            eliminate(matrix, volts);
            boolean again = false;
            for (int i = 0; i < n && round < 6; i++) {
                if (emfs[i] <= 0.0) continue;
                ElectricBlockEntity node = nodes.get(i);
                double out = (emfs[i] - volts[i]) / node.sourceOhms();
                if ((out > 1e-6 && !node.canGive()) || (out < -1e-6 && !node.canTake())) {
                    emfs[i] = 0.0;
                    again = true;
                }
            }
            if (!again) break;
        }

        double generated = 0.0, delivered = 0.0, leaked = 0.0, lost = 0.0;
        for (int i = 0; i < n; i++) {
            ElectricBlockEntity node = nodes.get(i);
            double emf = emfs[i];
            if (emf > 0.0) {
                amps[i] = (emf - volts[i]) / node.sourceOhms();
                // What is left of the arithmetic is not a current.
                if (Math.abs(amps[i]) < 1e-9) amps[i] = 0.0;
                // A battery taking current is a consumer for as long as it does.
                if (amps[i] >= 0.0) generated += volts[i] * amps[i];
                else delivered -= volts[i] * amps[i];
            } else {
                amps[i] = volts[i] * node.conductance();
                delivered += volts[i] * amps[i];
            }
            leaked += volts[i] * volts[i] * leak[i];
        }
        for (Edge edge : edges) {
            double ohms = edge.ohms();
            double current = (volts[edge.from()] - volts[edge.to()]) / ohms;
            lost += current * current * ohms;
            flows.add(new Flow(edge.from(), edge.to(), edge.gauge(), edge.length(), current));
        }
        return new Network(nodes, volts, amps, wet, flows, generated, delivered, leaked, lost);
    }

    /** Gaussian elimination with pivoting on an n by n+1 matrix. A point nothing holds to anything stays at nought. */
    private static void eliminate(double[][] m, double[] out) {
        int n = out.length;
        for (int column = 0; column < n; column++) {
            int pivot = column;
            for (int row = column + 1; row < n; row++) {
                if (Math.abs(m[row][column]) > Math.abs(m[pivot][column])) pivot = row;
            }
            if (Math.abs(m[pivot][column]) < 1e-12) continue;
            double[] swap = m[column];
            m[column] = m[pivot];
            m[pivot] = swap;
            for (int row = column + 1; row < n; row++) {
                double factor = m[row][column] / m[column][column];
                if (factor == 0.0) continue;
                for (int k = column; k <= n; k++) m[row][k] -= factor * m[column][k];
            }
        }
        for (int row = n - 1; row >= 0; row--) {
            if (Math.abs(m[row][row]) < 1e-12) {
                out[row] = 0.0;
                continue;
            }
            double sum = m[row][n];
            for (int k = row + 1; k < n; k++) sum -= m[row][k] * out[k];
            out[row] = sum / m[row][row];
        }
    }

    /** Works the network at this point out again because something about it changed. No time passes. */
    public static void update(ServerLevel level, BlockPos pos) {
        Network network = solve(level, pos);
        if (network != null) apply(level, network, 0.0);
    }

    /**
     * The regular beat: works the network out and lets half a second of it happen — wires heat and
     * cool, lamps strain, the careless are shocked. A network with several dynamos is done once.
     */
    public static void pulse(ServerLevel level, BlockPos pos) {
        Stamp stamp = PULSED.get(level);
        if (stamp == null || stamp.time() != level.getGameTime()) {
            stamp = new Stamp(level.getGameTime(), new HashSet<>());
            PULSED.put(level, stamp);
        }
        if (stamp.done().contains(pos)) return;
        Network network = pass(level, pos, PULSE_TICKS / 20.0);
        if (network != null) for (ElectricBlockEntity node : network.nodes()) stamp.done().add(node.getBlockPos());
    }

    /** Works the network out and lets this much time of it happen. The beat does this; tests do too. */
    public static Network pass(ServerLevel level, BlockPos pos, double seconds) {
        Network network = solve(level, pos);
        if (network == null) return null;
        apply(level, network, seconds);
        hazards(level, network, seconds);
        return network;
    }

    private static void apply(ServerLevel level, Network network, double seconds) {
        for (int i = 0; i < network.nodes().size(); i++) {
            network.nodes().get(i).solved(level, network.volts()[i], network.amps()[i], network.wet()[i], seconds);
        }
        if (seconds > 0.0) return;
        // Looked at again without time passing: a wire that no longer carries too much stops glowing at once.
        for (Flow flow : network.flows()) {
            if (flow.gauge() == null || Math.abs(flow.amps()) > flow.gauge().amps()) continue;
            ElectricBlockEntity a = network.nodes().get(flow.from()), b = network.nodes().get(flow.to());
            if (owner(a, b).heat(other(a, b).getBlockPos()) > 1.0f) continue;
            a.setHot(b.getBlockPos(), false);
            b.setHot(a.getBlockPos(), false);
        }
    }

    private static ElectricBlockEntity owner(ElectricBlockEntity a, ElectricBlockEntity b) {
        return a.getBlockPos().asLong() < b.getBlockPos().asLong() ? a : b;
    }

    private static ElectricBlockEntity other(ElectricBlockEntity a, ElectricBlockEntity b) {
        return owner(a, b) == a ? b : a;
    }

    /**
     * How much hotter or cooler a wire gets in this time. Heat goes with the square of the current: at
     * a quarter over its rating a wire lasts a quarter of a minute, at twice its rating three seconds.
     */
    static float cook(float heat, double amps, Gauge gauge, double seconds) {
        double ratio = Math.abs(amps) / gauge.amps();
        return ratio > 1.0 ? heat + (float) ((ratio * ratio - 1.0) * seconds) : Math.max(0.0f, heat - (float) seconds);
    }

    private static void hazards(ServerLevel level, Network network, double seconds) {
        List<BlockPos[]> parted = new ArrayList<>();
        for (Flow flow : network.flows()) {
            if (flow.gauge() == null) continue;
            ElectricBlockEntity a = network.nodes().get(flow.from()), b = network.nodes().get(flow.to());
            ElectricBlockEntity owner = owner(a, b), far = other(a, b);
            float heat = cook(owner.heat(far.getBlockPos()), flow.amps(), flow.gauge(), seconds);
            owner.setHeat(far.getBlockPos(), heat);
            boolean hot = Math.abs(flow.amps()) > flow.gauge().amps() || heat > 1.0f;
            a.setHot(b.getBlockPos(), hot);
            b.setHot(a.getBlockPos(), hot);
            if (heat >= BURN_HEAT) {
                burnThrough(level, a.terminal(), b.terminal());
                parted.add(new BlockPos[] {a.getBlockPos(), b.getBlockPos()});
                continue;
            }
            if (hot && level.getRandom().nextInt(3) == 0) {
                Vec3 at = along(a.terminal(), b.terminal(), level.getRandom().nextDouble());
                level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            double live = Math.max(Math.abs(network.volts()[flow.from()]), Math.abs(network.volts()[flow.to()]));
            if (live >= SHOCK_VOLTS) shockAlong(level, a.terminal(), b.terminal(), live);
        }
        for (int i = 0; i < network.nodes().size(); i++) {
            if (!network.wet()[i] || Math.abs(network.volts()[i]) < 1.0) continue;
            ElectricBlockEntity node = network.nodes().get(i);
            Vec3 at = node.terminal();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.05);
            if (wetness(level, node) != 2 || Math.abs(network.volts()[i]) < SHOCK_VOLTS) continue;
            AABB reach = new AABB(node.getBlockPos()).inflate(WATER_REACH);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, reach, LivingEntity::isInWater)) {
                shock(level, entity, Math.abs(network.volts()[i]));
            }
        }
        // Last, because a wire gone makes two networks of one.
        for (BlockPos[] ends : parted) disconnect(level, ends[0], ends[1]);
    }

    private static void shockAlong(ServerLevel level, Vec3 from, Vec3 to, double volts) {
        AABB box = new AABB(from, to).expandTowards(0.0, -sag(from.distanceTo(to)), 0.0).inflate(0.4);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive);
        if (near.isEmpty()) return;
        int points = Math.max(SEGMENTS, (int) Math.ceil(from.distanceTo(to) * 3));
        for (LivingEntity entity : near) {
            AABB body = entity.getBoundingBox().inflate(0.15);
            for (int i = 0; i <= points; i++) {
                if (body.contains(along(from, to, i / (double) points))) {
                    shock(level, entity, volts);
                    break;
                }
            }
        }
    }

    /** What a shock of this voltage takes, in half hearts: nothing below {@link #SHOCK_VOLTS}, three at sixty volts, six at 120. */
    public static float shockDamage(double volts) {
        return volts < SHOCK_VOLTS ? 0.0f : (float) Math.min(20.0, volts / 20.0);
    }

    /** Shocks something. True where it was hurt. */
    public static boolean shock(ServerLevel level, LivingEntity entity, double volts) {
        float damage = shockDamage(volts);
        if (damage <= 0.0f) return false;
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ModDamageTypes.ELECTROCUTION));
        if (!entity.hurtServer(level, source, damage)) return false;
        // The muscles lock: it is hard to get away from what is hurting.
        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, false, false));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY(0.6), entity.getZ(),
                8, 0.25, 0.4, 0.25, 0.1);
        level.playSound(null, entity.blockPosition(), SoundEvents.BEE_STING, SoundSource.BLOCKS, 0.9f, 0.5f);
        return true;
    }

    /** What a wire parting looks like, and what it does to the ground under it. */
    private static void burnThrough(ServerLevel level, Vec3 from, Vec3 to) {
        for (int i = 0; i <= SEGMENTS; i++) {
            Vec3 at = along(from, to, i / (double) SEGMENTS);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        Vec3 middle = along(from, to, 0.5);
        level.playSound(null, BlockPos.containing(middle), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.7f);
        // What drips from a burning wire lands on whatever lies under it.
        BlockPos.MutableBlockPos ground = BlockPos.containing(middle).mutable();
        for (int fall = 0; fall < 16 && level.getBlockState(ground).isAir(); fall++) ground.move(Direction.DOWN);
        BlockPos above = ground.above();
        BlockState fire = BaseFireBlock.getState(level, above);
        if (!level.getBlockState(ground).isAir() && level.getBlockState(above).isAir() && fire.canSurvive(level, above)) {
            level.setBlockAndUpdate(above, fire);
        }
    }

    // --- reading it ---

    /** What a meter held to this point says. */
    public static Component describe(ServerLevel level, BlockPos pos) {
        Network network = solve(level, pos);
        if (network == null) return Component.empty();
        int here = network.indexOf(pos);
        ElectricBlockEntity node = network.nodes().get(here);
        if (node instanceof DynamoBlockEntity dynamo && dynamo.tripped()) {
            return Component.translatable("message.hardwrought.meter.tripped");
        }
        Component note = node.note();
        if (network.generated() <= 0.0 && Math.abs(network.volts()[here]) < 0.05) {
            Component dead = Component.translatable("message.hardwrought.meter.dead");
            return note == null ? dead : dead.copy().append(" · ").append(note);
        }
        // The hardest-worked wire on this point, as a share of what it carries for good.
        double load = 0.0;
        for (Flow flow : network.flows()) {
            if (flow.gauge() != null && (flow.from() == here || flow.to() == here)) {
                load = Math.max(load, Math.abs(flow.amps()) / flow.gauge().amps());
            }
        }
        double wasted = network.generated() <= 0.0 ? 0.0
                : (network.lost() + network.leaked()) / network.generated() * 100.0;
        Component reading = Component.translatable("message.hardwrought.meter.reading",
                number(network.volts()[here]), number(Math.abs(network.amps()[here])),
                String.format(Locale.ROOT, "%.0f", load * 100.0), number(network.generated()),
                String.format(Locale.ROOT, "%.0f", wasted));
        if (note != null) reading = reading.copy().append(" · ").append(note);
        return network.wet()[here] ? reading.copy().append(Component.translatable("message.hardwrought.meter.wet")) : reading;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, Math.abs(value) < 10.0 ? "%.1f" : "%.0f", value);
    }
}
