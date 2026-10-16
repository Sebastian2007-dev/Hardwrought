package de.ipnats.hardwrought.underworld;

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
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * What makes the Graveyard a graveyard, and the Ossuary a charnel house.
 *
 * <ul>
 *   <li><b>Graves</b>, many to a chunk: a sunken mound of soil with a headstone at its head — a broken wall
 *   piece, a cracked block, or a stone fallen flat beside it — now and then a candle long gone out on it,
 *   bones coming up through the soil.</li>
 *   <li><b>Fences fallen to pieces</b>: lines of iron bars with gaps where they gave way.</li>
 *   <li><b>Rotting wood</b>: old trunks lying in the soil, mushrooms on them.</li>
 *   <li>In the <b>Ossuary</b>: skulls heaped on the floor and bones stacked into pillars to the roof.</li>
 * </ul>
 */
public final class Graveyard {
    static final MapCodec<Graves> GRAVES_TYPE = feature("graves", Graves.INSTANCE);
    static final MapCodec<Bones> BONES_TYPE = feature("ossuary_bones", Bones.INSTANCE);

    private Graveyard() { }

    /** Loading the class registers the features. */
    public static void initialize() { }

    private static <F extends Feature> MapCodec<F> feature(String name, F instance) {
        return Registry.register(BuiltInRegistries.FEATURE_TYPE, Hardwrought.id(name), MapCodec.unit(instance));
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** A cave floor in this chunk: open over solid ground, or null after a few tries. */
    static BlockPos floor(WorldGenLevel level, int x0, int z0, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 10; attempt++) {
            pos.set(x0 + 1 + random.nextInt(14), level.getMinY() + 10 + random.nextInt(level.getHeight() - 30), z0 + 1 + random.nextInt(14));
            if (!level.getBlockState(pos).isAir()) continue;
            while (pos.getY() > level.getMinY() + 8 && level.getBlockState(pos.below()).isAir()) pos.move(Direction.DOWN);
            if (level.getBlockState(pos.below()).isSolidRender() && level.getBlockState(pos.above()).isAir()) return pos.immutable();
        }
        return null;
    }

    private static boolean open(WorldGenLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.below()).isSolidRender();
    }

    /** Graves, fences and rotting wood over the chunk's cave floors, only where the biome is the Graveyard. */
    public record Graves() implements Feature {
        static final Graves INSTANCE = new Graves();
        static final String BIOME = "graveyard";

        @Override
        public MapCodec<Graves> codec() {
            return GRAVES_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4, z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
            boolean placed = false;
            for (int i = 0; i < 9; i++) {
                BlockPos at = floor(level, x0, z0, random);
                if (at == null || !level.getBiome(at).unwrapKey().map(k -> k.identifier().getPath().equals(BIOME)).orElse(false)) continue;
                int kind = random.nextInt(10);
                if (kind < 7) grave(level, at, random);
                else if (kind < 9) fence(level, at, random);
                else log(level, at, random);
                placed = true;
            }
            return placed;
        }

        /** A mound with a headstone at its head. */
        public static void grave(WorldGenLevel level, BlockPos head, RandomSource random) {
            Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos foot = head.relative(along);
            if (!open(level, foot)) return;
            BlockState[] soils = {Blocks.SOUL_SOIL.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState(),
                    Blocks.PODZOL.defaultBlockState()};
            BlockState soil = soils[random.nextInt(soils.length)];
            set(level, head.below(), soil);
            set(level, foot.below(), random.nextInt(5) == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : soil);
            BlockPos stone = head.relative(along.getOpposite());
            if (!open(level, stone)) stone = head;
            switch (random.nextInt(6)) {
                case 0 -> set(level, stone, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
                case 1 -> set(level, stone, Blocks.STONE_BRICK_WALL.defaultBlockState());
                case 2 -> set(level, stone, Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState());
                case 3 -> set(level, stone, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
                case 4 -> set(level, stone, Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
                // Fallen flat beside the grave.
                default -> {
                    BlockPos beside = head.relative(along.getClockWise());
                    if (open(level, beside)) set(level, beside, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                }
            }
            if (random.nextInt(4) == 0 && level.getBlockState(foot).isAir()) {
                set(level, foot, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3)).setValue(CandleBlock.LIT, false));
            } else if (random.nextInt(8) == 0 && level.getBlockState(foot).isAir()) {
                set(level, foot, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)));
            }
        }

        /** A line of iron bars, gaps where it gave way. */
        public static void fence(WorldGenLevel level, BlockPos start, RandomSource random) {
            Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int length = 3 + random.nextInt(5);
            for (int i = 0; i < length; i++) {
                BlockPos at = start.relative(along, i);
                if (!open(level, at)) break;
                if (random.nextInt(4) == 0) continue;
                set(level, at, Blocks.IRON_BARS.defaultBlockState());
                if (random.nextInt(3) == 0 && level.getBlockState(at.above()).isAir()) set(level, at.above(), Blocks.IRON_BARS.defaultBlockState());
                if (random.nextInt(6) == 0 && level.getBlockState(at.above()).isAir()) set(level, at.above(), Blocks.COBWEB.defaultBlockState());
            }
        }

        /** An old trunk lying in the soil, mushrooms on it. */
        public static void log(WorldGenLevel level, BlockPos start, RandomSource random) {
            Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockState wood = (random.nextBoolean() ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.STRIPPED_SPRUCE_LOG).defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, along.getAxis());
            int length = 2 + random.nextInt(3);
            for (int i = 0; i < length; i++) {
                BlockPos at = start.relative(along, i);
                if (!open(level, at)) break;
                set(level, at, wood);
                if (random.nextInt(3) == 0 && level.getBlockState(at.above()).isAir()) {
                    set(level, at.above(), (random.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState());
                }
            }
        }
    }

    /** In the Ossuary: heaps of skulls on the floor, and bones stacked into pillars up to the roof. */
    record Bones() implements Feature {
        static final Bones INSTANCE = new Bones();

        @Override
        public MapCodec<Bones> codec() {
            return BONES_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4, z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
            boolean placed = false;
            for (int i = 0; i < 6; i++) {
                BlockPos at = floor(level, x0, z0, random);
                if (at == null || !level.getBiome(at).unwrapKey().map(k -> k.identifier().getPath().equals("ossuary")).orElse(false)) continue;
                if (random.nextInt(3) == 0) pillar(level, at, random);
                else heap(level, at, random);
                placed = true;
            }
            return placed;
        }

        static void heap(WorldGenLevel level, BlockPos center, RandomSource random) {
            for (int i = 0; i < 4 + random.nextInt(6); i++) {
                BlockPos at = center.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (!open(level, at)) continue;
                set(level, at, (random.nextInt(12) == 0 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState()
                        .setValue(SkullBlock.ROTATION, random.nextInt(16)));
            }
            if (random.nextBoolean() && open(level, center)) set(level, center, Blocks.BONE_BLOCK.defaultBlockState());
        }

        /** Bone blocks up to the roof, if the roof is near enough; a skull set into it here and there. */
        static void pillar(WorldGenLevel level, BlockPos foot, RandomSource random) {
            int height = 0;
            while (height < 14 && level.getBlockState(foot.above(height)).isAir()) height++;
            if (height >= 14) return;
            for (int y = 0; y < height; y++) {
                BlockPos at = foot.above(y);
                set(level, at, random.nextInt(9) == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
            }
        }
    }
}
