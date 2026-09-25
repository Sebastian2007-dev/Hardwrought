package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sneak and swing at a gas block: push it one block the way the player is looking. */
public record GasPushPayload(BlockPos pos, Direction direction) implements CustomPacketPayload {
    public static final Type<GasPushPayload> TYPE = new Type<>(Hardwrought.id("gas_push_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GasPushPayload> CODEC = new StreamCodec<>() {
        @Override public GasPushPayload decode(RegistryFriendlyByteBuf buffer) {
            return new GasPushPayload(buffer.readBlockPos(), Direction.from3DDataValue(buffer.readVarInt()));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, GasPushPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeVarInt(payload.direction().get3DDataValue());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
