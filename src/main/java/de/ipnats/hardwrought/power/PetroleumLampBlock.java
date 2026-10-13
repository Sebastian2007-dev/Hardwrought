package de.ipnats.hardwrought.power;

import de.ipnats.hardwrought.refining.RefineryProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A petroleum lamp: a lantern that burns kerosene instead of tallow, and burns long — a bucket keeps
 * it going ten days, where a torch is out in one. It is filled with a bucket of kerosene and turned up
 * and down with an empty hand. With its tank dry it goes out, and lights again when filled.
 */
public class PetroleumLampBlock extends Block implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 9, 11);

    public PetroleumLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(RefineryProduct.KEROSENE.bucket()) || !(level.getBlockEntity(pos) instanceof Entity lamp)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (lamp.fuel > Entity.TANK - 1000) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        lamp.fuel += 1000;
        lamp.setChanged();
        if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity lamp)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        boolean lit = !state.getValue(LIT) && lamp.fuel > 0;
        level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        player.sendOverlayMessage(Component.translatable("message.hardwrought.petroleum_lamp", lamp.fuel,
                String.format(java.util.Locale.ROOT, "%.1f", lamp.fuel / (double) Entity.PER_DAY)));
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.4f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != PowerBlocks.PETROLEUM_LAMP_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    public static class Entity extends BlockEntity {
        /** Kerosene it holds, and burns in a day, in millibuckets: a bucket is ten days. */
        public static final int TANK = 2000, PER_DAY = 100;
        private static final int TICKS_PER_MILLIBUCKET = 24_000 / PER_DAY;

        int fuel;
        private int clock;

        public Entity(BlockPos pos, BlockState state) {
            super(PowerBlocks.PETROLEUM_LAMP_ENTITY, pos, state);
        }

        public int fuel() {
            return fuel;
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity lamp) {
            if (!state.getValue(LIT)) return;
            if (++lamp.clock < TICKS_PER_MILLIBUCKET) return;
            lamp.clock = 0;
            lamp.fuel = Math.max(0, lamp.fuel - 1);
            lamp.setChanged();
            if (lamp.fuel == 0) level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("fuel", fuel);
            output.putInt("clock", clock);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            fuel = input.getIntOr("fuel", 0);
            clock = input.getIntOr("clock", 0);
        }
    }
}
