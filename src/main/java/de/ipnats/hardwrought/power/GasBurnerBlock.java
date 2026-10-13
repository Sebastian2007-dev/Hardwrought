package de.ipnats.hardwrought.power;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasLines;
import de.ipnats.hardwrought.gasline.GasStore;
import de.ipnats.hardwrought.refining.RefineryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/**
 * A gas burner: a clean, steady fire for whatever stands on it — a still, a refinery base, a cracker —
 * with no wood to feed and no ash. It burns natural gas or LPG, from a gas line or from a canister
 * held to it, and lights itself on its spark plug as long as there is gas. A redstone signal turns it
 * off. Its flame is an open flame: firedamp goes off at it.
 */
public class GasBurnerBlock extends Block implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public GasBurnerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        GasKind kind = GasLines.kindOf(stack);
        if (kind == null || !(level.getBlockEntity(pos) instanceof Entity burner)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (burner.intake.capacity() - burner.intake.amount() < GasLines.CANISTER_UNITS || !burner.intake.accepts(kind)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        burner.intake.insert(kind, GasLines.CANISTER_UNITS);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ItemStack empty = new ItemStack(ModItems.GAS_CANISTER);
        if (!player.getInventory().add(empty) && level instanceof ServerLevel server) player.spawnAtLocation(server, empty);
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8f, 0.6f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity burner)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(burner);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        for (int i = 0; i < 2; i++) {
            level.addParticle(random.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + 1.0, pos.getZ() + 0.25 + random.nextDouble() * 0.5,
                    0, 0.01, 0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != PowerBlocks.GAS_BURNER_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    public static class Entity extends BlockEntity implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos> {
        /** Gas burnt in a second: a canister lasts ten minutes, as it does in a furnace. */
        public static final double PER_SECOND = 16.0 / 600.0;

        public final GasStore intake = new GasStore(64, 2, true, GasKind::burns, this::setChanged);
        private double burnt;

        public Entity(BlockPos pos, BlockState state) {
            super(PowerBlocks.GAS_BURNER_ENTITY, pos, state);
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity burner) {
            boolean lit = burner.intake.amount() > 0 && !level.hasNeighborSignal(pos);
            if (lit != state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
            if (!lit || level.getGameTime() % 20 != 0) return;
            double taken = burner.intake.extract(PER_SECOND);
            burner.burnt += taken;
            // Every four units burnt go up as one of carbon dioxide; a clean flame makes no carbon monoxide.
            if (burner.burnt >= 4 && level instanceof ServerLevel server) {
                burner.burnt -= 4;
                Exhaust.emit(server, pos, 1, 0);
            }
            burner.setChanged();
        }

        RefineryMenu.Panel panel() {
            boolean lit = getBlockState().getValue(LIT);
            GasKind kind = intake.kind();
            return new RefineryMenu.Panel(RefineryMenu.KIND_BURNER, lit, lit, 0, 0)
                    .gauge(kind == null ? RefineryMenu.NOTHING : RefineryMenu.substance(kind), (long) intake.amount(), (long) intake.capacity());
        }

        @Override
        public Component getDisplayName() {
            return getBlockState().getBlock().getName();
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
            return new RefineryMenu(id, RefineryMenu.data(this::panel), worldPosition);
        }

        @Override
        public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
            return worldPosition;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            intake.save(output, "gas");
            output.putDouble("burnt", burnt);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            intake.load(input, "gas");
            burnt = input.getDoubleOr("burnt", 0);
        }
    }
}
