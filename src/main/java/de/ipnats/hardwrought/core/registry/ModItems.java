package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import de.ipnats.hardwrought.environment.SafetyLampItem;
import de.ipnats.hardwrought.survival.WaterskinItem;
import net.minecraft.world.item.ToolMaterial;

import java.util.function.Function;

/** Central registration point for Hardwrought items. */
public final class ModItems {
    public static final Item FLINT_SHARD = register("flint_shard", Item::new, new Item.Properties());
    public static final Item FILLED_WATERSKIN = register("filled_waterskin", WaterskinItem::new,
            new Item.Properties().durability(9).stacksTo(1));
    // Milestone 2 weapon classes that vanilla has no item for. Values follow the class profiles in
    // data/hardwrought/hardwrought/weapon_profiles; vanilla still supplies the base attack damage.
    public static final Item FLINT_DAGGER = register("flint_dagger", Item::new,
            new Item.Properties().sword(ModToolMaterials.FLINT, 0.5F, -1.4F));
    public static final Item IRON_DAGGER = register("iron_dagger", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 1.0F, -1.5F));
    public static final Item IRON_GREATSWORD = register("iron_greatsword", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 6.0F, -3.2F));
    public static final Item IRON_HALBERD = register("iron_halberd", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 5.0F, -3.1F));
    /** Milestone 3, section 18.3: the primitive instrument that makes bad air readable. */
    public static final Item SAFETY_LAMP = register("safety_lamp", SafetyLampItem::new,
            new Item.Properties().stacksTo(1));

    private ModItems() {
    }

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory,
                                              Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        T item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        // Calling this method loads the class and registers its static item fields.
        if (Hardwrought.config().debugLogging()) {
            Hardwrought.LOGGER.info("Registered starter item: {}", BuiltInRegistries.ITEM.getKey(FLINT_SHARD));
        }
    }
}
