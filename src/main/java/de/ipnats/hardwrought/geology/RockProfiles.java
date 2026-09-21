package de.ipnats.hardwrought.geology;

import de.ipnats.hardwrought.core.registry.ProfileDefinitions;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Datapack directory hardwrought/rock, indexed per rock type. */
public final class RockProfiles extends ProfileDefinitions<RockProfile> {
    public static final DataResourceStore.Key<Map<Identifier, RockProfile>> KEY = new DataResourceStore.Key<>();

    public RockProfiles() {
        super("hardwrought/rock", RockProfile.CODEC, RockProfile::rocks);
    }

    @Override
    protected DataResourceStore.Key<Map<Identifier, RockProfile>> key() {
        return KEY;
    }
}
