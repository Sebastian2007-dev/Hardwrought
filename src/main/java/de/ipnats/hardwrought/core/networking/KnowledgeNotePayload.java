package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player has just found out, on its way to the corner of their screen.
 *
 * <p>Learning something is a moment, and a moment that leaves no mark may as well not have happened.
 * This is the packet behind that mark: the same toast vanilla shows for an unlocked recipe, saying
 * what was discovered or studied.
 *
 * @param notes what was learned since the last one of these, newest last
 */
public record KnowledgeNotePayload(List<Note> notes) implements CustomPacketPayload {
    public static final Type<KnowledgeNotePayload> TYPE = new Type<>(Hardwrought.id("knowledge_note_v1"));

    /**
     * How many learnings one toast is worth showing. A player who empties a shulker of unfamiliar
     * things has still learned all of it — the compendium has every entry — but a toast that cycled
     * for two minutes would be a nuisance rather than a reward.
     */
    public static final int MAX_NOTES = 8;

    /** One thing learned: what it was, and how well it is now known. */
    public record Note(Identifier id, int level) { }

    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeNotePayload> CODEC =
            new StreamCodec<>() {
        @Override
        public KnowledgeNotePayload decode(RegistryFriendlyByteBuf buffer) {
            int count = Math.min(buffer.readVarInt(), MAX_NOTES);
            List<Note> notes = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                notes.add(new Note(buffer.readIdentifier(), buffer.readVarInt()));
            }
            return new KnowledgeNotePayload(notes);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, KnowledgeNotePayload payload) {
            buffer.writeVarInt(payload.notes.size());
            for (Note note : payload.notes) {
                buffer.writeIdentifier(note.id());
                buffer.writeVarInt(note.level());
            }
        }
    };

    public KnowledgeNotePayload {
        notes = List.copyOf(notes);
        if (notes.size() > MAX_NOTES) notes = notes.subList(0, MAX_NOTES);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
