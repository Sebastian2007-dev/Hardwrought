package de.ipnats.hardwrought.mobs;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biomes;

/**
 * Milestone "Mobs I" of the mob specification: hostile mobs made dangerous by what they do, not by
 * more health. This wires the parts together; each lives in its own class.
 */
public final class MobSystem {
    /** § 39.1: in an ordinary forest a creaking is very rare, next to about a hundred zombies. */
    static final int CREAKING_FOREST_WEIGHT = 1;
    /** Dark and old-growth forests: rare. */
    static final int CREAKING_DEEP_FOREST_WEIGHT = 3;
    /** Its own biome, where it is to be expected. */
    static final int CREAKING_PALE_GARDEN_WEIGHT = 30;

    private MobSystem() { }

    public static void initialize() {
        Breaching.initialize();
        MobHits.initialize();
        MobHeat.initialize();
        SpiderWebGoal.initialize();
        MobWorld.initialize();
        ServerEntityEvents.ENTITY_LOAD.register(MobTraits::onLoad);
        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_FOREST), MobCategory.MONSTER,
                EntityTypes.CREAKING, CREAKING_FOREST_WEIGHT, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.DARK_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
                        Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA),
                MobCategory.MONSTER, EntityTypes.CREAKING, CREAKING_DEEP_FOREST_WEIGHT, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PALE_GARDEN), MobCategory.MONSTER,
                EntityTypes.CREAKING, CREAKING_PALE_GARDEN_WEIGHT, 1, 1);
    }
}
