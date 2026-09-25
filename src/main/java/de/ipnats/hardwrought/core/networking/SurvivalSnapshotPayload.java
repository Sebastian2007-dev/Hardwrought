package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.survival.Nutrition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SurvivalSnapshotPayload(double stamina, double hydration, Nutrition nutrition, double fatigue,
                                      double bodyTemperature, double ambientTemperature,
                                      double carriedKg, double capacityKg,
                                      boolean sleeping, double sleepQuality,
                                      double stress) implements CustomPacketPayload {
    public static final Type<SurvivalSnapshotPayload> TYPE = new Type<>(Hardwrought.id("survival_snapshot_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivalSnapshotPayload> CODEC = new StreamCodec<>() {
        @Override public SurvivalSnapshotPayload decode(RegistryFriendlyByteBuf b) {
            double stamina = b.readDouble();
            double hydration = b.readDouble();
            Nutrition nutrition = new Nutrition(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readDouble()).clamped();
            return new SurvivalSnapshotPayload(stamina, hydration, nutrition, b.readDouble(), b.readDouble(),
                    b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean(), b.readDouble(), b.readDouble());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, SurvivalSnapshotPayload p) {
            b.writeDouble(p.stamina); b.writeDouble(p.hydration);
            b.writeDouble(p.nutrition.protein()); b.writeDouble(p.nutrition.fat());
            b.writeDouble(p.nutrition.carbohydrates()); b.writeDouble(p.nutrition.vitamins());
            b.writeDouble(p.nutrition.fiber());
            b.writeDouble(p.fatigue); b.writeDouble(p.bodyTemperature);
            b.writeDouble(p.ambientTemperature); b.writeDouble(p.carriedKg); b.writeDouble(p.capacityKg);
            b.writeBoolean(p.sleeping); b.writeDouble(p.sleepQuality); b.writeDouble(p.stress);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
