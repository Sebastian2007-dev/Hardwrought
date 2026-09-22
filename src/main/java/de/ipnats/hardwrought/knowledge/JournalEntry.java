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
 */
public record JournalEntry(Identifier id, List<Identifier> teaches, List<Identifier> after) {
    public JournalEntry {
        if (id == null) throw new IllegalArgumentException("A journal entry needs a name");
        teaches = List.copyOf(teaches);
        after = List.copyOf(after);
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
