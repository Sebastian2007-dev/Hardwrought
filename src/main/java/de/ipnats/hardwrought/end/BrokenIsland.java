package de.ipnats.hardwrought.end;

import com.mojang.serialization.MapCodec;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What is left of the End's main island (endgame § 4): fragments of it hanging in the void at different
 * heights round where it stood, the largest in the middle — flat on top, to carry the exit portal — and
 * stones broken off them drifting further out. The obsidian pillars stand alone in the void between them,
 * each ending below in a jagged point ({@code EndSpikeFeatureMixin} keeps them from reaching down to the
 * bottom of the world).
 *
 * <p>Where the fragments lie follows from the world's seed alone, so every chunk can build its own share
 * of them without looking at its neighbours.
 */
public final class BrokenIsland {
    public static final MapCodec<Arena> TYPE = Registry.register(BuiltInRegistries.FEATURE_TYPE, Hardwrought.id("broken_island"), MapCodec.unit(Arena.INSTANCE));

    /** The middle fragment: its top, its reach, and how far round its middle it lies quite flat. */
    public static final int MIDDLE_TOP = 62, MIDDLE_RADIUS = 18, MIDDLE_FLAT = 9;
    /** How far below a pillar's top it ends, and how long the point under it is. */
    public static final int PILLAR_DEPTH = 34, POINT = 9;
    static final int FRAGMENTS = 18, STONES = 45;
    static final double FARTHEST = 112, STONES_FARTHEST = 140;

    /** One piece of the island: where its top lies, how far it reaches, how deep it hangs. */
    public record Fragment(double x, double z, int top, double radius, double depth, double wobble, double phase, boolean flat) {
        double minX() {
            return x - radius * 1.3;
        }

        double maxX() {
            return x + radius * 1.3;
        }

        double minZ() {
            return z - radius * 1.3;
        }

        double maxZ() {
            return z + radius * 1.3;
        }
    }

    private static final Map<Long, List<Fragment>> LAYOUTS = new ConcurrentHashMap<>();

    private BrokenIsland() { }

    public static void initialize() { }

    /** Every fragment of this world's island, the middle one first. */
    public static List<Fragment> fragments(long seed, List<EndSpikeFeature.EndSpike> pillars) {
        return LAYOUTS.computeIfAbsent(seed, s -> layout(s, pillars));
    }

    static List<Fragment> layout(long seed, List<EndSpikeFeature.EndSpike> pillars) {
        RandomSource random = RandomSource.create(seed ^ 0x5EED_B40C_E11AL);
        List<Fragment> all = new ArrayList<>();
        all.add(new Fragment(0, 0, MIDDLE_TOP, MIDDLE_RADIUS, 24, 0.12, random.nextDouble() * Math.PI * 2, true));
        place(all, random, pillars, FRAGMENTS, 30, FARTHEST, 4.5, 12, 44, 96, 400);
        place(all, random, pillars, STONES, 22, STONES_FARTHEST, 1.2, 3.2, 30, 112, 600);
        return List.copyOf(all);
    }

    private static void place(List<Fragment> all, RandomSource random, List<EndSpikeFeature.EndSpike> pillars, int count,
                              double nearest, double farthest, double smallest, double largest, int lowest, int highest, int tries) {
        int placed = 0;
        for (int attempt = 0; attempt < tries && placed < count; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Mth.lerp(Math.sqrt(random.nextDouble()), nearest, farthest);
            double x = Math.cos(angle) * distance, z = Math.sin(angle) * distance;
            // The larger near the middle, the smaller further out, as if blown apart from it.
            double out = (distance - nearest) / (farthest - nearest);
            double radius = Mth.lerp(random.nextDouble() * (1 - 0.5 * out), smallest, largest);
            int top = lowest + random.nextInt(highest - lowest + 1);
            if (!clear(all, pillars, x, z, top, radius)) continue;
            double depth = radius * (1.2 + random.nextDouble() * 0.9) + 2;
            all.add(new Fragment(x, z, top, radius, depth, 0.1 + random.nextDouble() * 0.15, random.nextDouble() * Math.PI * 2, false));
            placed++;
        }
    }

    /** Whether a fragment here keeps clear of the pillars, of the others, and of where travellers arrive. */
    private static boolean clear(List<Fragment> all, List<EndSpikeFeature.EndSpike> pillars, double x, double z, int top, double radius) {
        for (EndSpikeFeature.EndSpike pillar : pillars) {
            if (Math.hypot(pillar.getCenterX() - x, pillar.getCenterZ() - z) < radius * 1.3 + pillar.getRadius() + 6) return false;
        }
        for (Fragment other : all) {
            double apart = Math.hypot(other.x() - x, other.z() - z);
            boolean stacked = Math.abs(other.top() - top) > other.depth() + 8;
            if (apart < (radius + other.radius()) * 1.3 + 5 && !stacked) return false;
        }
        return Math.hypot(EndRules.ARRIVAL.x - x, EndRules.ARRIVAL.z - z) > radius + 24;
    }

    record Arena() implements Feature {
        static final Arena INSTANCE = new Arena();

        @Override
        public MapCodec<Arena> codec() {
            return TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            int cx = SectionPos.blockToSectionCoord(origin.getX()), cz = SectionPos.blockToSectionCoord(origin.getZ());
            List<EndSpikeFeature.EndSpike> pillars = EndSpikeFeature.getSpikesForLevel(level);
            build(level, level.getSeed(), pillars, cx, cz);
            return true;
        }
    }

    /** This chunk's share of the island: the fragments over it, and the points under the pillars whose middle it holds. */
    public static void build(WorldGenLevel level, long seed, List<EndSpikeFeature.EndSpike> pillars, int cx, int cz) {
        int x0 = cx << 4, z0 = cz << 4;
        List<Fragment> here = new ArrayList<>();
        for (Fragment f : fragments(seed, pillars)) {
            if (f.maxX() >= x0 && f.minX() < x0 + 16 && f.maxZ() >= z0 && f.minZ() < z0 + 16) here.add(f);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Fragment f : here) {
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) column(level, seed, f, x, z, pos);
            }
        }
        for (EndSpikeFeature.EndSpike pillar : pillars) {
            if (pillar.getCenterX() >> 4 == cx && pillar.getCenterZ() >> 4 == cz) point(level, seed, pillar, pos);
        }
    }

    private static void column(WorldGenLevel level, long seed, Fragment f, int x, int z, BlockPos.MutableBlockPos pos) {
        double dx = x + 0.5 - f.x(), dz = z + 0.5 - f.z();
        double angle = Math.atan2(dz, dx);
        // A broken edge: not round, but bitten into here and there.
        double edge = f.radius() * (1 + f.wobble() * Math.sin(3 * angle + f.phase()) + f.wobble() * 0.6 * Math.sin(7 * angle + f.phase() * 2)
                + 0.08 * (hash(seed, x, z, 1) - 0.5));
        double t = Math.sqrt(dx * dx + dz * dz) / edge;
        if (t >= 1) return;
        int top = f.top();
        if (!f.flat() || t > MIDDLE_FLAT / f.radius()) {
            top += (int) Math.round((hash(seed, x >> 1, z >> 1, 2) - 0.5) * (f.radius() > 6 ? 2.4 : 1.2) - (t > 0.8 ? (t - 0.8) * 6 : 0));
        }
        // Hanging deepest under its middle, in jagged points.
        double depth = f.depth() * Math.pow(1 - t, 0.75) * (0.75 + 0.5 * hash(seed, x, z, 3)) + 1;
        if (t > 0.86 && hash(seed, x, z, 4) < 0.35) top -= 1;
        int bottom = (int) Math.floor(top - depth);
        for (int y = bottom; y <= top; y++) {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir()) continue;
            level.setBlock(pos, stone(seed, x, y, z, top - y, y - bottom), Block.UPDATE_CLIENTS);
        }
    }

    /** End stone, with obsidian where it was fused and broke, deeper down and towards the points. */
    private static BlockState stone(long seed, int x, int y, int z, int below, int above) {
        if (below > 3 && above < 3 && hash(seed, x, y * 31 + z, 5) < 0.18) return Blocks.OBSIDIAN.defaultBlockState();
        if (below > 6 && hash(seed, x * 7 + y, z, 6) < 0.025) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        return Blocks.END_STONE.defaultBlockState();
    }

    /** The broken end under a pillar: obsidian tapering to a jagged point. */
    private static void point(WorldGenLevel level, long seed, EndSpikeFeature.EndSpike pillar, BlockPos.MutableBlockPos pos) {
        int base = pillar.getHeight() - PILLAR_DEPTH;
        int r = pillar.getRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5) continue;
                int x = pillar.getCenterX() + dx, z = pillar.getCenterZ() + dz;
                int length = (int) Math.round(POINT * (1 - d / (r + 1)) * (0.6 + 0.6 * hash(seed, x, z, 7)));
                for (int y = base - 1; y >= base - length; y--) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** A number from 0 to 1 that depends only on the seed, a place and a purpose. */
    static double hash(long seed, int x, int z, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + z * 0x94D049BB133111EBL + salt * 0x2545F4914F6CDD1DL;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        h *= 0x81DADEF4BC2DD44DL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
