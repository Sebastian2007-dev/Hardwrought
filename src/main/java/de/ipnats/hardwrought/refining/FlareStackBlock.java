package de.ipnats.hardwrought.refining;

import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.Gases;
import de.ipnats.hardwrought.fluids.FluidTankBlock;
import de.ipnats.hardwrought.gasline.GasStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
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
import net.minecraft.world.item.Items;
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
 * A burning stack: a flare that gets rid of what nobody wants. It stands on a tank — a liquid tank or
 * a gas tank — and, once lit with flint and steel or a fire charge, burns off whatever will burn:
 * <ul>
 *   <li>the gas a gas line brings it, natural gas or LPG, which it takes in at low pressure;
 *   <li>methane in the air touching it, drawn into the flame and burnt quietly, a pocket at a time
 *       rather than all at once — this is how firedamp is got rid of safely;
 *   <li>what is in the tank under it: crude oil, any cut of the refinery, natural gas, LPG. Water and
 *       lava it leaves alone.
 * </ul>
 * Everything it burns goes up as carbon dioxide, a good deal of it, which sinks around the stack. With
 * nothing left to burn it goes out and must be lit again — by hand, or, where it is given current
 * through a copper coil set against it, by its electric igniter, which lights it again by itself as soon
 * as there is something to burn.
 */
public class FlareStackBlock extends de.ipnats.hardwrought.electricity.ElectricNodeBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** What it burns each second at most: liquid in millibuckets, or gas in units. */
    public static final int LIQUID_PER_SECOND = 250, GAS_PER_SECOND = 8;
    /** Carbon dioxide given off for each second of burning, in gas units: two whole blocks of it a minute and more. */
    public static final int CO2_PER_SECOND = 3;

    public FlareStackBlock(Properties properties) {
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
        boolean flint = stack.is(Items.FLINT_AND_STEEL), charge = stack.is(Items.FIRE_CHARGE);
        if (!flint && !charge) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (state.getValue(LIT)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.playSound(null, pos, flint ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (flint) stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
        else if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!onTank(level, pos) || !(level.getBlockEntity(pos) instanceof Entity entity) || !entity.hasFuel((ServerLevel) level, pos)) {
            return InteractionResult.FAIL;
        }
        level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    @Override
    public net.minecraft.world.phys.Vec3 terminal(BlockState state) {
        return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity entity)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(entity);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.3, y + random.nextDouble() * 0.4,
                    z + (random.nextDouble() - 0.5) * 0.3, 0, 0.06, 0);
        }
        level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.8, z, 0, 0.05, 0);
        if (random.nextInt(10) == 0) level.playLocalSound(x, y, z, SoundEvents.BLAZE_BURN, SoundSource.BLOCKS, 0.4f, 0.5f, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != RefineryBlocks.FLARE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    /** Whether the stack stands on a tank, liquid or gas: it is built on one and lights only there. */
    static boolean onTank(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).getBlock() instanceof FluidTankBlock
                || GasStorage.SIDED.find(level, below, Direction.UP) != null;
    }

    /** What burns in the tank under the stack: the liquid storage or the gas store, or null for nothing that burns. */
    static Object fuelBelow(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).getBlock() instanceof FluidTankBlock) {
            Storage<FluidVariant> tank = FluidTankBlock.storage(level, below);
            for (StorageView<FluidVariant> view : tank) {
                if (!view.isResourceBlank() && view.getAmount() > 0 && burns(view.getResource())) return tank;
            }
            return null;
        }
        GasStorage gas = GasStorage.SIDED.find(level, below, Direction.UP);
        return gas != null && gas.kind() != null && gas.kind().burns() && gas.amount() > 0 ? gas : null;
    }

    /** Crude oil and every cut of it burn; water, lava and the rest do not. */
    public static boolean burns(FluidVariant liquid) {
        int substance = RefineryMenu.substance(liquid.getFluid());
        return substance != RefineryMenu.NOTHING && substance != RefineryMenu.WATER;
    }

    public static class Entity extends de.ipnats.hardwrought.electricity.ElectricBlockEntity
            implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos> {
        /** The igniter's resistance: a quarter of an ampere at the line's sixty volts. */
        public static final double IGNITER_OHMS = 240.0;
        private int clock;
        /** What gas lines bring it: taken in at a low pressure, so the line's gas runs toward it. */
        public final de.ipnats.hardwrought.gasline.GasStore intake =
                new de.ipnats.hardwrought.gasline.GasStore(32, 2, true, de.ipnats.hardwrought.gasline.GasKind::burns, this::setChanged);
        /** What it burnt in its last second, for the window: substance and amount. */
        private int lastSubstance = RefineryMenu.NOTHING, lastAmount;

        public Entity(BlockPos pos, BlockState state) {
            super(RefineryBlocks.FLARE_ENTITY, pos, state);
        }

        /** Whether its igniter has current enough to spark. */
        public boolean igniterLive() {
            return de.ipnats.hardwrought.electricity.MachineBlockEntity.rate(volts()) > 0.0;
        }

        @Override
        public double conductance() {
            return 1.0 / IGNITER_OHMS;
        }

        @Override
        public boolean takesWires() {
            return false;
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity stack) {
            if (++stack.clock < 20) return;
            stack.clock = 0;
            if (!state.getValue(LIT)) {
                // Out, with current on its igniter: it sparks, and catches if there is anything to burn.
                if (stack.igniterLive() && onTank(level, pos) && stack.hasFuel((ServerLevel) level, pos)) {
                    level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
                    level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
                }
                return;
            }
            if (!stack.burn((ServerLevel) level, pos)) {
                stack.lastSubstance = RefineryMenu.NOTHING;
                stack.lastAmount = 0;
                level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
            }
            stack.setChanged();
        }

        /** Whether anything is there to burn: in its intake, in the air at it, or in the tank under it. */
        boolean hasFuel(ServerLevel level, BlockPos pos) {
            return intake.amount() > 0 || methaneAround(level, pos) > 0 || (onTank(level, pos) && fuelBelow(level, pos) != null);
        }

        /** One second of burning. Returns whether anything burnt. */
        private boolean burn(ServerLevel level, BlockPos pos) {
            if (!onTank(level, pos)) return false;
            double share = 0;
            lastAmount = 0;
            // What the lines bring first: that is what a line to the stack is for.
            if (intake.amount() > 0) {
                lastSubstance = RefineryMenu.substance(intake.kind());
                double burnt = intake.extract(GAS_PER_SECOND);
                lastAmount += (int) Math.round(burnt);
                share += burnt / GAS_PER_SECOND;
            }
            // Methane in the air at the flame burns there, quietly, and goes up as carbon dioxide.
            int methane = burnMethane(level, pos, GAS_PER_SECOND);
            if (methane > 0) {
                lastSubstance = RefineryMenu.substance(de.ipnats.hardwrought.gasline.GasKind.NATURAL_GAS);
                lastAmount += methane;
                share += methane / (double) GAS_PER_SECOND;
            }
            if (share < 1) share += burnTank(level, pos, 1 - share);
            if (share <= 0) return false;
            int co2 = Math.max(1, (int) Math.round(CO2_PER_SECOND * Math.min(1.0, share)));
            Gases.emit(level, pos.above(), Gas.CARBON_DIOXIDE, co2);
            return true;
        }

        /** Burns up to this share of a second's worth from the tank under the stack; returns the share burnt. */
        private double burnTank(ServerLevel level, BlockPos pos, double room) {
            Object fuel = fuelBelow(level, pos);
            if (fuel instanceof GasStorage gas) {
                lastSubstance = RefineryMenu.substance(gas.kind());
                double burnt = gas.extract(GAS_PER_SECOND * room);
                lastAmount += (int) Math.round(burnt);
                return burnt / GAS_PER_SECOND;
            }
            if (!(fuel instanceof Storage<?> raw)) return 0;
            @SuppressWarnings("unchecked")
            Storage<FluidVariant> tank = (Storage<FluidVariant>) raw;
            FluidVariant liquid = null;
            for (StorageView<FluidVariant> view : tank) {
                if (!view.isResourceBlank() && view.getAmount() > 0 && burns(view.getResource())) liquid = view.getResource();
            }
            if (liquid == null) return 0;
            long most = (long) (LIQUID_PER_SECOND * room) * ProcessTank.MILLIBUCKET;
            long burnt;
            try (Transaction transaction = Transaction.openOuter()) {
                burnt = tank.extract(liquid, most, transaction);
                transaction.commit();
            }
            lastSubstance = RefineryMenu.substance(liquid.getFluid());
            lastAmount += (int) (burnt / ProcessTank.MILLIBUCKET);
            return burnt / (double) (LIQUID_PER_SECOND * ProcessTank.MILLIBUCKET);
        }

        /** Methane in the gas blocks touching the stack, and in the one over its top. */
        private static int methaneAround(Level level, BlockPos pos) {
            int units = 0;
            for (Direction side : Direction.values()) {
                if (side == Direction.DOWN) continue;
                units += Gases.units(level.getBlockState(pos.relative(side)), Gas.METHANE);
            }
            return units;
        }

        /**
         * Draws up to so many units of methane out of the gas touching the stack and burns them: each unit
         * goes, and a unit of carbon dioxide takes its place, as when firedamp goes off — but only as much
         * as the flame takes in, never the whole pocket at once.
         */
        private static int burnMethane(ServerLevel level, BlockPos pos, int most) {
            int burnt = 0;
            for (Direction side : Direction.values()) {
                if (side == Direction.DOWN || burnt >= most) continue;
                BlockPos at = pos.relative(side);
                BlockState state = level.getBlockState(at);
                int units = Math.min(most - burnt, Gases.units(state, Gas.METHANE));
                if (units <= 0) continue;
                level.setBlock(at, Gases.with(state, Gas.METHANE, -units), Block.UPDATE_ALL);
                burnt += units;
            }
            return burnt;
        }

        /** What the window shows: whether it is lit, what it is burning, and the tank under it. */
        RefineryMenu.Panel panel() {
            boolean lit = getBlockState().getValue(LIT);
            RefineryMenu.Panel panel = new RefineryMenu.Panel(RefineryMenu.KIND_FLARE, lit, lit && lastAmount > 0,
                    Math.round(Math.abs(volts())),
                    lit ? lastAmount : 0);
            BlockPos below = worldPosition.below();
            if (level.getBlockState(below).getBlock() instanceof FluidTankBlock) {
                Storage<FluidVariant> tank = FluidTankBlock.storage(level, below);
                for (StorageView<FluidVariant> view : tank) {
                    panel.gauge(view.isResourceBlank() ? RefineryMenu.NOTHING : RefineryMenu.substance(view.getResource().getFluid()),
                            view.getAmount() / ProcessTank.MILLIBUCKET, view.getCapacity() / ProcessTank.MILLIBUCKET);
                    break;
                }
            } else {
                GasStorage gas = GasStorage.SIDED.find(level, below, Direction.UP);
                if (gas != null) {
                    panel.gauge(gas.kind() == null ? RefineryMenu.NOTHING : RefineryMenu.substance(gas.kind()),
                            (long) gas.amount(), (long) gas.capacity());
                }
            }
            panel.gauge(intake.kind() == null ? RefineryMenu.NOTHING : RefineryMenu.substance(intake.kind()),
                    (long) intake.amount(), (long) intake.capacity());
            return panel;
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
            output.putInt("clock", clock);
            intake.save(output, "intake");
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            clock = input.getIntOr("clock", 0);
            intake.load(input, "intake");
        }
    }
}
