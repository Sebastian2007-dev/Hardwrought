package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * What is dissolved in the water of a particular block, where that differs from what the
 * surroundings would say.
 *
 * <p>Section 23.2 gives water a quality, and until now that quality was re-derived from the biome
 * every time anyone looked. That works for a lake sitting where it was generated and for nothing
 * else: seawater carried inland became clean the moment it was poured out, a spring that had been
 * lifted out of a well was swamp water again, and salting a pond was impossible. Water now carries
 * what is in it, and it carries it while it moves.
 *
 * <p>Only the cells that differ from their surroundings are stored, exactly as only partial amounts
 * are stored in {@link WaterStorage}. Ordinary water in an ordinary lake costs nothing, the table
 * hangs off the chunk so it saves and loads with it, and a cell whose mark has been diluted back to
 * what its surroundings would say drops out of the table again.
 */
public final class WaterQualityStorage {
    private static final Codec<QualityChunkData> TABLE_CODEC =
            Codec.unboundedMap(Codec.STRING, WaterQuality.CODEC).xmap(
                    QualityChunkData::fromSerialized, QualityChunkData::serialized);
    public static final AttachmentType<QualityChunkData> CELL_QUALITY =
            AttachmentRegistry.<QualityChunkData>create(Hardwrought.id("cell_water_quality"), builder ->
                    builder.persistent(TABLE_CODEC).initializer(QualityChunkData::new));

    /**
     * How many marked cells one chunk carries. A flooded mine or a salted marsh is a few hundred;
     * past this the mark is simply not recorded and the water reads as its surroundings, which keeps
     * a chunk table from growing without a bound.
     */
    public static final int MAX_MARKED_CELLS_PER_CHUNK = 4_096;

    private WaterQualityStorage() { }

    /**
     * Forces the attachment to be registered during mod initialisation. An attachment type that is
     * only registered when its class happens to be touched is unknown while chunks are being read,
     * and everything stored in them is discarded on load.
     */
    public static void initialize() {
        // Loading the class registers the attachment.
    }

    /** What is marked on this cell, or null where the water is whatever its surroundings are. */
    public static WaterQuality stored(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        QualityChunkData table = level.getChunkAt(pos).getAttached(CELL_QUALITY);
        return table == null ? null : table.get(pos.asLong());
    }

    /** Marks a cell, or clears the mark where the water is no different from its surroundings. */
    public static void mark(ServerLevel level, BlockPos pos, WaterQuality quality) {
        if (!level.hasChunkAt(pos)) return;
        if (quality == null || quality == WaterQualityProfiles.ambient(level, pos)) {
            clear(level, pos);
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        QualityChunkData table = chunk.getAttached(CELL_QUALITY);
        if (table == null) {
            table = new QualityChunkData();
            table.put(pos.asLong(), quality);
            chunk.setAttached(CELL_QUALITY, table);
            return;
        }
        if (table.size() >= MAX_MARKED_CELLS_PER_CHUNK && table.get(pos.asLong()) == null) return;
        if (table.put(pos.asLong(), quality) != quality) chunk.markUnsaved();
    }

    /** Water that is no longer there has no quality; an empty table is dropped entirely. */
    public static void clear(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        LevelChunk chunk = level.getChunkAt(pos);
        QualityChunkData table = chunk.getAttached(CELL_QUALITY);
        if (table == null || table.remove(pos.asLong()) == null) return;
        if (table.isEmpty()) chunk.removeAttached(CELL_QUALITY);
        else chunk.markUnsaved();
    }

    /**
     * Pours water of a known quality into a cell and records what the cell now holds. This is the
     * one place a quality enters the world: a bucket being emptied, and a spring seeping out of the
     * ground.
     */
    public static void add(ServerLevel level, BlockPos pos, WaterQuality incoming,
                           int present, int added) {
        if (incoming == null || added <= 0 || !level.hasChunkAt(pos)) return;
        WaterQuality current = stored(level, pos);
        if (current == null && present > 0) current = WaterQualityProfiles.ambient(level, pos);
        mark(level, pos, current == null ? incoming : WaterQuality.mix(current, present, incoming, added));
    }

    /**
     * Carries quality along one transfer of the water simulation. The common case — ordinary water
     * moving into ordinary water — is two map lookups and nothing else; only water somebody has
     * marked costs more than that.
     *
     * <p>Called before the amounts are written, so both sides still read as they were.
     */
    public static void carry(ServerLevel level, BlockPos from, int sourceAmount,
                             BlockPos to, int destinationAmount, int moved) {
        if (moved <= 0) return;
        WaterQuality source = stored(level, from);
        if (source == null && stored(level, to) == null) return;
        WaterQuality incoming = source == null ? WaterQualityProfiles.ambient(level, from) : source;
        add(level, to, incoming, destinationAmount, moved);
        if (source != null && sourceAmount - moved <= 0) clear(level, from);
    }

    /** Primitive runtime representation; the codec keeps a readable name per cell in the save. */
    public static final class QualityChunkData {
        private static final int MISSING = -1;
        private final Long2IntOpenHashMap qualities = new Long2IntOpenHashMap();

        private QualityChunkData() {
            qualities.defaultReturnValue(MISSING);
        }

        public static QualityChunkData fromSerialized(Map<String, WaterQuality> serialized) {
            QualityChunkData data = new QualityChunkData();
            serialized.forEach((key, value) -> data.qualities.put(Long.parseLong(key), value.ordinal()));
            return data;
        }

        private Map<String, WaterQuality> serialized() {
            Map<String, WaterQuality> result = new HashMap<>(qualities.size());
            for (Long2IntMap.Entry entry : qualities.long2IntEntrySet()) {
                result.put(Long.toString(entry.getLongKey()), WaterQuality.byOrdinal(entry.getIntValue()));
            }
            return result;
        }

        private WaterQuality get(long key) {
            int ordinal = qualities.get(key);
            return ordinal == MISSING ? null : WaterQuality.byOrdinal(ordinal);
        }

        private WaterQuality put(long key, WaterQuality quality) {
            int previous = qualities.put(key, quality.ordinal());
            return previous == MISSING ? null : WaterQuality.byOrdinal(previous);
        }

        private WaterQuality remove(long key) {
            int previous = qualities.remove(key);
            return previous == MISSING ? null : WaterQuality.byOrdinal(previous);
        }

        private int size() {
            return qualities.size();
        }

        private boolean isEmpty() {
            return qualities.isEmpty();
        }
    }
}
