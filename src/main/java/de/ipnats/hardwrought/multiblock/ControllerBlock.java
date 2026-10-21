package de.ipnats.hardwrought.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The controller of a big machine: a block in the middle of one side of a cubic hull, its face out —
 * 3×3×3 for most, 5×5×5 for the reactor and the battery bank. It checks the hull behind it every second; while the hull is
 * whole it is <i>formed</i>, and the ports set in the hull serve it. Opened with an empty hand, it shows
 * the machine, or, unformed, what is wrong.
 */
public class ControllerBlock extends Block implements EntityBlock {
    public enum Machine { ARC_FURNACE, LARGE_DIESEL, REACTOR, TURBINE, CENTRIFUGE, BATTERY_BANK }

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    private final Machine machine;

    public ControllerBlock(Machine machine, Properties properties) {
        super(properties);
        this.machine = machine;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, RUNNING);
    }

    /** Its face to the player: the hull is built behind it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity controller)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        controller.check((ServerLevel) level);
        if (controller.formed() && controller instanceof MenuProvider menu) player.openMenu(menu);
        else player.sendOverlayMessage(Component.translatable("message.hardwrought.multiblock.unformed", controller.problem()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (machine) {
            case ARC_FURNACE -> new ArcFurnaceController(pos, state);
            case LARGE_DIESEL -> new LargeDieselController(pos, state);
            case REACTOR -> new de.ipnats.hardwrought.nuclear.ReactorController(pos, state);
            case TURBINE -> new de.ipnats.hardwrought.nuclear.TurbineController(pos, state);
            case CENTRIFUGE -> new de.ipnats.hardwrought.nuclear.CentrifugeController(pos, state);
            case BATTERY_BANK -> new BatteryBankController(pos, state);
        };
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof Entity controller) controller.tick((ServerLevel) tickLevel, pos, tickState);
        };
    }

    /**
     * What every controller has in common: checking its hull, the ports found in it, and whether it stands.
     */
    public abstract static class Entity extends BlockEntity {
        private boolean formed;
        private final List<BlockPos> ports = new ArrayList<>();
        private Component problem = Component.empty();
        /** Whether the hull has been looked over since the controller was loaded: the first tick does it. */
        private boolean checked;

        protected Entity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        /** The casing the hull is made of. */
        protected abstract Block casing();

        /**
         * What must stand at a place inside the hull other than casing, by its offset from the centre;
         * null where casing or a port belongs.
         */
        protected abstract Predicate<BlockState> special(int dx, int dy, int dz);

        /** Names what belongs at such a special place, for the message when it is missing. */
        protected abstract Component specialName(int dx, int dy, int dz);

        /** How far the hull reaches from its centre: 1 for a 3×3×3 hull, 2 for a 5×5×5 one. */
        protected int reach() {
            return 1;
        }

        /**
         * Whether a port may stand at this place of the shell. Every place of the shell may take one,
         * unless the machine says otherwise.
         */
        protected boolean portAllowed(int dx, int dy, int dz) {
            return true;
        }

        /** One tick of the machine, once it stands. */
        protected abstract void work(ServerLevel level, BlockPos pos, BlockState state);

        /**
         * One tick while the hull is not whole. Most machines simply stop; a reactor's core does not stop
         * being hot because a wall was taken out of it.
         */
        protected void idle(ServerLevel level, BlockPos pos, BlockState state) { }

        /** Told when the hull has come together or fallen apart, or its ports have changed. */
        protected void changed(ServerLevel level) { }

        public boolean formed() {
            return formed;
        }

        public List<BlockPos> ports() {
            return ports;
        }

        public Component problem() {
            return problem;
        }

        /** The middle of the hull: as far behind the controller as the hull reaches. */
        public BlockPos centre() {
            return worldPosition.relative(getBlockState().getValue(FACING).getOpposite(), reach());
        }

        /** The first port of a kind in the hull, or null. */
        public BlockPos port(PortBlock.Kind kind) {
            if (level == null) return null;
            for (BlockPos port : ports) {
                if (level.getBlockState(port).getBlock() instanceof PortBlock block && block.kind() == kind) return port;
            }
            return null;
        }

        /** Whether this is the power port that serves the machine: the first in the hull. */
        public boolean powerPort(BlockPos pos) {
            return pos.equals(port(PortBlock.Kind.POWER));
        }

        void tick(ServerLevel level, BlockPos pos, BlockState state) {
            if (!checked || (level.getGameTime() + pos.asLong()) % 20 == 0) {
                checked = true;
                check(level);
            }
            if (formed) work(level, pos, level.getBlockState(pos));
            else idle(level, pos, level.getBlockState(pos));
        }

        /** Looks the hull over and sets what it found. */
        public void check(ServerLevel level) {
            List<BlockPos> found = new ArrayList<>();
            Component wrong = null;
            BlockPos centre = centre();
            int reach = reach();
            for (int dx = -reach; dx <= reach && wrong == null; dx++) {
                for (int dy = -reach; dy <= reach && wrong == null; dy++) {
                    for (int dz = -reach; dz <= reach && wrong == null; dz++) {
                        BlockPos at = centre.offset(dx, dy, dz);
                        if (at.equals(worldPosition)) continue;
                        BlockState state = level.getBlockState(at);
                        Predicate<BlockState> special = special(dx, dy, dz);
                        if (special != null) {
                            if (!special.test(state)) wrong = Component.translatable("message.hardwrought.multiblock.needs",
                                    specialName(dx, dy, dz), at.getX(), at.getY(), at.getZ());
                        } else if (state.getBlock() instanceof PortBlock && portAllowed(dx, dy, dz)) {
                            found.add(at.immutable());
                        } else if (!state.is(casing())) {
                            wrong = Component.translatable("message.hardwrought.multiblock.needs",
                                    casing().getName(), at.getX(), at.getY(), at.getZ());
                        }
                    }
                }
            }
            boolean now = wrong == null;
            problem = now ? Component.empty() : wrong;
            if (!now) found.clear();
            boolean differs = now != formed || !found.equals(ports);
            for (BlockPos port : found) {
                if (level.getBlockEntity(port) instanceof PortBlock.Entity entity) entity.belongTo(worldPosition);
            }
            List<BlockPos> before = List.copyOf(ports);
            ports.clear();
            ports.addAll(found);
            formed = now;
            BlockState state = getBlockState();
            if (state.getValue(FORMED) != now) level.setBlock(worldPosition, state.setValue(FORMED, now), Block.UPDATE_ALL);
            if (differs) {
                // Ports joined or let go are new points on the line, or old ones gone.
                for (BlockPos port : before) de.ipnats.hardwrought.electricity.Electricity.update(level, port);
                for (BlockPos port : found) de.ipnats.hardwrought.electricity.Electricity.update(level, port);
                changed(level);
                setChanged();
            }
        }

        /** Sets whether it is working, for its look. */
        protected void showRunning(Level level, boolean running) {
            BlockState state = getBlockState();
            if (state.getValue(RUNNING) != running) level.setBlock(worldPosition, state.setValue(RUNNING, running), Block.UPDATE_CLIENTS);
        }
    }
}
