package de.ipnats.hardwrought.building;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which blocks were built rather than generated, and how much stress the stressed ones have taken.
 *
 * <p>Only built blocks take part in the statics; natural terrain is the ground they stand on. Both
 * tables hang off the chunk they describe, so they are saved and loaded with it and never grow into
 * a world-sized structure. Blocks built before this existed are simply not in the table and count as
 * terrain — an old base does not fall down the day the mod is updated.
 */
public final class BuiltBlocks {
    public static final class Positions {
        final LongOpenHashSet set;

        Positions(LongOpenHashSet set) { this.set = set; }

        Positions() { this(new LongOpenHashSet()); }

        public int size() { return set.size(); }
    }

    public static final class Stress {
        final Long2FloatOpenHashMap map;

        Stress(Long2FloatOpenHashMap map) { this.map = map; }

        Stress() { this(new Long2FloatOpenHashMap()); }
    }

    private static final Codec<Positions> POSITIONS_CODEC = Codec.LONG.listOf().xmap(
            list -> new Positions(new LongOpenHashSet(list)), positions -> List.copyOf(positions.set));
    private static final Codec<Stress> STRESS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.FLOAT).xmap(
            map -> {
                Long2FloatOpenHashMap result = new Long2FloatOpenHashMap();
                map.forEach((key, value) -> result.put(Long.parseLong(key), value.floatValue()));
                return new Stress(result);
            },
            stress -> {
                Map<String, Float> result = new HashMap<>();
                for (Long2FloatMap.Entry entry : stress.map.long2FloatEntrySet()) {
                    result.put(Long.toString(entry.getLongKey()), entry.getFloatValue());
                }
                return result;
            });

    public static final AttachmentType<Positions> BUILT = AttachmentRegistry.<Positions>create(
            Hardwrought.id("built_blocks"), builder -> builder.persistent(POSITIONS_CODEC));
    /** Built blocks a structural anchor ties to their neighbours. */
    public static final AttachmentType<Positions> ANCHORED = AttachmentRegistry.<Positions>create(
            Hardwrought.id("anchored_blocks"), builder -> builder.persistent(POSITIONS_CODEC));
    public static final AttachmentType<Stress> STRESS = AttachmentRegistry.<Stress>create(
            Hardwrought.id("structural_stress"), builder -> builder.persistent(STRESS_CODEC));

    private BuiltBlocks() { }

    /** Loading the class registers the attachments before any chunk is read. */
    public static void initialize() { }

    /** The chunk, if it is loaded; never loads one. */
    static LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public static boolean isBuilt(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return false;
        Positions built = chunk.getAttached(BUILT);
        return built != null && built.set.contains(pos.asLong());
    }

    static void markBuilt(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return;
        Positions built = chunk.getAttached(BUILT);
        if (built == null) {
            built = new Positions();
            chunk.setAttached(BUILT, built);
        }
        if (built.set.add(pos.asLong())) chunk.markUnsaved();
    }

    /** Forgets a block that is gone, with its anchor and any stress it had. */
    static void unmark(LevelChunk chunk, BlockPos pos) {
        remove(chunk, BUILT, pos);
        remove(chunk, ANCHORED, pos);
        setStress(chunk, pos, 0);
    }

    private static void remove(LevelChunk chunk, AttachmentType<Positions> type, BlockPos pos) {
        Positions positions = chunk.getAttached(type);
        if (positions != null && positions.set.remove(pos.asLong())) {
            if (positions.set.isEmpty()) chunk.removeAttached(type);
            else chunk.markUnsaved();
        }
    }

    public static boolean isAnchored(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return false;
        Positions anchored = chunk.getAttached(ANCHORED);
        return anchored != null && anchored.set.contains(pos.asLong());
    }

    /** Ties a built block to its neighbours. False when it is not built or already tied. */
    static boolean markAnchored(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !isBuilt(level, pos)) return false;
        Positions anchored = chunk.getAttached(ANCHORED);
        if (anchored == null) {
            anchored = new Positions();
            chunk.setAttached(ANCHORED, anchored);
        }
        if (!anchored.set.add(pos.asLong())) return false;
        chunk.markUnsaved();
        return true;
    }

    static float stress(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        Stress stress = chunk == null ? null : chunk.getAttached(STRESS);
        return stress == null ? 0 : stress.map.get(pos.asLong());
    }

    static void setStress(LevelChunk chunk, BlockPos pos, float value) {
        Stress stress = chunk.getAttached(STRESS);
        if (value <= 0) {
            if (stress != null && stress.map.containsKey(pos.asLong())) {
                stress.map.remove(pos.asLong());
                if (stress.map.isEmpty()) chunk.removeAttached(STRESS);
                else chunk.markUnsaved();
            }
            return;
        }
        if (stress == null) {
            stress = new Stress();
            chunk.setAttached(STRESS, stress);
        }
        stress.map.put(pos.asLong(), value);
        chunk.markUnsaved();
    }

    /** Every stressed position of a chunk, for crack marks after it was loaded. */
    static long[] stressed(LevelChunk chunk) {
        Stress stress = chunk.getAttached(STRESS);
        return stress == null ? new long[0] : stress.map.keySet().toLongArray();
    }

    /** Whether this chunk holds any built block at all: most do not, and are skipped at once. */
    static boolean hasBuilt(LevelChunk chunk) {
        return chunk.hasAttached(BUILT);
    }
}
