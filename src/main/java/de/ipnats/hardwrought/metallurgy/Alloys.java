package de.ipnats.hardwrought.metallurgy;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * The steels (mechanics specification section 56: "iron + carbon \u2192 steel", "iron + chromium + nickel
 * \u2192 stainless steel") and tungsten steel. They are made only in the smeltery, alloyed in its tank;
 * see {@code de.ipnats.hardwrought.smeltery.MoltenMetals}.
 */
public final class Alloys {
    public static final Item STEEL_INGOT = register("steel_ingot");
    public static final Item STAINLESS_STEEL_INGOT = register("stainless_steel_ingot");
    public static final Item TUNGSTEN_STEEL_INGOT = register("tungsten_steel_ingot");

    private Alloys() { }

    /** Touching the class registers everything in it; called before the parts and stock are made. */
    public static void initialize() { }

    private static Item register(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }
}
