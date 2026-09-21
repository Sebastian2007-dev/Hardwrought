package de.ipnats.hardwrought.metallurgy;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.Arrays;
import java.util.List;

/**
 * Puts the ores of {@link Metal} into the ground, and touches nothing that was already there.
 *
 * <p>Vanilla ore generation is deliberately left exactly as it is — an earlier attempt at replacing
 * it made a world that read as empty. These are additions: fifteen new ores in the depth bands their
 * table states, each of them scarce, and every vanilla vein still where it always was.
 */
public final class MetalWorldgen {
    private static boolean initialized;

    private MetalWorldgen() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        BiomeModification modification = BiomeModifications.create(Hardwrought.id("metal_ores"));
        modification.add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInOverworld(), context -> {
            for (ResourceKey<PlacedFeature> ore : oreFeatures()) {
                context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ore);
            }
        });
    }

    /** The placed features this mod adds, for tests and diagnostics. */
    public static List<ResourceKey<PlacedFeature>> oreFeatures() {
        return Arrays.stream(Metal.values())
                .map(metal -> ResourceKey.create(Registries.PLACED_FEATURE,
                        Hardwrought.id("ore_" + metal.id())))
                .toList();
    }
}
