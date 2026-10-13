package de.ipnats.hardwrought.power;

import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.refining.ProcessTank;
import de.ipnats.hardwrought.refining.RefineryMenu;
import de.ipnats.hardwrought.refining.RefineryProduct;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

import java.util.Set;

/**
 * A diesel generator: an engine and a dynamo in one housing, giving the line {@value Entity#VOLTS} V
 * as long as it has fuel, whatever turns or does not turn around it. It is joined to a line through a
 * copper coil set against it, as a dynamo is.
 *
 * <p>Fuel comes in by pipe or bucket: diesel, or, less well, the still's fuel cut or kerosene. It burns
 * a little idling and more the more power is drawn. Its exhaust — carbon dioxide and carbon monoxide —
 * comes out over it, or out of the end of a flue pipe set on it. A redstone signal stops it.
 */
public class DieselGeneratorBlock extends ElectricNodeBlock {
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public DieselGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    public net.minecraft.world.phys.Vec3 terminal(BlockState state) {
        return new net.minecraft.world.phys.Vec3(0.5, 15.0 / 16.0, 0.5);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() && !stack.is(Items.SHEARS) && level.getBlockEntity(pos) instanceof Entity generator
                && FluidStorageUtil.interactWithFluidStorage(generator.fuel, player, hand)) {
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity generator)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(generator);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.04, 0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != PowerBlocks.DIESEL_GENERATOR_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    public static class Entity extends ElectricBlockEntity implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos> {
        public static final double VOLTS = 64.0;
        public static final double OHMS = 0.5;
        public static final int BUCKETS = 4;
        /** Fuel burnt idling, in millibuckets a second, and for each watt-second given. */
        public static final double IDLE_PER_SECOND = 0.25, PER_JOULE = 0.001;

        public final ProcessTank fuel = ProcessTank.inlet(BUCKETS,
                Set.of(RefineryProduct.DIESEL.source(), RefineryProduct.FUEL_FRACTION.source(), RefineryProduct.KEROSENE.source()),
                this::setChanged);
        private boolean running;
        private double burning, exhaust;
        private float watts;

        public Entity(BlockPos pos, BlockState state) {
            super(PowerBlocks.DIESEL_GENERATOR_ENTITY, pos, state);
        }

        /** How far a millibucket of this fuel goes, against diesel. */
        static double worth(Fluid fuel) {
            if (fuel == RefineryProduct.KEROSENE.source()) return 0.9;
            if (fuel == RefineryProduct.FUEL_FRACTION.source()) return 0.75;
            return 1.0;
        }

        public boolean running() {
            return running;
        }

        @Override
        public double emf() {
            return running ? VOLTS : 0.0;
        }

        @Override
        public double sourceOhms() {
            return OHMS;
        }

        @Override
        public boolean canGive() {
            return running;
        }

        @Override
        public boolean takesWires() {
            return false;
        }

        @Override
        public boolean mindsRain() {
            return true;
        }

        @Override
        protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
            super.solved(level, volts, amps, wet, seconds);
            watts = running ? (float) Math.abs(volts * amps) : 0.0f;
            if (running && seconds > 0 && fuel.held() != null) {
                burning += seconds * (IDLE_PER_SECOND + watts * PER_JOULE) / worth(fuel.held());
            }
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity generator) {
            if (!(level instanceof ServerLevel server)) return;
            boolean run = generator.fuel.millibuckets() >= 1 && !level.hasNeighborSignal(pos);
            if (run != generator.running) {
                generator.running = run;
                generator.setChanged();
                level.setBlock(pos, state.setValue(RUNNING, run), Block.UPDATE_CLIENTS);
                Electricity.update(server, pos);
            }
            if (level.getGameTime() % Electricity.PULSE_TICKS == 0) Electricity.pulse(server, pos);
            while (generator.burning >= 1 && generator.fuel.take(1)) {
                generator.burning -= 1;
                generator.exhaust += 1;
            }
            // Every twenty millibuckets burnt go up as a unit of carbon dioxide and a fifth of one of carbon monoxide.
            if (generator.exhaust >= 20) {
                int units = (int) (generator.exhaust / 20);
                generator.exhaust -= units * 20;
                Exhaust.emit(server, pos, units, level.getRandom().nextInt(5) < units ? 1 : 0);
            }
        }

        /** What the window shows: running or not, the power given, and the fuel. */
        RefineryMenu.Panel panel() {
            Fluid held = fuel.held();
            return new RefineryMenu.Panel(RefineryMenu.KIND_DIESEL, running, running, 0, Math.round(watts))
                    .liquid(held == null ? RefineryProduct.DIESEL.source() : held, fuel, BUCKETS * 1000);
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
            fuel.save(output, "fuel");
            output.putBoolean("running", running);
            output.putDouble("burning", burning);
            output.putDouble("exhaust", exhaust);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            fuel.load(input, "fuel");
            running = input.getBooleanOr("running", false);
            burning = input.getDoubleOr("burning", 0);
            exhaust = input.getDoubleOr("exhaust", 0);
        }
    }
}
