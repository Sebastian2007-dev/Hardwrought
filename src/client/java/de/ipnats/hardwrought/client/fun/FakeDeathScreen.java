package de.ipnats.hardwrought.client.fun;

import de.ipnats.hardwrought.fun.StampiEasterEgg;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The death a hardcore player only acts out ({@link StampiEasterEgg}): it looks like the death screen,
 * but there is nothing on it to press — no spectating, no title screen, and so no deleting the world from
 * it — and after a moment it says so, and goes away.
 */
public class FakeDeathScreen extends Screen {
    /** When the joke is told, and when the screen goes, in ticks after it opened. */
    private static final int JOKE_AT = 60;
    private static final int CLOSE_AT = StampiEasterEgg.NOT_REALLY_TICKS;

    private final Component message;
    private int ticks;

    public FakeDeathScreen(Component message) {
        super(Component.translatable("deathScreen.title.hardcore"));
        this.message = message;
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(StampiEasterEgg.FakeDeath.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().gui.setScreen(new FakeDeathScreen(payload.message()))));
    }

    @Override
    public void tick() {
        if (++ticks >= CLOSE_AT) onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The red of the real thing.
        graphics.fillGradient(0, 0, width, height, 0x60500000, 0xA0803030);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.pose().pushMatrix();
        graphics.pose().scale(2.0f, 2.0f);
        graphics.centeredText(font, title, width / 2 / 2, 30, 0xFFFFFFFF);
        graphics.pose().popMatrix();
        graphics.centeredText(font, message, width / 2, 85, 0xFFFFFFFF);
        if (ticks >= JOKE_AT) {
            graphics.pose().pushMatrix();
            graphics.pose().scale(3.0f, 3.0f);
            graphics.centeredText(font, Component.translatable("message.hardwrought.stampi.not_really")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), width / 2 / 3, 120 / 3, 0xFFFFFF55);
            graphics.pose().popMatrix();
        }
    }
}
