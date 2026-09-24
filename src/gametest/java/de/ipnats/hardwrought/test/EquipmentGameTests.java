package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.equipment.BackpackItem;
import de.ipnats.hardwrought.equipment.BackpackTier;
import de.ipnats.hardwrought.equipment.CarriedInventoryMenu;
import de.ipnats.hardwrought.equipment.EquipmentContainer;
import de.ipnats.hardwrought.equipment.EquipmentSystem;
import de.ipnats.hardwrought.progression.BenchTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

/** What a player wears, and what the first bench is good enough to make. */
public final class EquipmentGameTests {

    @GameTest
    public void aPackIsNotInTheInventoryAndSoCannotBeDropped(GameTestHelper helper) {
        EquipmentSystem equipment = CoreLifecycle.require(helper.getLevel().getServer()).equipment();
        ServerPlayer player = survivor(helper);
        equipment.clear(player.getUUID());

        equipment.setBackpack(player, new ItemStack(ModItems.BASIC_BACKPACK));
        helper.assertTrue(player.getInventory().contains(
                        stack -> BackpackItem.isBackpack(stack)) == false,
                "A worn pack is nowhere in the inventory, which is the whole reason it survives");

        // Death empties the inventory. It cannot empty something the inventory never held.
        player.getInventory().clearContent();
        helper.assertTrue(equipment.tier(player) == BackpackTier.BASIC,
                "and it is still on the player's back afterwards");
        equipment.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void aPlayerWithoutOneIsGivenThePlainestPackBack(GameTestHelper helper) {
        EquipmentSystem equipment = CoreLifecycle.require(helper.getLevel().getServer()).equipment();
        ServerPlayer player = survivor(helper);
        equipment.clear(player.getUUID());

        helper.assertTrue(equipment.tier(player) == null, "A player may be without a pack");
        helper.assertTrue(equipment.mainRows(player) == 0,
                "and then has nothing but their belt");
        helper.assertTrue(equipment.capacityKg(player) == BackpackTier.BARE_CAPACITY_KG,
                "and carries only what fits on it");

        helper.assertTrue(equipment.ensureBackpack(player), "Being without one is put right");
        helper.assertTrue(equipment.tier(player) == BackpackTier.STARTER,
                "with the plainest pack there is");
        helper.assertTrue(equipment.mainRows(player) == BackpackTier.MAIN_ROWS,
                "which is what opens the ordinary inventory");

        // Never an upgrade, and never a replacement for something better.
        equipment.setBackpack(player, new ItemStack(ModItems.BASIC_BACKPACK));
        helper.assertFalse(equipment.ensureBackpack(player),
                "A player already wearing a better pack is left alone");
        helper.assertTrue(equipment.tier(player) == BackpackTier.BASIC, "and keeps it");
        equipment.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void eachTierCarriesMoreAndOpensOneMoreRow(GameTestHelper helper) {
        helper.assertTrue(BackpackTier.STARTER.capacityKg() > BackpackTier.BARE_CAPACITY_KG
                        && BackpackTier.BASIC.capacityKg() > BackpackTier.STARTER.capacityKg(),
                "Every step up the chain carries more than the one below it");
        helper.assertTrue(BackpackTier.STARTER.slots() == 0 && !BackpackTier.STARTER.hasPages(),
                "The woven pack unlocks the inventory and holds nothing of its own");
        helper.assertTrue(BackpackTier.BASIC.slots() == BackpackTier.COLUMNS
                        && BackpackTier.BASIC.hasPages(),
                "and the next one brings exactly one row with it");
        helper.assertTrue(BackpackTier.BASIC.slots() <= EquipmentContainer.SIZE * 100
                        && BackpackTier.BASIC.slots()
                                <= BackpackTier.MAIN_ROWS * BackpackTier.COLUMNS,
                "No pack may have more rows than the grid it is drawn in");
        helper.succeed();
    }

    @GameTest
    public void whatIsInAPackRidesOnThePack(GameTestHelper helper) {
        ItemStack pack = new ItemStack(ModItems.BASIC_BACKPACK);
        helper.assertTrue(BackpackItem.contentsOf(pack).size() == BackpackTier.BASIC.slots(),
                "A pack reads as exactly as many slots as its tier has");

        NonNullList<ItemStack> items = BackpackItem.contentsOf(pack);
        items.set(0, new ItemStack(Items.IRON_INGOT, 5));
        BackpackItem.setContents(pack, items);

        ItemStack copy = pack.copy();
        helper.assertTrue(BackpackItem.contentsOf(copy).get(0).is(Items.IRON_INGOT)
                        && BackpackItem.contentsOf(copy).get(0).getCount() == 5,
                "and carries what is in it wherever the stack goes");

        // A woven pack has no room at all, and saying so is a bounded answer rather than a crash.
        ItemStack woven = new ItemStack(ModItems.STARTER_BACKPACK);
        helper.assertTrue(BackpackItem.contentsOf(woven).isEmpty(), "The woven pack holds nothing");
        helper.assertTrue(BackpackItem.tierOf(new ItemStack(Items.STONE)) == null
                        && !BackpackItem.isBackpack(ItemStack.EMPTY),
                "and something that is not a pack is not mistaken for one");
        helper.succeed();
    }

    @GameTest
    public void onlyWornThingsGoInWornSlots(GameTestHelper helper) {
        helper.assertTrue(EquipmentContainer.fits(EquipmentContainer.BACKPACK_SLOT,
                        new ItemStack(ModItems.BASIC_BACKPACK)), "A pack goes on the back");
        helper.assertFalse(EquipmentContainer.fits(EquipmentContainer.BACKPACK_SLOT,
                        new ItemStack(ModItems.SAFETY_LAMP)), "and a lamp does not");
        helper.assertTrue(EquipmentContainer.fits(EquipmentContainer.LAMP_SLOT,
                        new ItemStack(ModItems.SAFETY_LAMP)), "The lamp goes on the belt");
        helper.assertFalse(EquipmentContainer.fits(EquipmentContainer.LAMP_SLOT,
                        new ItemStack(Items.IRON_INGOT)), "and an ingot goes in neither");
        helper.assertTrue(EquipmentContainer.fits(EquipmentContainer.BACKPACK_SLOT, ItemStack.EMPTY),
                "Emptying a worn slot is always allowed; being without a pack is a step, not a trap");
        helper.succeed();
    }

    @GameTest
    public void eachBenchMakesWhatItIsGoodEnoughToMake(GameTestHelper helper) {
        // The tags say the MINIMUM bench, not the permission, so each result is listed once and a
        // bench makes anything at or below itself. Anything no tag has heard of wants the best one.
        for (ItemStack bare : List.of(new ItemStack(Items.STICK), new ItemStack(Items.TORCH),
                new ItemStack(ModItems.LEAF_STRING), new ItemStack(ModItems.FLINT_HATCHET),
                new ItemStack(ModItems.STARTER_BACKPACK))) {
            helper.assertTrue(BenchTier.required(bare) == BenchTier.INVENTORY,
                    "A player's own hands are the bottom rung: " + bare.getHoverName().getString());
        }
        // Without this the ladder has no first step: hewing a bench needs a tool, and the only tool
        // available before there is a bench is one made in the grid the player carries.
        helper.assertTrue(BenchTier.allows(BenchTier.INVENTORY, new ItemStack(ModItems.FLINT_HATCHET)),
                "Section 71: the hatchet that cuts the first bench has to come before the first bench");

        for (ItemStack hewn : List.of(new ItemStack(Items.CHEST), new ItemStack(Items.CAMPFIRE),
                new ItemStack(Items.BOW), new ItemStack(ModItems.BASIC_BACKPACK),
                new ItemStack(ModItems.STONE_PICKAXE))) {
            helper.assertTrue(BenchTier.required(hewn) == BenchTier.HEWN,
                    "Wood, stone, flint and fibre want the hewn bench: "
                            + hewn.getHoverName().getString());
            helper.assertFalse(BenchTier.allows(BenchTier.INVENTORY, hewn),
                    "and not the grid in a player's hands");
        }

        for (ItemStack joined : List.of(new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(ModItems.IRON_HATCHET), new ItemStack(ModItems.BRONZE_INGOT),
                new ItemStack(Items.BUCKET), new ItemStack(Items.SHIELD),
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.PISTON))) {
            helper.assertTrue(BenchTier.required(joined) == BenchTier.JOINED,
                    "Everything metal, and everything unlisted, wants a bench that was built: "
                            + joined.getHoverName().getString());
            helper.assertFalse(BenchTier.allows(BenchTier.HEWN, joined),
                    "and the stump with a flat top does not make it");
        }

        // A rung makes everything below it as well as its own, which is what makes it a ladder.
        helper.assertTrue(BenchTier.allows(BenchTier.HEWN, new ItemStack(Items.STICK))
                        && BenchTier.allows(BenchTier.JOINED, new ItemStack(Items.CHEST)),
                "A better bench never makes less than a worse one");
        helper.assertTrue(BenchTier.required(ItemStack.EMPTY) == BenchTier.HIGHEST
                        && !BenchTier.allows(BenchTier.JOINED, ItemStack.EMPTY),
                "and nothing at all is never a product");

        // The chain has to stay walkable: without this entry the first bench is also the last one.
        helper.assertTrue(BenchTier.allows(BenchTier.HEWN, new ItemStack(Items.CRAFTING_TABLE)),
                "Section 71.1.8: the hewn bench must be able to make the bench that replaces it");
        helper.succeed();
    }

    @GameTest
    public void everyInventorySquareStillPointsAtItsOwnSlot(GameTestHelper helper) {
        // The gated main grid is rebuilt slot by slot, and a slot carries two different numbers: the
        // container slot it shows, and its place in the menu. Reading the wrong one pointed all
        // twenty-seven squares at container slot 0, so whatever was in the first belt slot appeared
        // twenty-seven times over and could be taken twenty-seven times. Nothing else caught it.
        ServerPlayer player = survivor(helper);
        var menu = player.inventoryMenu;

        java.util.List<Integer> shown = new java.util.ArrayList<>();
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) shown.add(slot.getContainerSlot());
        }
        helper.assertTrue(new java.util.HashSet<>(shown).size() == shown.size(),
                "No two squares may show the same slot: " + shown);

        // The carried grid itself is the part the bug broke: the belt and the twenty-seven, each
        // shown once and only once. Armour and the offhand share the container and are left alone.
        int carried = BackpackTier.MAIN_ROWS * BackpackTier.COLUMNS + BackpackTier.COLUMNS;
        for (int slot = 0; slot < carried; slot++) {
            int index = slot;
            helper.assertTrue(shown.stream().filter(seen -> seen == index).count() == 1,
                    "Slot " + slot + " has exactly one square of its own, found "
                            + shown.stream().filter(seen -> seen == index).count());
        }

        // The same for the pack page, which shares its coordinates with the grid above.
        java.util.List<Integer> pack = new java.util.ArrayList<>();
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot instanceof de.ipnats.hardwrought.equipment.CarriedSlot
                    && slot.container != player.getInventory()) {
                pack.add(slot.getContainerSlot());
            }
        }
        helper.assertTrue(new java.util.HashSet<>(pack).size() == pack.size(),
                "and no two squares of the pack page either: " + pack);
        helper.succeed();
    }

    @GameTest
    public void shiftClickingReachesThePackPage(GameTestHelper helper) {
        // Every vanilla menu quick-moves by hard-coded slot ranges written when the player's grid was
        // the only grid. The pack's page lies outside all of them, so a shift-click at it did nothing
        // whatsoever — the one gesture that makes a second grid worth having.
        EquipmentSystem equipment = CoreLifecycle.require(helper.getLevel().getServer()).equipment();
        ServerPlayer player = survivor(helper);
        equipment.clear(player.getUUID());
        equipment.setBackpack(player, new ItemStack(ModItems.BASIC_BACKPACK));

        var menu = player.inventoryMenu;
        var carried = (CarriedInventoryMenu) menu;
        menu.broadcastChanges();
        helper.assertTrue(carried.hardwrought$packSlots() == BackpackTier.BASIC.slots(),
                "The pack is on, and the menu knows how much of it there is");
        helper.assertTrue(menu.clickMenuButton(player, CarriedInventoryMenu.TOGGLE_PAGE_BUTTON),
                "and its page can be turned to");
        helper.assertTrue(carried.hardwrought$page() == 1, "which is the page now being looked at");

        int belt = hardwroughtBeltSlot(menu, player);
        menu.getSlot(belt).set(new ItemStack(Items.IRON_INGOT, 5));
        menu.clicked(belt, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(!menu.getSlot(belt).hasItem(), "A shift-click empties the square it came from");
        ItemStack stowed = BackpackItem.contentsOf(equipment.backpack(player)).get(0);
        helper.assertTrue(stowed.is(Items.IRON_INGOT) && stowed.getCount() == 5,
                "and the whole stack is in the pack, not lost: " + stowed);

        // Out again is vanilla's own business, and has to keep working.
        int pack = hardwroughtPackSlot(menu, player);
        menu.clicked(pack, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 5,
                "and a shift-click back takes it out again");

        // On the player's own page the pack is shut, and a shift-click must not reach through it.
        helper.assertTrue(menu.clickMenuButton(player, CarriedInventoryMenu.TOGGLE_PAGE_BUTTON),
                "Turning back to the player's own grid");
        menu.getSlot(belt).set(new ItemStack(Items.COAL, 3));
        menu.clicked(belt, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(BackpackItem.contentsOf(equipment.backpack(player)).stream()
                        .noneMatch(stack -> stack.is(Items.COAL)),
                "puts nothing into a pack page the player cannot see");

        equipment.clear(player.getUUID());
        helper.succeed();
    }

    /** The first belt square: the one grid a player has whether they are wearing anything or not. */
    private static int hardwroughtBeltSlot(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                           ServerPlayer player) {
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == 0) return slot.index;
        }
        throw new AssertionError("No belt square in the player's own menu");
    }

    /** The first square of the pack's page. */
    private static int hardwroughtPackSlot(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                           ServerPlayer player) {
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot instanceof de.ipnats.hardwrought.equipment.CarriedSlot
                    && slot.container != player.getInventory() && slot.getContainerSlot() == 0) {
                return slot.index;
            }
        }
        throw new AssertionError("No pack page in the player's own menu");
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
    }
}
