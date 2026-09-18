package de.ipnats.hardwrought.combat;

import de.ipnats.hardwrought.core.registry.ItemProfileDefinitions;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Datapack directory hardwrought/shield_profiles, indexed per item. */
public final class ShieldProfiles extends ItemProfileDefinitions<ShieldProfile> {
    public static final DataResourceStore.Key<Map<Identifier, ShieldProfile>> KEY = new DataResourceStore.Key<>();

    public ShieldProfiles() {
        super("hardwrought/shield_profiles", ShieldProfile.CODEC, ShieldProfile::items);
    }

    @Override
    protected DataResourceStore.Key<Map<Identifier, ShieldProfile>> key() {
        return KEY;
    }
}
