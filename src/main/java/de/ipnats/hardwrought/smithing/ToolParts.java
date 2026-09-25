package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The heads and blades a metal tool is made of. A tool is no longer crafted out of ingots: the part
 * is forged on the anvil, and the part and its handle make the tool.
 *
 * <p>Registered only for the metals that have tools, and only for the tools each metal has. A part
 * that nothing can be made from would be a trap.
 */
public final class ToolParts {
    /** One kind of head or blade, and how many ingots go into it. */
    public enum Part {
        PICKAXE_HEAD(3),
        AXE_HEAD(3),
        SHOVEL_HEAD(1),
        HOE_HEAD(2),
        SWORD_BLADE(2),
        DAGGER_BLADE(1),
        GREATSWORD_BLADE(4),
        HALBERD_HEAD(3),
        /** The head of the iron hammer. Only a hammer of stone or better can forge it. */
        HAMMER_HEAD(2);

        private final int ingots;

        Part(int ingots) {
            this.ingots = ingots;
        }

        public int ingots() {
            return ingots;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A metal that tools are forged from: its material id and its ingot. */
    public enum SmithMetal {
        IRON("iron", () -> Items.IRON_INGOT, Part.values()),
        GOLD("gold", () -> Items.GOLD_INGOT, Part.PICKAXE_HEAD, Part.AXE_HEAD, Part.SHOVEL_HEAD,
                Part.HOE_HEAD, Part.SWORD_BLADE),
        COPPER("copper", () -> Items.COPPER_INGOT, Part.PICKAXE_HEAD, Part.AXE_HEAD, Part.SHOVEL_HEAD,
                Part.HOE_HEAD, Part.SWORD_BLADE),
        BRONZE("bronze", () -> ModItems.BRONZE_INGOT, Part.PICKAXE_HEAD, Part.AXE_HEAD);

        private final String material;
        private final Supplier<Item> ingot;
        private final List<Part> parts;

        SmithMetal(String material, Supplier<Item> ingot, Part... parts) {
            this.material = material;
            this.ingot = ingot;
            this.parts = List.of(parts);
        }

        public String material() {
            return material;
        }

        public Item ingot() {
            return ingot.get();
        }

        public List<Part> parts() {
            return parts;
        }
    }

    private static final Map<SmithMetal, Map<Part, Item>> ITEMS = new EnumMap<>(SmithMetal.class);

    private ToolParts() { }

    public static void initialize() {
        if (!ITEMS.isEmpty()) return;
        for (SmithMetal metal : SmithMetal.values()) {
            Map<Part, Item> parts = new LinkedHashMap<>();
            for (Part part : metal.parts()) {
                String name = metal.material() + "_" + part.id();
                ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
                parts.put(part, Registry.register(BuiltInRegistries.ITEM, key,
                        new Item(new Item.Properties().setId(key).stacksTo(1))));
            }
            ITEMS.put(metal, parts);
        }
    }

    public static Item part(SmithMetal metal, Part part) {
        Item item = ITEMS.getOrDefault(metal, Map.of()).get(part);
        if (item == null) throw new IllegalStateException(metal + " has no " + part);
        return item;
    }

    /** Every part item, metal by metal. */
    public static List<Item> all() {
        List<Item> all = new ArrayList<>();
        ITEMS.values().forEach(parts -> all.addAll(parts.values()));
        return all;
    }

    /** Which metal a part is made of, or null where the item is not a part. */
    public static SmithMetal metalOf(Item item) {
        for (var entry : ITEMS.entrySet()) {
            if (entry.getValue().containsValue(item)) return entry.getKey();
        }
        return null;
    }
}
