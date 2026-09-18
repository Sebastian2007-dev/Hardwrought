package de.ipnats.hardwrought.combat;

import de.ipnats.hardwrought.core.registry.ProfileDefinitions;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Datapack directory hardwrought/armor_profiles, indexed per item. */
public final class ArmorProfiles extends ProfileDefinitions<ArmorProfile> {
    public static final DataResourceStore.Key<Map<Identifier, ArmorProfile>> KEY = new DataResourceStore.Key<>();

    public ArmorProfiles() {
        super("hardwrought/armor_profiles", ArmorProfile.CODEC, ArmorProfile::items);
    }

    @Override
    protected DataResourceStore.Key<Map<Identifier, ArmorProfile>> key() {
        return KEY;
    }
}
