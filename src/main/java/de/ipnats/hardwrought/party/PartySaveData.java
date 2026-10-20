package de.ipnats.hardwrought.party;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every party of the world, saved with it: a group keeps standing across restarts and while its
 * members are offline. Invitations are not kept here; they are a moment, not a fact about the world.
 */
public final class PartySaveData extends SavedData {
    private static final Codec<PartySaveData> CODEC = Party.CODEC.listOf().xmap(PartySaveData::new, data -> List.copyOf(data.parties.values()));
    public static final SavedDataType<PartySaveData> TYPE = new SavedDataType<>(Hardwrought.id("parties"), PartySaveData::new, CODEC, null);

    private final Map<UUID, Party> parties = new HashMap<>();
    /** Which party each player is in; derived from the parties, never saved on its own. */
    private final Map<UUID, Party> byMember = new HashMap<>();

    PartySaveData() { }

    private PartySaveData(List<Party> saved) {
        for (Party party : saved) {
            // A hand-edited file could put a player into two parties; the first one keeps them.
            List<UUID> doubled = new ArrayList<>();
            for (UUID member : party.members()) if (byMember.containsKey(member)) doubled.add(member);
            boolean left = true;
            for (UUID member : doubled) left = party.remove(member);
            if (!left) continue;
            parties.put(party.id(), party);
            for (UUID member : party.members()) byMember.put(member, party);
        }
    }

    Party of(UUID player) {
        return byMember.get(player);
    }

    Collection<Party> all() {
        return parties.values();
    }

    Party create(UUID leader) {
        Party party = new Party(UUID.randomUUID(), leader, List.of(leader), de.ipnats.hardwrought.server.ServerConfig.get().friendlyFire());
        parties.put(party.id(), party);
        byMember.put(leader, party);
        setDirty();
        return party;
    }

    void join(Party party, UUID player) {
        party.add(player);
        byMember.put(player, party);
        setDirty();
    }

    /** Takes a player out of their party; the last one out ends it. */
    void leave(UUID player) {
        Party party = byMember.remove(player);
        if (party == null) return;
        if (!party.remove(player)) parties.remove(party.id());
        setDirty();
    }

    void changed() {
        setDirty();
    }
}
