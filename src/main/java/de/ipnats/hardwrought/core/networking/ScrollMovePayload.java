package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** One notch of the scroll wheel over a slot: move one item out of it, or pull one of its kind in. */
public record ScrollMovePayload(int containerId, int slot, boolean push) implements CustomPacketPayload {
    public static final Type<ScrollMovePayload> TYPE = new Type<>(Hardwrought.id("scroll_move_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrollMovePayload> CODEC = new StreamCodec<>() {
        @Override public ScrollMovePayload decode(RegistryFriendlyByteBuf buffer) {
            return new ScrollMovePayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ScrollMovePayload payload) {
            buffer.writeVarInt(payload.containerId());
            buffer.writeVarInt(payload.slot());
            buffer.writeBoolean(payload.push());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
