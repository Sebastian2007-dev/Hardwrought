import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a generated picture that is pixel art blown up into a texture Minecraft can use.
 *
 * <p>The generated pictures are pixel art drawn on a grid of roughly a hundred squares, each square some
 * thirteen screen pixels with soft edges, on a transparent ground. Three steps:
 *
 * <ol>
 *   <li><b>The grid is measured.</b> Along every few rows the picture is cut into runs of one colour. In
 *   pixel art every run is a whole number of squares long, so the square is the length that, with its
 *   multiples, accounts for the most runs.</li>
 *   <li><b>The art is read back,</b> one pixel to a square, each square read at its middle where no soft
 *   edge reaches, and cut to the box round what is not transparent.</li>
 *   <li><b>It is brought to the size asked for</b> — 16 or 32 for an item — by giving each pixel of the
 *   result the colour most of its share of the art has, never an average, so the result is still pixel art
 *   with the art's own colours and no blur. The art is centred and keeps its proportions.</li>
 * </ol>
 *
 * <pre>java tools/PixelArtRecover.java in.png out.png [size]</pre>
 * Without a size, the recovered art is written at its own size.
 */
public final class PixelArtRecover {
    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(new File(args[0]));
        double cell = square(source);
        BufferedImage art = crop(read(source, cell));
        BufferedImage out = args.length > 2 ? fit(art, Integer.parseInt(args[2])) : art;
        ImageIO.write(out, "png", new File(args[1]));
        System.out.printf("%s: square %.2f px, art %dx%d -> %dx%d%n", args[0], cell, art.getWidth(), art.getHeight(),
                out.getWidth(), out.getHeight());
    }

    // ---------------------------------------------------------------- the grid

    /** The size of one square of the art, in pixels of the picture. */
    static double square(BufferedImage image) {
        int[] runs = new int[400];
        for (int pass = 0; pass < 2; pass++) {
            boolean rows = pass == 0;
            int lines = rows ? image.getHeight() : image.getWidth(), length = rows ? image.getWidth() : image.getHeight();
            for (int line = 0; line < lines; line += 5) {
                int start = 0, previous = rows ? image.getRGB(0, line) : image.getRGB(line, 0);
                for (int i = 1; i < length; i++) {
                    int colour = rows ? image.getRGB(i, line) : image.getRGB(line, i);
                    if (distance(colour, previous) <= 40) continue;
                    int run = i - start;
                    if ((previous >>> 24) > 128 && run < runs.length) runs[run]++;
                    start = i;
                    previous = colour;
                }
            }
        }
        double best = 0, bestScore = -1;
        for (double size = 4; size <= 80; size += 0.05) {
            double score = 0;
            for (int run = 3; run < runs.length; run++) {
                double multiple = run / size;
                long whole = Math.round(multiple);
                // Runs of a whole number of squares count; the longer a run, the more slack its edges allow.
                if (whole >= 1 && whole <= 6 && Math.abs(multiple - whole) * size <= 1.5) score += runs[run] * (1.0 + 0.15 * (whole - 1));
            }
            // A size half as large explains the same runs again as even multiples: only a clearly better
            // score lets a finer grid win, so the art is not read at twice its own resolution.
            if (score > bestScore * 1.12) {
                bestScore = score;
                best = size;
            }
        }
        return best;
    }

    /** The art, one pixel to a square, each read at the square's middle. */
    static BufferedImage read(BufferedImage source, double cell) {
        int across = (int) Math.floor(source.getWidth() / cell), down = (int) Math.floor(source.getHeight() / cell);
        BufferedImage art = new BufferedImage(across, down, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < down; y++) {
            for (int x = 0; x < across; x++) {
                int argb = source.getRGB((int) ((x + 0.5) * cell), (int) ((y + 0.5) * cell));
                art.setRGB(x, y, (argb >>> 24) < 128 ? 0 : 0xFF000000 | (argb & 0xFFFFFF));
            }
        }
        return art;
    }

    static BufferedImage crop(BufferedImage art) {
        int left = art.getWidth(), top = art.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < art.getHeight(); y++) {
            for (int x = 0; x < art.getWidth(); x++) {
                if ((art.getRGB(x, y) >>> 24) == 0) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return art.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    // ---------------------------------------------------------------- the size

    /**
     * The art on a square of {@code size}, keeping its proportions, each pixel the colour most of its share
     * of the art has. A share that is mostly empty stays empty, so the outline is not fattened.
     */
    static BufferedImage fit(BufferedImage art, int size) {
        double scale = Math.max(art.getWidth(), art.getHeight()) / (double) size;
        int w = Math.max(1, (int) Math.round(art.getWidth() / scale)), h = Math.max(1, (int) Math.round(art.getHeight() / scale));
        int ox = (size - w) / 2, oy = (size - h) / 2;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int x0 = (int) Math.floor(x * scale), x1 = Math.max(x0 + 1, (int) Math.floor((x + 1) * scale));
                int y0 = (int) Math.floor(y * scale), y1 = Math.max(y0 + 1, (int) Math.floor((y + 1) * scale));
                Map<Integer, Integer> votes = new HashMap<>();
                int empty = 0, total = 0;
                for (int sy = y0; sy < Math.min(y1, art.getHeight()); sy++) {
                    for (int sx = x0; sx < Math.min(x1, art.getWidth()); sx++) {
                        int argb = art.getRGB(sx, sy);
                        total++;
                        if ((argb >>> 24) == 0) empty++;
                        else votes.merge(quantise(argb), 1, Integer::sum);
                    }
                }
                if (votes.isEmpty() || empty * 2 > total) continue;
                int winner = votes.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
                out.setRGB(ox + x, oy + y, representative(art, x0, y0, x1, y1, winner));
            }
        }
        return out;
    }

    /** Near colours vote together: the soft noise of a generated picture is not a colour of its own. */
    private static int quantise(int argb) {
        return ((argb >> 16) & 0xF0) << 16 | ((argb >> 8) & 0xF0) << 8 | (argb & 0xF0);
    }

    /** The art's own colour for the winning vote: the first pixel of the share that cast it. */
    private static int representative(BufferedImage art, int x0, int y0, int x1, int y1, int vote) {
        for (int sy = y0; sy < Math.min(y1, art.getHeight()); sy++) {
            for (int sx = x0; sx < Math.min(x1, art.getWidth()); sx++) {
                int argb = art.getRGB(sx, sy);
                if ((argb >>> 24) != 0 && quantise(argb) == vote) return argb;
            }
        }
        return 0;
    }

    private static double distance(int a, int b) {
        int da = (a >>> 24) - (b >>> 24);
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(da * da + dr * dr + dg * dg + db * db);
    }
}
