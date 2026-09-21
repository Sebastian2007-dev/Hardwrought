package de.ipnats.hardwrought.progression;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A workbench hewn out of a standing log rather than joined from boards.
 *
 * <p>It is a crafting table in every way that matters — the same three-by-three grid opens on it —
 * and it looks like what it is: the lower half is still the log it was cut from, the upper half is
 * the worked surface. Section 71 asks for the physical interaction with the world to be believable,
 * and a bench that grows out of the tree it was cut from says more about a first workshop than any
 * recipe could.
 *
 * <p>The wood travels with the block, because a birch that turns into an oak stump when it is worked
 * is exactly the detail that makes a block look pasted into the world.
 */
public class HewnWorkbenchBlock extends CraftingTableBlock {
    public static final EnumProperty<HewnWood> WOOD = EnumProperty.create("wood", HewnWood.class);

    public HewnWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WOOD, HewnWood.OAK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WOOD);
    }

    /** Creates an inventory stack whose model and placed state keep the selected timber. */
    public ItemStack itemStack(HewnWood wood) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(WOOD, wood));
        return stack;
    }

    /** Creative pick-block must keep the same wood instead of silently returning the oak default. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
                                          boolean includeData) {
        return itemStack(state.getValue(WOOD));
    }
}
