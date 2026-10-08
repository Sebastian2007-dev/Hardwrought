package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * The ruins rune knowledge is found in (magic specification section 4.1): a broken ring of old
 * stone around a plinth, and on the plinth a stone with one rune cut into it — element or
 * auxiliary. Scattered thinly over the overworld, so finding all twelve takes a journey.
 *
 * <p>Temples, libraries and halls are left for a later milestone; this is the smallest structure
 * that lets a player learn magic by exploring at all.
 */
public final class RuneRuins {
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE,
            Hardwrought.id("rune_ruin"));
    static final com.mojang.serialization.MapCodec<Ruin> TYPE = Registry.register(BuiltInRegistries.FEATURE_TYPE,
            Hardwrought.id("rune_ruin"), com.mojang.serialization.MapCodec.unit(Ruin.INSTANCE));

    private RuneRuins() { }

    static void initialize() {
        BiomeModifications.create(Hardwrought.id("rune_ruins")).add(ModificationPhase.ADDITIONS,
                BiomeSelectors.foundInOverworld(),
                context -> context.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, PLACED));
    }

    /** Builds one ruin at a spot; also used by tests and the debug command. */
    public static boolean build(WorldGenLevel level, BlockPos ground, RandomSource random, Glyph glyph) {
        BlockState below = level.getBlockState(ground.below());
        if (!below.isSolidRender() || !level.getFluidState(ground).isEmpty()) return false;
        // The ring: a five by five square of old stone, with stones missing and some fallen lower.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                if (random.nextFloat() < 0.3f) continue;
                int height = 1 + random.nextInt((dx + dz) % 2 == 0 ? 3 : 2);
                BlockPos base = ground.offset(dx, 0, dz);
                for (int y = 0; y < height; y++) level.setBlock(base.above(y), oldStone(random), 2);
                fill(level, base.below(), random);
            }
        }
        // The plinth, and the rune stone on it, its carving turned to a random side.
        level.setBlock(ground, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        fill(level, ground.below(), random);
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        level.setBlock(ground.above(), Magic.RUNE_STONE.defaultBlockState()
                .setValue(RuneStoneBlock.FACING, facing).setValue(RuneStoneBlock.GLYPH, glyph), 2);
        return true;
    }

    private static BlockState oldStone(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.4f) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (roll < 0.7f) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (roll < 0.85f) return Blocks.STONE_BRICKS.defaultBlockState();
        return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    /** Fills the gap under a stone down to the ground, so the ruin does not float on a slope. */
    private static void fill(WorldGenLevel level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 4 && !level.getBlockState(pos).isSolidRender(); i++) {
            level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
            pos = pos.below();
        }
    }

    /** The feature as world generation sees it; it has nothing to configure. */
    record Ruin() implements Feature {
        static final Ruin INSTANCE = new Ruin();

        @Override
        public com.mojang.serialization.MapCodec<Ruin> codec() {
            return TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin);
            Glyph glyph = Glyph.values()[random.nextInt(Glyph.values().length)];
            return build(level, ground, random, glyph);
        }
    }
}
