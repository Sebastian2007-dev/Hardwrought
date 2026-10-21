package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.knowledge.FluidIcons;
import de.ipnats.hardwrought.knowledge.WorldRecipes;
import de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Nuclear engineering (development plan section 113; see docs/nuclear.md): everything is registered here,
 * fluorite is put into the ground, and the work of the big machines is described for the compendium.
 */
public final class Nuclear {
    private Nuclear() { }

    public static void initialize() {
        NuclearItems.initialize();
        NuclearBlocks.initialize();
        ReactorMenu.initialize();
        Radiation.initialize();
        BiomeModifications.create(Hardwrought.id("fluorite")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInOverworld(),
                context -> context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES,
                        ResourceKey.create(Registries.PLACED_FEATURE, Hardwrought.id("ore_fluorite"))));
    }

    /**
     * The work of the reactor, the turbine and the cascade, for the book: what a core does to its fuel,
     * the steam it makes, what the cascade spins out, and what is left when a core melts.
     */
    public static List<WorldRecipe> worldRecipes() {
        List<WorldRecipe> recipes = new ArrayList<>();
        ItemStack reactor = new ItemStack(NuclearBlocks.REACTOR_CONTROLLER);
        ItemStack cascade = new ItemStack(NuclearBlocks.CENTRIFUGE_CONTROLLER);
        ItemStack feedwater = FluidIcons.liquid(Fluids.WATER, ReactorController.WATER_PER_STEAM * 1000);
        recipes.add(new WorldRecipe(Hardwrought.id("reactor/spent_fuel_assembly"),
                WorldRecipes.one(new ItemStack(NuclearItems.FUEL_ASSEMBLY)), new ItemStack(NuclearItems.SPENT_FUEL_ASSEMBLY), reactor));
        recipes.add(new WorldRecipe(Hardwrought.id("reactor/irradiated_thorium_rod"),
                WorldRecipes.one(new ItemStack(NuclearItems.THORIUM_BREEDER_ROD)), new ItemStack(NuclearItems.IRRADIATED_THORIUM_ROD), reactor));
        recipes.add(new WorldRecipe(Hardwrought.id("reactor/steam"),
                WorldRecipes.one(new ItemStack(NuclearItems.FUEL_ASSEMBLY), feedwater), FluidIcons.gas(GasKind.STEAM, 1000), reactor));
        // The turbine's condensate is not shown: water made of steam made of water would send the book round in a circle.
        recipes.add(new WorldRecipe(Hardwrought.id("meltdown/corium"),
                WorldRecipes.one(new ItemStack(NuclearItems.FUEL_ASSEMBLY)), new ItemStack(NuclearBlocks.CORIUM), reactor));
        recipes.add(new WorldRecipe(Hardwrought.id("meltdown/radioactive_debris"),
                WorldRecipes.one(new ItemStack(NuclearItems.FUEL_ASSEMBLY)), new ItemStack(NuclearBlocks.RADIOACTIVE_DEBRIS), reactor));
        for (var recipe : CentrifugeController.recipes()) {
            List<List<ItemStack>> inputs = WorldRecipes.one(FluidIcons.gas(recipe.gas(), recipe.gasUnits()));
            recipes.add(new WorldRecipe(Hardwrought.id("centrifuge_cascade/depleted_uranium"), inputs,
                    recipe.outputs().getFirst().copy(), cascade));
            recipes.add(new WorldRecipe(Hardwrought.id("centrifuge_cascade/" + recipe.gasOut().getSerializedName()), inputs,
                    FluidIcons.gas(recipe.gasOut(), recipe.gasOutUnits()), cascade));
        }
        return recipes;
    }
}
