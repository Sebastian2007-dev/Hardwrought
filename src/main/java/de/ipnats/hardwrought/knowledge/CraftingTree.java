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
 * recipe that really makes it rather than turning it back out of something made of it: an ingot is
 * smelted, not knocked out of a block, waxed or not, that was made of ingots (see {@link #choiceFor}).
 * Whatever a recipe makes beyond what was needed is kept, and the next part of the tree that wants the
 * same thing takes it from there first: four planks made for one stick leave two for the next. With an
 * inventory given, what the player carries is taken before anything is made.
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
        /**
         * It can be made — something comes into its ring from outside — yet no recipe for it is followed
         * here, though the player chose none: the tree went round to it from above. A sound tree has none.
         */
        public boolean stuck;
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
        findRings();
        int level = page.entries().isEmpty() ? 0 : page.entries().getFirst().level();
        CompendiumPagePayload.Known shown = new CompendiumPagePayload.Known(stackOf(page.subject()), level);
        boolean fluid = FluidIcons.of(shown.stack()) != null;
        if (amount <= 0) {
            List<CompendiumPagePayload.Recipe> own = recipes.getOrDefault(page.subject(), List.of());
            int choice = choiceFor(page.subject(), own, Set.of(page.subject()), true);
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
        node.choice = choiceFor(key, own, inner, depth == 0);
        node.stuck = node.choice < 0 && !own.isEmpty() && entryDistance.containsKey(key) && chosen.apply(key) == null;
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

    /**
     * The player's choice where it still fits, else the recipe the shape of the recipes calls for.
     *
     * <p>Things that can be turned into each other and back — ingots, the block of them, the waxed block
     * that gives ingots again; raw copper and a block of raw copper — form a ring, and a recipe inside the
     * ring only moves a thing round it. What really makes a thing is a recipe that comes into its ring from
     * outside: ingots smelted from raw copper. So:
     * <ol>
     *   <li>a recipe that comes in from outside the ring is taken first;</li>
     *   <li>failing that, the recipe inside the ring that leads soonest to a thing that has one — a block
     *       is made of ingots, which are smelted;</li>
     *   <li>a ring nothing comes into from outside is found, not made: raw copper is mined. Only the
     *       thing the tree is about is still shown made, where a recipe packs it — the block of nine.</li>
     * </ol>
     * No rule names an item, so a new recipe of any kind falls into place by its shape alone.
     */
    private int choiceFor(Identifier key, List<CompendiumPagePayload.Recipe> own, Set<Identifier> path, boolean root) {
        Integer picked = chosen.apply(key);
        if (picked != null) return Math.clamp(picked, -1, own.size() - 1);
        int ring = rings.getOrDefault(key, -1);
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        int packing = -1;
        for (int index = 0; index < own.size(); index++) {
            CompendiumPagePayload.Recipe recipe = own.get(index);
            if (loops(recipe, path)) continue;
            // How far inside the ring this recipe still is from a way in: none for one that comes in.
            int distance = 0;
            for (Input input : inputs(recipe)) {
                if (rings.getOrDefault(input.key(), -2) == ring) {
                    distance = Math.max(distance, 1 + entryDistance.getOrDefault(input.key(), UNREACHABLE));
                }
            }
            if (distance < bestDistance) {
                best = index;
                bestDistance = distance;
            }
            if (packing < 0 && packs(recipe)) packing = index;
        }
        if (best >= 0 && bestDistance < UNREACHABLE) return best;
        // A ring with no way in.
        return root ? packing : -1;
    }

    /** Further than any way in: what a ring nothing comes into is from one. */
    private static final int UNREACHABLE = 1 << 20;

    /** Which ring each thing with a recipe belongs to: things that can be made out of each other share one. */
    private final Map<Identifier, Integer> rings = new HashMap<>();
    /** How many recipes inside its ring a thing is from one that comes in from outside; absent where none does. */
    private final Map<Identifier, Integer> entryDistance = new HashMap<>();

    /**
     * Finds the rings — the strongly connected parts of the graph from each thing to what its recipes
     * take — and how far each thing in one is from a recipe that comes in from outside it.
     */
    private void findRings() {
        // Tarjan's algorithm, with an explicit stack so a long chain cannot overflow the real one.
        Map<Identifier, Integer> order = new HashMap<>();
        Map<Identifier, Integer> low = new HashMap<>();
        java.util.ArrayDeque<Identifier> stack = new java.util.ArrayDeque<>();
        Set<Identifier> onStack = new HashSet<>();
        int[] counter = {0};
        for (Identifier start : recipes.keySet()) {
            if (order.containsKey(start)) continue;
            java.util.ArrayDeque<java.util.Iterator<Identifier>> work = new java.util.ArrayDeque<>();
            java.util.ArrayDeque<Identifier> visiting = new java.util.ArrayDeque<>();
            visit(start, order, low, stack, onStack, counter);
            visiting.push(start);
            work.push(successors(start).iterator());
            while (!work.isEmpty()) {
                Identifier here = visiting.peek();
                java.util.Iterator<Identifier> next = work.peek();
                if (next.hasNext()) {
                    Identifier there = next.next();
                    if (!order.containsKey(there)) {
                        visit(there, order, low, stack, onStack, counter);
                        visiting.push(there);
                        work.push(successors(there).iterator());
                    } else if (onStack.contains(there)) {
                        low.put(here, Math.min(low.get(here), order.get(there)));
                    }
                    continue;
                }
                work.pop();
                visiting.pop();
                if (!visiting.isEmpty()) low.put(visiting.peek(), Math.min(low.get(visiting.peek()), low.get(here)));
                if (low.get(here).equals(order.get(here))) {
                    int ring = rings.size();
                    Identifier member;
                    do {
                        member = stack.pop();
                        onStack.remove(member);
                        rings.put(member, ring);
                    } while (!member.equals(here));
                }
            }
        }
        // Ways in: a recipe all of whose parts lie outside the thing's ring. From there, inward, one
        // recipe at a time, to everything in the ring a recipe can reach it from.
        java.util.ArrayDeque<Identifier> frontier = new java.util.ArrayDeque<>();
        for (Map.Entry<Identifier, List<CompendiumPagePayload.Recipe>> entry : recipes.entrySet()) {
            int ring = rings.get(entry.getKey());
            for (CompendiumPagePayload.Recipe recipe : entry.getValue()) {
                if (inputs(recipe).stream().noneMatch(input -> rings.getOrDefault(input.key(), -1) == ring)) {
                    entryDistance.put(entry.getKey(), 0);
                    frontier.add(entry.getKey());
                    break;
                }
            }
        }
        while (!frontier.isEmpty()) {
            Identifier made = frontier.poll();
            int ring = rings.get(made);
            int distance = entryDistance.get(made);
            for (Map.Entry<Identifier, List<CompendiumPagePayload.Recipe>> entry : recipes.entrySet()) {
                Identifier key = entry.getKey();
                if (rings.get(key) != ring || entryDistance.containsKey(key)) continue;
                boolean takes = entry.getValue().stream()
                        .anyMatch(recipe -> inputs(recipe).stream().anyMatch(input -> input.key().equals(made)));
                if (takes) {
                    entryDistance.put(key, distance + 1);
                    frontier.add(key);
                }
            }
        }
    }

    private static void visit(Identifier node, Map<Identifier, Integer> order, Map<Identifier, Integer> low,
                              java.util.ArrayDeque<Identifier> stack, Set<Identifier> onStack, int[] counter) {
        order.put(node, counter[0]);
        low.put(node, counter[0]);
        counter[0]++;
        stack.push(node);
        onStack.add(node);
    }

    /** What the recipes for a thing take, among the things that have recipes of their own. */
    private List<Identifier> successors(Identifier key) {
        List<Identifier> next = new ArrayList<>();
        for (CompendiumPagePayload.Recipe recipe : recipes.getOrDefault(key, List.of())) {
            for (Input input : inputs(recipe)) if (recipes.containsKey(input.key()) && !next.contains(input.key())) next.add(input.key());
        }
        return next;
    }

    /** Whether a recipe takes something above it in the tree, itself included. */
    private boolean loops(CompendiumPagePayload.Recipe recipe, Set<Identifier> path) {
        for (Input input : inputs(recipe)) if (path.contains(input.key())) return true;
        return false;
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
