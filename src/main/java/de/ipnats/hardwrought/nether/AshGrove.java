package de.ipnats.hardwrought.nether;

import com.mojang.serialization.MapCodec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.function.Function;

/**
 * The Ash Grove: the Nether's one cool corner, where a start over finds water and wood (see
 * docs/endgame.md). Ash drifts through it; the ashcork grows there ({@link Ashcork}).
 *
 * <p>Its netherrack is covered in grey <b>Nether grass</b>, as nylium covers the crimson and warped forests,
 * and there is <b>no lava</b> in it: where the rest of the Nether has its lava sea, the Ash Grove has a
 * lake of boiled water, and no lava springs break from its walls ({@link Ground}).
 *
 * <p><b>Water</b> lies there instead of boiling off at once — the biome turns the Nether's evaporation
 * off — but it is always boiled: whatever is poured, purified water is what stands (biomes tagged
 * {@code hardwrought:boils_water}). Everywhere else in the Nether a bucket hisses empty, as in vanilla.
 */
public final class AshGrove {
    public static final ResourceKey<Biome> KEY = ResourceKey.create(Registries.BIOME, Hardwrought.id("ash_grove"));
    /** Biomes whose standing water is always boiled clean. */
    public static final TagKey<Biome> BOILS_WATER = TagKey.create(Registries.BIOME, Hardwrought.id("boils_water"));
    /** How far an unstable crossing into the Nether looks for an Ash Grove to set its traveller down in. */
    static final int LANDING_SEARCH = 640;

    public static final Block NETHER_GRASS = register("nether_grass", NetherGrassBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(0.4f).sound(SoundType.NYLIUM).randomTicks());

    static final MapCodec<Ground> GROUND_TYPE = Registry.register(BuiltInRegistries.FEATURE_TYPE,
            Hardwrought.id("ash_grove_ground"), MapCodec.unit(Ground.INSTANCE));

    private AshGrove() { }

    public static void initialize() {
        ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, Hardwrought.id("nether_grass"));
        Registry.register(BuiltInRegistries.ITEM, item,
                new BlockItem(NETHER_GRASS, new Item.Properties().setId(item).useBlockDescriptionPrefix()));
        // Between the warped forest and the basalt deltas, and as common as any vanilla Nether biome.
        NetherBiomes.addNetherBiome(KEY, Climate.parameters(-0.35f, 0.35f, 0, 0, 0, 0, 0));
    }

    /** Whether water poured here boils away, as everywhere in the Nether but the Ash Grove. */
    public static boolean evaporates(Level level, BlockPos pos) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos);
    }

    /** Whether water standing here is always boiled clean. */
    public static boolean boils(Level level, BlockPos pos) {
        // Asked on every move of marked water, so the cheap question first: only the Nether has any.
        return level.dimension() == Level.NETHER && level.getBiome(pos).is(BOILS_WATER);
    }

    /** The hiss and the steam of water that boils off the moment it is poured, as vanilla's bucket makes it. */
    public static void fizz(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f,
                2.6f + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
    }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
                                                BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    /**
     * What makes the Ash Grove's ground, run over each chunk it touches before anything grows: its
     * netherrack floor turns to Nether grass, and its share of the lava sea to water, sealed off from
     * the lava beyond by a rim of cast obsidian. Column by column
     * and only where the biome is the Ash Grove, so a chunk on its edge keeps the Nether's own ground
     * and lava on the far side.
     */
    record Ground() implements Feature {
        static final Ground INSTANCE = new Ground();
        /** The Nether's lava sea lies at and below this height. */
        static final int LAVA_SEA_TOP = 31;
        /** Below the bedrock roof. */
        static final int TOP = 122;

        @Override
        public MapCodec<Ground> codec() {
            return GROUND_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4;
            int z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
            int minY = level.getMinY() + 1;
            BlockState water = Blocks.WATER.defaultBlockState();
            BlockState grass = NETHER_GRASS.defaultBlockState();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            boolean changed = false;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    BlockState above = null;
                    for (int y = TOP; y >= minY; y--) {
                        pos.set(x0 + dx, y, z0 + dz);
                        BlockState state = level.getBlockState(pos);
                        if (state.is(Blocks.LAVA) && level.getBiome(pos).is(KEY)) {
                            level.setBlock(pos, water, Block.UPDATE_CLIENTS);
                            seal(level, pos);
                            state = water;
                            changed = true;
                        } else if (state.is(Blocks.NETHERRACK) && above != null && above.isAir()
                                && y > LAVA_SEA_TOP && level.getBiome(pos).is(KEY)) {
                            level.setBlock(pos, grass, Block.UPDATE_CLIENTS);
                            state = grass;
                            changed = true;
                        }
                        above = state;
                    }
                }
            }
            return changed;
        }

        /**
         * Where the lake meets the lava of the biome next door, the two have already met: the lava has set
         * into cast obsidian, as it would the moment they touched. Otherwise water would stand against lava
         * with nothing between them.
         */
        private static void seal(WorldGenLevel level, BlockPos water) {
            for (Direction side : Direction.values()) {
                BlockPos next = water.relative(side);
                if (level.getBlockState(next).is(Blocks.LAVA) && !level.getBiome(next).is(KEY)) {
                    level.setBlock(next, CastObsidian.BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The nearest Ash Grove to {@code around} in the Nether, or null if none is within reach. */
    public static BlockPos nearest(ServerLevel nether, BlockPos around) {
        var found = nether.findClosestBiome3d(biome -> biome.is(KEY), around, LANDING_SEARCH, 32, 64);
        return found == null ? null : found.getFirst();
    }
}
