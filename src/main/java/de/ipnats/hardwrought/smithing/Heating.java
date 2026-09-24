package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.metallurgy.Smelting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * A furnace bringing a piece of metal to working heat for the anvil.
 *
 * <p>The furnace does not know it is doing this. When it holds a piece of forge metal and finds no
 * recipe for it, it is handed one: a smelting recipe whose result is the piece itself. From there on
 * vanilla does all of it — fuel, cooking time, the slower brick furnace, the output slot — and the
 * only thing added afterwards is the heat, put on the piece as it comes out.
 *
 * <p>The piece comes out as hot as the furnace gets, but never past the top of its working range: a
 * fire hot enough to cast iron would only burn a pickaxe head.
 */
public final class Heating {
    /** Ticks to bring one piece up to heat, before the furnace's own speed. */
    public static final int HEATING_TICKS = 100;
    private static final ResourceKey<Recipe<?>> KEY = ResourceKey.create(Registries.RECIPE, Hardwrought.id("heating"));

    private static final Map<Item, RecipeHolder<SmeltingRecipe>> HOLDERS = new HashMap<>();

    /** Marks the recipes this class makes, so the result can be told apart from a real smelt. */
    public static final class HeatingRecipe extends SmeltingRecipe {
        HeatingRecipe(Item item) {
            super(new Recipe.CommonInfo(false), new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, ""),
                    Ingredient.of(item), new ItemStackTemplate(item), 0.0f, HEATING_TICKS);
        }
    }

    private Heating() { }

    /** The heating recipe for this piece, where it is a piece the anvil takes. */
    public static RecipeHolder<SmeltingRecipe> recipeFor(ItemStack input) {
        if (input.isEmpty() || Smelting.isCast(input.getItem()) || !Smithing.isForgeMetal(input)) return null;
        return HOLDERS.computeIfAbsent(input.getItem(), item -> new RecipeHolder<>(KEY, new HeatingRecipe(item)));
    }

    /**
     * What comes out of the furnace: the complete stack that went in — progress, quality and all —
     * at the furnace's heat or the top of the piece's working range, whichever is lower. Heating is
     * one operation on a load; unlike smelting it does not process the load one item at a time.
     */
    public static ItemStack heated(ServerLevel level, AbstractFurnaceBlockEntity furnace, ItemStack input) {
        ItemStack piece = input.copy();
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return piece;
        OptionalDouble melting = Smelting.meltingPoint(piece.getItem(), runtime.materials());
        double ceiling = melting.isPresent() ? melting.getAsDouble() * Smithing.WORKING_MAX : Double.MAX_VALUE;
        Smithing.heat(piece, Math.min(Smelting.maxTemperature(furnace), ceiling), level.getGameTime(),
                runtime.materials());
        return piece;
    }
}
