package de.ipnats.hardwrought.nether;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.survival.Thermal;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/**
 * Armour that keeps the heat out: ashcork bark ({@link Ashcork}), and whatever else is tagged
 * {@code hardwrought:heat_shielding}.
 *
 * <p>Like temperance it works on the air the body has to argue with, but on the hot side only: it
 * pulls hot air towards comfort and leaves the cold alone. Weighted by the coverage the rest of the
 * armour model uses, so a helmet alone is a little shade and a full set is most of the way.
 */
public final class HeatShield {
    public static final TagKey<Item> SHIELDING = TagKey.create(Registries.ITEM, Hardwrought.id("heat_shielding"));
    /** How much of the way from hot air to comfort a full set gets. */
    public static final double FULL_STRENGTH = 0.75;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** The same area shares {@code ArmorCoverage} uses. */
    private static final double[] COVERAGE = {0.15, 0.40, 0.30, 0.15};

    private HeatShield() { }

    /** How shielded this entity is, 0 to 1. */
    public static double of(LivingEntity entity) {
        double worn = 0;
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            if (entity.getItemBySlot(ARMOR_SLOTS[index]).is(SHIELDING)) worn += COVERAGE[index];
        }
        return Math.min(1.0, worn);
    }

    /** Hot air as a shielded body meets it; cold air untouched. */
    public static double shield(double ambient, double shielding, boolean immersed) {
        double comfort = Thermal.comfort(immersed);
        if (ambient <= comfort || shielding <= 0) return ambient;
        return ambient - (ambient - comfort) * Math.min(1, shielding) * FULL_STRENGTH;
    }
}
