package de.ipnats.hardwrought.smeltery;

import de.ipnats.hardwrought.smithing.ToolParts;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The casts: one for a bar and one for every part a tool is made of.
 *
 * <p>A cast starts as a {@link #BLANK} of fireclay. Laid on a casting table, a bar or a part pressed
 * into it leaves its shape; fired in a furnace, that is a cast, and it lasts. So the first part of a
 * kind is always forged — there is nothing to press into the clay otherwise — and the cast only
 * copies it.
 *
 * <p>A copy is not as good as the piece a smith made: a cast part comes out at
 * {@link #CAST_CRAFTSMANSHIP} and has to be worked over at a grindstone
 * ({@link de.ipnats.hardwrought.smithing.Grinding}) to be a decent part.
 */
public final class Casts {
    /** How well made a part is as it comes out of the cast: rough. */
    public static final float CAST_CRAFTSMANSHIP = 0.2f;
    /** The shape of the bar's cast; every other shape is named after its part. */
    public static final String INGOT = "ingot";

    /**
     * One shape.
     *
     * @param part the part it makes, or null for the bar
     */
    public record Cast(String shape, ToolParts.Part part, Item unfired, Item fired) {
        /** How much metal fills it. */
        public int amount() {
            return (part == null ? 1 : part.ingots()) * MoltenMetals.INGOT;
        }

        /** What this metal sets into in it, or null where there is no such thing: a pickaxe head of tin, say. */
        public Item result(String material) {
            if (part == null) return MoltenMetals.ingot(material);
            for (ToolParts.SmithMetal metal : ToolParts.SmithMetal.values()) {
                if (metal.material().equals(material) && metal.parts().contains(part)) return ToolParts.part(metal, part);
            }
            return null;
        }
    }

    /** Fireclay patted flat: a cast that has no shape yet. */
    public static final Item BLANK = SmelteryBlocks.item("cast_blank", Item::new);

    private static final Map<String, Cast> BY_SHAPE = new LinkedHashMap<>();

    static {
        BY_SHAPE.put(INGOT, new Cast(INGOT, null, SmelteryBlocks.UNFIRED_INGOT_CAST, SmelteryBlocks.INGOT_CAST));
        for (ToolParts.Part part : ToolParts.Part.values()) {
            BY_SHAPE.put(part.id(), new Cast(part.id(), part, SmelteryBlocks.item("unfired_" + part.id() + "_cast", Item::new),
                    SmelteryBlocks.item(part.id() + "_cast", Item::new)));
        }
    }

    /** Never held: the metal standing in a cast, drawn by the casting table in the cast's own shape. */
    public static final Item FILL = SmelteryBlocks.item("casting_fill", Item::new);

    private Casts() { }

    /** Touching the class registers the casts. */
    public static void initialize() { }

    public static List<Cast> all() {
        return List.copyOf(BY_SHAPE.values());
    }

    /** The cast this stack is, fired and ready to be poured into; null for anything else. */
    public static Cast of(ItemStack stack) {
        for (Cast cast : BY_SHAPE.values()) if (stack.is(cast.fired())) return cast;
        return null;
    }

    /** The cast this stack is, still unfired; null for anything else. */
    public static Cast unfired(ItemStack stack) {
        for (Cast cast : BY_SHAPE.values()) if (stack.is(cast.unfired())) return cast;
        return null;
    }

    /** The cast a bar or a part leaves in a blank, or null where the thing leaves none. */
    public static Cast imprintOf(ItemStack pressed) {
        ToolParts.Part part = ToolParts.partOf(pressed.getItem());
        if (part != null) return BY_SHAPE.get(part.id());
        MoltenMetals.Melt melt = MoltenMetals.melt(pressed.getItem());
        return melt != null && MoltenMetals.ingot(melt.material()) == pressed.getItem() ? BY_SHAPE.get(INGOT) : null;
    }

    /** What lies on a casting table: a blank or a fired cast. */
    public static boolean liesOnTable(ItemStack stack) {
        return stack.is(BLANK) || of(stack) != null;
    }

    /** Every cast a player can hold, for the creative tab. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        items.add(BLANK);
        for (Cast cast : BY_SHAPE.values()) {
            items.add(cast.unfired());
            items.add(cast.fired());
        }
        return items;
    }
}
