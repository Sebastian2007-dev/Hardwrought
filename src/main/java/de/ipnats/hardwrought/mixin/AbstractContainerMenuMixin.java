package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.equipment.BackpackContainer;
import de.ipnats.hardwrought.equipment.BackpackTier;
import de.ipnats.hardwrought.equipment.CarriedInventoryMenu;
import de.ipnats.hardwrought.equipment.CarriedSlot;
import de.ipnats.hardwrought.progression.RecipeSelectionMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two things every container menu has to know about, because the player grid is in all of them.
 *
 * <p>The first is old: a workbench that is not vanilla's crafting table has to be able to keep its
 * own menu open. An open menu is re-checked every tick against the block it was opened on, and the
 * crafting menu asks for {@code minecraft:crafting_table} by identity, so a hewn workbench opened
 * and shut again in the same breath. The check is widened rather than replaced — any block that
 * <em>is</em> a crafting table satisfies a menu that asked for one — and the reach test stays
 * vanilla's, so walking away still closes the grid.
 *
 * <p>The second is the pack. A player carries their own grid only because something is carrying it
 * for them, and the pack's own page sits on the same coordinates as that grid. Both live here rather
 * than in the inventory menu alone, so the pack is reachable from a chest and a workbench too: a
 * pack you can only open by closing the chest you are packing from is a pack nobody would use.
 *
 * <p>The third thing here is the consequence of the second: shift-clicking. Every vanilla menu
 * decides where a quick-moved stack goes by hard-coded slot ranges — nine to forty-five, thirty-six
 * to forty-five — written when the player's grid was the only grid there was. The pack's page is
 * outside every one of those ranges, so while it was open a shift-click had nowhere to go and did
 * nothing at all. Rather than rewrite a range per menu, the quick move is deferred to and only
 * caught when it comes back empty-handed: whatever vanilla could not place goes into the pack.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin implements CarriedInventoryMenu {
    /** The same reach vanilla checks an open menu against, so walking away still closes it. */
    private static final double VANILLA_REACH = 4.0;

    @Shadow protected abstract DataSlot addDataSlot(DataSlot dataSlot);

    @Shadow protected abstract Slot addSlot(Slot slot);

    @Shadow protected abstract boolean moveItemStackTo(ItemStack stack, int start, int end, boolean reverse);

    /** Which grid is being looked at, and how much pack there is. Both travel to the client. */
    @Unique private DataSlot hardwrought$page;
    @Unique private DataSlot hardwrought$packSlots;
    @Unique private DataSlot hardwrought$mainRowsSlot;
    /** The player whose grid this menu is showing, or null for a menu that shows none. */
    @Unique private Player hardwrought$owner;
    /** The pack page this menu draws, kept so the sync can pull the worn pack into it. */
    @Unique private BackpackContainer hardwrought$pack;
    /** The worn strap, where there is one. Only the player's own inventory has one. */
    @Unique private de.ipnats.hardwrought.equipment.EquipmentContainer hardwrought$worn;
    /** Where the pack's page starts in this menu, or -1 for a menu that has no player grid in it. */
    @Unique private int hardwrought$packStart = -1;

    @Override
    public void hardwrought$trackWorn(de.ipnats.hardwrought.equipment.EquipmentContainer worn) {
        hardwrought$worn = worn;
    }

    @Override
    public int hardwrought$page() {
        return hardwrought$page == null ? 0 : hardwrought$page.get();
    }

    @Override
    public int hardwrought$mainRows() {
        // A menu with no player grid in it has nothing to say, and reads as an ordinary open one.
        return hardwrought$mainRowsSlot == null ? BackpackTier.MAIN_ROWS : hardwrought$mainRowsSlot.get();
    }

    @Override
    public int hardwrought$packSlots() {
        // A menu with no player grid in it has nothing to say, and reads as an ordinary open one.
        return hardwrought$packSlots == null ? 0 : hardwrought$packSlots.get();
    }

    /**
     * Builds the second grid on top of the first.
     *
     * <p>Vanilla has just added the player's own twenty-seven slots and the nine of the belt. The
     * pack's page is twenty-seven more at exactly the same coordinates, and which set is open is
     * decided per frame rather than by rebuilding the menu. Swapping a pack or turning to its page
     * therefore never changes the menu's shape, never moves an index, and never needs a resync.
     */
    @Inject(method = "addStandardInventorySlots", at = @At("TAIL"))
    private void hardwrought$addPackPage(Container container, int x, int y, CallbackInfo info) {
        if (!(container instanceof Inventory inventory)) return;
        hardwrought$owner = inventory.player;
        hardwrought$page = addDataSlot(DataSlot.standalone());
        hardwrought$packSlots = addDataSlot(DataSlot.standalone());
        hardwrought$mainRowsSlot = addDataSlot(DataSlot.standalone());
        // Deliberately not asked here. The player's own menu is built inside the ServerPlayer
        // constructor, before the game mode is even assigned, and a player that is still being made
        // is in no state to be asked what it is wearing — asking anyway took the whole login down.
        // The first broadcastChanges fills this in, long before anything can look at it.
        BackpackContainer pack = inventory.player instanceof ServerPlayer serverPlayer
                ? BackpackContainer.of(serverPlayer) : BackpackContainer.clientSide();
        hardwrought$pack = pack;
        for (int row = 0; row < BackpackTier.MAIN_ROWS; row++) {
            for (int column = 0; column < BackpackTier.COLUMNS; column++) {
                int index = column + row * BackpackTier.COLUMNS;
                Slot added = addSlot(new CarriedSlot(pack, index, x + column * 18, y + row * 18,
                        () -> hardwrought$page() == 1 && index < hardwrought$packSlots()));
                if (index == 0) hardwrought$packStart = added.index;
            }
        }
    }

    /**
     * Closes the player's own grid while there is no pack to carry it in, and while the pack's page
     * is the one being looked at. The belt is never closed: hands and a belt are what a player has
     * before they have anything at all, and that is the floor the whole progression stands on.
     */
    @Redirect(method = "addInventoryExtendedSlots",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;addSlot"
                            + "(Lnet/minecraft/world/inventory/Slot;)Lnet/minecraft/world/inventory/Slot;"))
    private Slot hardwrought$gateMainGrid(AbstractContainerMenu menu, Slot slot) {
        // Only the twenty-seven; the belt is added by a method of its own and is never closed.
        if (!(slot.container instanceof Inventory)) return addSlot(slot);
        // getContainerSlot, emphatically not Slot.index. The public index field is the slot's place
        // in the *menu* and is overwritten by addSlot moments later; reading it here handed every
        // one of the twenty-seven the container slot 0, so the whole grid mirrored the first belt
        // slot and anything put there appeared twenty-seven times.
        int row = (slot.getContainerSlot() - BackpackTier.COLUMNS) / BackpackTier.COLUMNS;
        return addSlot(new CarriedSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y,
                () -> hardwrought$page() == 0 && hardwrought$mainUnlocked(row)));
    }

    /** Keeps the client's copy of what is worn in step, the same way any other menu value travels. */
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void hardwrought$syncPack(CallbackInfo info) {
        if (hardwrought$packSlots == null || !(hardwrought$owner instanceof ServerPlayer)) return;
        // The menu outlives every pack that passes through it, so the page is read fresh each sync
        // rather than copied in once.
        if (hardwrought$pack != null) hardwrought$pack.refresh();
        if (hardwrought$worn != null) hardwrought$worn.refresh();
        int slots = hardwrought$readPackSlots();
        if (hardwrought$packSlots.get() != slots) hardwrought$packSlots.set(slots);
        int rows = hardwrought$readMainRows();
        if (hardwrought$mainRowsSlot != null && hardwrought$mainRowsSlot.get() != rows) hardwrought$mainRowsSlot.set(rows);
        // A pack that has just been taken off cannot leave the player staring at its empty page.
        if (slots <= 0 && hardwrought$page.get() != 0) hardwrought$page.set(0);
    }

    @Unique
    private int hardwrought$readMainRows() {
        if (!(hardwrought$owner instanceof ServerPlayer serverPlayer) || serverPlayer.level() == null) return 0;
        var runtime = CoreLifecycle.find(serverPlayer.level().getServer());
        return runtime == null ? 0 : runtime.equipment().mainRows(serverPlayer);
    }

    @Unique
    private int hardwrought$readPackSlots() {
        if (!(hardwrought$owner instanceof ServerPlayer serverPlayer)
                || serverPlayer.level() == null) {
            return 0;
        }
        var runtime = CoreLifecycle.find(serverPlayer.level().getServer());
        if (runtime == null) return 0;
        BackpackTier tier = runtime.equipment().tier(serverPlayer);
        return tier == null ? -1 : tier.slots();
    }

    /**
     * Catches the quick move that vanilla gave up on and offers it the pack.
     *
     * <p>Deliberately second, never first. A shift-click inside a chest has to keep meaning the
     * chest, and one inside the crafting grid has to keep meaning the grid; only where the menu's
     * own answer was "nowhere" is the pack the grid it could not see — which, while the pack's page
     * is the open one, is exactly the twenty-seven squares the player is looking at.
     *
     * <p>Redirected rather than injected because {@code doClick} calls this twice, once to start and
     * once per turn of the loop that empties a stack slot by slot. Both have to go through here, or
     * a stack would move its first slotful into the pack and stop.
     */
    @Redirect(method = "doClick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack"
                            + "(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack hardwrought$quickMoveIntoThePack(AbstractContainerMenu menu, Player player, int index) {
        ItemStack moved = menu.quickMoveStack(player, index);
        return moved.isEmpty() ? hardwrought$stow(menu, player, index) : moved;
    }

    /**
     * Puts one slot's contents into the pack, following vanilla's own quick-move contract to the
     * letter: the stack as it was on success so the caller can loop, and empty for "not an inch".
     * Returning a stack nothing was done to would spin that loop for ever.
     */
    @Unique
    private ItemStack hardwrought$stow(AbstractContainerMenu menu, Player player, int index) {
        if (hardwrought$packStart < 0 || hardwrought$page() != 1 || !hardwrought$hasPackPage()) {
            return ItemStack.EMPTY;
        }
        if (index < 0 || index >= menu.slots.size()) return ItemStack.EMPTY;
        // A stack already in the pack has nowhere further in to go, and asking would move it
        // sideways between two of the pack's own squares for no reason.
        int end = hardwrought$packStart + BackpackContainer.SIZE;
        if (index >= hardwrought$packStart && index < end) return ItemStack.EMPTY;

        Slot from = menu.slots.get(index);
        ItemStack stack = from.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack before = stack.copy();
        // The closed squares of a pack smaller than three rows refuse on their own, so the whole
        // page can be offered and the tier never has to be read here.
        if (!moveItemStackTo(stack, hardwrought$packStart, end, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) from.setByPlayer(ItemStack.EMPTY, before);
        else from.setChanged();
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        from.onTake(player, stack);
        return before;
    }

    @Inject(method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;"
            + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void hardwrought$acceptOtherWorkbenches(ContainerLevelAccess access, Player player,
                                                           Block block,
                                                           CallbackInfoReturnable<Boolean> info) {
        if (block != Blocks.CRAFTING_TABLE) return;
        boolean workbench = access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock() instanceof CraftingTableBlock
                        && level.getBlockState(pos).getBlock() != Blocks.CRAFTING_TABLE
                        && player.isWithinBlockInteractionRange(pos, VANILLA_REACH), false);
        if (workbench) info.setReturnValue(true);
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void hardwrought$turnToTheOtherGrid(Player player, int button,
                                                 CallbackInfoReturnable<Boolean> info) {
        if (button != CarriedInventoryMenu.TOGGLE_PAGE_BUTTON || hardwrought$page == null) return;
        if (!hardwrought$hasPackPage()) {
            info.setReturnValue(false);
            return;
        }
        hardwrought$page.set(hardwrought$page.get() == 0 ? 1 : 0);
        info.setReturnValue(true);
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void hardwrought$selectAnotherRecipe(Player player, int button,
                                                  CallbackInfoReturnable<Boolean> info) {
        if (button != RecipeSelectionMenu.NEXT_RECIPE_BUTTON
                || !((Object) this instanceof RecipeSelectionMenu selection)
                || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        info.setReturnValue(selection.hardwrought$nextRecipe(serverPlayer));
    }
}
