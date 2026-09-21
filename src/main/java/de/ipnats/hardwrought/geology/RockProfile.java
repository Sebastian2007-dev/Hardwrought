package de.ipnats.hardwrought.geology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * What a rock type is worth, per specification section 51: a granite region carries different ores
 * than a volcanic or a sedimentary one, and that mapping is a design decision, so it lives in a
 * datapack rather than in a constant.
 *
 * @param rocks    the rock types this profile describes, by id
 * @param deposits the kinds of ore body that can occur in them
 */
public record RockProfile(List<Identifier> rocks, List<DepositProfile> deposits) {
    public static final Codec<RockProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("rocks").forGetter(RockProfile::rocks),
            DepositProfile.CODEC.listOf().fieldOf("deposits").forGetter(RockProfile::deposits)
    ).apply(instance, RockProfile::new));

    public RockProfile {
        if (rocks == null || deposits == null) {
            throw new IllegalArgumentException("A rock profile needs rock types and deposits");
        }
        rocks = List.copyOf(rocks);
        deposits = List.copyOf(deposits);
    }

    /** Total weight of the ores in this rock, which is what a deposit roll is taken against. */
    public int totalWeight() {
        int total = 0;
        for (DepositProfile deposit : deposits) total += deposit.weight();
        return total;
    }

    /** The ore at this point of the weighted range, or null where the rock carries nothing. */
    public DepositProfile pick(int roll) {
        int remaining = roll;
        for (DepositProfile deposit : deposits) {
            remaining -= deposit.weight();
            if (remaining < 0) return deposit;
        }
        return deposits.isEmpty() ? null : deposits.get(deposits.size() - 1);
    }
}
