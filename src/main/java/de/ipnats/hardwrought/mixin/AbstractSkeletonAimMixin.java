package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.ipnats.hardwrought.mobs.SkeletonAim;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob specification section 8: shot kinds and fallible aim; see {@link SkeletonAim}. */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonAimMixin {
    @WrapOperation(method = "performRangedAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile hardwrought$aim(Projectile arrow, ServerLevel level, ItemStack stack, double dx, double dy, double dz,
                                       float velocity, float inaccuracy, Operation<Projectile> original,
                                       @Local(argsOnly = true) LivingEntity target) {
        return SkeletonAim.shoot((AbstractSkeleton) (Object) this, target, arrow, velocity, inaccuracy,
                (speed, spread) -> original.call(arrow, level, stack, dx, dy, dz, speed, spread));
    }
}
