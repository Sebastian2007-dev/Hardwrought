package de.ipnats.hardwrought.client.magic;

import de.ipnats.hardwrought.magic.MagicTableBlock;
import de.ipnats.hardwrought.magic.WandItem;
import net.minecraft.client.Minecraft;

/** The player's side of magic: the drawing surface a wand opens, and action casting. */
public final class MagicClient {
    private MagicClient() { }

    public static void initialize() {
        WandItem.openCanvas = hand -> {
            Minecraft client = Minecraft.getInstance();
            if (client.gui.screen() == null && !ActionCasting.capturing()) client.gui.setScreen(new RuneCanvasScreen());
        };
        MagicTableBlock.openTable = hand -> {
            Minecraft client = Minecraft.getInstance();
            if (client.gui.screen() == null && !ActionCasting.capturing()) client.gui.setScreen(new MagicTableScreen());
        };
        // A far gate opened by a cast asks where it should lead.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                de.ipnats.hardwrought.magic.GatePayload.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(
                        new GateScreen(payload.range(), payload.seconds(), payload.anchored())));
        ActionCasting.initialize();
        ShieldSpheres.initialize();
        ClientSigils.initialize();
    }
}
