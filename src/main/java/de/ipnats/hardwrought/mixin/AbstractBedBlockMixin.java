package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.block.AbstractBedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Specification section 11: a player should be able to sleep almost anywhere. Vanilla beds only
 * accept a sleeper after dark, but Hardwrought sleep never skips the night — it accelerates real
 * ticks — so there is nothing to protect against by refusing a bed at noon. A night shift is a
 * legitimate way to live.
 *
 * <p>The rule is read in two places: once when lying down, and again on every tick of
 * {@code Player#tick}, which is what threw a daytime sleeper straight back out of bed. Changing the
 * rule itself covers both, and any other caller, instead of patching each check.
 *
 * <p>Only {@code WHEN_DARK} becomes {@code ALWAYS}. A bed whose rule is {@code NEVER} keeps it, so
 * beds in the Nether and the End still explode rather than quietly becoming safe, and whether a bed
 * sets the respawn point or is destroyed on use is left exactly as the dimension defines it.
 */
@Mixin(AbstractBedBlock.class)
public abstract class AbstractBedBlockMixin {
    @ModifyReturnValue(method = "getBedRule", at = @At("RETURN"))
    private BedRule hardwrought$allowSleepingByDay(BedRule rule) {
        if (rule.canSleep() != BedRule.Rule.WHEN_DARK) return rule;
        return new BedRule(BedRule.Rule.ALWAYS, rule.canSetSpawn(), rule.destroyOnUse(),
                rule.destroyOnLeave(), rule.errorMessage());
    }
}
