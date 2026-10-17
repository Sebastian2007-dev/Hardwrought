package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.witchking.MantleClient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hovering wearer of the Witch King's mantle moves as the hover says, not as gravity does; see {@code MantleClient}. */
@Mixin(Player.class)
public abstract class PlayerHoverMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void hardwrought$hover(Vec3 input, CallbackInfo info) {
        if (MantleClient.travel((Player) (Object) this, input)) info.cancel();
    }
}
