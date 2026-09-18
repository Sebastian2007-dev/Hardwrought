package de.ipnats.hardwrought.client.combat;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.combat.CombatDamageType;
import de.ipnats.hardwrought.combat.CombatEvent;
import de.ipnats.hardwrought.core.networking.CombatSnapshotPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BlocksAttacks;

import java.util.Locale;

/**
 * Combat feedback: a guard bar below the crosshair while a shield is raised and a short label for
 * the last result. The server decides every outcome; this only renders what it reported. The guard
 * bar animates from the local item-use timer so it stays smooth between packets.
 */
public final class CombatHud {
    private static final int LABEL_TICKS = 30;
    private static final int GUARD_BAR_WIDTH = 62;
    private static final int RISING_COLOR = 0xFF6D7A82;
    private static final int PARRY_COLOR = 0xFFE9C45C;
    private static final int HOLD_COLOR = 0xFF5A93C4;
    private static CombatSnapshotPayload snapshot;
    private static CombatEvent label = CombatEvent.NONE;
    private static CombatDamageType labelType = CombatDamageType.BLUNT;
    private static float labelDamage;
    private static int labelTicksLeft;

    private CombatHud() { }

    public static CombatSnapshotPayload snapshot() { return snapshot; }
    public static CombatEvent activeLabel() { return labelTicksLeft > 0 ? label : CombatEvent.NONE; }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(CombatSnapshotPayload.TYPE, (payload, context) -> accept(payload));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (labelTicksLeft > 0) labelTicksLeft--;
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("combat_hud"),
                (graphics, delta) -> render(graphics));
    }

    private static void accept(CombatSnapshotPayload payload) {
        snapshot = payload;
        if (payload.event() == CombatEvent.GUARD_UP || payload.event() == CombatEvent.GUARD_DOWN) return;
        label = payload.event();
        labelType = payload.damageType();
        labelDamage = payload.damage();
        labelTicksLeft = LABEL_TICKS;
    }

    private static void reset() {
        snapshot = null;
        label = CombatEvent.NONE;
        labelTicksLeft = 0;
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;
        int center = graphics.guiWidth() / 2;
        renderGuardBar(graphics, player, center, graphics.guiHeight() / 2 + 14);
        if (labelTicksLeft > 0 && label != CombatEvent.NONE) {
            renderLabel(graphics, client, center, graphics.guiHeight() - 62);
        }
    }

    private static void renderGuardBar(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                       LocalPlayer player, int center, int y) {
        if (!player.isUsingItem()) return;
        BlocksAttacks blocksAttacks = player.getUseItem().get(DataComponents.BLOCKS_ATTACKS);
        if (blocksAttacks == null) return;
        int window = snapshot == null ? 0 : snapshot.parryWindowTicks();
        int active = player.getTicksUsingItem() - blocksAttacks.blockDelayTicks();
        int left = center - GUARD_BAR_WIDTH / 2;

        graphics.fill(left - 1, y - 1, left + GUARD_BAR_WIDTH + 1, y + 4, 0xC010161A);
        graphics.fill(left, y, left + GUARD_BAR_WIDTH, y + 3, 0xFF2A3238);
        double ratio;
        int color;
        if (active < 0) {
            int delay = Math.max(1, blocksAttacks.blockDelayTicks());
            ratio = clamp((delay + active) / (double) delay, 0, 1);
            color = RISING_COLOR;
        } else if (window > 0 && active <= window) {
            ratio = clamp(1.0 - active / (double) window, 0, 1);
            color = PARRY_COLOR;
        } else {
            ratio = 1.0;
            color = HOLD_COLOR;
        }
        int filled = (int) Math.round(GUARD_BAR_WIDTH * ratio);
        graphics.fill(left, y, left + filled, y + 3, color);
    }

    private static void renderLabel(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                    Minecraft client, int center, int y) {
        String text = switch (label) {
            case BLOCKED -> I18n.get("hud.hardwrought.combat.blocked");
            case PARRIED -> I18n.get("hud.hardwrought.combat.parried");
            case GUARD_BROKEN -> I18n.get("hud.hardwrought.combat.guard_broken");
            case PARRY_LANDED -> I18n.get("hud.hardwrought.combat.parry_landed");
            case HIT -> String.format(Locale.ROOT, "%s %.1f", damageTypeName(labelType), labelDamage);
            default -> "";
        };
        if (text.isEmpty()) return;
        int alpha = (int) Math.round(255 * clamp(labelTicksLeft / (double) LABEL_TICKS, 0.1, 1.0));
        int color = alpha << 24 | (labelColor(label) & 0x00FFFFFF);
        graphics.text(client.font, text, center - client.font.width(text) / 2, y, color, true);
    }

    private static String damageTypeName(CombatDamageType type) {
        return I18n.get("hud.hardwrought.damage_type." + type.serializedName());
    }

    private static int labelColor(CombatEvent event) {
        return switch (event) {
            case PARRIED, PARRY_LANDED -> 0xFFE9C45C;
            case BLOCKED -> 0xFF8FC1E8;
            case GUARD_BROKEN -> 0xFFE06B6B;
            default -> 0xFFE8EDF0;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
