package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * The water that is inside the ice.
 *
 * <p>{@link ThinIceBlock} can only say how deep it is in eighths, and the water system counts in
 * millibuckets. So the exact figure waits here while the cell is frozen, in a chunk attachment of
 * the same shape as {@link WaterStorage}'s, and is handed straight back when the ice goes. A lake
 * that freezes and thaws is therefore the same lake afterwards — not a lake rounded to eighths,
 * which over a few winters is how a water system quietly stops conserving anything.
 *
 * <p>Keeping it apart from the liquid table matters for a second reason: the flow solver reads
 * that table to decide what to move, and frozen water must not move. Water that is in here is out
 * of the solver's sight until it thaws.
 */
public final class FrozenWater {
    private static final Codec<FrozenChunkData> TABLE_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
                    FrozenChunkData::fromSerialized, FrozenChunkData::serialized);
    public static final AttachmentType<FrozenChunkData> FROZEN_WATER =
            AttachmentRegistry.<FrozenChunkData>create(Hardwrought.id("frozen_water"), builder ->
                    builder.persistent(TABLE_CODEC).initializer(FrozenChunkData::new));

    private FrozenWater() { }

    /**
     * Registered during mod initialisation for the same reason the water table is: an attachment
     * type that is unknown while a chunk is being read has its stored values thrown away, and every
     * frozen lake in the world would thaw into nothing after a restart.
     */
    public static void initialize() {
        // Loading the class registers the attachment.
    }

    /** Puts this cell's water into the ice standing at the same place. */
    public static void hold(ServerLevel level, BlockPos pos, int milliBuckets) {
        if (milliBuckets <= 0) return;
        LevelChunk chunk = level.getChunkAt(pos);
        FrozenChunkData table = copyOf(chunk);
        table.amounts.put(pos.asLong(), WaterAmounts.clamp(milliBuckets));
        chunk.setAttached(FROZEN_WATER, table);
    }

    /** How much water is locked up at this position, or zero if none is. */
    public static int held(ServerLevel level, BlockPos pos) {
        FrozenChunkData table = level.getChunkAt(pos).getAttached(FROZEN_WATER);
        if (table == null) return 0;
        int stored = table.amounts.get(pos.asLong());
        return stored == FrozenChunkData.MISSING ? 0 : stored;
    }

    /** Takes the water back out of the table without putting it anywhere. */
    public static int take(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        FrozenChunkData table = chunk.getAttached(FROZEN_WATER);
        if (table == null) return 0;
        int stored = table.amounts.get(pos.asLong());
        if (stored == FrozenChunkData.MISSING) return 0;
        FrozenChunkData without = copyOf(chunk);
        without.amounts.remove(pos.asLong());
        if (without.amounts.isEmpty()) chunk.removeAttached(FROZEN_WATER);
        else chunk.setAttached(FROZEN_WATER, without);
        return stored;
    }

    /**
     * The ice at this position has gone: put its water back as liquid and wake the solver, so a
     * hole cut in a frozen lake fills from the lake around it rather than staying a dry pit.
     */
    public static void release(ServerLevel level, BlockPos pos) {
        int held = take(level, pos);
        if (held <= 0) return;
        if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) return;
        WaterStorage.setAmount(level, pos, held);
        WaterFlow.disturb(level, pos);
        for (var direction : net.minecraft.core.Direction.values()) {
            WaterFlow.disturb(level, pos.relative(direction));
        }
    }

    /** Whether this position is ice of ours. */
    public static boolean isIce(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(de.ipnats.hardwrought.core.registry.ModBlocks.THIN_ICE);
    }

    /** Whether the block here is plain air or something water may wash through. */
    static boolean replaceable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.WATER);
    }

    private static FrozenChunkData copyOf(LevelChunk chunk) {
        FrozenChunkData existing = chunk.getAttached(FROZEN_WATER);
        FrozenChunkData copy = new FrozenChunkData();
        if (existing != null) copy.amounts.putAll(existing.amounts);
        return copy;
    }

    /** One chunk's worth of locked-up water, saved and loaded with the chunk it belongs to. */
    public static final class FrozenChunkData {
        private static final int MISSING = -1;
        private final Long2IntOpenHashMap amounts = new Long2IntOpenHashMap();

        private FrozenChunkData() {
            amounts.defaultReturnValue(MISSING);
        }

        public static FrozenChunkData fromSerialized(Map<String, Integer> serialized) {
            FrozenChunkData data = new FrozenChunkData();
            serialized.forEach((key, value) -> data.amounts.put(Long.parseLong(key), value.intValue()));
            return data;
        }

        private Map<String, Integer> serialized() {
            Map<String, Integer> result = new HashMap<>(amounts.size());
            for (Long2IntMap.Entry entry : amounts.long2IntEntrySet()) {
                result.put(Long.toString(entry.getLongKey()), entry.getIntValue());
            }
            return result;
        }
    }
}
