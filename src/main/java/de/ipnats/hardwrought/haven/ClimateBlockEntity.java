package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.environment.RoomScan;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * An electric heater or an air cooler: it acts on the room it stands in, as the room scan finds it, and
 * holds it at the temperature its thermostat is set to. A heater puts its watts into the room; a cooler
 * takes out {@link #COOLING_PER_WATT} times what it draws, its compressor pumping the heat away.
 *
 * <p>What it puts in is spread over the room's volume: a heater warms one room well and a hall poorly.
 * The walls keep it by their insulation, and every open door, fence or wall-gap lets it out — a room
 * with its door open does not reach what the thermostat asks. In the open air it does nothing.
 *
 * <p>The thermostat is a bimetal strip: it switches on a little below its setting and off a little
 * above. An empty hand turns it up, round from {@link #HIGHEST} back to {@link #LOWEST}.
 */
public class ClimateBlockEntity extends ApplianceBlockEntity {
    public static final int LOWEST = 10, HIGHEST = 30, STEP = 2;
    public static final double COOLING_PER_WATT = 2.5;
    /** How far from its setting it lets the room drift before it switches. */
    public static final double BAND = 0.5;
    private static final int RESCAN_TICKS = 200;

    private int setting;
    private boolean calling;
    private RoomScan room;
    private long scanned = Long.MIN_VALUE;
    private double temperature = Double.NaN;

    public ClimateBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.CLIMATE_ENTITY, pos, state);
        setting = cooling() ? 18 : 20;
    }

    private boolean cooling() {
        return appliance() == Appliance.AIR_COOLER;
    }

    public int setting() {
        return setting;
    }

    /** Sets the thermostat. For tests too. */
    public void setSetting(int degrees) {
        setting = degrees;
        setChanged();
    }

    /** The temperature of its room as last read; NaN in the open air. */
    public double roomTemperature() {
        return temperature;
    }

    @Override
    protected boolean wants() {
        return calling;
    }

    @Override
    protected void work(ServerLevel level, double power) {
        long now = level.getGameTime();
        if (room == null || now - scanned >= RESCAN_TICKS) {
            // The room starts in front of it, or above it where it stands against a wall.
            room = RoomScan.scan(level, worldPosition.relative(getBlockState().getValue(ApplianceBlock.FACING)));
            if (!room.sealed()) room = RoomScan.scan(level, worldPosition.above());
            scanned = now;
        }
        var environment = CoreLifecycle.require(level.getServer()).environment();
        temperature = environment.roomTemperature(level, room);
        boolean calls = !Double.isNaN(temperature) && (cooling()
                ? temperature > setting + (calling ? -BAND : BAND)
                : temperature < setting + (calling ? BAND : -BAND));
        if (calls != calling) {
            calling = calls;
            electricDirty = true;
        }
        if (power <= 0.0) return;
        double watts = power * appliance().ratedWatts();
        environment.offerHeat(level, room, worldPosition, cooling() ? -watts * COOLING_PER_WATT : watts);
    }

    @Override
    protected Component use(ServerPlayer player) {
        setting = setting >= HIGHEST ? LOWEST : setting + STEP;
        calling = false;
        electricDirty = true;
        setChanged();
        return Component.translatable("message.hardwrought.thermostat.set", setting);
    }

    @Override
    protected Component state() {
        if (Double.isNaN(temperature)) return Component.translatable("message.hardwrought.meter.thermostat.open");
        return Component.translatable("message.hardwrought.meter.thermostat", String.format(Locale.ROOT, "%.1f", temperature), setting);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        setting = input.getIntOr("setting", setting);
        calling = input.getBooleanOr("calling", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("setting", setting);
        output.putBoolean("calling", calling);
    }
}
