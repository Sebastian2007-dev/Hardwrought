package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Function;

/**
 * The things of nuclear engineering that are not blocks: the minerals and chemicals of the fuel chain,
 * the small parts a fuel assembly, a control rod and a turbine are put together from, the fuel itself
 * before and after the reactor, and the counter that hears it.
 *
 * <p>The fuel chain, from the ground to the core (see docs/nuclear.md):
 * <pre>
 * uranium ore ─ crush ─ uranium powder ─ leach with sulfuric acid ─ yellowcake
 * fluorite ─ with sulfuric acid ─ hydrofluoric acid
 * yellowcake + hydrofluoric acid ─ chemical reactor ─ UF₆ gas
 * UF₆ ─ centrifuge cascade ─ enriched UF₆ + depleted uranium
 * enriched UF₆ + water ─ chemical reactor ─ uranium dioxide ─ press ─ green pellet ─ kiln ─ fuel pellet
 * thorium powder + caustic soda ─ chemical reactor ─ thorium dioxide + zircon
 * zircon + coke + chlorine ─ chemical reactor ─ zirconium sponge + hafnium
 * zirconium sponge + tin ─ large arc furnace ─ zircaloy ─ tube mill ─ cladding tube
 * </pre>
 */
public final class NuclearItems {
    // ---------------------------------------------------------------- minerals and chemicals

    /** Calcium fluoride, purple in the granite next to the uranium: the fluorine of the whole chain. */
    public static final Item FLUORITE = item("fluorite", 64);
    /** Fluorite with oil of vitriol, the fume caught in water and bottled. Eats glass, in principle. */
    public static final Item HYDROFLUORIC_ACID = item("hydrofluoric_acid", 16);
    /** The yellow uranium oxide out of the leach: what a uranium mine sells. */
    public static final Item YELLOWCAKE = item("yellowcake", 64);
    /** Enriched uranium as a black ceramic powder, the stuff of fuel pellets. */
    public static final Item URANIUM_DIOXIDE = item("uranium_dioxide", 64);
    /** Uranium dioxide pressed into a pellet but not yet fired: soft, it crumbles. */
    public static final Item GREEN_PELLET = item("green_pellet", 64);
    /** A fired pellet, the size of a fingertip; a few hundred of them in a rod. */
    public static final Item FUEL_PELLET = item("fuel_pellet", 64);
    /** What is left of natural uranium once the centrifuges have taken its uranium-235: dense, and good for shielding. */
    public static final Item DEPLETED_URANIUM = item("depleted_uranium", 64);
    /** Thorium out of monazite, cracked with caustic soda. Fertile, not fissile: it must be bred. */
    public static final Item THORIUM_DIOXIDE = item("thorium_dioxide", 64);
    public static final Item THORIUM_PELLET = item("thorium_pellet", 64);
    /** Zirconium silicate: the hard sand that is left when monazite is cracked. */
    public static final Item ZIRCON = item("zircon", 64);
    /** Zirconium out of the Kroll process: a grey sponge, to be remelted. */
    public static final Item ZIRCONIUM_SPONGE = item("zirconium_sponge", 64);
    /** Zirconium's twin, which follows it everywhere and swallows neutrons: control rods are made of it. */
    public static final Item HAFNIUM = item("hafnium", 64);

    // ---------------------------------------------------------------- small parts

    /** Zircaloy rolled and drawn into a thin seamless tube: a fuel rod's cladding. */
    public static final Item CLADDING_TUBE = item("cladding_tube", 64);
    /** The spring in a rod's gas plenum, holding the pellet stack together. */
    public static final Item PLENUM_SPRING = item("plenum_spring", 64);
    /** The zircaloy plug welded into each end of a rod. */
    public static final Item END_PLUG = item("end_plug", 64);
    /** The grid of springy zircaloy strips that holds the rods of an assembly apart. */
    public static final Item SPACER_GRID = item("spacer_grid", 64);
    public static final Item TOP_NOZZLE = item("top_nozzle", 16);
    /** The bottom nozzle, with a nichrome mesh across it to catch debris before it fret a rod through. */
    public static final Item BOTTOM_NOZZLE = item("bottom_nozzle", 16);
    /** One sealed rod: cladding, pellets, spring and plugs. */
    public static final Item FUEL_ROD = item("fuel_rod", 16);
    /** A rod of thorium pellets, set in the core to breed uranium-233 out of the neutrons it takes. */
    public static final Item THORIUM_BREEDER_ROD = item(ReactorFuelItem::new, "thorium_breeder_rod", 1);
    /** A breeder rod that has done its time: thorium with uranium-233 and fierce fission products in it. */
    public static final Item IRRADIATED_THORIUM_ROD = item("irradiated_thorium_rod", 1);
    /** Hafnium in a stainless tube: drops into the core, and the core stops. */
    public static final Item CONTROL_ROD = item("control_rod", 16);
    /** A stainless steel turbine blade, twisted along its length. */
    public static final Item TURBINE_BLADE = item("turbine_blade", 64);
    /** The impeller of the main coolant pump. */
    public static final Item IMPELLER = item("impeller", 16);
    /** A neutron detector: a chamber lined with uranium, where every fission is a pulse. */
    public static final Item FISSION_CHAMBER = item("fission_chamber", 16);
    /** A glass tube of thin gas round a wire: every ray that passes through it is a click. */
    public static final Item GEIGER_MUELLER_TUBE = item("geiger_mueller_tube", 16);
    /** A thin-walled tube of maraging steel, spun faster than sound on a magnetic bearing. */
    public static final Item CENTRIFUGE_ROTOR = item("centrifuge_rotor", 16);

    // ---------------------------------------------------------------- fuel

    /** A fresh fuel assembly: nine rods, two spacer grids and the nozzles. Burnt up in the core. */
    public static final Item FUEL_ASSEMBLY = item(ReactorFuelItem::new, "fuel_assembly", 1);
    /**
     * An assembly that has burnt its uranium-235: hot, and more radioactive than anything else in the
     * world. It is kept under water until it has cooled.
     */
    public static final Item SPENT_FUEL_ASSEMBLY = item("spent_fuel_assembly", 1);

    // ---------------------------------------------------------------- tools

    public static final Item GEIGER_COUNTER = item(GeigerCounterItem::new, "geiger_counter", 1);

    private NuclearItems() { }

    /** Touching the class registers everything in it. */
    public static void initialize() { }

    public static List<Item> items() {
        return List.of(FLUORITE, HYDROFLUORIC_ACID, YELLOWCAKE, URANIUM_DIOXIDE, GREEN_PELLET, FUEL_PELLET, DEPLETED_URANIUM,
                THORIUM_DIOXIDE, THORIUM_PELLET, ZIRCON, ZIRCONIUM_SPONGE, HAFNIUM,
                de.ipnats.hardwrought.metallurgy.Alloys.ZIRCALOY_INGOT,
                de.ipnats.hardwrought.smithing.MetalStock.rod("zircaloy"), de.ipnats.hardwrought.smithing.MetalStock.plate("zircaloy"),
                CLADDING_TUBE, PLENUM_SPRING, END_PLUG, SPACER_GRID, TOP_NOZZLE, BOTTOM_NOZZLE, FUEL_ROD,
                THORIUM_BREEDER_ROD, IRRADIATED_THORIUM_ROD, CONTROL_ROD, TURBINE_BLADE, IMPELLER, FISSION_CHAMBER,
                GEIGER_MUELLER_TUBE, CENTRIFUGE_ROTOR, FUEL_ASSEMBLY, SPENT_FUEL_ASSEMBLY, GEIGER_COUNTER);
    }

    private static Item item(String name, int stack) {
        return item(Item::new, name, stack);
    }

    private static Item item(Function<Item.Properties, Item> factory, String name, int stack) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().stacksTo(stack).setId(key)));
    }
}
