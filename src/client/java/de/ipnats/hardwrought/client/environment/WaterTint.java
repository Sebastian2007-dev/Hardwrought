package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.water.WaterQuality;
import de.ipnats.hardwrought.water.WaterQualityStorage;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Water shows what is in it. Purified water is a clear, healthy light blue; swamp water is murky;
 * seawater greyer and greener than a lake; river water a shade off clean. Plain water keeps the
 * colour its biome gives it.
 *
 * <p>The server sends, with every chunk and again when it changes, the quality of each cell that
 * differs from its surroundings ({@link WaterQualityStorage#SHOWN}) — a salted pond, a purified
 * spring, a cauldron of seawater. Everything else is judged by its biome, the way the server's own
 * fallback judges it for the open sea. Sections are built on worker threads, so what they read is a
 * concurrent map, and a change marks the sections around it for rebuilding.
 */
public final class WaterTint {
    private static final ConcurrentHashMap<Long, WaterQuality> MARKS = new ConcurrentHashMap<>();

    private WaterTint() { }

    public static void initialize() {
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            chunk.onAttachedSet(WaterQualityStorage.SHOWN).register((before, after) -> apply(level, before, after));
            WaterQualityStorage.Marks present = chunk.getAttached(WaterQualityStorage.SHOWN);
            if (present != null) apply(level, null, present);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> forget(chunk));
    }

    /** What the water at this position is, as far as the client can tell. */
    public static WaterQuality at(BlockPos pos) {
        WaterQuality marked = MARKS.get(pos.asLong());
        if (marked != null) return marked;
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? WaterQuality.FRESH : WaterQualityStorage.shownByDefault(level, pos);
    }

    /** The colour vanilla and the season gave the water here, pulled towards what is in it. */
    public static int tint(int color, BlockPos pos) {
        WaterQuality quality = at(pos);
        float blend = quality.worldBlend();
        if (blend <= 0) return color;
        int towards = quality.worldTint();
        int red = Math.round((color >> 16 & 0xFF) + ((towards >> 16 & 0xFF) - (color >> 16 & 0xFF)) * blend);
        int green = Math.round((color >> 8 & 0xFF) + ((towards >> 8 & 0xFF) - (color >> 8 & 0xFF)) * blend);
        int blue = Math.round((color & 0xFF) + ((towards & 0xFF) - (color & 0xFF)) * blend);
        // The alpha byte is carried through untouched: zero there would make the water invisible.
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void apply(ClientLevel level, WaterQualityStorage.Marks before, WaterQualityStorage.Marks after) {
        LongSet touched = new LongOpenHashSet();
        if (before != null) touched.addAll(before.positions());
        if (after != null) touched.addAll(after.positions());
        LongSet sections = new LongOpenHashSet();
        touched.forEach((long pos) -> {
            WaterQuality was = before == null ? null : before.at(pos);
            WaterQuality now = after == null ? null : after.at(pos);
            if (was == now) return;
            if (now == null) MARKS.remove(pos);
            else MARKS.put(pos, now);
            sections.add(SectionPos.asLong(BlockPos.of(pos)));
        });
        sections.forEach((long section) -> level.setSectionDirtyWithNeighbors(
                SectionPos.x(section), SectionPos.y(section), SectionPos.z(section)));
    }

    private static void forget(LevelChunk chunk) {
        WaterQualityStorage.Marks present = chunk.getAttached(WaterQualityStorage.SHOWN);
        if (present != null) present.positions().forEach((long pos) -> MARKS.remove(pos));
    }
}
