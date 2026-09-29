package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.ipnats.hardwrought.mobs.SleepDebt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob specification § 20: phantoms follow sleep debt, not time awake; see {@link SleepDebt}. */
@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/stats/ServerStatsCounter;getValue(Lnet/minecraft/stats/Stat;)I"))
    private int hardwrought$sleepDebt(ServerStatsCounter stats, Stat<?> stat, Operation<Integer> original,
                                      @Local ServerPlayer player) {
        return SleepDebt.phantomPressure(SleepDebt.debt(player));
    }
}
