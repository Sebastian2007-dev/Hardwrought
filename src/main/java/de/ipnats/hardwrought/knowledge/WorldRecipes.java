package de.ipnats.hardwrought.knowledge;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Everything a player makes that the recipe manager has never heard of: work done in the world
 * with a tool, and work done by a machine.
 *
 * <p>The compendium reads recipes from two places and this is the second. Each mechanic describes
 * its own recipes where it is implemented and registers that description once; the compendium
 * then shows them under how a thing is made and what it is used in, exactly like a crafting
 * recipe, with the machine that does the work drawn as the station. A source is asked afresh on
 * every lookup, so a machine whose table grows — a new ore the crusher accepts — shows the new
 * row without anyone touching the book.
 *
 * <p>The instruction printed under a recipe is looked up as {@code recipe.<namespace>.<group>.instruction},
 * where the group is the recipe id's path up to its first slash. One line of text therefore covers
 * every row a machine produces: {@code hardwrought:crushing/raw_iron} and
 * {@code hardwrought:crushing/tin_ingot} both read {@code recipe.hardwrought.crushing.instruction}.
 */
public final class WorldRecipes {
    /**
     * One recipe that is performed rather than crafted.
     *
     * @param inputs  what goes in; each slot lists the items that would do, the first being shown
     * @param station the block that does the work, or empty where a player does it with their hands
     */
    public record WorldRecipe(Identifier id, List<List<ItemStack>> inputs, ItemStack result, ItemStack station) {
        public WorldRecipe {
            if (id == null || inputs == null || result == null || result.isEmpty()) {
                throw new IllegalArgumentException("A world recipe needs an id and a result");
            }
            inputs = List.copyOf(inputs);
            station = station == null ? ItemStack.EMPTY : station;
        }

        /** Whether this item goes into it, including as the machine that does the work. */
        public boolean uses(Item item) {
            if (!station.isEmpty() && station.is(item)) return true;
            for (List<ItemStack> slot : inputs) {
                for (ItemStack option : slot) {
                    if (option.is(item)) return true;
                }
            }
            return false;
        }

        public boolean makes(Item item) {
            return result.is(item);
        }
    }

    private static final List<Supplier<? extends List<WorldRecipe>>> SOURCES = new CopyOnWriteArrayList<>();

    private WorldRecipes() { }

    /** Adds one mechanic's recipes. Called once, from the mod initializer. */
    public static void register(Supplier<? extends List<WorldRecipe>> source) {
        SOURCES.add(source);
    }

    /** Every recipe from every source, as the sources describe them right now. */
    public static List<WorldRecipe> all() {
        List<WorldRecipe> recipes = new ArrayList<>();
        for (Supplier<? extends List<WorldRecipe>> source : SOURCES) {
            try {
                recipes.addAll(source.get());
            } catch (RuntimeException failure) {
                // One broken description must not take every other page of the book down with it.
            }
        }
        return recipes;
    }

    /** A single-slot input list, for the common case of one item going in. */
    public static List<List<ItemStack>> one(ItemStack... slots) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (ItemStack stack : slots) inputs.add(List.of(stack));
        return inputs;
    }

    /** Registers every mechanic in the mod that has recipes of this kind. */
    public static void initialize() {
        if (!SOURCES.isEmpty()) return;
        register(de.ipnats.hardwrought.progression.LogWorking::worldRecipes);
        register(de.ipnats.hardwrought.progression.NailDriving::worldRecipes);
        register(de.ipnats.hardwrought.metallurgy.Crushing::worldRecipes);
        register(de.ipnats.hardwrought.smithing.Smithing::worldRecipes);
        register(WorldRecipes::fireAndWater);
    }

    /** The two uses of a fire that are acts rather than cooking. */
    private static List<WorldRecipe> fireAndWater() {
        var sticks = new ItemStack(de.ipnats.hardwrought.core.registry.ModItems.LIGHTING_STICKS);
        var campfire = new ItemStack(net.minecraft.world.item.Items.CAMPFIRE);
        var skin = new ItemStack(de.ipnats.hardwrought.core.registry.ModItems.FILLED_WATERSKIN);
        return List.of(
                new WorldRecipe(de.ipnats.hardwrought.Hardwrought.id("light_campfire_in_world"),
                        one(sticks, campfire), campfire, ItemStack.EMPTY),
                new WorldRecipe(de.ipnats.hardwrought.Hardwrought.id("boil_waterskin_in_world"),
                        one(skin, campfire), skin, ItemStack.EMPTY));
    }
}
