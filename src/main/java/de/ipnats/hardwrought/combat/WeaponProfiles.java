package de.ipnats.hardwrought.combat;

import de.ipnats.hardwrought.core.registry.ItemProfileDefinitions;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Datapack directory hardwrought/weapon_profiles, indexed per item. */
public final class WeaponProfiles extends ItemProfileDefinitions<WeaponProfile> {
    public static final DataResourceStore.Key<Map<Identifier, WeaponProfile>> KEY = new DataResourceStore.Key<>();

    public WeaponProfiles() {
        super("hardwrought/weapon_profiles", WeaponProfile.CODEC, WeaponProfile::items);
    }

    @Override
    protected DataResourceStore.Key<Map<Identifier, WeaponProfile>> key() {
        return KEY;
    }
}
