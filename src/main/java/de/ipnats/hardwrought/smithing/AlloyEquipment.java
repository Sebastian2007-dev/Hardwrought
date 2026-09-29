package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The equipment above iron: steel, stainless steel, titanium and tungsten steel — tools, weapons and
 * armor for each. Tools and weapons are a forged head or blade on a handle (see {@link ToolParts});
 * armor is riveted from forged plates.
 *
 * <p>Each step is worth the trouble of reaching it:
 * <ul>
 *   <li><b>steel</b> — harder and far more lasting than iron, and it cuts what a diamond does. It
 *   rusts ({@link Rust});</li>
 *   <li><b>stainless steel</b> — as hard as steel, lasts longer still, and never rusts;</li>
 *   <li><b>titanium</b> — harder again, and armor of it weighs little more than half of steel's;</li>
 *   <li><b>tungsten steel</b> — the hardest and most lasting of all, and the heaviest.</li>
 * </ul>
 */
public final class AlloyEquipment {
    /** One kind of tool or weapon: what part it is made from, how many handles, how it swings. */
    public enum Tool {
        PICKAXE(ToolParts.Part.PICKAXE_HEAD, 2, 1.0f, -2.8f),
        AXE(ToolParts.Part.AXE_HEAD, 2, 6.0f, -3.1f),
        SHOVEL(ToolParts.Part.SHOVEL_HEAD, 2, 1.5f, -3.0f),
        HOE(ToolParts.Part.HOE_HEAD, 2, -2.0f, -1.0f),
        SWORD(ToolParts.Part.SWORD_BLADE, 1, 3.0f, -2.4f),
        DAGGER(ToolParts.Part.DAGGER_BLADE, 1, 1.0f, -1.5f),
        GREATSWORD(ToolParts.Part.GREATSWORD_BLADE, 2, 6.0f, -3.2f),
        HALBERD(ToolParts.Part.HALBERD_HEAD, 2, 5.0f, -3.1f);

        private final ToolParts.Part part;
        private final int handles;
        private final float damage, speed;

        Tool(ToolParts.Part part, int handles, float damage, float speed) {
            this.part = part;
            this.handles = handles;
            this.damage = damage;
            this.speed = speed;
        }

        public ToolParts.Part part() { return part; }

        public int handles() { return handles; }

        public String id() { return name().toLowerCase(Locale.ROOT); }

        Item.Properties properties(ToolMaterial material) {
            Item.Properties properties = new Item.Properties();
            return switch (this) {
                case PICKAXE -> properties.pickaxe(material, damage, speed);
                case AXE -> properties.axe(material, damage, speed);
                case SHOVEL -> properties.shovel(material, damage, speed);
                case HOE -> properties.hoe(material, damage, speed);
                case SWORD, DAGGER, GREATSWORD, HALBERD -> properties.sword(material, damage, speed);
            };
        }
    }

    /** The four metals above iron. */
    public enum Tier {
        STEEL("steel", ToolParts.SmithMetal.STEEL, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 900, 7.5f, 3.0f, 12,
                22, new int[] {2, 5, 7, 2}, 1.0f, 0.0f, true),
        STAINLESS_STEEL("stainless_steel", ToolParts.SmithMetal.STAINLESS_STEEL, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
                1300, 7.5f, 3.0f, 14, 28, new int[] {2, 6, 7, 2}, 1.5f, 0.0f, false),
        TITANIUM("titanium", ToolParts.SmithMetal.TITANIUM, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1700, 8.5f, 3.5f, 15,
                32, new int[] {3, 6, 8, 3}, 2.5f, 0.0f, false),
        TUNGSTEN_STEEL("tungsten_steel", ToolParts.SmithMetal.TUNGSTEN_STEEL, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
                2600, 10.0f, 4.5f, 16, 40, new int[] {3, 7, 9, 3}, 3.5f, 0.1f, false);

        private final String id;
        private final ToolParts.SmithMetal metal;
        private final ToolMaterial toolMaterial;
        private final ArmorMaterial armorMaterial;
        private final boolean rusts;

        Tier(String id, ToolParts.SmithMetal metal, TagKey<net.minecraft.world.level.block.Block> incorrect, int durability,
             float speed, float damage, int enchantability, int armorDurability, int[] defense, float toughness,
             float knockbackResistance, boolean rusts) {
            this.id = id;
            this.metal = metal;
            this.rusts = rusts;
            TagKey<Item> repair = TagKey.create(Registries.ITEM, Hardwrought.id(id + "_tool_materials"));
            this.toolMaterial = new ToolMaterial(incorrect, durability, speed, damage, enchantability, repair);
            Map<ArmorType, Integer> defence = new EnumMap<>(ArmorType.class);
            defence.put(ArmorType.HELMET, defense[0]);
            defence.put(ArmorType.CHESTPLATE, defense[1]);
            defence.put(ArmorType.LEGGINGS, defense[2]);
            defence.put(ArmorType.BOOTS, defense[3]);
            defence.put(ArmorType.BODY, defense[1]);
            @SuppressWarnings("unchecked")
            ResourceKey<EquipmentAsset> asset = ResourceKey.create(
                    (ResourceKey<? extends Registry<EquipmentAsset>>) EquipmentAssets.ROOT_ID, Hardwrought.id(id));
            this.armorMaterial = new ArmorMaterial(armorDurability, defence, enchantability, SoundEvents.ARMOR_EQUIP_IRON,
                    toughness, knockbackResistance, repair, asset);
        }

        public String id() { return id; }

        public ToolParts.SmithMetal metal() { return metal; }

        public boolean rusts() { return rusts; }
    }

    /** The armor pieces, in slot order. */
    public static final List<ArmorType> ARMOR = List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS);

    private static final Map<Tier, Map<Tool, Item>> TOOLS = new EnumMap<>(Tier.class);
    private static final Map<Tier, Map<ArmorType, Item>> ARMOR_ITEMS = new EnumMap<>(Tier.class);

    private AlloyEquipment() { }

    public static void initialize() {
        if (!TOOLS.isEmpty()) return;
        for (Tier tier : Tier.values()) {
            Map<Tool, Item> tools = new EnumMap<>(Tool.class);
            for (Tool tool : Tool.values()) {
                tools.put(tool, register(tier.id() + "_" + tool.id(), tool.properties(tier.toolMaterial)));
            }
            TOOLS.put(tier, tools);
            Map<ArmorType, Item> armor = new EnumMap<>(ArmorType.class);
            for (ArmorType type : ARMOR) {
                armor.put(type, register(tier.id() + "_" + type.getName(),
                        new Item.Properties().humanoidArmor(tier.armorMaterial, type)));
            }
            ARMOR_ITEMS.put(tier, armor);
        }
    }

    public static Item tool(Tier tier, Tool tool) {
        return TOOLS.get(tier).get(tool);
    }

    public static Item armor(Tier tier, ArmorType type) {
        return ARMOR_ITEMS.get(tier).get(type);
    }

    /** Every tool, weapon and armor piece, tier by tier. */
    public static List<Item> all() {
        List<Item> all = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            all.addAll(TOOLS.get(tier).values());
            all.addAll(ARMOR_ITEMS.get(tier).values());
        }
        return all;
    }

    /** The tier this item is made of, or null. */
    public static Tier tierOf(Item item) {
        for (Tier tier : Tier.values()) {
            if (TOOLS.get(tier).containsValue(item) || ARMOR_ITEMS.get(tier).containsValue(item)) return tier;
        }
        return null;
    }

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
    }
}
