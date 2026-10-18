package de.ipnats.hardwrought.refining;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasLines;
import de.ipnats.hardwrought.knowledge.FluidIcons;
import de.ipnats.hardwrought.knowledge.WorldRecipes;
import de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe;
import de.ipnats.hardwrought.oil.ModFluids;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything oil is made into, for the compendium: what a rig brings up, what the still and the column
 * part crude oil into, what the cracker and the reformer make of the cuts, and how a bottle of the still
 * and a canister of gas are filled. Liquids and gases are shown as themselves, with how much in the
 * tooltip: per batch, as the machines work them.
 */
public final class RefiningRecipes {
    /** A third of a bucket, in millibuckets: a bottle. */
    private static final int BOTTLE = (int) (FluidConstants.BOTTLE * 1000 / FluidConstants.BUCKET);

    private RefiningRecipes() { }

    public static List<WorldRecipe> worldRecipes() {
        List<WorldRecipe> recipes = new ArrayList<>();
        ItemStack canister = new ItemStack(ModItems.GAS_CANISTER);

        // The rig: crude oil, a bucket's worth at a time, and the gas of the cap into a canister.
        ItemStack rig = new ItemStack(ModItems.DRILLING_RIG);
        recipes.add(new WorldRecipe(Hardwrought.id("drilling/crude_oil"), List.of(),
                FluidIcons.liquid(ModFluids.CRUDE_OIL, 1000), rig));
        recipes.add(new WorldRecipe(Hardwrought.id("drilling/natural_gas"), WorldRecipes.one(canister),
                new ItemStack(ModItems.NATURAL_GAS_CANISTER), rig));

        // The still: a bucket of crude oil and three bottles give a bottle of each light cut and bitumen.
        ItemStack still = new ItemStack(ModItems.STILL);
        List<List<ItemStack>> charge = WorldRecipes.one(FluidIcons.liquid(ModFluids.CRUDE_OIL, 1000), new ItemStack(Items.GLASS_BOTTLE, 3));
        // Read off the still itself, so that what a batch can give — the raw sulfur that comes over now
        // and then too — is all in the book.
        for (ItemStack product : de.ipnats.hardwrought.chemistry.StillRecipes.mostOf(new ItemStack(ModItems.CRUDE_OIL_BUCKET))) {
            recipes.add(new WorldRecipe(Hardwrought.id("still/" + BuiltInRegistries.ITEM.getKey(product.getItem()).getPath()),
                    charge, product, still));
        }
        // Its other charge: raw sulfur melted clean. Salt is the salt pan's (SaltPan).
        recipes.add(new WorldRecipe(Hardwrought.id("still/spirit"),
                WorldRecipes.one(new ItemStack(ModItems.MASH), new ItemStack(Items.GLASS_BOTTLE)),
                new ItemStack(ModItems.SPIRIT), still));
        recipes.add(new WorldRecipe(Hardwrought.id("still/sulfur"), WorldRecipes.one(new ItemStack(ModItems.RAW_SULFUR)),
                new ItemStack(ModItems.SULFUR), still));
        // A bottle of the still emptied into a tank or a pipe is a third of a bucket of its liquid: the
        // only way to a bucket of it.
        for (RefineryProduct product : RefineryProduct.values()) {
            var bottle = RefineryFluids.bottleOf(product);
            if (bottle == null) continue;
            recipes.add(new WorldRecipe(Hardwrought.id("emptying/" + product.getSerializedName()),
                    WorldRecipes.one(new ItemStack(bottle)), FluidIcons.liquid(product.source(), BOTTLE), ItemStack.EMPTY));
        }

        // The column: each batch of crude oil, and what every tray catches of it.
        ItemStack base = new ItemStack(RefineryBlocks.BASE);
        ItemStack batch = FluidIcons.liquid(ModFluids.CRUDE_OIL, RefineryBaseBlock.BATCH);
        recipes.add(new WorldRecipe(Hardwrought.id("refinery/bitumen"), WorldRecipes.one(batch),
                FluidIcons.liquid(RefineryProduct.BITUMEN.source(), RefineryBaseBlock.RESIDUE), base));
        for (int cut = 1; cut < RefineryBaseBlock.MOST_STAGES; cut++) {
            RefineryStageBlock.Cut caught = RefineryBaseBlock.CUTS.get(cut - 1);
            RefineryProduct product = productOf(caught.fluid());
            recipes.add(new WorldRecipe(Hardwrought.id("refinery/" + product.getSerializedName()),
                    WorldRecipes.one(batch, new ItemStack(RefineryBlocks.STAGE, cut)),
                    FluidIcons.liquid(caught.fluid(), caught.millibuckets()), base));
        }
        RefineryStageBlock.Cut top = RefineryBaseBlock.CUTS.get(RefineryBaseBlock.MOST_STAGES - 1);
        recipes.add(new WorldRecipe(Hardwrought.id("refinery/lpg"),
                WorldRecipes.one(batch, new ItemStack(RefineryBlocks.STAGE, RefineryBaseBlock.MOST_STAGES)),
                FluidIcons.gas(GasKind.LPG, top.gasUnits()), base));

        // Cracker and reformer.
        process(recipes, "cracker", new ItemStack(RefineryBlocks.CRACKER), ProcessorBlock.CRACKING);
        process(recipes, "reformer", new ItemStack(RefineryBlocks.REFORMER), ProcessorBlock.REFORMING);

        // Gas into a canister at a gas tank.
        ItemStack gasTank = new ItemStack(GasLines.TANK);
        for (GasKind kind : List.of(GasKind.LPG, GasKind.NATURAL_GAS)) {
            recipes.add(new WorldRecipe(Hardwrought.id("gas_tank/" + BuiltInRegistries.ITEM.getKey(GasLines.canister(kind)).getPath()),
                    WorldRecipes.one(FluidIcons.gas(kind, GasLines.CANISTER_UNITS), canister),
                    new ItemStack(GasLines.canister(kind)), gasTank));
        }

        // The still's bottles, filled from a tank.
        ItemStack fluidTank = new ItemStack(de.ipnats.hardwrought.fluids.FluidBlocks.TANK);
        for (RefineryProduct product : RefineryProduct.values()) {
            var bottle = RefineryFluids.bottleOf(product);
            if (bottle == null) continue;
            recipes.add(new WorldRecipe(Hardwrought.id("bottling/" + product.getSerializedName()),
                    WorldRecipes.one(FluidIcons.liquid(product.source(), BOTTLE), new ItemStack(Items.GLASS_BOTTLE)),
                    new ItemStack(bottle), fluidTank));
        }
        return recipes;
    }

    private static void process(List<WorldRecipe> recipes, String name, ItemStack station, ProcessorBlock.Process process) {
        ItemStack input = FluidIcons.liquid(process.input().get(), process.inputMillibuckets());
        for (ProcessorBlock.Output output : process.outputs()) {
            RefineryProduct product = productOf(output.fluid().get());
            recipes.add(new WorldRecipe(Hardwrought.id(name + "/" + product.getSerializedName()),
                    WorldRecipes.one(input), FluidIcons.liquid(output.fluid().get(), output.millibuckets()), station));
        }
        recipes.add(new WorldRecipe(Hardwrought.id(name + "/lpg"), WorldRecipes.one(input),
                FluidIcons.gas(GasKind.LPG, process.gasUnits()), station));
    }

    private static RefineryProduct productOf(net.minecraft.world.level.material.Fluid fluid) {
        for (RefineryProduct product : RefineryProduct.values()) {
            if (product.source() == fluid) return product;
        }
        throw new IllegalArgumentException("Not a refinery liquid: " + fluid);
    }
}
