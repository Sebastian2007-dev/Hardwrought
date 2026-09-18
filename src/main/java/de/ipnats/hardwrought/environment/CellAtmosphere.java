package de.ipnats.hardwrought.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The part of an environmental cell that is worth saving. Geometry is re-derived by a scan, so only
 * the atmosphere a room has built up over time has to survive a restart.
 */
public record CellAtmosphere(GasMixture gases, double temperature, long updatedTick) {
    public static final Codec<CellAtmosphere> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GasMixture.CODEC.fieldOf("gases").forGetter(CellAtmosphere::gases),
            Codec.doubleRange(-80, 1_200).fieldOf("temperature").forGetter(CellAtmosphere::temperature),
            Codec.LONG.fieldOf("updated_tick").forGetter(CellAtmosphere::updatedTick)
    ).apply(instance, CellAtmosphere::new));

    public CellAtmosphere {
        if (gases == null || !Double.isFinite(temperature) || temperature < -80 || temperature > 1_200
                || updatedTick < 0) {
            throw new IllegalArgumentException("Invalid cell atmosphere");
        }
    }
}
