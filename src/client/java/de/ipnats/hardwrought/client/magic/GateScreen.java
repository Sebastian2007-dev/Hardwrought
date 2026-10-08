package de.ipnats.hardwrought.client.magic;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.magic.GateTargetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A far gate stands open: where should it lead? Three boxes for X, Y and Z, filled with where the
 * caster stands, and how far the gate reaches. Closing it without an answer lets the gate fade.
 */
public final class GateScreen extends Screen {
    private static final int GATE_COLOR = 0xFFB07CFF;

    private final int range;
    private final boolean anchored;
    private final long closes;
    private EditBox x, y, z;
    private String[] values;

    public GateScreen(int range, int seconds, boolean anchored) {
        super(Component.translatable("gui.hardwrought.gate"));
        this.range = range;
        this.anchored = anchored;
        this.closes = System.currentTimeMillis() + seconds * 1000L;
    }

    @Override
    protected void init() {
        if (values == null) {
            var player = Minecraft.getInstance().player;
            values = player == null ? new String[] {"0", "64", "0"}
                    : new String[] {Integer.toString(player.getBlockX()), Integer.toString(player.getBlockY()),
                            Integer.toString(player.getBlockZ())};
        }
        int boxWidth = 64, gap = 18;
        int left = (width - 3 * boxWidth - 2 * gap) / 2;
        int top = height / 2 - 10;
        x = box(left, top, boxWidth, "X", 0);
        y = box(left + boxWidth + gap, top, boxWidth, "Y", 1);
        z = box(left + 2 * (boxWidth + gap), top, boxWidth, "Z", 2);
        setInitialFocus(x);
        int buttons = 3 * boxWidth + 2 * gap;
        addRenderableWidget(Button.builder(Component.translatable("gui.hardwrought.gate.go"), b -> go())
                .bounds(left, top + 30, (buttons - gap) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hardwrought.gate.cancel"), b -> onClose())
                .bounds(left + (buttons + gap) / 2, top + 30, (buttons - gap) / 2, 20).build());
    }

    private EditBox box(int left, int top, int width, String name, int index) {
        EditBox box = new EditBox(font, left, top, width, 18, Component.literal(name));
        box.setMaxLength(9);
        box.setValue(values[index]);
        box.setResponder(text -> values[index] = text);
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int top = height / 2 - 10;
        graphics.centeredText(font, title, width / 2, top - 46, GATE_COLOR);
        long left = Math.max(0, (closes - System.currentTimeMillis() + 999) / 1000);
        graphics.centeredText(font, Component.translatable("gui.hardwrought.gate.range", range, left), width / 2, top - 32,
                0xFFE0D8F0);
        graphics.centeredText(font, Component.translatable(anchored ? "gui.hardwrought.gate.anchored"
                : "gui.hardwrought.gate.unanchored"), width / 2, top - 21, anchored ? 0xFFA8E0A0 : 0xFFE0A070);
        int boxWidth = 64, gap = 18, x0 = (width - 3 * boxWidth - 2 * gap) / 2;
        String[] names = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) graphics.text(font, names[i], x0 + i * (boxWidth + gap) - 10, top + 5, 0xFFE0D8F0, true);
        var player = Minecraft.getInstance().player;
        int[] target = target();
        if (player != null && target != null) {
            double distance = Math.sqrt(Math.pow(target[0] + 0.5 - player.getX(), 2) + Math.pow(target[1] - player.getY(), 2)
                    + Math.pow(target[2] + 0.5 - player.getZ(), 2));
            graphics.centeredText(font, Component.translatable("gui.hardwrought.gate.distance", (int) distance), width / 2,
                    top + 58, distance > range ? 0xFFFF7070 : 0xFFE0D8F0);
        }
        if (left == 0) onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            go();
            return true;
        }
        return super.keyPressed(event);
    }

    private int[] target() {
        try {
            return new int[] {Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void go() {
        int[] target = target();
        if (target == null) return;
        ClientPlayNetworking.send(new GateTargetPayload(target[0], target[1], target[2]));
        onClose();
    }

    /** For tests: fills in the boxes and steps through, as a player typing would. */
    public void goForTesting(int tx, int ty, int tz) {
        x.setValue(Integer.toString(tx));
        y.setValue(Integer.toString(ty));
        z.setValue(Integer.toString(tz));
        go();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
