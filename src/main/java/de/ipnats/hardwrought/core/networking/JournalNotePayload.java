package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A thought in the journal just followed, and the one it opens, on their way to the corner of the
 * screen: the quest done, and straight after it what to do next.
 *
 * @param lines what to show, in order
 */
public record JournalNotePayload(List<Line> lines) implements CustomPacketPayload {
    public static final Type<JournalNotePayload> TYPE = new Type<>(Hardwrought.id("journal_note_v1"));

    /** As many as one toast is worth: a handful of thoughts followed at once still reads as one moment. */
    public static final int MAX_LINES = 6;

    /**
     * One line of the toast.
     *
     * @param note     the thought, whose title the toast shows
     * @param icon     the item drawn beside it: what was held, or what the next thought asks for
     * @param followed true for a thought just followed, false for the one it opens
     */
    public record Line(Identifier note, Identifier icon, boolean followed) { }

    public static final StreamCodec<RegistryFriendlyByteBuf, JournalNotePayload> CODEC = new StreamCodec<>() {
        @Override
        public JournalNotePayload decode(RegistryFriendlyByteBuf buffer) {
            int count = Math.min(buffer.readVarInt(), MAX_LINES);
            List<Line> lines = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                lines.add(new Line(buffer.readIdentifier(), buffer.readIdentifier(), buffer.readBoolean()));
            }
            return new JournalNotePayload(lines);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, JournalNotePayload payload) {
            buffer.writeVarInt(payload.lines.size());
            for (Line line : payload.lines) {
                buffer.writeIdentifier(line.note());
                buffer.writeIdentifier(line.icon());
                buffer.writeBoolean(line.followed());
            }
        }
    };

    public JournalNotePayload {
        lines = List.copyOf(lines.size() > MAX_LINES ? lines.subList(lines.size() - MAX_LINES, lines.size()) : lines);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
