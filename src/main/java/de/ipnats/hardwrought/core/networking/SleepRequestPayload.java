package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SleepRequestPayload() implements CustomPacketPayload {
    public static final Type<SleepRequestPayload> TYPE = new Type<>(Hardwrought.id("sleep_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SleepRequestPayload> CODEC = new StreamCodec<>() {
        @Override public SleepRequestPayload decode(RegistryFriendlyByteBuf buffer) { return new SleepRequestPayload(); }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SleepRequestPayload payload) { }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
