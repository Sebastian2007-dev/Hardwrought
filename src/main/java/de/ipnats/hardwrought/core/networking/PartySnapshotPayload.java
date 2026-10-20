package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * How the rest of a player's party is doing, for the small list at the side of the screen. Sent once a
 * second to every member online; an empty list means the player is in no party (any more).
 * Display only: nothing on the client acts on it.
 */
public record PartySnapshotPayload(List<Member> members) implements CustomPacketPayload {
    public static final int MAX_MEMBERS = 32, MAX_NAME = 24;
    public static final Type<PartySnapshotPayload> TYPE = new Type<>(Hardwrought.id("party_snapshot_v1"));

    /**
     * One other member. Distance is in whole blocks, or -1 when they are in another dimension or
     * offline; health is 0 for an offline member.
     */
    public record Member(UUID id, String name, boolean leader, boolean online, boolean away, float health, float maxHealth,
                         int distance) {
        public Member {
            if (id == null || name == null || name.length() > MAX_NAME) throw new IllegalArgumentException("Invalid party member");
            if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || health < 0 || maxHealth < 0 || maxHealth > 10_000) {
                throw new IllegalArgumentException("Invalid party member health");
            }
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PartySnapshotPayload> CODEC = new StreamCodec<>() {
        @Override
        public PartySnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_MEMBERS) throw new IllegalArgumentException("Too many party members: " + count);
            List<Member> members = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID id = UUIDUtil.STREAM_CODEC.decode(buffer);
                String name = buffer.readUtf(MAX_NAME);
                byte flags = buffer.readByte();
                members.add(new Member(id, name, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                        buffer.readFloat(), buffer.readFloat(), buffer.readVarInt()));
            }
            return new PartySnapshotPayload(members);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PartySnapshotPayload payload) {
            buffer.writeVarInt(payload.members.size());
            for (Member member : payload.members) {
                UUIDUtil.STREAM_CODEC.encode(buffer, member.id());
                buffer.writeUtf(member.name(), MAX_NAME);
                buffer.writeByte((member.leader() ? 1 : 0) | (member.online() ? 2 : 0) | (member.away() ? 4 : 0));
                buffer.writeFloat(member.health());
                buffer.writeFloat(member.maxHealth());
                buffer.writeVarInt(member.distance());
            }
        }
    };

    public PartySnapshotPayload {
        if (members == null || members.size() > MAX_MEMBERS) throw new IllegalArgumentException("Invalid party snapshot");
        members = List.copyOf(members);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
