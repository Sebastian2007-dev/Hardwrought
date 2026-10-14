package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A crafting tree worked out from the recipes the server sent for it: what the subject takes, what
 * each of those takes, and how many of everything, down to the things that are found rather than made.
 *
 * <p>Where a thing can be made more than one way, the player's choice is followed, and without one the
 * first recipe that neither goes round in a circle nor undoes another — an ingot is smelted, not
 * knocked out of a block that was made of ingots. Whatever a recipe makes beyond what was needed is
 * kept, and the next part of the tree that wants the same thing takes it from there first: four planks
 * made for one stick leave two for the next. With an inventory given, what the player carries is
 * taken before anything is made.
 */
public final class CraftingTree {
    /** Deeper than any real chain; past this a thing is treated as found. */
    public static final int MAX_DEPTH = 16;
    /** Every use of a thing is its own node, so a wide tree is bounded too. */
    public static final int MAX_NODES = 600;

    /** One thing in the tree, at one place it is needed. */
    public static final class Node {
        public final Identifier key;
        /** The stack drawn for it: the item, or a liquid or gas as itself. */
        public final CompendiumPagePayload.Known shown;
        /** Whether its amounts are millibuckets or units of gas rather than items. */
        public final boolean fluid;
        public long needed;
        /** Taken from what the player carries. */
        public long fromInventory;
        /** Taken from what another part of the tree made too much of. */
        public long fromLeftover;
        /** How many times the recipe is worked, and how many one working makes. */
        public long batches;
        public long perBatch;
        public long leftover;
        /** The recipe followed, or null where the thing is found rather than made here. */
        public CompendiumPagePayload.Recipe recipe;
        /** The recipe chosen, by index, or -1 for none; and how many there are to choose from. */
        public int choice = -1;
        public int choices;
        /** The chosen recipe would lead back to something above it, so it is not followed. */
        public boolean loop;
        public final List<Node> children = new ArrayList<>();
        /** Where the browser laid it out, in tree units. */
        public int x;
        public int y;
        public int width;

        Node(Identifier key, CompendiumPagePayload.Known shown, boolean fluid) {
            this.key = key;
            this.shown = shown;
            this.fluid = fluid;
        }

        /** How much of it is still to be made or found once the inventory and leftovers are used. */
        public long missing() {
            return needed - fromInventory - fromLeftover;
        }
    }

    /** One line of a total: a thing, as drawn, and how much of it. */
    public record Amount(Identifier key, CompendiumPagePayload.Known shown, boolean fluid, long amount) { }

    private record Input(Identifier key, CompendiumPagePayload.Known shown, List<CompendiumPagePayload.Known> options,
                         long amount) { }

    private final Map<Identifier, List<CompendiumPagePayload.Recipe>> recipes = new HashMap<>();
    private final Function<Identifier, Integer> chosen;
    /** What the player carries, as it is used up; null where the inventory is not counted. */
    private final Map<Identifier, Long> inventory;
    private final Map<Identifier, Long> leftovers = new HashMap<>();
    private final Map<Identifier, Amount> raw = new LinkedHashMap<>();
    private final Map<Identifier, Amount> spare = new LinkedHashMap<>();
    private int nodes;
    public final Node root;

    /**
     * @param page      the server's answer to a tree request
     * @param amount    how much of the subject is wanted, or 0 for one working of its recipe
     * @param chosen    the player's choice of recipe for a thing, null where they have made none
     * @param inventory how much of each thing the player carries, or null to leave it out
     */
    public CraftingTree(CompendiumPagePayload page, long amount, Function<Identifier, Integer> chosen,
                        Map<Identifier, Long> inventory) {
        this.chosen = chosen;
        this.inventory = inventory == null ? null : new HashMap<>(inventory);
        for (CompendiumPagePayload.Recipe recipe : page.recipes()) {
            if (recipe.result().isEmpty()) continue;
            Identifier key = Compendium.treeKey(Compendium.shownOption(recipe.result()).stack());
            if (key != null) recipes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe);
        }
        int level = page.entries().isEmpty() ? 0 : page.entries().getFirst().level();
        CompendiumPagePayload.Known shown = new CompendiumPagePayload.Known(stackOf(page.subject()), level);
        boolean fluid = FluidIcons.of(shown.stack()) != null;
        if (amount <= 0) {
            List<CompendiumPagePayload.Recipe> own = recipes.getOrDefault(page.subject(), List.of());
            int choice = choiceFor(page.subject(), own, Set.of(page.subject()));
            amount = choice < 0 ? 1 : Math.max(1, resultAmount(own.get(choice), fluid));
        }
        // The thing asked about is wanted made, not taken out of a pocket.
        root = expand(page.subject(), shown, List.of(shown), amount, Set.of(), 0, false);
    }

    /** What is still to be found, everything made from it being made. */
    public List<Amount> raw() {
        return List.copyOf(raw.values());
    }

    /** What is made beyond what is needed. */
    public List<Amount> leftovers() {
        return List.copyOf(spare.values());
    }

    /** How many recipes there are for one thing. */
    public int choices(Identifier key) {
        return recipes.getOrDefault(key, List.of()).size();
    }

    private Node expand(Identifier key, CompendiumPagePayload.Known shown, List<CompendiumPagePayload.Known> options,
                        long needed, Set<Identifier> path, int depth, boolean useStock) {
        nodes++;
        Node node = new Node(key, shown, FluidIcons.of(shown.stack()) != null);
        node.needed = needed;
        long rest = needed;
        if (useStock) {
            long fromSpare = Math.min(rest, leftovers.getOrDefault(key, 0L));
            if (fromSpare > 0) {
                leftovers.merge(key, -fromSpare, Long::sum);
                node.fromLeftover = fromSpare;
                rest -= fromSpare;
            }
            if (inventory != null && !node.fluid) {
                for (CompendiumPagePayload.Known option : options) {
                    if (rest <= 0) break;
                    Identifier carried = Compendium.treeKey(option.stack());
                    long take = Math.min(rest, inventory.getOrDefault(carried, 0L));
                    if (take <= 0) continue;
                    inventory.merge(carried, -take, Long::sum);
                    node.fromInventory += take;
                    rest -= take;
                }
            }
        }
        List<CompendiumPagePayload.Recipe> own = recipes.getOrDefault(key, List.of());
        node.choices = own.size();
        Set<Identifier> inner = new HashSet<>(path);
        inner.add(key);
        node.choice = choiceFor(key, own, inner);
        if (node.choice >= 0) {
            CompendiumPagePayload.Recipe recipe = own.get(node.choice);
            if (loops(recipe, inner) || depth >= MAX_DEPTH || nodes >= MAX_NODES) node.loop = true;
            else node.recipe = recipe;
        }
        if (rest <= 0) return node;
        if (node.recipe == null) {
            raw.merge(key, new Amount(key, shown, node.fluid, rest),
                    (was, more) -> new Amount(key, was.shown(), was.fluid(), was.amount() + more.amount()));
            return node;
        }
        node.perBatch = Math.max(1, resultAmount(node.recipe, node.fluid));
        node.batches = Math.ceilDiv(rest, node.perBatch);
        node.leftover = node.batches * node.perBatch - rest;
        for (Input input : inputs(node.recipe)) {
            node.children.add(expand(input.key(), input.shown(), input.options(), input.amount() * node.batches,
                    inner, depth + 1, true));
        }
        // Kept only once the parts are worked out, so a thing cannot be made of its own leftovers.
        if (node.leftover > 0) {
            leftovers.merge(key, node.leftover, Long::sum);
            spare.merge(key, new Amount(key, shown, node.fluid, node.leftover),
                    (was, more) -> new Amount(key, was.shown(), was.fluid(), was.amount() + more.amount()));
        }
        return node;
    }

    /** The player's choice where it still fits, else the best recipe there is by the rules above. */
    private int choiceFor(Identifier key, List<CompendiumPagePayload.Recipe> own, Set<Identifier> path) {
        Integer picked = chosen.apply(key);
        if (picked != null) return Math.clamp(picked, -1, own.size() - 1);
        int packing = -1;
        int any = -1;
        for (int index = 0; index < own.size(); index++) {
            CompendiumPagePayload.Recipe recipe = own.get(index);
            if (loops(recipe, path)) continue;
            if (any < 0) any = index;
            if (reversible(key, recipe) == null) return index;
            if (packing < 0 && packs(recipe)) packing = index;
        }
        return packing >= 0 ? packing : any;
    }

    /** Whether a recipe takes something above it in the tree, itself included. */
    private boolean loops(CompendiumPagePayload.Recipe recipe, Set<Identifier> path) {
        for (Input input : inputs(recipe)) if (path.contains(input.key())) return true;
        return false;
    }

    /**
     * The one thing a recipe is made of, where another recipe makes that thing back out of what this
     * one makes — a block of ingots and ingots out of a block; null for any other recipe.
     */
    private Identifier reversible(Identifier key, CompendiumPagePayload.Recipe recipe) {
        List<Input> inputs = inputs(recipe);
        if (inputs.size() != 1) return null;
        Identifier from = inputs.getFirst().key();
        for (CompendiumPagePayload.Recipe back : recipes.getOrDefault(from, List.of())) {
            List<Input> backInputs = inputs(back);
            if (backInputs.size() == 1 && backInputs.getFirst().key().equals(key)) return from;
        }
        return null;
    }

    /** Whether a recipe of one ingredient makes fewer than it takes: nuggets into an ingot, not the reverse. */
    private boolean packs(CompendiumPagePayload.Recipe recipe) {
        List<Input> inputs = inputs(recipe);
        return inputs.size() == 1 && inputs.getFirst().amount() > resultAmount(recipe, false);
    }

    /** What one working of a recipe takes, each thing once with how much of it. */
    private static List<Input> inputs(CompendiumPagePayload.Recipe recipe) {
        Map<Identifier, Input> merged = new LinkedHashMap<>();
        for (CompendiumPagePayload.Slot slot : recipe.inputs()) {
            if (slot.isEmpty()) continue;
            CompendiumPagePayload.Known shown = Compendium.shownOption(slot);
            Identifier key = Compendium.treeKey(shown.stack());
            if (key == null) continue;
            long amount = amountOf(shown.stack());
            merged.merge(key, new Input(key, shown, slot.options(), amount),
                    (was, more) -> new Input(key, was.shown(), was.options(), was.amount() + more.amount()));
        }
        return List.copyOf(merged.values());
    }

    /**
     * How much one working makes, in the unit of the thing it is wanted as: a bucket where millibuckets
     * are wanted is a thousand of them, and a thousand millibuckets where a bucket is wanted is one.
     */
    private static long resultAmount(CompendiumPagePayload.Recipe recipe, boolean fluid) {
        ItemStack result = Compendium.shownOption(recipe.result()).stack();
        boolean resultFluid = FluidIcons.of(result) != null;
        long amount = amountOf(result);
        if (fluid && !resultFluid) return amount * 1000;
        if (!fluid && resultFluid) return Math.max(1, amount / 1000);
        return amount;
    }

    /** How much a stack in a recipe stands for: its count, or for a liquid or gas its amount. */
    public static long amountOf(ItemStack stack) {
        FluidIcons.Ingredient ingredient = FluidIcons.of(stack);
        return ingredient != null ? ingredient.amount() : stack.getCount();
    }

    /** A stack to draw for what a tree is about: the item, or the liquid or gas a page of its own is about. */
    private static ItemStack stackOf(Identifier subject) {
        FluidIcons.Ingredient fluid = FluidIcons.ofSubject(subject);
        if (fluid != null) return FluidIcons.stack(fluid);
        Item item = BuiltInRegistries.ITEM.getValue(subject);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
