package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.environment.SeasonFogRules;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Under a thick autumn fog the sun does not reach the ground hard enough to set an undead alight.
 *
 * <p>This is the half of the fog a player feels rather than sees: the morning they cannot see far
 * in is also the morning the things in it do not burn away on their own. It is tied to the fog and
 * to nothing else — in clear weather, in every other season, and the moment the fog lifts, the sun
 * does exactly what it always did.
 */
@Mixin(Mob.class)
public abstract class MobFogSunMixin {
    @Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
    private void hardwrought$fogShadesTheSun(CallbackInfo callback) {
        if (SeasonFogRules.sunIsSmothered()) callback.cancel();
    }
}
