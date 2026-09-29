package de.ipnats.hardwrought.building;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Datapack directory {@code hardwrought/structure}: which blocks are made of which material. A file
 * names one material from {@code hardwrought/materials} and lists blocks and block tags
 * ({@code #minecraft:planks}). A block named directly wins over a tag; among tags the higher
 * {@code priority} wins, so a general {@code #minecraft:stairs} can sit under {@code #minecraft:wooden_stairs}.
 */
public final class StructuralProfiles extends SimpleReloadListener<List<StructuralProfiles.Profile>> {
    private static final String DIRECTORY = "hardwrought/structure";
    public static final DataResourceStore.Key<List<Profile>> KEY = new DataResourceStore.Key<>();

    public record Profile(Identifier material, List<String> blocks, int priority) {
        public static final Codec<Profile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("material").forGetter(Profile::material),
                Codec.STRING.listOf().fieldOf("blocks").forGetter(Profile::blocks),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(Profile::priority)
        ).apply(instance, Profile::new));
    }

    @Override
    protected List<Profile> prepare(PreparableReloadListener.SharedState state) {
        List<Profile> result = new ArrayList<>();
        state.resourceManager().listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        Profile profile = Profile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                                .getOrThrow(message -> new IllegalArgumentException(path + ": " + message));
                        if (profile.blocks().isEmpty()) throw new IllegalArgumentException("Profile lists no blocks");
                        for (String entry : profile.blocks()) {
                            // Fails the reload on a malformed name instead of silently never matching.
                            Identifier.parse(entry.startsWith("#") ? entry.substring(1) : entry);
                        }
                        result.add(profile);
                    } catch (IOException | RuntimeException exception) {
                        // Abort the complete reload; never run with half a building code.
                        throw new IllegalStateException("Invalid structural profile " + path, exception);
                    }
                });
        return List.copyOf(result);
    }

    @Override
    protected void apply(List<Profile> prepared, PreparableReloadListener.SharedState state) {
        state.get(DataResourceLoader.DATA_RESOURCE_STORE_KEY).put(KEY, prepared);
    }
}
