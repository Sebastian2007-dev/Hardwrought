package de.ipnats.hardwrought.client.atlas;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The little drawings of the atlas, each written out as a grid of characters: {@code #} is ink, {@code o}
 * the colour of the thing, {@code s} its shade, and anything else is left as the paper. Marks are drawn
 * on the screen over the map; the trees, peaks and waves are stamped into the map itself (see
 * {@link AtlasPainter}).
 */
final class AtlasIcons {
    static final int INK = 0xFF2A2018;

    /** A symbol the bearer can mark a place with, in the order of {@code Atlas.Marker#icon}. */
    record Icon(String key, int fill, String[] rows) { }

    static final Icon[] MARKS = {
            new Icon("home", 0xFFC0603A, new String[] {
                    "....#....",
                    "...#o#...",
                    "..#ooo#..",
                    ".#ooooo#.",
                    "#########",
                    ".#sssss#.",
                    ".#ss#ss#.",
                    ".#ss#ss#.",
                    ".#######."}),
            new Icon("camp", 0xFFA8B070, new String[] {
                    "....#....",
                    "...#o#...",
                    "...#o#...",
                    "..#ooo#..",
                    "..#o#o#..",
                    ".#oo#oo#.",
                    ".#o#s#o#.",
                    "#o#sss#o#",
                    "#########"}),
            new Icon("mine", 0xFFA0A6AC, new String[] {
                    ".#######.",
                    "#ooooooo#",
                    "#o#####o#",
                    "##.#s#.##",
                    "...#s#...",
                    "...#s#...",
                    "...#s#...",
                    "...#s#...",
                    "...###..."}),
            new Icon("cave", 0xFF8A7A66, new String[] {
                    "..#####..",
                    ".#ooooo#.",
                    "#oo###oo#",
                    "#o#...#o#",
                    "#o#...#o#",
                    "#o#...#o#",
                    "#o#...#o#",
                    "#o#...#o#",
                    "###...###"}),
            new Icon("flag", 0xFF3A70C0, new String[] {
                    "#######..",
                    "#ooooo#..",
                    "#ooooo#..",
                    "#ooooo#..",
                    "#######..",
                    "#........",
                    "#........",
                    "#........",
                    "##......."}),
            new Icon("danger", 0xFFE0B030, new String[] {
                    "....#....",
                    "...#o#...",
                    "...#o#...",
                    "..#o#o#..",
                    "..#o#o#..",
                    ".#oo#oo#.",
                    ".#ooooo#.",
                    "#ooo#ooo#",
                    "#########"}),
            new Icon("star", 0xFFF2D04A, new String[] {
                    "....#....",
                    "...#o#...",
                    "####o####",
                    "#ooooooo#",
                    ".#ooooo#.",
                    "..#ooo#..",
                    ".#oo#oo#.",
                    ".#o#.#o#.",
                    "##.....##"}),
            new Icon("portal", 0xFF8A4FC0, new String[] {
                    "..#####..",
                    ".#ooooo#.",
                    "#oo###oo#",
                    "#o#sss#o#",
                    "#o#sss#o#",
                    "#o#sss#o#",
                    "#oo###oo#",
                    ".#ooooo#.",
                    "..#####.."})
    };

    /** Where its bearer died. */
    static final Icon SKULL = new Icon("death", 0xFFE8E2D0, new String[] {
            "..#####..",
            ".#ooooo#.",
            "#ooooooo#",
            "#o##o##o#",
            "#o##o##o#",
            "#ooo#ooo#",
            ".#ooooo#.",
            "..#o#o#..",
            "..#####.."});

    // Stamped into the map.
    static final String[] TREE = {"..#..", ".#o#.", "#ooo#", "#ooo#", ".###.", "..#..", "..#.."};
    static final String[] PINE = {"..#..", ".#o#.", ".#o#.", "#ooo#", "#ooo#", "#####", "..#.."};
    static final String[] PEAK = {"....#....", "...#o#...", "..#soo#..", ".#ssooo#.", "#sssoooo#"};
    static final String[] WAVE = {".##..", "#..##"};
    static final String[] TUFT = {"#.#", ".#."};
    static final String[] HOUSE = {".###.", "#ooo#", "#o#o#", "#####"};

    private AtlasIcons() { }

    /** Darker by a share, for the shaded part of a drawing. */
    static int shade(int argb, double by) {
        int r = (int) (((argb >> 16) & 0xFF) * (1 - by)), g = (int) (((argb >> 8) & 0xFF) * (1 - by)), b = (int) ((argb & 0xFF) * (1 - by));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** Draws a mark on the screen, its middle at x, y, each of its pixels {@code size} screen pixels across. */
    static void draw(GuiGraphicsExtractor graphics, Icon icon, int x, int y, int size, boolean lit) {
        int fill = lit ? 0xFFFFFFFF : icon.fill(), shaded = lit ? 0xFFE0E0E0 : shade(icon.fill(), 0.3);
        int left = x - icon.rows()[0].length() * size / 2, top = y - icon.rows().length * size / 2;
        for (int row = 0; row < icon.rows().length; row++) {
            String line = icon.rows()[row];
            for (int column = 0; column < line.length(); column++) {
                int colour = switch (line.charAt(column)) {
                    case '#' -> INK;
                    case 'o' -> fill;
                    case 's' -> shaded;
                    default -> 0;
                };
                if (colour == 0) continue;
                int px = left + column * size, py = top + row * size;
                graphics.fill(px, py, px + size, py + size, colour);
            }
        }
    }
}
