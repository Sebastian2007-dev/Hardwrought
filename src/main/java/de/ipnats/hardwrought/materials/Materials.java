package de.ipnats.hardwrought.materials;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * The materials of the oil age, between the raw stuff and the parts:
 * <ul>
 *   <li>caustic soda, left over from splitting salt for its chlorine, which etches circuit boards;
 *   <li>plastic, polymerised out of naphtha and LPG into pellets, and pressed into sheets;
 *   <li>rubber, resin vulcanised with sulfur;
 *   <li>silicon, quartz reduced with coke in an arc furnace, and sawn into wafers;
 *   <li>silicone, silicon worked with chlorine and naphtha, which keeps its seal hot and under pressure.
 * </ul>
 */
public final class Materials {
    public static final Item CAUSTIC_SODA = item("caustic_soda");
    public static final Item PLASTIC_PELLETS = item("plastic_pellets");
    public static final Item PLASTIC_SHEET = item("plastic_sheet");
    public static final Item RUBBER = item("rubber");
    public static final Item RAW_SILICON = item("raw_silicon");
    public static final Item SILICON_WAFER = item("silicon_wafer");
    public static final Item SILICONE = item("silicone");

    private Materials() { }

    /** Touching the class registers the materials. */
    public static void initialize() { }

    public static List<Item> items() {
        return List.of(CAUSTIC_SODA, PLASTIC_PELLETS, PLASTIC_SHEET, RUBBER, RAW_SILICON, SILICON_WAFER, SILICONE);
    }

    private static Item item(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }
}
