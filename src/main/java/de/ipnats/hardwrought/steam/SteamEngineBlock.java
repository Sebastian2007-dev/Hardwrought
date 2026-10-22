package de.ipnats.hardwrought.steam;

import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStore;
import de.ipnats.hardwrought.machinery.GeneratorBlockEntity;
import de.ipnats.hardwrought.machinery.KineticBlock;
import de.ipnats.hardwrought.machinery.Kinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A steam engine: a cylinder whose piston drives a crank and a flywheel, and through it a shaft. It is a
 * source for a driveline, as a water wheel is, but it runs on whatever steam a gas line brings it.
 *
 * <p>It turns at {@value Entity#RPM} turns a minute. Its strength follows the pressure at its inlet:
 * from {@value Entity#MIN_BAR} bar it turns over, at {@value Entity#FULL_BAR} bar and above it gives its
 * full {@value Entity#CAPACITY}. A governor on it lets in only the steam the load on its line needs, so
 * an engine running light uses little; one stalled by too heavy a line uses none. Its exhaust puffs out
 * into the air. The axle leaves by the back; a redstone signal shuts its throttle.
 */
public class SteamEngineBlock extends Block implements EntityBlock, KineticBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public SteamEngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUNNING);
    }

    /** Front to the player, axle away from them, into whatever they set it against. */
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

    /** An empty hand reads its inlet pressure and how hard it is working. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity engine)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.steam_engine.reading",
                    String.format(java.util.Locale.ROOT, "%.1f", engine.inlet.pressure()), (int) Math.abs(engine.kineticSpeed()),
                    (int) Math.round(engine.load * 100)));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 0.03, 0.08, (random.nextDouble() - 0.5) * 0.03);
        }
        if (random.nextInt(5) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PISTON_EXTEND,
                    SoundSource.BLOCKS, 0.15f, 0.6f, false);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != SteamBlocks.STEAM_ENGINE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    public static class Entity extends GeneratorBlockEntity {
        public static final float RPM = 32.0f, CAPACITY = 3072.0f;
        /** The pressure it turns over at, and the one it gives its full strength from. */
        public static final double MIN_BAR = 1.5, FULL_BAR = 8.0;
        /** Steam it takes a second running light, and running at its full strength, in units. */
        public static final double IDLE = 0.2, PER_SECOND = 3.0;
        /** Its steam chest: so many units, standing at the boiler's scale of pressure when full. */
        public static final double CHEST = 20, CHEST_BAR = 20;
        /** Strength comes in so many steps between turning over and full, so a line is not worked out on every puff. */
        private static final int STEPS = 12;

        public final GasStore inlet = new GasStore(CHEST, CHEST_BAR, true, kind -> kind == GasKind.STEAM, this::setChanged);
        /** What share of its full strength the line is taking, as the governor last found it. */
        private double load;

        public Entity(BlockPos pos, BlockState state) {
            super(SteamBlocks.STEAM_ENGINE_ENTITY, pos, state);
        }

        public double load() {
            return load;
        }

        @Override
        protected int interval() {
            return 10;
        }

        @Override
        protected float[] measure(ServerLevel level, BlockPos pos, BlockState state) {
            double bar = inlet.pressure();
            if (bar < MIN_BAR || level.hasNeighborSignal(pos)) return new float[] {0.0f, 0.0f};
            double share = Math.min(1.0, bar / FULL_BAR);
            float strength = (float) (Math.ceil(share * STEPS) / STEPS * CAPACITY);
            return new float[] {RPM, strength};
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity engine) {
            if (!(level instanceof ServerLevel server)) return;
            boolean running = engine.output() != 0.0f;
            if (running && (level.getGameTime() + pos.asLong()) % 20 == 0) engine.govern(server, pos);
            if (running) engine.inlet.extract((IDLE + PER_SECOND * engine.load) / 20.0);
            else engine.load = 0;
            if (state.getValue(RUNNING) != running) level.setBlock(pos, state.setValue(RUNNING, running), Block.UPDATE_CLIENTS);
            GeneratorBlockEntity.serverTick(level, pos, level.getBlockState(pos), engine);
        }

        /** The governor: how much of what the line takes falls to this engine, as a share of its full strength. */
        private void govern(ServerLevel level, BlockPos pos) {
            Kinetics.Network line = Kinetics.solve(level, pos);
            if (line == null || line.status() != Kinetics.Status.RUNNING || line.capacity() <= 0) {
                load = 0;
                return;
            }
            double mine = line.stress() * capacity() / line.capacity();
            load = Math.min(1.0, mine / CAPACITY);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            inlet.save(output, "inlet");
            output.putDouble("load", load);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            inlet.load(input, "inlet");
            load = input.getDoubleOr("load", 0);
        }
    }
}
