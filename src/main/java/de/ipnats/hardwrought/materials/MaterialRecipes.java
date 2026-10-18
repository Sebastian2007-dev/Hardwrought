package de.ipnats.hardwrought.materials;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.knowledge.FluidIcons;
import de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The materials' processes, for the compendium: every recipe of the chemistry machines, and plastic
 * sheet off the plate press and wafers off the sawmill. Liquids and gases are shown as themselves, with
 * how much in the tooltip.
 */
public final class MaterialRecipes {
    private MaterialRecipes() { }

    public static List<WorldRecipe> worldRecipes() {
        List<WorldRecipe> recipes = new ArrayList<>();
        for (ChemMachine machine : ChemMachine.values()) {
            ItemStack station = new ItemStack(MaterialBlocks.block(machine));
            for (ChemMachine.Recipe recipe : machine.recipes()) {
                List<List<ItemStack>> inputs = new ArrayList<>();
                for (ItemStack input : recipe.inputs()) inputs.add(List.of(input.copy()));
                if (recipe.fluid() != null) inputs.add(List.of(FluidIcons.liquid(recipe.fluid(), recipe.fluidMb())));
                if (recipe.gas() != null) inputs.add(List.of(FluidIcons.gas(recipe.gas(), recipe.gasUnits())));
                for (ItemStack output : recipe.outputs()) {
                    String path = BuiltInRegistries.ITEM.getKey(output.getItem()).getPath();
                    recipes.add(new WorldRecipe(Hardwrought.id(group(recipe) + "/" + path), inputs, output.copy(), station));
                }
                // A gas given off is a result of its own: the electrolyser's chlorine.
                if (recipe.gasOut() != null) {
                    recipes.add(new WorldRecipe(Hardwrought.id(group(recipe) + "/" + recipe.gasOut().getSerializedName()), inputs,
                            FluidIcons.gas(recipe.gasOut(), recipe.gasOutUnits()), station));
                }
            }
        }
        // The large arc furnace, a multiblock: shown at its controller.
        ItemStack controller = new ItemStack(de.ipnats.hardwrought.multiblock.BigMachines.ARC_FURNACE_CONTROLLER);
        for (ChemMachine.Recipe recipe : de.ipnats.hardwrought.multiblock.ArcFurnaceController.recipes()) {
            List<List<ItemStack>> inputs = new ArrayList<>();
            for (ItemStack input : recipe.inputs()) inputs.add(List.of(input.copy()));
            for (ItemStack output : recipe.outputs()) {
                String path = BuiltInRegistries.ITEM.getKey(output.getItem()).getPath();
                recipes.add(new WorldRecipe(Hardwrought.id(group(recipe) + "/" + path), inputs, output.copy(), controller));
            }
        }
        return recipes;
    }

    /** The recipe's group, which names its instruction: the part of its id before the slash. */
    private static String group(ChemMachine.Recipe recipe) {
        int slash = recipe.id().indexOf('/');
        return slash < 0 ? recipe.id() : recipe.id().substring(0, slash);
    }
}
