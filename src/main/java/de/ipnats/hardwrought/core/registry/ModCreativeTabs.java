package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.progression.HewnWood;
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
                        output.accept(ModItems.DIRT_SLAB);
                        for (HewnWood wood : HewnWood.values()) {
                            output.accept(ModBlocks.HEWN_WORKBENCH.itemStack(wood));
                        }
                        for (HewnWood wood : HewnWood.values()) {
                            output.accept(ModBlocks.NAILED_WORKBENCH.itemStack(wood));
                        }
                        output.accept(ModItems.LEAF_STRING);
                        output.accept(ModItems.COMPENDIUM);
                        output.accept(ModItems.BRONZE_MIXTURE);
                        output.accept(ModItems.BRONZE_NAILS);
                        output.accept(ModItems.WOODEN_HAMMER);
                        output.accept(ModItems.HAMMER);
                        output.accept(ModItems.IRON_HAMMER);
                        output.accept(ModItems.SMITHING_GLOVES);
                        output.accept(ModItems.WOODEN_ANVIL);
                        output.accept(ModItems.FORGE);
                        output.accept(ModItems.BELLOWS);
                        output.accept(ModItems.COGWHEEL);
                        output.accept(ModItems.LARGE_COGWHEEL);
                        output.accept(ModItems.GEARBOX);
                        output.accept(ModItems.BELT);
                        output.accept(ModItems.WATER_WHEEL);
                        output.accept(ModItems.WINDMILL);
                        output.accept(ModItems.FIRECLAY);
                        output.accept(ModItems.REFRACTORY_BRICK);
                        de.ipnats.hardwrought.smithing.ToolParts.all().forEach(output::accept);
                        output.accept(ModItems.BRONZE_INGOT);
                        output.accept(ModItems.LIGHTING_STICKS);
                        output.accept(ModItems.BRICK_FURNACE);
                        output.accept(ModItems.FILLED_WATERSKIN);
                        output.accept(ModItems.SAFETY_LAMP);
                        output.accept(ModItems.SHAFT);
                        output.accept(ModItems.CRANK_BOX);
                        output.accept(ModItems.HAND_CRANK);
                        output.accept(ModItems.STARTER_CRUSHER);

                        // Primitive tools and weapons, ordered by material progression.
                        output.accept(ModItems.FLINT_DAGGER);
                        output.accept(ModItems.FLINT_HATCHET);
                        output.accept(ModItems.FLINT_HOE);
                        output.accept(ModItems.FLINT_PICKAXE);
                        output.accept(ModItems.FLINT_SHOVEL);
                        output.accept(ModItems.FLINT_SWORD);
                        output.accept(ModItems.POINTED_STICK);
                        output.accept(ModItems.SEWING_NEEDLE);
                        output.accept(ModItems.IRON_SEWING_NEEDLE);
                        output.accept(ModItems.WOVEN);
                        output.accept(ModItems.GREEN_FIBRE);
                        output.accept(ModItems.DRYING_RACK);
                        output.accept(ModItems.STARTER_BACKPACK);
                        output.accept(ModItems.FRAME_BACKPACK);
                        output.accept(ModItems.BASIC_BACKPACK);
                        output.accept(ModItems.STONE_HATCHET);
                        output.accept(ModItems.STONE_PICKAXE);
                        output.accept(ModItems.BRONZE_HATCHET);
                        output.accept(ModItems.BRONZE_PICKAXE);
                        output.accept(ModItems.IRON_DAGGER);
                        output.accept(ModItems.IRON_HATCHET);
                        output.accept(ModItems.IRON_PICKAXE);
                        output.accept(ModItems.IRON_GREATSWORD);
                        output.accept(ModItems.IRON_HALBERD);

                        // Powder forms of vanilla ore materials; redstone already has vanilla dust.
                        for (var ore : de.ipnats.hardwrought.metallurgy.OrePowders.VanillaOre.values()) {
                            output.accept(de.ipnats.hardwrought.metallurgy.OrePowders.powder(ore));
                        }

                        // Sections 55 and 56: every metal of the table, ore, raw, powder and ingot.
                        for (var metal : de.ipnats.hardwrought.metallurgy.Metal.values()) {
                            output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.ore(metal));
                            output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.deepslateOre(metal));
                            output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.raw(metal));
                            output.accept(de.ipnats.hardwrought.metallurgy.OrePowders.powder(metal));
                            output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.ingot(metal));
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void initialize() {
        // Loading this class registers the creative tab after all item fields exist.
    }
}
