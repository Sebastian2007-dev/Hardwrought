package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Melting points per item, sent once when a player joins so an ore's tooltip can say how hot a
 * furnace has to get for it. The numbers come from the material definitions in a datapack and
 * therefore live on the server.
 *
 * @param points degrees Celsius per item id, for every item that melts as a known material
 */
public record MeltingPointPayload(Map<Identifier, Double> points) implements CustomPacketPayload {
    public static final Type<MeltingPointPayload> TYPE = new Type<>(Hardwrought.id("melting_points_v1"));
    /** Far more than the metals ever produce, and small enough that the packet stays a packet. */
    public static final int MAX_ENTRIES = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, MeltingPointPayload> CODEC = new StreamCodec<>() {
        @Override
        public MeltingPointPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = Math.min(buffer.readVarInt(), MAX_ENTRIES);
            Map<Identifier, Double> points = new HashMap<>(count);
            for (int index = 0; index < count; index++) {
                points.put(buffer.readIdentifier(), buffer.readDouble());
            }
            return new MeltingPointPayload(points);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MeltingPointPayload payload) {
            buffer.writeVarInt(payload.points.size());
            payload.points.forEach((id, point) -> {
                buffer.writeIdentifier(id);
                buffer.writeDouble(point);
            });
        }
    };

    public MeltingPointPayload {
        points = Map.copyOf(points);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
