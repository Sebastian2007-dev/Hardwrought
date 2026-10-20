package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.ipnats.hardwrought.server.Afk;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player away from the keyboard is shown so in everyone's player list (see {@link Afk}). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTabNameMixin {
    @ModifyReturnValue(method = "getTabListDisplayName", at = @At("RETURN"))
    private Component hardwrought$markAway(Component name) {
        return Afk.tabName((ServerPlayer) (Object) this, name);
    }
}
