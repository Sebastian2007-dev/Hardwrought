package de.ipnats.hardwrought.smithing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Sections 37 and 38: how well a piece was forged, and what was done to it afterwards.
 *
 * <p>Carried by a forged part and handed on to the tool it becomes, so two pickaxes of the same iron
 * can be two different pickaxes. Everything a player notices — how fast it digs, how long it lasts,
 * how hard it hits — is worked out from these two values rather than written onto the item, so the
 * balance can change without every forged tool in every world having to be rewritten.
 *
 * <p>A tool nobody forged — one from a chest, a villager, another mod — carries none, and behaves
 * exactly as vanilla made it.
 *
 * @param craftsmanship 0 to 1: how cleanly the piece was worked
 * @param treatment     what happened to it after the last time it was at forging heat
 */
public record ForgeQuality(float craftsmanship, Treatment treatment) {
    /** The heat treatment of section 38. Only iron takes a hardening; bronze and gold do not. */
    public enum Treatment implements StringRepresentable {
        /** Left to cool in the air: soft, tough, the baseline. */
        AIR,
        /** Quenched from forging heat: hard and sharp, and brittle with it. */
        QUENCHED,
        /** Quenched, then warmed gently again: most of the hardness, little of the brittleness. */
        TEMPERED;

        public static final Codec<Treatment> CODEC = StringRepresentable.fromEnum(Treatment::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<ForgeQuality> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0, 1).fieldOf("craftsmanship").forGetter(ForgeQuality::craftsmanship),
            Treatment.CODEC.optionalFieldOf("treatment", Treatment.AIR).forGetter(ForgeQuality::treatment)
    ).apply(instance, ForgeQuality::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeQuality> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ForgeQuality::craftsmanship,
            ByteBufCodecs.VAR_INT.map(index -> Treatment.values()[index], Treatment::ordinal), ForgeQuality::treatment,
            ForgeQuality::new);

    public ForgeQuality {
        if (!Float.isFinite(craftsmanship)) craftsmanship = 0;
        craftsmanship = Math.clamp(craftsmanship, 0f, 1f);
        if (treatment == null) treatment = Treatment.AIR;
    }

    public ForgeQuality withTreatment(Treatment value) {
        return new ForgeQuality(craftsmanship, value);
    }

    /**
     * Mining speed against vanilla's. Section 37's example is the whole range: a poorly made pick
     * digs at about four fifths of the speed, a well made one a little above full.
     */
    public double speedFactor() {
        double base = 0.80 + 0.30 * craftsmanship;
        return base * switch (treatment) {
            case AIR -> 1.00;
            case QUENCHED -> 1.10;
            case TEMPERED -> 1.06;
        };
    }

    /** Durability against vanilla's: 71 % for poor work up to about 125 % for the best. */
    public double durabilityFactor() {
        double base = 0.65 + 0.60 * craftsmanship;
        return base * switch (treatment) {
            case AIR -> 1.00;
            case QUENCHED -> 0.75;
            case TEMPERED -> 1.12;
        };
    }

    /** Damage against the weapon's own. */
    public double damageFactor() {
        double base = 0.85 + 0.25 * craftsmanship;
        return base * switch (treatment) {
            case AIR -> 1.00;
            case QUENCHED -> 1.08;
            case TEMPERED -> 1.05;
        };
    }

    /** The quality on this stack, or null where nobody forged it. */
    public static ForgeQuality of(ItemStack stack) {
        return stack.get(ModDataComponents.FORGE_QUALITY);
    }

    public static double speedFactor(ItemStack stack) {
        ForgeQuality quality = of(stack);
        return quality == null ? 1.0 : quality.speedFactor();
    }

    public static double durabilityFactor(ItemStack stack) {
        ForgeQuality quality = of(stack);
        return quality == null ? 1.0 : quality.durabilityFactor();
    }

    public static double damageFactor(ItemStack stack) {
        ForgeQuality quality = of(stack);
        return quality == null ? 1.0 : quality.damageFactor();
    }
}
