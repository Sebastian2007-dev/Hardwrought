package de.ipnats.hardwrought.equipment;

/**
 * The part of every container menu that knows which grid the player is currently looking at.
 *
 * <p>Implemented by a mixin on {@code AbstractContainerMenu}, so it is true of the inventory, a
 * chest, a workbench and any menu a later milestone or another mod adds. The pack is reachable
 * wherever the player's own grid is, because a pack you can only open by closing the chest you are
 * packing from is a pack nobody would use.
 */
public interface CarriedInventoryMenu {
    /** The menu button that swaps between the player's own grid and the pack's page. */
    int TOGGLE_PAGE_BUTTON = 4240;

    /**
     * Hands the menu the worn strap so its own sync can keep it fresh. Only the player's inventory
     * has one; every other menu leaves this alone.
     */
    default void hardwrought$trackWorn(EquipmentContainer worn) { }

    /** 0 while the player's own grid is shown, 1 while the pack's page is. */
    int hardwrought$page();

    /**
     * How many slots the worn pack has, or -1 for no pack at all — which is also what closes the
     * main grid. Synchronised, so the client can draw the right rows without being told the tier.
     */
    int hardwrought$packSlots();

    /** True where there is a pack page to switch to at all. */
    default boolean hardwrought$hasPackPage() {
        return hardwrought$packSlots() > 0;
    }

    /** True where the player's own grid is unlocked, which is to say: they are wearing something. */
    default boolean hardwrought$mainUnlocked() {
        return hardwrought$packSlots() >= 0;
    }
}
