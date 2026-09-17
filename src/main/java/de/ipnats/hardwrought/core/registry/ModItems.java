package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import de.ipnats.hardwrought.survival.WaterskinItem;

import java.util.function.Function;

/** Central registration point for Hardwrought items. */
public final class ModItems {
    public static final Item FLINT_SHARD = register("flint_shard", Item::new, new Item.Properties());
    public static final Item FILLED_WATERSKIN = register("filled_waterskin", WaterskinItem::new,
            new Item.Properties().durability(9).stacksTo(1));

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
