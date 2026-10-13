package de.ipnats.hardwrought.gasline;

import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A gas tank: a riveted steel vessel holding {@value Entity#CAPACITY} units of one gas, built for
 * {@value Entity#RATING} bar. Gas lines fill and empty it by pressure alone. An empty gas canister held
 * to it draws a canister of what it holds; a full canister empties into it. An empty hand reads it.
 */
public class GasTankBlock extends Block implements EntityBlock {
    public GasTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity tank)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        GasKind filled = GasLines.kindOf(stack);
        boolean empty = stack.is(ModItems.GAS_CANISTER);
        if (filled == null && !empty) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack result;
        if (empty) {
            GasKind kind = tank.store.kind();
            if (kind == null || GasLines.canister(kind) == null || tank.store.amount() < GasLines.CANISTER_UNITS) return InteractionResult.FAIL;
            tank.store.extract(GasLines.CANISTER_UNITS);
            result = new ItemStack(GasLines.canister(kind));
        } else {
            if (tank.store.capacity() - tank.store.amount() < GasLines.CANISTER_UNITS || !tank.store.accepts(filled)) {
                return InteractionResult.FAIL;
            }
            tank.store.insert(filled, GasLines.CANISTER_UNITS);
            result = new ItemStack(ModItems.GAS_CANISTER);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!player.getInventory().add(result) && level instanceof net.minecraft.server.level.ServerLevel server) {
            player.spawnAtLocation(server, result);
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 0.6f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity tank)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(tank);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    public static class Entity extends BlockEntity implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos> {
        public static final int CAPACITY = 256;
        public static final int RATING = 20;

        public final GasStore store = new GasStore(CAPACITY, RATING, true, this::setChanged);

        public Entity(BlockPos pos, BlockState state) {
            super(GasLines.TANK_ENTITY, pos, state);
        }

        /** What the window shows: the gas held, and its pressure in tenths of a bar. */
        de.ipnats.hardwrought.refining.RefineryMenu.Panel panel() {
            GasKind kind = store.kind();
            return new de.ipnats.hardwrought.refining.RefineryMenu.Panel(de.ipnats.hardwrought.refining.RefineryMenu.KIND_GAS_TANK, false, false, 0, (int) Math.round(store.pressure() * 10))
                    .gauge(kind == null ? de.ipnats.hardwrought.refining.RefineryMenu.NOTHING : de.ipnats.hardwrought.refining.RefineryMenu.substance(kind), (long) store.amount(), CAPACITY);
        }

        @Override
        public Component getDisplayName() {
            return getBlockState().getBlock().getName();
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
            return new de.ipnats.hardwrought.refining.RefineryMenu(id, de.ipnats.hardwrought.refining.RefineryMenu.data(this::panel), worldPosition);
        }

        @Override
        public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
            return worldPosition;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            store.save(output, "gas");
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            store.load(input, "gas");
        }
    }
}
