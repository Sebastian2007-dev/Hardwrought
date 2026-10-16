package de.ipnats.hardwrought.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import de.ipnats.hardwrought.client.underworld.UnderworldReverb;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every sound, the moment it starts: sent through the Underworld's reverb, and muffled by the rock in
 * the way, or left dry. Its position is already set by then. See {@link UnderworldReverb}.
 */
@Mixin(Channel.class)
public abstract class SoundChannelMixin {
    @Shadow @Final private int source;

    @Inject(method = "play", at = @At("HEAD"))
    private void hardwrought$reverb(CallbackInfo callback) {
        UnderworldReverb.onPlay(source);
    }
}
