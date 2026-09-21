package de.ipnats.hardwrought.geology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * The rock a region is made of, specification section 51.
 *
 * <p>Geology is its own layer of the world. It is not derived from the biome, because what grows on
 * top of the ground and what the ground is made of are two different questions — a granite massif
 * does not stop being granite because a forest grows on it. A region has one rock type, derived from
 * the world seed, and the rock decides what can be found in it.
 *
 * <p>The ores each type carries are not listed here but in a datapack profile, so what a granite
 * region is worth is a design decision rather than a constant.
 */
public enum RockType {
    /** Intrusive rock: the old, hard core of a mountain. Iron, gold in quartz veins, emerald. */
    GRANITE("granite"),
    /** Where the deep came up: copper, redstone, and the pipes that carry diamond. */
    VOLCANIC("volcanic"),
    /** Laid down in water, layer by layer: coal, copper, lapis. */
    SEDIMENTARY("sedimentary");

    public static final Codec<RockType> CODEC = Codec.STRING.comapFlatMap(name -> {
        RockType value = byName(name);
        return value == null ? DataResult.error(() -> "Unknown rock type: " + name) : DataResult.success(value);
    }, RockType::serializedName);

    private final String serializedName;

    RockType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    /** The id a datapack profile claims this rock type under. */
    public Identifier id() {
        return Hardwrought.id(serializedName);
    }

    public static RockType byName(String name) {
        if (name == null) return null;
        for (RockType value : values()) {
            if (value.serializedName.equals(name.toLowerCase(Locale.ROOT))) return value;
        }
        return null;
    }

    public static RockType byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown rock type: " + ordinal);
        }
        return values()[ordinal];
    }
}
