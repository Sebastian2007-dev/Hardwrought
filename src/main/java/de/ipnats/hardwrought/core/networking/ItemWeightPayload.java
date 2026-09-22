package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The weight table, sent once when a player joins so a tooltip can say what a thing weighs.
 *
 * <p>The weights come from a datapack and therefore live on the server. Without this the client
 * would have to guess, and would be wrong about exactly the items whose weight was worth writing
 * down.
 *
 * @param weights kilograms per item, for every item somebody has given a weight
 */
public record ItemWeightPayload(Map<Identifier, Double> weights) implements CustomPacketPayload {
    public static final Type<ItemWeightPayload> TYPE = new Type<>(Hardwrought.id("item_weights_v1"));
    /** Far more than the bundled table has, and small enough that the packet stays a packet. */
    public static final int MAX_ENTRIES = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemWeightPayload> CODEC = new StreamCodec<>() {
        @Override
        public ItemWeightPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = Math.min(buffer.readVarInt(), MAX_ENTRIES);
            Map<Identifier, Double> weights = new HashMap<>(count);
            for (int index = 0; index < count; index++) {
                weights.put(buffer.readIdentifier(), buffer.readDouble());
            }
            return new ItemWeightPayload(weights);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ItemWeightPayload payload) {
            buffer.writeVarInt(payload.weights.size());
            payload.weights.forEach((id, weight) -> {
                buffer.writeIdentifier(id);
                buffer.writeDouble(weight);
            });
        }
    };

    public ItemWeightPayload {
        weights = Map.copyOf(weights);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
