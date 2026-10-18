package de.ipnats.hardwrought.client.knowledge;

import de.ipnats.hardwrought.core.networking.JournalNotePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The corner of the screen saying that a thought in the journal was followed, and which one comes next.
 *
 * <p>Drawn like vanilla's advancement toast rather than the recipe toast the knowledge half uses: a quest
 * done is a step on the road, not one more thing learned. The two lines take turns in one toast, the done
 * thought first and then the next, so a player reads what they did and what to do in one glance.
 */
public class JournalToast implements Toast {
    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_TIME = 7000L;
    private static final int FOLLOWED_COLOR = 0xFF7FD45A;
    private static final int NEXT_COLOR = 0xFFFFD866;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final List<JournalNotePayload.Line> lines = new ArrayList<>();
    private long lastChanged;
    private boolean changed;
    private Visibility wantedVisibility = Visibility.HIDE;
    private int shown;

    private JournalToast() { }

    /** Adds a followed thought to the toast, opening a new one only when none is up. */
    public static void addOrUpdate(ToastManager manager, JournalNotePayload.Line line) {
        JournalToast toast = manager.getToast(JournalToast.class, NO_TOKEN);
        if (toast == null) {
            toast = new JournalToast();
            manager.addToast(toast);
        }
        toast.lines.removeIf(existing -> existing.note().equals(line.note()));
        toast.lines.add(line);
        toast.changed = true;
    }

    @Override
    public Visibility getWantedVisibility() {
        return wantedVisibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (changed) {
            lastChanged = time;
            changed = false;
        }
        if (lines.isEmpty()) {
            wantedVisibility = Visibility.HIDE;
            return;
        }
        double full = DISPLAY_TIME * manager.getNotificationDisplayTimeMultiplier();
        wantedVisibility = time - lastChanged < full ? Visibility.SHOW : Visibility.HIDE;
        shown = KnowledgeToast.entryIndex(time, full, lines.size());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, width(), height());
        if (lines.isEmpty()) return;
        JournalNotePayload.Line line = lines.get(Math.clamp(shown, 0, lines.size() - 1));
        graphics.text(font, Component.translatable(line.followed()
                ? "toast.hardwrought.journal.followed" : "toast.hardwrought.journal.next"),
                30, 7, line.followed() ? FOLLOWED_COLOR : NEXT_COLOR, false);
        Component title = Component.translatable("journal.hardwrought." + line.note().getPath() + ".title");
        String plain = title.getString();
        int room = width() - 34;
        graphics.text(font, font.width(plain) > room
                ? Component.literal(font.plainSubstrByWidth(plain, room - font.width("…")) + "…") : title,
                30, 18, TEXT_COLOR, false);
        Item item = BuiltInRegistries.ITEM.getValue(line.icon());
        graphics.fakeItem(new ItemStack(item == null ? Items.BOOK : item), 8, 8);
    }
}
