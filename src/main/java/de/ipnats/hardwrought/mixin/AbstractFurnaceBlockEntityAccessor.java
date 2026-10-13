package de.ipnats.hardwrought.mixin;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * How far along a furnace is.
 *
 * <p>The furnace tells only its own menu, and the overlay that names what a player is looking at
 * wants the same four numbers without anybody having opened it.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
    @Accessor("dataAccess")
    ContainerData hardwrought$data();
}
