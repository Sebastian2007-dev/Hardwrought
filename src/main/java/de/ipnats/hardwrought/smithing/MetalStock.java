package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.metallurgy.Metal;
import de.ipnats.hardwrought.metallurgy.ModMetals;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Stock: the rods and plates every forgeable metal is hammered into on the anvil, from which frames,
 * machines and armour are built.
 *
 * <p>Every metal that is solid and comes as an ingot has both; mercury, which is neither, has none.
 * An ingot drawn out long on the anvil is cut into four rods; an ingot beaten flat is one plate. Both
 * melt as the metal they are made of, and glow while hot like any other forged piece.
 */
public final class MetalStock {
    /** The two shapes stock comes in, and how many come off one ingot. */
    public enum Form {
        ROD(4),
        PLATE(1);

        private final int perIngot;

        Form(int perIngot) {
            this.perIngot = perIngot;
        }

        public int perIngot() {
            return perIngot;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A metal stock is made of: its material id and its ingot. */
    public record StockMetal(String material, Supplier<Item> ingot) { }

    private static final Map<String, StockMetal> METALS = new LinkedHashMap<>();
    private static final Map<String, Map<Form, Item>> ITEMS = new LinkedHashMap<>();

    private MetalStock() { }

    /** Every metal that has stock, vanilla's first. */
    public static List<StockMetal> metals() {
        if (METALS.isEmpty()) {
            add("iron", () -> Items.IRON_INGOT);
            add("copper", () -> Items.COPPER_INGOT);
            add("gold", () -> Items.GOLD_INGOT);
            add("bronze", () -> ModItems.BRONZE_INGOT);
            add("steel", () -> de.ipnats.hardwrought.metallurgy.Alloys.STEEL_INGOT);
            add("stainless_steel", () -> de.ipnats.hardwrought.metallurgy.Alloys.STAINLESS_STEEL_INGOT);
            add("tungsten_steel", () -> de.ipnats.hardwrought.metallurgy.Alloys.TUNGSTEN_STEEL_INGOT);
            for (Metal metal : Metal.values()) {
                // Liquid at room temperature: there is nothing to hammer.
                if (metal == Metal.MERCURY) continue;
                add(metal.id(), () -> ModMetals.ingot(metal));
            }
        }
        return List.copyOf(METALS.values());
    }

    private static void add(String material, Supplier<Item> ingot) {
        METALS.put(material, new StockMetal(material, ingot));
    }

    public static void initialize() {
        if (!ITEMS.isEmpty()) return;
        for (StockMetal metal : metals()) {
            Map<Form, Item> forms = new LinkedHashMap<>();
            for (Form form : Form.values()) {
                String name = metal.material() + "_" + form.id();
                ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
                forms.put(form, Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key))));
            }
            ITEMS.put(metal.material(), forms);
        }
    }

    /** The rod or plate of a metal, by its material id. */
    public static Item of(String material, Form form) {
        Map<Form, Item> forms = ITEMS.get(material);
        if (forms == null) throw new IllegalStateException("No stock of " + material);
        return forms.get(form);
    }

    public static Item rod(String material) {
        return of(material, Form.ROD);
    }

    public static Item plate(String material) {
        return of(material, Form.PLATE);
    }

    /** Every rod and plate, metal by metal. */
    public static List<Item> all() {
        List<Item> all = new ArrayList<>();
        ITEMS.values().forEach(forms -> all.addAll(forms.values()));
        return all;
    }

    /** The material a piece of stock is made of, or null where the item is not stock. */
    public static String materialOf(Item item) {
        for (var entry : ITEMS.entrySet()) {
            if (entry.getValue().containsValue(item)) return entry.getKey();
        }
        return null;
    }
}
