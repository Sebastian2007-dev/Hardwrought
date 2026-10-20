package de.ipnats.hardwrought.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A group of players who play together: they share what they learn, are friends to each other's
 * spells, and by default cannot hurt one another. One of them leads it; the leader is the one who
 * invites, removes and decides about friendly fire.
 *
 * <p>Members are kept in the order they joined, so when the leader leaves the one who has been there
 * longest takes over, which is what a group of people would expect.
 */
public final class Party {
    public static final Codec<Party> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Party::id),
            UUIDUtil.STRING_CODEC.fieldOf("leader").forGetter(Party::leader),
            UUIDUtil.STRING_CODEC.listOf().fieldOf("members").forGetter(party -> List.copyOf(party.members)),
            Codec.BOOL.optionalFieldOf("friendly_fire", false).forGetter(Party::friendlyFire)
    ).apply(instance, Party::new));

    private final UUID id;
    private UUID leader;
    private final LinkedHashSet<UUID> members;
    private boolean friendlyFire;

    Party(UUID id, UUID leader, List<UUID> members, boolean friendlyFire) {
        this.id = id;
        this.members = new LinkedHashSet<>(members);
        this.members.add(leader);
        this.leader = leader;
        this.friendlyFire = friendlyFire;
    }

    public UUID id() {
        return id;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    /** Everyone in it, the leader included, in the order they joined. */
    public Set<UUID> members() {
        return java.util.Collections.unmodifiableSet(members);
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    public int size() {
        return members.size();
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    void add(UUID player) {
        members.add(player);
    }

    /**
     * Takes a member out. A leader leaving hands the party to whoever joined first after them.
     * Returns false when nobody is left in it, which is the end of the party.
     */
    boolean remove(UUID player) {
        members.remove(player);
        if (members.isEmpty()) return false;
        if (leader.equals(player)) leader = members.iterator().next();
        return true;
    }

    void promote(UUID player) {
        if (members.contains(player)) leader = player;
    }

    List<UUID> others(UUID player) {
        List<UUID> others = new ArrayList<>(members);
        others.remove(player);
        return others;
    }
}
