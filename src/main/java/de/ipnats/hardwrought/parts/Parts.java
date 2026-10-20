package de.ipnats.hardwrought.parts;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * The small parts machines are built from, rather than from whole blocks of other machines: a motor
 * is made once and goes into a crusher, a pump and a saw alike.
 *
 * <p>Electrical: the redstone coil, copper wire wound on an iron core, which is a motor's field, a
 * heating element and a relay's magnet; and the electric motor itself. Engine parts: pistons, a
 * crankshaft, bearings, a fuel injector, and the plugs that light the fuel — a spark plug for gasoline,
 * which wears and is changed, and a glow plug for diesel.
 *
 * <p>Seals: gaskets of rubber, and of silicone where it is hot or the pressure high. Electronics, in
 * the order they can be made: an etched circuit board, resistors and capacitors, transistors and
 * microchips cut from silicon, a quartz oscillator; and the three grades of circuit built from them,
 * which the faster machines need.
 *
 * <p>The motor, taken apart: a stator (the field), a rotor on its shaft, a commutator turned from copper,
 * graphite brushes turned from an electrode, which wear in a drive and are changed, and a housing. A
 * motor controller — a circuit and a bank of resistors — starts a drive gently and sets its speed.
 *
 * <p>The safe haven's parts (stage E3): tungsten filament and nichrome wire drawn on the wire drawer,
 * glass bulbs blown in the glass kiln, and what the devices are put together from: a bulb in its socket,
 * a reflector and a ballast for the arc lamp, a sensor and a relay, a heating element and a thermostat,
 * fan blades, a compressor with its ammonia charge and cooling coil, insulated panels for a cold store,
 * and a pressure vessel and gauge for the autoclave.
 *
 * <p>The grid: electrical steel, rolled thin and stacked into a transformer core; enamelled wire, wound
 * eight lengths to a winding on the coil winder; porcelain, fired from a blank, for whatever stands high
 * voltage; and the cartridge a fuse box takes.
 */
public final class Parts {
    public static final Item REDSTONE_COIL = item("redstone_coil", 64);
    public static final Item ELECTRIC_MOTOR = item("electric_motor", 16);
    public static final Item SPARK_PLUG = item("spark_plug", 16);
    public static final Item GLOW_PLUG = item("glow_plug", 16);
    public static final Item ENGINE_PISTON = item("engine_piston", 16);
    public static final Item CRANKSHAFT = item("crankshaft", 16);
    public static final Item FUEL_INJECTOR = item("fuel_injector", 16);
    public static final Item BEARING = item("bearing", 64);
    public static final Item RUBBER_GASKET = item("rubber_gasket", 64);
    public static final Item SILICONE_GASKET = item("silicone_gasket", 64);
    public static final Item CIRCUIT_BOARD = item("circuit_board", 64);
    public static final Item RESISTOR = item("resistor", 64);
    public static final Item CAPACITOR = item("capacitor", 64);
    public static final Item TRANSISTOR = item("transistor", 64);
    public static final Item MICROCHIP = item("microchip", 64);
    public static final Item QUARTZ_OSCILLATOR = item("quartz_oscillator", 64);
    public static final Item BASIC_CIRCUIT = item("basic_circuit", 64);
    public static final Item ADVANCED_CIRCUIT = item("advanced_circuit", 64);
    public static final Item ELITE_CIRCUIT = item("elite_circuit", 64);
    public static final Item ELECTRICAL_STEEL = item("electrical_steel", 64);
    public static final Item ELECTRICAL_STEEL_PLATE = item("electrical_steel_plate", 64);
    public static final Item TRANSFORMER_CORE = item("transformer_core", 16);
    public static final Item ENAMELLED_WIRE = item("enamelled_wire", 64);
    public static final Item WINDING = item("winding", 16);
    public static final Item PORCELAIN_BLANK = item("porcelain_blank", 64);
    public static final Item PORCELAIN = item("porcelain", 64);
    public static final Item FUSE_CARTRIDGE = item("fuse_cartridge", 64);
    public static final Item STATOR = item("stator", 16);
    public static final Item ROTOR = item("rotor", 16);
    public static final Item COMMUTATOR = item("commutator", 16);
    public static final Item GRAPHITE_BRUSH = item("graphite_brush", 64);
    public static final Item MOTOR_HOUSING = item("motor_housing", 16);
    public static final Item MOTOR_CONTROLLER = item("motor_controller", 16);
    public static final Item TUNGSTEN_FILAMENT = item("tungsten_filament", 64);
    public static final Item NICHROME_WIRE = item("nichrome_wire", 64);
    public static final Item GLASS_BULB = item("glass_bulb", 64);
    public static final Item GLASS_TUBE = item("glass_tube", 64);
    public static final Item LAMP_SOCKET = item("lamp_socket", 64);
    public static final Item LIGHT_BULB = item("light_bulb", 16);
    public static final Item REFLECTOR = item("reflector", 16);
    public static final Item BALLAST = item("ballast", 16);
    public static final Item ARC_ELECTRODE = item("arc_electrode", 16);
    public static final Item MOTION_SENSOR = item("motion_sensor", 16);
    public static final Item RELAY = item("relay", 64);
    public static final Item HEATING_ELEMENT = item("heating_element", 16);
    public static final Item BIMETAL_STRIP = item("bimetal_strip", 64);
    public static final Item THERMOSTAT = item("thermostat", 16);
    public static final Item FAN_BLADES = item("fan_blades", 16);
    public static final Item COMPRESSOR = item("compressor", 16);
    public static final Item REFRIGERANT = item("refrigerant", 16);
    public static final Item COOLING_COIL = item("cooling_coil", 16);
    public static final Item INSULATED_PANEL = item("insulated_panel", 64);
    public static final Item PRESSURE_VESSEL = item("pressure_vessel", 16);
    public static final Item PRESSURE_GAUGE = item("pressure_gauge", 16);

    private Parts() { }

    /** Touching the class registers the parts. */
    public static void initialize() { }

    /** Every part, for the creative tab. */
    public static List<Item> items() {
        return new ArrayList<>(List.of(REDSTONE_COIL, ELECTRIC_MOTOR, SPARK_PLUG, GLOW_PLUG, ENGINE_PISTON, CRANKSHAFT,
                FUEL_INJECTOR, BEARING, RUBBER_GASKET, SILICONE_GASKET, CIRCUIT_BOARD, RESISTOR, CAPACITOR, TRANSISTOR,
                MICROCHIP, QUARTZ_OSCILLATOR, BASIC_CIRCUIT, ADVANCED_CIRCUIT, ELITE_CIRCUIT, ELECTRICAL_STEEL,
                ELECTRICAL_STEEL_PLATE, TRANSFORMER_CORE, ENAMELLED_WIRE, WINDING, PORCELAIN_BLANK, PORCELAIN, FUSE_CARTRIDGE,
                STATOR, ROTOR, COMMUTATOR, GRAPHITE_BRUSH, MOTOR_HOUSING, MOTOR_CONTROLLER,
                TUNGSTEN_FILAMENT, NICHROME_WIRE, GLASS_BULB, GLASS_TUBE, LAMP_SOCKET, LIGHT_BULB, REFLECTOR, BALLAST, ARC_ELECTRODE,
                MOTION_SENSOR, RELAY, HEATING_ELEMENT, BIMETAL_STRIP, THERMOSTAT, FAN_BLADES, COMPRESSOR, REFRIGERANT,
                COOLING_COIL, INSULATED_PANEL, PRESSURE_VESSEL, PRESSURE_GAUGE));
    }

    private static Item item(String name, int stack) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(stack).setId(key)));
    }
}
