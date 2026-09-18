package de.ipnats.hardwrought.core.utilities;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Transient attribute modifiers owned by one system, keyed by a stable Hardwrought identifier. */
public final class AttributeModifiers {
    private AttributeModifiers() { }

    public static void update(AttributeInstance attribute, Identifier id, double amount,
                              AttributeModifier.Operation operation) {
        if (attribute == null) return;
        attribute.removeModifier(id);
        if (amount != 0 && Double.isFinite(amount)) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    public static void clear(AttributeInstance attribute, Identifier id) {
        if (attribute != null) attribute.removeModifier(id);
    }
}
