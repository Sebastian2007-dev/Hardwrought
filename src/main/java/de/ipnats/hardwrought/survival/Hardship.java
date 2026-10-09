package de.ipnats.hardwrought.survival;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Two rules that make the world push back, in the manner of Better Than Wolves and MITE. Each is
 * hard and neither is a dice roll the player cannot see coming: what it costs is known before the
 * price is paid.
 *
 * <ul>
 *   <li><b>Wounds.</b> A hurt body does less. Under {@value #WOUNDED_BELOW} of its health it walks
 *   and digs slower, the more the worse; under {@value #CRIPPLED_BELOW} it cannot run at all. Being
 *   hit is therefore not something to shrug off until the last heart: every fight leaves the next
 *   one harder until the wounds are healed.</li>
 *   <li><b>The arcane burden.</b> Runes are power, and power is noticed. Everything hostile sees a
 *   player from farther off for every rune they wear or hold, and a piece wears faster for every
 *   rune written on it — overcharged runes most of all. The best armor there is makes its wearer
 *   the easiest thing in the dark to find, and has to be mended the most.</li>
 * </ul>
 */
public final class Hardship {
    /** Shares of full health under which a body is wounded, and under which it can no longer run. */
    public static final double WOUNDED_BELOW = 0.6, CRIPPLED_BELOW = 0.3;
    /** What the worst wounds take from walking, from digging and from the jump. */
    public static final double WOUND_SPEED = 0.25, WOUND_MINING = 0.40, WOUND_JUMP = 0.20;

    /** How much farther a player is noticed from for each rune, and for each level a rune is overcharged. */
    public static final double SCENT_PER_RUNE = 0.04, SCENT_PER_OVERCHARGE = 0.06;
    /** The most the runes add to how far a player is noticed from: twice as far. */
    public static final double MOST_SCENT = 1.0;
    /** The chance, for each point of wear, to cost one more: per rune, per overcharged level, and at most. */
    public static final double WEAR_PER_RUNE = 0.04, WEAR_PER_OVERCHARGE = 0.10, MOST_WEAR = 0.75;

    private static final EquipmentSlot[] CARRIED = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Hardship() { }

    // ---------------------------------------------------------------- wounds

    /** How badly wounded a body with this much health is: nought when sound enough, one at the point of death. */
    public static double wound(float health, float most) {
        if (most <= 0) return 0;
        double share = Math.clamp(health / most, 0.0, 1.0);
        return share >= WOUNDED_BELOW ? 0 : (WOUNDED_BELOW - share) / WOUNDED_BELOW;
    }

    /** Whether a body with this much health can no longer run. */
    public static boolean crippled(float health, float most) {
        return most > 0 && health / most < CRIPPLED_BELOW;
    }

    // ---------------------------------------------------------------- the arcane burden

    /** How many runes — enchantments of any kind — a piece carries. */
    public static int runes(ItemStack piece) {
        return piece.getEnchantments().size();
    }

    /** How many levels beyond their enchantments' own highest the runes on a piece are charged. */
    public static int overcharge(ItemStack piece) {
        int over = 0;
        var enchantments = piece.getEnchantments();
        for (Holder<Enchantment> rune : enchantments.keySet()) {
            over += Math.max(0, enchantments.getLevel(rune) - rune.value().getMaxLevel());
        }
        return over;
    }

    /** How much farther than otherwise this body is noticed from, as a factor: one for somebody who carries no rune. */
    public static double scent(LivingEntity body) {
        double scent = 0;
        for (EquipmentSlot slot : CARRIED) {
            ItemStack piece = body.getItemBySlot(slot);
            scent += runes(piece) * SCENT_PER_RUNE + overcharge(piece) * SCENT_PER_OVERCHARGE;
        }
        return 1 + Math.min(MOST_SCENT, scent);
    }

    /** The chance for each point of wear on this piece to cost one more. */
    public static double wearChance(ItemStack piece) {
        return Math.min(MOST_WEAR, runes(piece) * WEAR_PER_RUNE + overcharge(piece) * WEAR_PER_OVERCHARGE);
    }

    /** Wear on a piece that carries runes: each point of it may cost one more. */
    public static int wear(ItemStack piece, int amount, RandomSource random) {
        if (amount <= 0) return amount;
        double chance = wearChance(piece);
        if (chance <= 0) return amount;
        int extra = 0;
        for (int i = 0; i < amount; i++) if (random.nextDouble() < chance) extra++;
        return amount + extra;
    }
}
