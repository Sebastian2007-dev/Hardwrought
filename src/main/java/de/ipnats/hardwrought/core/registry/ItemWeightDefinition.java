package de.ipnats.hardwrought.core.registry;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Per-item carried mass. Material density remains a separate physical property.
 *
 * <p>A file either names one item, or a group of items whose masses differ:
 * <pre>{"item": "hardwrought:filled_waterskin", "kilograms": 1.2}</pre>
 * <pre>{"weights": {"minecraft:iron_helmet": 2.5, "minecraft:iron_chestplate": 9.0}}</pre>
 */
public record ItemWeightDefinition(Map<Identifier, Double> weights) {
    private static final Codec<Double> MASS = Codec.DOUBLE.validate(value ->
            valid(value) ? DataResult.success(value) : DataResult.error(() -> "Invalid item mass"));
    private static final Codec<ItemWeightDefinition> SINGLE = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(definition -> definition.weights.keySet().iterator().next()),
            MASS.fieldOf("kilograms").forGetter(definition -> definition.weights.values().iterator().next())
    ).apply(instance, ItemWeightDefinition::new));
    /**
     * A map codec drops entries it cannot read and reports a partial result, so the group form is
     * validated here: an empty result is rejected instead of being turned into a broken definition.
     */
    private static final Codec<ItemWeightDefinition> GROUP =
            Codec.unboundedMap(Identifier.CODEC, MASS).fieldOf("weights").codec().comapFlatMap(
                    weights -> weights.isEmpty()
                            ? DataResult.error(() -> "Item weight definition lists no items")
                            : DataResult.success(new ItemWeightDefinition(weights)),
                    ItemWeightDefinition::weights);
    public static final Codec<ItemWeightDefinition> CODEC = Codec.either(SINGLE, GROUP)
            .xmap(either -> either.map(single -> single, group -> group),
                    definition -> definition.weights.size() == 1
                            ? Either.left(definition) : Either.right(definition));

    public ItemWeightDefinition(Identifier item, double kilograms) {
        this(Map.of(itemOrThrow(item), kilograms));
    }

    public ItemWeightDefinition {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("Item weight definition lists no items");
        }
        weights.forEach((item, mass) -> {
            if (item == null || mass == null || !valid(mass)) {
                throw new IllegalArgumentException("Invalid item weight definition");
            }
        });
        weights = Map.copyOf(weights);
    }

    /** Kept for the single-item form so a definition can still be built in code. */
    public Identifier item() {
        if (weights.size() != 1) throw new IllegalStateException("Definition covers several items");
        return weights.keySet().iterator().next();
    }

    public double kilograms() {
        if (weights.size() != 1) throw new IllegalStateException("Definition covers several items");
        return weights.values().iterator().next();
    }

    private static Identifier itemOrThrow(Identifier item) {
        if (item == null) throw new IllegalArgumentException("Item weight definition needs an item");
        return item;
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1_000;
    }
}
