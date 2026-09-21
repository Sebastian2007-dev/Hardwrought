package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.registry.ProfileDefinitions;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Datapack directory hardwrought/aquifer, indexed per biome. */
public final class AquiferProfiles extends ProfileDefinitions<AquiferProfile> {
    public static final DataResourceStore.Key<Map<Identifier, AquiferProfile>> KEY = new DataResourceStore.Key<>();

    public AquiferProfiles() {
        super("hardwrought/aquifer", AquiferProfile.CODEC, AquiferProfile::biomes);
    }

    @Override
    protected DataResourceStore.Key<Map<Identifier, AquiferProfile>> key() {
        return KEY;
    }
}
