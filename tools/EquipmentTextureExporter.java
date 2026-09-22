import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pixel-exact Milestone 9 backpack and worn-equipment artwork. */
public final class EquipmentTextureExporter {
    private static final int CLEAR = 0x00000000;
    private static final int OUTLINE = rgb(38, 29, 23);
    private static final int LEATHER_DARK = rgb(58, 39, 29);
    private static final int LEATHER = rgb(91, 59, 39);
    private static final int LEATHER_LIGHT = rgb(126, 83, 51);
    private static final int STITCH = rgb(176, 137, 86);
    private static final int FIBER_DARK = rgb(71, 71, 48);
    private static final int FIBER_GREEN = rgb(104, 105, 68);
    private static final int FIBER_STRAW = rgb(155, 137, 82);
    private static final int SLOT_DARK = rgb(47, 43, 39);
    private static final int SLOT = rgb(76, 70, 62);
    private static final int SLOT_LIGHT = rgb(119, 109, 94);

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        Path items = root.resolve("src/main/resources/assets/hardwrought/textures/item");
        Path gui = root.resolve("src/main/resources/assets/hardwrought/textures/gui/sprites/equipment");
        Path source = root.resolve("art_source/equipment");

        Map<String, BufferedImage> sprites = new LinkedHashMap<>();
        sprites.put("starter_backpack", starterBackpack());
        sprites.put("basic_backpack", basicBackpack());
        sprites.put("strap", strap());
        sprites.put("worn_slot", wornSlot());
        sprites.put("fold_button", foldButton(false, false));
        sprites.put("fold_button_hovered", foldButton(true, false));
        sprites.put("fold_button_blocked", foldButton(false, true));
        sprites.put("slot_backpack", emptyBackpack());
        sprites.put("slot_lamp", emptyLamp());

        write(items.resolve("starter_backpack.png"), sprites.get("starter_backpack"));
        write(items.resolve("basic_backpack.png"), sprites.get("basic_backpack"));
        for (String name : new String[]{"strap", "worn_slot", "fold_button",
                "fold_button_hovered", "fold_button_blocked", "slot_backpack", "slot_lamp"}) {
            write(gui.resolve(name + ".png"), sprites.get(name));
        }
        write(source.resolve("equipment_pixel_atlas.png"), atlas(sprites));
        System.out.println("Exported Milestone 9 backpack and equipment GUI textures.");
    }

    private static BufferedImage starterBackpack() {
        BufferedImage image = image(16, 16, CLEAR);
        // Two open carrying loops make this read as a primitive woven carrier, not armour.
        line(image, 3, 7, 3, 3, OUTLINE); line(image, 4, 3, 6, 1, OUTLINE);
        line(image, 12, 7, 12, 3, OUTLINE); line(image, 11, 3, 9, 1, OUTLINE);
        line(image, 4, 4, 6, 2, FIBER_STRAW); line(image, 11, 4, 9, 2, FIBER_STRAW);
        fill(image, 3, 5, 12, 13, OUTLINE);
        fill(image, 4, 6, 11, 13, FIBER_GREEN);
        fill(image, 5, 5, 10, 6, FIBER_DARK);
        set(image, 4, 7, FIBER_STRAW); set(image, 6, 7, FIBER_STRAW);
        set(image, 8, 7, FIBER_STRAW); set(image, 10, 7, FIBER_STRAW);
        for (int y = 8; y <= 12; y++) for (int x = 4; x <= 11; x++) {
            if ((x + y) % 3 == 0) set(image, x, y, FIBER_STRAW);
            else if ((x * 2 + y) % 5 == 0) set(image, x, y, FIBER_DARK);
        }
        line(image, 4, 10, 11, 10, rgb(126, 113, 70));
        set(image, 4, 13, OUTLINE); set(image, 11, 13, OUTLINE);
        return image;
    }

    private static BufferedImage basicBackpack() {
        BufferedImage image = image(16, 16, CLEAR);
        fill(image, 3, 2, 12, 14, OUTLINE);
        fill(image, 2, 5, 13, 12, OUTLINE);
        fill(image, 3, 5, 12, 13, LEATHER);
        fill(image, 4, 3, 11, 6, LEATHER_LIGHT);
        fill(image, 3, 4, 12, 5, LEATHER_LIGHT);
        line(image, 4, 6, 11, 6, STITCH);
        fill(image, 7, 5, 8, 12, LEATHER_DARK);
        set(image, 7, 8, STITCH); set(image, 8, 8, STITCH);
        // Side pocket gives the upgraded pack an unmistakably fuller silhouette.
        fill(image, 12, 8, 14, 12, OUTLINE);
        fill(image, 12, 9, 13, 11, LEATHER_LIGHT);
        line(image, 4, 13, 11, 13, LEATHER_DARK);
        for (int x = 4; x <= 11; x += 2) set(image, x, 4, STITCH);
        set(image, 4, 8, LEATHER_LIGHT); set(image, 10, 10, LEATHER_LIGHT);
        return image;
    }

    private static BufferedImage strap() {
        BufferedImage image = image(30, 32, CLEAR);
        fill(image, 3, 0, 26, 31, OUTLINE);
        fill(image, 1, 4, 28, 27, OUTLINE);
        fill(image, 3, 2, 26, 29, LEATHER_DARK);
        fill(image, 4, 3, 25, 28, LEATHER);
        line(image, 5, 2, 24, 2, LEATHER_LIGHT);
        line(image, 5, 29, 24, 29, rgb(44, 31, 25));
        for (int y = 5; y <= 27; y += 4) {
            set(image, 5, y, STITCH); set(image, 24, y + 1, STITCH);
        }
        // Quiet wear, kept away from the slots and the scalable centre.
        set(image, 8, 4, LEATHER_LIGHT); set(image, 21, 27, LEATHER_LIGHT);
        set(image, 6, 25, rgb(72, 47, 34)); set(image, 23, 6, rgb(72, 47, 34));
        return image;
    }

    private static BufferedImage wornSlot() {
        BufferedImage image = image(18, 18, CLEAR);
        fill(image, 0, 0, 17, 17, OUTLINE);
        fill(image, 1, 1, 16, 16, LEATHER_DARK);
        fill(image, 2, 2, 15, 15, SLOT_DARK);
        fill(image, 3, 3, 14, 14, SLOT);
        line(image, 3, 14, 14, 14, SLOT_LIGHT);
        line(image, 14, 3, 14, 14, SLOT_LIGHT);
        for (int p : new int[]{3, 7, 11, 14}) {
            set(image, p, 1, STITCH); set(image, 1, p, STITCH);
        }
        return image;
    }

    private static BufferedImage foldButton(boolean hovered, boolean blocked) {
        BufferedImage image = image(14, 14, CLEAR);
        int edge = blocked ? rgb(61, 57, 52) : OUTLINE;
        int base = blocked ? rgb(79, 74, 67) : hovered ? LEATHER_LIGHT : LEATHER;
        int light = blocked ? rgb(99, 94, 85) : hovered ? rgb(158, 107, 65) : rgb(111, 74, 48);
        fill(image, 2, 0, 11, 13, edge);
        fill(image, 0, 2, 13, 11, edge);
        fill(image, 2, 1, 11, 12, base);
        fill(image, 1, 3, 12, 10, base);
        line(image, 3, 2, 10, 2, light);
        line(image, 3, 11, 10, 11, blocked ? rgb(60, 57, 53) : LEATHER_DARK);
        if (!blocked) {
            // A double chevron stays truthful in both open and closed states.
            set(image, 3, 6, STITCH); set(image, 4, 5, STITCH); set(image, 4, 7, STITCH);
            set(image, 10, 6, STITCH); set(image, 9, 5, STITCH); set(image, 9, 7, STITCH);
            line(image, 5, 6, 8, 6, STITCH);
        }
        return image;
    }

    private static BufferedImage emptyBackpack() {
        BufferedImage image = image(16, 16, CLEAR);
        int ink = rgba(170, 170, 170, 150);
        line(image, 5, 4, 6, 2, ink); line(image, 10, 4, 9, 2, ink);
        line(image, 6, 2, 9, 2, ink);
        line(image, 4, 5, 11, 5, ink); line(image, 3, 6, 3, 12, ink);
        line(image, 12, 6, 12, 12, ink); line(image, 4, 13, 11, 13, ink);
        set(image, 4, 6, ink); set(image, 11, 6, ink);
        line(image, 6, 8, 9, 8, ink); set(image, 6, 9, ink); set(image, 9, 9, ink);
        return image;
    }

    private static BufferedImage emptyLamp() {
        BufferedImage image = image(16, 16, CLEAR);
        int ink = rgba(170, 170, 170, 150);
        line(image, 6, 2, 9, 2, ink); set(image, 5, 3, ink); set(image, 10, 3, ink);
        set(image, 4, 4, ink); set(image, 11, 4, ink);
        line(image, 5, 5, 10, 5, ink); line(image, 5, 6, 5, 11, ink);
        line(image, 10, 6, 10, 11, ink); line(image, 4, 12, 11, 12, ink);
        line(image, 5, 13, 10, 13, ink); line(image, 7, 7, 8, 7, ink);
        line(image, 7, 10, 8, 10, ink);
        return image;
    }

    private static BufferedImage atlas(Map<String, BufferedImage> sprites) {
        int cell = 72;
        BufferedImage atlas = image(cell * 3, cell * 3, rgb(24, 22, 20));
        Graphics2D graphics = atlas.createGraphics();
        int index = 0;
        for (BufferedImage sprite : sprites.values()) {
            int scale = Math.max(1, Math.min(3, 56 / Math.max(sprite.getWidth(), sprite.getHeight())));
            graphics.drawImage(sprite, index % 3 * cell + 8, index / 3 * cell + 8,
                    sprite.getWidth() * scale, sprite.getHeight() * scale, null);
            index++;
        }
        graphics.dispose();
        return atlas;
    }

    private static BufferedImage image(int width, int height, int color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (color != CLEAR) fill(image, 0, 0, width - 1, height - 1, color);
        return image;
    }

    private static void fill(BufferedImage image, int x1, int y1, int x2, int y2, int color) {
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) set(image, x, y, color);
    }

    private static void line(BufferedImage image, int x1, int y1, int x2, int y2, int color) {
        int dx = Integer.compare(x2, x1);
        int dy = Integer.compare(y2, y1);
        int length = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 0; step <= length; step++) set(image, x1 + dx * step, y1 + dy * step, color);
    }

    private static void set(BufferedImage image, int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) image.setRGB(x, y, color);
    }

    private static int rgb(int red, int green, int blue) {
        return rgba(red, green, blue, 255);
    }

    private static int rgba(int red, int green, int blue, int alpha) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }
}
