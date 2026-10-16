package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.core.registry.ModEffects;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Golden apples are not food: they fill neither the stomach nor the body's stores (their nutrition
 * profiles are all naught), and are eaten for what they do. On top of vanilla's effects they give
 * stamina regeneration — the golden apple at the first level, the enchanted one at the third. The
 * enchanted apple is made again from eight blocks of gold, and stacks only to {@link #ENCHANTED_STACK}.
 */
public final class GoldenApples {
    /** How long the stamina regeneration of either lasts: a minute. */
    public static final int STAMINA_TICKS = 1200;
    public static final int ENCHANTED_STACK = 8;

    private GoldenApples() { }

    public static void initialize() {
        DefaultItemComponentEvents.MODIFY.register(context -> {
            context.modify(Items.GOLDEN_APPLE, (builder, lookup, item) -> {
                builder.set(DataComponents.FOOD, new FoodProperties(0, 0.0f, true));
                builder.set(DataComponents.CONSUMABLE, withStamina(Consumables.GOLDEN_APPLE, 0));
            });
            context.modify(Items.ENCHANTED_GOLDEN_APPLE, (builder, lookup, item) -> {
                builder.set(DataComponents.FOOD, new FoodProperties(0, 0.0f, true));
                builder.set(DataComponents.CONSUMABLE, withStamina(Consumables.ENCHANTED_GOLDEN_APPLE, 2));
                builder.set(DataComponents.MAX_STACK_SIZE, ENCHANTED_STACK);
            });
        });
    }

    /** Vanilla's way of eating it, and what it does, with stamina regeneration at this amplifier besides. */
    static Consumable withStamina(Consumable vanilla, int amplifier) {
        List<ConsumeEffect> effects = new ArrayList<>(vanilla.onConsumeEffects());
        effects.add(new ApplyStatusEffectsConsumeEffect(
                new MobEffectInstance(ModEffects.STAMINA_REGENERATION, STAMINA_TICKS, amplifier)));
        return new Consumable(vanilla.consumeSeconds(), vanilla.animation(), vanilla.sound(),
                vanilla.hasConsumeParticles(), List.copyOf(effects));
    }
}
