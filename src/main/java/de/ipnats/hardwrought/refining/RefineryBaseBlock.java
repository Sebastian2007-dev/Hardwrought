package de.ipnats.hardwrought.refining;

import de.ipnats.hardwrought.chemistry.StillBlockEntity;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.oil.ModFluids;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * The foot of a refinery column. Crude oil is piped into it; over a fire, it boils it, and what boils
 * off rises through the trays stacked on it ({@link RefineryStageBlock}), each catching one cut as it
 * condenses: heavy oil lowest, then diesel, kerosene, naphtha, and the gas at the top. What does not boil
 * at all, bitumen, stays in the foot, and pipes draw it off there.
 *
 * <p>A column with fewer than five trays catches only the cuts it has trays for; what would have
 * condensed higher goes out of the top and is flared off, a flame and smoke over the column.
 */
public class RefineryBaseBlock extends Block implements EntityBlock {
    public static final int MOST_STAGES = 5;
    /** Crude oil boiled in one batch, and how long a batch takes. */
    public static final int BATCH = 100, BATCH_TICKS = 20;
    /** Bitumen left in the foot from each batch. */
    public static final int RESIDUE = 15;
    /** The cuts, from the lowest tray up: of each 100 mB of crude oil. */
    public static final List<RefineryStageBlock.Cut> CUTS = List.of(
            new RefineryStageBlock.Cut("fluid.hardwrought.heavy_oil", RefineryProduct.HEAVY_OIL.source(), 25, 0),
            new RefineryStageBlock.Cut("fluid.hardwrought.diesel", RefineryProduct.DIESEL.source(), 25, 0),
            new RefineryStageBlock.Cut("fluid.hardwrought.kerosene", RefineryProduct.KEROSENE.source(), 10, 0),
            new RefineryStageBlock.Cut("fluid.hardwrought.naphtha", RefineryProduct.NAPHTHA.source(), 20, 0),
            new RefineryStageBlock.Cut("gas.hardwrought.lpg", null, 0, 2));

    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public RefineryBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity entity)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(entity);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.2, pos.getZ() + random.nextDouble(), 0, 0.01, 0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != RefineryBlocks.BASE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    /** How many trays stand on the base at pos, one on the other. */
    public static int stages(Level level, BlockPos base) {
        int stages = 0;
        while (stages < MOST_STAGES && level.getBlockState(base.above(stages + 1)).getBlock() instanceof RefineryStageBlock) stages++;
        return stages;
    }

    public static class Entity extends BlockEntity implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos> {
        public final ProcessTank crude = ProcessTank.inlet(8, ModFluids.CRUDE_OIL, this::setChanged);
        public final ProcessTank bitumen = ProcessTank.outlet(4, RefineryProduct.BITUMEN.source(), this::setChanged);
        private int progress;

        public Entity(BlockPos pos, BlockState state) {
            super(RefineryBlocks.BASE_ENTITY, pos, state);
        }

        /** What pipes see: crude oil goes in, bitumen comes out. */
        public Storage<FluidVariant> storage() {
            return new CombinedStorage<>(List.of(crude, bitumen));
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity base) {
            boolean working = StillBlockEntity.heated(level, pos) && base.canBoil(level, pos);
            if (working) {
                if (++base.progress >= BATCH_TICKS) {
                    base.progress = 0;
                    base.boil((ServerLevel) level, pos);
                }
            } else {
                base.progress = 0;
            }
            if (state.getValue(RUNNING) != working) level.setBlock(pos, state.setValue(RUNNING, working), Block.UPDATE_CLIENTS);
        }

        /** Whether a batch can be boiled: oil enough, at least one tray, and room for every cut it catches. */
        private boolean canBoil(Level level, BlockPos pos) {
            int stages = stages(level, pos);
            if (stages == 0 || crude.millibuckets() < BATCH || !bitumen.room(RESIDUE)) return false;
            for (int tray = 1; tray <= stages; tray++) {
                if (!(level.getBlockEntity(pos.above(tray)) instanceof RefineryStageBlock.Entity stage)) return false;
                RefineryStageBlock.Cut cut = CUTS.get(tray - 1);
                if (cut.fluid() != null && !stage.liquid(tray).room(cut.millibuckets())) return false;
                if (cut.gasUnits() > 0 && stage.gas.capacity() - stage.gas.amount() < cut.gasUnits()) return false;
            }
            return true;
        }

        private void boil(ServerLevel level, BlockPos pos) {
            int stages = stages(level, pos);
            crude.take(BATCH);
            bitumen.fill(RESIDUE);
            for (int tray = 1; tray <= stages; tray++) {
                RefineryStageBlock.Entity stage = (RefineryStageBlock.Entity) level.getBlockEntity(pos.above(tray));
                RefineryStageBlock.Cut cut = CUTS.get(tray - 1);
                if (cut.fluid() != null) stage.liquid(tray).fill(cut.millibuckets());
                if (cut.gasUnits() > 0) stage.gas.produce(GasKind.LPG, cut.gasUnits());
            }
            if (stages < MOST_STAGES) flare(level, pos.above(stages + 1));
        }

        /** What no tray caught goes out of the top and is burnt off there. */
        private static void flare(ServerLevel level, BlockPos top) {
            level.sendParticles(ParticleTypes.FLAME, top.getX() + 0.5, top.getY() + 0.2, top.getZ() + 0.5, 12, 0.15, 0.3, 0.15, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, top.getX() + 0.5, top.getY() + 0.8, top.getZ() + 0.5, 4, 0.2, 0.3, 0.2, 0.02);
            if (level.getRandom().nextInt(4) == 0) {
                level.playSound(null, top, SoundEvents.BLAZE_BURN, SoundSource.BLOCKS, 0.5f, 0.6f);
            }
        }

        /** What the window shows: the trays standing, the crude oil going in and the bitumen left. */
        RefineryMenu.Panel panel() {
            return new RefineryMenu.Panel(RefineryMenu.KIND_BASE, StillBlockEntity.heated(level, worldPosition),
                    getBlockState().getValue(RUNNING), progressPerMille(), stages(level, worldPosition))
                    .liquid(ModFluids.CRUDE_OIL, crude, 8000)
                    .liquid(RefineryProduct.BITUMEN.source(), bitumen, 4000);
        }

        /** How far the batch boiling now is, in thousandths. */
        int progressPerMille() {
            return progress * 1000 / BATCH_TICKS;
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
            crude.save(output, "crude");
            bitumen.save(output, "bitumen");
            output.putInt("progress", progress);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            crude.load(input, "crude");
            bitumen.load(input, "bitumen");
            progress = input.getIntOr("progress", 0);
        }
    }
}
