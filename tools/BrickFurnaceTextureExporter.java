import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exports the brick furnace as deliberately authored, vanilla-scale 16x16 pixel art. */
public final class BrickFurnaceTextureExporter {
    private static final int SIZE = 16;

    private static final int BRICK_DARK = rgb(91, 43, 33);
    private static final int BRICK = rgb(132, 65, 46);
    private static final int BRICK_LIGHT = rgb(161, 82, 56);
    private static final int BRICK_HIGHLIGHT = rgb(178, 94, 64);
    private static final int MORTAR_DARK = rgb(91, 84, 73);
    private static final int MORTAR = rgb(119, 108, 92);
    private static final int SOOT = rgb(49, 39, 35);
    private static final int SOOT_DARK = rgb(25, 22, 21);
    private static final int FIRE_DARK = rgb(122, 42, 15);
    private static final int FIRE_ORANGE = rgb(235, 91, 9);
    private static final int FIRE_GOLD = rgb(255, 174, 18);
    private static final int FIRE_LIGHT = rgb(255, 226, 77);

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        Path source = root.resolve("art_source/brick_furnace");
        Path output = root.resolve("src/main/resources/assets/hardwrought/textures/block");

        BufferedImage side = brickField();
        BufferedImage top = top(side);
        BufferedImage front = front(side, false);
        BufferedImage frontOn = front(side, true);

        write(output.resolve("brick_furnace_side.png"), side);
        write(output.resolve("brick_furnace_top.png"), top);
        write(output.resolve("brick_furnace_front.png"), front);
        write(output.resolve("brick_furnace_front_on.png"), frontOn);
        write(source.resolve("brick_furnace_pixel_atlas.png"), atlas(side, top, front, frontOn));
        System.out.println("Exported four brick furnace textures at 16x16.");
    }

    private static BufferedImage brickField() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] joints = {5, 12, 2, 9};
        for (int y = 0; y < SIZE; y++) {
            int course = y / 4;
            int localY = y % 4;
            for (int x = 0; x < SIZE; x++) {
                if (localY == 3 || x == joints[course]) {
                    image.setRGB(x, y, ((x + y) & 3) == 0 ? MORTAR : MORTAR_DARK);
                    continue;
                }
                int variation = Math.floorMod(x * 13 + y * 7 + course * 11, 12);
                int color = variation < 2 ? BRICK_DARK
                        : variation < 7 ? BRICK
                        : variation < 11 ? BRICK_LIGHT : BRICK_HIGHLIGHT;
                if (localY == 0 && variation > 3) color = BRICK_HIGHLIGHT;
                image.setRGB(x, y, color);
            }
        }
        return image;
    }

    private static BufferedImage top(BufferedImage bricks) {
        BufferedImage image = copy(bricks);
        for (int y = 1; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                double distance = Math.hypot(x - 7.5, y - 7.5);
                int original = image.getRGB(x, y);
                if (distance < 3.0) image.setRGB(x, y, blend(original, SOOT, 0.58));
                else if (distance < 5.0) image.setRGB(x, y, blend(original, SOOT, 0.34));
                else if (distance < 6.5 && ((x + y) & 1) == 0)
                    image.setRGB(x, y, blend(original, SOOT, 0.18));
            }
        }
        return image;
    }

    private static BufferedImage front(BufferedImage bricks, boolean lit) {
        BufferedImage image = copy(bricks);

        // Uneven soot above the firebox, while the brick pattern remains visible.
        for (int y = 4; y <= 8; y++) {
            for (int x = 2; x <= 13; x++) {
                int edge = Math.abs(x - 7) + Math.abs(y - 8);
                double amount = edge < 4 ? 0.40 : edge < 7 ? 0.24 : 0.10;
                if (((x * 3 + y) & 3) != 0)
                    image.setRGB(x, y, blend(image.getRGB(x, y), SOOT, amount));
            }
        }

        // Chunky clay rim and arched opening, readable at Minecraft's native resolution.
        fill(image, 4, 8, 11, 13, BRICK_DARK);
        fill(image, 5, 7, 10, 7, BRICK_DARK);
        fill(image, 5, 8, 10, 8, SOOT_DARK);
        fill(image, 4, 9, 11, 12, SOOT_DARK);
        set(image, 3, 10, BRICK_DARK); set(image, 12, 10, BRICK_DARK);
        set(image, 3, 11, BRICK_DARK); set(image, 12, 11, BRICK_DARK);

        // Broken, hand-built rim highlights rather than a clean metal frame.
        set(image, 4, 8, MORTAR_DARK); set(image, 11, 8, MORTAR);
        set(image, 3, 9, MORTAR_DARK); set(image, 12, 9, MORTAR_DARK);
        set(image, 4, 13, MORTAR_DARK); set(image, 7, 13, MORTAR);
        set(image, 8, 13, MORTAR_DARK); set(image, 11, 13, MORTAR_DARK);

        if (lit) addFire(image);
        else {
            set(image, 6, 11, rgb(34, 31, 29));
            set(image, 9, 10, rgb(38, 32, 29));
            set(image, 10, 12, rgb(31, 28, 27));
        }
        return image;
    }

    private static void addFire(BufferedImage image) {
        fill(image, 4, 11, 11, 12, FIRE_DARK);
        set(image, 5, 10, FIRE_ORANGE); set(image, 9, 10, FIRE_ORANGE);
        set(image, 10, 10, FIRE_DARK);
        set(image, 5, 11, FIRE_GOLD); set(image, 6, 11, FIRE_ORANGE);
        set(image, 7, 11, FIRE_GOLD); set(image, 8, 11, FIRE_LIGHT);
        set(image, 9, 11, FIRE_GOLD); set(image, 10, 11, FIRE_ORANGE);
        set(image, 4, 12, FIRE_ORANGE); set(image, 5, 12, FIRE_GOLD);
        set(image, 6, 12, FIRE_LIGHT); set(image, 7, 12, FIRE_GOLD);
        set(image, 8, 12, FIRE_LIGHT); set(image, 9, 12, FIRE_GOLD);
        set(image, 10, 12, FIRE_ORANGE); set(image, 11, 12, FIRE_DARK);
    }

    private static void fill(BufferedImage image, int x1, int y1, int x2, int y2, int color) {
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) set(image, x, y, color);
    }

    private static void set(BufferedImage image, int x, int y, int color) {
        image.setRGB(x, y, color);
    }

    private static int blend(int first, int second, double amount) {
        int red = (int) (((first >>> 16) & 255) * (1 - amount) + ((second >>> 16) & 255) * amount);
        int green = (int) (((first >>> 8) & 255) * (1 - amount) + ((second >>> 8) & 255) * amount);
        int blue = (int) ((first & 255) * (1 - amount) + (second & 255) * amount);
        return rgb(red, green, blue);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private static BufferedImage atlas(BufferedImage side, BufferedImage top,
                                       BufferedImage front, BufferedImage frontOn) {
        BufferedImage atlas = new BufferedImage(SIZE * 2, SIZE * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.drawImage(side, 0, 0, null);
        graphics.drawImage(top, SIZE, 0, null);
        graphics.drawImage(front, 0, SIZE, null);
        graphics.drawImage(frontOn, SIZE, SIZE, null);
        graphics.dispose();
        return atlas;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return output;
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }
}
