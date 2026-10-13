package de.ipnats.hardwrought.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A creaking heart grown into a tree that is not pale oak. It is vanilla's heart in every way —
 * the same block entity calls and protects its creaking — except that it wears the bark of its
 * {@link #WOOD} and needs logs of that wood above and below it, where vanilla's needs pale oak.
 * The log check itself is swapped in by {@code CreakingHeartBlockMixin}, since vanilla calls it
 * statically from the block entity.
 *
 * <p>It has no item of its own: broken with silk touch it gives vanilla's heart, which takes on the
 * bark again wherever it is set back between logs.
 */
public class TreeHeartBlock extends CreakingHeartBlock {
    public static final EnumProperty<HeartWood> WOOD = EnumProperty.create("wood", HeartWood.class);

    public TreeHeartBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WOOD, HeartWood.OAK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WOOD);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.CREAKING_HEART);
    }
}
