package de.ipnats.hardwrought.client.knowledge;

import de.ipnats.hardwrought.core.networking.KnowledgeNotePayload;
import de.ipnats.hardwrought.knowledge.KnowledgeLevel;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The corner of the screen saying that something was just learned.
 *
 * <p>Built to feel exactly like the toast vanilla shows for an unlocked recipe, because that is the
 * moment it stands in for. Several learnings at once share one toast and take turns in it rather
 * than stacking up a column of them: breaking a new rock with a new pick teaches two things in one
 * swing, and that should read as one event.
 */
public class KnowledgeToast implements Toast {
    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/recipe");
    private static final long DISPLAY_TIME = 5000L;
    private static final int TITLE_COLOR = 0xFF500050;
    private static final int TEXT_COLOR = 0xFF000000;

    private final List<Entry> learned = new ArrayList<>();
    private long lastChanged;
    private boolean changed;
    private Visibility wantedVisibility = Visibility.HIDE;
    private int shown;

    /** One line of the toast: what was learned, and whether it was held or worked with. */
    private record Entry(ItemStack stack, KnowledgeLevel level) { }

    private KnowledgeToast() { }

    /** Adds what a player just learned to the toast, opening a new one only when none is up. */
    public static void addOrUpdate(ToastManager manager, KnowledgeNotePayload.Note note) {
        Item item = BuiltInRegistries.ITEM.getValue(note.id());
        if (item == null) return;
        KnowledgeLevel level = KnowledgeLevel.byOrdinal(note.level());
        if (level == KnowledgeLevel.UNKNOWN) return;
        KnowledgeToast toast = manager.getToast(KnowledgeToast.class, NO_TOKEN);
        if (toast == null) {
            toast = new KnowledgeToast();
            manager.addToast(toast);
        }
        toast.add(new ItemStack(item), level);
    }

    private void add(ItemStack stack, KnowledgeLevel level) {
        // A thing discovered and then studied in the same breath is one learning with a better
        // ending, not two lines in a row.
        learned.removeIf(entry -> entry.stack().is(stack.getItem()));
        learned.add(new Entry(stack, level));
        changed = true;
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
        if (learned.isEmpty()) {
            wantedVisibility = Visibility.HIDE;
            return;
        }
        double full = DISPLAY_TIME * manager.getNotificationDisplayTimeMultiplier();
        wantedVisibility = time - lastChanged < full ? Visibility.SHOW : Visibility.HIDE;
        shown = entryIndex(time, full, learned.size());
    }

    /**
     * Which of the gathered learnings is showing right now.
     *
     * <p>The whole toast lasts as long as any other, however much it has to say; the lines take
     * turns inside that one span rather than each claiming a span of their own. Eight things learned
     * at once would otherwise hold the corner of the screen for forty seconds.
     *
     * <p>Written as vanilla writes it, a remainder of a rising clock, for one reason worth recording:
     * the arithmetic cannot produce an index outside the list. An earlier version measured elapsed
     * time against a stamp taken on another thread, and an elapsed time that briefly ran backwards
     * indexed the list at minus one and took the game down with it.
     */
    public static int entryIndex(long time, double fullDisplay, int count) {
        if (count <= 0) return 0;
        double perEntry = Math.max(1.0, fullDisplay / count);
        return (int) Math.floorMod((long) (time / perEntry), (long) count);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, width(), height());
        if (learned.isEmpty()) return;
        // The list is read here and written when a packet arrives, so the index is clamped against
        // the list as it is now rather than as it was when update last ran.
        Entry entry = learned.get(Math.clamp(shown, 0, learned.size() - 1));
        graphics.text(font, Component.translatable(entry.level() == KnowledgeLevel.STUDIED
                ? "toast.hardwrought.studied" : "toast.hardwrought.discovered"), 30, 7, TITLE_COLOR, false);
        graphics.text(font, entry.stack().getHoverName(), 30, 18, TEXT_COLOR, false);
        graphics.fakeItem(entry.stack(), 8, 8);
    }
}
