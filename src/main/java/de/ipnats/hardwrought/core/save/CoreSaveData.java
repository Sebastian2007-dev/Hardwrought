package de.ipnats.hardwrought.core.save;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.environment.CellAtmosphere;
import de.ipnats.hardwrought.equipment.PlayerEquipment;
import de.ipnats.hardwrought.survival.PlayerVitals;
import de.ipnats.hardwrought.knowledge.PlayerKnowledge;
import de.ipnats.hardwrought.water.AquiferState;
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
                    .forGetter(data -> java.util.Map.copyOf(data.cellAtmospheres)),
            Codec.unboundedMap(Codec.STRING, AquiferState.CODEC)
                    .optionalFieldOf("aquifers", java.util.Map.of())
                    .forGetter(data -> java.util.Map.copyOf(data.aquifers)),
            Codec.unboundedMap(Codec.STRING, PlayerKnowledge.CODEC)
                    .optionalFieldOf("knowledge", java.util.Map.of())
                    .forGetter(data -> java.util.Map.copyOf(data.knowledge)),
            // Added after Milestone 8, so a world saved before packs existed keeps loading and
            // every player in it is handed a starter the next time they are looked at.
            Codec.unboundedMap(Codec.STRING, PlayerEquipment.CODEC)
                    .optionalFieldOf("equipment", java.util.Map.of())
                    .forGetter(data -> java.util.Map.copyOf(data.equipment))
    ).apply(instance, (version, ticks, vitals, cells, aquifers, knowledge, equipment) ->
            new CoreSaveData(ticks, vitals, cells, aquifers, knowledge, equipment)));
    public static final SavedDataType<CoreSaveData> TYPE = new SavedDataType<>(
            Hardwrought.id("core"),
            () -> new CoreSaveData(0, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                    java.util.Map.of(), java.util.Map.of()), CODEC, null);
    /** Bounded so a long-lived world cannot grow an unlimited room table in its save file. */
    public static final int MAX_SAVED_CELLS = 256;
    /**
     * The same bound for the water in the ground. Only regions that have been drawn on are stored;
     * a forgotten one counts as full again, which is what an untouched aquifer is.
     */
    public static final int MAX_SAVED_AQUIFERS = 256;

    private long ticks;
    private final java.util.Map<String, PlayerVitals> playerVitals;
    private final java.util.Map<String, CellAtmosphere> cellAtmospheres;
    private final java.util.Map<String, AquiferState> aquifers;
    private final java.util.Map<String, PlayerKnowledge> knowledge;
    private final java.util.Map<String, PlayerEquipment> equipment;

    private CoreSaveData(long ticks, java.util.Map<String, PlayerVitals> playerVitals,
                         java.util.Map<String, CellAtmosphere> cellAtmospheres,
                         java.util.Map<String, AquiferState> aquifers,
                         java.util.Map<String, PlayerKnowledge> knowledge,
                         java.util.Map<String, PlayerEquipment> equipment) {
        this.ticks = ticks;
        this.playerVitals = new java.util.HashMap<>(playerVitals);
        this.cellAtmospheres = new java.util.HashMap<>(cellAtmospheres);
        this.aquifers = new java.util.HashMap<>(aquifers);
        this.knowledge = new java.util.HashMap<>(knowledge);
        this.equipment = new java.util.HashMap<>(equipment);
    }

    /**
     * What this player wears. World data rather than player data, which is exactly what keeps a pack
     * out of the drop on death without anything having to intervene.
     */
    public PlayerEquipment equipment(java.util.UUID playerId) {
        return equipment.getOrDefault(playerId.toString(), PlayerEquipment.empty());
    }

    public void setEquipment(java.util.UUID playerId, PlayerEquipment value) {
        equipment.put(playerId.toString(), value == null ? PlayerEquipment.empty() : value);
        setDirty();
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

    /** Null means nothing has ever been taken out of this region, so its aquifer is full. */
    public AquiferState aquifer(String regionKey) {
        return aquifers.get(regionKey);
    }

    public void setAquifer(String regionKey, AquiferState state) {
        if (regionKey == null || state == null) throw new IllegalArgumentException("Invalid aquifer state");
        if (state.equals(aquifers.put(regionKey, state))) return;
        if (aquifers.size() > MAX_SAVED_AQUIFERS) {
            // Forget the region that has gone longest without being visited. It reverts to full,
            // which is where an aquifer nobody draws on ends up anyway.
            aquifers.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(entry -> entry.getValue().updatedTick()))
                    .map(java.util.Map.Entry::getKey)
                    .ifPresent(aquifers::remove);
        }
        setDirty();
    }

    /** A region that has filled back up is forgotten rather than saved forever as "full". */
    public void clearAquifer(String regionKey) {
        if (aquifers.remove(regionKey) != null) setDirty();
    }

    public int savedAquiferCount() {
        return aquifers.size();
    }

    /** Section 79: what one player has found out. A player nobody has tracked yet knows nothing. */
    public PlayerKnowledge knowledge(java.util.UUID playerId) {
        return knowledge.computeIfAbsent(playerId.toString(), ignored -> PlayerKnowledge.empty());
    }

    public void setKnowledge(java.util.UUID playerId, PlayerKnowledge value) {
        if (playerId == null || value == null) throw new IllegalArgumentException("Invalid knowledge");
        knowledge.put(playerId.toString(), value);
        setDirty();
    }
}
