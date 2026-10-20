package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.parts.Parts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * The devices of the safe haven (docs/electricity-roadmap.md, stage E3), each the answer to a hardship
 * the world already has: what it is rated for, how bright it is while it works, which part of it goes
 * and mends it, and what it is shaped like. How each works is in its block entity.
 */
public enum Appliance {
    /** A tungsten bulb in a shade: bright for little power, and the bulb is changed when it goes. */
    ELECTRIC_LIGHT("electric_light", 60.0, 25.0, 15, () -> Parts.LIGHT_BULB, Block.box(4, 0, 4, 12, 9, 12)),
    /** An arc between tungsten electrodes behind a mirror, on the 240-volt line: no monster comes into its light. */
    ARC_FLOODLIGHT("arc_floodlight", 240.0, 960.0, 15, () -> Parts.ARC_ELECTRODE, Block.box(2, 0, 2, 14, 14, 14)),
    /** A lamp with an eye: dark until something moves near it, then light and a redstone signal. */
    MOTION_LAMP("motion_lamp", 60.0, 25.0, 15, () -> Parts.LIGHT_BULB, Block.box(4, 0, 4, 12, 10, 12)),
    /** A bell struck by a relay while it has a redstone signal: wakes whoever sleeps near, and draws what hears it. */
    ALARM_BELL("alarm_bell", 60.0, 30.0, 0, () -> Parts.RELAY, Block.box(3, 0, 3, 13, 12, 13)),
    /** A nichrome element behind a guard, with a thermostat: holds its room at what it is set to. */
    ELECTRIC_HEATER("electric_heater", 60.0, 120.0, 0, () -> Parts.HEATING_ELEMENT, Block.box(1, 0, 3, 15, 14, 13)),
    /** A compressor and coils: pumps the heat out of its room. */
    AIR_COOLER("air_cooler", 60.0, 180.0, 0, () -> Parts.COMPRESSOR, Block.box(1, 0, 1, 15, 15, 15)),
    /** A fan in a frame, set in a wall: draws the bad air out of the room in front of it and blows it out behind. */
    VENTILATION_FAN("ventilation_fan", 60.0, 40.0, 0, () -> Parts.FAN_BLADES, Block.box(0, 0, 0, 16, 16, 16)),
    /** A cold cabinet: food in it goes off at a quarter of the speed. */
    REFRIGERATOR("refrigerator", 60.0, 60.0, 0, () -> Parts.COMPRESSOR, Block.box(1, 0, 1, 15, 16, 15)),
    /** A colder one: food in it does not go off at all. */
    FREEZER("freezer", 60.0, 100.0, 0, () -> Parts.COMPRESSOR, Block.box(1, 0, 1, 15, 16, 15)),
    /** A red bulb behind a reflector: warmth for whoever lies or stands under it, and frostbite eased. */
    HEAT_LAMP("heat_lamp", 60.0, 100.0, 8, () -> Parts.LIGHT_BULB, Block.box(4, 0, 4, 12, 12, 12)),
    /** A bedside monitor: the bed beside it is a sickbed. */
    BEDSIDE_MONITOR("bedside_monitor", 60.0, 15.0, 0, () -> Parts.BASIC_CIRCUIT, Block.box(3, 0, 4, 13, 12, 12)),
    /** Stage E4: five places to charge cells in, or the devices and linings they sit in. */
    CHARGING_STATION("charging_station", 60.0, 240.0, 0, () -> Parts.WINDING, Block.box(1, 0, 2, 15, 10, 14)),
    /** Stage E5: makes what its drawing shows from the parts put into it, and takes orders from a storage desk. */
    ASSEMBLER("assembler", 60.0, 200.0, 0, () -> Parts.ADVANCED_CIRCUIT, Block.box(0, 0, 0, 16, 16, 16)),
    /** Stage E6: the foot of a Tesla tower, fed at 960 volts. */
    TESLA_TOWER("tesla_tower", 960.0, 900.0, 0, () -> de.ipnats.hardwrought.tesla.TeslaBlocks.SPARK_GAP, Block.box(0, 0, 0, 16, 16, 16));

    private final String id;
    private final double ratedVolts;
    private final double ratedWatts;
    private final int light;
    private final Supplier<Item> mends;
    private final VoxelShape shape;

    Appliance(String id, double ratedVolts, double ratedWatts, int light, Supplier<Item> mends, VoxelShape shape) {
        this.id = id;
        this.ratedVolts = ratedVolts;
        this.ratedWatts = ratedWatts;
        this.light = light;
        this.mends = mends;
        this.shape = shape;
    }

    public String id() {
        return id;
    }

    public double ratedVolts() {
        return ratedVolts;
    }

    public double ratedWatts() {
        return ratedWatts;
    }

    /** How bright it is while it works. */
    public int light() {
        return light;
    }

    /** The part that goes, and mends it. */
    public Item mends() {
        return mends.get();
    }

    public VoxelShape shape(Direction facing) {
        return shape;
    }

    /** Where its wire is made fast: the middle of its top, or of its side where something stands on it. */
    public Vec3 terminal(Direction facing) {
        if (this == TESLA_TOWER) return new Vec3(0.5 + facing.getStepX() * 0.5, 0.5, 0.5 + facing.getStepZ() * 0.5);
        return new Vec3(0.5, shape.max(Direction.Axis.Y), 0.5);
    }

    /** Whether a Tesla tower within reach lights it without wire: lamps of the matching kind. */
    public boolean wireless() {
        return this == ELECTRIC_LIGHT;
    }

    public BlockEntity create(BlockPos pos, BlockState state) {
        return switch (this) {
            case ELECTRIC_LIGHT, HEAT_LAMP -> new BulbBlockEntity(pos, state);
            case MOTION_LAMP -> new MotionLampBlockEntity(pos, state);
            case ARC_FLOODLIGHT -> new FloodlightBlockEntity(pos, state);
            case ALARM_BELL -> new AlarmBellBlockEntity(pos, state);
            case ELECTRIC_HEATER, AIR_COOLER -> new ClimateBlockEntity(pos, state);
            case VENTILATION_FAN -> new FanBlockEntity(pos, state);
            case REFRIGERATOR, FREEZER -> new ColdStoreBlockEntity(pos, state);
            case BEDSIDE_MONITOR -> new MonitorBlockEntity(pos, state);
            case CHARGING_STATION -> new ChargerBlockEntity(pos, state);
            case ASSEMBLER -> new de.ipnats.hardwrought.automation.AssemblerBlockEntity(pos, state);
            case TESLA_TOWER -> new de.ipnats.hardwrought.tesla.TeslaTowerBlockEntity(pos, state);
        };
    }

    /** What it looks like working, on the client. */
    public void animate(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        switch (this) {
            case ARC_FLOODLIGHT -> {
                if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + 0.3, z, 0, 0.02, 0);
            }
            case ELECTRIC_HEATER, HEAT_LAMP -> {
                if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.WHITE_ASH, x, y + 0.5, z, 0, 0.04, 0);
            }
            case AIR_COOLER, FREEZER -> {
                if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.SNOWFLAKE, x, y + 0.5, z, 0, -0.02, 0);
            }
            case VENTILATION_FAN -> {
                if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.CLOUD, x, y, z, 0, 0.01, 0);
            }
            default -> { }
        }
    }
}
