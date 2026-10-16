package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

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
     * What is in the water of each cauldron that holds anything but plain water. A cauldron is not
     * water in the world, so it keeps its own table; without it seawater poured into a cauldron came
     * back out clean.
     */
    public static final AttachmentType<QualityChunkData> CAULDRON_QUALITY =
            AttachmentRegistry.<QualityChunkData>create(Hardwrought.id("cauldron_water_quality"), builder ->
                    builder.persistent(TABLE_CODEC).initializer(QualityChunkData::new));
    /**
     * What the client draws: every marked cell and cauldron of a chunk, by quality. Kept apart from the
     * tables above, and only rebuilt now and then ({@link #flush}), so that marked water flowing through a
     * lake does not send a packet every step. Immutable, so a change is a new value Fabric sends.
     */
    public static final AttachmentType<Marks> SHOWN =
            AttachmentRegistry.<Marks>create(Hardwrought.id("shown_water_quality"), builder ->
                    builder.syncWith(Marks.STREAM_CODEC, AttachmentSyncPredicate.all()));
    /** How often the shown marks are brought up to date, in ticks. */
    private static final int FLUSH_TICKS = 10;
    /** Chunks whose marks changed since the last flush, by level. */
    private static final Map<ServerLevel, LongSet> DIRTY = new WeakHashMap<>();

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
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % FLUSH_TICKS == 0) flush();
        });
    }

    private static void dirty(ServerLevel level, BlockPos pos) {
        DIRTY.computeIfAbsent(level, ignored -> new LongOpenHashSet()).add(ChunkPos.pack(pos));
    }

    /** Brings the marks the client draws up to date in every chunk whose marks changed. */
    public static void flush() {
        for (Map.Entry<ServerLevel, LongSet> entry : DIRTY.entrySet()) {
            ServerLevel level = entry.getKey();
            for (long packed : entry.getValue()) {
                int x = ChunkPos.getX(packed), z = ChunkPos.getZ(packed);
                if (!level.hasChunk(x, z)) continue;
                LevelChunk chunk = level.getChunk(x, z);
                Marks now = Marks.of(chunk.getAttached(CELL_QUALITY), chunk.getAttached(CAULDRON_QUALITY));
                Marks was = chunk.getAttached(SHOWN);
                if (now.isEmpty() ? was == null : now.equals(was)) continue;
                if (now.isEmpty()) chunk.removeAttached(SHOWN);
                else chunk.setAttached(SHOWN, now);
            }
        }
        DIRTY.clear();
    }

    /**
     * What water nobody has marked looks like on the client: seawater in the sea, plain water anywhere
     * else. The client cannot repeat the server's finer judgement (it knows neither the groundwater table
     * nor the size of a lake), so anything that differs from this has to be sent.
     */
    public static WaterQuality shownByDefault(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_OCEAN) ? WaterQuality.SALT : WaterQuality.FRESH;
    }

    /**
     * Whether the water at pos is seawater, cheaply: its mark, or what its surroundings make it. The
     * lake-size test {@code WaterSystem.qualityAt} adds is left out, since it only ever tells swamp
     * water from river water, and this is asked by every field on every random tick.
     */
    public static boolean salty(ServerLevel level, BlockPos pos) {
        WaterQuality marked = stored(level, pos);
        return (marked != null ? marked : WaterQualityProfiles.ambient(level, pos)) == WaterQuality.SALT;
    }

    /** What is in the water of the cauldron at pos; plain water where nothing is recorded. */
    public static WaterQuality cauldron(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return WaterQuality.FRESH;
        QualityChunkData table = level.getChunkAt(pos).getAttached(CAULDRON_QUALITY);
        WaterQuality quality = table == null ? null : table.get(pos.asLong());
        return quality == null ? WaterQuality.FRESH : quality;
    }

    /** Records what is in a cauldron; plain water, or none, clears the record. */
    public static void setCauldron(ServerLevel level, BlockPos pos, WaterQuality quality) {
        if (!level.hasChunkAt(pos)) return;
        LevelChunk chunk = level.getChunkAt(pos);
        QualityChunkData table = chunk.getAttached(CAULDRON_QUALITY);
        if (quality == null || quality == WaterQuality.FRESH) {
            if (table == null || table.remove(pos.asLong()) == null) return;
            if (table.isEmpty()) chunk.removeAttached(CAULDRON_QUALITY);
            else chunk.markUnsaved();
        } else {
            if (table == null) {
                table = new QualityChunkData();
                chunk.setAttached(CAULDRON_QUALITY, table);
            }
            table.put(pos.asLong(), quality);
            chunk.markUnsaved();
        }
        dirty(level, pos);
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
        // In the Ash Grove water is always boiled: whatever was poured, purified water is what stands.
        if (quality != null && de.ipnats.hardwrought.nether.AshGrove.boils(level, pos)) quality = WaterQuality.PURIFIED;
        // Kept even where the surroundings would say the same, if the client would draw it otherwise:
        // swamp water poured into a meadow pond is swamp water by both counts, and must look it.
        if (quality == null || quality == WaterQualityProfiles.ambient(level, pos) && quality == shownByDefault(level, pos)) {
            clear(level, pos);
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        QualityChunkData table = chunk.getAttached(CELL_QUALITY);
        if (table == null) {
            table = new QualityChunkData();
            table.put(pos.asLong(), quality);
            chunk.setAttached(CELL_QUALITY, table);
            dirty(level, pos);
            return;
        }
        if (table.size() >= MAX_MARKED_CELLS_PER_CHUNK && table.get(pos.asLong()) == null) return;
        if (table.put(pos.asLong(), quality) != quality) {
            chunk.markUnsaved();
            dirty(level, pos);
        }
    }

    /** Water that is no longer there has no quality; an empty table is dropped entirely. */
    public static void clear(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        LevelChunk chunk = level.getChunkAt(pos);
        QualityChunkData table = chunk.getAttached(CELL_QUALITY);
        if (table == null || table.remove(pos.asLong()) == null) return;
        if (table.isEmpty()) chunk.removeAttached(CELL_QUALITY);
        else chunk.markUnsaved();
        dirty(level, pos);
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

    /** The quality of every marked cell and cauldron of one chunk, as the client draws it. Immutable. */
    public static final class Marks {
        public static final StreamCodec<ByteBuf, Marks> STREAM_CODEC = StreamCodec.of(
                (buffer, marks) -> {
                    VarInt.write(buffer, marks.marks.size());
                    for (Long2ByteMap.Entry entry : marks.marks.long2ByteEntrySet()) {
                        buffer.writeLong(entry.getLongKey());
                        buffer.writeByte(entry.getByteValue());
                    }
                },
                buffer -> {
                    int size = VarInt.read(buffer);
                    Long2ByteOpenHashMap marks = new Long2ByteOpenHashMap(size);
                    for (int i = 0; i < size; i++) marks.put(buffer.readLong(), buffer.readByte());
                    return new Marks(marks);
                });

        private final Long2ByteOpenHashMap marks;

        private Marks(Long2ByteOpenHashMap marks) {
            this.marks = marks;
            marks.defaultReturnValue((byte) -1);
        }

        static Marks of(QualityChunkData cells, QualityChunkData cauldrons) {
            Long2ByteOpenHashMap marks = new Long2ByteOpenHashMap();
            for (QualityChunkData table : new QualityChunkData[] {cells, cauldrons}) {
                if (table == null) continue;
                for (Long2IntMap.Entry entry : table.qualities.long2IntEntrySet()) {
                    marks.put(entry.getLongKey(), (byte) entry.getIntValue());
                }
            }
            return new Marks(marks);
        }

        /** The quality marked at this position, or null where the water is what its surroundings are. */
        public WaterQuality at(long pos) {
            byte ordinal = marks.get(pos);
            return ordinal < 0 ? null : WaterQuality.byOrdinal(ordinal);
        }

        public LongSet positions() {
            return marks.keySet();
        }

        boolean isEmpty() {
            return marks.isEmpty();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Marks that && marks.equals(that.marks);
        }

        @Override
        public int hashCode() {
            return marks.hashCode();
        }
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
