package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** The dedicated creative inventory tab containing every Hardwrought item. */
public final class ModCreativeTabs {
    public static final ResourceKey<CreativeModeTab> HARDWROUGHT_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Hardwrought.id("hardwrought"));

    public static final CreativeModeTab HARDWROUGHT = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            HARDWROUGHT_KEY,
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hardwrought"))
                    .icon(() -> new ItemStack(ModItems.FLINT_HATCHET))
                    .displayItems((parameters, output) -> {
                        // Basic resources and survival equipment first.
                        output.accept(ModItems.FLINT_SHARD);
                        output.accept(ModItems.COBBLESTONE_PIECE);
                        output.accept(ModItems.DIRT_BLOB);
                        output.accept(ModItems.LEAF_STRING);
                        output.accept(ModItems.LIGHTING_STICKS);;
                        output.accept(ModItems.FILLED_WATERSKIN);
                        output.accept(ModItems.SAFETY_LAMP);

                        // Primitive tools and weapons, ordered by material progression.
                        output.accept(ModItems.FLINT_DAGGER);
                        output.accept(ModItems.FLINT_HATCHET);
                        output.accept(ModItems.FLINT_HOE);
                        output.accept(ModItems.FLINT_PICKAXE);
                        output.accept(ModItems.FLINT_SHOVEL);
                        output.accept(ModItems.FLINT_SWORD);
                        output.accept(ModItems.STONE_HATCHET);
                        output.accept(ModItems.STONE_PICKAXE);
                        output.accept(ModItems.IRON_DAGGER);
                        output.accept(ModItems.IRON_HATCHET);
                        output.accept(ModItems.IRON_PICKAXE);
                        output.accept(ModItems.IRON_GREATSWORD);
                        output.accept(ModItems.IRON_HALBERD);
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void initialize() {
        // Loading this class registers the creative tab after all item fields exist.
    }
}
