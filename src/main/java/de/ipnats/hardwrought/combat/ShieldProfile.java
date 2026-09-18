package de.ipnats.hardwrought.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Blocking and parrying of specification section 34. Section 31 describes buckler, round shield and
 * tower shield as different points in this value space; only the vanilla shield ships a profile,
 * further shield classes arrive together with their items.
 */
public record ShieldProfile(List<Identifier> items, double blockFraction, double parryFraction,
                            int parryWindowTicks, double staminaPerDamage, double guardBreakImpact,
                            int staggerTicks) {
    public static final Codec<ShieldProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("items").forGetter(ShieldProfile::items),
            Codec.doubleRange(0, 1).optionalFieldOf("block_fraction", 0.5).forGetter(ShieldProfile::blockFraction),
            Codec.doubleRange(0, 1).optionalFieldOf("parry_fraction", 0.95).forGetter(ShieldProfile::parryFraction),
            Codec.intRange(0, 40).optionalFieldOf("parry_window_ticks", 6).forGetter(ShieldProfile::parryWindowTicks),
            Codec.doubleRange(0, 20).optionalFieldOf("stamina_per_damage", 1.0).forGetter(ShieldProfile::staminaPerDamage),
            Codec.doubleRange(0, 1000).optionalFieldOf("guard_break_impact", 8.0).forGetter(ShieldProfile::guardBreakImpact),
            Codec.intRange(0, 200).optionalFieldOf("stagger_ticks", 30).forGetter(ShieldProfile::staggerTicks)
    ).apply(instance, ShieldProfile::new));

    public ShieldProfile {
        if (items == null) throw new IllegalArgumentException("Shield profile needs items");
        if (!finite(blockFraction, parryFraction, staminaPerDamage, guardBreakImpact)
                || blockFraction < 0 || blockFraction > 1
                || parryFraction < 0 || parryFraction > 1
                || parryWindowTicks < 0 || parryWindowTicks > 40
                || staminaPerDamage < 0 || staminaPerDamage > 20
                || guardBreakImpact < 0 || guardBreakImpact > 1000
                || staggerTicks < 0 || staggerTicks > 200) {
            throw new IllegalArgumentException("Invalid shield profile values");
        }
        items = List.copyOf(items);
    }

    /** A hit inside the window is a parry, a later hit is an ordinary block. */
    public boolean isParry(int ticksUsingItem) {
        return ticksUsingItem <= parryWindowTicks;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
