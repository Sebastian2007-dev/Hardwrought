package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Temperance: the rune a player puts on armor once the weather has beaten them often enough.
 *
 * <p>It does not warm anybody and it does not cool anybody. What it does is move the air the body
 * has to argue with towards the temperature the body would have chosen, which is the same thing
 * from the inside and a much better thing from the design's side: a tempered player in a winter
 * tundra is comfortable, and a tempered player in a summer desert is comfortable, with one
 * enchantment and no special case for either.
 *
 * <p>It is graded, and it is weighted by the same coverage the rest of the armor model uses. One
 * enchanted helmet is a seventh of the effect; a full set at level three is as near weatherproof as
 * the game offers. That is a deliberate amount of work — through the rune table it is nine shelves
 * and four pieces — because a switch that turns a whole mechanic off should cost what a whole
 * mechanic is worth.
 */
public final class Temperance {
    public static final ResourceKey<Enchantment> KEY =
            ResourceKey.create(Registries.ENCHANTMENT, Hardwrought.id("temperance"));

    /** Matches {@code max_level} in the enchantment definition. */
    public static final int MAX_LEVEL = 3;
    /**
     * How much of the way to comfort a full set at full level gets. Not all of it: there is always
     * a little weather left, so a player still has a reason to look at the sky.
     */
    public static final double FULL_STRENGTH = 0.92;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** The same area shares {@code ArmorCoverage} uses, for the same reason. */
    private static final double[] COVERAGE = {0.15, 0.40, 0.30, 0.15};

    private Temperance() { }

    /**
     * How tempered this player is, 0 to 1. Zero is nobody wearing it; one is all four pieces at
     * level three.
     */
    public static double of(LivingEntity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return 0;
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var rune = registry.get(KEY).orElse(null);
        if (rune == null) return 0;
        double worn = 0;
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            ItemStack piece = entity.getItemBySlot(ARMOR_SLOTS[index]);
            if (piece.isEmpty()) continue;
            int enchanted = EnchantmentHelper.getItemEnchantmentLevel(rune, piece);
            if (enchanted > 0) worn += COVERAGE[index] * Math.min(MAX_LEVEL, enchanted) / (double) MAX_LEVEL;
        }
        return Math.min(1.0, worn);
    }

    /**
     * The surroundings as a tempered player meets them: pulled towards the balance point by however
     * much of the rune they are wearing.
     */
    public static double temper(double ambient, double temperance, boolean immersed) {
        double share = Thermal.clamp(temperance, 0, 1) * FULL_STRENGTH;
        if (share <= 0) return ambient;
        return ambient + (Thermal.comfort(immersed) - ambient) * share;
    }
}
