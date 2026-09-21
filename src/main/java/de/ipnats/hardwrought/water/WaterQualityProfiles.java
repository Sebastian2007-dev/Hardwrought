package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.registry.ProfileDefinitions;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;

/** Datapack directory hardwrought/water_quality, indexed per biome. */
public final class WaterQualityProfiles extends ProfileDefinitions<WaterQualityProfile> {
    public static final DataResourceStore.Key<Map<Identifier, WaterQualityProfile>> KEY = new DataResourceStore.Key<>();

    public WaterQualityProfiles() {
        super("hardwrought/water_quality", WaterQualityProfile.CODEC, WaterQualityProfile::biomes);
    }

    /** What a datapack says about the water here, or null where no profile claims this biome. */
    public static WaterQuality declared(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        Identifier id = level.getBiome(pos).unwrapKey().map(key -> key.identifier()).orElse(null);
        if (id == null) return null;
        WaterQualityProfile profile = server.getOrThrow(KEY).get(id);
        return profile == null ? null : profile.quality(!level.getFluidState(pos).isSource());
    }

    /**
     * What the water at a position is by its surroundings alone, section 23.2. A datapack profile
     * decides first. Without one, ocean water is salt, river water is river water, and everything
     * else depends on whether it moves: running water counts as river water, a still pool as swamp
     * water. Water standing below the table is the region's groundwater, which is what makes a well
     * worth digging.
     *
     * <p>Deliberately cheap — block state, biome and arithmetic, no flood fill — because mixing
     * asks for it while water is moving. The one refinement that needs a measured body, telling a
     * large still lake from a stagnant pool, stays in {@code WaterSystem.qualityAt}.
     */
    public static WaterQuality ambient(ServerLevel level, BlockPos pos) {
        WaterQuality declared = declared(level, pos);
        if (declared != null) return declared;
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return WaterQuality.SALT;
        if (biome.is(BiomeTags.IS_RIVER)) return WaterQuality.RIVER;
        if (!level.getFluidState(pos).isSource()) return WaterQuality.RIVER;
        if (Groundwater.belowTable(level, pos)) return Groundwater.qualityOf(level, pos);
        return WaterQuality.SWAMP;
    }

    @Override
    protected DataResourceStore.Key<Map<Identifier, WaterQualityProfile>> key() {
        return KEY;
    }
}
