package de.ipnats.hardwrought.multiblock;

import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import de.ipnats.hardwrought.electricity.Electricity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A port in the hull of a big machine: set anywhere in the hull in place of a casing block, it is where
 * pipes, item pipes or current go in and out. What comes through it goes to the machine's controller.
 * <ul>
 *   <li>A fluid port is the machine's tank to a fluid pipe.
 *   <li>An item port is its inputs and outputs to an item pipe or a hopper.
 *   <li>A gas port is its gas store to a gas line: steam, or uranium hexafluoride.
 *   <li>A power port is the machine on the line: a copper coil set against it joins it to wires.
 * </ul>
 * What each leads to is the controller's to say (see {@link PortHost}).
 */
public class PortBlock extends ElectricNodeBlock {
    public enum Kind { FLUID, ITEM, POWER, GAS }

    private final Kind kind;

    public PortBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 0.5, 0.5);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BigMachines.PORT_ENTITY || kind != Kind.POWER) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    /**
     * The port's entity. Only a power port does anything electrically: it gives the voltage, or takes the
     * current, of the controller it belongs to. The other ports are only passages.
     */
    public static class Entity extends ElectricBlockEntity {
        /** The controller of the machine it is part of, set each time the controller checks its hull. */
        private BlockPos controller;

        public Entity(BlockPos pos, BlockState state) {
            super(BigMachines.PORT_ENTITY, pos, state);
        }

        public Kind kind() {
            return getBlockState().getBlock() instanceof PortBlock port ? port.kind() : Kind.FLUID;
        }

        void belongTo(BlockPos controller) {
            this.controller = controller;
        }

        /** The controller this port serves, while the machine stands. */
        public ControllerBlock.Entity controller() {
            if (controller == null || level == null) return null;
            return level.getBlockEntity(controller) instanceof ControllerBlock.Entity entity && entity.formed()
                    && entity.ports().contains(worldPosition) ? entity : null;
        }

        /** The machine behind this port, if it is a working power port: the first of its kind in the hull. */
        private PortHost power() {
            return kind() == Kind.POWER && controller() instanceof PortHost host
                    && ((ControllerBlock.Entity) host).powerPort(worldPosition) ? host : null;
        }

        /** The machine behind this port, whatever its kind, while it stands. */
        public PortHost host() {
            return controller() instanceof PortHost host ? host : null;
        }

        @Override
        public double emf() {
            PortHost host = power();
            return host == null ? 0.0 : host.emf();
        }

        @Override
        public double sourceOhms() {
            PortHost host = power();
            return host == null ? 1.0 : host.sourceOhms();
        }

        @Override
        public boolean canGive() {
            PortHost host = power();
            return host != null && host.emf() > 0.0 && host.canGive();
        }

        @Override
        public boolean canTake() {
            PortHost host = power();
            return host != null && host.canTake();
        }

        @Override
        public double conductance() {
            PortHost host = power();
            return host == null ? 0.0 : host.conductance();
        }

        @Override
        public boolean takesWires() {
            return false;
        }

        @Override
        protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
            super.solved(level, volts, amps, wet, seconds);
            PortHost host = power();
            if (host != null) host.solved(volts, amps, seconds);
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity port) {
            if (level instanceof ServerLevel server && level.getGameTime() % Electricity.PULSE_TICKS == 0) {
                PortHost host = port.power();
                if (host != null && host.pulses()) Electricity.pulse(server, pos);
            }
        }
    }
}
