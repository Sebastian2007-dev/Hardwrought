package de.ipnats.hardwrought.client.survival;

import de.ipnats.hardwrought.client.knowledge.CompendiumClient;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.network.chat.Component;

/**
 * Lying awake, or asleep: there is nothing to do but wait, so the compendium can be read meanwhile. The
 * bed screen gets a button that opens it, above the one that leaves the bed.
 *
 * <p>Opening it does not get the sleeper up: only closing the bed screen itself wakes them, and opening
 * another screen over it only takes it away. Closing the compendium leaves no screen open, and vanilla
 * puts the bed screen back for anyone still asleep, so the night goes on where it was.
 */
public final class SleepScreen {
    private SleepScreen() { }

    public static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof InBedChatScreen)) return;
            // The leave-bed button is 200 wide at height - 40; this one sits right above it.
            Screens.getWidgets(screen).add(Button.builder(Component.translatable("gui.hardwrought.sleep.compendium"),
                    button -> CompendiumClient.openHome()).bounds(width / 2 - 100, height - 64, 200, 20).build());
        });
    }
}
