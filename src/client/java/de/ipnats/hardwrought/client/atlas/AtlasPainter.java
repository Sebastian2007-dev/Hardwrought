package de.ipnats.hardwrought.client.atlas;

import de.ipnats.hardwrought.atlas.Atlas;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;

/**
 * Paints a page of the atlas the way a cartographer would have: watercolour washes on parchment, a line
 * of ink along every coast, the edge of what is known burnt and ragged, and the land told by little
 * drawings — trees in the forests, peaks on the rock, waves on open water, tufts on the grass, houses
 * where something has been built.
 *
 * <p>What the server keeps is a map colour for each square of four blocks. That is read back into a kind
 * of land, and the kind decides the wash and the drawings; the brightness the server kept for the slope
 * shades the wash, so the relief still shows.
 *
 * <p>Under a roof of rock the server draws the floor at the bearer's height and marks the rock they would
 * walk into as wall, in the darkest shade of its colour. Walls are hatched dark and inked along the
 * floor they close in, and the rock underfoot gets rubble, not mountain peaks.
 *
 * <p>Everything here is laid out in pixels counted from the world, not from the screen: the grain of the
 * paper, the ragged edge and where a tree stands stay where they are while the page is dragged.
 */
final class AtlasPainter {
    enum Land {
        WATER(0xFF8FB3B8), FOREST(0xFF8DA570), GRASS(0xFFB9C489), SAND(0xFFE2CB8E), SNOW(0xFFF1EEE6),
        ROCK(0xFFB5AC9A), DIRT(0xFFC2A77E), BUILT(0xFFC9A27A), LAVA(0xFFD9784A), NETHER(0xFFA8665A),
        WALL(0xFF6B6153), OTHER(0);

        final int wash;

        Land(int wash) {
            this.wash = wash;
        }
    }

    static final int PAPER = 0xFFE8D9B0;
    private static final int EDGE = 0xFF8A6A40, SCORCH = 0xFFD2BE90;
    private static final int COAST = 0xFF3E4A52, SHALLOW = 0xFFA9C7C2;
    private static final int WAVE_INK = 0xFF55808C;
    private static final int TREE_FILL = 0xFF5E7F45, TREE_INK = 0xFF34482A;
    private static final int TUFT_INK = 0xFF6F8250;
    private static final int SAND_DOT = 0xFFB59A5C, SNOW_DOT = 0xFFB8C8D6;
    private static final int PEAK_FILL = 0xFFD8D0BE, PEAK_SHADE = 0xFF8F8574, PEAK_INK = 0xFF4A4034;
    private static final int HOUSE_FILL = 0xFFB06A48;
    private static final int WALL_HATCH = 0xFF4E463C, WALL_INK = 0xFF2E2820;
    private static final int RUBBLE = 0xFF857B6A, EMBER = 0xFF7A3A30, LAVA_INK = 0xFF9A3A1E;
    /** How far apart, in pixels, the little drawings may stand. */
    private static final int CELL = 9;

    private static final int UNKNOWN = -1;

    /** The squares in view, each as its land's ordinal times four plus the brightness of its slope. */
    private int[] squares = new int[0];
    private long squareX0, squareZ0;
    private int squaresWide, squaresHigh;
    /** Whether the page is of a roofed dimension, drawn at the bearer's height. */
    private boolean cave;

    /**
     * Paints the page into {@code pixels}, row by row.
     *
     * @param worldX0 the world pixel the page's left edge falls on: a block at x lies on world pixel x / bpp
     * @param bpp     blocks to a pixel
     */
    void paint(int[] pixels, int width, int height, long worldX0, long worldZ0, double bpp, Map<Long, byte[]> tiles,
               boolean cave) {
        this.cave = cave;
        survey(width, height, worldX0, worldZ0, bpp, tiles);
        boolean[] known = new boolean[width * height];
        Land[] lands = new Land[width * height];
        for (int py = 0; py < height; py++) {
            long wy = worldZ0 + py;
            for (int px = 0; px < width; px++) {
                long wx = worldX0 + px;
                long hash = mix(wx >> 1, wy >> 1);
                // The edge of what is known is ragged: a pixel near it looks a little way off for its land.
                int jx = (int) Math.floorMod(hash, 5) - 2, jy = (int) Math.floorMod(hash >> 8, 5) - 2;
                int here = square(wx, wy, bpp), near = square(wx + jx, wy + jy, bpp);
                int index = py * width + px;
                if (near == UNKNOWN) {
                    pixels[index] = grain(PAPER, wx, wy);
                    continue;
                }
                int value = here == UNKNOWN ? near : here;
                Land land = Land.values()[value >> 2];
                known[index] = true;
                lands[index] = land;
                // Rock is hatched, as engravers hatch a section through it.
                int wash = land == Land.WALL ? (Math.floorMod(wx + wy, 4) == 0 ? WALL_HATCH : land.wash)
                        : relief(land == Land.OTHER ? PAPER : land.wash, value & 3);
                pixels[index] = grain(wash, wx, wy);
            }
        }
        inkEdges(pixels, known, lands, width, height);
        stamp(pixels, known, lands, width, height, worldX0, worldZ0, bpp, cave);
    }

    // ---------------------------------------------------------------- the land

    /** Reads every square in view out of the tiles once, so a pixel costs an array lookup. */
    private void survey(int width, int height, long worldX0, long worldZ0, double bpp, Map<Long, byte[]> tiles) {
        squareX0 = squareOf(worldX0 - 4, bpp) - 1;
        squareZ0 = squareOf(worldZ0 - 4, bpp) - 1;
        squaresWide = (int) (squareOf(worldX0 + width + 4, bpp) - squareX0) + 2;
        squaresHigh = (int) (squareOf(worldZ0 + height + 4, bpp) - squareZ0) + 2;
        if (squares.length < squaresWide * squaresHigh) squares = new int[squaresWide * squaresHigh];
        int[] raw = new int[squaresWide * squaresHigh];
        long lastKey = Long.MIN_VALUE;
        byte[] tile = null;
        for (int row = 0; row < squaresHigh; row++) {
            long sz = squareZ0 + row;
            for (int column = 0; column < squaresWide; column++) {
                long sx = squareX0 + column;
                long key = Atlas.key((int) (sx >> 2), (int) (sz >> 2));
                if (key != lastKey) {
                    lastKey = key;
                    tile = tiles.get(key);
                }
                raw[row * squaresWide + column] = tile == null ? UNKNOWN : tile[(int) ((sz & 3) * Atlas.SQUARES + (sx & 3))] & 0xFF;
            }
        }
        for (int row = 0; row < squaresHigh; row++) {
            for (int column = 0; column < squaresWide; column++) {
                int packed = raw[row * squaresWide + column];
                squares[row * squaresWide + column] = packed == UNKNOWN ? UNKNOWN
                        : classify(packed, raw, row, column).ordinal() * 4 + (packed & 3);
            }
        }
    }

    /**
     * What kind of land a map colour is. Leaves and the plants on a meadow share one colour, so a square
     * of plants is forest only where most of what is round it is plants too.
     */
    private Land classify(int packed, int[] raw, int row, int column) {
        MapColor colour = MapColor.byId(packed >> 2);
        if ((packed & 3) == MapColor.Brightness.LOWEST.id && colour != MapColor.NONE) return Land.WALL;
        if (colour == MapColor.WATER) return Land.WATER;
        if (colour == MapColor.PLANT) {
            int plants = 0;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int r = row + dz, c = column + dx;
                    if ((dx != 0 || dz != 0) && r >= 0 && c >= 0 && r < squaresHigh && c < squaresWide
                            && raw[r * squaresWide + c] != UNKNOWN && MapColor.byId(raw[r * squaresWide + c] >> 2) == MapColor.PLANT) {
                        plants++;
                    }
                }
            }
            return plants >= 5 ? Land.FOREST : Land.GRASS;
        }
        if (colour == MapColor.GRASS || colour == MapColor.COLOR_LIGHT_GREEN || colour == MapColor.COLOR_GREEN
                || colour == MapColor.GLOW_LICHEN) return Land.GRASS;
        if (colour == MapColor.SAND || colour == MapColor.QUARTZ) return Land.SAND;
        if (colour == MapColor.SNOW || colour == MapColor.ICE) return Land.SNOW;
        if (colour == MapColor.STONE || colour == MapColor.DEEPSLATE || colour == MapColor.RAW_IRON
                || colour == MapColor.COLOR_GRAY || colour == MapColor.COLOR_LIGHT_GRAY) return Land.ROCK;
        if (colour == MapColor.DIRT || colour == MapColor.PODZOL || colour == MapColor.CLAY
                || colour == MapColor.COLOR_BROWN) return Land.DIRT;
        if (colour == MapColor.FIRE) return Land.LAVA;
        if (colour == MapColor.NETHER || colour == MapColor.CRIMSON_NYLIUM || colour == MapColor.CRIMSON_STEM
                || colour == MapColor.CRIMSON_HYPHAE || colour == MapColor.WARPED_NYLIUM || colour == MapColor.WARPED_STEM
                || colour == MapColor.WARPED_HYPHAE || colour == MapColor.WARPED_WART_BLOCK) return Land.NETHER;
        // Basalt and blackstone, in the Nether, are the land and not something built on it.
        if (cave && (colour == MapColor.COLOR_BLACK || colour == MapColor.TERRACOTTA_BLACK)) return Land.ROCK;
        if (colour == MapColor.WOOD || colour == MapColor.WOOL || colour == MapColor.METAL) return Land.BUILT;
        // Badlands are coloured clay; the dyed colours are what people put there.
        if (colour.id >= MapColor.TERRACOTTA_WHITE.id && colour.id <= MapColor.TERRACOTTA_BLACK.id) return Land.DIRT;
        if (colour.id >= MapColor.COLOR_ORANGE.id && colour.id <= MapColor.COLOR_BLACK.id) return Land.BUILT;
        return Land.OTHER;
    }

    private static long squareOf(long worldPixel, double bpp) {
        return Math.floorDiv((long) Math.floor(worldPixel * bpp), Atlas.BLOCKS);
    }

    /** The land under a world pixel, or {@link #UNKNOWN}. */
    private int square(long wx, long wy, double bpp) {
        long column = squareOf(wx, bpp) - squareX0, row = squareOf(wy, bpp) - squareZ0;
        if (column < 0 || row < 0 || column >= squaresWide || row >= squaresHigh) return UNKNOWN;
        return squares[(int) (row * squaresWide + column)];
    }

    // ---------------------------------------------------------------- ink

    /** The edge of the known burnt dark and singed behind; every coast drawn in ink on its land side. */
    private static void inkEdges(int[] pixels, boolean[] known, Land[] lands, int width, int height) {
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int index = py * width + px;
                if (!known[index]) continue;
                boolean edge = false, scorched = false, coast = false, shallow = false, cliff = false;
                Land land = lands[index];
                for (int d = 1; d <= 2; d++) {
                    for (int[] step : STEPS) {
                        int x = px + step[0] * d, y = py + step[1] * d;
                        if (x < 0 || y < 0 || x >= width || y >= height) continue;
                        int other = y * width + x;
                        if (!known[other]) {
                            if (d == 1) edge = true;
                            else scorched = true;
                            continue;
                        }
                        if (land == Land.WALL && lands[other] != Land.WALL && d == 1) cliff = true;
                        boolean water = lands[other] == Land.WATER;
                        if (land != Land.WATER && water && d == 1) coast = true;
                        if (land == Land.WATER && !water) shallow = true;
                    }
                }
                if (edge) pixels[index] = EDGE;
                else if (cliff) pixels[index] = WALL_INK;
                else if (coast) pixels[index] = COAST;
                else if (scorched) pixels[index] = blend(pixels[index], SCORCH, 0.6);
                else if (shallow) pixels[index] = SHALLOW;
            }
        }
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // ---------------------------------------------------------------- drawings

    /** Trees, peaks, waves, tufts, dots and houses, at most one to a cell of the world's pixels. */
    private static void stamp(int[] pixels, boolean[] known, Land[] lands, int width, int height,
                              long worldX0, long worldZ0, double bpp, boolean cave) {
        long cellX0 = Math.floorDiv(worldX0, CELL) - 1, cellZ0 = Math.floorDiv(worldZ0, CELL) - 1;
        long cellX1 = Math.floorDiv(worldX0 + width, CELL) + 1, cellZ1 = Math.floorDiv(worldZ0 + height, CELL) + 1;
        for (long cz = cellZ0; cz <= cellZ1; cz++) {
            for (long cx = cellX0; cx <= cellX1; cx++) {
                long hash = mix(cx * 31 + 7, cz * 17 + 3);
                int ox = (int) Math.floorMod(hash, 4), oz = (int) Math.floorMod(hash >> 4, 3);
                int left = (int) (cx * CELL + ox - worldX0), top = (int) (cz * CELL + oz - worldZ0);
                int centre = at(left + 3, top + 3, width, height);
                if (centre < 0 || !known[centre]) continue;
                Land land = lands[centre];
                int roll = (int) Math.floorMod(hash >> 12, 100);
                switch (land) {
                    case FOREST -> draw(pixels, known, lands, width, height, left + 1, top,
                            roll < 50 ? AtlasIcons.TREE : AtlasIcons.PINE, land, TREE_INK, TREE_FILL, TREE_FILL);
                    case ROCK -> {
                        // Underground the rock is the floor, not a mountain: rubble on it.
                        if (cave) {
                            if (roll < 55) dots(pixels, known, lands, width, height, left, top, hash, land, RUBBLE);
                        } else if (roll < 70) draw(pixels, known, lands, width, height, left - 1, top + 2, AtlasIcons.PEAK,
                                land, PEAK_INK, PEAK_FILL, PEAK_SHADE);
                    }
                    case SNOW -> {
                        if (roll < 35) draw(pixels, known, lands, width, height, left - 1, top + 2, AtlasIcons.PEAK,
                                land, PEAK_INK, 0xFFFFFFFF, SNOW_DOT);
                        else if (roll < 60) dots(pixels, known, lands, width, height, left, top, hash, land, SNOW_DOT);
                    }
                    case WATER -> {
                        if (roll < 40) draw(pixels, known, lands, width, height, left + 1, top + 3, AtlasIcons.WAVE,
                                land, WAVE_INK, WAVE_INK, WAVE_INK);
                    }
                    case GRASS -> {
                        if (roll < 30) draw(pixels, known, lands, width, height, left + 2, top + 3, AtlasIcons.TUFT,
                                land, TUFT_INK, TUFT_INK, TUFT_INK);
                    }
                    case SAND, DIRT -> {
                        if (roll < 55) dots(pixels, known, lands, width, height, left, top, hash, land, SAND_DOT);
                    }
                    case NETHER -> {
                        if (roll < 55) dots(pixels, known, lands, width, height, left, top, hash, land, EMBER);
                    }
                    case LAVA -> {
                        if (roll < 40) draw(pixels, known, lands, width, height, left + 1, top + 3, AtlasIcons.WAVE,
                                land, LAVA_INK, LAVA_INK, LAVA_INK);
                    }
                    case BUILT -> {
                        if (roll < 60) draw(pixels, known, lands, width, height, left + 1, top + 2, AtlasIcons.HOUSE,
                                land, AtlasIcons.INK, HOUSE_FILL, HOUSE_FILL);
                    }
                    default -> { }
                }
            }
        }
    }

    /** Stamps a drawing, if every pixel it would cover is the same land and not an edge or a coast. */
    private static void draw(int[] pixels, boolean[] known, Land[] lands, int width, int height, int left, int top,
                             String[] rows, Land land, int ink, int fill, int shade) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                int index = at(left + column, top + row, width, height);
                if (index < 0 || !known[index] || lands[index] != land) return;
            }
        }
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                int colour = switch (rows[row].charAt(column)) {
                    case '#' -> ink;
                    case 'o' -> fill;
                    case 's' -> shade;
                    default -> 0;
                };
                if (colour != 0) pixels[(top + row) * width + left + column] = colour;
            }
        }
    }

    private static void dots(int[] pixels, boolean[] known, Land[] lands, int width, int height, int left, int top,
                             long hash, Land land, int colour) {
        for (int dot = 0; dot < 3; dot++) {
            int index = at(left + (int) Math.floorMod(hash >> (16 + dot * 6), 7), top + (int) Math.floorMod(hash >> (19 + dot * 6), 7), width, height);
            if (index >= 0 && known[index] && lands[index] == land) pixels[index] = colour;
        }
    }

    private static int at(int x, int y, int width, int height) {
        return x < 0 || y < 0 || x >= width || y >= height ? -1 : y * width + x;
    }

    // ---------------------------------------------------------------- colour

    /** The slope the server kept: lighter rising to the north, darker falling. */
    private static int relief(int argb, int brightness) {
        return switch (brightness) {
            case 2 -> blend(argb, 0xFFFFFFFF, 0.10);
            case 0 -> AtlasIcons.shade(argb, 0.10);
            case 3 -> AtlasIcons.shade(argb, 0.18);
            default -> argb;
        };
    }

    /** The grain of the paper: each pixel a shade off, fixed to its place in the world. */
    private static int grain(int argb, long wx, long wy) {
        int nudge = (int) Math.floorMod(mix(wx, wy), 9) - 4;
        int r = Math.clamp(((argb >> 16) & 0xFF) + nudge, 0, 255);
        int g = Math.clamp(((argb >> 8) & 0xFF) + nudge, 0, 255);
        int b = Math.clamp((argb & 0xFF) + nudge, 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int blend(int from, int to, double share) {
        int r = (int) (((from >> 16) & 0xFF) * (1 - share) + ((to >> 16) & 0xFF) * share);
        int g = (int) (((from >> 8) & 0xFF) * (1 - share) + ((to >> 8) & 0xFF) * share);
        int b = (int) ((from & 0xFF) * (1 - share) + (to & 0xFF) * share);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }
}
