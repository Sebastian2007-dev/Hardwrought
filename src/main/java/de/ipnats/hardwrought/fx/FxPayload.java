package de.ipnats.hardwrought.fx;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Play an effect: where, aimed at what, in which colour and how large. {@code entity} is the id of an
 * entity the effect should follow, or -1.
 */
public record FxPayload(String effect, Vec3 pos, Vec3 target, int color, float scale, int entity)
        implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(Hardwrought.id("fx_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = new StreamCodec<>() {
        @Override public FxPayload decode(RegistryFriendlyByteBuf buffer) {
            return new FxPayload(buffer.readUtf(64),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readInt(), buffer.readFloat(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, FxPayload payload) {
            buffer.writeUtf(payload.effect(), 64);
            buffer.writeDouble(payload.pos().x);
            buffer.writeDouble(payload.pos().y);
            buffer.writeDouble(payload.pos().z);
            buffer.writeDouble(payload.target().x);
            buffer.writeDouble(payload.target().y);
            buffer.writeDouble(payload.target().z);
            buffer.writeInt(payload.color());
            buffer.writeFloat(payload.scale());
            buffer.writeVarInt(payload.entity());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
