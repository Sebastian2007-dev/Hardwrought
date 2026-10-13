package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The ribbons a player has laid into their compendium.
 *
 * <p>They are kept with what the player knows, on the server, so they are there again in the same
 * world on another day and on another machine. Each travels with the level the player has reached
 * on it, like everything else the book draws: a page marked before its subject was ever held is a
 * ribbon with a shadow on it.
 */
public final class BookmarkPayloads {
    /** As many ribbons as hang down the edge of the book. */
    public static final int MAX = 10;

    private BookmarkPayloads() { }

    /** Lay a ribbon at this entry, or take out the one that is there. */
    public record Toggle(Identifier subject) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = new Type<>(Hardwrought.id("bookmark_toggle_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Toggle> CODEC = new StreamCodec<>() {
            @Override
            public Toggle decode(RegistryFriendlyByteBuf buffer) {
                return new Toggle(buffer.readIdentifier());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Toggle toggle) {
                buffer.writeIdentifier(toggle.subject);
            }
        };

        public Toggle {
            if (subject == null) throw new IllegalArgumentException("A ribbon needs a page to lie at");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Which ribbons are in my book? Asked when it is opened. */
    public record Ask() implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Hardwrought.id("bookmark_ask_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.unit(new Ask());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Every ribbon in the book, in the order they were laid in. */
    public record Marks(List<CompendiumPagePayload.Entry> entries) implements CustomPacketPayload {
        public static final Type<Marks> TYPE = new Type<>(Hardwrought.id("bookmark_marks_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Marks> CODEC = new StreamCodec<>() {
            @Override
            public Marks decode(RegistryFriendlyByteBuf buffer) {
                int size = Math.min(buffer.readVarInt(), MAX);
                List<CompendiumPagePayload.Entry> entries = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    entries.add(new CompendiumPagePayload.Entry(buffer.readIdentifier(), buffer.readVarInt()));
                }
                return new Marks(entries);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Marks marks) {
                buffer.writeVarInt(marks.entries.size());
                for (CompendiumPagePayload.Entry entry : marks.entries) {
                    buffer.writeIdentifier(entry.id());
                    buffer.writeVarInt(entry.level());
                }
            }
        };

        public Marks {
            entries = List.copyOf(entries);
            if (entries.size() > MAX) entries = entries.subList(0, MAX);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
