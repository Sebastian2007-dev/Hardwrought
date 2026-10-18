package de.ipnats.hardwrought.test;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.client.equipment.BundlePeek;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

/**
 * A bundle held open with shift: the look into it stays while the pointer moves onto it, and a click on a
 * thing in it takes that thing out onto the cursor. Played with real keys and a real pointer.
 */
public final class BundleClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var contents = new BundleContents.Mutable();
                contents.tryInsert(new ItemStack(Items.FEATHER, 2));
                contents.tryInsert(new ItemStack(Items.STICK));
                contents.tryInsert(new ItemStack(Items.DIRT, 4));
                ItemStack bundle = new ItemStack(Items.BUNDLE);
                bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
                player.getInventory().setItem(0, bundle);
            });
            context.waitTicks(10);
            context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
            context.waitTicks(5);
            double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
            int[] at = context.computeOnClient(client -> {
                var screen = (InventoryScreen) client.gui.screen();
                Slot slot = screen.getMenu().slots.stream()
                        .filter(s -> s.container == client.player.getInventory() && s.getContainerSlot() == 0).findFirst().orElseThrow();
                try {
                    var leftField = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("leftPos");
                    var topField = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("topPos");
                    leftField.setAccessible(true);
                    topField.setAccessible(true);
                    return new int[] {leftField.getInt(screen) + slot.x + 8, topField.getInt(screen) + slot.y + 8};
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            });
            context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
            context.waitTicks(3);
            context.getInput().holdShift();
            context.waitTicks(3);
            String wanted = context.computeOnClient(client -> client.player.getInventory().getItem(0)
                    .get(DataComponents.BUNDLE_CONTENTS).itemCopies().toList().get(1).getItem().toString());
            int[] cell = context.computeOnClient(client -> BundlePeek.cellCentre(1));
            // Onto the look, off the bundle: it stays open.
            context.getInput().setCursorPos(cell[0] * scale, cell[1] * scale);
            context.waitTicks(3);
            boolean open = context.computeOnClient(client -> BundlePeek.open((InventoryScreen) client.gui.screen()));
            String state = context.computeOnClient(client -> "shift=" + client.hasShiftDown() + " mouse=" + client.mouseHandler.xpos() / scale + "," + client.mouseHandler.ypos() / scale
                    + " slot=" + at[0] + "," + at[1] + " cell=" + cell[0] + "," + cell[1]);
            if (!open) throw new AssertionError("Held with shift, the bundle stays open while the pointer moves onto it: " + state);
            context.takeScreenshot("hardwrought-bundle-peek");
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(5);
            String carried = context.computeOnClient(client -> ((InventoryScreen) client.gui.screen()).getMenu().getCarried().getItem().toString());
            int left = context.computeOnClient(client -> client.player.getInventory().getItem(0).get(DataComponents.BUNDLE_CONTENTS).size());
            context.getInput().releaseShift();
            if (!carried.equals(wanted)) throw new AssertionError("The thing clicked comes out onto the cursor: " + carried + ", not " + wanted);
            if (left != 2) throw new AssertionError("and the bundle holds one thing fewer: " + left);
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }
}
