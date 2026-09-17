package de.ipnats.hardwrought.core.save;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.survival.PlayerVitals;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Saved once per world by Minecraft's save lifecycle, including save-all and shutdown. */
public final class CoreSaveData extends SavedData {
    public static final Codec<CoreSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 1).fieldOf("schema_version").forGetter(data -> 1),
            Codec.LONG.validate(value -> value >= 0 && value < Long.MAX_VALUE
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "Invalid simulation clock"))
                    .fieldOf("simulation_ticks").forGetter(CoreSaveData::ticks),
            Codec.unboundedMap(Codec.STRING, PlayerVitals.CODEC).optionalFieldOf("player_vitals", java.util.Map.of())
                    .forGetter(data -> java.util.Map.copyOf(data.playerVitals))
    ).apply(instance, (version, ticks, vitals) -> new CoreSaveData(ticks, vitals)));
    public static final SavedDataType<CoreSaveData> TYPE = new SavedDataType<>(
            Hardwrought.id("core"), () -> new CoreSaveData(0, java.util.Map.of()), CODEC, null);

    private long ticks;
    private final java.util.Map<String, PlayerVitals> playerVitals;

    private CoreSaveData(long ticks, java.util.Map<String, PlayerVitals> playerVitals) {
        this.ticks = ticks;
        this.playerVitals = new java.util.HashMap<>(playerVitals);
    }

    public long ticks() {
        return ticks;
    }

    public void setTicks(long ticks) {
        if (ticks < this.ticks) throw new IllegalArgumentException("Simulation time cannot move backwards");
        if (ticks != this.ticks) {
            this.ticks = ticks;
            setDirty();
        }
    }

    public PlayerVitals vitals(java.util.UUID playerId) {
        return playerVitals.computeIfAbsent(playerId.toString(), ignored -> PlayerVitals.defaults());
    }

    public void setVitals(java.util.UUID playerId, PlayerVitals vitals) {
        PlayerVitals value = vitals.normalized();
        if (!value.equals(playerVitals.put(playerId.toString(), value))) setDirty();
    }
}
