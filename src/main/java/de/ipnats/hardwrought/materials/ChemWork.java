package de.ipnats.hardwrought.materials;

import de.ipnats.hardwrought.gasline.GasStore;
import de.ipnats.hardwrought.refining.ProcessTank;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What a machine working {@link ChemMachine.Recipe recipes} holds — two input slots, two output slots, a
 * liquid tank, a gas store in and one out — and the working of a recipe on it: whether it can be done,
 * and doing it. The single-block machines and the arc furnace multiblock share it.
 */
public final class ChemWork {
    public static final int FIRST_INPUT = 0, INPUTS = 2, FIRST_OUTPUT = 2, SIZE = 4;

    public final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    /** The tank pipes fill; null for a machine that takes no liquid. */
    public final ProcessTank fluid;
    public final GasStore gasIn;
    public final GasStore gasOut;

    public ChemWork(ProcessTank fluid, GasStore gasIn, GasStore gasOut) {
        this.fluid = fluid;
        this.gasIn = gasIn;
        this.gasOut = gasOut;
    }

    /** The first of these recipes what it holds will do, or null for none. */
    public ChemMachine.Recipe workable(List<ChemMachine.Recipe> recipes) {
        for (ChemMachine.Recipe recipe : recipes) {
            if (canWork(recipe)) return recipe;
        }
        return null;
    }

    public boolean canWork(ChemMachine.Recipe recipe) {
        for (ItemStack input : recipe.inputs()) {
            if (held(input) < input.getCount()) return false;
        }
        if (recipe.fluid() != null && (fluid == null || fluid.held() != recipe.fluid() || fluid.millibuckets() < recipe.fluidMb())) {
            return false;
        }
        if (recipe.gas() != null && (gasIn == null || gasIn.kind() != recipe.gas() || gasIn.amount() < recipe.gasUnits())) {
            return false;
        }
        if (recipe.gasOut() != null && (gasOut == null || gasOut.capacity() - gasOut.amount() < recipe.gasOutUnits()
                || (gasOut.kind() != null && gasOut.kind() != recipe.gasOut()))) {
            return false;
        }
        return fits(recipe.outputs());
    }

    /** How many of the input's kind lie in the input slots. */
    private int held(ItemStack kind) {
        int count = 0;
        for (int slot = FIRST_INPUT; slot < FIRST_INPUT + INPUTS; slot++) {
            if (ItemStack.isSameItem(items.get(slot), kind)) count += items.get(slot).getCount();
        }
        return count;
    }

    /** Whether all of these go into the output slots. */
    private boolean fits(List<ItemStack> outputs) {
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = FIRST_OUTPUT; slot < SIZE; slot++) slots.add(items.get(slot).copy());
        for (ItemStack output : outputs) {
            if (merge(slots, output) > 0) return false;
        }
        return true;
    }

    /** Puts as much of the stack into these slots as goes; returns how much is left over. */
    private static int merge(List<ItemStack> slots, ItemStack stack) {
        int left = stack.getCount();
        for (int i = 0; i < slots.size() && left > 0; i++) {
            ItemStack in = slots.get(i);
            if (in.isEmpty()) {
                slots.set(i, stack.copyWithCount(left));
                left = 0;
            } else if (ItemStack.isSameItemSameComponents(in, stack)) {
                int moved = Math.min(left, in.getMaxStackSize() - in.getCount());
                in.grow(moved);
                left -= moved;
            }
        }
        return left;
    }

    /** Does the recipe: takes what goes in and gives what comes out. Ask {@link #canWork} first. */
    public void work(ChemMachine.Recipe recipe) {
        for (ItemStack input : recipe.inputs()) {
            int left = input.getCount();
            for (int slot = FIRST_INPUT; slot < FIRST_INPUT + INPUTS && left > 0; slot++) {
                ItemStack in = items.get(slot);
                if (!ItemStack.isSameItem(in, input)) continue;
                int taken = Math.min(left, in.getCount());
                in.shrink(taken);
                left -= taken;
            }
        }
        if (recipe.fluid() != null) fluid.take(recipe.fluidMb());
        if (recipe.gas() != null) gasIn.extract(recipe.gasUnits());
        if (recipe.gasOut() != null) gasOut.produce(recipe.gasOut(), recipe.gasOutUnits());
        List<ItemStack> outputs = new ArrayList<>(items.subList(FIRST_OUTPUT, SIZE));
        for (ItemStack output : recipe.outputs()) merge(outputs, output);
        for (int i = 0; i < outputs.size(); i++) items.set(FIRST_OUTPUT + i, outputs.get(i));
    }

    /** Whether any of these recipes takes this item. */
    public static boolean takes(List<ChemMachine.Recipe> recipes, ItemStack stack) {
        for (ChemMachine.Recipe recipe : recipes) {
            for (ItemStack input : recipe.inputs()) {
                if (ItemStack.isSameItem(input, stack)) return true;
            }
        }
        return false;
    }
}
