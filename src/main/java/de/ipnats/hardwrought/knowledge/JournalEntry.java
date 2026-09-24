package de.ipnats.hardwrought.knowledge;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * One thought in the journal: a nudge towards something the player could try next.
 *
 * <p>An entry carries no text. The text lives in the language files under the entry's own id, which
 * is what lets the whole chain be translated, and read by a player, without a line of it travelling
 * over the wire or sitting in a class file.
 *
 * @param id      names the entry and, through it, its two translation keys
 * @param teaches what the entry is nudging towards; the first of them is the icon it is drawn with,
 *                and holding any of them is what marks the thought as followed
 * @param after   what has to have been seen before the thought occurs to the player at all; empty
 *                for the first entry, which is readable the moment the book is opened
 * @param needsAll whether the thought is followed only once every one of {@code teaches} has been
 *                held, for a problem that takes several things together to solve
 * @param ultraOnly whether the thought belongs only to an Ultra world, for a problem that only
 *                exists there
 */
public record JournalEntry(Identifier id, List<Identifier> teaches, List<Identifier> after, boolean needsAll,
                           boolean ultraOnly) {
    public JournalEntry {
        if (id == null) throw new IllegalArgumentException("A journal entry needs a name");
        teaches = List.copyOf(teaches);
        after = List.copyOf(after);
    }

    /** An entry followed by holding any one of the things it points at. */
    public JournalEntry(Identifier id, List<Identifier> teaches, List<Identifier> after) {
        this(id, teaches, after, false, false);
    }

    public JournalEntry(Identifier id, List<Identifier> teaches, List<Identifier> after, boolean needsAll) {
        this(id, teaches, after, needsAll, false);
    }

    /** The item the entry is drawn with and the compendium opens on when it is clicked. */
    public Identifier subject() {
        return teaches.isEmpty() ? null : teaches.getFirst();
    }

    public String titleKey() {
        return "journal.hardwrought." + id.getPath() + ".title";
    }

    public String textKey() {
        return "journal.hardwrought." + id.getPath() + ".text";
    }
}
