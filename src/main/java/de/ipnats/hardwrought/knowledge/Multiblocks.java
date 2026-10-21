package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The structures built of several blocks, as the compendium shows them: what goes where, layer by
 * layer from the ground up.
 *
 * <p>Each layer is a grid seen from above, one string per row running north to south, one character
 * per block running west to east. A space is air. Every other character stands for one kind of block,
 * or for any of several that do the same job.
 *
 * <p>The compendium offers the view on the page of any block of the structure, once the player has
 * studied a block of every kind in it: until then there are parts in it they would not recognise.
 */
public final class Multiblocks {
    /**
     * @param id      names the structure, and with it its translation key
     * @param layers  bottom to top, each a list of rows north to south
     * @param legend  what each character stands for; several blocks where any of them will do
     * @param formed  the part number a finished structure gives each block, by x, y, z, where the
     *                blocks change their look once the whole stands; empty where they do not
     */
    public record Multiblock(Identifier id, List<List<String>> layers, Map<Character, List<Identifier>> legend,
                             Optional<PartNumbers> formed) {
        public int width() {
            return layers.getFirst().getFirst().length();
        }

        public int depth() {
            return layers.getFirst().size();
        }

        public int height() {
            return layers.size();
        }

        /** The character at a position, or a space outside the structure. */
        public char at(int x, int y, int z) {
            if (y < 0 || y >= height() || z < 0 || z >= depth()) return ' ';
            String row = layers.get(y).get(z);
            return x < 0 || x >= row.length() ? ' ' : row.charAt(x);
        }

        /** Every block that belongs to it, whichever of the choices is taken. */
        public Set<Identifier> blocks() {
            Set<Identifier> all = new LinkedHashSet<>();
            legend.values().forEach(all::addAll);
            return all;
        }

        /** How many of each character one layer, or with -1 the whole structure, takes. */
        public Map<Character, Integer> count(int layer) {
            Map<Character, Integer> counts = new LinkedHashMap<>();
            for (char symbol : legend.keySet()) counts.put(symbol, 0);
            for (int y = 0; y < height(); y++) {
                if (layer >= 0 && y != layer) continue;
                for (String row : layers.get(y)) {
                    for (char c : row.toCharArray()) {
                        if (c != ' ') counts.merge(c, 1, Integer::sum);
                    }
                }
            }
            return counts;
        }
    }

    /** The part number of the block at x, y, z of a finished structure. */
    @FunctionalInterface
    public interface PartNumbers {
        int part(int x, int y, int z);
    }

    public static final Multiblock HOODED_FORGE = new Multiblock(Hardwrought.id("hooded_forge"),
            List.of(List.of("FFF", "FFF", "FFF"), List.of("   ", " H ", "   ")),
            legend('F', List.of(Hardwrought.id("forge")), 'H', List.of(Hardwrought.id("forge_hood"))),
            Optional.empty());

    /** The smallest smeltery: a tank one block wide and two high, a drain to pour from, glass to look in. */
    public static final Multiblock SMELTERY = new Multiblock(Hardwrought.id("smeltery"),
            List.of(List.of("BBB", "BBB", "BBB"), List.of("BCB", "B D", "BBB"), List.of("BGB", "G G", "BGB")),
            legend('B', List.of(Hardwrought.id("smeltery_bricks")), 'C', List.of(Hardwrought.id("smeltery_controller")),
                    'D', List.of(Hardwrought.id("smeltery_drain")), 'G', List.of(Hardwrought.id("smeltery_glass"))),
            Optional.empty());

    /**
     * The large arc furnace: refractory casing round a hollow hearth, a graphite electrode in its roof,
     * the controller in the middle of one side, and an item port and a power port — drawn at one corner,
     * but they may take the place of any casing block.
     */
    public static final Multiblock LARGE_ARC_FURNACE = new Multiblock(Hardwrought.id("large_arc_furnace"),
            List.of(List.of("RRR", "RRR", "IRP"), List.of("RRR", "R R", "RKR"), List.of("RRR", "RER", "RRR")),
            legend('R', List.of(Hardwrought.id("refractory_casing")), 'K', List.of(Hardwrought.id("arc_furnace_controller")),
                    'E', List.of(Hardwrought.id("graphite_electrode")), 'I', List.of(Hardwrought.id("item_port")),
                    'P', List.of(Hardwrought.id("power_port"))),
            Optional.empty());

    /**
     * The large diesel generator: engine casing round an engine core, the controller in the middle of one
     * side, and a fluid port and a power port, drawn at one corner but free to sit anywhere in the hull.
     */
    public static final Multiblock LARGE_DIESEL = new Multiblock(Hardwrought.id("large_diesel_generator"),
            List.of(List.of("CCC", "CCC", "FCP"), List.of("CCC", "CMC", "CKC"), List.of("CCC", "CCC", "CCC")),
            legend('C', List.of(Hardwrought.id("engine_casing")), 'K', List.of(Hardwrought.id("large_diesel_controller")),
                    'M', List.of(Hardwrought.id("engine_core")), 'F', List.of(Hardwrought.id("fluid_port")),
                    'P', List.of(Hardwrought.id("power_port"))),
            Optional.empty());

    /**
     * The reactor: a 5×5×5 vessel. Graphite above and below the core, nine fuel channels in it, up to nine
     * control rod drives in the head. The ports may take the place of any wall block but the head's middle.
     */
    public static final Multiblock REACTOR = new Multiblock(Hardwrought.id("reactor"),
            List.of(List.of("WWWWW", "WWWWW", "WWWWW", "WWWWW", "WWWWW"),
                    List.of("WWWWW", "WGGGW", "WGGGW", "WGGGW", "FWIWP"),
                    List.of("WWWWW", "WCCCW", "WCCCW", "WCCCW", "WWKWW"),
                    List.of("WWWWW", "WGGGW", "WGGGW", "WGGGW", "WWSWW"),
                    List.of("WWWWW", "WDDDW", "WDDDW", "WDDDW", "WWWWW")),
            legend('W', List.of(Hardwrought.id("reactor_vessel_wall")), 'K', List.of(Hardwrought.id("reactor_controller")),
                    'C', List.of(Hardwrought.id("fuel_channel")), 'G', List.of(Hardwrought.id("graphite_electrode")),
                    'D', List.of(Hardwrought.id("control_rod_drive"), Hardwrought.id("reactor_vessel_wall")),
                    'F', List.of(Hardwrought.id("fluid_port")), 'S', List.of(Hardwrought.id("gas_port")),
                    'I', List.of(Hardwrought.id("item_port")), 'P', List.of(Hardwrought.id("power_port"))),
            Optional.empty());

    /** The steam turbine: casing round a rotor, a condenser under it, steam in, water and current out. */
    public static final Multiblock STEAM_TURBINE = new Multiblock(Hardwrought.id("steam_turbine"),
            List.of(List.of("TTT", "TNT", "FTP"), List.of("TTT", "TRT", "TKT"), List.of("TTT", "TTT", "TST")),
            legend('T', List.of(Hardwrought.id("turbine_casing")), 'K', List.of(Hardwrought.id("turbine_controller")),
                    'R', List.of(Hardwrought.id("turbine_rotor")), 'N', List.of(Hardwrought.id("condenser")),
                    'F', List.of(Hardwrought.id("fluid_port")), 'S', List.of(Hardwrought.id("gas_port")),
                    'P', List.of(Hardwrought.id("power_port"))),
            Optional.empty());

    /**
     * The centrifuge cascade: five gas centrifuges in the middle layer, the feed in through a gas port in a
     * wall or the floor, the product out through one in the roof, the tails out through an item port.
     */
    public static final Multiblock CENTRIFUGE_CASCADE = new Multiblock(Hardwrought.id("centrifuge_cascade"),
            List.of(List.of("CCC", "CSC", "IPC"), List.of("ZCZ", "CZC", "ZKZ"), List.of("CCC", "COC", "CCC")),
            legend('C', List.of(Hardwrought.id("centrifuge_casing")), 'K', List.of(Hardwrought.id("centrifuge_controller")),
                    'Z', List.of(Hardwrought.id("gas_centrifuge")), 'S', List.of(Hardwrought.id("gas_port")),
                    'O', List.of(Hardwrought.id("gas_port")), 'I', List.of(Hardwrought.id("item_port")),
                    'P', List.of(Hardwrought.id("power_port"))),
            Optional.empty());

    /**
     * The battery bank: a 5×5×5 hull of casing with up to 27 cell stacks inside, the controller in the
     * middle of one side, and a power port anywhere in the casing.
     */
    public static final Multiblock BATTERY_BANK = new Multiblock(Hardwrought.id("battery_bank"),
            List.of(List.of("CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC"),
                    List.of("CCCCC", "CSSSC", "CSSSC", "CSSSC", "CCCCP"),
                    List.of("CCCCC", "CSSSC", "CSSSC", "CSSSC", "CCKCC"),
                    List.of("CCCCC", "CSSSC", "CSSSC", "CSSSC", "CCCCC"),
                    List.of("CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC")),
            legend('C', List.of(Hardwrought.id("battery_bank_casing")), 'K', List.of(Hardwrought.id("battery_bank_controller")),
                    'S', List.of(Hardwrought.id("battery_cell_stack")), 'P', List.of(Hardwrought.id("power_port"))),
            Optional.empty());

    /**
     * The seal in the abyss: the seal stone between four pillars of worked deepslate, two blocks out at
     * the corners, each with a soul lantern on it. A nether star on the stone opens the way down.
     */
    public static final Multiblock UNDERWORLD_SEAL = new Multiblock(Hardwrought.id("underworld_seal"),
            List.of(List.of("P   P", "     ", "  S  ", "     ", "P   P"), List.of("L   L", "     ", "     ", "     ", "L   L")),
            legend('S', List.of(Hardwrought.id("seal_stone")),
                    'P', List.of(Identifier.withDefaultNamespace("polished_deepslate"), Identifier.withDefaultNamespace("chiseled_deepslate"),
                            Identifier.withDefaultNamespace("deepslate_bricks"), Identifier.withDefaultNamespace("deepslate_tiles"),
                            Identifier.withDefaultNamespace("cracked_deepslate_bricks"), Identifier.withDefaultNamespace("cracked_deepslate_tiles")),
                    'L', List.of(Identifier.withDefaultNamespace("soul_lantern"))),
            Optional.empty());

    /** Every structure: the machines, the seal, and the three temples of the end of the game. */
    public static final List<Multiblock> ALL = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(HOODED_FORGE, SMELTERY, LARGE_ARC_FURNACE, LARGE_DIESEL, BATTERY_BANK, REACTOR, STEAM_TURBINE,
                    CENTRIFUGE_CASCADE, UNDERWORLD_SEAL),
            de.ipnats.hardwrought.ritual.Temple.multiblocks().stream()).toList();

    private Multiblocks() { }

    /** The legend in the order it is written, so the compendium lists it the same way every time. */
    private static Map<Character, List<Identifier>> legend(Object... pairs) {
        Map<Character, List<Identifier>> legend = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<Identifier> blocks = (List<Identifier>) pairs[i + 1];
            legend.put((Character) pairs[i], blocks);
        }
        return java.util.Collections.unmodifiableMap(legend);
    }

    public static Multiblock byId(Identifier id) {
        for (Multiblock multiblock : ALL) {
            if (multiblock.id().equals(id)) return multiblock;
        }
        return null;
    }

    /** The structures a block is part of. */
    public static List<Multiblock> containing(Identifier block) {
        List<Multiblock> found = new ArrayList<>();
        for (Multiblock multiblock : ALL) {
            if (multiblock.blocks().contains(block)) found.add(multiblock);
        }
        return found;
    }
}
