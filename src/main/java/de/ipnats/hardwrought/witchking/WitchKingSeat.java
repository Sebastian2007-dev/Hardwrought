package de.ipnats.hardwrought.witchking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Witch King's seat: the throne's counterpart in the overworld, set down the first time someone sits
 * on the cold throne. Sitting on it carries one down to the throne; sneaking with empty hands picks it up,
 * still bound to its throne, to set down somewhere else. It cannot be broken.
 */
public class WitchKingSeat extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        VoxelShape seat = Block.box(1, 0, 1, 15, 8, 15);
        SHAPES.put(Direction.SOUTH, Shapes.or(seat, Block.box(1, 8, 1, 15, 20, 4)));
        SHAPES.put(Direction.NORTH, Shapes.or(seat, Block.box(1, 8, 12, 15, 20, 15)));
        SHAPES.put(Direction.EAST, Shapes.or(seat, Block.box(1, 8, 1, 4, 20, 15)));
        SHAPES.put(Direction.WEST, Shapes.or(seat, Block.box(12, 8, 1, 15, 20, 15)));
    }

    public WitchKingSeat(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        BlockPos throne = stack.get(WitchKingContent.THRONE_LINK);
        if (throne == null || !(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof Entity seat)) return;
        seat.bind(throne);
        ThronePassage.link(server.getServer(), throne, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (player.isSecondaryUseActive()) {
            // Picked up, still bound to its throne.
            ItemStack item = new ItemStack(WitchKingContent.SEAT_ITEM);
            if (level.getBlockEntity(pos) instanceof Entity seat && seat.throne() != null) item.set(WitchKingContent.THRONE_LINK, seat.throne());
            level.removeBlock(pos, false);
            if (!player.getInventory().add(item)) player.spawnAtLocation(server, item);
            level.playSound(null, pos, SoundEvents.POLISHED_DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
            return InteractionResult.SUCCESS;
        }
        SeatMount.sit(server, pos, state.getValue(FACING).toYRot(), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.6,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01, 0);
        }
    }

    public static class Entity extends BlockEntity {
        private @Nullable BlockPos throne;

        public Entity(BlockPos pos, BlockState state) {
            super(WitchKingContent.SEAT_ENTITY, pos, state);
        }

        /** The throne in the Underworld this seat leads down to, or null if it is bound to none. */
        public @Nullable BlockPos throne() {
            return throne;
        }

        public void bind(BlockPos throne) {
            this.throne = throne;
            setChanged();
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            if (throne != null) output.store("throne", BlockPos.CODEC, throne);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            throne = input.read("throne", BlockPos.CODEC).orElse(null);
        }
    }
}
