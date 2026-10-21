package de.ipnats.hardwrought.multiblock;

import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.refining.RefineryMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Predicate;

/**
 * The battery bank: a 5×5×5 hull of battery bank casing, its inside 3×3×3 filled with up to 27 lead-acid
 * cell stacks, and this controller in the middle of one side. It is the battery of the 240-volt line a
 * steam turbine makes.
 *
 * <p>It behaves as the small battery does, only larger: {@value #EMPTY_VOLTS} V empty to
 * {@value #FULL_VOLTS} V full, behind a resistance that falls the more stacks there are. Where the line
 * stands above that, it charges; below, it gives. Nothing switches it. A line of sixty volts never
 * charges it; stepped up four times by a transformer, it does.
 *
 * <p>Each stack holds {@value #STACK_JOULES} watt-seconds and carries {@value #AMPS_PER_STACK} amperes in
 * or out; 27 stacks hold fifteen kilowatt-hours, enough to carry a turbine's forty kilowatts for twenty
 * minutes. Of what goes in, {@value #EFFICIENCY} comes out again. More current than the stacks are made
 * for and the acid boils; kept up, a stack bursts, and the bank with it.
 */
public class BatteryBankController extends ControllerBlock.Entity implements ExtendedMenuProvider<BlockPos>, PortHost {
    public static final double EMPTY_VOLTS = 216.0, FULL_VOLTS = 252.0;
    public static final double STACK_JOULES = 2_000_000.0, AMPS_PER_STACK = 10.0, STACK_OHMS = 13.5, EFFICIENCY = 0.85;
    /** Seconds it stands at half as much again as it is made for. */
    private static final float BOIL_LIMIT = 15.0f;

    private double charge;
    private int stacks;
    private float boil;
    private float watts;
    /** The line's voltage at the power port, as last worked out. */
    private float lineVolts;

    public BatteryBankController(BlockPos pos, BlockState state) {
        super(BigMachines.BATTERY_BANK_ENTITY, pos, state);
    }

    @Override
    protected int reach() {
        return 2;
    }

    @Override
    protected Block casing() {
        return BigMachines.BATTERY_BANK_CASING;
    }

    @Override
    protected Predicate<BlockState> special(int dx, int dy, int dz) {
        boolean inside = Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1;
        return inside ? state -> state.is(BigMachines.BATTERY_CELL_STACK) || state.isAir() : null;
    }

    @Override
    protected Component specialName(int dx, int dy, int dz) {
        return BigMachines.BATTERY_CELL_STACK.getName();
    }

    /** The cell stacks inside the hull. */
    private int countStacks(Level level) {
        int count = 0;
        BlockPos centre = centre();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockState(centre.offset(dx, dy, dz)).is(BigMachines.BATTERY_CELL_STACK)) count++;
        }
        return count;
    }

    public double capacity() {
        return stacks * STACK_JOULES;
    }

    public double charge() {
        return charge;
    }

    public void setCharge(double joules) {
        charge = Math.clamp(joules, 0.0, capacity());
        setChanged();
    }

    public int stacks() {
        return stacks;
    }

    // ---------------------------------------------------------------- the line

    @Override
    public double emf() {
        if (!formed() || stacks == 0) return 0.0;
        return EMPTY_VOLTS + (FULL_VOLTS - EMPTY_VOLTS) * charge / capacity();
    }

    /**
     * The stacks stand side by side: the more there are, the stiffer the bank. Each sits behind its share
     * of the charge controller, so 27 of them make half an ohm: charged from a turbine at 240 V when empty,
     * the current stays under fifty amperes, which a heavy cable carries.
     */
    @Override
    public double sourceOhms() {
        return STACK_OHMS / Math.max(1, stacks);
    }

    @Override
    public boolean canGive() {
        return charge > 0.0;
    }

    @Override
    public boolean canTake() {
        return stacks > 0 && charge < capacity();
    }

    @Override
    public boolean pulses() {
        return true;
    }

    @Override
    public void solved(double volts, double amps, double seconds) {
        lineVolts = (float) Math.abs(volts);
        if (seconds <= 0.0 || stacks == 0) return;
        // Out of the cells when giving; into them, less what charging wastes, when taking.
        double power = emf() * amps;
        watts = (float) power;
        if (power != 0.0) setCharge(charge - (power > 0.0 ? power : power * EFFICIENCY) * seconds);
        double over = Math.abs(amps) / (AMPS_PER_STACK * stacks);
        boil = over > 1.0 ? boil + (float) ((over * over - 1.0) * seconds * 0.8) : Math.max(0.0f, boil - (float) seconds);
    }

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 == 0) {
            int now = countStacks(level);
            if (now != stacks) {
                stacks = now;
                charge = Math.min(charge, capacity());
                BlockPos power = port(PortBlock.Kind.POWER);
                if (power != null) Electricity.update(level, power);
                setChanged();
            }
        }
        if (boil > 1.0f && level.getGameTime() % 10 == 0) {
            BlockPos top = centre().above(2);
            level.sendParticles(ParticleTypes.CLOUD, top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5, 4, 0.6, 0.05, 0.6, 0.01);
            level.playSound(null, top, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.6f, 0.8f);
        }
        if (boil >= BOIL_LIMIT) burst(level);
        showRunning(level, Math.abs(watts) > 1.0f);
    }

    /** A stack boils dry and bursts: it is gone, and the blast takes some of the casing with it. */
    private void burst(ServerLevel level) {
        boil = 0.0f;
        BlockPos centre = centre();
        level.setBlock(centre, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.explode(null, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 3.0f, Level.ExplosionInteraction.BLOCK);
    }

    @Override
    protected void changed(ServerLevel level) {
        // A broken hull gives nothing, but the cells keep their charge for when it is whole again.
        if (formed()) {
            stacks = countStacks(level);
            charge = Math.min(charge, capacity());
        } else {
            watts = 0;
            showRunning(level, false);
        }
    }

    // ---------------------------------------------------------------- the window

    /**
     * The charge in thousandths, the power in tens of watts — given positive, taken negative — the line's
     * voltage at the port in tenths, and the stacks.
     */
    RefineryMenu.Panel panel() {
        int perMille = capacity() <= 0 ? 0 : (int) Math.round(charge / capacity() * 1000);
        return new RefineryMenu.Panel(RefineryMenu.KIND_BATTERY_BANK, stacks > 0, Math.abs(watts) > 1.0f, perMille,
                Math.round(watts / 10)).more(Math.round(lineVolts * 10), stacks);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RefineryMenu(id, RefineryMenu.data(this::panel), worldPosition);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("charge", charge);
        output.putInt("stacks", stacks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stacks = input.getIntOr("stacks", 0);
        charge = input.getDoubleOr("charge", 0.0);
    }
}
