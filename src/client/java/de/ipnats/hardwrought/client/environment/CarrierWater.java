package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.water.WaterStorage;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's copy of how full each waterlogged block, kelp or bubble column is.
 *
 * <p>The server sends the visible step with the chunk and again whenever it changes. Sections are
 * built on worker threads, so what they read is a plain concurrent map rather than the chunk itself;
 * every change also marks the sections around it for rebuilding, since a partly filled block bends
 * the surface of the water beside it too.
 */
public final class CarrierWater {
    private static final ConcurrentHashMap<Long, Byte> LEVELS = new ConcurrentHashMap<>();

    private CarrierWater() { }

    public static void initialize() {
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            chunk.onAttachedSet(WaterStorage.CARRIER_LEVELS).register((before, after) -> apply(level, before, after));
            WaterStorage.CarrierLevels present = chunk.getAttached(WaterStorage.CARRIER_LEVELS);
            if (present != null) apply(level, null, present);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> forget(chunk));
    }

    /** The visible step to draw at this position, or 0 where vanilla's own height stands. */
    public static int level(BlockPos pos) {
        Byte step = LEVELS.get(pos.asLong());
        return step == null ? 0 : step;
    }

    private static void apply(ClientLevel level, WaterStorage.CarrierLevels before,
                              WaterStorage.CarrierLevels after) {
        LongSet touched = new LongOpenHashSet();
        if (before != null) touched.addAll(before.positions());
        if (after != null) touched.addAll(after.positions());
        LongSet sections = new LongOpenHashSet();
        touched.forEach((long pos) -> {
            byte was = before == null ? 0 : before.level(pos);
            byte now = after == null ? 0 : after.level(pos);
            if (was == now) return;
            if (now == 0) LEVELS.remove(pos);
            else LEVELS.put(pos, now);
            sections.add(SectionPos.asLong(BlockPos.of(pos)));
        });
        sections.forEach((long section) -> level.setSectionDirtyWithNeighbors(
                SectionPos.x(section), SectionPos.y(section), SectionPos.z(section)));
    }

    private static void forget(LevelChunk chunk) {
        WaterStorage.CarrierLevels present = chunk.getAttached(WaterStorage.CARRIER_LEVELS);
        if (present != null) present.positions().forEach((long pos) -> LEVELS.remove(pos));
    }
}
