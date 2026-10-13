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
 * The controller of a big machine: a block in the middle of one side of a 3×3×3 hull, its face out.
 * It checks the hull behind it every second; while the hull is whole it is <i>formed</i>, and the ports
 * set in the hull serve it. Opened with an empty hand, it shows the machine, or, unformed, what is wrong.
 */
public class ControllerBlock extends Block implements EntityBlock {
    public enum Machine { ARC_FURNACE, LARGE_DIESEL }

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
        return machine == Machine.ARC_FURNACE ? new ArcFurnaceController(pos, state) : new LargeDieselController(pos, state);
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

        /** One tick of the machine, once it stands. */
        protected abstract void work(ServerLevel level, BlockPos pos, BlockState state);

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

        /** The middle of the hull: one block behind the controller. */
        public BlockPos centre() {
            return worldPosition.relative(getBlockState().getValue(FACING).getOpposite());
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
            if ((level.getGameTime() + pos.asLong()) % 20 == 0) check(level);
            if (formed) work(level, pos, level.getBlockState(pos));
        }

        /** Looks the hull over and sets what it found. */
        public void check(ServerLevel level) {
            List<BlockPos> found = new ArrayList<>();
            Component wrong = null;
            BlockPos centre = centre();
            for (int dx = -1; dx <= 1 && wrong == null; dx++) {
                for (int dy = -1; dy <= 1 && wrong == null; dy++) {
                    for (int dz = -1; dz <= 1 && wrong == null; dz++) {
                        BlockPos at = centre.offset(dx, dy, dz);
                        if (at.equals(worldPosition)) continue;
                        BlockState state = level.getBlockState(at);
                        Predicate<BlockState> special = special(dx, dy, dz);
                        if (special != null) {
                            if (!special.test(state)) wrong = Component.translatable("message.hardwrought.multiblock.needs",
                                    specialName(dx, dy, dz), at.getX(), at.getY(), at.getZ());
                        } else if (state.getBlock() instanceof PortBlock) {
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
