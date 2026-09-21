package de.ipnats.hardwrought.geology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * What one kind of ore body in a rock type looks like, specification sections 52 and 53.
 *
 * <p>Section 52 asks for the opposite of vanilla: not tiny veins scattered over the whole world, but
 * few, large, regional deposits that have to be found. So a deposit is described by how big it is,
 * how deep it sits and how rich it can be — never by how often a chunk should roll for it.
 *
 * @param ore        the ore block this deposit is made of
 * @param weight     relative chance of a deposit in this rock being this ore
 * @param minY       lowest height the body can sit at
 * @param maxY       highest height the body can sit at
 * @param minRadius  smallest horizontal radius in blocks
 * @param maxRadius  largest horizontal radius in blocks
 * @param minGrade   poorest this ore ever assays, as a fraction of metal in the rock
 * @param maxGrade   richest it ever assays
 * @param drillTier  how good a drill has to be to bring this ore up out of bare rock. The early
 *                   ores are tier 1; the ones a world should not hand out freely are higher.
 */
public record DepositProfile(Identifier ore, int weight, int minY, int maxY,
                             int minRadius, int maxRadius, double minGrade, double maxGrade,
                             int drillTier) {
    public static final Codec<DepositProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("ore").forGetter(DepositProfile::ore),
            Codec.intRange(1, 100).optionalFieldOf("weight", 1).forGetter(DepositProfile::weight),
            Codec.intRange(-256, 1023).fieldOf("min_y").forGetter(DepositProfile::minY),
            Codec.intRange(-256, 1023).fieldOf("max_y").forGetter(DepositProfile::maxY),
            Codec.intRange(4, 128).optionalFieldOf("min_radius", 20).forGetter(DepositProfile::minRadius),
            Codec.intRange(4, 128).optionalFieldOf("max_radius", 44).forGetter(DepositProfile::maxRadius),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("min_grade", 0.03).forGetter(DepositProfile::minGrade),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("max_grade", 0.20).forGetter(DepositProfile::maxGrade),
            Codec.intRange(1, 3).optionalFieldOf("drill_tier", 1).forGetter(DepositProfile::drillTier)
    ).apply(instance, DepositProfile::new));

    public DepositProfile {
        if (ore == null) throw new IllegalArgumentException("A deposit needs an ore");
        if (maxY < minY) throw new IllegalArgumentException("Deposit " + ore + " has an inverted depth band");
        if (maxRadius < minRadius) throw new IllegalArgumentException("Deposit " + ore + " has an inverted size");
        if (maxGrade < minGrade) throw new IllegalArgumentException("Deposit " + ore + " has an inverted grade");
        if (drillTier < 1 || drillTier > DrillTier.values().length) {
            throw new IllegalArgumentException("Deposit " + ore + " wants a drill that does not exist");
        }
    }

    /** How thick the band this ore can sit in is. */
    public int bandHeight() {
        return maxY - minY;
    }
}
