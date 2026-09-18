package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.registry.ProfileDefinitions;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Datapack directory hardwrought/water_quality, indexed per biome. */
public final class WaterQualityProfiles extends ProfileDefinitions<WaterQualityProfile> {
    public static final DataResourceStore.Key<Map<Identifier, WaterQualityProfile>> KEY = new DataResourceStore.Key<>();

    public WaterQualityProfiles() {
        super("hardwrought/water_quality", WaterQualityProfile.CODEC, WaterQualityProfile::biomes);
    }

    @Override
    protected DataResourceStore.Key<Map<Identifier, WaterQualityProfile>> key() {
        return KEY;
    }
}
