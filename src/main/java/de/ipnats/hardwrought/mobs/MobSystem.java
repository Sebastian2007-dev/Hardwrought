package de.ipnats.hardwrought.mobs;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

/**
 * Milestone "Mobs I" of the mob specification: hostile mobs made dangerous by what they do, not by
 * more health. This wires the parts together; each lives in its own class.
 */
public final class MobSystem {
    private MobSystem() { }

    public static void initialize() {
        Breaching.initialize();
        MobHits.initialize();
        MobHeat.initialize();
        SpiderWebGoal.initialize();
        MobWorld.initialize();
        // § 39.1: creakings come from hearts grown into the trees of every forest.
        TreeHearts.initialize();
        ServerEntityEvents.ENTITY_LOAD.register(MobTraits::onLoad);
    }
}
