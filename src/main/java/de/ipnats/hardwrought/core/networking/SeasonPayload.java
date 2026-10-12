package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What time of year it is, and whether there is fog on the world.
 *
 * <p>The calendar itself needs no packet — it is derived from the day time, which the client has
 * anyway. This is sent because two things about a season are not derivable: fog is rolled by the
 * server and would otherwise differ between players standing next to each other, and both sides
 * need to agree on the season before the client can draw it or the server can decide whether the
 * sun is reaching a zombie.
 *
 * @param season   ordinal of {@link de.ipnats.hardwrought.environment.Season}
 * @param fog      0 to 1, how thick the fog on the world is at this moment
 * @param dayInSeason one-based, for anything that wants to show a date
 * @param blend    0 to 1, how far the look of the world has already turned towards the next
 *                 season. A year that changed colour in one frame would look like a bug; this is
 *                 what lets the grass go slowly rather than all at once.
 */
public record SeasonPayload(int season, float fog, int dayInSeason, float blend) implements CustomPacketPayload {
    public static final Type<SeasonPayload> TYPE = new Type<>(Hardwrought.id("season"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SeasonPayload> CODEC = new StreamCodec<>() {
        @Override public SeasonPayload decode(RegistryFriendlyByteBuf buffer) {
            return new SeasonPayload(buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt(),
                    buffer.readFloat());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SeasonPayload payload) {
            buffer.writeVarInt(payload.season);
            buffer.writeFloat(payload.fog);
            buffer.writeVarInt(payload.dayInSeason);
            buffer.writeFloat(payload.blend);
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
