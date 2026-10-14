package de.ipnats.hardwrought.fluids;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A tank: an iron-bound glass drum holding {@value Entity#BUCKETS} buckets. Tanks that touch are one
 * tank (see {@link TankGroup}): no glass between them, one liquid in all of them, filling from the bottom
 * up. Buckets fill and empty it by hand, fluid pipes on any side; an empty hand reads what is in it.
 */
public class FluidTankBlock extends Block implements EntityBlock {
    public FluidTankBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (Direction side : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    /** Whether the tank at pos is joined to another tank on that side. */
    public static boolean joined(BlockState state, Direction side) {
        return state.hasProperty(PipeBlock.PROPERTY_BY_DIRECTION.get(side)) && state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side),
                    context.getLevel().getBlockState(context.getClickedPos().relative(side)).getBlock() instanceof FluidTankBlock);
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), neighbour.getBlock() instanceof FluidTankBlock);
    }

    /** The whole group of tanks this one is part of, as one store. */
    public static Storage<FluidVariant> storage(Level level, BlockPos pos) {
        return TankGroup.of(level, pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        return FluidStorageUtil.interactWithFluidStorage(storage(level, pos), player, hand)
                ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.sendOverlayMessage(describe(TankGroup.of(level, pos)));
        return InteractionResult.SUCCESS;
    }

    static Component describe(TankGroup group) {
        FluidVariant liquid = group.liquid();
        long capacity = group.capacity() * 1000 / FluidConstants.BUCKET;
        if (liquid.isBlank()) return Component.translatable("message.hardwrought.tank.empty_of", capacity);
        de.ipnats.hardwrought.water.WaterQuality quality = de.ipnats.hardwrought.chemistry.SaltPan.quality(liquid);
        Component name = quality != null && quality != de.ipnats.hardwrought.water.WaterQuality.FRESH
                ? Component.translatable("water_quality.hardwrought." + quality.serializedName())
                : FluidVariantAttributes.getName(liquid);
        return Component.translatable("message.hardwrought.tank.holds", name,
                group.amount() * 1000 / FluidConstants.BUCKET, capacity);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof Entity tank) || tank.storage.amount == 0) return 0;
        return 1 + (int) (14 * tank.storage.amount / Entity.CAPACITY);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != FluidBlocks.TANK_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::settle;
        return ticker;
    }

    public static class Entity extends BlockEntity {
        public static final int BUCKETS = 16;
        public static final long CAPACITY = BUCKETS * FluidConstants.BUCKET;
        /** How much runs down into the tank below, or across to a neighbour, in a tick at most. */
        private static final long FLOW = FluidConstants.BUCKET;

        public final SingleVariantStorage<FluidVariant> storage = new SingleVariantStorage<>() {
            @Override
            protected FluidVariant getBlankVariant() {
                return FluidVariant.blank();
            }

            @Override
            protected long getCapacity(FluidVariant variant) {
                return CAPACITY;
            }

            @Override
            protected void onFinalCommit() {
                changed();
            }
        };

        public Entity(BlockPos pos, BlockState state) {
            super(FluidBlocks.TANK_ENTITY, pos, state);
        }

        /** How full it is, 0 to 1. */
        public float fill() {
            return storage.isResourceBlank() ? 0 : (float) storage.amount / CAPACITY;
        }

        public FluidVariant fluid() {
            return storage.variant;
        }

        /**
         * Liquid settles: down into the tank under this one while it has room, and across into a tank
         * beside it that holds less, until they stand level.
         */
        public static void settle(Level level, BlockPos pos, BlockState state, Entity tank) {
            if (tank.storage.amount <= 0) return;
            if (joined(state, Direction.DOWN) && level.getBlockEntity(pos.below()) instanceof Entity below) {
                tank.pour(below, Math.min(FLOW, CAPACITY - below.storage.amount));
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (tank.storage.amount <= 0) return;
                if (!joined(state, side) || !(level.getBlockEntity(pos.relative(side)) instanceof Entity beside)) continue;
                long difference = tank.storage.amount - beside.storage.amount;
                // Half the difference brings the two level; a droplet or two more is not worth the moving.
                if (difference > 2) tank.pour(beside, Math.min(FLOW, difference / 2));
            }
        }

        private void pour(Entity into, long most) {
            if (most <= 0) return;
            if (!into.storage.isResourceBlank() && into.storage.amount > 0 && !into.storage.variant.equals(storage.variant)) return;
            long moved = Math.min(most, storage.amount);
            if (moved <= 0) return;
            into.storage.variant = storage.variant;
            into.storage.amount += moved;
            storage.amount -= moved;
            if (storage.amount == 0) storage.variant = FluidVariant.blank();
            changed();
            into.changed();
        }

        private void changed() {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            if (!storage.isResourceBlank() && storage.amount > 0) {
                output.store("fluid", FluidVariant.CODEC, storage.variant);
                output.putLong("amount", storage.amount);
            }
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            storage.variant = input.read("fluid", FluidVariant.CODEC).orElse(FluidVariant.blank());
            storage.amount = storage.variant.isBlank() ? 0 : input.getLongOr("amount", 0);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveCustomOnly(registries);
        }
    }
}
