package de.ipnats.hardwrought.materials;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.refining.RefineryProduct;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Set;

/**
 * The machines of the chemistry of the oil age. Each runs on current, a piece of work at a time, as the
 * crusher does, and differs in what it takes: items in two slots, a liquid by pipe, a gas by line; and
 * gives items, and for the electrolyser a gas out into a line.
 */
public enum ChemMachine {
    /** Two electrodes in brine: salt and water split into caustic soda and chlorine gas. */
    ELECTROLYZER("electrolyzer", 20.0),
    /** A heated, stirred steel vessel: plastic out of naphtha and LPG, rubber out of resin and sulfur, silicone. */
    CHEMICAL_REACTOR("chemical_reactor", 30.0),
    /** Carbon electrodes over a refractory hearth: quartz reduced with coke to silicon. Heavy on current. */
    ARC_FURNACE("arc_furnace", 10.0);

    /** One piece of work: what goes in, what comes out, and how long it takes at the rated voltage. */
    public record Recipe(String id, List<ItemStack> inputs, Fluid fluid, int fluidMb, GasKind gas, double gasUnits,
                         List<ItemStack> outputs, GasKind gasOut, double gasOutUnits, int ticks) { }

    private final String id;
    private final double ohms;
    private List<Recipe> recipes;

    ChemMachine(String id, double ohms) {
        this.id = id;
        this.ohms = ohms;
    }

    public String id() {
        return id;
    }

    /** What it is between the wire and the ground while it works. */
    public double ohms() {
        return ohms;
    }

    /** The liquids it takes by pipe; none for the arc furnace. */
    public Set<Fluid> fluids() {
        return switch (this) {
            case ELECTROLYZER -> Set.of(Fluids.WATER);
            case CHEMICAL_REACTOR -> Set.of(RefineryProduct.NAPHTHA.source(), Fluids.WATER);
            case ARC_FURNACE -> Set.of();
        };
    }

    /** The gases it takes by line; none where it takes none. */
    public Set<GasKind> gases() {
        return this == CHEMICAL_REACTOR ? Set.of(GasKind.LPG, GasKind.CHLORINE, GasKind.ENRICHED_HEXAFLUORIDE) : Set.of();
    }

    /** Built on first use: the items they name are registered after this class is loaded. */
    public List<Recipe> recipes() {
        if (recipes == null) {
            recipes = switch (this) {
                case ELECTROLYZER -> List.of(new Recipe("electrolysis/salt", List.of(new ItemStack(ModItems.SALT)),
                        Fluids.WATER, 250, null, 0, List.of(new ItemStack(Materials.CAUSTIC_SODA)), GasKind.CHLORINE, 4, 100));
                case CHEMICAL_REACTOR -> List.of(
                        new Recipe("chemical_reactor/plastic", List.of(), RefineryProduct.NAPHTHA.source(), 250, GasKind.LPG, 2,
                                List.of(new ItemStack(Materials.PLASTIC_PELLETS, 4)), null, 0, 80),
                        new Recipe("chemical_reactor/rubber", List.of(new ItemStack(Items.RESIN_CLUMP, 2), new ItemStack(ModItems.SULFUR)),
                                null, 0, null, 0, List.of(new ItemStack(Materials.RUBBER, 2)), null, 0, 80),
                        new Recipe("chemical_reactor/silicone", List.of(new ItemStack(Materials.RAW_SILICON)),
                                RefineryProduct.NAPHTHA.source(), 100, GasKind.CHLORINE, 4,
                                List.of(new ItemStack(Materials.SILICONE, 2)), null, 0, 120),
                        // Oil of vitriol: sulfur burnt and its fume taken up, bottled for a lead-acid pack.
                        new Recipe("chemical_reactor/battery_acid", List.of(new ItemStack(ModItems.SULFUR, 2), new ItemStack(Items.GLASS_BOTTLE)),
                                null, 0, null, 0, List.of(new ItemStack(de.ipnats.hardwrought.carried.CarriedItems.BATTERY_ACID)), null, 0, 100),
                        // Spirit of hartshorn: ammonia driven out of bone, pressed into a steel charge for a compressor.
                        new Recipe("chemical_reactor/refrigerant", List.of(new ItemStack(Items.BONE_MEAL, 4),
                                new ItemStack(de.ipnats.hardwrought.smithing.MetalStock.plate("steel"))), null, 0, null, 0,
                                List.of(new ItemStack(de.ipnats.hardwrought.parts.Parts.REFRIGERANT)), null, 0, 120),
                        // Medicine. Chlorine through caustic soda is bleach: antiseptic, bottled.
                        new Recipe("chemical_reactor/antiseptic", List.of(new ItemStack(Materials.CAUSTIC_SODA),
                                new ItemStack(Items.GLASS_BOTTLE)), null, 0, GasKind.CHLORINE, 2,
                                List.of(new ItemStack(ModItems.ANTISEPTIC)), null, 0, 80),
                        // The fever-lowering part of willow bark, drawn out with naphtha and pressed into pills.
                        new Recipe("chemical_reactor/fever_pills", List.of(new ItemStack(ModItems.WILLOW_BARK, 2)),
                                RefineryProduct.NAPHTHA.source(), 50, null, 0,
                                List.of(new ItemStack(ModItems.FEVER_PILLS, 4)), null, 0, 100),
                        // A mould grown on sugar, its medicine drawn out with naphtha.
                        new Recipe("chemical_reactor/antibiotics", List.of(new ItemStack(Items.BROWN_MUSHROOM),
                                new ItemStack(Items.SUGAR)), RefineryProduct.NAPHTHA.source(), 50, null, 0,
                                List.of(new ItemStack(ModItems.ANTIBIOTICS, 2)), null, 0, 160),
                        // The nuclear fuel chain (docs/nuclear.md). Uranium ore leached with oil of vitriol: yellowcake.
                        new Recipe("chemical_reactor/yellowcake", List.of(new ItemStack(de.ipnats.hardwrought.metallurgy.OrePowders.powder(
                                de.ipnats.hardwrought.metallurgy.Metal.URANIUM), 4), new ItemStack(de.ipnats.hardwrought.carried.CarriedItems.BATTERY_ACID)),
                                null, 0, null, 0, List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.YELLOWCAKE, 2)), null, 0, 160),
                        // Fluorite with oil of vitriol gives off hydrogen fluoride, caught in water.
                        new Recipe("chemical_reactor/hydrofluoric_acid", List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.FLUORITE, 2),
                                new ItemStack(de.ipnats.hardwrought.carried.CarriedItems.BATTERY_ACID)), null, 0, null, 0,
                                List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.HYDROFLUORIC_ACID)), null, 0, 100),
                        // Yellowcake through hydrofluoric acid and fluorine: uranium hexafluoride, a gas above 56 degrees.
                        new Recipe("chemical_reactor/uranium_hexafluoride", List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.YELLOWCAKE),
                                new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.HYDROFLUORIC_ACID, 2)), null, 0, null, 0,
                                List.of(new ItemStack(Items.GLASS_BOTTLE, 2)), GasKind.URANIUM_HEXAFLUORIDE, 10, 200),
                        // Enriched hexafluoride back to oxide with steam and hydrogen: the black powder of the pellets.
                        new Recipe("chemical_reactor/uranium_dioxide", List.of(), Fluids.WATER, 250, GasKind.ENRICHED_HEXAFLUORIDE, 1,
                                List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.URANIUM_DIOXIDE, 2)), null, 0, 120),
                        // Monazite cracked with hot caustic soda: thorium, and the zircon that will not dissolve.
                        new Recipe("chemical_reactor/monazite", List.of(new ItemStack(de.ipnats.hardwrought.metallurgy.OrePowders.powder(
                                de.ipnats.hardwrought.metallurgy.Metal.THORIUM), 2), new ItemStack(Materials.CAUSTIC_SODA)), null, 0, null, 0,
                                List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.THORIUM_DIOXIDE, 2), new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.ZIRCON)), null, 0, 140),
                        // The Kroll process: zircon chlorinated over coke, the chloride reduced to sponge; hafnium separated off.
                        new Recipe("chemical_reactor/zirconium", List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.ZIRCON, 4),
                                new ItemStack(de.ipnats.hardwrought.smeltery.SmelteryBlocks.COKE)), null, 0, GasKind.CHLORINE, 8,
                                List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.ZIRCONIUM_SPONGE, 3), new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.HAFNIUM)), null, 0, 200),
                        // A bred thorium rod dissolved and fluorinated: its uranium-233 comes off as hexafluoride, the rest is waste.
                        new Recipe("chemical_reactor/thorium_reprocessing", List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.IRRADIATED_THORIUM_ROD),
                                new ItemStack(de.ipnats.hardwrought.nuclear.NuclearItems.HYDROFLUORIC_ACID, 2)), null, 0, null, 0,
                                List.of(new ItemStack(de.ipnats.hardwrought.nuclear.NuclearBlocks.RADIOACTIVE_DEBRIS)),
                                GasKind.ENRICHED_HEXAFLUORIDE, 4, 300));
                case ARC_FURNACE -> List.of(new Recipe("arc_furnace/silicon",
                        List.of(new ItemStack(Items.QUARTZ, 2), new ItemStack(de.ipnats.hardwrought.smeltery.SmelteryBlocks.COKE)),
                        null, 0, null, 0, List.of(new ItemStack(Materials.RAW_SILICON)), null, 0, 160),
                        // Steel melted with silicon, for transformer and motor cores.
                        new Recipe("arc_furnace/electrical_steel", List.of(new ItemStack(de.ipnats.hardwrought.metallurgy.Alloys.STEEL_INGOT, 2),
                                new ItemStack(Materials.RAW_SILICON)), null, 0, null, 0,
                                List.of(new ItemStack(de.ipnats.hardwrought.parts.Parts.ELECTRICAL_STEEL, 2)), null, 0, 160));
            };
        }
        return recipes;
    }
}
