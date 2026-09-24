package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import de.ipnats.hardwrought.core.networking.CompendiumRequestPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recipe side of the compendium: how a thing is made, and what it is used in.
 *
 * <p>This is what a player reaches for with R and U, and it answers the way a recipe browser does,
 * with the one difference the milestone exists for: every stack goes out carrying the level this
 * player has reached on it, so an ingredient they have never held is drawn as a black shadow and
 * named with question marks. The recipe itself is never withheld. A shadow in a grid is an
 * invitation; a missing row is a dead end.
 *
 * <p>Recipes change only on a datapack reload, so both lookup directions are indexed once and
 * rebuilt when the recipe count moves. A keypress then costs a map lookup and the resolution of at
 * most {@link CompendiumPagePayload#MAX_RECIPES} displays, never a sweep over every recipe in the
 * game.
 */
public final class Compendium {
    /** Where things come from when nobody makes them: drops, block breaks and chests. */
    private final LootSources loot = new LootSources();
    private Map<Item, Set<RecipeHolder<?>>> byResult;
    private Map<Item, Set<RecipeHolder<?>>> byIngredient;
    private int indexedRecipes = -1;

    /** Answers one request from an open compendium. */
    public CompendiumPagePayload answer(ServerPlayer player, KnowledgeSystem knowledge,
                                        CompendiumRequestPayload request) {
        return switch (request.mode()) {
            case CompendiumPagePayload.MODE_RECIPES -> lookup(player, knowledge, request.subject(), true);
            case CompendiumPagePayload.MODE_USAGES -> lookup(player, knowledge, request.subject(), false);
            case CompendiumPagePayload.MODE_JOURNAL -> journal(player, knowledge, request.subject());
            default -> shelf(player, knowledge, request);
        };
    }

    // ---------------------------------------------------------------- the written half

    /**
     * The journal. Built on the server for the same reason a shelf is: whether a thought has occurred
     * to this player yet is a fact about their save, and a client that was handed the whole chain
     * would be handing it on to anyone who looked.
     */
    private CompendiumPagePayload journal(ServerPlayer player, KnowledgeSystem knowledge,
                                          Identifier subject) {
        return new CompendiumPagePayload(CompendiumPagePayload.MODE_JOURNAL, subject, List.of(),
                List.of(), List.of(), Journal.page(knowledge.knowledge(player)));
    }

    // ---------------------------------------------------------------- shelves

    private CompendiumPagePayload shelf(ServerPlayer player, KnowledgeSystem knowledge,
                                        CompendiumRequestPayload request) {
        KnowledgeCategory category = KnowledgeCategory.byName(request.subject().getPath());
        if (category == null) category = KnowledgeCategory.MATERIALS;
        List<CompendiumPagePayload.Entry> entries = new ArrayList<>();
        for (KnowledgeSystem.Entry entry : knowledge.page(player, category, request.search())) {
            if (entries.size() >= CompendiumPagePayload.MAX_ENTRIES) break;
            entries.add(new CompendiumPagePayload.Entry(entry.id(), entry.level().ordinal()));
        }
        return new CompendiumPagePayload(CompendiumPagePayload.MODE_SHELF,
                Identifier.fromNamespaceAndPath("hardwrought", category.serializedName()),
                entries, List.of(), List.of(), List.of());
    }

    // ---------------------------------------------------------------- recipes and usages

    private CompendiumPagePayload lookup(ServerPlayer player, KnowledgeSystem knowledge,
                                         Identifier subject, boolean asResult) {
        int mode = asResult ? CompendiumPagePayload.MODE_RECIPES : CompendiumPagePayload.MODE_USAGES;
        // The item registry answers an unknown name with air rather than nothing, so air is what a
        // request for something that does not exist looks like.
        Item item = BuiltInRegistries.ITEM.getValue(subject);
        MinecraftServer server = player.level().getServer();
        if (item == null || item == Items.AIR || server == null) {
            return new CompendiumPagePayload(mode, subject, List.of(), List.of(), List.of(), List.of());
        }
        // The browser needs to know whether it may name the subject in its own title, so the one
        // entry a lookup page carries is the subject itself.
        List<CompendiumPagePayload.Entry> heading = List.of(new CompendiumPagePayload.Entry(subject,
                knowledge.knowledge(player).level(subject).ordinal()));
        ContextMap context = SlotDisplayContext.fromLevel(player.level());
        var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(server);
        Map<Identifier, de.ipnats.hardwrought.core.registry.MaterialDefinition> materials =
                runtime == null ? Map.of() : runtime.materials();
        index(server.getRecipeManager(), context);
        Map<Item, Set<RecipeHolder<?>>> source = asResult ? byResult : byIngredient;
        PlayerKnowledge known = knowledge.knowledge(player);
        List<CompendiumPagePayload.Recipe> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : source.getOrDefault(item, Set.of())) {
            if (recipes.size() >= CompendiumPagePayload.MAX_RECIPES) break;
            for (RecipeDisplay display : holder.value().display()) {
                if (recipes.size() >= CompendiumPagePayload.MAX_RECIPES) break;
                CompendiumPagePayload.Recipe view = view(holder, display, context, known, materials);
                if (view != null) recipes.add(view);
            }
        }
        for (CompendiumPagePayload.Recipe inWorld : inWorldRecipes(item, asResult, known)) {
            if (recipes.size() >= CompendiumPagePayload.MAX_RECIPES) break;
            recipes.add(inWorld);
        }
        // What drops it belongs under how it is made, not under what it is used for: both answer
        // the same question a player asks when they want one and do not have one.
        List<CompendiumPagePayload.Source> sources = asResult
                ? loot.of(server, item, known) : List.of();
        return new CompendiumPagePayload(mode, subject, heading, recipes, sources, List.of());
    }

    private CompendiumPagePayload.Recipe view(RecipeHolder<?> holder, RecipeDisplay display,
                                              ContextMap context, PlayerKnowledge known,
                                              Map<Identifier, de.ipnats.hardwrought.core.registry.MaterialDefinition> materials) {
        List<CompendiumPagePayload.Slot> inputs = new ArrayList<>();
        int width = 0;
        int height = 0;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            width = shaped.width();
            height = shaped.height();
            for (SlotDisplay ingredient : shaped.ingredients()) inputs.add(slot(ingredient, context, known));
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            for (SlotDisplay ingredient : shapeless.ingredients()) inputs.add(slot(ingredient, context, known));
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            inputs.add(slot(furnace.ingredient(), context, known));
            inputs.add(slot(furnace.fuel(), context, known));
        } else if (display instanceof SmithingRecipeDisplay smithing) {
            inputs.add(slot(smithing.template(), context, known));
            inputs.add(slot(smithing.base(), context, known));
            inputs.add(slot(smithing.addition(), context, known));
        } else if (display instanceof StonecutterRecipeDisplay stonecutter) {
            inputs.add(slot(stonecutter.input(), context, known));
        }
        // A recipe type nobody here has heard of still has a result and a station, and showing those
        // is more use than showing nothing at all.
        while (inputs.size() > CompendiumPagePayload.MAX_SLOTS) inputs.remove(inputs.size() - 1);
        CompendiumPagePayload.Slot result = slot(display.result(), context, known);
        if (result.isEmpty()) return null;
        return new CompendiumPagePayload.Recipe(holder.id().identifier(), inputs, width, height,
                result, station(holder, display, context, known, materials), method(holder));
    }

    /**
     * What the player actually needs to make this, drawn beside the recipe.
     *
     * <p>Vanilla names a crafting table for every crafting recipe and a furnace for every smelting
     * one, and in this mod both answers are wrong: a crafting table is never made, and a furnace is
     * only one rung of a ladder. So a crafting recipe shows the lowest bench that may make its
     * result — nothing at all where the grid in the player's hands will do — and a smelting recipe
     * the coldest furnace that still melts what goes into it.
     */
    private CompendiumPagePayload.Slot station(RecipeHolder<?> holder, RecipeDisplay display, ContextMap context,
                                               PlayerKnowledge known,
                                               Map<Identifier, de.ipnats.hardwrought.core.registry.MaterialDefinition> materials) {
        RecipeType<?> type = holder.value().getType();
        if (type == RecipeType.CRAFTING) {
            List<ItemStack> results = resolve(display.result(), context);
            if (results.isEmpty()) return CompendiumPagePayload.Slot.EMPTY;
            return switch (de.ipnats.hardwrought.progression.BenchTier.required(results.getFirst())) {
                case de.ipnats.hardwrought.progression.BenchTier.INVENTORY -> CompendiumPagePayload.Slot.EMPTY;
                case de.ipnats.hardwrought.progression.BenchTier.HEWN ->
                        slot(List.of(new ItemStack(de.ipnats.hardwrought.core.registry.ModBlocks.HEWN_WORKBENCH)), known);
                default -> slot(List.of(new ItemStack(de.ipnats.hardwrought.core.registry.ModBlocks.NAILED_WORKBENCH)), known);
            };
        }
        if (type == RecipeType.SMELTING && display instanceof FurnaceRecipeDisplay furnace) {
            List<ItemStack> inputs = resolve(furnace.ingredient(), context);
            if (!inputs.isEmpty()) {
                var melting = de.ipnats.hardwrought.metallurgy.Smelting.meltingPoint(inputs.getFirst().getItem(), materials);
                if (melting.isPresent()) {
                    double degrees = melting.getAsDouble();
                    ItemStack needed = degrees <= de.ipnats.hardwrought.metallurgy.Smelting.BRICK_FURNACE_MAX_C
                            ? new ItemStack(de.ipnats.hardwrought.core.registry.ModBlocks.BRICK_FURNACE)
                            : degrees <= de.ipnats.hardwrought.metallurgy.Smelting.FURNACE_MAX_C
                                    ? new ItemStack(Items.FURNACE) : new ItemStack(Items.BLAST_FURNACE);
                    return slot(List.of(needed), known);
                }
            }
        }
        return slot(display.craftingStation(), context, known);
    }

    /** The top bookmarks group vanilla recipe displays by the actual way the player performs them. */
    private static int method(RecipeHolder<?> holder) {
        RecipeType<?> type = holder.value().getType();
        if (type == RecipeType.CAMPFIRE_COOKING || type == RecipeType.SMOKING) {
            return CompendiumPagePayload.METHOD_COOKING;
        }
        if (type == RecipeType.SMITHING) return CompendiumPagePayload.METHOD_SMITHING;
        if (type == RecipeType.SMELTING || type == RecipeType.BLASTING) {
            return CompendiumPagePayload.METHOD_SMELTING;
        }
        return CompendiumPagePayload.METHOD_CRAFTING;
    }

    /**
     * Recipes the recipe manager does not hold — work done in the world and work done by a machine —
     * come from {@link WorldRecipes}, where each mechanic describes its own. Nothing here names a
     * single one of them, so a new machine or a new row in a machine's table needs no change to the
     * book. Asking for how the machine itself is used lists what it works on.
     */
    private static List<CompendiumPagePayload.Recipe> inWorldRecipes(Item subject, boolean asResult,
                                                                     PlayerKnowledge known) {
        List<CompendiumPagePayload.Recipe> recipes = new ArrayList<>();
        for (WorldRecipes.WorldRecipe recipe : WorldRecipes.all()) {
            if (recipes.size() >= CompendiumPagePayload.MAX_RECIPES) break;
            if (asResult ? !recipe.makes(subject) : !recipe.uses(subject)) continue;
            List<CompendiumPagePayload.Slot> inputs = new ArrayList<>();
            for (List<ItemStack> options : recipe.inputs()) {
                if (inputs.size() >= CompendiumPagePayload.MAX_SLOTS) break;
                inputs.add(slot(options, known));
            }
            recipes.add(new CompendiumPagePayload.Recipe(recipe.id(), inputs, 0, 0,
                    slot(List.of(recipe.result()), known),
                    recipe.station().isEmpty() ? CompendiumPagePayload.Slot.EMPTY
                            : slot(List.of(recipe.station()), known),
                    CompendiumPagePayload.METHOD_IN_WORLD));
        }
        return List.copyOf(recipes);
    }

    private static CompendiumPagePayload.Slot slot(List<ItemStack> stacks, PlayerKnowledge known) {
        List<CompendiumPagePayload.Known> options = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (options.size() >= CompendiumPagePayload.MAX_OPTIONS) break;
            if (!stack.isEmpty()) options.add(known(stack, known));
        }
        return options.isEmpty() ? CompendiumPagePayload.Slot.EMPTY : new CompendiumPagePayload.Slot(options);
    }

    private static CompendiumPagePayload.Known known(ItemStack stack, PlayerKnowledge known) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new CompendiumPagePayload.Known(stack, known.level(id).ordinal());
    }

    private CompendiumPagePayload.Slot slot(SlotDisplay display, ContextMap context, PlayerKnowledge known) {
        if (display == null) return CompendiumPagePayload.Slot.EMPTY;
        List<CompendiumPagePayload.Known> options = new ArrayList<>();
        for (ItemStack stack : resolve(display, context)) {
            if (options.size() >= CompendiumPagePayload.MAX_OPTIONS) break;
            if (stack.isEmpty()) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            options.add(new CompendiumPagePayload.Known(stack, known.level(id).ordinal()));
        }
        return options.isEmpty() ? CompendiumPagePayload.Slot.EMPTY : new CompendiumPagePayload.Slot(options);
    }

    private static List<ItemStack> resolve(SlotDisplay display, ContextMap context) {
        try {
            return display.resolveForStacks(context);
        } catch (RuntimeException failure) {
            // A display that wants a context this one does not carry is drawn as a blank square
            // rather than taking the whole page down with it.
            return List.of();
        }
    }

    // ---------------------------------------------------------------- the index

    /**
     * Builds both lookup directions, and rebuilds them when a datapack reload has changed how many
     * recipes there are. Resolving every display once is what buys never resolving them all again on
     * a keypress.
     */
    private void index(RecipeManager manager, ContextMap context) {
        int count = manager.getRecipes().size();
        if (byResult != null && count == indexedRecipes) return;
        Map<Item, Set<RecipeHolder<?>>> results = new HashMap<>();
        Map<Item, Set<RecipeHolder<?>>> ingredients = new HashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            for (Item item : resultItems(holder, context)) {
                results.computeIfAbsent(item, key -> new LinkedHashSet<>()).add(holder);
            }
            for (Item item : ingredientItems(holder)) {
                ingredients.computeIfAbsent(item, key -> new LinkedHashSet<>()).add(holder);
            }
        }
        byResult = Map.copyOf(results);
        byIngredient = Map.copyOf(ingredients);
        indexedRecipes = count;
    }

    private static Set<Item> resultItems(RecipeHolder<?> holder, ContextMap context) {
        Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RecipeDisplay display : holder.value().display()) {
            for (ItemStack stack : resolve(display.result(), context)) {
                if (!stack.isEmpty()) items.add(stack.getItem());
            }
        }
        return items;
    }

    private static Set<Item> ingredientItems(RecipeHolder<?> holder) {
        Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
        // Placement info is the one place every recipe type lists what goes into it, shape or no
        // shape, so the usage direction needs no case per recipe type.
        try {
            holder.value().placementInfo().ingredients().forEach(ingredient ->
                    ingredient.items().forEach(item -> items.add(item.value())));
        } catch (RuntimeException failure) {
            // A recipe that refuses to describe its placement is simply not indexed by ingredient.
        }
        return items;
    }
}
