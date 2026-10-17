package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.ipnats.hardwrought.end.dragon.VoidDragon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The dragon of the end of the game: far more to it than vanilla's, and what touches its head or its
 * wings is torn and thrown by Hardwrought's harm rather than vanilla's (see {@code VoidDragon}).
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
    @ModifyReturnValue(method = "createAttributes", at = @At("RETURN"))
    private static AttributeSupplier.Builder hardwrought$tougher(AttributeSupplier.Builder builder) {
        return builder.add(Attributes.MAX_HEALTH, VoidDragon.HEALTH);
    }

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
    private void hardwrought$bite(ServerLevel level, List<Entity> entities, CallbackInfo info) {
        VoidDragon.bite(level, (EnderDragon) (Object) this, entities);
        info.cancel();
    }

    @Inject(method = "knockBack", at = @At("HEAD"), cancellable = true)
    private void hardwrought$buffet(ServerLevel level, List<Entity> entities, CallbackInfo info) {
        VoidDragon.buffet(level, (EnderDragon) (Object) this, entities);
        info.cancel();
    }
}
