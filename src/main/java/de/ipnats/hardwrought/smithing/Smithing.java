package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.registry.MaterialDefinition;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.metallurgy.Metal;
import de.ipnats.hardwrought.metallurgy.ModMetals;
import de.ipnats.hardwrought.metallurgy.Smelting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Section 38: what the anvil makes out of what, at which heat, and what fire and water do to the
 * result afterwards.
 *
 * <p>Temperatures are fractions of a metal's melting point, which is what a smith's rules of thumb
 * really are. Iron, melting at 1538 °C, is worked between about 850 and 1300 °C, hardens when
 * quenched from above about 690 °C and tempers between about 185 and 460 °C — the numbers a smith
 * would recognise — and a metal added later gets sensible ones without anyone writing them down.
 *
 * <p>Nothing here is listed per item. Every raw ore the mod knows forges into its ingot, and every
 * part {@link ToolParts} registers forges out of its metal's ingot, so a new metal or a new part is
 * on the anvil the moment it exists.
 */
public final class Smithing {
    /** Coldest a piece can be hammered without straining it. */
    public static final double WORKING_MIN = 0.55;
    /**
     * Hottest a fire takes it, as a share of its melting point: all the way up to it. A piece follows
     * the fire it lies in, and only its own melting point stops it.
     */
    public static final double WORKING_MAX = 1.0;
    /** Quenched from above this, iron hardens. */
    public static final double HARDENING = 0.45;
    /** Warmed back to this band after quenching, it tempers. */
    public static final double TEMPER_MIN = 0.12;
    public static final double TEMPER_MAX = 0.30;
    /** How much heat one blow of the hammer takes out of the piece. */
    public static final double HEAT_PER_BLOW = 6.0;

    /** One thing the anvil turns into another. */
    public record Recipe(Item input, int count, Item result, boolean part) {
        public Identifier id() {
            Identifier in = BuiltInRegistries.ITEM.getKey(input);
            Identifier out = BuiltInRegistries.ITEM.getKey(result);
            return de.ipnats.hardwrought.Hardwrought.id("forging/" + in.getNamespace() + "_" + in.getPath()
                    + "_to_" + out.getNamespace() + "_" + out.getPath());
        }
    }

    private static final List<Recipe> RECIPES = new ArrayList<>();

    private Smithing() { }

    /** Every recipe the anvil knows. */
    public static List<Recipe> recipes() {
        if (RECIPES.isEmpty()) {
            RECIPES.add(new Recipe(Items.RAW_IRON, 1, Items.IRON_INGOT, false));
            RECIPES.add(new Recipe(Items.RAW_COPPER, 1, Items.COPPER_INGOT, false));
            RECIPES.add(new Recipe(Items.RAW_GOLD, 1, Items.GOLD_INGOT, false));
            // Things that are forged rather than crafted, and are not tool parts.
            RECIPES.add(new Recipe(de.ipnats.hardwrought.core.registry.ModItems.BRONZE_INGOT, 1,
                    de.ipnats.hardwrought.core.registry.ModItems.BRONZE_NAILS, false));
            RECIPES.add(new Recipe(Items.IRON_INGOT, 1,
                    de.ipnats.hardwrought.core.registry.ModItems.IRON_SEWING_NEEDLE, false));
            for (Metal metal : Metal.values()) {
                RECIPES.add(new Recipe(ModMetals.raw(metal), 1, ModMetals.ingot(metal), false));
            }
            for (ToolParts.SmithMetal metal : ToolParts.SmithMetal.values()) {
                for (ToolParts.Part part : metal.parts()) {
                    RECIPES.add(new Recipe(metal.ingot(), part.ingots(), ToolParts.part(metal, part), true));
                }
            }
        }
        return RECIPES;
    }

    /** What this piece can be forged into. A piece already under way can only become what it was. */
    public static List<Recipe> recipesFor(ItemStack stack) {
        ForgingState state = stack.get(ModDataComponents.FORGING_STATE);
        List<Recipe> found = new ArrayList<>();
        for (Recipe recipe : recipes()) {
            if (!stack.is(recipe.input())) continue;
            if (state != null && !BuiltInRegistries.ITEM.getKey(recipe.result()).equals(state.result())) continue;
            found.add(recipe);
        }
        return found;
    }

    /** The recipe that makes this result, or null. */
    public static Recipe recipeFor(Item input, Identifier result) {
        for (Recipe recipe : recipes()) {
            if (recipe.input() == input && BuiltInRegistries.ITEM.getKey(recipe.result()).equals(result)) return recipe;
        }
        return null;
    }

    public static OptionalDouble meltingPoint(Item item, Map<Identifier, MaterialDefinition> materials) {
        return Smelting.meltingPoint(item, materials);
    }

    /** The heat range this piece can be worked in, or null where it is not a metal the anvil takes. */
    public static double[] workingRange(Item item, Map<Identifier, MaterialDefinition> materials) {
        OptionalDouble melting = meltingPoint(item, materials);
        if (melting.isEmpty()) return null;
        double point = melting.getAsDouble();
        return new double[] { point * WORKING_MIN, point * WORKING_MAX };
    }

    /** Only iron takes a hardening. Bronze, gold and copper come out of the water as soft as they went in. */
    public static boolean hardenable(Item item) {
        Identifier material = Smelting.materialOf(item);
        return material != null && material.getPath().equals("iron");
    }

    /**
     * Puts heat into a piece, and applies what that heat does to its treatment: back above the
     * hardening point it is soft again, whatever was done to it before; warmed gently after being
     * quenched, it tempers.
     */
    public static void heat(ItemStack stack, double celsius, long now, Map<Identifier, MaterialDefinition> materials) {
        stack.set(ModDataComponents.HEAT, new Heat((float) celsius, now));
        ForgeQuality quality = ForgeQuality.of(stack);
        if (quality == null || !hardenable(stack.getItem())) return;
        OptionalDouble melting = meltingPoint(stack.getItem(), materials);
        if (melting.isEmpty()) return;
        double fraction = celsius / melting.getAsDouble();
        if (fraction > HARDENING) {
            stack.set(ModDataComponents.FORGE_QUALITY, quality.withTreatment(ForgeQuality.Treatment.AIR));
        } else if (quality.treatment() == ForgeQuality.Treatment.QUENCHED
                && fraction >= TEMPER_MIN && fraction <= TEMPER_MAX) {
            stack.set(ModDataComponents.FORGE_QUALITY, quality.withTreatment(ForgeQuality.Treatment.TEMPERED));
        }
    }

    /**
     * Plunges a piece into water. It is cold at once; and iron that went in above its hardening
     * point comes out hard. Returns the temperature it was quenched from.
     */
    public static double quench(ItemStack stack, long now, Map<Identifier, MaterialDefinition> materials) {
        double was = Heat.of(stack, now);
        stack.remove(ModDataComponents.HEAT);
        ForgeQuality quality = ForgeQuality.of(stack);
        if (quality != null && hardenable(stack.getItem())) {
            OptionalDouble melting = meltingPoint(stack.getItem(), materials);
            if (melting.isPresent() && was / melting.getAsDouble() > HARDENING) {
                stack.set(ModDataComponents.FORGE_QUALITY, quality.withTreatment(ForgeQuality.Treatment.QUENCHED));
            }
        }
        return was;
    }

    /**
     * The anvil's recipes as the compendium shows them: what goes in, how many of it, the hammer, and
     * the anvil as the station. Read straight off {@link #recipes()}, so nothing is listed twice.
     */
    public static List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> worldRecipes() {
        ItemStack anvil = new ItemStack(de.ipnats.hardwrought.core.registry.ModBlocks.WOODEN_ANVIL);
        List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> shown = new ArrayList<>();
        for (Recipe recipe : recipes()) {
            // Every hammer that will do, the plainest first: a bar needs no more than a wooden
            // mallet, and the book must not send a player off to make a hammer they cannot yet make.
            List<ItemStack> hammers = new ArrayList<>();
            for (Item hammer : List.of(de.ipnats.hardwrought.core.registry.ModItems.WOODEN_HAMMER,
                    de.ipnats.hardwrought.core.registry.ModItems.HAMMER,
                    de.ipnats.hardwrought.core.registry.ModItems.IRON_HAMMER)) {
                if (Hammers.reach(new ItemStack(hammer)) >= Hammers.reachNeeded(recipe.result())) {
                    hammers.add(new ItemStack(hammer));
                }
            }
            shown.add(new de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe(recipe.id(),
                    List.of(List.of(new ItemStack(recipe.input(), recipe.count())), List.copyOf(hammers)),
                    new ItemStack(recipe.result()), anvil));
        }
        return shown;
    }

    /** True where this item is forged metal of some kind: raw ore, ingot, part or piece under way. */
    public static boolean isForgeMetal(ItemStack stack) {
        if (stack.has(ModDataComponents.FORGING_STATE)) return true;
        for (Recipe recipe : recipes()) {
            if (stack.is(recipe.input()) || stack.is(recipe.result())) return true;
        }
        return false;
    }
}
