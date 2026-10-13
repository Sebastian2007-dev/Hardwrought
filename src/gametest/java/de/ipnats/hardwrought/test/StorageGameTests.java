package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.electricity.Machine;
import de.ipnats.hardwrought.smithing.MetalStock;
import de.ipnats.hardwrought.storage.StorageBlocks;
import de.ipnats.hardwrought.storage.StorageDeskMenu;
import de.ipnats.hardwrought.storage.StorageNetwork;
import de.ipnats.hardwrought.storage.StoragePortBlock;
import de.ipnats.hardwrought.core.networking.StoragePayloads;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** The store room: chests strung together with tubes and worked from one desk. */
public final class StorageGameTests {
    /**
     * A desk, three tubes east of it, and a port on the first and the last of them with a chest
     * beyond each. Returns the two chests, the nearer first.
     */
    private static ChestBlockEntity[] room(ServerLevel level, BlockPos desk) {
        level.setBlockAndUpdate(desk, StorageBlocks.DESK.defaultBlockState());
        for (int step = 1; step <= 3; step++) {
            level.setBlockAndUpdate(desk.east(step), StorageBlocks.TUBE.defaultBlockState());
        }
        ChestBlockEntity[] chests = new ChestBlockEntity[2];
        int[] along = {1, 3};
        for (int index = 0; index < 2; index++) {
            BlockPos port = desk.east(along[index]).south();
            level.setBlockAndUpdate(port, StorageBlocks.PORT.defaultBlockState()
                    .setValue(StoragePortBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(port.south(), Blocks.CHEST.defaultBlockState());
            chests[index] = (ChestBlockEntity) level.getBlockEntity(port.south());
        }
        return chests;
    }

    /** Current for a desk: a crank box turning a dynamo above it, and a wire from that to the desk. */
    private static void power(ServerLevel level, BlockPos desk) {
        BlockPos box = desk.above(3);
        level.setBlockAndUpdate(box.east(), de.ipnats.hardwrought.electricity.ElectricBlocks.DYNAMO.defaultBlockState()
                .setValue(de.ipnats.hardwrought.electricity.DynamoBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(box, de.ipnats.hardwrought.core.registry.ModBlocks.CRANK_BOX.defaultBlockState()
                .setValue(de.ipnats.hardwrought.machinery.CrankBoxBlock.TURNING, true));
        de.ipnats.hardwrought.electricity.Electricity.connect(level, box.east(), desk,
                de.ipnats.hardwrought.electricity.Gauge.WIRE);
        de.ipnats.hardwrought.electricity.Electricity.update(level, desk);
    }

    private static void unpower(ServerLevel level, BlockPos desk) {
        level.setBlockAndUpdate(desk.above(3), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(desk.above(3).east(), Blocks.AIR.defaultBlockState());
    }

    @GameTest
    public void aDeskReachesEveryChestOnItsTubes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos desk = helper.absolutePos(new BlockPos(1, 1, 1));
        ChestBlockEntity[] chests = room(level, desk);
        chests[0].setItem(0, new ItemStack(Items.IRON_INGOT, 40));
        chests[1].setItem(3, new ItemStack(Items.IRON_INGOT, 30));
        chests[1].setItem(4, new ItemStack(Items.COAL, 5));
        // A chest the tubes merely run past, with no port against it, is no part of the room.
        level.setBlockAndUpdate(desk.east(2).north(), Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(desk.east(2).north())).setItem(0, new ItemStack(Items.DIAMOND));

        StorageNetwork network = StorageNetwork.find(level, desk);
        var held = network.contents();
        helper.assertTrue(network.stores() == 2, "Two ports, two chests: " + network.stores());
        helper.assertTrue(held.size() == 2 && held.get(ItemVariant.of(Items.IRON_INGOT)) == 70
                        && held.get(ItemVariant.of(Items.COAL)) == 5,
                "Like is counted with like across the chests, and the unported chest is left out: " + held);

        ItemStack fetched = network.extract(new ItemStack(Items.IRON_INGOT), 64);
        helper.assertTrue(fetched.is(Items.IRON_INGOT) && fetched.getCount() == 64, "A stack is fetched out of two chests");
        helper.assertTrue(chests[0].getItem(0).isEmpty() && chests[1].getItem(3).getCount() == 6,
                "the nearer chest first, the rest left where it lay");

        ItemStack left = network.insert(new ItemStack(Items.IRON_INGOT, 10));
        helper.assertTrue(left.isEmpty() && chests[1].getItem(3).getCount() == 16 && chests[0].getItem(0).isEmpty(),
                "What is put away goes onto the heap of its kind, not into the first empty room");

        level.setBlockAndUpdate(desk.east(2), Blocks.AIR.defaultBlockState());
        helper.assertTrue(StorageNetwork.find(level, desk).stores() == 1, "A tube taken out cuts the far chest off");
        helper.succeed();
    }

    @GameTest
    public void anOpenDeskFetchesAndPutsAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos desk = helper.absolutePos(new BlockPos(1, 1, 1));
        ChestBlockEntity[] chests = room(level, desk);
        chests[0].setItem(0, new ItemStack(Items.COAL, 20));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(desk.getX() + 0.5, desk.getY() + 1.0, desk.getZ() + 0.5);
        player.getInventory().clearContent();
        StorageDeskMenu menu = new StorageDeskMenu(1, player.getInventory(), level, desk);

        var works = (de.ipnats.hardwrought.storage.StorageDeskBlockEntity) level.getBlockEntity(desk);
        menu.click(new ItemStack(Items.COAL), StoragePayloads.TAKE);
        helper.assertTrue(!works.powered() && menu.getCarried().isEmpty(), "A desk without current fetches nothing");
        power(level, desk);
        helper.assertTrue(works.powered() && works.amps() > 0.9f,
                "On a line it takes its ampere and works: " + works.volts() + " V, " + works.amps() + " A");

        menu.click(new ItemStack(Items.COAL), StoragePayloads.TAKE);
        helper.assertTrue(menu.getCarried().getCount() == 20, "A left click fetches what there is, up to a stack");
        menu.click(ItemStack.EMPTY, StoragePayloads.TAKE);
        helper.assertTrue(menu.getCarried().isEmpty() && chests[0].getItem(0).getCount() == 20,
                "and a left click with it on the cursor puts all of it away");

        menu.click(new ItemStack(Items.COAL), StoragePayloads.HALF);
        helper.assertTrue(menu.getCarried().is(Items.COAL) && menu.getCarried().getCount() == 10,
                "A right click fetches half of what there is: " + menu.getCarried());
        menu.click(ItemStack.EMPTY, StoragePayloads.TAKE);
        helper.assertTrue(menu.getCarried().isEmpty() && chests[0].getItem(0).getCount() == 20,
                "A click with it on the cursor puts it away again");

        menu.click(new ItemStack(Items.COAL), StoragePayloads.QUICK);
        helper.assertTrue(player.getInventory().countItem(Items.COAL) == 20 && chests[0].getItem(0).isEmpty(),
                "A shift-click fetches straight into the inventory");
        int slot = -1;
        for (int index = 0; index < menu.slots.size() && slot < 0; index++) {
            if (menu.slots.get(index).getItem().is(Items.COAL)) slot = index;
        }
        menu.quickMoveStack(player, slot);
        helper.assertTrue(player.getInventory().countItem(Items.COAL) == 0 && chests[0].getItem(0).getCount() == 20,
                "and a shift-click in the inventory puts the stack away");

        menu.click(new ItemStack(Items.COAL), StoragePayloads.FETCH_ONE);
        menu.click(new ItemStack(Items.COAL), StoragePayloads.FETCH_ONE);
        menu.click(new ItemStack(Items.COAL), StoragePayloads.PUT_ONE);
        helper.assertTrue(player.getInventory().countItem(Items.COAL) == 1 && chests[0].getItem(0).getCount() == 19,
                "A notch of the wheel moves one: two out and one back leave one in the hand");

        player.setPos(desk.getX() + 40.5, desk.getY() + 1.0, desk.getZ() + 0.5);
        menu.click(new ItemStack(Items.COAL), StoragePayloads.TAKE);
        helper.assertTrue(menu.getCarried().isEmpty(), "Nobody works a desk from forty blocks off");
        player.getInventory().clearContent();
        unpower(level, desk);
        helper.succeed();
    }

    @GameTest
    public void theLatheAndTheTubeMillMakeTheParts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(Machine.LATHE.result(level, new ItemStack(MetalStock.rod("steel"))).is(StorageBlocks.FINE_GEAR),
                "The lathe turns a steel rod into a fine gear");
        helper.assertTrue(Machine.LATHE.result(level, new ItemStack(MetalStock.rod("iron"))).isEmpty(),
                "and makes nothing of iron");
        helper.assertTrue(Machine.TUBE_MILL.result(level, new ItemStack(MetalStock.plate("bronze"))).is(StorageBlocks.TUBE_SECTION),
                "The tube mill rolls a bronze plate into a length of tube");
        for (String recipe : new String[] {"lathe", "tube_mill", "sorting_mechanism", "storage_tube", "storage_port", "storage_desk"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                    de.ipnats.hardwrought.Hardwrought.id(recipe));
            helper.assertTrue(level.recipeAccess().byKey(key).isPresent(), "There is a recipe for the " + recipe);
        }
        helper.succeed();
    }
}
