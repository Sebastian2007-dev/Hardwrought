package de.ipnats.hardwrought.mixin;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Which block a crafting grid was opened on.
 *
 * <p>The menu keeps it to itself, and the tier rule cannot be applied without it: a hewn bench and a
 * joined crafting table open the very same menu class, so the only thing that tells them apart is
 * the block the menu is anchored to.
 */
@Mixin(CraftingMenu.class)
public interface CraftingMenuAccessor {
    @Accessor("access")
    ContainerLevelAccess hardwrought$access();
}
