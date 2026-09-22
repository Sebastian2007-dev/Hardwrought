package de.ipnats.hardwrought.equipment;

/**
 * Where the worn squares sit beside the inventory, and whether they are folded out.
 *
 * <p>The numbers live in common code rather than in the screen because the menu places the slots and
 * the screen draws the panel behind them, and the two have to agree to the pixel. A slot drawn
 * outside its own panel is the kind of mistake that only shows up in a screenshot.
 *
 * <p>The strap hangs off the left edge of the inventory, so its coordinates are negative: slot
 * positions in a menu are measured from the panel's own top-left corner.
 */
public final class WornStrap {
    /** How wide the panel is, including its border. */
    public static final int WIDTH = 30;
    /** How far left of the inventory panel the strap begins. */
    public static final int OFFSET_X = -WIDTH - 2;
    /** Where the first square sits, measured from the inventory panel's corner. */
    public static final int SLOT_X = OFFSET_X + 7;
    public static final int SLOT_Y = 24;
    public static final int SLOT_PITCH = 18;
    /** The fold button, above the panel and always reachable whether it is open or shut. */
    public static final int BUTTON_X = OFFSET_X + 8;
    public static final int BUTTON_Y = 4;
    public static final int BUTTON_SIZE = 14;
    /** Where the drawn panel starts and how tall it is for the squares it holds. */
    public static final int PANEL_Y = SLOT_Y - 7;

    private WornStrap() { }

    public static int slotY(int index) {
        return SLOT_Y + index * SLOT_PITCH;
    }

    public static int panelHeight(int slots) {
        return 14 + slots * SLOT_PITCH;
    }

    /**
     * Set by the client to say whether the strap is folded out. The server never changes it and never
     * reads it as anything but open, so a click it validates is never refused for a reason the server
     * could not know about.
     */
    public static boolean clientExpanded = true;
    /**
     * Set by the client while the recipe book has taken this space. Kept apart from the fold so that
     * closing the book puts the strap back exactly as the player left it.
     */
    public static boolean clientBlocked;

    /** Whether the squares are reachable on this client right now. */
    public static boolean shown() {
        return clientExpanded && !clientBlocked;
    }
}
