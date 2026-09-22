package de.ipnats.hardwrought.metallurgy;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The first furnace a player can build, and a poor one.
 *
 * <p>It exists to close a chain that was otherwise knotted: a crafting table needs an iron hatchet,
 * iron needs a furnace, and a vanilla furnace needs a three-by-three grid — which needs a crafting
 * table. Four fired bricks in the inventory square break that circle.
 *
 * <p>Being poor is the price. It smelts at half the speed of a stone furnace and burns through fuel
 * half again as fast, so it is something a player replaces rather than keeps.
 */
public class BrickFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
    /** Half the speed of a proper furnace: the recipe takes twice as long. */
    public static final float SPEED = 0.5f;
    /** And the same fuel does two thirds of the work it would do in one. */
    public static final float FUEL_EFFICIENCY = 0.67f;

    public BrickFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BRICK_FURNACE, pos, state, RecipeType.SMELTING);
    }

    @Override
    protected float getSpeedMultiplier(ServerLevel level, ItemStack fuel) {
        return super.getSpeedMultiplier(level, fuel) * SPEED;
    }

    @Override
    protected int getBurnDuration(ServerLevel level, ItemStack fuel) {
        return Math.round(super.getBurnDuration(level, fuel) * FUEL_EFFICIENCY);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.hardwrought.brick_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new FurnaceMenu(id, inventory, this, this.dataAccess);
    }
}
