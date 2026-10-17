package de.ipnats.hardwrought.ritual;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One of the eight pedestals round the altar in a temple's ritual chamber. Each wants one offering (see
 * {@link RitualContent#OFFERINGS}): it shows it hanging over it as a ghost until it is laid there, and then
 * whole, slowly turning (drawn by the client, {@code RitualPedestalRenderer}). What lies on it can be taken
 * back until the ritual has begun.
 *
 * <p>An offering held to a pedestal is only ever laid there, never used: a bucket of diesel is not poured
 * out over it, a wand not cast. The client knows what the pedestal wants (the block entity is synced), so
 * it takes the click as the server will.
 */
public class RitualPedestal extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(4, 4, 4, 12, 12, 12), Block.box(2, 12, 2, 14, 15, 14));

    public RitualPedestal(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, RitualContent.PEDESTAL_ENTITY, (l, pos, s, pedestal) -> pedestal.tick((ServerLevel) l));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity pedestal) || !RitualContent.isOffering(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        // Any offering held to a pedestal is taken as an offering, right or wrong: never used on it.
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (pedestal.index < 0) {
            if (pedestal.index == -1) pedestal.bind(server);
            if (pedestal.index < 0) return InteractionResult.FAIL;
        }
        if (Ritual.running(server, pedestal.altar())) return InteractionResult.FAIL;
        if (!stack.is(RitualContent.offering(pedestal.index))) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.ritual.wants",
                    RitualContent.offering(pedestal.index).getDefaultInstance().getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            return InteractionResult.FAIL;
        }
        if (!pedestal.held.isEmpty()) return InteractionResult.FAIL;
        pedestal.lay(server, stack.consumeAndReturn(1, player));
        server.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.2f, 0.8f);
        server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.7f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity pedestal)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return pedestal.index >= 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (pedestal.index < 0) return InteractionResult.PASS;
        if (pedestal.held.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.ritual.wants",
                    RitualContent.offering(pedestal.index).getDefaultInstance().getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            return InteractionResult.SUCCESS;
        }
        if (Ritual.running(server, pedestal.altar())) return InteractionResult.FAIL;
        ItemStack back = pedestal.take(server);
        if (!player.getInventory().add(back)) player.spawnAtLocation(server, back);
        return InteractionResult.SUCCESS;
    }

    /** What lies on a pedestal, which offering it wants, and where its altar is. */
    public static class Entity extends BlockEntity {
        /** The offering it wants; -1 while it stands round no altar, -2 once its offering has burnt up. */
        int index = -1;
        BlockPos altarOffset = BlockPos.ZERO;
        ItemStack held = ItemStack.EMPTY;
        /** Whether what an older version left over it (an item display) has been cleared away. */
        boolean cleared;

        public Entity(BlockPos pos, BlockState state) {
            super(RitualContent.PEDESTAL_ENTITY, pos, state);
        }

        /**
         * Finds the altar it stands round, and from where it stands which offering it wants: the pedestal
         * at each of the eight places round an altar wants its own.
         */
        void bind(ServerLevel level) {
            BlockPos here = getBlockPos();
            for (BlockPos at : BlockPos.betweenClosed(here.offset(-8, -1, -8), here.offset(8, 3, 8))) {
                if (!level.getBlockState(at).is(RitualContent.ALTAR)) continue;
                int found = Temple.pedestals(at).indexOf(here);
                if (found < 0) continue;
                index = found;
                altarOffset = at.subtract(here);
                sync();
                return;
            }
        }

        public int index() {
            return index;
        }

        /** What lies on it. */
        public ItemStack held() {
            return held;
        }

        /** What it wants and has not got: shown over it as a ghost. */
        public ItemStack wanted() {
            return index >= 0 && held.isEmpty() ? RitualContent.offering(index).getDefaultInstance() : ItemStack.EMPTY;
        }

        public boolean filled() {
            return index >= 0 && held.is(RitualContent.offering(index));
        }

        BlockPos altar() {
            return getBlockPos().offset(altarOffset);
        }

        void lay(ServerLevel level, ItemStack stack) {
            held = stack.copyWithCount(1);
            sync();
        }

        ItemStack take(ServerLevel level) {
            ItemStack back = held;
            held = ItemStack.EMPTY;
            sync();
            return back;
        }

        /** Burnt up in the ritual: gone, and nothing shown. */
        void consume(ServerLevel level) {
            held = ItemStack.EMPTY;
            index = -2;
            sync();
        }

        /** Saved, and what the clients round it see brought up to date. */
        private void sync() {
            setChanged();
            if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }

        void tick(ServerLevel level) {
            if (!cleared) {
                cleared = true;
                Showcase.clear(level, getBlockPos());
            }
            if (index == -1 && level.getGameTime() % 20 == Math.floorMod(getBlockPos().asLong(), 20)) bind(level);
            if (index >= 0 && !level.getBlockState(altar()).is(RitualContent.ALTAR)) {
                // Its altar is gone: it wants nothing until it stands round one again.
                index = -1;
                sync();
            }
        }

        /** Binds it to its altar and lays this on it, if it is what it wants; for tests. */
        public boolean offerForTesting(ServerLevel level, ItemStack stack) {
            if (index < 0) bind(level);
            if (index < 0 || !stack.is(RitualContent.offering(index))) return false;
            lay(level, stack);
            return true;
        }

        /** Broken: what lay on it falls where it stood. */
        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState state) {
            if (level != null && !held.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, held);
                held = ItemStack.EMPTY;
            }
            super.preRemoveSideEffects(pos, state);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("index", index);
            output.putLong("altar", altarOffset.asLong());
            if (!held.isEmpty()) output.store("held", ItemStack.CODEC, held);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            index = input.getIntOr("index", -1);
            altarOffset = BlockPos.of(input.getLongOr("altar", 0));
            held = input.read("held", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveWithoutMetadata(registries);
        }
    }
}
