package de.ipnats.hardwrought.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the ritual's pedestals show what lies on them, as vanilla's display entities do (see {@code RitualPedestal}). */
@Mixin(Display.class)
public interface DisplayInvoker {
    @Invoker("setTransformation")
    void hardwrought$setTransformation(Transformation transformation);

    @Invoker("setBillboardConstraints")
    void hardwrought$setBillboardConstraints(Display.BillboardConstraints constraints);

    @Invoker("setBrightnessOverride")
    void hardwrought$setBrightnessOverride(Brightness brightness);

    @Invoker("setViewRange")
    void hardwrought$setViewRange(float range);
}
