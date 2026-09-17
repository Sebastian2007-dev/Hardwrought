package de.ipnats.hardwrought.client.debug;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.DebugSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;

import java.util.List;

/** A display-only snapshot: the client never calculates or changes simulation state. */
public final class DebugHud {
    private static List<String> lines = List.of();

    private DebugHud() { }

    public static List<String> snapshot() {
        return lines;
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(DebugSnapshotPayload.TYPE, (payload, context) -> lines = payload.lines());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> lines = List.of());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> lines = List.of());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("debug_hud"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || lines.isEmpty()) return;
            int y = 8;
            for (String line : lines) {
                if (y + 12 > graphics.guiHeight()) break;
                String visible = client.font.plainSubstrByWidth(line, Math.max(0, graphics.guiWidth() - 20));
                graphics.fill(6, y - 1, 12 + client.font.width(visible), y + 10, 0xB0000000);
                graphics.text(client.font, visible, 8, y, 0xFFFFFFFF, false);
                y += 12;
            }
        });
    }
}
