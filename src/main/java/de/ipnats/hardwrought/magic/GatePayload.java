package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A far gate stands open: the caster is asked where it should lead.
 *
 * @param range how far it reaches, in blocks
 * @param seconds how long it stays open
 * @param anchored whether runes across the shaft hold it steady; an unsteady gate lands wide
 */
public record GatePayload(int range, int seconds, boolean anchored) implements CustomPacketPayload {
    public static final Type<GatePayload> TYPE = new Type<>(Hardwrought.id("gate_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GatePayload> CODEC = new StreamCodec<>() {
        @Override
        public GatePayload decode(RegistryFriendlyByteBuf buffer) {
            return new GatePayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, GatePayload payload) {
            buffer.writeVarInt(payload.range());
            buffer.writeVarInt(payload.seconds());
            buffer.writeBoolean(payload.anchored());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
