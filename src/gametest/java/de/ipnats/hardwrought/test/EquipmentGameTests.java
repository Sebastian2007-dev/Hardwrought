package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.equipment.BackpackItem;
import de.ipnats.hardwrought.equipment.BackpackTier;
import de.ipnats.hardwrought.equipment.EquipmentContainer;
import de.ipnats.hardwrought.equipment.EquipmentSystem;
import de.ipnats.hardwrought.progression.BenchTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
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
    public void theHewnBenchOnlyDoesEarlyWork(GameTestHelper helper) {
        // The bench cut out of a standing log is the first one in the game, and the list of what it
        // can make is deliberately a list of what it CAN: anything it has not heard of is refused.
        for (ItemStack early : List.of(new ItemStack(Items.STICK), new ItemStack(Items.CRAFTING_TABLE),
                new ItemStack(Items.CHEST), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(ModItems.IRON_HATCHET), new ItemStack(ModItems.BASIC_BACKPACK))) {
            helper.assertTrue(BenchTier.allows(true, early),
                    "The hewn bench does early work: " + early.getHoverName().getString());
        }
        for (ItemStack later : List.of(new ItemStack(Items.DIAMOND_PICKAXE),
                new ItemStack(Items.PISTON), new ItemStack(Items.ANVIL),
                new ItemStack(Items.ENCHANTING_TABLE), new ItemStack(Items.HOPPER))) {
            helper.assertFalse(BenchTier.allows(true, later),
                    "and refuses what wants a joined bench: " + later.getHoverName().getString());
        }

        // A crafting table that was itself joined is not limited by any of this.
        helper.assertTrue(BenchTier.allows(false, new ItemStack(Items.DIAMOND_PICKAXE))
                        && BenchTier.allows(false, new ItemStack(Items.PISTON)),
                "A joined crafting table makes anything a recipe allows");
        helper.assertFalse(BenchTier.allows(true, ItemStack.EMPTY),
                "and nothing at all is never a product");

        // The chain has to stay walkable: without this entry the first bench is also the last one.
        helper.assertTrue(BenchTier.allows(true, new ItemStack(Items.CRAFTING_TABLE)),
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

    private static ServerPlayer survivor(GameTestHelper helper) {
        return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
    }
}
