package de.ipnats.hardwrought.mixin;

import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Hardwrought add a phase of its own to the dragon's: the whole of its own AI (see {@code DragonHunt}). */
@Mixin(EnderDragonPhase.class)
public interface EnderDragonPhaseInvoker {
    @Invoker("create")
    static <T extends DragonPhaseInstance> EnderDragonPhase<T> hardwrought$create(Class<T> type, String name) {
        throw new AssertionError();
    }
}
