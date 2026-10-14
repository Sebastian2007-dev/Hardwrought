package de.ipnats.hardwrought.knowledge;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shelves of the compendium, from specification section 80.
 *
 * <p>The list is the one the specification gives, in its order, because the compendium replaces the
 * wiki as well as the recipe book and a player will learn where things live. Which shelf an entry
 * sits on is worked out from the item itself rather than written down per item: a mod that adds a
 * thousand items should not need a thousand lines here.
 */
public enum KnowledgeCategory {
    MATERIALS("materials"),
    CRAFTING("crafting"),
    METALLURGY("metallurgy"),
    ENGINEERING("engineering"),
    AGRICULTURE("agriculture"),
    BIOLOGY("biology"),
    MEDICINE("medicine"),
    CHEMISTRY("chemistry"),
    MAGIC("magic"),
    ELECTRICITY("electricity");

    private final String serializedName;

    KnowledgeCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "knowledge.hardwrought.category." + serializedName;
    }

    /**
     * Which shelf this belongs on. Read off what the item <em>is</em> — tags, components and the
     * block behind it — so the answer stays right for items nobody here has heard of.
     */
    public static KnowledgeCategory of(ItemLike itemLike) {
        if (itemLike == null) return MATERIALS;
        Item item = itemLike.asItem();
        ItemStack stack = new ItemStack(item);
        if (item instanceof de.ipnats.hardwrought.magic.WandItem
                || item == de.ipnats.hardwrought.magic.Magic.RUNE_STONE.asItem()) {
            return MAGIC;
        }
        KnowledgeCategory listed = listed().get(item);
        if (listed != null) return listed;
        if (stack.has(DataComponents.POTION_CONTENTS)) return MEDICINE;
        if (stack.has(DataComponents.FOOD)) return AGRICULTURE;
        if (stack.has(DataComponents.TOOL) || stack.is(ItemTags.SWORDS)
                || stack.has(DataComponents.EQUIPPABLE)) {
            return CRAFTING;
        }
        if (isMetal(stack)) return METALLURGY;
        if (isMechanism(item)) return ENGINEERING;
        if (isCircuitry(stack)) return ELECTRICITY;
        if (isLiving(item)) return BIOLOGY;
        return MATERIALS;
    }

    private static Map<Item, KnowledgeCategory> listed;

    /**
     * The oil age is written down, since nothing about a bucket of naphtha or a circuit says what it is:
     * oil, gas and what is made of them on the chemistry shelf; engines, parts and the big machines on
     * engineering; circuits, electronics and the machines on current on electricity.
     */
    private static Map<Item, KnowledgeCategory> listed() {
        if (listed != null) return listed;
        Map<Item, KnowledgeCategory> map = new java.util.HashMap<>();
        java.util.function.BiConsumer<KnowledgeCategory, java.util.Collection<? extends ItemLike>> put =
                (category, items) -> items.forEach(item -> map.put(item.asItem(), category));
        put.accept(CHEMISTRY, de.ipnats.hardwrought.refining.RefineryFluids.items());
        put.accept(CHEMISTRY, de.ipnats.hardwrought.refining.RefineryBlocks.items());
        put.accept(CHEMISTRY, de.ipnats.hardwrought.gasline.GasLines.items());
        put.accept(CHEMISTRY, de.ipnats.hardwrought.materials.Materials.items());
        put.accept(CHEMISTRY, de.ipnats.hardwrought.materials.MaterialBlocks.items());
        put.accept(CHEMISTRY, de.ipnats.hardwrought.chemistry.SaltPan.items());
        put.accept(MEDICINE, de.ipnats.hardwrought.health.Herbs.items());
        put.accept(CHEMISTRY, de.ipnats.hardwrought.water.WaterTreatmentBlocks.items());
        put.accept(CHEMISTRY, List.of(de.ipnats.hardwrought.core.registry.ModItems.STILL,
                de.ipnats.hardwrought.core.registry.ModItems.CRUDE_OIL_BUCKET, de.ipnats.hardwrought.core.registry.ModItems.GAS_CANISTER,
                de.ipnats.hardwrought.core.registry.ModItems.NATURAL_GAS_CANISTER, de.ipnats.hardwrought.core.registry.ModItems.LIGHT_FRACTION,
                de.ipnats.hardwrought.core.registry.ModItems.FUEL_FRACTION, de.ipnats.hardwrought.core.registry.ModItems.HEAVY_OIL,
                de.ipnats.hardwrought.core.registry.ModItems.BITUMEN, de.ipnats.hardwrought.core.registry.ModItems.RAW_SULFUR,
                de.ipnats.hardwrought.core.registry.ModItems.SULFUR, de.ipnats.hardwrought.core.registry.ModItems.SALT));
        put.accept(ENGINEERING, de.ipnats.hardwrought.power.PowerBlocks.items());
        put.accept(ENGINEERING, de.ipnats.hardwrought.fluids.FluidBlocks.items());
        put.accept(ENGINEERING, de.ipnats.hardwrought.multiblock.BigMachines.items());
        put.accept(ENGINEERING, List.of(de.ipnats.hardwrought.core.registry.ModItems.DRILLING_RIG));
        put.accept(ENGINEERING, de.ipnats.hardwrought.parts.Parts.items());
        put.accept(ENGINEERING, de.ipnats.hardwrought.storage.StorageBlocks.parts());
        put.accept(ELECTRICITY, de.ipnats.hardwrought.electricity.ElectricBlocks.items());
        put.accept(ELECTRICITY, List.of(de.ipnats.hardwrought.parts.Parts.REDSTONE_COIL, de.ipnats.hardwrought.parts.Parts.ELECTRIC_MOTOR,
                de.ipnats.hardwrought.parts.Parts.CIRCUIT_BOARD, de.ipnats.hardwrought.parts.Parts.RESISTOR,
                de.ipnats.hardwrought.parts.Parts.CAPACITOR, de.ipnats.hardwrought.parts.Parts.TRANSISTOR,
                de.ipnats.hardwrought.parts.Parts.MICROCHIP, de.ipnats.hardwrought.parts.Parts.QUARTZ_OSCILLATOR,
                de.ipnats.hardwrought.parts.Parts.BASIC_CIRCUIT, de.ipnats.hardwrought.parts.Parts.ADVANCED_CIRCUIT,
                de.ipnats.hardwrought.parts.Parts.ELITE_CIRCUIT));
        listed = map;
        return map;
    }

    private static boolean isMetal(ItemStack stack) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().is(BlockTags.ORES)) {
            return true;
        }
        String path = key(stack.getItem());
        return path.endsWith("_ingot") || path.endsWith("_nugget") || path.startsWith("raw_")
                || path.endsWith("_ore") || path.contains("bronze") || path.contains("steel");
    }

    private static boolean isMechanism(Item item) {
        if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        return block.defaultBlockState().is(BlockTags.DOORS) || block.defaultBlockState().is(BlockTags.RAILS)
                || key(item).contains("piston") || key(item).contains("dispenser")
                || key(item).contains("hopper") || key(item).contains("drill");
    }

    private static boolean isCircuitry(ItemStack stack) {
        String path = key(stack.getItem());
        return path.contains("redstone") || path.contains("copper_bulb") || path.contains("comparator")
                || path.contains("repeater") || path.contains("lever") || path.contains("wire")
                || path.contains("dynamo") || path.contains("insulator") || path.contains("electric")
                || path.contains("voltmeter") || path.contains("copper_cable") || path.equals("mast")
                || path.equals("battery") || path.equals("basic_crusher") || path.equals("sawmill")
                || path.equals("plate_press") || path.equals("copper_coil");
    }

    private static boolean isLiving(Item item) {
        if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)) return false;
        var state = blockItem.getBlock().defaultBlockState();
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS);
    }

    private static String key(Item item) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.getPath();
    }

    public static KnowledgeCategory byName(String name) {
        if (name == null) return null;
        for (KnowledgeCategory category : values()) {
            if (category.serializedName.equals(name.toLowerCase(Locale.ROOT))) return category;
        }
        return null;
    }

    public static KnowledgeCategory byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown knowledge category: " + ordinal);
        }
        return values()[ordinal];
    }
}
