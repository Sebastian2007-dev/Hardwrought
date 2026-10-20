package de.ipnats.hardwrought.client.party;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.PartySnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * The rest of the party, at the left edge of the screen, a third of the way down: below the vitals
 * panel and above the chat. One line each — name, a health bar, how far — so a glance says who is
 * hurt and where everyone is without opening anything. Hidden with the rest of the HUD (F1), and
 * while the debug screen is open.
 *
 * <p>What it shows comes from the server once a second ({@link PartySnapshotPayload}); nothing here
 * decides anything.
 */
public final class PartyHud {
    static final int X = 6, LINE = 12, BAR_WIDTH = 40, NAME_WIDTH = 70;
    private static List<PartySnapshotPayload.Member> members = List.of();

    private PartyHud() { }

    public static List<PartySnapshotPayload.Member> members() {
        return members;
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(PartySnapshotPayload.TYPE, (payload, context) -> members = payload.members());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> members = List.of());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> members = List.of());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("party_hud"), (graphics, delta) -> render(graphics));
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || members.isEmpty() || client.getDebugOverlay().showDebugScreen()) return;
        var font = client.font;
        int y = graphics.guiHeight() / 3;
        int right = X + NAME_WIDTH + BAR_WIDTH + 34;
        graphics.fill(X - 3, y - 3, right + 3, y + members.size() * LINE + 1, 0x80080B0E);
        for (PartySnapshotPayload.Member member : members) {
            String name = font.plainSubstrByWidth((member.leader() ? "★ " : "") + member.name(), NAME_WIDTH);
            int color = !member.online() ? 0xFF6B7378 : member.away() ? 0xFF9AA2A6 : 0xFFE8EDF0;
            graphics.text(font, name, X, y + 1, color, true);
            int barX = X + NAME_WIDTH + 2;
            if (member.online()) {
                float share = member.maxHealth() <= 0 ? 0 : Math.min(1, member.health() / member.maxHealth());
                int barColor = share > 0.6f ? 0xFF5FBF5A : share > 0.3f ? 0xFFE0B84A : 0xFFE05A4A;
                graphics.fill(barX - 1, y + 2, barX + BAR_WIDTH + 1, y + 8, 0xD812171A);
                graphics.fill(barX, y + 3, barX + Math.round(BAR_WIDTH * share), y + 7, barColor);
                String far = member.away() ? "AFK" : member.distance() < 0 ? "—" : member.distance() > 999 ? "999+"
                        : String.format(Locale.ROOT, "%dm", member.distance());
                graphics.text(font, far, barX + BAR_WIDTH + 4, y + 1, 0xFFA8B0B4, true);
            } else {
                graphics.text(font, "offline", barX, y + 1, 0xFF6B7378, true);
            }
            y += LINE;
        }
    }
}
