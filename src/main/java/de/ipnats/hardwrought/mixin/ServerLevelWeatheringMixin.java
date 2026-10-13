package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.environment.Weathering;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where Hardwrought's snow and ice are worked out: on vanilla's own per-chunk precipitation tick.
 *
 * <p>{@code tickChunk} rolls a position in every ticking chunk and hands it here, which is uniform
 * coverage of the whole loaded world at a rate the game already budgets for. Riding on it costs no
 * new scheduling and, far more importantly, means the weather happens because of the season rather
 * than because somebody is standing nearby. The passes this replaced walked outward from each
 * player and produced a circle of deep snow that followed them about.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatheringMixin {
    @Inject(method = "tickPrecipitation", at = @At("TAIL"))
    private void hardwrought$weather(BlockPos pos, CallbackInfo callback) {
        Weathering.tick((ServerLevel) (Object) this, pos);
    }
}
