package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * What the ground under a biome holds, per specification sections 23.6 and 68. The water table of a
 * region is derived from the world seed, but how much water that region actually carries, how fast
 * it comes back and what it tastes like is a property of the place — and that is a design decision,
 * so it belongs in a datapack rather than in a constant.
 *
 * @param biomes      the biomes this aquifer describes
 * @param richness    how much water the ground holds and how fast it refills, relative to ordinary
 *                    ground. A desert is a fraction of it, a jungle several times it. Zero is dry
 *                    rock: a well there never yields anything, however deep it is dug.
 * @param tableOffset blocks the table sits above (positive) or below (negative) its seed-derived
 *                    height. This is where regional water scarcity comes from.
 * @param quality     what the water in this ground is. Coastal ground holds seawater, and a well
 *                    dug next to the sea therefore yields brine no matter how deep it goes.
 */
public record AquiferProfile(List<Identifier> biomes, double richness, int tableOffset,
                             WaterQuality quality) {
    public static final Codec<AquiferProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("biomes").forGetter(AquiferProfile::biomes),
            Codec.doubleRange(0.0, 8.0).optionalFieldOf("richness", 1.0).forGetter(AquiferProfile::richness),
            Codec.intRange(-64, 64).optionalFieldOf("table_offset", 0).forGetter(AquiferProfile::tableOffset),
            WaterQuality.CODEC.optionalFieldOf("quality", WaterQuality.FRESH).forGetter(AquiferProfile::quality)
    ).apply(instance, AquiferProfile::new));

    public AquiferProfile {
        if (biomes == null || quality == null || !Double.isFinite(richness) || richness < 0) {
            throw new IllegalArgumentException("Aquifer profile needs biomes, a quality and a finite richness");
        }
        biomes = List.copyOf(biomes);
    }

    /** True where the ground carries no water at all, so no amount of digging opens a spring. */
    public boolean dry() {
        return richness <= 0;
    }
}
