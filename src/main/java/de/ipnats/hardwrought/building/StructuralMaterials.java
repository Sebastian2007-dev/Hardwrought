package de.ipnats.hardwrought.building;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.MaterialDefinition;
import de.ipnats.hardwrought.core.registry.MaterialDefinitions;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which material a block state is built of, as a {@link Statics.Member}, or empty for a block that
 * carries nothing — a torch, a rail, a flower. The answer is cached per block state and forgotten
 * whenever the datapacks or the tags are reloaded.
 */
public final class StructuralMaterials {
    /**
     * The material assumed for a full block no profile names — a modded block, a workbench. Absent
     * from the data, such blocks carry nothing.
     */
    public static final Identifier UNCLASSIFIED = Hardwrought.id("unclassified");
    private static final double GRAVITY = 9.81;

    private static final Map<BlockState, Optional<Statics.Member>> cache = new IdentityHashMap<>();
    private static List<StructuralProfiles.Profile> cachedProfiles;
    private static Map<Identifier, MaterialDefinition> cachedMaterials;

    private StructuralMaterials() { }

    public static void initialize() {
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (!client) cache.clear();
        });
    }

    public static Optional<Statics.Member> of(MinecraftServer server, BlockState state) {
        List<StructuralProfiles.Profile> profiles = server.getOrThrow(StructuralProfiles.KEY);
        Map<Identifier, MaterialDefinition> materials = server.getOrThrow(MaterialDefinitions.KEY);
        if (profiles != cachedProfiles || materials != cachedMaterials) {
            cache.clear();
            cachedProfiles = profiles;
            cachedMaterials = materials;
        }
        return cache.computeIfAbsent(state, s -> resolve(profiles, materials, s));
    }

    /** The material id this state is built of, whether or not it has structural data. */
    public static Optional<Identifier> materialId(MinecraftServer server, BlockState state) {
        return Optional.ofNullable(match(server.getOrThrow(StructuralProfiles.KEY), state))
                .or(() -> state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                        ? Optional.of(UNCLASSIFIED) : Optional.empty());
    }

    private static Optional<Statics.Member> resolve(List<StructuralProfiles.Profile> profiles,
                                                    Map<Identifier, MaterialDefinition> materials, BlockState state) {
        // Air, fluids, plants and anything else without a collision shape weigh on nothing.
        double volume = volume(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        if (volume <= 0) return Optional.empty();
        Identifier id = match(profiles, state);
        if (id == null) {
            if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return Optional.empty();
            id = UNCLASSIFIED;
        }
        MaterialDefinition material = materials.get(id);
        if (material == null || material.structure().isEmpty()) return Optional.empty();
        return Optional.of(new Statics.Member(material.structure().get(), material.densityKgM3() * volume * GRAVITY));
    }

    private static Identifier match(List<StructuralProfiles.Profile> profiles, BlockState state) {
        Identifier block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Identifier byTag = null;
        int tagPriority = Integer.MIN_VALUE;
        for (StructuralProfiles.Profile profile : profiles) {
            for (String entry : profile.blocks()) {
                if (entry.startsWith("#")) {
                    if (profile.priority() > tagPriority
                            && state.is(TagKey.create(Registries.BLOCK, Identifier.parse(entry.substring(1))))) {
                        byTag = profile.material();
                        tagPriority = profile.priority();
                    }
                } else if (Identifier.parse(entry).equals(block)) {
                    return profile.material();
                }
            }
        }
        return byTag;
    }

    private static double volume(VoxelShape shape) {
        double volume = 0;
        for (AABB box : shape.toAabbs()) volume += box.getXsize() * box.getYsize() * box.getZsize();
        return Math.min(1, volume);
    }
}
