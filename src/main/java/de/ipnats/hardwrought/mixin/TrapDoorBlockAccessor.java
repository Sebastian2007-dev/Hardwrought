package de.ipnats.hardwrought.mixin;

import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A trapdoor's set type — its sounds and whether a hand may open it — for trapdoors in a shared cell. */
@Mixin(TrapDoorBlock.class)
public interface TrapDoorBlockAccessor {
    @Invoker("getType")
    BlockSetType hardwrought$type();
}
