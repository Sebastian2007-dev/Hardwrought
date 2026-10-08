package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A shield's life as the clients draw it: raised, struck, gone. The sphere they draw lasts exactly
 * as long as the shield the server keeps, and shows every blow it takes.
 *
 * @param entity for a shield that moves with its caster: the player it surrounds; for a fixed one,
 *               a negative number naming it
 * @param radius its radius in blocks
 * @param color its colour, from its element
 * @param ticks for {@link #UP}: how long it will stand at most
 * @param event {@link #UP}, {@link #HIT} or {@link #DOWN}
 * @param fixed whether it stands where it was raised rather than moving with its caster
 * @param x for a fixed shield: its middle
 * @param y for a fixed shield: its middle
 * @param z for a fixed shield: its middle
 */
public record ShieldPayload(int entity, float radius, int color, int ticks, byte event, boolean fixed, double x, double y,
                            double z) implements CustomPacketPayload {
    public static final byte UP = 0, HIT = 1, DOWN = 2;

    public static final Type<ShieldPayload> TYPE = new Type<>(Hardwrought.id("shield_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldPayload> CODEC = new StreamCodec<>() {
        @Override
        public ShieldPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ShieldPayload(buffer.readVarInt(), buffer.readFloat(), buffer.readInt(), buffer.readVarInt(), buffer.readByte(),
                    buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ShieldPayload payload) {
            buffer.writeVarInt(payload.entity());
            buffer.writeFloat(payload.radius());
            buffer.writeInt(payload.color());
            buffer.writeVarInt(payload.ticks());
            buffer.writeByte(payload.event());
            buffer.writeBoolean(payload.fixed());
            buffer.writeDouble(payload.x());
            buffer.writeDouble(payload.y());
            buffer.writeDouble(payload.z());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
