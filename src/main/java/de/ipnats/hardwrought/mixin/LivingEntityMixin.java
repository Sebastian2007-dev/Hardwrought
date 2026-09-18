package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.ipnats.hardwrought.combat.CombatSystem;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The two seams Milestone-2 combat needs. Vanilla keeps the surrounding flow: invulnerability,
 * the damage cooldown, knockback, armor points and the front-arc check of a raised shield.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    /**
     * Damage type against armor material. Vanilla reaches this for hits it later discards during
     * invulnerability, so the combat system must not change any state here.
     */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float hardwrought$scaleIncomingDamage(float value, ServerLevel level, DamageSource source, float damage) {
        CombatSystem combat = hardwrought$combat(level);
        return combat == null ? value : combat.scaleIncomingDamage((LivingEntity) (Object) this, source, value);
    }

    /**
     * Blocking and parrying. Vanilla has already decided that a guard is up and faces the attack;
     * a returned zero therefore means there is nothing for Hardwrought to resolve.
     */
    @ModifyReturnValue(method = "applyItemBlocking", at = @At("RETURN"))
    private float hardwrought$resolveBlocking(float blocked, ServerLevel level, DamageSource source, float damage) {
        CombatSystem combat = hardwrought$combat(level);
        return combat == null ? blocked
                : combat.resolveBlocking((LivingEntity) (Object) this, level, source, damage, blocked);
    }

    private static CombatSystem hardwrought$combat(ServerLevel level) {
        var runtime = CoreLifecycle.find(level.getServer());
        return runtime == null ? null : runtime.combat();
    }
}
