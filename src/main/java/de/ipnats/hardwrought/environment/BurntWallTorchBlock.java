package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A torch burnt down to charcoal, on a wall. See {@link BurntLight}. */
public class BurntWallTorchBlock extends WallTorchBlock implements BurntLight {
    public BurntWallTorchBlock(Properties properties) {
        super(ParticleTypes.SMOKE, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // No flame, no smoke: it is cold.
    }

    @Override
    public BlockState relit(BlockState burnt) {
        return Blocks.WALL_TORCH.defaultBlockState().setValue(FACING, burnt.getValue(FACING));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return BurntLight.light(stack, state, level, pos, player, hand);
    }
}
