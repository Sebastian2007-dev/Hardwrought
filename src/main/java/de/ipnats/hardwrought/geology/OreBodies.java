package de.ipnats.hardwrought.geology;

import com.mojang.serialization.MapCodec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The rich ground of Milestone 6 made into ore you can see. A body used to be only a number — more
 * drops from the ordinary veins that happened to lie in it — and a tunnel through the richest core
 * looked like a tunnel through bare rock. Now every body is threaded with veins of its ore: winding
 * strands, thick and close together in the middle where the grade is highest, thinning out towards the
 * rim. Prospect it, dig to it, and the walls glitter; follow a strand and it leads on.
 *
 * <p>A strand is where two smooth noise fields both pass near zero: the crossing of two curved sheets is a
 * curved line, which is how a real vein lies in its host rock. How near zero counts grows with the grade,
 * so the strands thicken and crowd towards the core.
 *
 * <p>Like the rest of the geology this is a pure function of the seed and the position, and it writes
 * only into the chunk being generated, so neighbouring chunks agree about where a strand crosses between
 * them without either of them looking at the other.
 */
public final class OreBodies {
    /** How thick a strand is at the rim of a body, and how much thicker at its core. */
    static final double RIM_WIDTH = 0.035;
    static final double CORE_WIDTH = 0.105;
    /** Strands wind slowly sideways and climb more steeply: a vein lies in its seam. */
    private static final double SIDEWAYS = 1.0 / 22.0;
    private static final double UPWARDS = 1.0 / 14.0;

    static final MapCodec<Veins> TYPE = Registry.register(BuiltInRegistries.FEATURE_TYPE,
            Hardwrought.id("ore_bodies"), MapCodec.unit(Veins.INSTANCE));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE,
            Hardwrought.id("ore_bodies"));

    private static boolean initialized;

    private OreBodies() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        BiomeModifications.create(Hardwrought.id("ore_bodies")).add(ModificationPhase.ADDITIONS,
                BiomeSelectors.foundInOverworld(), context ->
                        context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, PLACED));
    }

    /** The two fields one ore's strands are drawn from. Seeded per ore, so two bodies that overlap do not share strands. */
    public static Field[] fields(long seed, Identifier ore) {
        long salt = seed ^ (ore.toString().hashCode() * 0x9E3779B97F4A7C15L);
        return new Field[] {new Field(salt),
                new Field(salt * 31 + 17)};
    }

    /** Whether the rock at this position of a body is ore: on a strand, as thick as the grade there makes it. */
    public static boolean veinAt(Field[] fields, OreDeposit body, int x, int y, int z) {
        double reach = body.reach(x, y, z);
        if (reach > 1.0) return false;
        double richness = 1.0 - (1.0 - OreDeposit.RIM_SHARE) * reach;
        double width = RIM_WIDTH + (CORE_WIDTH - RIM_WIDTH) * (richness - OreDeposit.RIM_SHARE) / (1.0 - OreDeposit.RIM_SHARE);
        double first = fields[0].noise(x * SIDEWAYS, y * UPWARDS, z * SIDEWAYS);
        if (Math.abs(first) >= width) return false;
        return Math.abs(fields[1].noise(x * SIDEWAYS, y * UPWARDS, z * SIDEWAYS)) < width;
    }

    /** The deepslate form of an ore: deepslate_ before its name, as vanilla and this mod both name them. */
    static Block deepslateOf(Block ore) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(ore);
        Identifier deep = Identifier.fromNamespaceAndPath(id.getNamespace(), "deepslate_" + id.getPath());
        return BuiltInRegistries.BLOCK.getOptional(deep).orElse(ore);
    }

    /** Lays the strands of every body that reaches into the chunk being generated, inside that chunk alone. */
    public record Veins() implements Feature {
        static final Veins INSTANCE = new Veins();

        @Override
        public MapCodec<Veins> codec() {
            return TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            long seed = level.getSeed();
            Map<Identifier, RockProfile> profiles = Geology.profiles(level.getLevel().getServer());
            if (profiles.isEmpty()) return false;
            int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4;
            int z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
            boolean placed = false;
            for (OreDeposit body : reaching(seed, profiles, x0, z0)) {
                Block ore = BuiltInRegistries.BLOCK.getOptional(body.ore()).orElse(null);
                if (ore == null || ore == Blocks.AIR) continue;
                placed |= lay(level, body, ore, deepslateOf(ore), fields(seed, body.ore()), x0, z0);
            }
            return placed;
        }

        private static boolean lay(WorldGenLevel level, OreDeposit body, Block ore, Block deepOre,
                                   Field[] fields, int x0, int z0) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockState stoneOre = ore.defaultBlockState();
            BlockState deepslateOre = deepOre.defaultBlockState();
            int minY = Math.max(level.getMinY(), body.centre().getY() - body.verticalRadius());
            int maxY = Math.min(level.getMaxY(), body.centre().getY() + body.verticalRadius());
            boolean placed = false;
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) {
                    double dx = (x - body.centre().getX()) / (double) body.horizontalRadius();
                    double dz = (z - body.centre().getZ()) / (double) body.horizontalRadius();
                    if (dx * dx + dz * dz > 1.0) continue;
                    for (int y = minY; y <= maxY; y++) {
                        if (!veinAt(fields, body, x, y, z)) continue;
                        pos.set(x, y, z);
                        BlockState rock = level.getBlockState(pos);
                        if (rock.is(BlockTags.STONE_ORE_REPLACEABLES)) {
                            level.setBlock(pos, stoneOre, Block.UPDATE_CLIENTS);
                            placed = true;
                        } else if (rock.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                            level.setBlock(pos, deepslateOre, Block.UPDATE_CLIENTS);
                            placed = true;
                        }
                    }
                }
            }
            return placed;
        }
    }

    /**
     * One smooth field the strands are drawn from: classic gradient noise, about −1 to 1, zero on every
     * whole lattice point. Written out here rather than borrowed, so that what the world generates does not
     * move when the game's own noise classes change shape between versions.
     */
    public static final class Field {
        private final int[] permutation = new int[512];

        public Field(long seed) {
            int[] base = new int[256];
            for (int i = 0; i < 256; i++) base[i] = i;
            java.util.SplittableRandom random = new java.util.SplittableRandom(seed);
            for (int i = 255; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = base[i];
                base[i] = base[j];
                base[j] = swap;
            }
            for (int i = 0; i < 512; i++) permutation[i] = base[i & 255];
        }

        public double noise(double x, double y, double z) {
            int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
            double xf = x - xi, yf = y - yi, zf = z - zi;
            int X = xi & 255, Y = yi & 255, Z = zi & 255;
            double u = fade(xf), v = fade(yf), w = fade(zf);
            int a = permutation[X] + Y, aa = permutation[a] + Z, ab = permutation[a + 1] + Z;
            int b = permutation[X + 1] + Y, ba = permutation[b] + Z, bb = permutation[b + 1] + Z;
            return lerp(w,
                    lerp(v, lerp(u, grad(permutation[aa], xf, yf, zf), grad(permutation[ba], xf - 1, yf, zf)),
                            lerp(u, grad(permutation[ab], xf, yf - 1, zf), grad(permutation[bb], xf - 1, yf - 1, zf))),
                    lerp(v, lerp(u, grad(permutation[aa + 1], xf, yf, zf - 1), grad(permutation[ba + 1], xf - 1, yf, zf - 1)),
                            lerp(u, grad(permutation[ab + 1], xf, yf - 1, zf - 1), grad(permutation[bb + 1], xf - 1, yf - 1, zf - 1))));
        }

        private static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        private static double grad(int hash, double x, double y, double z) {
            int h = hash & 15;
            double u = h < 8 ? x : y;
            double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
    }

    /** Every body of the regions around a chunk whose footprint reaches into it. */
    static List<OreDeposit> reaching(long seed, Map<Identifier, RockProfile> profiles, int x0, int z0) {
        List<OreDeposit> found = new ArrayList<>();
        int regionX = Geology.regionX(x0);
        int regionZ = Geology.regionZ(z0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (OreDeposit body : Geology.deposits(seed, profiles, regionX + dx, regionZ + dz)) {
                    int r = body.horizontalRadius();
                    if (body.centre().getX() + r < x0 || body.centre().getX() - r > x0 + 15) continue;
                    if (body.centre().getZ() + r < z0 || body.centre().getZ() - r > z0 + 15) continue;
                    found.add(body);
                }
            }
        }
        return found;
    }
}
