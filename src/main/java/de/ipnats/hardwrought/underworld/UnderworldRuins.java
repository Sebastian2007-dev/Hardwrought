package de.ipnats.hardwrought.underworld;

import com.mojang.serialization.MapCodec;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Stage 4: what is left of those who came down before. Scattered through the Underworld, half fallen in:
 *
 * <ul>
 *   <li><b>Small ruins</b>: broken walls round a worn floor, the roof fallen in, a cold fireplace, a chest,
 *   now and then a journal left open on a lectern.</li>
 *   <li><b>Camps</b>: a bedroll or two by a burnt-out campfire, a few barrels, old rope on the ground, a
 *   lantern long gone out.</li>
 *   <li><b>Watchtowers</b> at the edges of the Echo Chasm: a tower of deepslate looking out over the drop,
 *   its top broken, a ladder with rungs missing, a chest at the top.</li>
 * </ul>
 *
 * What they hold is little and worn: stale bread and rotten food, bad water, worn iron and rusted steel,
 * candles, torches, rope, flint, bandages — and the journals ({@link Journals}).
 */
public final class UnderworldRuins {
    public static final ResourceKey<LootTable> RUIN_LOOT = loot("chests/underworld_ruin");
    public static final ResourceKey<LootTable> CAMP_LOOT = loot("chests/underworld_camp");
    public static final ResourceKey<LootTable> WATCHTOWER_LOOT = loot("chests/underworld_watchtower");

    /** Bread a long time down here: hard, a little green, and not good for the stomach. */
    public static final Item STALE_BREAD = Registry.register(BuiltInRegistries.ITEM, Hardwrought.id("stale_bread"),
            new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Hardwrought.id("stale_bread")))
                    .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1f).build())));

    static final MapCodec<Ruin> RUIN_TYPE = feature("underworld_ruin", Ruin.INSTANCE);
    static final MapCodec<Camp> CAMP_TYPE = feature("underworld_camp", Camp.INSTANCE);
    static final MapCodec<Watchtower> WATCHTOWER_TYPE = feature("underworld_watchtower", Watchtower.INSTANCE);

    private UnderworldRuins() { }

    /** Loading the class registers the item and the features. */
    public static void initialize() {
        Journals.initialize();
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Hardwrought.id(path));
    }

    private static <F extends Feature> MapCodec<F> feature(String name, F instance) {
        return Registry.register(BuiltInRegistries.FEATURE_TYPE, Hardwrought.id(name), MapCodec.unit(instance));
    }

    // ------------------------------------------------------------------------------------ placing

    /** A spot on a cave floor in this chunk, open and flat over a square of the given half width, or null. */
    static BlockPos site(WorldGenLevel level, BlockPos origin, RandomSource random, int half, int height) {
        int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4, z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 40; attempt++) {
            pos.set(x0 + half + random.nextInt(16 - 2 * half), level.getMinY() + 12 + random.nextInt(level.getHeight() - 40),
                    z0 + half + random.nextInt(16 - 2 * half));
            if (!level.getBlockState(pos).isAir()) continue;
            while (pos.getY() > level.getMinY() + 8 && level.getBlockState(pos.below()).isAir()) pos.move(Direction.DOWN);
            if (flat(level, pos, half, height)) return pos.immutable();
        }
        return null;
    }

    /** Open over the whole square; the floor may sag in a few places, and is filled in where it does. */
    private static boolean flat(WorldGenLevel level, BlockPos center, int half, int height) {
        int side = 2 * half + 1, sagging = 0;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos at = center.offset(dx, 0, dz);
                if (!level.getBlockState(at.below()).isSolidRender()) {
                    if (!level.getBlockState(at.below(2)).isSolidRender() || ++sagging > side * side / 5) return false;
                }
                for (int dy = 0; dy < height; dy++) if (!level.getBlockState(at.above(dy)).isAir()) return false;
            }
        }
        return true;
    }

    /** Fills in where the floor under a square sags, with the palette's rubble. */
    static void level(WorldGenLevel level, BlockPos center, int half, BlockState rubble) {
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos below = center.offset(dx, -1, dz);
                if (!level.getBlockState(below).isSolidRender()) set(level, below, rubble);
            }
        }
    }

    /** What a ruin is built of: deepslate below y 0, stone above, with moss where things grow. */
    record Palette(BlockState[] walls, BlockState floor, BlockState rubble, BlockState slab) {
        static Palette at(WorldGenLevel level, BlockPos pos) {
            String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            boolean green = biome.equals("overgrown_halls") || biome.equals("mossy_grove");
            if (pos.getY() < 0) {
                return new Palette(new BlockState[] {Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                        Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()},
                        Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                        Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
            }
            return new Palette(new BlockState[] {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                    green ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(),
                    green ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState()},
                    Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState());
        }

        BlockState wall(RandomSource random) {
            return walls[random.nextInt(walls.length)];
        }
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    static void chest(WorldGenLevel level, BlockPos pos, Direction facing, ResourceKey<LootTable> loot, RandomSource random) {
        set(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
    }

    static void barrel(WorldGenLevel level, BlockPos pos, ResourceKey<LootTable> loot, RandomSource random) {
        set(level, pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        if (loot != null) RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
    }

    /** A lectern with a journal left open on it. */
    static void lectern(WorldGenLevel level, BlockPos pos, Direction facing, RandomSource random) {
        set(level, pos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, true));
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) lectern.setBook(Journals.random(random));
    }

    static BlockState coldCampfire() {
        return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false);
    }

    static BlockState skull(RandomSource random) {
        return Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16));
    }

    // ------------------------------------------------------------------------------------ the ruins

    /** A small ruin: seven by seven, its walls broken, the roof fallen in. */
    public record Ruin() implements Feature {
        static final Ruin INSTANCE = new Ruin();

        @Override
        public MapCodec<Ruin> codec() {
            return RUIN_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            BlockPos center = site(level, origin, random, 3, 4);
            if (center == null) return false;
            build(level, center, random);
            return true;
        }

        public static void build(WorldGenLevel level, BlockPos center, RandomSource random) {
            Palette palette = Palette.at(level, center);
            level(level, center, 3, palette.rubble());
            Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos at = center.offset(dx, 0, dz);
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    if (random.nextInt(6) > 0) set(level, at.below(), random.nextInt(3) == 0 ? palette.rubble() : palette.floor());
                    if (ring != 3) continue;
                    boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                    boolean doorway = dx == door.getStepX() * 3 && dz == door.getStepZ() * 3
                            || door.getStepX() != 0 && dx == door.getStepX() * 3 && Math.abs(dz) == 0
                            || door.getStepZ() != 0 && dz == door.getStepZ() * 3 && Math.abs(dx) == 0;
                    int high = doorway ? 0 : corner ? 3 + random.nextInt(2) : random.nextInt(4);
                    for (int y = 0; y < high; y++) set(level, at.above(y), palette.wall(random));
                    // What is left of the roof, on the highest of the walls.
                    if (high >= 3 && random.nextInt(3) == 0) {
                        set(level, at.above(high), palette.slab().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    }
                }
            }
            // The roof, fallen in: rubble and slabs over the floor.
            for (int i = 0; i < 6 + random.nextInt(5); i++) {
                BlockPos at = center.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                if (!level.getBlockState(at).isAir()) continue;
                set(level, at, random.nextBoolean() ? palette.rubble() : palette.slab().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            }
            // In the corners: the cold fireplace, the chest, and sometimes a journal and someone who stayed.
            Direction back = door.getOpposite();
            BlockPos hearth = center.relative(back, 2).relative(back.getClockWise(), 2);
            set(level, hearth, coldCampfire());
            set(level, hearth.above(), Blocks.AIR.defaultBlockState());
            BlockPos chestAt = center.relative(back, 2).relative(back.getCounterClockWise(), 2);
            chest(level, chestAt, door, RUIN_LOOT, random);
            if (random.nextInt(3) == 0) {
                lectern(level, center.relative(door, 1).relative(door.getClockWise(), 2), door.getCounterClockWise(), random);
            }
            if (random.nextInt(3) == 0) set(level, center.relative(back, 1).relative(back.getClockWise(), 1), skull(random));
            if (random.nextBoolean()) set(level, center.relative(door, 2).relative(door.getCounterClockWise(), 2), ModBlocks.BURNT_TORCH.defaultBlockState());
            for (int i = 0; i < 3; i++) {
                BlockPos web = center.offset(random.nextInt(5) - 2, 1 + random.nextInt(2), random.nextInt(5) - 2);
                if (level.getBlockState(web).isAir()) set(level, web, Blocks.COBWEB.defaultBlockState());
            }
        }
    }

    /** A camp: bedrolls by a burnt-out fire, barrels, rope, a lantern gone out. */
    public record Camp() implements Feature {
        static final Camp INSTANCE = new Camp();

        @Override
        public MapCodec<Camp> codec() {
            return CAMP_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            BlockPos center = site(level, origin, random, 2, 3);
            if (center == null) return false;
            build(level, center, random);
            return true;
        }

        public static void build(WorldGenLevel level, BlockPos center, RandomSource random) {
            level(level, center, 2, Palette.at(level, center).rubble());
            set(level, center, coldCampfire());
            Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            // A bedroll on each side of the fire, or one.
            for (Direction bed : random.nextBoolean() ? new Direction[] {side, side.getOpposite()} : new Direction[] {side}) {
                BlockPos head = center.relative(bed, 2);
                BlockState roll = Blocks.CARPET.pick(random.nextBoolean() ? net.minecraft.world.item.DyeColor.BROWN : net.minecraft.world.item.DyeColor.GRAY).defaultBlockState();
                set(level, head.relative(bed.getClockWise()), roll);
                set(level, head, roll);
                set(level, head.relative(bed.getCounterClockWise()), Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
            }
            Direction other = side.getClockWise();
            BlockPos stores = center.relative(other, 2);
            barrel(level, stores, CAMP_LOOT, random);
            if (random.nextBoolean()) barrel(level, stores.relative(side), CAMP_LOOT, random);
            if (random.nextBoolean()) barrel(level, stores.relative(side.getOpposite()), null, random);
            // Old rope on the ground, a lantern long out, and sometimes the one who never left.
            BlockPos rope = center.relative(other.getOpposite(), 2);
            for (int i = 0; i < 2 + random.nextInt(2); i++) {
                BlockPos at = rope.relative(side, i - 1);
                if (level.getBlockState(at).isAir()) set(level, at, Blocks.TRIPWIRE.defaultBlockState());
            }
            set(level, center.relative(other.getOpposite(), 1).relative(side, 1), ModBlocks.BURNT_LANTERN.defaultBlockState());
            if (random.nextInt(3) == 0) {
                set(level, center.relative(side, 2).relative(side.getCounterClockWise(), 2), skull(random));
            }
        }
    }

    /** A watchtower at the edge of the Echo Chasm, looking out over the drop. */
    public record Watchtower() implements Feature {
        static final Watchtower INSTANCE = new Watchtower();
        static final int DROP = 14;

        @Override
        public MapCodec<Watchtower> codec() {
            return WATCHTOWER_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            for (int attempt = 0; attempt < 6; attempt++) {
                BlockPos center = site(level, origin, random, 2, 12);
                if (center == null) return false;
                Direction chasm = edge(level, center);
                if (chasm == null) continue;
                build(level, center, chasm, random);
                return true;
            }
            return false;
        }

        /** The direction a long drop lies in, just beyond the tower's footprint, or null. */
        static Direction edge(WorldGenLevel level, BlockPos center) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos beyond = center.relative(direction, 4);
                boolean drop = true;
                for (int down = 1; down <= DROP && drop; down++) drop = level.getBlockState(beyond.below(down)).isAir();
                if (drop) return direction;
            }
            return null;
        }

        public static void build(WorldGenLevel level, BlockPos center, Direction chasm, RandomSource random) {
            Palette palette = Palette.at(level, center);
            int height = 8 + random.nextInt(4);
            Direction door = chasm.getOpposite();
            for (int y = -1; y <= height + 1; y++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos at = center.offset(dx, y, dz);
                        boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        if (y == -1) {
                            set(level, at, palette.floor());
                            continue;
                        }
                        if (!wall) {
                            set(level, at, y == height ? palette.slab().setValue(SlabBlock.TYPE, SlabType.TOP) : Blocks.AIR.defaultBlockState());
                            continue;
                        }
                        // The top is broken: the last courses stand only here and there.
                        if (y > height - 2 && random.nextInt(3) == 0) continue;
                        if (y == height + 1 && (dx + dz) % 2 != 0) continue;
                        boolean doorway = y < 2 && dx == door.getStepX() * 2 && dz == door.getStepZ() * 2;
                        boolean window = y >= 3 && y % 3 == 0 && dx == chasm.getStepX() * 2 && dz == chasm.getStepZ() * 2;
                        set(level, at, doorway || window ? Blocks.AIR.defaultBlockState() : palette.wall(random));
                    }
                }
            }
            // A ladder up the inside of the chasm side, some rungs gone; a hole in the top floor over it.
            BlockPos ladder = center.relative(chasm, 1);
            for (int y = 0; y <= height; y++) {
                BlockPos at = ladder.above(y);
                if (y == height || random.nextInt(7) > 0) {
                    set(level, at, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, door));
                }
            }
            BlockPos top = center.above(height + 1);
            chest(level, top.relative(door, 1).relative(door.getClockWise(), 1), chasm, WATCHTOWER_LOOT, random);
            if (random.nextBoolean()) lectern(level, top.relative(door, 1).relative(door.getCounterClockWise(), 1), chasm, random);
            set(level, top.relative(chasm.getClockWise(), 1), ModBlocks.BURNT_LANTERN.defaultBlockState());
        }
    }
}
