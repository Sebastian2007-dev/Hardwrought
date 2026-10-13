package de.ipnats.hardwrought.oil;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Crude oil standing in the world: lakes of it, as lava stands in lakes, in caves now and then and
 * rarely in the open. Oil a player can find without a rig — enough to start with, never a field's worth.
 * The lakes are vanilla's lake feature with oil in it ({@code worldgen/feature/lake_oil.json}).
 */
public final class OilLakes {
    /** In caves, about half as often as lava lakes: one in sixteen chunks, between Y −48 and 48. */
    public static final ResourceKey<PlacedFeature> UNDERGROUND =
            ResourceKey.create(Registries.PLACED_FEATURE, Hardwrought.id("lake_oil_underground"));
    /** In the open: one in 160 chunks. */
    public static final ResourceKey<PlacedFeature> SURFACE =
            ResourceKey.create(Registries.PLACED_FEATURE, Hardwrought.id("lake_oil_surface"));

    private OilLakes() { }

    public static void initialize() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LAKES, UNDERGROUND);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LAKES, SURFACE);
    }
}
