package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.atlas.Atlas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The messages of the atlas. The server draws and keeps it; the client is sent each chunk once, as it
 * is drawn, and the whole of it when the book is opened.
 */
public final class AtlasPayloads {
    /** The most chunks one message carries. */
    public static final int MAX_CHUNKS = 512;

    private AtlasPayloads() { }

    /**
     * Server to client: chunks of the atlas of this dimension.
     *
     * @param chunks the chunks' places (see {@link Atlas#key})
     * @param tiles  their squares, {@link Atlas#TILE} bytes for each, in the same order
     * @param fresh  whether what the client had of this dimension is to be thrown away first
     */
    public record Tiles(Identifier dimension, long[] chunks, byte[] tiles, boolean fresh) implements CustomPacketPayload {
        public static final Type<Tiles> TYPE = new Type<>(Hardwrought.id("atlas_tiles_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tiles> CODEC = StreamCodec.of(
                (buffer, tiles) -> {
                    buffer.writeIdentifier(tiles.dimension);
                    buffer.writeBoolean(tiles.fresh);
                    buffer.writeVarInt(tiles.chunks.length);
                    for (long chunk : tiles.chunks) buffer.writeLong(chunk);
                    buffer.writeBytes(tiles.tiles);
                },
                buffer -> {
                    Identifier dimension = buffer.readIdentifier();
                    boolean fresh = buffer.readBoolean();
                    int count = Math.min(buffer.readVarInt(), MAX_CHUNKS);
                    long[] chunks = new long[count];
                    for (int i = 0; i < count; i++) chunks[i] = buffer.readLong();
                    byte[] tiles = new byte[count * Atlas.TILE];
                    buffer.readBytes(tiles);
                    return new Tiles(dimension, chunks, tiles, fresh);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: every place marked in the atlas of this dimension. */
    public record Markers(Identifier dimension, List<Atlas.Marker> markers) implements CustomPacketPayload {
        public static final Type<Markers> TYPE = new Type<>(Hardwrought.id("atlas_markers_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Markers> CODEC = StreamCodec.of(
                (buffer, markers) -> {
                    buffer.writeIdentifier(markers.dimension);
                    buffer.writeVarInt(markers.markers.size());
                    for (Atlas.Marker marker : markers.markers) {
                        buffer.writeInt(marker.x());
                        buffer.writeInt(marker.z());
                        buffer.writeUtf(marker.name(), Atlas.MAX_NAME);
                        buffer.writeByte(marker.kind());
                    }
                },
                buffer -> {
                    Identifier dimension = buffer.readIdentifier();
                    int count = Math.min(buffer.readVarInt(), Atlas.MAX_MARKERS);
                    List<Atlas.Marker> markers = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        markers.add(new Atlas.Marker(buffer.readInt(), buffer.readInt(), buffer.readUtf(Atlas.MAX_NAME), buffer.readByte()));
                    }
                    return new Markers(dimension, markers);
                });

        public Markers {
            markers = List.copyOf(markers);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the book was opened; send the whole atlas of this dimension. */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Hardwrought.id("atlas_request_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: mark this place, under this name. */
    public record Mark(int x, int z, String name) implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(Hardwrought.id("atlas_mark_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Mark> CODEC = StreamCodec.of(
                (buffer, mark) -> {
                    buffer.writeInt(mark.x);
                    buffer.writeInt(mark.z);
                    buffer.writeUtf(mark.name, Atlas.MAX_NAME);
                },
                buffer -> new Mark(buffer.readInt(), buffer.readInt(), buffer.readUtf(Atlas.MAX_NAME)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: take this mark out again, by its place in the list. */
    public record Unmark(int index) implements CustomPacketPayload {
        public static final Type<Unmark> TYPE = new Type<>(Hardwrought.id("atlas_unmark_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Unmark> CODEC = StreamCodec.of(
                (buffer, unmark) -> buffer.writeVarInt(unmark.index), buffer -> new Unmark(buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
