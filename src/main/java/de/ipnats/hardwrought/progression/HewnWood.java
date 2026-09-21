package de.ipnats.hardwrought.progression;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * The timber a hewn workbench was cut out of.
 *
 * <p>A bench keeps the bark of its own tree. Cutting a birch and getting an oak stump underneath is
 * exactly the kind of detail that makes a block look pasted into the world, so the wood travels with
 * the block as a state and the model follows it.
 *
 * @param texture the block the lower half borrows its bark from
 */
public enum HewnWood implements StringRepresentable {
    OAK("oak", "oak_log"),
    SPRUCE("spruce", "spruce_log"),
    BIRCH("birch", "birch_log"),
    JUNGLE("jungle", "jungle_log"),
    ACACIA("acacia", "acacia_log"),
    DARK_OAK("dark_oak", "dark_oak_log"),
    PALE_OAK("pale_oak", "pale_oak_log"),
    POPLAR("poplar", "poplar_log"),
    MANGROVE("mangrove", "mangrove_log"),
    CHERRY("cherry", "cherry_log"),
    BAMBOO("bamboo", "bamboo_block"),
    CRIMSON("crimson", "crimson_stem"),
    WARPED("warped", "warped_stem");

    private final String name;
    private final String texture;

    HewnWood(String name, String texture) {
        this.name = name;
        this.texture = texture;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The vanilla block whose bark and end grain this wood is drawn with. */
    public String texture() {
        return texture;
    }

    /**
     * Which wood this log is. Read off the block id, which every vanilla and modded log follows:
     * the stripped, wood, stem and hyphae forms of a tree all name the same tree.
     */
    public static HewnWood of(BlockState state) {
        net.minecraft.resources.Identifier id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return OAK;
        String path = id.getPath().replace("stripped_", "");
        for (HewnWood wood : values()) {
            if (path.startsWith(wood.name)) return wood;
        }
        return OAK;
    }

    public static HewnWood byName(String name) {
        if (name == null) return null;
        for (HewnWood wood : values()) {
            if (wood.name.equals(name.toLowerCase(Locale.ROOT))) return wood;
        }
        return null;
    }
}
