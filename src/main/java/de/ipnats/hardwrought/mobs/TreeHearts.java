package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * § 39.1: creakings are not only the pale garden's. Their hearts grow in the trees of every forest
 * — very rarely in an ordinary one, rarely in a dark or old-growth one, and the pale garden keeps
 * vanilla's hearts, one pale oak in ten. A heart outside pale oak wears the bark of its tree, and
 * the creaking it calls is made of the same wood.
 */
public final class TreeHearts {
    /** Ordinary forests: one tree in three hundred. */
    static final double FOREST_CHANCE = 1.0 / 300;
    /** Dark and old-growth forests: one in a hundred. The pale garden's one in ten is vanilla's. */
    static final double DEEP_FOREST_CHANCE = 1.0 / 100;

    private static final StreamCodec<ByteBuf, HeartWood> WOOD_STREAM_CODEC =
            ByteBufCodecs.idMapper(id -> HeartWood.values()[id], HeartWood::ordinal);

    /** The wood a creaking was called out of. Absent for pale oak and for creakings from an egg. */
    public static final AttachmentType<HeartWood> CREAKING_WOOD = AttachmentRegistry.<HeartWood>create(
            Hardwrought.id("creaking_wood"), builder -> builder.persistent(HeartWood.CODEC)
                    .syncWith(WOOD_STREAM_CODEC, AttachmentSyncPredicate.all()));

    private TreeHearts() { }

    public static void initialize() {
        // The tree heart is vanilla's heart in another bark, so it shares vanilla's block entity.
        BlockEntityTypes.CREAKING_HEART.addValidBlock(ModBlocks.TREE_CREAKING_HEART);
    }

    /** How likely a freshly grown tree in this biome is to hold a heart. */
    public static double chance(Holder<Biome> biome) {
        if (biome.is(Biomes.PALE_GARDEN)) return 0;
        if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
                || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)) {
            return DEEP_FOREST_CHANCE;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)
                || biome.is(Biomes.CHERRY_GROVE) || biome.is(Biomes.MANGROVE_SWAMP)) {
            return FOREST_CHANCE;
        }
        return 0;
    }

    /** Called by {@code TreeFeatureMixin} once a tree and its decorations stand. */
    public static void growInto(WorldGenLevel level, RandomSource random, BlockPos origin,
                                Set<BlockPos> trunks, Set<BlockPos> decorations) {
        if (trunks.isEmpty()) return;
        double chance = chance(level.getBiome(origin));
        if (chance <= 0 || random.nextDouble() >= chance) return;
        BlockPos spot = pickSpot(level, random, trunks);
        if (spot == null) return;
        BlockState log = level.getBlockState(spot);
        level.setBlock(spot, ModBlocks.TREE_CREAKING_HEART.defaultBlockState()
                .setValue(CreakingHeartBlock.AXIS, log.getValue(BlockStateProperties.AXIS))
                .setValue(CreakingHeartBlock.STATE, CreakingHeartState.DORMANT)
                .setValue(CreakingHeartBlock.NATURAL, true)
                .setValue(TreeHeartBlock.WOOD, HeartWood.of(log)), 19);
        decorations.add(spot);
    }

    /**
     * Where in the trunk the heart goes. Like vanilla, a log buried in wood on every side is best;
     * a slim tree has none, so then any log with its own wood above and below it will do.
     */
    static @Nullable BlockPos pickSpot(LevelReader level, RandomSource random, Set<BlockPos> trunks) {
        List<BlockPos> logs = new ArrayList<>(trunks);
        logs.sort(Comparator.comparingLong(BlockPos::asLong));
        Util.shuffle(logs, random);
        BlockPos fallback = null;
        for (BlockPos pos : logs) {
            BlockState state = level.getBlockState(pos);
            HeartWood wood = HeartWood.of(state);
            if (wood == null || !state.hasProperty(BlockStateProperties.AXIS)) continue;
            if (!hasLogsAlong(level, pos, state.getValue(BlockStateProperties.AXIS), wood)) continue;
            if (buried(level, pos)) return pos;
            if (fallback == null) fallback = pos;
        }
        return fallback;
    }

    private static boolean buried(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getBlockState(pos.relative(direction)).is(BlockTags.LOGS)) return false;
        }
        return true;
    }

    /** The tree heart's version of {@link CreakingHeartBlock#hasRequiredLogs}: its own wood, not pale oak. */
    public static boolean hasRequiredLogs(BlockState heart, LevelReader level, BlockPos pos) {
        return hasLogsAlong(level, pos, heart.getValue(CreakingHeartBlock.AXIS), heart.getValue(TreeHeartBlock.WOOD));
    }

    private static boolean hasLogsAlong(LevelReader level, BlockPos pos, Direction.Axis axis, HeartWood wood) {
        for (Direction direction : axis.getDirections()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (!neighbour.is(wood.logs()) || !neighbour.hasProperty(BlockStateProperties.AXIS)
                    || neighbour.getValue(BlockStateProperties.AXIS) != axis) {
                return false;
            }
        }
        return true;
    }

    /** For the idle sound: a heart sealed in its own wood on every side. */
    public static boolean surroundedByLogs(BlockState heart, LevelReader level, BlockPos pos) {
        HeartWood wood = heart.getValue(TreeHeartBlock.WOOD);
        for (Direction direction : Direction.values()) {
            if (!level.getBlockState(pos.relative(direction)).is(wood.logs())) return false;
        }
        return true;
    }

    /**
     * A vanilla heart set between two logs of another wood grows into that wood's bark, so a heart
     * taken from an oak works in an oak again. Between pale oak, or anything else, it stays as it is.
     */
    public static BlockState forPlacement(BlockState vanilla, BlockPlaceContext context) {
        Direction.Axis axis = vanilla.getValue(CreakingHeartBlock.AXIS);
        HeartWood wood = HeartWood.of(context.getLevel().getBlockState(context.getClickedPos().relative(axis.getPositive())));
        if (wood == null || !hasLogsAlong(context.getLevel(), context.getClickedPos(), axis, wood)) return vanilla;
        CreakingHeartState state = context.getLevel().environmentAttributes()
                .getValue(EnvironmentAttributes.CREAKING_ACTIVE, context.getClickedPos())
                ? CreakingHeartState.AWAKE : CreakingHeartState.DORMANT;
        return ModBlocks.TREE_CREAKING_HEART.defaultBlockState()
                .setValue(CreakingHeartBlock.AXIS, axis)
                .setValue(CreakingHeartBlock.STATE, state)
                .setValue(TreeHeartBlock.WOOD, wood);
    }
}
