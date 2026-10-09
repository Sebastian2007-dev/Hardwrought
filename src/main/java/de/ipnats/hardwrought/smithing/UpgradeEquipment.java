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
import net.minecraft.world.item.Items;
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
 * The equipment beyond netherite: mithril, and beyond that adamant. Neither is forged. A piece is
 * worked up from the tier below at a smithing table, with a bar of the metal and its template (see
 * {@link Upgrading}): tungsten steel to netherite, netherite to mithril, mithril to adamant.
 *
 * <ul>
 *   <li><b>mithril</b> — lighter and keener than netherite, lasts half as long again, and takes
 *   enchantment more readily than anything but gold;</li>
 *   <li><b>adamant</b> — the hardest thing there is: more than twice netherite's life, and the
 *   heaviest blow.</li>
 * </ul>
 * Like netherite, neither burns.
 */
public final class UpgradeEquipment {
    /** A tool or weapon, with netherite's own reach and swing. */
    public enum Tool {
        PICKAXE(1.0f, -2.8f), AXE(5.0f, -3.0f), SHOVEL(1.5f, -3.0f), HOE(-4.0f, 0.0f), SWORD(3.0f, -2.4f);

        private final float damage, speed;

        Tool(float damage, float speed) {
            this.damage = damage;
            this.speed = speed;
        }

        public String id() { return name().toLowerCase(Locale.ROOT); }

        Item.Properties properties(ToolMaterial material) {
            Item.Properties properties = new Item.Properties().fireResistant();
            return switch (this) {
                case PICKAXE -> properties.pickaxe(material, damage, speed);
                case AXE -> properties.axe(material, damage, speed);
                case SHOVEL -> properties.shovel(material, damage, speed);
                case HOE -> properties.hoe(material, damage, speed);
                case SWORD -> properties.sword(material, damage, speed);
            };
        }
    }

    /** The two metals beyond netherite, in order. */
    public enum Tier {
        MITHRIL("mithril", 3400, 11.5f, 5.5f, 20, 46, new int[] {4, 7, 9, 4}, 4.0f, 0.1f),
        ADAMANT("adamant", 4800, 13.0f, 7.0f, 16, 54, new int[] {4, 8, 10, 4}, 5.0f, 0.2f);

        private final String id;
        private final ToolMaterial toolMaterial;
        private final ArmorMaterial armorMaterial;

        Tier(String id, int durability, float speed, float damage, int enchantability, int armorDurability, int[] defense,
             float toughness, float knockbackResistance) {
            this.id = id;
            TagKey<Item> repair = TagKey.create(Registries.ITEM, Hardwrought.id(id + "_tool_materials"));
            this.toolMaterial = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, durability, speed, damage,
                    enchantability, repair);
            Map<ArmorType, Integer> defence = new EnumMap<>(ArmorType.class);
            defence.put(ArmorType.HELMET, defense[0]);
            defence.put(ArmorType.CHESTPLATE, defense[1]);
            defence.put(ArmorType.LEGGINGS, defense[2]);
            defence.put(ArmorType.BOOTS, defense[3]);
            defence.put(ArmorType.BODY, defense[1]);
            @SuppressWarnings("unchecked")
            ResourceKey<EquipmentAsset> asset = ResourceKey.create(
                    (ResourceKey<? extends Registry<EquipmentAsset>>) EquipmentAssets.ROOT_ID, Hardwrought.id(id));
            this.armorMaterial = new ArmorMaterial(armorDurability, defence, enchantability, SoundEvents.ARMOR_EQUIP_NETHERITE,
                    toughness, knockbackResistance, repair, asset);
        }

        public String id() { return id; }
    }

    public static final List<ArmorType> ARMOR = List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS);

    private static final Map<Tier, Map<Tool, Item>> TOOLS = new EnumMap<>(Tier.class);
    private static final Map<Tier, Map<ArmorType, Item>> ARMOR_ITEMS = new EnumMap<>(Tier.class);
    private static final Map<Tier, Item> TEMPLATES = new EnumMap<>(Tier.class);

    private UpgradeEquipment() { }

    public static void initialize() {
        if (!TOOLS.isEmpty()) return;
        for (Tier tier : Tier.values()) {
            Map<Tool, Item> tools = new EnumMap<>(Tool.class);
            for (Tool tool : Tool.values()) tools.put(tool, register(tier.id() + "_" + tool.id(), tool.properties(tier.toolMaterial)));
            TOOLS.put(tier, tools);
            Map<ArmorType, Item> armor = new EnumMap<>(ArmorType.class);
            for (ArmorType type : ARMOR) {
                armor.put(type, register(tier.id() + "_" + type.getName(),
                        new Item.Properties().fireResistant().humanoidArmor(tier.armorMaterial, type)));
            }
            ARMOR_ITEMS.put(tier, armor);
            TEMPLATES.put(tier, register(tier.id() + "_upgrade_smithing_template", new Item.Properties().fireResistant()));
        }
    }

    public static Item tool(Tier tier, Tool tool) {
        return TOOLS.get(tier).get(tool);
    }

    public static Item armor(Tier tier, ArmorType type) {
        return ARMOR_ITEMS.get(tier).get(type);
    }

    /** The tier this item is of, or null. */
    public static Tier tierOf(Item item) {
        for (Tier tier : Tier.values()) {
            if (TOOLS.get(tier).containsValue(item) || ARMOR_ITEMS.get(tier).containsValue(item)) return tier;
        }
        return null;
    }

    /** The template a piece is worked up to this tier with. */
    public static Item template(Tier tier) {
        return TEMPLATES.get(tier);
    }

    /** Every template an upgrade is worked with, netherite's first: the order of the tiers. */
    public static List<Item> templates() {
        List<Item> templates = new ArrayList<>();
        templates.add(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        for (Tier tier : Tier.values()) templates.add(TEMPLATES.get(tier));
        return templates;
    }

    /** Every template, tool, weapon and armor piece, tier by tier. */
    public static List<Item> all() {
        List<Item> all = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            all.add(TEMPLATES.get(tier));
            all.addAll(TOOLS.get(tier).values());
            all.addAll(ARMOR_ITEMS.get(tier).values());
        }
        return all;
    }

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
    }
}
