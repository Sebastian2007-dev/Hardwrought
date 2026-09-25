package de.ipnats.hardwrought.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The part of an environmental cell that is worth saving. Geometry is re-derived by a scan, so only
 * the warmth a room has built up over time has to survive a restart. The gas in it is in the world
 * itself, as blocks.
 */
public record CellAtmosphere(double temperature, long updatedTick) {
    public static final Codec<CellAtmosphere> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(-80, 1_200).fieldOf("temperature").forGetter(CellAtmosphere::temperature),
            Codec.LONG.fieldOf("updated_tick").forGetter(CellAtmosphere::updatedTick)
    ).apply(instance, CellAtmosphere::new));

    public CellAtmosphere {
        if (!Double.isFinite(temperature) || temperature < -80 || temperature > 1_200
                || updatedTick < 0) {
            throw new IllegalArgumentException("Invalid cell atmosphere");
        }
    }
}
