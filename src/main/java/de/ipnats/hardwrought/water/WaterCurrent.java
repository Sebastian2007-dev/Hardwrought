package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Water that moves without going anywhere.
 *
 * <p>The water in this world is finite: it runs downhill until it lies level, and then it stops. A
 * river, though, has to keep flowing, or nothing could ever be driven by it. So a river's current is
 * not its water moving from one cell to the next — that would drain it into the sea in an afternoon —
 * but a direction and a strength laid over the water, the way the wind is laid over the air. It
 * pushes what floats in it and turns wheels set into it, and the river stays where it is.
 *
 * <p>Two kinds of current exist:
 * <ul>
 *   <li><b>river current</b>, worked out once per chunk when a chunk in a river biome is first
 *       loaded: along the river, towards the nearer sea. Saved with the chunk and sent to the client,
 *       so players drift with it the same on both sides;</li>
 *   <li><b>running water</b>: water that really is moving — draining, falling, pouring out of a
 *       bucket — leaves a short-lived current along its way. Server side only, and gone a couple of
 *       seconds after the water settles.</li>
 * </ul>
 */
public final class WaterCurrent {
    /** How strong a river's current is, where one exists. Roughly the push of vanilla flowing water. */
    public static final float RIVER_STRENGTH = 0.6f;
    /** How deep under the surface a river's current still reaches. */
    private static final int RIVER_DEPTH = 12;
    /** How far along a river to look for the sea its current runs to. */
    private static final int SEA_SEARCH = 768;
    /** How long running water's current outlasts the movement that made it, in ticks. */
    private static final int RUNNING_TICKS = 40;
    /** A transfer of this many millibuckets in one pass counts as a full-strength current. */
    private static final float FULL_TRANSFER = 250.0f;

    /** A river's direction across one chunk, as a unit vector on the ground plane. */
    public record River(float dx, float dz) {
        static final Codec<River> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("dx").forGetter(River::dx),
                Codec.FLOAT.fieldOf("dz").forGetter(River::dz)
        ).apply(instance, River::new));
        static final StreamCodec<ByteBuf, River> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, River::dx, ByteBufCodecs.FLOAT, River::dz, River::new);
    }

    public static final AttachmentType<River> RIVER = AttachmentRegistry.<River>create(
            Hardwrought.id("river_current"), builder -> builder.persistent(River.CODEC)
                    .syncWith(River.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Which way water ran through a cell, and when. */
    private record Running(float dx, float dy, float dz, long time) { }

    /** Running water per level, by position. */
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<Running>> RUNNING = new WeakHashMap<>();

    private WaterCurrent() { }

    public static void initialize() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (chunk.getAttached(RIVER) == null) {
                River river = findRiver(level, chunk);
                if (river != null) chunk.setAttached(RIVER, river);
            }
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % RUNNING_TICKS != 0) return;
            Long2ObjectOpenHashMap<Running> running = RUNNING.get(level);
            if (running == null) return;
            long now = level.getGameTime();
            running.values().removeIf(entry -> now - entry.time() > RUNNING_TICKS);
        });
    }

    /**
     * The current in this water, as a vector whose length is its strength (about 0 to 1). Zero where
     * there is no water or it lies still. Safe to call on either side; running water is only known on
     * the server.
     */
    public static Vec3 at(BlockGetter getter, BlockPos pos) {
        if (!getter.getFluidState(pos).is(FluidTags.WATER)) return Vec3.ZERO;
        Vec3 current = Vec3.ZERO;
        if (getter instanceof LevelReader level && level.hasChunkAt(pos)) {
            int sea = level.getSeaLevel();
            if (pos.getY() <= sea && pos.getY() >= sea - RIVER_DEPTH) {
                ChunkAccess chunk = level.getChunk(pos);
                River river = chunk.getAttached(RIVER);
                if (river != null) current = new Vec3(river.dx() * RIVER_STRENGTH, 0, river.dz() * RIVER_STRENGTH);
            }
        }
        if (getter instanceof ServerLevel server) {
            Long2ObjectOpenHashMap<Running> running = RUNNING.get(server);
            Running entry = running == null ? null : running.get(pos.asLong());
            if (entry != null && server.getGameTime() - entry.time() <= RUNNING_TICKS) {
                current = current.add(entry.dx(), entry.dy(), entry.dz());
            }
        }
        return current;
    }

    /** Called for every transfer of water between two cells: the water in both is running that way. */
    public static void record(ServerLevel level, BlockPos from, BlockPos to, int millibuckets) {
        if (millibuckets <= 0) return;
        float strength = Math.min(1.0f, millibuckets / FULL_TRANSFER);
        float dx = (to.getX() - from.getX()) * strength;
        float dy = (to.getY() - from.getY()) * strength;
        float dz = (to.getZ() - from.getZ()) * strength;
        Long2ObjectOpenHashMap<Running> running = RUNNING.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
        Running entry = new Running(dx, dy, dz, level.getGameTime());
        running.put(from.asLong(), entry);
        running.put(to.asLong(), entry);
    }

    /**
     * Whether this chunk carries a river, and which way it runs. The river's line through the chunk
     * is the long axis of its water at sea level; which way along that line is towards the sea found
     * first when looking both ways, or — with no sea in reach — a way fixed for the whole region, so
     * that neighbouring chunks agree.
     */
    static River findRiver(ServerLevel level, LevelChunk chunk) {
        int sea = level.getSeaLevel();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        boolean river = false;
        for (int[] at : new int[][] {{8, 8}, {1, 1}, {14, 1}, {1, 14}, {14, 14}}) {
            if (level.getBiome(new BlockPos(baseX + at[0], sea, baseZ + at[1])).is(BiomeTags.IS_RIVER)) {
                river = true;
                break;
            }
        }
        if (!river) return null;

        int count = 0;
        double sumX = 0, sumZ = 0, sumXX = 0, sumZZ = 0, sumXZ = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                cursor.set(baseX + x, sea - 1, baseZ + z);
                if (!chunk.getFluidState(cursor).is(FluidTags.WATER)) continue;
                count++;
                sumX += x;
                sumZ += z;
                sumXX += x * x;
                sumZZ += z * z;
                sumXZ += x * z;
            }
        }
        if (count < 12) return null;
        double meanX = sumX / count, meanZ = sumZ / count;
        double varX = sumXX / count - meanX * meanX;
        double varZ = sumZZ / count - meanZ * meanZ;
        double cov = sumXZ / count - meanX * meanZ;
        double angle = 0.5 * Math.atan2(2 * cov, varX - varZ);
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        // A pool as wide as it is long has no line to flow along.
        if (Math.abs(varX - varZ) < 1.0 && Math.abs(cov) < 1.0) return null;

        int sign = seaward(level, baseX + 8, baseZ + 8, dx, dz, sea);
        return new River((float) (dx * sign), (float) (dz * sign));
    }

    private static int seaward(ServerLevel level, int x, int z, double dx, double dz, int sea) {
        for (int distance = 32; distance <= SEA_SEARCH; distance += 32) {
            if (isSea(level, x + dx * distance, z + dz * distance, sea)) return 1;
            if (isSea(level, x - dx * distance, z - dz * distance, sea)) return -1;
        }
        // No sea in reach either way: fixed per region of 256 blocks, so neighbours agree.
        long region = ((long) (x >> 8) * 0x9E3779B97F4A7C15L) ^ ((long) (z >> 8) * 0xC2B2AE3D27D4EB4FL);
        return (region & 1) == 0 ? 1 : -1;
    }

    private static boolean isSea(ServerLevel level, double x, double z, int sea) {
        return level.getUncachedNoiseBiome(((int) Math.floor(x)) >> 2, sea >> 2, ((int) Math.floor(z)) >> 2)
                .is(BiomeTags.IS_OCEAN);
    }
}
