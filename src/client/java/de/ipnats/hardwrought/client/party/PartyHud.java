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
 * while the debug screen is open; and on its own with {@link #TOGGLE} (O), which is remembered
 * across restarts in {@code config/hardwrought-hud.properties}.
 *
 * <p>What it shows comes from the server once a second ({@link PartySnapshotPayload}); nothing here
 * decides anything.
 */
public final class PartyHud {
    static final int X = 6, LINE = 12, BAR_WIDTH = 40, NAME_WIDTH = 70;
    /** Shows or hides the list. */
    public static final net.minecraft.client.KeyMapping TOGGLE = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            new net.minecraft.client.KeyMapping("key.hardwrought.party_list", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD,
                    com.mojang.blaze3d.platform.InputConstants.KEY_O, de.ipnats.hardwrought.client.HardwroughtKeys.CATEGORY));
    private static final java.nio.file.Path SETTINGS = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("hardwrought-hud.properties");
    private static List<PartySnapshotPayload.Member> members = List.of();
    private static boolean shown = true;

    private PartyHud() { }

    public static List<PartySnapshotPayload.Member> members() {
        return members;
    }

    public static boolean shown() {
        return shown;
    }

    public static void initialize() {
        shown = load();
        ClientPlayNetworking.registerGlobalReceiver(PartySnapshotPayload.TYPE, (payload, context) -> members = payload.members());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> members = List.of());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> members = List.of());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.consumeClick()) {
                shown = !shown;
                save(shown);
                if (client.player != null) {
                    client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                            shown ? "message.hardwrought.party_list.shown" : "message.hardwrought.party_list.hidden"));
                }
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("party_hud"), (graphics, delta) -> render(graphics));
    }

    private static boolean load() {
        var properties = new java.util.Properties();
        try (var reader = java.nio.file.Files.newBufferedReader(SETTINGS)) {
            properties.load(reader);
        } catch (java.io.IOException | IllegalArgumentException missing) {
            return true;
        }
        return !"false".equalsIgnoreCase(properties.getProperty("partyList", "true").trim());
    }

    private static void save(boolean shown) {
        var properties = new java.util.Properties();
        try (var reader = java.nio.file.Files.newBufferedReader(SETTINGS)) {
            properties.load(reader);
        } catch (java.io.IOException | IllegalArgumentException missing) {
            // Written fresh below.
        }
        properties.setProperty("partyList", Boolean.toString(shown));
        try (var writer = java.nio.file.Files.newBufferedWriter(SETTINGS)) {
            properties.store(writer, "Hardwrought HUD - changed in game");
        } catch (java.io.IOException exception) {
            Hardwrought.LOGGER.warn("Cannot save {}", SETTINGS, exception);
        }
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!shown || client.player == null || members.isEmpty() || client.getDebugOverlay().showDebugScreen()) return;
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
