package de.ipnats.hardwrought.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How one weapon distributes a hit across slash, pierce and blunt. Shares are normalized, so a
 * profile may be written with percentages, weights or fractions without changing the result.
 */
public record DamageSplit(double slash, double pierce, double blunt) {
    public static final DamageSplit BLUNT_ONLY = new DamageSplit(0, 0, 1);
    private static final String NO_PHYSICAL_DAMAGE = "A weapon must deal at least one physical damage type";
    private static final Codec<Double> SHARE = Codec.doubleRange(0, 1_000_000);
    private static final Codec<Shares> RAW = RecordCodecBuilder.create(instance -> instance.group(
            SHARE.optionalFieldOf("slash", 0.0).forGetter(Shares::slash),
            SHARE.optionalFieldOf("pierce", 0.0).forGetter(Shares::pierce),
            SHARE.optionalFieldOf("blunt", 0.0).forGetter(Shares::blunt)
    ).apply(instance, Shares::new));
    /** A malformed profile must fail the reload as a rejected value, never as a thrown exception. */
    public static final Codec<DamageSplit> CODEC = RAW.comapFlatMap(
            shares -> shares.slash() + shares.pierce() + shares.blunt() > 0
                    ? DataResult.success(new DamageSplit(shares.slash(), shares.pierce(), shares.blunt()))
                    : DataResult.error(() -> NO_PHYSICAL_DAMAGE),
            split -> new Shares(split.slash, split.pierce, split.blunt));

    private record Shares(double slash, double pierce, double blunt) { }

    public DamageSplit {
        if (!finite(slash, pierce, blunt) || slash < 0 || pierce < 0 || blunt < 0) {
            throw new IllegalArgumentException("Damage shares must be finite and non-negative");
        }
        double sum = slash + pierce + blunt;
        if (sum <= 0) throw new IllegalArgumentException(NO_PHYSICAL_DAMAGE);
        slash /= sum;
        pierce /= sum;
        blunt /= sum;
    }

    public double share(CombatDamageType type) {
        return switch (type) {
            case SLASH -> slash;
            case PIERCE -> pierce;
            case BLUNT -> blunt;
            default -> 0;
        };
    }

    /** The type carrying the largest share; ties resolve in slash, pierce, blunt order. */
    public CombatDamageType dominantType() {
        if (slash >= pierce && slash >= blunt) return CombatDamageType.SLASH;
        return pierce >= blunt ? CombatDamageType.PIERCE : CombatDamageType.BLUNT;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
