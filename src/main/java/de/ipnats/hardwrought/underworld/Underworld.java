package de.ipnats.hardwrought.underworld;

import com.mojang.serialization.MapCodec;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Underworld: a dimension of its own beneath the world (see docs/underworld.md). Its terrain,
 * biomes and light are data — the dimension, its noise and its surface rules under
 * {@code data/hardwrought}; what is made here in code is what data cannot say:
 *
 * <ul>
 *   <li><b>pale shards</b> — faintly glowing crystal on the walls of the Stone Wastes and the Echo Chasm,
 *       one of the few lights down here, and what rekindles the ascent shafts;
 *   <li>the <b>Flooded Halls</b>' lakes, black stagnant water filling the bottoms of their caverns;
 *   <li>the <b>Echo Chasm</b>'s chasms, cut deep through the rock, with ledges along their walls.
 * </ul>
 */
public final class Underworld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Hardwrought.id("underworld"));
    public static final TagKey<Biome> GROWS_PALE_SHARDS = TagKey.create(Registries.BIOME, Hardwrought.id("grows_pale_shards"));
    public static final ResourceKey<Biome> FLOODED_HALLS = biome("flooded_halls");
    public static final ResourceKey<Biome> ECHO_CHASM = biome("echo_chasm");

    public static final Block PALE_SHARD_CLUSTER = Registry.register(BuiltInRegistries.BLOCK, blockKey("pale_shard_cluster"),
            new AmethystClusterBlock(7.0f, 10.0f, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).forceSolidOn()
                    .noOcclusion().sound(SoundType.AMETHYST_CLUSTER).strength(1.5f).lightLevel(state -> 6)
                    .pushReaction(PushReaction.POPPED).setId(blockKey("pale_shard_cluster"))));
    public static final Item PALE_SHARD = item("pale_shard");

    static final MapCodec<PaleShards> PALE_SHARDS_TYPE = feature("pale_shards", PaleShards.INSTANCE);
    static final MapCodec<Flood> FLOOD_TYPE = feature("underworld_flood", Flood.INSTANCE);
    static final MapCodec<Chasm> CHASM_TYPE = feature("echo_chasm", Chasm.INSTANCE);

    private Underworld() { }

    public static void initialize() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id("pale_shard_cluster"));
        BlockItem cluster = new BlockItem(PALE_SHARD_CLUSTER, new Item.Properties().setId(key).useBlockDescriptionPrefix());
        cluster.registerBlocks(Item.BY_BLOCK, cluster);
        Registry.register(BuiltInRegistries.ITEM, key, cluster);
    }

    public static boolean in(Level level) {
        return level.dimension() == LEVEL;
    }

    /**
     * Pale shards: one or two clusters a chunk where they grow (in the Stone Wastes only one chunk in eight),
     * each on a face of bare rock, growing out of it into the open.
     * Only where the biome grows them, so a chunk on a biome's edge keeps them on its own side.
     */
    record PaleShards() implements Feature {
        static final PaleShards INSTANCE = new PaleShards();
        static final int TRIES = 48, MOST = 2;

        @Override
        public MapCodec<PaleShards> codec() {
            return PALE_SHARDS_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4, z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
            int placed = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int attempt = 0; attempt < TRIES && placed < MOST; attempt++) {
                pos.set(x0 + random.nextInt(16), level.getMinY() + 8 + random.nextInt(level.getHeight() - 16), z0 + random.nextInt(16));
                if (!level.getBlockState(pos).isAir() || !level.getBiome(pos).is(GROWS_PALE_SHARDS)) continue;
                for (Direction side : Direction.values()) {
                    BlockState wall = level.getBlockState(pos.relative(side));
                    if (!wall.is(BlockTags.BASE_STONE_OVERWORLD)) continue;
                    level.setBlock(pos, PALE_SHARD_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, side.getOpposite()),
                            Block.UPDATE_CLIENTS);
                    placed++;
                    break;
                }
            }
            return placed > 0;
        }
    }

    /**
     * The Flooded Halls: every cavern in them below {@link #WATER_LINE} stands full of still water. At the
     * edge of the biome the caverns are walled off with the rock round them up to the water line, so the
     * lakes stay sealed in and never spill out into the halls beside them.
     */
    record Flood() implements Feature {
        static final Flood INSTANCE = new Flood();
        static final int WATER_LINE = 8;

        @Override
        public MapCodec<Flood> codec() {
            return FLOOD_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4, z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
            BlockState water = Blocks.WATER.defaultBlockState();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            boolean changed = false;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = x0 + dx, z = z0 + dz;
                    if (!flooded(level, pos, x, z)) continue;
                    boolean edge = !flooded(level, pos, x + 1, z) || !flooded(level, pos, x - 1, z)
                            || !flooded(level, pos, x, z + 1) || !flooded(level, pos, x, z - 1);
                    BlockState rock = Blocks.STONE.defaultBlockState();
                    for (int y = level.getMinY() + 1; y <= WATER_LINE; y++) {
                        pos.set(x, y, z);
                        BlockState here = level.getBlockState(pos);
                        if (here.isAir() || edge && !here.isSolidRender()) {
                            level.setBlock(pos, edge ? rock : water, Block.UPDATE_CLIENTS);
                            changed = true;
                        } else if (here.isSolidRender() && !here.is(Blocks.BEDROCK)) {
                            rock = here;
                        }
                    }
                }
            }
            return changed;
        }

        private static boolean flooded(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
            return level.getBiome(pos.set(x, WATER_LINE, z)).is(FLOODED_HALLS);
        }
    }

    /**
     * The Echo Chasm: long winding chasms cut down through the rock along a ridge of noise, wide at the
     * middle and narrowing to the bottom and the top, with ledges left standing along their walls.
     */
    record Chasm() implements Feature {
        static final Chasm INSTANCE = new Chasm();
        static final int BOTTOM = -48, TOP = 210;
        static final double SCALE = 0.006, WIDTH = 0.09;

        @Override
        public MapCodec<Chasm> codec() {
            return CHASM_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            SimplexNoise path = new SimplexNoise(RandomSource.create(level.getSeed() ^ 0x5EC0C4A5L));
            SimplexNoise ledges = new SimplexNoise(RandomSource.create(level.getSeed() ^ 0x1ED6E5L));
            int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4, z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            boolean changed = false;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = x0 + dx, z = z0 + dz;
                    pos.set(x, (BOTTOM + TOP) / 2, z);
                    if (!level.getBiome(pos).is(ECHO_CHASM)) continue;
                    double ridge = Math.abs(path.get(x * SCALE, z * SCALE) / 2.0);
                    if (ridge > WIDTH) continue;
                    double ledge = ledges.get(x * 0.05, z * 0.05);
                    for (int y = BOTTOM; y <= TOP; y++) {
                        // Widest in the middle, narrowing to a cleft at the bottom and the top.
                        double height = (y - BOTTOM) / (double) (TOP - BOTTOM);
                        double width = WIDTH * Math.sqrt(Math.max(0, Math.sin(Math.PI * height)));
                        if (ridge > width) continue;
                        // Ledges: shelves left standing at the edges, every so often up the wall.
                        if (ridge > width * 0.7 && Math.floorMod(y + (int) (ledge * 6), 17) == 0) continue;
                        pos.setY(y);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) continue;
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                        changed = true;
                    }
                }
            }
            return changed;
        }
    }

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, Hardwrought.id(name));
    }

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
    }

    private static Item item(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private static <F extends Feature> MapCodec<F> feature(String name, F instance) {
        return Registry.register(BuiltInRegistries.FEATURE_TYPE, Hardwrought.id(name), MapCodec.unit(instance));
    }
}
