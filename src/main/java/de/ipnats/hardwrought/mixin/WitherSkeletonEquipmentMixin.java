package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.MobEquipment;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob specification § 28.2: some wither skeletons are archers; see {@link MobEquipment}. */
@Mixin(WitherSkeleton.class)
public abstract class WitherSkeletonEquipmentMixin {
    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void hardwrought$bow(RandomSource random, DifficultyInstance difficulty, CallbackInfo callback) {
        MobEquipment.equipWitherSkeleton((WitherSkeleton) (Object) this, random);
    }
}
