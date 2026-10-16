package de.ipnats.hardwrought.client.mixin;

import com.mojang.blaze3d.audio.Library;
import de.ipnats.hardwrought.client.underworld.UnderworldReverb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The sound engine has opened its device: the Underworld's reverb is built on it. See {@link UnderworldReverb}. */
@Mixin(Library.class)
public abstract class SoundLibraryMixin {
    @Shadow private long currentDevice;

    @Inject(method = "init", at = @At("TAIL"))
    private void hardwrought$reverb(CallbackInfo callback) {
        UnderworldReverb.setup(currentDevice);
    }
}
