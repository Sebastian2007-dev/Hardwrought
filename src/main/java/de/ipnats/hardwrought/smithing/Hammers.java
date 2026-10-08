package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * The four smith's hammers and how much of the metal one blow of each moves.
 *
 * <p>A wooden mallet only dents the square it lands on. A stone head is heavier and spreads the blow
 * over two by two squares; an iron one over three by three; a steel one over four by four, and it
 * lasts three times as long. Each is made from the one below — its handle and binding, with the
 * better head in place of the old — so there is no skipping a rung: the iron hammer's head wants a
 * stone hammer to forge it, the steel hammer's head an iron one.
 */
public final class Hammers {
    private Hammers() { }

    /** How many squares wide one blow of this hammer is: 1, 2 or 3; 0 where it is no hammer. */
    public static int reach(ItemStack stack) {
        if (stack.is(ModItems.WOODEN_HAMMER)) return 1;
        if (stack.is(ModItems.HAMMER)) return 2;
        if (stack.is(ModItems.IRON_HAMMER)) return 3;
        if (stack.is(ModItems.STEEL_HAMMER)) return 4;
        return 0;
    }

    /**
     * The least hammer a piece needs to be forged: a hammer head or a sheet of armor wants at least a
     * stone one, a steel hammer head an iron one.
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
            return metal == ToolParts.SmithMetal.STEEL ? 3 : 2;
        }
        return 1;
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
