package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * What every point of a network does when a hand comes near it: shears take its wires off, and a
 * bare hand on it while it is live finds out that it is.
 */
public abstract class ElectricNodeBlock extends Block implements EntityBlock, ElectricBlock {
    protected ElectricNodeBlock(Properties properties) {
        super(properties);
    }

    /** Set down beside a coil, or as one against a machine, it joins what it now touches. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (level instanceof ServerLevel server && !previous.is(this)) Electricity.update(server, pos);
    }

    /** Gone, whatever it was joined to through a coil is a network of its own again. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) Electricity.update(level, pos.relative(side));
    }

    /** Shears cut the wires. Anything else in the hand goes on to its own use — a coil of wire, a meter. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!stack.is(Items.SHEARS)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        // Cutting a live line with steel in the hand is touching it.
        if (level.getBlockEntity(pos) instanceof ElectricBlockEntity node) {
            Electricity.shock(server, player, Math.abs(node.volts()));
        }
        return Electricity.cut(server, pos, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;
        if (level.getBlockEntity(pos) instanceof ElectricBlockEntity node
                && Electricity.shock(server, player, Math.abs(node.volts()))) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
