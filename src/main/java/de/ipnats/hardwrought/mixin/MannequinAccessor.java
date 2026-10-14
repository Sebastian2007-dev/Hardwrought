package de.ipnats.hardwrought.mixin;

import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the easter egg dress a mannequin in a player's skin, which vanilla only does from saved data. */
@Mixin(Mannequin.class)
public interface MannequinAccessor {
    @Invoker("setProfile")
    void hardwrought$setProfile(ResolvableProfile profile);

    @Invoker("setHideDescription")
    void hardwrought$setHideDescription(boolean hide);
}
