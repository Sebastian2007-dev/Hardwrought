package de.ipnats.hardwrought.oil;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.environment.Flues;
import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.Gases;
import de.ipnats.hardwrought.geology.Reservoir;
import de.ipnats.hardwrought.geology.Reservoirs;
import de.ipnats.hardwrought.machinery.KineticHolder;
import de.ipnats.hardwrought.smithing.GasPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * A rig's hole and what comes up it.
 *
 * <p>While it is <b>boring</b> it counts its way down through the rock under it — the blocks are not
 * dug out; a borehole is a hand's width across — until it reaches the top of the reservoir under the
 * column it stands on. A column over nothing is bored to the bottom of the world and comes up
 * <b>dry</b>. Once it has struck it is a <b>well</b>: oil into its tank, gas into its store, both in
 * proportion to how fast it is turned and to what the reservoir still has to give.
 *
 * <p>Section 61's risks come up with the gas. An oil well brings up the gas out of its reservoir's cap
 * with the oil, a gas well nothing else. What its store cannot hold goes up a gas pipe set on top of
 * the rig and out at the pipe's open end; with no pipe, it escapes into the air around the rig as
 * methane — which suffocates in a closed shed and explodes at the first open flame, as the gas system
 * makes it do.
 */
public class DrillingRigBlockEntity extends BlockEntity implements KineticHolder {
    /** Strength taken per turn per minute: 192 at 16 turns, more than a hand crank gives. */
    public static final float IMPACT = 12.0f;
    /** Oil the rig's tank holds, in millibuckets. */
    public static final double OIL_TANK = 16_000;
    /** Gas the rig holds before it has to let it go, in gas units. */
    public static final double GAS_STORE = 64;
    /** Gas one canister takes. */
    public static final int CANISTER_UNITS = 16;
    private static final int BUCKET = 1000;

    public enum Stage { BORING, WELL, DRY }

    private float speed;
    private Stage stage = Stage.BORING;
    private double bored;
    /** How far down the rig has to bore, worked out once, the first time it is turned. -1 before. */
    private int depth = -1;
    private double oil;
    private double gas;
    /** Drawn out of the reservoir but not yet written to the save, below one whole unit. */
    private double undrawn;
    /** Test hook: the reservoir to act as if this rig stood over, wherever it really is. */
    private Reservoir testReservoir;

    public DrillingRigBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRILLING_RIG, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DrillingRigBlockEntity rig) {
        if (!(level instanceof ServerLevel server)) return;
        Reservoir reservoir = rig.testReservoir != null ? rig.testReservoir
                : Reservoirs.under(server.getSeed(), pos.getX(), pos.getZ());
        if (rig.depth < 0) rig.depth = depthTo(reservoir, pos, level.getMinY());
        boolean working = rig.speed != 0.0f && rig.stage != Stage.DRY;
        if (working) {
            if (rig.stage == Stage.BORING) rig.bore(server, pos, reservoir);
            else working = rig.pump(server, reservoir);
            rig.setChanged();
        }
        if (level.getGameTime() % 20 == 0 && rig.gas > GAS_STORE) rig.release(server, pos);
        if (state.getValue(DrillingRigBlock.RUNNING) != working) {
            level.setBlock(pos, state.setValue(DrillingRigBlock.RUNNING, working), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Blocks of rock between the rig and the top of the reservoir under it, or to the bottom of the
     * world where there is none.
     */
    public static int depthTo(Reservoir reservoir, BlockPos pos, int minY) {
        if (reservoir == null) return Math.max(0, pos.getY() - (minY + 1));
        return Math.max(0, pos.getY() - 1 - reservoir.topAt(pos.getX(), pos.getZ()));
    }

    private void bore(ServerLevel level, BlockPos pos, Reservoir reservoir) {
        int before = (int) bored;
        bored = Math.min(depth, bored + Oilfield.borePerTick(speed));
        if ((int) bored != before && (int) bored % 4 == 0) {
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.5f, 0.6f);
        }
        if (bored < depth) return;
        stage = reservoir == null ? Stage.DRY : Stage.WELL;
        if (reservoir == null) {
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8f, 0.5f);
        } else {
            // Breaking into a reservoir is a rush of gas out of its cap, whatever lies under it.
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.5f);
            gas += CANISTER_UNITS;
        }
    }

    /** One tick of a well. Returns whether it is still giving. */
    private boolean pump(ServerLevel level, Reservoir reservoir) {
        if (reservoir == null) {
            stage = Stage.DRY;
            return false;
        }
        // Gas that has nowhere to go backs up the hole and chokes the well until it is let out.
        if (gas >= GAS_STORE * 2) return false;
        var runtime = CoreLifecycle.find(level.getServer());
        long drawn = runtime == null ? 0 : runtime.saveData().reservoirDrawn(reservoir.key());
        double yield = Oilfield.perTick(reservoir, drawn, speed);
        if (yield <= 0) return false;
        double taken;
        if (reservoir.kind() == Reservoir.Kind.OIL) {
            taken = Math.min(yield, OIL_TANK - oil);
            if (taken <= 0) return false;
            oil += taken;
            // The gas out of the cap comes up with the oil, whether or not there is room for it.
            gas += taken / Oilfield.OIL_PER_SECOND * Oilfield.GAS_PER_SECOND * Oilfield.ASSOCIATED_GAS;
        } else {
            taken = yield;
            gas += taken;
        }
        undrawn += taken;
        if (undrawn >= 1 && runtime != null) {
            long whole = (long) undrawn;
            runtime.saveData().drawFromReservoir(reservoir.key(), whole);
            undrawn -= whole;
        }
        return true;
    }

    /**
     * Lets out the gas the rig cannot hold: up the pipe on top of it and out at the pipe's open end,
     * or, with no pipe, into the air right where the rig stands.
     */
    public int release(ServerLevel level, BlockPos pos) {
        int excess = (int) Math.floor(gas - GAS_STORE);
        if (excess <= 0) return 0;
        BlockPos above = pos.above();
        BlockPos outlet = level.getBlockState(above).getBlock() instanceof GasPipeBlock
                ? Flues.outletFrom(level, above) : null;
        BlockPos out = outlet != null ? outlet : above;
        int left = Gases.emit(level, out, Gas.METHANE, excess);
        gas -= excess - left;
        if (outlet == null && excess - left > 0) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.6f);
        }
        return excess - left;
    }

    /** A bucket of crude oil out of the tank, or empty when there is not a bucketful. */
    public ItemStack drawOil() {
        if (oil < BUCKET) return ItemStack.EMPTY;
        oil -= BUCKET;
        setChanged();
        return new ItemStack(ModItems.CRUDE_OIL_BUCKET);
    }

    /** A canister of gas out of the store, or empty when there is not enough for one. */
    public ItemStack drawGas() {
        if (gas < CANISTER_UNITS) return ItemStack.EMPTY;
        gas -= CANISTER_UNITS;
        setChanged();
        return new ItemStack(ModItems.NATURAL_GAS_CANISTER);
    }

    /** What reading the rig tells: how deep, what it struck, what it holds. */
    public Component describe() {
        Component state = switch (stage) {
            case BORING -> Component.translatable("message.hardwrought.rig.boring", (int) bored,
                    Math.max(0, depth));
            case DRY -> Component.translatable("message.hardwrought.rig.dry", (int) bored);
            case WELL -> Component.translatable("message.hardwrought.rig.well",
                    String.format(Locale.ROOT, "%.0f", oil), String.format(Locale.ROOT, "%.0f", OIL_TANK),
                    String.format(Locale.ROOT, "%.0f", Math.min(gas, GAS_STORE)),
                    String.format(Locale.ROOT, "%.0f", GAS_STORE));
        };
        if (speed == 0.0f && stage != Stage.DRY) {
            return state.copy().append(Component.translatable("message.hardwrought.rig.still"));
        }
        return state;
    }

    // ---------------------------------------------------------------- for tests

    public Stage stage() {
        return stage;
    }

    public double oil() {
        return oil;
    }

    public double gas() {
        return gas;
    }

    public double bored() {
        return bored;
    }

    public int depth() {
        return depth;
    }

    /** Test hook: act as if this rig stood over this reservoir, and work its depth out again. */
    public void overrideReservoirForTesting(Reservoir reservoir) {
        testReservoir = reservoir;
        depth = -1;
        setChanged();
    }

    /** Test hook: as if it had bored this far already. */
    public void setBoredForTesting(double value) {
        bored = value;
        setChanged();
    }

    /** Test hook: as much gas in the store as this. */
    public void setGasForTesting(double value) {
        gas = value;
        setChanged();
    }

    // ---------------------------------------------------------------- kinetics and saving

    @Override
    public float kineticSpeed() {
        return speed;
    }

    @Override
    public void setKineticSpeed(float speed) {
        this.speed = speed;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        speed = input.getFloatOr("speed", 0.0f);
        stage = Stage.values()[Math.floorMod(input.getIntOr("stage", 0), Stage.values().length)];
        bored = input.getDoubleOr("bored", 0);
        depth = input.getIntOr("depth", -1);
        oil = input.getDoubleOr("oil", 0);
        gas = input.getDoubleOr("gas", 0);
        undrawn = input.getDoubleOr("undrawn", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("speed", speed);
        output.putInt("stage", stage.ordinal());
        output.putDouble("bored", bored);
        output.putInt("depth", depth);
        output.putDouble("oil", oil);
        output.putDouble("gas", gas);
        output.putDouble("undrawn", undrawn);
    }
}
