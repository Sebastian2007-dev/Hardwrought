package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.end.dragon.CrystalWards;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The ward round each of the End's crystals while its dragon lives; see {@code CrystalWards}. Seen by the client, kept in the save. */
@Mixin(EndCrystal.class)
public abstract class EndCrystalMixin implements CrystalWards.Warded {
    @Unique
    private static final EntityDataAccessor<Float> HARDWROUGHT_WARD = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.FLOAT);

    @Override
    public float hardwrought$ward() {
        return ((EndCrystal) (Object) this).getEntityData().get(HARDWROUGHT_WARD);
    }

    @Override
    public void hardwrought$setWard(float ward) {
        ((EndCrystal) (Object) this).getEntityData().set(HARDWROUGHT_WARD, ward);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void hardwrought$defineWard(SynchedEntityData.Builder builder, CallbackInfo info) {
        builder.define(HARDWROUGHT_WARD, CrystalWards.UNSET);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hardwrought$tickWard(CallbackInfo info) {
        if (((EndCrystal) (Object) this).level() instanceof ServerLevel level) CrystalWards.tick(level, (EndCrystal) (Object) this);
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void hardwrought$wardHolds(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> info) {
        if (CrystalWards.absorb(level, (EndCrystal) (Object) this, source, damage)) info.setReturnValue(false);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void hardwrought$saveWard(ValueOutput output, CallbackInfo info) {
        output.putFloat("hardwrought_ward", hardwrought$ward());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void hardwrought$loadWard(ValueInput input, CallbackInfo info) {
        hardwrought$setWard(input.getFloatOr("hardwrought_ward", CrystalWards.UNSET));
    }
}
