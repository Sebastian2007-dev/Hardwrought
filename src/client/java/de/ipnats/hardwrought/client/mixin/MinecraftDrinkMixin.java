package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.core.networking.DrinkByHandPayload;
import de.ipnats.hardwrought.water.WaterEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sneaking with an empty hand at water drinks from it — at the water itself, whatever is or is not behind
 * it — and does nothing else: the other hand does not set down what it holds. Asked before the game tries
 * either hand; the server finds the water again and judges ({@code WaterEvents.drinkByHand}).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDrinkMixin {
    @Shadow public LocalPlayer player;
    @Shadow public ClientLevel level;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void hardwrought$drinkByHand(CallbackInfo callback) {
        if (player == null || level == null || player.isSpectator() || !player.isSecondaryUseActive()) return;
        if (!player.getMainHandItem().isEmpty()) return;
        if (WaterEvents.drinkableWater(level, player) == null) return;
        if (ClientPlayNetworking.canSend(DrinkByHandPayload.TYPE)) ClientPlayNetworking.send(new DrinkByHandPayload());
        callback.cancel();
    }
}
