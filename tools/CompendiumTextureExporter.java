import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exports the deliberately authored native-scale pixel art for the compendium and thirst effect. */
public final class CompendiumTextureExporter {
    private static final int TRANSPARENT = 0x00000000;
    private static final int PAGE_DEEP = rgb(48, 38, 29);
    private static final int PAGE = rgb(111, 94, 67);
    private static final int PAGE_WARM = rgb(126, 108, 78);
    private static final int PAGE_EDGE = rgb(82, 66, 46);
    private static final int PAGE_WEAR = rgb(151, 129, 91);
    private static final int INK = rgb(30, 24, 18);
    private static final int INK_SOFT = rgb(61, 50, 37);
    private static final int CORD_DARK = rgb(65, 53, 35);
    private static final int CORD = rgb(122, 101, 63);
    private static final int CORD_LIGHT = rgb(158, 134, 84);
    private static final int SLOT = rgb(151, 141, 121);
    private static final int SLOT_LIGHT = rgb(177, 164, 137);

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        Path gui = root.resolve("src/main/resources/assets/hardwrought/textures/gui/sprites/compendium");
        Path item = root.resolve("src/main/resources/assets/hardwrought/textures/item");
        Path effect = root.resolve("src/main/resources/assets/hardwrought/textures/mob_effect");
        Path source = root.resolve("art_source/compendium");

        Map<String, BufferedImage> sprites = new LinkedHashMap<>();
        sprites.put("cover", cover());
        sprites.put("page_left", bookPage(false));
        sprites.put("page_right", bookPage(true));
        sprites.put("page", page());
        sprites.put("binding", binding());
        sprites.put("slot", slot());
        sprites.put("half", panel(false));
        sprites.put("half_hovered", panel(true));
        sprites.put("tab", tab(false));
        sprites.put("tab_selected", tab(true));
        sprites.put("card", card());
        sprites.put("emblem_knowledge", knowledgeEmblem());
        sprites.put("emblem_thoughts", thoughtsEmblem());
        sprites.put("followed", followed());
        sprites.put("button", button(false));
        sprites.put("button_hovered", button(true));
        sprites.put("search", search());
        sprites.put("method_crafting", methodTab(0));
        sprites.put("method_loot", methodTab(1));
        sprites.put("method_cooking", methodTab(2));
        sprites.put("method_smithing", methodTab(3));
        sprites.put("method_smelting", methodTab(4));
        sprites.put("method_in_world", methodTab(5));
        for (Map.Entry<String, BufferedImage> sprite : sprites.entrySet()) {
            write(gui.resolve(sprite.getKey() + ".png"), sprite.getValue());
        }

        BufferedImage book = compendiumItem();
        BufferedImage thirst = thirst();
        write(item.resolve("compendium.png"), book);
        write(effect.resolve("thirst.png"), thirst);
        write(source.resolve("compendium_item_pixel_source.png"), scaleNearest(book, 16));
        write(source.resolve("thirst_pixel_source.png"), scaleNearest(thirst, 12));
        write(source.resolve("gui_pixel_atlas.png"), atlas(sprites));
        System.out.println("Exported compendium item, GUI sprites, and thirst effect texture.");
    }

    private static BufferedImage compendiumItem() {
        BufferedImage image = image(16, 16, TRANSPARENT);
        // Uneven layers of rag paper behind a smoke-darkened bark-paper cover.
        fill(image, 4, 1, 12, 1, rgb(126, 111, 86));
        fill(image, 3, 2, 13, 12, rgb(91, 76, 57));
        fill(image, 4, 13, 12, 14, rgb(117, 101, 77));
        set(image, 3, 13, rgb(75, 62, 47)); set(image, 13, 11, rgb(67, 55, 43));
        fill(image, 4, 3, 12, 12, rgb(55, 47, 39));
        fill(image, 5, 4, 11, 11, rgb(61, 52, 43));
        set(image, 11, 4, rgb(77, 65, 49)); set(image, 5, 11, rgb(44, 38, 33));
        set(image, 9, 7, rgb(71, 60, 48)); set(image, 7, 10, rgb(48, 42, 36));
        // The exposed spine, doubled plant cord, and a readable knot.
        fill(image, 2, 2, 3, 13, CORD_DARK);
        for (int y = 3; y <= 12; y += 3) {
            set(image, 2, y, CORD_LIGHT); set(image, 3, y + 1, CORD);
        }
        fill(image, 1, 7, 4, 8, CORD);
        set(image, 2, 6, CORD_LIGHT); set(image, 4, 7, CORD_LIGHT);
        set(image, 1, 9, CORD_DARK); set(image, 4, 9, CORD_DARK);
        set(image, 5, 8, CORD); set(image, 5, 9, CORD_DARK);
        return image;
    }

    private static BufferedImage page() {
        BufferedImage image = textured(32, 32, PAGE, PAGE_WARM, 7);
        // Worn outside edge. It stays intentionally irregular inside the non-stretched six pixels.
        for (int i = 0; i < 32; i++) {
            set(image, i, 0, (i % 7 == 1) ? PAGE_DEEP : PAGE_EDGE);
            set(image, i, 31, (i % 6 == 2) ? PAGE_DEEP : PAGE_EDGE);
            set(image, 0, i, (i % 5 == 0) ? PAGE_DEEP : PAGE_EDGE);
            set(image, 31, i, (i % 7 == 3) ? PAGE_DEEP : PAGE_EDGE);
        }
        brokenLine(image, 2, 2, 29, 2, PAGE_WEAR, 5);
        brokenLine(image, 2, 29, 29, 29, PAGE_WEAR, 7);
        brokenLine(image, 2, 3, 2, 28, PAGE_WEAR, 6);
        brokenLine(image, 29, 3, 29, 28, PAGE_WEAR, 5);
        return image;
    }

    private static BufferedImage cover() {
        BufferedImage image = textured(32, 32, rgb(47, 29, 22), rgb(67, 40, 28), 6);
        fill(image, 0, 0, 31, 1, rgb(29, 20, 17));
        fill(image, 0, 30, 31, 31, rgb(27, 18, 15));
        fill(image, 0, 0, 1, 31, rgb(29, 20, 17));
        fill(image, 30, 0, 31, 31, rgb(29, 20, 17));
        brokenLine(image, 3, 3, 28, 3, rgb(91, 52, 34), 6);
        brokenLine(image, 3, 28, 28, 28, rgb(91, 52, 34), 5);
        return image;
    }

    /** One page of the spread. The inner edge is shaded toward the physical gutter. */
    private static BufferedImage bookPage(boolean right) {
        BufferedImage image = textured(32, 32, PAGE, PAGE_WARM, 8);
        int outer = right ? 31 : 0;
        int inner = right ? 0 : 31;
        for (int y = 0; y < 32; y++) {
            set(image, outer, y, (y % 6 == 1) ? PAGE_DEEP : PAGE_EDGE);
            set(image, right ? 1 : 30, y, rgb(88, 72, 52));
            set(image, inner, y, rgb(55, 43, 32));
            if (y > 2 && y < 29) set(image, right ? 2 : 29, y, rgb(94, 77, 55));
        }
        for (int x = 1; x < 31; x++) {
            set(image, x, 0, (x % 7 == 2) ? PAGE_DEEP : PAGE_EDGE);
            set(image, x, 31, (x % 5 == 1) ? PAGE_DEEP : PAGE_EDGE);
            if (x % 4 != 0) set(image, x, 30, PAGE_WEAR);
        }
        // Dog-eared outer bottom corner.
        if (right) {
            set(image, 30, 29, PAGE_WEAR); set(image, 29, 30, PAGE_WEAR);
            set(image, 30, 30, rgb(72, 57, 42));
        } else {
            set(image, 1, 29, PAGE_WEAR); set(image, 2, 30, PAGE_WEAR);
            set(image, 1, 30, rgb(72, 57, 42));
        }
        return image;
    }

    private static BufferedImage binding() {
        BufferedImage image = image(8, 32, TRANSPARENT);
        for (int y = 0; y < 32; y++) {
            set(image, 3, y, CORD_DARK); set(image, 4, y, CORD);
            if ((y & 3) == 1) set(image, 4, y, CORD_LIGHT);
        }
        for (int y : new int[]{4, 14, 25}) {
            fill(image, 1, y, 6, y + 1, CORD_DARK);
            fill(image, 2, y - 1, 5, y, CORD);
            set(image, 3, y - 1, CORD_LIGHT);
        }
        return image;
    }

    private static BufferedImage slot() {
        BufferedImage image = image(18, 18, SLOT);
        fill(image, 0, 0, 17, 0, rgb(55, 45, 34));
        fill(image, 0, 0, 0, 17, rgb(55, 45, 34));
        fill(image, 1, 1, 16, 1, rgb(91, 76, 57));
        fill(image, 1, 1, 1, 16, rgb(91, 76, 57));
        fill(image, 2, 2, 15, 15, SLOT);
        fill(image, 2, 15, 15, 16, SLOT_LIGHT);
        fill(image, 15, 2, 16, 16, SLOT_LIGHT);
        set(image, 5, 4, rgb(158, 147, 126)); set(image, 12, 11, rgb(137, 128, 111));
        return image;
    }

    private static BufferedImage panel(boolean hovered) {
        int base = hovered ? rgb(143, 117, 76) : rgb(111, 94, 67);
        int fiber = hovered ? rgb(163, 137, 94) : rgb(128, 109, 78);
        BufferedImage image = textured(32, 32, base, fiber, hovered ? 5 : 8);
        int edge = hovered ? rgb(103, 82, 58) : rgb(66, 56, 45);
        brokenLine(image, 0, 1, 31, 1, edge, 7);
        brokenLine(image, 1, 30, 30, 30, edge, 5);
        brokenLine(image, 1, 2, 1, 29, edge, 6);
        brokenLine(image, 30, 2, 30, 29, edge, 8);
        set(image, 0, 0, PAGE_DEEP); set(image, 31, 0, PAGE_DEEP);
        set(image, 0, 31, PAGE_DEEP); set(image, 31, 31, PAGE_DEEP);
        return image;
    }

    private static BufferedImage tab(boolean selected) {
        int base = selected ? rgb(78, 61, 45) : rgb(47, 40, 34);
        BufferedImage image = textured(16, 18, base,
                selected ? rgb(93, 73, 52) : rgb(57, 48, 39), 5);
        int edge = selected ? rgb(126, 101, 70) : rgb(70, 59, 47);
        fill(image, 0, 0, 15, 0, edge);
        fill(image, 0, 17, 15, 17, rgb(29, 25, 22));
        fill(image, 0, 1, 0, 16, rgb(29, 25, 22));
        fill(image, 15, 2, 15, 15, edge);
        set(image, 15, 0, PAGE); set(image, 15, 17, PAGE);
        if (selected) {
            fill(image, 13, 3, 15, 14, base);
            set(image, 15, 5, rgb(143, 113, 74)); set(image, 15, 12, rgb(111, 86, 60));
        }
        return image;
    }

    private static BufferedImage card() {
        BufferedImage image = textured(32, 32, PAGE, PAGE_WARM, 9);
        brokenLine(image, 1, 2, 30, 2, INK_SOFT, 6);
        brokenLine(image, 1, 29, 30, 29, INK_SOFT, 5);
        brokenLine(image, 2, 2, 2, 29, INK_SOFT, 7);
        brokenLine(image, 29, 2, 29, 29, INK_SOFT, 6);
        set(image, 4, 3, INK); set(image, 27, 28, INK);
        return image;
    }

    private static BufferedImage knowledgeEmblem() {
        BufferedImage image = image(32, 32, TRANSPARENT);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            int x = 4 + column * 9;
            int y = 4 + row * 9;
            brokenLine(image, x, y, x + 6, y, INK_SOFT, 4 + row);
            brokenLine(image, x, y + 6, x + 6, y + 6, INK_SOFT, 5 + column);
            brokenLine(image, x, y, x, y + 6, INK_SOFT, 4 + column);
            brokenLine(image, x + 6, y, x + 6, y + 6, INK_SOFT, 6 + row);
        }
        return image;
    }

    private static BufferedImage thoughtsEmblem() {
        BufferedImage image = image(32, 32, TRANSPARENT);
        int[] starts = {3, 5, 3, 6, 4};
        int[] ends = {28, 25, 29, 23, 27};
        for (int line = 0; line < 5; line++) {
            int y = 4 + line * 5;
            for (int x = starts[line]; x <= ends[line]; x++) {
                int wobble = Math.floorMod(x * 7 + line * 3, 11) == 0 ? 1 : 0;
                set(image, x, y + wobble, INK_SOFT);
                if ((x + line) % 7 == 0 && x < ends[line]) set(image, x, y + 1, INK);
            }
        }
        return image;
    }

    private static BufferedImage followed() {
        BufferedImage image = image(9, 9, TRANSPARENT);
        int doneInk = rgb(159, 181, 122);
        int doneInkDark = rgb(94, 108, 72);
        set(image, 1, 4, doneInk); set(image, 2, 5, doneInk); set(image, 3, 6, doneInkDark);
        set(image, 4, 5, doneInkDark); set(image, 5, 4, doneInkDark); set(image, 6, 3, doneInk);
        set(image, 7, 2, doneInk); set(image, 4, 6, doneInk);
        return image;
    }

    private static BufferedImage button(boolean hovered) {
        int base = hovered ? rgb(151, 126, 84) : rgb(119, 99, 69);
        BufferedImage image = textured(32, 20, base,
                hovered ? rgb(172, 145, 99) : rgb(135, 114, 80), 6);
        brokenLine(image, 1, 1, 30, 1, hovered ? PAGE_WEAR : PAGE_EDGE, 6);
        brokenLine(image, 1, 18, 30, 18, rgb(55, 43, 32), 5);
        brokenLine(image, 1, 2, 1, 17, PAGE_EDGE, 7);
        brokenLine(image, 30, 2, 30, 17, rgb(55, 43, 32), 6);
        return image;
    }

    private static BufferedImage search() {
        BufferedImage image = textured(32, 20, rgb(91, 77, 58), rgb(105, 89, 65), 8);
        brokenLine(image, 1, 1, 30, 1, rgb(49, 39, 30), 5);
        brokenLine(image, 1, 18, 30, 18, PAGE_WEAR, 7);
        brokenLine(image, 1, 2, 1, 17, rgb(49, 39, 30), 6);
        brokenLine(image, 30, 2, 30, 17, PAGE_WEAR, 5);
        return image;
    }

    /** Six top-edge bookmarks, each identifiable without relying on generated text. */
    private static BufferedImage methodTab(int method) {
        int[] colors = {
                rgb(130, 103, 61), rgb(101, 68, 49), rgb(139, 72, 54),
                rgb(80, 89, 91), rgb(92, 82, 72), rgb(91, 108, 62)
        };
        BufferedImage image = image(24, 24, TRANSPARENT);
        int base = colors[method];
        fill(image, 2, 0, 21, 20, base);
        fill(image, 3, 1, 20, 1, blend(base, rgb(220, 196, 147), 0.28));
        fill(image, 2, 0, 2, 19, rgb(45, 34, 27));
        fill(image, 21, 0, 21, 19, rgb(45, 34, 27));
        fill(image, 4, 20, 9, 22, base); fill(image, 14, 20, 19, 22, base);
        set(image, 10, 20, rgb(45, 34, 27)); set(image, 13, 20, rgb(45, 34, 27));
        switch (method) {
            case 0 -> { // crafting grid
                for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++)
                    outline(image, 7 + x * 4, 5 + y * 4, 3, 3, INK);
            }
            case 1 -> { // loot chest
                fill(image, 6, 9, 17, 16, INK); fill(image, 7, 7, 16, 9, INK_SOFT);
                fill(image, 7, 10, 16, 14, rgb(151, 106, 44)); set(image, 11, 11, CORD_LIGHT);
            }
            case 2 -> { // flame
                fill(image, 9, 8, 14, 16, rgb(71, 38, 25));
                fill(image, 10, 6, 12, 15, rgb(188, 102, 43));
                set(image, 13, 10, rgb(215, 144, 58)); set(image, 11, 13, rgb(234, 177, 75));
            }
            case 3 -> { // anvil
                fill(image, 6, 8, 18, 10, INK); fill(image, 8, 11, 15, 13, INK);
                fill(image, 10, 14, 14, 16, INK); fill(image, 7, 17, 17, 18, INK);
            }
            case 4 -> { // furnace
                outline(image, 6, 6, 13, 13, INK); fill(image, 8, 9, 16, 11, INK);
                fill(image, 9, 13, 15, 16, rgb(159, 75, 36)); set(image, 12, 13, rgb(222, 143, 55));
            }
            case 5 -> { // log and hatchet
                fill(image, 6, 10, 12, 17, rgb(75, 49, 29));
                outline(image, 6, 8, 7, 4, INK); set(image, 8, 9, CORD_LIGHT);
                brokenLine(image, 13, 15, 18, 6, INK, 9);
                fill(image, 15, 6, 19, 9, rgb(94, 101, 93));
            }
        }
        return image;
    }

    private static BufferedImage thirst() {
        BufferedImage image = image(18, 18, TRANSPARENT);
        int outline = rgb(28, 38, 41);
        int dark = rgb(44, 78, 89);
        int middle = rgb(58, 107, 124);
        int light = rgb(91, 133, 145);
        // A nearly empty drop with the chunky bevel and dark underside of vanilla effect icons.
        int[] widths = {1, 3, 5, 7, 9, 11, 11, 13, 13, 13, 11, 9, 7};
        for (int y = 1; y <= 13; y++) {
            int width = widths[y - 1];
            int start = 9 - width / 2;
            fill(image, start, y, start + width - 1, y, outline);
            if (width > 4) fill(image, start + 1, y, start + width - 2, y, rgb(42, 49, 49));
        }
        set(image, 8, 2, light); set(image, 7, 4, rgb(67, 77, 76));
        fill(image, 4, 11, 14, 12, dark);
        fill(image, 5, 10, 13, 10, middle);
        fill(image, 6, 10, 9, 10, light);
        fill(image, 6, 13, 12, 14, outline);
        fill(image, 7, 15, 11, 15, rgb(20, 29, 32));
        return image;
    }

    private static BufferedImage atlas(Map<String, BufferedImage> sprites) {
        int cell = 64;
        int columns = 4;
        int rows = (sprites.size() + columns - 1) / columns;
        BufferedImage atlas = image(columns * cell, rows * cell, rgb(12, 11, 10));
        Graphics2D graphics = atlas.createGraphics();
        int index = 0;
        for (BufferedImage sprite : sprites.values()) {
            int scale = Math.max(1, Math.min(2, 52 / Math.max(sprite.getWidth(), sprite.getHeight())));
            graphics.drawImage(sprite, index % columns * cell + 6, index / columns * cell + 6,
                    sprite.getWidth() * scale, sprite.getHeight() * scale, null);
            index++;
        }
        graphics.dispose();
        return atlas;
    }

    private static BufferedImage scaleNearest(BufferedImage source, int scale) {
        BufferedImage result = image(source.getWidth() * scale, source.getHeight() * scale, TRANSPARENT);
        Graphics2D graphics = result.createGraphics();
        graphics.drawImage(source, 0, 0, result.getWidth(), result.getHeight(), null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage textured(int width, int height, int base, int fiber, int spacing) {
        BufferedImage image = image(width, height, base);
        for (int y = 1; y < height - 1; y++) for (int x = 1; x < width - 1; x++) {
            int noise = Math.floorMod(x * 31 + y * 17 + x * y * 3, spacing * 3);
            if (noise == 0 || (noise == 1 && x % 3 != 0)) set(image, x, y, fiber);
            else if (noise == spacing * 2) set(image, x, y, PAGE_DEEP);
        }
        return image;
    }

    private static void brokenLine(BufferedImage image, int x1, int y1, int x2, int y2,
                                   int color, int rhythm) {
        int dx = Integer.compare(x2, x1);
        int dy = Integer.compare(y2, y1);
        int length = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 0; step <= length; step++) {
            if (Math.floorMod(step * 5 + x1 + y1, rhythm) != 0)
                set(image, x1 + dx * step, y1 + dy * step, color);
        }
    }

    private static BufferedImage image(int width, int height, int color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (color != TRANSPARENT) fill(image, 0, 0, width - 1, height - 1, color);
        return image;
    }

    private static void fill(BufferedImage image, int x1, int y1, int x2, int y2, int color) {
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) set(image, x, y, color);
    }

    private static void outline(BufferedImage image, int x, int y, int width, int height, int color) {
        fill(image, x, y, x + width - 1, y, color);
        fill(image, x, y + height - 1, x + width - 1, y + height - 1, color);
        fill(image, x, y, x, y + height - 1, color);
        fill(image, x + width - 1, y, x + width - 1, y + height - 1, color);
    }

    private static int blend(int first, int second, double amount) {
        int red = (int) (((first >>> 16) & 255) * (1 - amount) + ((second >>> 16) & 255) * amount);
        int green = (int) (((first >>> 8) & 255) * (1 - amount) + ((second >>> 8) & 255) * amount);
        int blue = (int) ((first & 255) * (1 - amount) + (second & 255) * amount);
        return rgb(red, green, blue);
    }

    private static void set(BufferedImage image, int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) image.setRGB(x, y, color);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }
}
