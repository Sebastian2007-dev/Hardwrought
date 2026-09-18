package de.ipnats.hardwrought.core.save;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.environment.CellAtmosphere;
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
                    .forGetter(data -> java.util.Map.copyOf(data.playerVitals)),
            Codec.unboundedMap(Codec.STRING, CellAtmosphere.CODEC)
                    .optionalFieldOf("environment_cells", java.util.Map.of())
                    .forGetter(data -> java.util.Map.copyOf(data.cellAtmospheres))
    ).apply(instance, (version, ticks, vitals, cells) -> new CoreSaveData(ticks, vitals, cells)));
    public static final SavedDataType<CoreSaveData> TYPE = new SavedDataType<>(
            Hardwrought.id("core"), () -> new CoreSaveData(0, java.util.Map.of(), java.util.Map.of()), CODEC, null);
    /** Bounded so a long-lived world cannot grow an unlimited room table in its save file. */
    public static final int MAX_SAVED_CELLS = 256;

    private long ticks;
    private final java.util.Map<String, PlayerVitals> playerVitals;
    private final java.util.Map<String, CellAtmosphere> cellAtmospheres;

    private CoreSaveData(long ticks, java.util.Map<String, PlayerVitals> playerVitals,
                         java.util.Map<String, CellAtmosphere> cellAtmospheres) {
        this.ticks = ticks;
        this.playerVitals = new java.util.HashMap<>(playerVitals);
        this.cellAtmospheres = new java.util.HashMap<>(cellAtmospheres);
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

    /** Null means the room has no history yet and starts from outside air. */
    public CellAtmosphere cellAtmosphere(String cellKey) {
        return cellAtmospheres.get(cellKey);
    }

    public void setCellAtmosphere(String cellKey, CellAtmosphere atmosphere) {
        if (cellKey == null || atmosphere == null) throw new IllegalArgumentException("Invalid cell atmosphere");
        if (atmosphere.equals(cellAtmospheres.put(cellKey, atmosphere))) return;
        if (cellAtmospheres.size() > MAX_SAVED_CELLS) {
            // Forget the room that has gone unvisited longest rather than growing without a bound.
            cellAtmospheres.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(entry -> entry.getValue().updatedTick()))
                    .map(java.util.Map.Entry::getKey)
                    .ifPresent(cellAtmospheres::remove);
        }
        setDirty();
    }

    public int savedCellCount() {
        return cellAtmospheres.size();
    }
}
