package de.ipnats.hardwrought.client.storage;

import de.ipnats.hardwrought.core.networking.StoragePayloads;
import de.ipnats.hardwrought.storage.StorageDeskMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

/** The client half of the store room: the desk has a screen, and the screen is sent a list. */
public final class StorageClient {
    /** What the server last said the open desk reaches, or null before the first list is in. */
    private static StoragePayloads.Contents contents;

    private StorageClient() { }

    public static void initialize() {
        MenuScreens.register(StorageDeskMenu.TYPE, StorageDeskScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(StoragePayloads.Contents.TYPE, (payload, context) ->
                context.client().execute(() -> contents = payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> contents = null);
    }

    /** The list for this menu, or null where the one in hand belongs to a desk since closed. */
    public static StoragePayloads.Contents contents(int containerId) {
        return contents != null && contents.containerId() == containerId ? contents : null;
    }
}
