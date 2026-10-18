package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: drink a handful from the water in sight or underfoot. Vanilla sends nothing for an
 * empty hand unless a block was hit, and the water itself is no block to the aim; so the client says so
 * itself, and the server finds the water again and judges.
 */
public record DrinkByHandPayload() implements CustomPacketPayload {
    public static final Type<DrinkByHandPayload> TYPE = new Type<>(Hardwrought.id("drink_by_hand_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DrinkByHandPayload> CODEC = StreamCodec.unit(new DrinkByHandPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
