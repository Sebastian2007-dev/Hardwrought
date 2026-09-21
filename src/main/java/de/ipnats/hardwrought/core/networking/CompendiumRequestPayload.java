package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * What the open compendium wants to see next: a shelf, the recipes for something, or its uses.
 *
 * @param mode    one of the modes of {@link CompendiumPagePayload}
 * @param subject the shelf being opened, or the entry being looked up
 * @param search  the text typed into the search field, empty for none
 */
public record CompendiumRequestPayload(int mode, Identifier subject, String search)
        implements CustomPacketPayload {
    public static final Type<CompendiumRequestPayload> TYPE =
            new Type<>(Hardwrought.id("compendium_request_v1"));
    /** A search term longer than this is a client bug or an attack, never a player. */
    public static final int MAX_SEARCH = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, CompendiumRequestPayload> CODEC =
            new StreamCodec<>() {
        @Override
        public CompendiumRequestPayload decode(RegistryFriendlyByteBuf buffer) {
            return new CompendiumRequestPayload(buffer.readVarInt(), buffer.readIdentifier(),
                    buffer.readUtf(MAX_SEARCH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, CompendiumRequestPayload request) {
            buffer.writeVarInt(request.mode);
            buffer.writeIdentifier(request.subject);
            buffer.writeUtf(request.search, MAX_SEARCH);
        }
    };

    public CompendiumRequestPayload {
        if (subject == null) throw new IllegalArgumentException("A request needs a subject");
        if (search == null) search = "";
        if (search.length() > MAX_SEARCH) search = search.substring(0, MAX_SEARCH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
