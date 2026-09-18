package de.ipnats.hardwrought.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Weapon properties of specification section 29. Vanilla keeps its own base damage, durability and
 * attribute modifiers; a profile only adds the values that distinguish the weapon classes of
 * section 30. Reach and attack speed are relative, so they compose with the modifiers an item
 * already carries instead of replacing them.
 */
public record WeaponProfile(List<Identifier> items, DamageSplit damage, double staminaCost,
                            double armorPenetration, double impact, double handling,
                            double reachBonusBlocks, double attackSpeedFactor,
                            double minimumReachBlocks) {
    /** Applied to hits from items without a profile: an unclassified impact counts as blunt. */
    public static final WeaponProfile IMPROVISED =
            new WeaponProfile(List.of(), DamageSplit.BLUNT_ONLY, 0.5, 0, 1.0, 1.0, 0, 1.0, 0);

    public static final Codec<WeaponProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("items").forGetter(WeaponProfile::items),
            DamageSplit.CODEC.fieldOf("damage").forGetter(WeaponProfile::damage),
            Codec.doubleRange(0, 20).optionalFieldOf("stamina_cost", 0.5).forGetter(WeaponProfile::staminaCost),
            Codec.doubleRange(0, 1).optionalFieldOf("armor_penetration", 0.0).forGetter(WeaponProfile::armorPenetration),
            Codec.doubleRange(0, 100).optionalFieldOf("impact", 1.0).forGetter(WeaponProfile::impact),
            Codec.doubleRange(0.25, 4).optionalFieldOf("handling", 1.0).forGetter(WeaponProfile::handling),
            Codec.doubleRange(-2, 6).optionalFieldOf("reach_bonus_blocks", 0.0).forGetter(WeaponProfile::reachBonusBlocks),
            Codec.doubleRange(0.25, 4).optionalFieldOf("attack_speed_factor", 1.0).forGetter(WeaponProfile::attackSpeedFactor),
            Codec.doubleRange(0, 6).optionalFieldOf("minimum_reach_blocks", 0.0).forGetter(WeaponProfile::minimumReachBlocks)
    ).apply(instance, WeaponProfile::new));

    public WeaponProfile {
        if (items == null || damage == null) throw new IllegalArgumentException("Weapon profile needs items and damage");
        if (!finite(staminaCost, armorPenetration, impact, handling, reachBonusBlocks,
                attackSpeedFactor, minimumReachBlocks)
                || staminaCost < 0 || staminaCost > 20
                || armorPenetration < 0 || armorPenetration > 1
                || impact < 0 || impact > 100
                || handling < 0.25 || handling > 4
                || reachBonusBlocks < -2 || reachBonusBlocks > 6
                || attackSpeedFactor < 0.25 || attackSpeedFactor > 4
                || minimumReachBlocks < 0 || minimumReachBlocks > 6) {
            throw new IllegalArgumentException("Invalid weapon profile values");
        }
        items = List.copyOf(items);
    }

    /**
     * Control quality of section 29. A swing while sprinting or in mid-air lands worse; a
     * high-handling weapon loses less of the hit than a heavy, unwieldy one.
     */
    public double controlFactor(boolean sprinting, boolean airborne) {
        double penalty = airborne ? 0.35 : sprinting ? 0.20 : 0.0;
        if (penalty == 0) return 1.0;
        return Math.max(0.35, 1.0 - penalty / handling);
    }

    /**
     * Section 30: spears and polearms are weak against enemies inside their minimum range.
     * Weapons without a minimum range are unaffected.
     */
    public double closeQuartersFactor(double distanceBlocks) {
        if (minimumReachBlocks <= 0 || distanceBlocks >= minimumReachBlocks) return 1.0;
        double inside = (minimumReachBlocks - Math.max(0, distanceBlocks)) / minimumReachBlocks;
        return Math.max(0.40, 1.0 - inside * 0.60);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
