package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.parts.Parts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Section 76: a transformer — the answer to long lines. See {@link TransformerBlockEntity}.
 *
 * <p>Set down, its high side faces away from whoever set it. Coils on the other faces are its low
 * side. Its tank is earthed and can be touched while it works.
 */
public class TransformerBlock extends ElectricNodeBlock {
    /** The face its high side is on, marked by a porcelain bushing. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Its windings are charred. A new winding mends it. */
    public static final BooleanProperty BURNT = BooleanProperty.create("burnt");

    public TransformerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BURNT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BURNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 1.0, 0.5);
    }

    /** A winding mends a burnt one; shears, a coil and a meter go on to their own use. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(BURNT) && stack.is(Parts.WINDING)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.setBlockAndUpdate(pos, state.setValue(BURNT, false));
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.4f);
            if (level instanceof ServerLevel server) Electricity.updateAround(server, pos);
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Earthed: a hand on the tank feels nothing. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(BURNT) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransformerBlockEntity(pos, state);
    }
}
