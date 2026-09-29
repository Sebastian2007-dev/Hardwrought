package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.MobEquipment;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob specification § 5.1: a zombie's tool is what it can break through; see {@link MobEquipment}. */
@Mixin(Zombie.class)
public abstract class ZombieEquipmentMixin {
    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void hardwrought$tools(RandomSource random, DifficultyInstance difficulty, CallbackInfo callback) {
        MobEquipment.equipZombie((Zombie) (Object) this, random);
    }
}
