package de.ipnats.hardwrought.metallurgy;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.MaterialDefinition;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * How hot a furnace gets, and how hot a thing has to get before it gives up its metal.
 *
 * <p>Every ore has a melting point, read from the material definitions in the datapack
 * ({@code data/<namespace>/hardwrought/materials/<metal>.json}), so the numbers can be tuned with a
 * reload rather than a rebuild. Which items belong to which metal is not listed anywhere: an ore
 * block, its deepslate twin, the raw chunk and the powder of a metal all melt as that metal, and a
 * metal added to {@link Metal} is covered the moment it is.
 *
 * <p>A furnace smelts what it can bring to its melting point and leaves the rest where it lies,
 * without burning fuel on it. That is what makes the ladder of furnaces a ladder: the brick furnace
 * gets as far as iron and no further, the stone furnace reaches the hard metals, and only a blast
 * furnace melts tungsten.
 *
 * <p>Anything without a melting point — food, sand, clay — is not limited here at all.
 */
public final class Smelting {
    /** A brick furnace just reaches iron, at 1538 °C, and nothing that melts hotter. */
    public static final double BRICK_FURNACE_MAX_C = 1550;
    /** A stone furnace: titanium, platinum, chromium. */
    public static final double FURNACE_MAX_C = 2000;
    /** A blast furnace: everything, tungsten included. */
    public static final double BLAST_FURNACE_MAX_C = 3500;

    private static final Map<Item, Identifier> MATERIAL_OF = new HashMap<>();

    private Smelting() { }

    /** How hot this furnace can get. */
    public static double maxTemperature(AbstractFurnaceBlockEntity furnace) {
        if (furnace instanceof BrickFurnaceBlockEntity) return BRICK_FURNACE_MAX_C;
        if (furnace instanceof BlastFurnaceBlockEntity) return BLAST_FURNACE_MAX_C;
        return FURNACE_MAX_C;
    }

    /** Which material this item melts as, or null where it is not a metal-bearing thing at all. */
    public static Identifier materialOf(Item item) {
        return table().get(item);
    }

    /** The melting point of what this item is made of, where the datapack gives one. */
    public static OptionalDouble meltingPoint(Item item, Map<Identifier, MaterialDefinition> materials) {
        Identifier material = materialOf(item);
        if (material == null || materials == null) return OptionalDouble.empty();
        MaterialDefinition definition = materials.get(material);
        if (definition == null || definition.meltingPointC().isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(definition.meltingPointC().get());
    }

    /**
     * Whether this furnace gets hot enough for what is in it. Answers yes when nothing is known,
     * because a missing number must never stop a furnace that vanilla would have run.
     *
     * <p>Two different questions under one name. Powder and mixture are cast: they have to melt, so
     * the furnace has to reach the melting point. Everything else metal is only heated for the anvil,
     * and needs the furnace to reach working heat — well below melting, which is the whole reason a
     * brick furnace can bring titanium to the anvil but could never cast it.
     */
    public static boolean hotEnough(ServerLevel level, AbstractFurnaceBlockEntity furnace, ItemStack input) {
        if (input.isEmpty()) return true;
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return true;
        OptionalDouble melting = meltingPoint(input.getItem(), runtime.materials());
        if (melting.isEmpty()) return true;
        double needed = isCast(input.getItem())
                ? melting.getAsDouble()
                : melting.getAsDouble() * de.ipnats.hardwrought.smithing.Smithing.WORKING_MIN;
        return needed <= maxTemperature(furnace);
    }

    /** Powder and mixture are melted and cast; everything else metal is heated and forged. */
    public static boolean isCast(Item item) {
        if (item == ModItems.BRONZE_MIXTURE) return true;
        return OrePowders.all().contains(item);
    }

    /** Melting point per item id, for every item that has one. What the client needs for tooltips. */
    public static Map<Identifier, Double> itemMeltingPoints(Map<Identifier, MaterialDefinition> materials) {
        Map<Identifier, Double> result = new HashMap<>();
        for (Item item : table().keySet()) {
            OptionalDouble melting = meltingPoint(item, materials);
            if (melting.isPresent()) result.put(BuiltInRegistries.ITEM.getKey(item), melting.getAsDouble());
        }
        return result;
    }

    /**
     * Built on first use: the metals and their powders are registered by the mod initializer, and
     * do not exist when this class is first loaded.
     */
    private static Map<Item, Identifier> table() {
        if (MATERIAL_OF.isEmpty()) {
            put("iron", Items.RAW_IRON, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE,
                    OrePowders.powder(OrePowders.VanillaOre.IRON));
            put("copper", Items.RAW_COPPER, Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE,
                    OrePowders.powder(OrePowders.VanillaOre.COPPER));
            put("gold", Items.RAW_GOLD, Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE, Items.NETHER_GOLD_ORE,
                    OrePowders.powder(OrePowders.VanillaOre.GOLD),
                    OrePowders.powder(OrePowders.VanillaOre.NETHER_GOLD));
            put("netherite", Items.ANCIENT_DEBRIS, OrePowders.powder(OrePowders.VanillaOre.ANCIENT_DEBRIS));
            put("bronze", ModItems.BRONZE_MIXTURE, ModItems.BRONZE_INGOT);
            put("iron", Items.IRON_INGOT);
            put("copper", Items.COPPER_INGOT);
            put("gold", Items.GOLD_INGOT);
            for (Metal metal : Metal.values()) {
                put(metal.id(), ModMetals.raw(metal), ModMetals.ore(metal), ModMetals.deepslateOre(metal),
                        OrePowders.powder(metal), ModMetals.ingot(metal));
            }
            // A forged head or blade melts as the metal it was forged from.
            for (Item part : de.ipnats.hardwrought.smithing.ToolParts.all()) {
                put(de.ipnats.hardwrought.smithing.ToolParts.metalOf(part).material(), part);
            }
        }
        return MATERIAL_OF;
    }

    private static void put(String material, ItemLike... items) {
        Identifier id = Hardwrought.id(material);
        for (ItemLike item : items) MATERIAL_OF.put(item.asItem(), id);
    }
}
