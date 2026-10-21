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

import java.util.function.Consumer;
import java.util.function.Supplier;

/** The creative inventory tabs of Hardwrought, one for each part of the game. */
public final class ModCreativeTabs {
    public static final ResourceKey<CreativeModeTab> HARDWROUGHT_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Hardwrought.id("hardwrought"));

    /** Survival first: the first tools, the workbenches, the book, what is carried and worn early. */
    public static final CreativeModeTab HARDWROUGHT = tab(HARDWROUGHT_KEY, "itemGroup.hardwrought",
            () -> new ItemStack(ModItems.FLINT_HATCHET), output -> {
                output.accept(ModItems.FLINT_SHARD);
                output.accept(ModItems.COBBLESTONE_PIECE);
                output.accept(ModItems.DIRT_BLOB);
                output.accept(ModItems.DIRT_SLAB);
                for (HewnWood wood : HewnWood.values()) output.accept(ModBlocks.HEWN_WORKBENCH.itemStack(wood));
                for (HewnWood wood : HewnWood.values()) output.accept(ModBlocks.NAILED_WORKBENCH.itemStack(wood));
                output.accept(ModItems.WORKSHOP_BENCH);
                output.accept(ModItems.LEAF_STRING);
                output.accept(ModItems.COMPENDIUM);
                output.accept(de.ipnats.hardwrought.atlas.Atlas.ITEM);
                output.accept(ModItems.LIGHTING_STICKS);
                output.accept(ModItems.UNLIT_TORCH);
                output.accept(ModItems.FILLED_WATERSKIN);
                output.accept(ModItems.WATER_BOWL);
                de.ipnats.hardwrought.health.Herbs.items().forEach(output::accept);
                de.ipnats.hardwrought.nether.Ashcork.items().forEach(output::accept);
                de.ipnats.hardwrought.water.WaterTreatmentBlocks.items().forEach(output::accept);
                output.accept(ModItems.SAFETY_LAMP);
                output.accept(ModItems.STRUCTURAL_ANCHOR);
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
                output.accept(ModItems.IRON_GREATSWORD);
                output.accept(ModItems.IRON_HALBERD);
            });

    /** Metal from ore to finished piece: the metals, the forge and smeltery, stock, parts of tools, armour. */
    public static final CreativeModeTab METALS = tab(key("metals"), "itemGroup.hardwrought.metals",
            () -> new ItemStack(ModItems.IRON_HAMMER), output -> {
                output.accept(ModItems.BRONZE_MIXTURE);
                output.accept(ModItems.BRONZE_INGOT);
                output.accept(ModItems.BRONZE_NAILS);
                output.accept(ModItems.WOODEN_HAMMER);
                output.accept(ModItems.HAMMER);
                output.accept(ModItems.IRON_HAMMER);
                output.accept(ModItems.STEEL_HAMMER);
                output.accept(ModItems.STAINLESS_STEEL_HAMMER);
                output.accept(ModItems.TITANIUM_HAMMER);
                output.accept(ModItems.TUNGSTEN_STEEL_HAMMER);
                output.accept(ModItems.NETHERITE_HAMMER);
                output.accept(ModItems.MITHRIL_HAMMER);
                output.accept(ModItems.ADAMANT_HAMMER);
                output.accept(ModItems.SMITHING_GLOVES);
                output.accept(ModItems.WOODEN_ANVIL);
                output.accept(ModItems.FORGE);
                output.accept(ModItems.FORGE_HOOD);
                output.accept(ModItems.GAS_PIPE);
                output.accept(ModItems.BELLOWS);
                output.accept(ModItems.BRICK_FURNACE);
                output.accept(ModItems.FIRECLAY);
                output.accept(ModItems.REFRACTORY_BRICK);
                de.ipnats.hardwrought.smeltery.SmelteryBlocks.items().forEach(output::accept);
                output.accept(de.ipnats.hardwrought.metallurgy.Alloys.STEEL_INGOT);
                output.accept(de.ipnats.hardwrought.metallurgy.Alloys.STAINLESS_STEEL_INGOT);
                output.accept(de.ipnats.hardwrought.metallurgy.Alloys.TUNGSTEN_STEEL_INGOT);
                de.ipnats.hardwrought.smithing.MetalStock.all().forEach(output::accept);
                de.ipnats.hardwrought.smithing.ToolParts.all().forEach(output::accept);
                de.ipnats.hardwrought.smithing.AlloyEquipment.all().forEach(output::accept);
                de.ipnats.hardwrought.smithing.UpgradeEquipment.all().forEach(output::accept);
                de.ipnats.hardwrought.metallurgy.MetalBlocks.items().forEach(output::accept);
                for (var ore : de.ipnats.hardwrought.metallurgy.OrePowders.VanillaOre.values()) {
                    output.accept(de.ipnats.hardwrought.metallurgy.OrePowders.powder(ore));
                }
                for (var metal : de.ipnats.hardwrought.metallurgy.Metal.values()) {
                    output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.ore(metal));
                    output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.deepslateOre(metal));
                    output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.raw(metal));
                    output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.rawBlock(metal));
                    output.accept(de.ipnats.hardwrought.metallurgy.OrePowders.powder(metal));
                    output.accept(de.ipnats.hardwrought.metallurgy.ModMetals.ingot(metal));
                }
            });

    /** What turns and what runs on current: drivelines, pipes, the electric machines and engines. */
    public static final CreativeModeTab MACHINES = tab(key("machines"), "itemGroup.hardwrought.machines",
            () -> new ItemStack(de.ipnats.hardwrought.electricity.ElectricBlocks.DYNAMO), output -> {
                output.accept(ModItems.SHAFT);
                output.accept(ModItems.COGWHEEL);
                output.accept(ModItems.LARGE_COGWHEEL);
                output.accept(ModItems.GEARBOX);
                output.accept(ModItems.BELT);
                output.accept(ModItems.CRANK_BOX);
                output.accept(ModItems.HAND_CRANK);
                output.accept(ModItems.WATER_WHEEL);
                output.accept(ModItems.WINDMILL);
                output.accept(ModItems.STARTER_CRUSHER);
                output.accept(ModItems.ITEM_PIPE_BRONZE);
                output.accept(ModItems.ITEM_PIPE_IRON);
                output.accept(ModItems.ITEM_PIPE_NICKEL);
                output.accept(ModItems.ITEM_PIPE_CHROMIUM);
                output.accept(ModItems.ITEM_PIPE_TITANIUM);
                de.ipnats.hardwrought.fluids.FluidBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.electricity.ElectricBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.haven.HavenBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.carried.CarriedItems.items().forEach(output::accept);
                de.ipnats.hardwrought.automation.AutomationBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.tesla.TeslaBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.power.PowerBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.multiblock.BigMachines.items().forEach(output::accept);
                de.ipnats.hardwrought.storage.StorageBlocks.items().forEach(output::accept);
            });

    /** Oil and gas, from the well to the refinery, and the chemistry made of them. */
    public static final CreativeModeTab OIL = tab(key("oil"), "itemGroup.hardwrought.oil",
            () -> new ItemStack(de.ipnats.hardwrought.refining.RefineryBlocks.BASE), output -> {
                output.accept(ModItems.DRILLING_RIG);
                output.accept(ModItems.STILL);
                output.accept(ModItems.CRUDE_OIL_BUCKET);
                output.accept(ModItems.GAS_CANISTER);
                output.accept(ModItems.NATURAL_GAS_CANISTER);
                output.accept(ModItems.LIGHT_FRACTION);
                output.accept(ModItems.FUEL_FRACTION);
                output.accept(ModItems.HEAVY_OIL);
                output.accept(ModItems.BITUMEN);
                output.accept(ModItems.RAW_SULFUR);
                output.accept(ModItems.SULFUR);
                output.accept(ModItems.SALT);
                de.ipnats.hardwrought.chemistry.SaltPan.items().forEach(output::accept);
                de.ipnats.hardwrought.refining.RefineryFluids.items().forEach(output::accept);
                de.ipnats.hardwrought.gasline.GasLines.items().forEach(output::accept);
                de.ipnats.hardwrought.refining.RefineryBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.materials.MaterialBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.materials.Materials.items().forEach(output::accept);
            });

    /** The small parts machines are built of, and the circuits. */
    public static final CreativeModeTab PARTS = tab(key("parts"), "itemGroup.hardwrought.parts",
            () -> new ItemStack(de.ipnats.hardwrought.parts.Parts.BASIC_CIRCUIT),
            output -> {
                de.ipnats.hardwrought.parts.Parts.items().forEach(output::accept);
                de.ipnats.hardwrought.storage.StorageBlocks.parts().forEach(output::accept);
            });

    /** Nuclear engineering: from fluorite and yellowcake to the reactor, the turbine and the counter. */
    public static final CreativeModeTab NUCLEAR = tab(key("nuclear"), "itemGroup.hardwrought.nuclear",
            () -> new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.FUEL_ASSEMBLY), output -> {
                de.ipnats.hardwrought.nuclear.NuclearBlocks.items().forEach(output::accept);
                de.ipnats.hardwrought.nuclear.NuclearItems.items().forEach(output::accept);
                output.accept(de.ipnats.hardwrought.multiblock.BigMachines.GAS_PORT);
            });

    /** Runes, sigils and the void. */
    public static final CreativeModeTab MAGIC = tab(key("magic"), "itemGroup.hardwrought.magic",
            () -> de.ipnats.hardwrought.magic.Magic.items().getFirst().copy(), output -> {
                de.ipnats.hardwrought.magic.Magic.items().forEach(output::accept);
                de.ipnats.hardwrought.magic.VoidCrystal.items().forEach(output::accept);
            });

    private ModCreativeTabs() {
    }

    public static void initialize() {
        // Loading this class registers the creative tabs after all item fields exist.
    }

    private static ResourceKey<CreativeModeTab> key(String name) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Hardwrought.id(name));
    }

    private static CreativeModeTab tab(ResourceKey<CreativeModeTab> key, String title, Supplier<ItemStack> icon,
                                       Consumer<CreativeModeTab.Output> items) {
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, FabricCreativeModeTab.builder()
                .title(Component.translatable(title))
                .icon(icon)
                .displayItems((parameters, output) -> items.accept(output))
                .build());
    }
}
