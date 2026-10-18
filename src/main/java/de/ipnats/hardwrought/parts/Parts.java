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

    private Parts() { }

    /** Touching the class registers the parts. */
    public static void initialize() { }

    /** Every part, for the creative tab. */
    public static List<Item> items() {
        return new ArrayList<>(List.of(REDSTONE_COIL, ELECTRIC_MOTOR, SPARK_PLUG, GLOW_PLUG, ENGINE_PISTON, CRANKSHAFT,
                FUEL_INJECTOR, BEARING, RUBBER_GASKET, SILICONE_GASKET, CIRCUIT_BOARD, RESISTOR, CAPACITOR, TRANSISTOR,
                MICROCHIP, QUARTZ_OSCILLATOR, BASIC_CIRCUIT, ADVANCED_CIRCUIT, ELITE_CIRCUIT, ELECTRICAL_STEEL,
                ELECTRICAL_STEEL_PLATE, TRANSFORMER_CORE, ENAMELLED_WIRE, WINDING, PORCELAIN_BLANK, PORCELAIN, FUSE_CARTRIDGE));
    }

    private static Item item(String name, int stack) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(stack).setId(key)));
    }
}
