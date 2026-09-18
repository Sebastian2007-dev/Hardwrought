package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * What the standing water of a biome is, per specification section 23.2. Vanilla has tags for ocean
 * and river but none for swamp, so the biomes whose water is organic and stagnant are named here
 * rather than guessed at from anything else.
 *
 * @param standing what a still body in these biomes contains
 * @param flowing  what running water there contains; moving water carries less, so it defaults one
 *                 step better than standing water rather than being invented per biome
 */
public record WaterQualityProfile(List<Identifier> biomes, WaterQuality standing,
                                  Optional<WaterQuality> flowing) {
    public static final Codec<WaterQualityProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("biomes").forGetter(WaterQualityProfile::biomes),
            WaterQuality.CODEC.fieldOf("standing").forGetter(WaterQualityProfile::standing),
            WaterQuality.CODEC.optionalFieldOf("flowing").forGetter(WaterQualityProfile::flowing)
    ).apply(instance, WaterQualityProfile::new));

    public WaterQualityProfile {
        if (biomes == null || standing == null || flowing == null) {
            throw new IllegalArgumentException("Water quality profile needs biomes and a standing quality");
        }
        biomes = List.copyOf(biomes);
    }

    public WaterQuality quality(boolean moving) {
        if (!moving) return standing;
        return flowing.orElseGet(() -> standing == WaterQuality.SWAMP ? WaterQuality.RIVER : standing);
    }
}
