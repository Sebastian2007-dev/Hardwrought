package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.Perception;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every game event is a possible noise for hostile mobs to hear; see {@link Perception}. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelHearingMixin {
    @Inject(method = "gameEvent", at = @At("HEAD"))
    private void hardwrought$hear(Holder<GameEvent> event, Vec3 at, GameEvent.Context context, CallbackInfo callback) {
        Perception.heard((ServerLevel) (Object) this, event, at, context.sourceEntity());
    }
}
