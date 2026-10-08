package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Where the caster wants an open far gate to lead, as block coordinates. */
public record GateTargetPayload(int x, int y, int z) implements CustomPacketPayload {
    public static final Type<GateTargetPayload> TYPE = new Type<>(Hardwrought.id("gate_target_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GateTargetPayload> CODEC = new StreamCodec<>() {
        @Override
        public GateTargetPayload decode(RegistryFriendlyByteBuf buffer) {
            return new GateTargetPayload(buffer.readInt(), buffer.readInt(), buffer.readInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, GateTargetPayload payload) {
            buffer.writeInt(payload.x());
            buffer.writeInt(payload.y());
            buffer.writeInt(payload.z());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
