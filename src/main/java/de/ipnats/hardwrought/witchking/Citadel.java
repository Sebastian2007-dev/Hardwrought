package de.ipnats.hardwrought.witchking;

import com.mojang.serialization.MapCodec;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Witch King's citadel: a great round hall cut out of the rock at the bottom of the Ossuary. A domed
 * roof of blackstone, a ring wall with buttresses and soul lanterns, eight pillars, a floor inlaid with
 * his sigil in gold and crying obsidian, four pedestals for his crystals, and at its head his throne on
 * a dais between two soul fires. Four passages lead in.
 *
 * <p>At most one to every 256 by 256 blocks: only the chunk at a fixed place in each such square may hold
 * one, and only if the Ossuary is there.
 */
public final class Citadel {
    public static final int RADIUS = 18, WALL = 20, HEIGHT = 22, THRONE_DISTANCE = 14;
    static final int GRID = 16, GRID_AT = 8;
    static final MapCodec<Hall> TYPE = Registry.register(BuiltInRegistries.FEATURE_TYPE, Hardwrought.id("witch_king_citadel"), MapCodec.unit(Hall.INSTANCE));

    private Citadel() { }

    static void initialize() { }

    /** Whether this chunk is the one in its square that may hold a citadel. */
    public static boolean candidate(int chunkX, int chunkZ) {
        return Math.floorMod(chunkX, GRID) == GRID_AT && Math.floorMod(chunkZ, GRID) == GRID_AT;
    }

    /**
     * Where the middle of the nearest citadel's hall lies, from a position, searching outwards square by
     * square of the grid; null when none lies within the given number of squares. Asks the biome source
     * itself, so it finds them in chunks not yet generated too.
     */
    public static BlockPos nearest(net.minecraft.server.level.ServerLevel level, BlockPos from, int squares) {
        int gx = Math.floorDiv(SectionPos.blockToSectionCoord(from.getX()) - GRID_AT, GRID);
        int gz = Math.floorDiv(SectionPos.blockToSectionCoord(from.getZ()) - GRID_AT, GRID);
        BlockPos best = null;
        for (int ring = 0; ring <= squares && best == null; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = ((gx + dx) * GRID + GRID_AT) * 16 + 8, z = ((gz + dz) * GRID + GRID_AT) * 16 + 8;
                    Integer floor = floor(y -> level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey()
                            .map(k -> k.identifier().getPath().equals("ossuary")).orElse(false), level.getMinY(), level.getMaxY());
                    if (floor == null) continue;
                    BlockPos at = new BlockPos(x, floor, z);
                    if (best == null || at.distSqr(from) < best.distSqr(from)) best = at;
                }
            }
        }
        return best;
    }

    /** The lowest height at which the Ossuary is there, sampled every four blocks; null where it is not. */
    static Integer floor(java.util.function.IntPredicate ossuary, int minY, int maxY) {
        for (int y = minY + 10; y < maxY - HEIGHT - 8; y += 4) {
            if (ossuary.test(y)) return y;
        }
        return null;
    }

    record Hall() implements Feature {
        static final Hall INSTANCE = new Hall();

        @Override
        public MapCodec<Hall> codec() {
            return TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            int cx = SectionPos.blockToSectionCoord(origin.getX()), cz = SectionPos.blockToSectionCoord(origin.getZ());
            if (!candidate(cx, cz)) return false;
            int x = (cx << 4) + 8, z = (cz << 4) + 8;
            // The lowest height at which the Ossuary is there: the bottom of the bones.
            Integer floor = floor(y -> level.getBiome(new BlockPos(x, y, z)).unwrapKey().map(k -> k.identifier().getPath().equals("ossuary"))
                    .orElse(false), level.getMinY(), level.getMaxY());
            if (floor == null) return false;
            build(level, new BlockPos(x, floor, z), random);
            return true;
        }
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** The hall around its middle, at floor height; public for tests. */
    public static void build(WorldGenLevel level, BlockPos home, RandomSource random) {
        BlockState[] walls = {Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()};
        for (int dx = -WALL - 1; dx <= WALL + 1; dx++) {
            for (int dz = -WALL - 1; dz <= WALL + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > WALL + 0.5) continue;
                int roof = roof(d);
                for (int dy = -2; dy <= roof + 1; dy++) {
                    BlockPos at = home.offset(dx, dy, dz);
                    if (d <= RADIUS) {
                        if (dy == -2) set(level, at, Blocks.DEEPSLATE_TILES.defaultBlockState());
                        else if (dy == -1) set(level, at, floor(dx, dz, d));
                        else if (dy < roof) set(level, at, Blocks.AIR.defaultBlockState());
                        else set(level, at, dy == roof ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                    } else {
                        // The ring wall, buttressed every thirty degrees.
                        double angle = Math.toDegrees(Math.atan2(dz, dx));
                        boolean buttress = Math.abs(((angle % 30) + 30) % 30 - 15) > 13;
                        set(level, at, buttress ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : walls[random.nextInt(walls.length)]);
                    }
                }
            }
        }
        // Soul lanterns round the inside of the wall.
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6 + Math.PI / 12;
            BlockPos at = home.offset((int) Math.round(Math.cos(angle) * (RADIUS - 0.5)), 6, (int) Math.round(Math.sin(angle) * (RADIUS - 0.5)));
            set(level, at, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // Eight pillars up to the roof, lanterns hanging from them.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + Math.PI / 8;
            int px = (int) Math.round(Math.cos(angle) * 14), pz = (int) Math.round(Math.sin(angle) * 14);
            int roof = roof(Math.sqrt(px * px + pz * pz));
            for (int dy = 0; dy < roof; dy++) {
                set(level, home.offset(px, dy, pz), dy == 0 || dy == roof - 1 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : Blocks.POLISHED_BASALT.defaultBlockState());
            }
            BlockPos lantern = home.offset(px, 9, pz).relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
            if (Math.sqrt(Math.pow(lantern.getX() - home.getX(), 2) + Math.pow(lantern.getZ() - home.getZ(), 2)) < RADIUS) {
                set(level, lantern.above(), Blocks.IRON_CHAIN.defaultBlockState());
                set(level, lantern, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        // The pedestals of the crystals.
        for (BlockPos spot : WitchKing.CRYSTAL_SPOTS) {
            BlockPos base = home.offset(spot.getX(), 0, spot.getZ());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) set(level, base.offset(dx, -1, dz), Blocks.GILDED_BLACKSTONE.defaultBlockState());
            }
            set(level, base, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        }
        // The dais and the throne at the head of the hall, between two soul fires.
        BlockPos dais = home.offset(0, 0, -THRONE_DISTANCE);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -1; dz <= 1; dz++) set(level, dais.offset(dx, 0, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            set(level, dais.offset(dx, 0, 2), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        }
        set(level, dais.above(), WitchKingContent.THRONE.defaultBlockState());
        for (int side : new int[] {-3, 3}) {
            set(level, dais.offset(side, 1, 0), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        }
        // Four passages in.
        for (Direction way : Direction.Plane.HORIZONTAL) {
            for (int out = RADIUS - 1; out <= WALL + 3; out++) {
                for (int side = -1; side <= 1; side++) {
                    BlockPos at = home.relative(way, out).relative(way.getClockWise(), side);
                    set(level, at.below(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    for (int dy = 0; dy < 4; dy++) set(level, at.above(dy), Blocks.AIR.defaultBlockState());
                    set(level, at.above(4), Blocks.GILDED_BLACKSTONE.defaultBlockState());
                }
            }
        }
    }

    /** How high the roof stands at this distance from the middle: a shallow dome. */
    static int roof(double d) {
        return HEIGHT - (int) Math.round(Math.pow(Math.min(d, RADIUS) / RADIUS, 2) * 7);
    }

    /** The floor: blackstone, his sigil inlaid in it. */
    private static BlockState floor(int dx, int dz, double d) {
        double angle = Math.atan2(dz, dx);
        boolean ring = Math.abs(d - 9) < 0.5 || Math.abs(d - 16) < 0.5;
        boolean inner = Math.abs(d - 4) < 0.5;
        // Seven spokes between the rings.
        double spoke = Math.abs(((angle / (Math.PI * 2 / 7)) % 1 + 1) % 1 - 0.5);
        boolean spokes = d > 4 && d < 16 && spoke > 0.5 - 0.35 / Math.max(d, 1);
        if (ring) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
        if (inner || spokes) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        if (d < 3) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        return ((dx + dz) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    }
}
