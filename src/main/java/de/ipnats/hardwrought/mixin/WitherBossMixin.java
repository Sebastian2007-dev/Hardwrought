package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.wither.WitherPowers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Wither's own powers ({@link WitherPowers}) each tick, and its death: the star is not dropped at
 * once, but gathered out of its souls.
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void hardwrought$powers(ServerLevel level, CallbackInfo callback) {
        WitherPowers.tick((WitherBoss) (Object) this, level);
    }

    @Redirect(method = "dropCustomDeathLoot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity hardwrought$gatherTheStar(WitherBoss wither, ServerLevel level, ItemLike star) {
        WitherPowers.died(wither, level);
        return null;
    }
}
