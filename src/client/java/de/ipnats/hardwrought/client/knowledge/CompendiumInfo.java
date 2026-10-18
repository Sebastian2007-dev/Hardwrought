package de.ipnats.hardwrought.client.knowledge;

import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Predicate;

/**
 * The few lines of what a thing is good for, under its name on the left page.
 *
 * <p>Written in the language files, not here: {@code compendium.<namespace>.info.<path>} for one
 * thing. Most of the mod comes in families — a powder, a rod and a plate of every metal, a head of
 * every tool in every alloy — and a line written thirty times over says nothing the first did not, so
 * a thing without a line of its own falls back to its family's, {@code compendium.hardwrought.info.family.<name>}.
 * A thing neither names has no such lines, and its page is drawn as before.
 */
public final class CompendiumInfo {
    /** Materials whose finished tools and armor share one line; the longer name first, where one starts another. */
    private static final String[] ALLOYS = {"tungsten_steel", "stainless_steel", "steel", "titanium", "mithril", "adamant",
            "ashcork"};
    private static final String[] WORKED = {"_pickaxe", "_axe", "_shovel", "_hoe", "_sword", "_dagger", "_greatsword",
            "_halberd", "_hammer", "_helmet", "_chestplate", "_leggings", "_boots"};
    private static final String[] PARTS = {"_pickaxe_head", "_axe_head", "_shovel_head", "_hoe_head", "_sword_blade",
            "_dagger_blade", "_greatsword_blade", "_halberd_head", "_hammer_head"};
    private static final String[] ARMOR_PARTS = {"_helmet_shell", "_cuirass", "_greaves", "_sabatons"};

    /** A family and how its members are told: in order, the first that fits is the one. */
    private record Family(String name, Predicate<String> member) { }

    private static final List<Family> FAMILIES = List.of(
            new Family("unfired_cast", path -> path.startsWith("unfired_") && path.endsWith("_cast")),
            new Family("cast", path -> path.endsWith("_cast")),
            new Family("tool_part", path -> endsWithAny(path, PARTS)),
            new Family("armor_part", path -> endsWithAny(path, ARMOR_PARTS)),
            new Family("nether_ore", path -> path.startsWith("nether_") && path.endsWith("_ore")),
            new Family("ore", path -> path.endsWith("_ore")),
            new Family("raw_block", path -> path.startsWith("raw_") && path.endsWith("_block")),
            new Family("raw", path -> path.startsWith("raw_")),
            new Family("powder", path -> path.endsWith("_powder")),
            new Family("rod", path -> path.endsWith("_rod")),
            new Family("plate", path -> path.endsWith("_plate")),
            new Family("ingot", path -> path.endsWith("_ingot")),
            new Family("item_pipe", path -> path.startsWith("item_pipe_")),
            new Family("fluid_pipe", path -> path.startsWith("fluid_pipe_")),
            new Family("gas_line", path -> path.startsWith("gas_line_")),
            new Family("mark", path -> path.endsWith("_mk2") || path.endsWith("_mk3") || path.endsWith("_mk4")),
            new Family("void_crystal_bud", path -> path.endsWith("_void_crystal_bud") || path.equals("void_crystal_cluster")));

    private CompendiumInfo() { }

    /** The language key of what the book says about this thing, or null where it says nothing. */
    public static String key(Identifier id) {
        if (id == null) return null;
        Language language = Language.getInstance();
        String own = "compendium." + id.getNamespace() + ".info." + id.getPath();
        if (language.has(own)) return own;
        // A bucket or a bottle of something is that something, carried.
        for (String carrier : new String[] {"_bucket", "_bottle"}) {
            if (!id.getPath().endsWith(carrier)) continue;
            String held = "compendium." + id.getNamespace() + ".info."
                    + id.getPath().substring(0, id.getPath().length() - carrier.length());
            if (language.has(held)) return held;
        }
        if (!id.getNamespace().equals("hardwrought")) return null;
        String alloy = alloyOf(id.getPath());
        if (alloy != null && language.has("compendium.hardwrought.info.family." + alloy)) {
            return "compendium.hardwrought.info.family." + alloy;
        }
        for (Family family : FAMILIES) {
            if (!family.member().test(id.getPath())) continue;
            String key = "compendium.hardwrought.info.family." + family.name();
            if (language.has(key)) return key;
        }
        return null;
    }

    /** The alloy a finished tool, weapon or armor piece is made of; its parts belong to the parts. */
    private static String alloyOf(String path) {
        if (endsWithAny(path, PARTS) || endsWithAny(path, ARMOR_PARTS)) return null;
        for (String alloy : ALLOYS) {
            if (!path.startsWith(alloy + "_")) continue;
            return endsWithAny(path, WORKED) ? alloy : null;
        }
        return null;
    }

    private static boolean endsWithAny(String path, String[] endings) {
        for (String ending : endings) if (path.endsWith(ending)) return true;
        return false;
    }
}
