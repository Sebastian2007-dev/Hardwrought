package de.ipnats.hardwrought.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mob specification section 17: a slime no longer deals damage. It is space control instead: a big
 * one shoves a player out of its way and blocks a corridor; splitting stays what it was.
 */
@Mixin(AbstractCubeMob.class)
public abstract class CubeMobSlimeMixin {
    @Unique private static final double PUSH_PER_SIZE = 0.25;

    @Inject(method = "dealDamage", at = @At("HEAD"), cancellable = true)
    private void hardwrought$shove(LivingEntity target, CallbackInfo callback) {
        AbstractCubeMob self = (AbstractCubeMob) (Object) this;
        if (!(self instanceof Slime) || self instanceof MagmaCube) return;
        callback.cancel();
        // Once in a while, not every tick of contact.
        if (target instanceof Player && self.tickCount % 10 == 0 && self.getSize() > 1) {
            de.ipnats.hardwrought.mobs.MobTraits.shove(target, PUSH_PER_SIZE * self.getSize(), self.getX(), self.getZ());
        }
    }
}
