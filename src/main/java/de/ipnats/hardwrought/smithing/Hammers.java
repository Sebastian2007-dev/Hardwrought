package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * The smith's hammers and how much of the metal one blow of each moves.
 *
 * <p>A wooden mallet only dents the square it lands on. A stone head is heavier and spreads the blow
 * over two by two squares; an iron one over three by three; a steel one over four by four — and so
 * on up the metals, a square wider with every rung, to adamant's ten by ten. Each also lasts longer
 * than the one before.
 *
 * <p>There is no skipping a rung. Up to tungsten steel a hammer is the one below it with a better
 * head in place of the old, and that head wants the hammer below to be forged with: the iron
 * hammer's head a stone hammer, the steel one's an iron hammer. Beyond that nothing is forged: a
 * tungsten steel hammer is worked up into a netherite one at the smithing table, that into mithril
 * and that into adamant, like every other piece of those tiers (see {@link Upgrading}).
 */
public final class Hammers {
    private Hammers() { }

    /** Every hammer, the plainest first. A hammer's place here is how wide its blow is. */
    public static java.util.List<net.minecraft.world.item.Item> all() {
        return java.util.List.of(ModItems.WOODEN_HAMMER, ModItems.HAMMER, ModItems.IRON_HAMMER, ModItems.STEEL_HAMMER,
                ModItems.STAINLESS_STEEL_HAMMER, ModItems.TITANIUM_HAMMER, ModItems.TUNGSTEN_STEEL_HAMMER,
                ModItems.NETHERITE_HAMMER, ModItems.MITHRIL_HAMMER, ModItems.ADAMANT_HAMMER);
    }

    /** The hammer whose head is forged of this metal, or null where the metal makes none. */
    public static net.minecraft.world.item.Item of(ToolParts.SmithMetal metal) {
        return switch (metal) {
            case IRON -> ModItems.IRON_HAMMER;
            case STEEL -> ModItems.STEEL_HAMMER;
            case STAINLESS_STEEL -> ModItems.STAINLESS_STEEL_HAMMER;
            case TITANIUM -> ModItems.TITANIUM_HAMMER;
            case TUNGSTEN_STEEL -> ModItems.TUNGSTEN_STEEL_HAMMER;
            default -> null;
        };
    }

    /** How many squares wide one blow of this hammer is, from 1 for the mallet; 0 where it is no hammer. */
    public static int reach(ItemStack stack) {
        return all().indexOf(stack.getItem()) + 1;
    }

    /**
     * The least hammer a piece needs to be forged: a sheet of armor wants at least a stone one, and
     * a hammer's head the hammer below the one it is for.
     */
    public static int reachNeeded(net.minecraft.world.item.Item result) {
        ToolParts.SmithMetal metal = ToolParts.metalOf(result);
        if (metal != null) {
            for (ToolParts.Part part : ToolParts.ARMOR) {
                if (metal.parts().contains(part) && ToolParts.part(metal, part) == result) return 2;
            }
        }
        if (metal != null && metal.parts().contains(ToolParts.Part.HAMMER_HEAD)
                && ToolParts.part(metal, ToolParts.Part.HAMMER_HEAD) == result) {
            net.minecraft.world.item.Item hammer = of(metal);
            return hammer == null ? 2 : reach(new ItemStack(hammer)) - 1;
        }
        return 1;
    }

    /** The plainest hammer that will forge this piece: what a smith holding a lighter one is told to fetch. */
    public static net.minecraft.world.item.Item least(net.minecraft.world.item.Item result) {
        return all().get(Math.clamp(reachNeeded(result), 1, all().size()) - 1);
    }

    public static boolean isHammer(ItemStack stack) {
        return reach(stack) > 0;
    }

    /** The first square a blow of this reach covers, relative to where it lands. */
    public static int from(int reach) {
        return -(reach - 1) / 2;
    }

    /** The last square a blow of this reach covers, relative to where it lands. */
    public static int to(int reach) {
        return reach / 2;
    }
}
