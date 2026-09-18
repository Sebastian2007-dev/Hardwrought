package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.environment.GasMixture;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Display-only view of the air a player is breathing.
 *
 * @param instrumented section 18.2: carbon dioxide cannot be sensed, so exact numbers are only shown
 *                     to a player carrying an instrument. Without one the client shows symptoms.
 */
public record EnvironmentSnapshotPayload(GasMixture gases, double temperature, double wind,
                                         boolean sealed, int volume,
                                         boolean instrumented) implements CustomPacketPayload {
    public static final Type<EnvironmentSnapshotPayload> TYPE = new Type<>(Hardwrought.id("environment_snapshot_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnvironmentSnapshotPayload> CODEC = new StreamCodec<>() {
        @Override
        public EnvironmentSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
            GasMixture gases = new GasMixture(buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble());
            return new EnvironmentSnapshotPayload(gases, buffer.readDouble(), buffer.readDouble(),
                    buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, EnvironmentSnapshotPayload payload) {
            buffer.writeDouble(payload.gases.oxygen());
            buffer.writeDouble(payload.gases.carbonDioxide());
            buffer.writeDouble(payload.gases.methane());
            buffer.writeDouble(payload.gases.smoke());
            buffer.writeDouble(payload.temperature);
            buffer.writeDouble(payload.wind);
            buffer.writeBoolean(payload.sealed);
            buffer.writeVarInt(payload.volume);
            buffer.writeBoolean(payload.instrumented);
        }
    };

    public EnvironmentSnapshotPayload {
        if (gases == null || !Double.isFinite(temperature) || !Double.isFinite(wind)
                || temperature < -80 || temperature > 1_200 || wind < 0 || wind > 1
                || volume < 0 || volume > 100_000) {
            throw new IllegalArgumentException("Invalid environment snapshot");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
