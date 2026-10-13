package de.ipnats.hardwrought.test;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.storage.StorageDeskScreen;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.electricity.DynamoBlock;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.storage.StorageBlocks;
import de.ipnats.hardwrought.storage.StorageDeskMenu;
import de.ipnats.hardwrought.storage.StoragePortBlock;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * The storage desk worked with a real mouse. What a click on its list does is decided on the
 * client — which button, with shift or without — and a server test that calls the menu directly
 * passes whatever the screen makes of the mouse.
 */
public final class StorageClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            // As it is played: in survival, where the pack and the belt decide what the hands hold.
            world.getServer().runCommand("gamemode survival @a");
            BlockPos[] at = new BlockPos[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                player.getInventory().clearContent();
                BlockPos desk = player.blockPosition().north(2).above();
                level.setBlockAndUpdate(desk, StorageBlocks.DESK.defaultBlockState());
                level.setBlockAndUpdate(desk.east(), StorageBlocks.PORT.defaultBlockState()
                        .setValue(StoragePortBlock.FACING, Direction.EAST));
                level.setBlockAndUpdate(desk.east(2), Blocks.CHEST.defaultBlockState());
                var chest = (ChestBlockEntity) level.getBlockEntity(desk.east(2));
                chest.setItem(0, new ItemStack(Items.COAL, 64));
                chest.setItem(1, new ItemStack(Items.COAL, 36));
                // Current for it: a crank box turning a dynamo, and a wire from that to the desk.
                BlockPos box = desk.above(3);
                level.setBlockAndUpdate(box.east(), ElectricBlocks.DYNAMO.defaultBlockState()
                        .setValue(DynamoBlock.FACING, Direction.EAST));
                level.setBlockAndUpdate(box, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
                Electricity.connect(level, box.east(), desk, Gauge.WIRE);
                Electricity.update(level, desk);
                at[0] = desk;
            });
            context.waitTicks(20);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                player.openMenu(new SimpleMenuProvider(
                        (id, inventory, who) -> new StorageDeskMenu(id, inventory, level, at[0]),
                        Component.translatable("block.hardwrought.storage_desk")));
            });
            context.waitForScreen(StorageDeskScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("hardwrought-storage-desk");

            // The first cell of the list, where the hundred coal stand.
            point(context, StorageDeskMenu.GRID_X + 8, StorageDeskMenu.GRID_Y + 8);
            // A real click: the button stays down for a few ticks, long enough for the stack to be
            // on the cursor when it comes up again.
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(4);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(10);
            expect(carried(world), 64, "A left click fetches a whole stack out of a hundred");

            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(4);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(10);
            expect(carried(world), 0, "A left click with a stack on the cursor puts the whole stack away");

            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(10);
            expect(carried(world), 32, "A right click fetches half a stack");
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(10);
            expect(carried(world), 31, "A right click with a stack on the cursor sets one down");
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(10);
            expect(carried(world), 0, "and a left click the rest");

            // Shift and the wheel over the list: one item a notch, out of the room and back into it.
            context.getInput().holdShift();
            context.getInput().scroll(-1.0);
            context.getInput().scroll(-1.0);
            context.waitTicks(10);
            expect(owned(world), 2, "Shift and two notches down fetch two into the inventory");
            context.getInput().scroll(1.0);
            context.waitTicks(10);
            expect(owned(world), 1, "and a notch up puts one of them back");
            context.getInput().releaseShift();
            context.takeScreenshot("hardwrought-storage-desk-used");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }

    /** Moves the pointer to a place on the open desk, given as the screen's own coordinates. */
    private static void point(ClientGameTestContext context, int x, int y) {
        double[] window = context.computeOnClient(client -> {
            var screen = client.gui.screen();
            double left = (screen.width - 176) / 2.0, top = (screen.height - 222) / 2.0;
            double scale = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
            return new double[] {(left + x) * scale, (top + y) * scale};
        });
        context.getInput().setCursorPos(window[0], window[1]);
        context.waitTicks(2);
    }

    private static int carried(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().containerMenu.getCarried().getCount());
    }

    private static int owned(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.COAL));
    }

    private static void expect(int found, int wanted, String what) {
        Hardwrought.LOGGER.info("[storage desk] {}: {} (wanted {})", what, found, wanted);
        if (found != wanted) throw new AssertionError(what + ": " + found + " instead of " + wanted);
    }
}
