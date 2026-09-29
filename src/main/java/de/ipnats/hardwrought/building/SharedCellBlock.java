package de.ipnats.hardwrought.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * One cell holding two blocks; see {@link SharedCell}. Everything about it — shape, drops, doors that
 * open, panes that join their neighbours — is the two parts' own behaviour, asked of them in turn.
 * It draws nothing itself: {@code SharedCellRenderer} draws both parts.
 */
public class SharedCellBlock extends Block implements EntityBlock {
    /** Whether the thin part is a pane or bars, which neighbouring panes join onto. */
    public static final BooleanProperty CONNECTS = BooleanProperty.create("connects");

    public SharedCellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CONNECTS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SharedCellBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    private static SharedCellBlockEntity entity(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SharedCellBlockEntity entity && !entity.host().isAir() ? entity : null;
    }

    /** The thin part's shape where it is drawn: a carpet lifted onto the host's floor. */
    private static VoxelShape insertShape(BlockState host, BlockState insert, VoxelShape shape) {
        if (!(insert.getBlock() instanceof CarpetBlock)) return shape;
        double floor = Math.max(0, SharedCell.carpetFloor(host));
        return shape.move(0, floor, 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        SharedCellBlockEntity entity = entity(level, pos);
        if (entity == null) return Shapes.block();
        return Shapes.or(entity.host().getShape(level, pos, context),
                insertShape(entity.host(), entity.insert(), entity.insert().getShape(level, pos, context)));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        SharedCellBlockEntity entity = entity(level, pos);
        if (entity == null) return Shapes.block();
        return Shapes.or(entity.host().getCollisionShape(level, pos, context),
                insertShape(entity.host(), entity.insert(), entity.insert().getCollisionShape(level, pos, context)));
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Mining takes the thin part out first, at the pace of the thin part.
        SharedCellBlockEntity entity = entity(level, pos);
        return entity == null ? super.getDestroyProgress(state, player, level, pos)
                : entity.insert().getDestroyProgress(player, level, pos);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        SharedCellBlockEntity entity = entity(level, pos);
        return entity == null ? ItemStack.EMPTY : new ItemStack(entity.insert().getBlock());
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        // Taken apart by anything but a player's hand — an explosion — both parts drop.
        if (!(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SharedCellBlockEntity entity)) {
            return List.of();
        }
        List<ItemStack> drops = new ArrayList<>(entity.host().getDrops(builder));
        drops.addAll(entity.insert().getDrops(builder));
        return drops;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        SharedCellBlockEntity entity = entity(level, pos);
        if (entity == null) return state;
        BlockState host = entity.host().updateShape(level, ticks, pos, direction, neighborPos, neighborState, random);
        BlockState insert = entity.insert();
        // A lifted carpet lies on the host, not on the block below; only a carpet on the floor asks it.
        if (!(insert.getBlock() instanceof CarpetBlock) || SharedCell.carpetFloor(host) <= 0) {
            insert = insert.updateShape(level, ticks, pos, direction, neighborPos, neighborState, random);
        }
        // Whichever part can no longer be there goes, and the other is left on its own.
        if (host.isAir()) return insert;
        if (insert.isAir()) return host;
        entity.set(host, insert);
        return state;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        SharedCellBlockEntity entity = entity(level, pos);
        if (entity == null) return InteractionResult.PASS;
        BlockSetType type = openableType(entity.host());
        if (type == null || !type.canOpenByHand()) return InteractionResult.PASS;
        setOpen(level, pos, entity, !entity.host().getValue(BlockStateProperties.OPEN), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moved) {
        SharedCellBlockEntity entity = entity(level, pos);
        if (entity == null || level.isClientSide() || openableType(entity.host()) == null) return;
        BlockState host = entity.host();
        boolean powered = level.hasNeighborSignal(pos);
        if (host.getBlock() instanceof DoorBlock) powered |= level.hasNeighborSignal(otherHalf(host, pos));
        if (powered == host.getValue(BlockStateProperties.POWERED)) return;
        entity.set(host.setValue(BlockStateProperties.POWERED, powered), entity.insert());
        if (powered != host.getValue(BlockStateProperties.OPEN)) setOpen(level, pos, entity, powered, null);
    }

    /** The door or trapdoor kind of the host, or null for a host that does not open. */
    private static BlockSetType openableType(BlockState host) {
        if (host.getBlock() instanceof DoorBlock door) return door.type();
        if (host.getBlock() instanceof TrapDoorBlock) return trapdoorType(host);
        return null;
    }

    private static BlockSetType trapdoorType(BlockState host) {
        // The trapdoor's own type is not public; its sounds are the same as those of its door.
        return ((de.ipnats.hardwrought.mixin.TrapDoorBlockAccessor) host.getBlock()).hardwrought$type();
    }

    private static BlockPos otherHalf(BlockState door, BlockPos pos) {
        return door.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
    }

    /**
     * Opens or shuts the host. A door's other half is told through the usual neighbour update, the
     * way vanilla keeps the two halves of a door together.
     */
    private static void setOpen(Level level, BlockPos pos, SharedCellBlockEntity entity, boolean open, Player player) {
        BlockState host = entity.host().setValue(BlockStateProperties.OPEN, open);
        entity.set(host, entity.insert());
        BlockSetType type = openableType(host);
        boolean door = host.getBlock() instanceof DoorBlock;
        level.playSound(player, pos, door ? (open ? type.doorOpen() : type.doorClose())
                        : (open ? type.trapdoorOpen() : type.trapdoorClose()),
                SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.1f + 0.9f);
        if (door) level.getBlockState(pos).updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
    }
}
