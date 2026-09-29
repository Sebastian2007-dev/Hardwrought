package de.ipnats.hardwrought.smeltery;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.MaterialDefinition;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.metallurgy.Metal;
import de.ipnats.hardwrought.metallurgy.ModMetals;
import de.ipnats.hardwrought.metallurgy.OrePowders;
import de.ipnats.hardwrought.smithing.MetalStock;
import de.ipnats.hardwrought.smithing.ToolParts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the smeltery melts, into how much, and what it alloys. Amounts are in millibuckets: an ingot
 * is {@value #INGOT}, as in Tinkers' Construct.
 *
 * <p>Carbon is not a metal but it is in the bath: coal powder dissolves into it, and iron with carbon
 * is steel.
 */
public final class MoltenMetals {
    public static final int INGOT = 144, NUGGET = 16, ROD = 36;
    /** An ore block is worth two ingots in the smeltery: the reward for bringing the ore whole. */
    public static final int ORE_BLOCK = 2 * INGOT;
    public static final int CARBON_PER_POWDER = 72;
    public static final String CARBON = "carbon";
    /** Carbon dissolves into the bath from this heat. */
    public static final double CARBON_DISSOLVES = 1000;

    /** Every molten material, in the order the render states number them. */
    public static final List<String> ORDER = List.of("iron", "gold", "copper", "bronze", "steel", "stainless_steel",
            "tungsten_steel", "titanium", "tungsten", "tin", "zinc", "lead", "manganese", "magnesium", "aluminum",
            "nickel", "cobalt", "chromium", "uranium", "thorium", "platinum", "netherite", CARBON);

    /** What one item gives: which material, how much of it. */
    public record Melt(String material, int amount) { }

    /** Input ratios, output ratio and the heat it takes. */
    public record Alloy(Map<String, Integer> inputs, String output, int outputRatio, double celsius) { }

    /** Section 56 of the specification, and Tinkers' own bronze. */
    public static final List<Alloy> ALLOYS = List.of(
            new Alloy(ratios("iron", 4, CARBON, 1), "steel", 4, 1540),
            new Alloy(ratios("steel", 3, "chromium", 1, "nickel", 1), "stainless_steel", 5, 1550),
            new Alloy(ratios("steel", 3, "tungsten", 1), "tungsten_steel", 4, 3422),
            new Alloy(ratios("copper", 3, "tin", 1), "bronze", 4, 1085));

    private static final Map<Item, Melt> MELTS = new HashMap<>();
    private static final Map<String, Item> INGOTS = new HashMap<>();

    private MoltenMetals() { }

    private static Map<String, Integer> ratios(Object... pairs) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], (Integer) pairs[i + 1]);
        return result;
    }

    /**
     * The alloys as the compendium shows them: what goes into the smeltery, in whole items, and the
     * bars that come out of the cast, with the controller as the station.
     */
    public static List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> worldRecipes() {
        net.minecraft.world.item.ItemStack station = new net.minecraft.world.item.ItemStack(SmelteryBlocks.CONTROLLER);
        Item coalPowder = de.ipnats.hardwrought.metallurgy.OrePowders.powder(
                de.ipnats.hardwrought.metallurgy.OrePowders.VanillaOre.COAL);
        List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> shown = new java.util.ArrayList<>();
        for (Alloy alloy : ALLOYS) {
            Item result = ingot(alloy.output());
            if (result == null) continue;
            List<List<net.minecraft.world.item.ItemStack>> inputs = new java.util.ArrayList<>();
            for (var input : alloy.inputs().entrySet()) {
                net.minecraft.world.item.ItemStack stack = CARBON.equals(input.getKey())
                        ? new net.minecraft.world.item.ItemStack(coalPowder, input.getValue() * INGOT / CARBON_PER_POWDER)
                        : new net.minecraft.world.item.ItemStack(ingot(input.getKey()), input.getValue());
                if (stack.isEmpty()) continue;
                inputs.add(List.of(stack));
            }
            shown.add(new de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe(
                    Hardwrought.id("smeltery/" + alloy.output()), inputs,
                    new net.minecraft.world.item.ItemStack(result, alloy.outputRatio()), station));
        }
        return shown;
    }

    public static int index(String material) {
        return Math.max(0, ORDER.indexOf(material));
    }

    /** What this item melts into, or null where the smeltery does not take it. */
    public static Melt melt(Item item) {
        return table().get(item);
    }

    /** The ingot a material is cast into, or null where it cannot be cast. */
    public static Item ingot(String material) {
        table();
        return INGOTS.get(material);
    }

    /** The heat at which this material melts, where known. */
    public static double meltingPoint(String material, Map<Identifier, MaterialDefinition> materials) {
        if (CARBON.equals(material)) return CARBON_DISSOLVES;
        MaterialDefinition definition = materials.get(Hardwrought.id(material));
        return definition == null || definition.meltingPointC().isEmpty() ? Double.MAX_VALUE : definition.meltingPointC().get();
    }

    private static Map<Item, Melt> table() {
        if (!MELTS.isEmpty()) return MELTS;
        for (MetalStock.StockMetal metal : MetalStock.metals()) {
            put(metal.ingot().get(), metal.material(), INGOT);
            INGOTS.put(metal.material(), metal.ingot().get());
            put(MetalStock.rod(metal.material()), metal.material(), ROD);
            put(MetalStock.plate(metal.material()), metal.material(), INGOT);
        }
        for (ToolParts.SmithMetal metal : ToolParts.SmithMetal.values()) {
            for (ToolParts.Part part : metal.parts()) {
                put(ToolParts.part(metal, part), metal.material(), part.ingots() * INGOT);
            }
        }
        for (Metal metal : Metal.values()) {
            if (metal == Metal.MERCURY) continue;
            put(ModMetals.raw(metal), metal.id(), INGOT);
            put(ModMetals.ore(metal).asItem(), metal.id(), ORE_BLOCK);
            put(ModMetals.deepslateOre(metal).asItem(), metal.id(), ORE_BLOCK);
            put(OrePowders.powder(metal), metal.id(), INGOT);
        }
        put(Items.RAW_IRON, "iron", INGOT);
        put(Items.IRON_ORE, "iron", ORE_BLOCK);
        put(Items.DEEPSLATE_IRON_ORE, "iron", ORE_BLOCK);
        put(Items.IRON_NUGGET, "iron", NUGGET);
        put(OrePowders.powder(OrePowders.VanillaOre.IRON), "iron", INGOT);
        put(Items.RAW_GOLD, "gold", INGOT);
        put(Items.GOLD_ORE, "gold", ORE_BLOCK);
        put(Items.DEEPSLATE_GOLD_ORE, "gold", ORE_BLOCK);
        put(Items.GOLD_NUGGET, "gold", NUGGET);
        put(OrePowders.powder(OrePowders.VanillaOre.GOLD), "gold", INGOT);
        put(Items.RAW_COPPER, "copper", INGOT);
        put(Items.COPPER_ORE, "copper", ORE_BLOCK);
        put(Items.DEEPSLATE_COPPER_ORE, "copper", ORE_BLOCK);
        put(OrePowders.powder(OrePowders.VanillaOre.COPPER), "copper", INGOT);
        put(Items.NETHERITE_INGOT, "netherite", INGOT);
        INGOTS.put("netherite", Items.NETHERITE_INGOT);
        put(ModItems.BRONZE_MIXTURE, "bronze", INGOT);
        put(OrePowders.powder(OrePowders.VanillaOre.COAL), CARBON, CARBON_PER_POWDER);
        return MELTS;
    }

    private static void put(Item item, String material, int amount) {
        MELTS.putIfAbsent(item, new Melt(material, amount));
    }
}
