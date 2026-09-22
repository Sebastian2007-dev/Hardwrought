package de.ipnats.hardwrought.equipment;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How much a pack lets a player carry, and in how many places.
 *
 * <p>A player with nothing on their back has their hands and their belt, and that is all: the nine
 * hotbar slots. Everything above that is the pack's doing, which is what makes the first one worth
 * making and the next one worth working towards.
 *
 * <p>Rows are pages of their own rather than extra room in the main inventory. The main grid is what
 * the player carries on their person; a pack is a second grid the same size that the inventory
 * switches to, and a later pack fills it from the top down. That way every new tier is the same
 * gesture — one more row appears — instead of a differently shaped screen each time.
 */
public enum BackpackTier implements StringRepresentable {
    /**
     * Woven from leaf fibre in the hands, with nothing else. It unlocks the ordinary inventory and
     * the carry allowance everything else in the mod was balanced against, and holds nothing of its
     * own. Section 71.1.8 in spirit: the first link of the chain has to be reachable from nothing.
     */
    STARTER("starter", 0, 85.0),
    /**
     * The first real pack. A little more on the back, and a row of its own to put it in.
     */
    BASIC("basic", 1, 110.0);

    /** Rows the main inventory has while a pack is worn. Nothing to do with the pack's own rows. */
    public static final int MAIN_ROWS = 3;
    public static final int COLUMNS = 9;
    /** What a player carries with no pack at all: their hands and their belt. */
    public static final double BARE_CAPACITY_KG = 30.0;

    private final String serializedName;
    private final int rows;
    private final double capacityKg;

    BackpackTier(String serializedName, int rows, double capacityKg) {
        this.serializedName = serializedName;
        this.rows = rows;
        this.capacityKg = capacityKg;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** How many rows of nine this pack carries of its own. */
    public int rows() {
        return rows;
    }

    /** How many slots that comes to. */
    public int slots() {
        return rows * COLUMNS;
    }

    /** What a player wearing this may carry before the weight starts to tell. */
    public double capacityKg() {
        return capacityKg;
    }

    /** True where this pack has a page of its own to switch to. */
    public boolean hasPages() {
        return rows > 0;
    }

    public static BackpackTier byName(String name) {
        if (name == null) return null;
        for (BackpackTier tier : values()) {
            if (tier.serializedName.equals(name.toLowerCase(Locale.ROOT))) return tier;
        }
        return null;
    }
}
