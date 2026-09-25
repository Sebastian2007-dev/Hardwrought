package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.machinery.OreDrillBlockEntity;
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
 * or for any of several that do the same job, like the five metals of a drill frame.
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

    private static final Identifier ORE_DRILL = Hardwrought.id("ore_drill");

    public static final Multiblock ORE_DRILL_RIG = new Multiblock(ORE_DRILL,
            List.of(List.of("FFF", "FHF", "FFF"), List.of("FFF", "FFF", "FFF")),
            legend('H', List.of(ORE_DRILL),
                    'F', List.of(Hardwrought.id("drill_frame_bronze"), Hardwrought.id("drill_frame_iron"),
                            Hardwrought.id("drill_frame_nickel"), Hardwrought.id("drill_frame_chromium"),
                            Hardwrought.id("drill_frame_titanium"))),
            Optional.of((x, y, z) -> {
                // The same order OreDrillBlockEntity counts its frame in, with the head at 1, 0, 1.
                var positions = OreDrillBlockEntity.framePositions(net.minecraft.core.BlockPos.ZERO);
                return positions.indexOf(new net.minecraft.core.BlockPos(x - 1, y, z - 1)) + 1;
            }));

    public static final Multiblock HOODED_FORGE = new Multiblock(Hardwrought.id("hooded_forge"),
            List.of(List.of("FFF", "FFF", "FFF"), List.of("   ", " H ", "   ")),
            legend('F', List.of(Hardwrought.id("forge")), 'H', List.of(Hardwrought.id("forge_hood"))),
            Optional.empty());

    public static final List<Multiblock> ALL = List.of(ORE_DRILL_RIG, HOODED_FORGE);

    private Multiblocks() { }

    /** The legend in the order it is written, so the compendium lists it the same way every time. */
    private static Map<Character, List<Identifier>> legend(char a, List<Identifier> first, char b, List<Identifier> second) {
        Map<Character, List<Identifier>> legend = new LinkedHashMap<>();
        legend.put(a, first);
        legend.put(b, second);
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
