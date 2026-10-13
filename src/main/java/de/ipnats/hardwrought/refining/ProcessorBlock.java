package de.ipnats.hardwrought.refining;

import de.ipnats.hardwrought.chemistry.StillBlockEntity;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStore;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A heated vessel that turns one cut of the column into lighter ones: the cracker breaks heavy oil into
 * diesel and gasoline, the reformer turns naphtha into gasoline. Each stands over a fire, takes its
 * liquid in from a pipe, and gives its products to pipes and the gas it makes to a gas line.
 */
public class ProcessorBlock extends Block implements EntityBlock {
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    /** What a processor does with one batch. */
    public record Process(Supplier<Fluid> input, int inputMillibuckets, List<Output> outputs, double gasUnits, int ticks) { }

    public record Output(Supplier<Fluid> fluid, int millibuckets) { }

    public static final Process CRACKING = new Process(RefineryProduct.HEAVY_OIL::source, 100,
            List.of(new Output(RefineryProduct.DIESEL::source, 45), new Output(RefineryProduct.GASOLINE::source, 35)), 2, 40);
    public static final Process REFORMING = new Process(RefineryProduct.NAPHTHA::source, 100,
            List.of(new Output(RefineryProduct.GASOLINE::source, 85)), 1, 30);

    private final Process process;

    public ProcessorBlock(Process process, Properties properties) {
        super(properties);
        this.process = process;
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    public Process process() {
        return process;
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
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.03, 0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != RefineryBlocks.PROCESSOR_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    public static class Entity extends BlockEntity implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos> {
        static final int BUCKETS = 4;

        private final Process process;
        public final ProcessTank input;
        public final List<ProcessTank> outputs = new ArrayList<>();
        public final GasStore gas = new GasStore(64, 8, false, this::setChanged);
        private int progress;

        public Entity(BlockPos pos, BlockState state) {
            super(RefineryBlocks.PROCESSOR_ENTITY, pos, state);
            process = ((ProcessorBlock) state.getBlock()).process;
            input = ProcessTank.inlet(BUCKETS, process.input().get(), this::setChanged);
            for (Output output : process.outputs()) outputs.add(ProcessTank.outlet(BUCKETS, output.fluid().get(), this::setChanged));
        }

        public Storage<FluidVariant> storage() {
            List<Storage<FluidVariant>> parts = new ArrayList<>();
            parts.add(input);
            parts.addAll(outputs);
            return new CombinedStorage<>(parts);
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity processor) {
            boolean working = StillBlockEntity.heated(level, pos) && processor.canRun();
            if (working) {
                if (++processor.progress >= processor.process.ticks()) {
                    processor.progress = 0;
                    processor.run();
                }
            } else {
                processor.progress = 0;
            }
            if (state.getValue(RUNNING) != working) level.setBlock(pos, state.setValue(RUNNING, working), Block.UPDATE_CLIENTS);
        }

        private boolean canRun() {
            if (input.millibuckets() < process.inputMillibuckets()) return false;
            for (int i = 0; i < outputs.size(); i++) {
                if (!outputs.get(i).room(process.outputs().get(i).millibuckets())) return false;
            }
            return gas.capacity() - gas.amount() >= process.gasUnits();
        }

        private void run() {
            input.take(process.inputMillibuckets());
            for (int i = 0; i < outputs.size(); i++) outputs.get(i).fill(process.outputs().get(i).millibuckets());
            if (process.gasUnits() > 0) gas.produce(GasKind.LPG, process.gasUnits());
        }

        /** What the window shows: the liquid going in, each product coming out, and the gas. */
        RefineryMenu.Panel panel() {
            RefineryMenu.Panel panel = new RefineryMenu.Panel(RefineryMenu.KIND_PROCESSOR,
                    StillBlockEntity.heated(level, worldPosition), getBlockState().getValue(RUNNING),
                    progress * 1000 / process.ticks(), 0);
            panel.liquid(process.input().get(), input, BUCKETS * 1000);
            for (int i = 0; i < outputs.size(); i++) panel.liquid(process.outputs().get(i).fluid().get(), outputs.get(i), BUCKETS * 1000);
            return panel.gauge(RefineryMenu.substance(GasKind.LPG), (long) gas.amount(), (long) gas.capacity());
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
            input.save(output, "input");
            for (int i = 0; i < outputs.size(); i++) outputs.get(i).save(output, "output_" + i);
            gas.save(output, "gas");
            output.putInt("progress", progress);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            this.input.load(input, "input");
            for (int i = 0; i < outputs.size(); i++) outputs.get(i).load(input, "output_" + i);
            gas.load(input, "gas");
            progress = input.getIntOr("progress", 0);
        }
    }
}
