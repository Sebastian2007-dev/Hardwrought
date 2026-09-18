package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the exact amount of water in a block lives.
 *
 * <p>Only blocks that are not completely full need storing: a full block is a thousand millibuckets
 * by definition, and so are the oceans and lakes a world was generated with. That keeps the stored
 * table to the moving edge of the water rather than to every block of it.
 *
 * <p>The table hangs off the chunk, so it is saved and loaded with the chunk it describes and never
 * grows into a world-sized structure.
 */
public final class WaterStorage {
    private static final Codec<Map<String, Integer>> TABLE_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);
    public static final AttachmentType<Map<String, Integer>> PARTIAL_WATER =
            AttachmentRegistry.<Map<String, Integer>>create(Hardwrought.id("partial_water"), builder ->
                    builder.persistent(TABLE_CODEC).initializer(HashMap::new));

    private WaterStorage() { }

    /**
     * Forces the attachment above to be registered during mod initialisation. An attachment type that
     * is only registered when the class happens to be touched is unknown while chunks are being read,
     * and every stored amount in them is discarded — the table would then only ever hold what the
     * current session put there.
     */
    public static void initialize() {
        // Loading the class registers the attachment.
    }

    /** How much water stands in this block, in millibuckets. Zero when there is none. */
    public static int amount(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isFreeWater(state)) return 0;
        // The table is asked first even for a block that looks full: a cell under pressure holds
        // more than a block and is drawn exactly the same way.
        Integer stored = table(level, pos).get(key(pos));
        if (stored != null) return WaterAmounts.clamp(stored);
        return state.getValue(LiquidBlock.LEVEL) == 0
                ? WaterAmounts.BLOCK : WaterAmounts.nominalAmount(state);
    }

    /**
     * Sets the water in this block and updates what is drawn there. Zero removes it; a full block is
     * stored implicitly, so the table only ever holds the partial cells.
     */
    public static void setAmount(ServerLevel level, BlockPos pos, int millibuckets) {
        int amount = WaterAmounts.clamp(millibuckets);
        BlockState state = level.getBlockState(pos);
        if (amount <= 0) {
            forget(level, pos);
            if (isFreeWater(state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (!isFreeWater(state) && !state.isAir()) {
            // Water does not stop at a tuft of grass or a torch: it washes them out of its way.
            if (!state.is(BlockTags.WASHED_AWAY_BY_FLUIDS)) return;
            Block.dropResources(state, level, pos);
        }
        int display = WaterAmounts.displayLevel(amount);
        BlockState target = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, display);
        if (!target.equals(state)) level.setBlock(pos, target, Block.UPDATE_ALL);
        // Exactly full needs nothing stored; anything else, including a cell under pressure
        // holding more than a block, is only known from the table.
        if (amount == WaterAmounts.BLOCK) forget(level, pos);
        else remember(level, pos, amount);
    }

    /** Free water is water that can move: a plain water block, not a waterlogged stair or a kelp. */
    public static boolean isFreeWater(BlockState state) {
        return state.is(Blocks.WATER) && state.hasProperty(LiquidBlock.LEVEL);
    }

    /**
     * True where water could stand: empty space, water that is not already full, or anything a fluid
     * washes away.
     *
     * <p>That last part is not a detail. A hillside is covered in grass tufts and flowers, and
     * treating them as walls left a bucket of water sitting in place on open ground because every
     * direction it could have gone was occupied by a plant. Vanilla keeps the list of what a fluid
     * removes in a tag, and that same tag is used here.
     *
     * <p>Anything else — a solid block, a waterlogged one, lava — is not free space for water.
     */
    public static boolean canHold(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (isFreeWater(state)) return true;
        return state.is(BlockTags.WASHED_AWAY_BY_FLUIDS);
    }

    private static void remember(ServerLevel level, BlockPos pos, int millibuckets) {
        LevelChunk chunk = level.getChunkAt(pos);
        Map<String, Integer> table = new HashMap<>(chunk.getAttachedOrCreate(PARTIAL_WATER));
        table.put(key(pos), millibuckets);
        chunk.setAttached(PARTIAL_WATER, table);
    }

    private static void forget(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        Map<String, Integer> existing = chunk.getAttachedOrCreate(PARTIAL_WATER);
        if (!existing.containsKey(key(pos))) return;
        Map<String, Integer> table = new HashMap<>(existing);
        table.remove(key(pos));
        chunk.setAttached(PARTIAL_WATER, table);
    }

    private static Map<String, Integer> table(ServerLevel level, BlockPos pos) {
        return level.getChunkAt(pos).getAttachedOrCreate(PARTIAL_WATER);
    }

    private static String key(BlockPos pos) {
        return Long.toString(pos.asLong());
    }
}
