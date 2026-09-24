package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.environment.Leaves;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

/**
 * Section 40: with one's head in the leaves, one sees mostly leaves. A green-dark veil over the view,
 * heaviest at the edges, for as long as the eyes are inside a leaf block.
 */
public final class LeafOverlay {
    private static final int VEIL = 0x6A1C3314;
    private static final int EDGE = 0x5A0E1C0A;

    private LeafOverlay() { }

    public static void initialize() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, Hardwrought.id("leaf_overlay"),
                (graphics, delta) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !client.options.getCameraType().isFirstPerson()) return;
        BlockPos eyes = BlockPos.containing(client.player.getEyePosition());
        if (!Leaves.isLeaves(client.level.getBlockState(eyes))) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, VEIL);
        int band = Math.max(8, Math.min(width, height) / 6);
        graphics.fill(0, 0, width, band, EDGE);
        graphics.fill(0, height - band, width, height, EDGE);
        graphics.fill(0, band, band, height - band, EDGE);
        graphics.fill(width - band, band, width, height - band, EDGE);
    }
}
