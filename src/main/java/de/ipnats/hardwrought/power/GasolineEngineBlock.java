package de.ipnats.hardwrought.power;

import de.ipnats.hardwrought.machinery.GeneratorBlockEntity;
import de.ipnats.hardwrought.machinery.KineticBlock;
import de.ipnats.hardwrought.machinery.Kinetics;
import de.ipnats.hardwrought.parts.Parts;
import de.ipnats.hardwrought.refining.ProcessTank;
import de.ipnats.hardwrought.refining.RefineryMenu;
import de.ipnats.hardwrought.refining.RefineryProduct;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Set;

/**
 * A gasoline engine: a source for a driveline, as a water wheel is, but one that runs anywhere and as
 * long as it has fuel and a spark. Its axle leaves by the back, into the shaft it is set against; it
 * drives at {@value Entity#RPM} turns a minute with a strength of {@value Entity#CAPACITY}.
 *
 * <p>Gasoline comes in by pipe or bucket; the still's light spirit runs it too, less well. It needs a
 * spark plug, which wears as it runs and is changed by holding a new one to it. Its exhaust goes out
 * over it or up a flue pipe on it. A redstone signal stops it.
 */
public class GasolineEngineBlock extends Block implements EntityBlock, KineticBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public GasolineEngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUNNING);
    }

    /** Front to the player, axle away from them — into whatever they set it against. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public float port(BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite() ? 1.0f : 0.0f;
    }

    @Override
    public float drive(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof Entity engine ? engine.output() : 0.0f;
    }

    @Override
    public float capacity(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof Entity engine ? engine.capacity() : 0.0f;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity engine)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(Parts.SPARK_PLUG)) {
            if (engine.plug > Entity.PLUG_LIFE / 4) return InteractionResult.FAIL;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            engine.plug = Entity.PLUG_LIFE;
            engine.setChanged();
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
            return InteractionResult.SUCCESS;
        }
        if (!stack.isEmpty() && FluidStorageUtil.interactWithFluidStorage(engine.fuel, player, hand)) return InteractionResult.SUCCESS;
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity engine)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(engine);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.05, 0);
        if (random.nextInt(8) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.MINECART_RIDING,
                    SoundSource.BLOCKS, 0.15f, 1.8f, false);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != PowerBlocks.GASOLINE_ENGINE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    public static class Entity extends GeneratorBlockEntity implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos> {
        public static final float RPM = 48.0f, CAPACITY = 6144.0f;
        public static final int BUCKETS = 4;
        /** Gasoline burnt in a second of running, in millibuckets: a bucket lasts eight minutes. */
        public static final double PER_SECOND = 2.0;
        /** Ticks a spark plug lasts running: an hour of play. */
        public static final int PLUG_LIFE = 72_000;

        public final ProcessTank fuel = ProcessTank.inlet(BUCKETS,
                Set.of(RefineryProduct.GASOLINE.source(), RefineryProduct.LIGHT_FRACTION.source()), this::setChanged);
        int plug;
        private boolean running;
        private double burning, exhaust;

        public Entity(BlockPos pos, BlockState state) {
            super(PowerBlocks.GASOLINE_ENGINE_ENTITY, pos, state);
        }

        static double worth(Fluid fuel) {
            return fuel == RefineryProduct.LIGHT_FRACTION.source() ? 0.7 : 1.0;
        }

        public boolean running() {
            return running;
        }

        /** How much of its spark plug is left, in hundredths; -1 for none in it. */
        public int plugLeft() {
            return plug <= 0 ? -1 : (int) Math.ceil(plug * 100.0 / PLUG_LIFE);
        }

        public void setPlugForTesting(int ticks) {
            plug = ticks;
        }

        @Override
        protected int interval() {
            return 10;
        }

        @Override
        protected float[] measure(ServerLevel level, BlockPos pos, BlockState state) {
            return running ? new float[] {RPM, CAPACITY} : new float[] {0.0f, 0.0f};
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity engine) {
            if (!(level instanceof ServerLevel server)) return;
            boolean run = engine.fuel.millibuckets() >= 1 && engine.plug > 0 && !level.hasNeighborSignal(pos);
            if (run != engine.running) {
                engine.running = run;
                engine.setChanged();
                level.setBlock(pos, state.setValue(RUNNING, run), Block.UPDATE_CLIENTS);
                engine.remeasure(server, pos, level.getBlockState(pos));
            }
            if (run) {
                engine.plug--;
                engine.burning += PER_SECOND / 20.0 / worth(engine.fuel.held());
                while (engine.burning >= 1 && engine.fuel.take(1)) {
                    engine.burning -= 1;
                    engine.exhaust += 1;
                }
                if (engine.exhaust >= 10) {
                    int units = (int) (engine.exhaust / 10);
                    engine.exhaust -= units * 10;
                    // A gasoline engine runs rich: more carbon monoxide than a diesel.
                    Exhaust.emit(server, pos, units, level.getRandom().nextInt(3) < units ? 1 : 0);
                }
                engine.setChanged();
            }
            GeneratorBlockEntity.serverTick(level, pos, level.getBlockState(pos), engine);
        }

        /** What the window shows: running or not, the spark plug, and the fuel. */
        RefineryMenu.Panel panel() {
            Fluid held = fuel.held();
            return new RefineryMenu.Panel(RefineryMenu.KIND_ENGINE, running, running, 0, plugLeft())
                    .liquid(held == null ? RefineryProduct.GASOLINE.source() : held, fuel, BUCKETS * 1000);
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
            output.putInt("plug", plug);
            output.putBoolean("running", running);
            output.putDouble("burning", burning);
            output.putDouble("exhaust", exhaust);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            fuel.load(input, "fuel");
            plug = input.getIntOr("plug", 0);
            running = input.getBooleanOr("running", false);
            burning = input.getDoubleOr("burning", 0);
            exhaust = input.getDoubleOr("exhaust", 0);
        }
    }
}
