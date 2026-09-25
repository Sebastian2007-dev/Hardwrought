package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A middle click on a slot: sort the grid that slot belongs to. */
public record SortInventoryPayload(int containerId, int slot) implements CustomPacketPayload {
    public static final Type<SortInventoryPayload> TYPE = new Type<>(Hardwrought.id("sort_inventory_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SortInventoryPayload> CODEC = new StreamCodec<>() {
        @Override public SortInventoryPayload decode(RegistryFriendlyByteBuf buffer) {
            return new SortInventoryPayload(buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SortInventoryPayload payload) {
            buffer.writeVarInt(payload.containerId());
            buffer.writeVarInt(payload.slot());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
