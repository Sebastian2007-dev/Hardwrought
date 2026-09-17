package de.ipnats.hardwrought.core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/** Per-item carried mass. Material density remains a separate physical property. */
public record ItemWeightDefinition(Identifier item, double kilograms) {
    private static final Codec<Double> MASS = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value >= 0 && value <= 1_000
                    ? DataResult.success(value) : DataResult.error(() -> "Invalid item mass"));
    public static final Codec<ItemWeightDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(ItemWeightDefinition::item),
            MASS.fieldOf("kilograms").forGetter(ItemWeightDefinition::kilograms)
    ).apply(instance, ItemWeightDefinition::new));

    public ItemWeightDefinition {
        if (item == null || !Double.isFinite(kilograms) || kilograms < 0 || kilograms > 1_000) {
            throw new IllegalArgumentException("Invalid item weight definition");
        }
    }
}
