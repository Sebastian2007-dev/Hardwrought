package de.ipnats.hardwrought.metallurgy;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Registers powder forms for vanilla ore materials and every Hardwrought metal ore. */
public final class OrePowders {
    /** Vanilla ore materials without an existing powder-like item; redstone already has dust. */
    public enum VanillaOre {
        COAL("coal"),
        COPPER("copper"),
        IRON("iron"),
        GOLD("gold"),
        LAPIS_LAZULI("lapis_lazuli"),
        DIAMOND("diamond"),
        EMERALD("emerald"),
        NETHER_GOLD("nether_gold"),
        NETHER_QUARTZ("nether_quartz"),
        ANCIENT_DEBRIS("ancient_debris");

        private final String id;

        VanillaOre(String id) {
            this.id = id;
        }

        public String powderId() {
            return id + "_powder";
        }
    }

    private static final Map<VanillaOre, Item> VANILLA_POWDERS = new EnumMap<>(VanillaOre.class);
    private static final Map<Metal, Item> METAL_POWDERS = new EnumMap<>(Metal.class);
    private static final List<Item> ALL_POWDERS = new ArrayList<>();

    private OrePowders() { }

    public static void initialize() {
        if (!ALL_POWDERS.isEmpty()) return;

        for (VanillaOre ore : VanillaOre.values()) {
            Item powder = register(ore.powderId());
            VANILLA_POWDERS.put(ore, powder);
            ALL_POWDERS.add(powder);
        }
        for (Metal metal : Metal.values()) {
            Item powder = register(metal.powderId());
            METAL_POWDERS.put(metal, powder);
            ALL_POWDERS.add(powder);
        }
    }

    public static Item powder(VanillaOre ore) {
        return required(VANILLA_POWDERS, ore);
    }

    public static Item powder(Metal metal) {
        return required(METAL_POWDERS, metal);
    }

    public static List<Item> all() {
        if (ALL_POWDERS.isEmpty()) throw new IllegalStateException("Ore powders have not been registered yet");
        return List.copyOf(ALL_POWDERS);
    }

    private static Item register(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private static <K> Item required(Map<K, Item> powders, K material) {
        Item powder = powders.get(material);
        if (powder == null) throw new IllegalStateException("Ore powders have not been registered yet");
        return powder;
    }
}
