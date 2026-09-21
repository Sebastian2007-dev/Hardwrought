import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds a hewn workbench that still reads as an ordinary vanilla log at first glance. */
public final class HewnWorkbenchTextureGenerator {
    private record Wood(String id, String vanilla) { }

    private static final List<Wood> WOODS = List.of(
            new Wood("oak", "oak_log"),
            new Wood("spruce", "spruce_log"),
            new Wood("birch", "birch_log"),
            new Wood("jungle", "jungle_log"),
            new Wood("acacia", "acacia_log"),
            new Wood("dark_oak", "dark_oak_log"),
            new Wood("pale_oak", "pale_oak_log"),
            new Wood("poplar", "poplar_log"),
            new Wood("mangrove", "mangrove_log"),
            new Wood("cherry", "cherry_log"),
            new Wood("bamboo", "bamboo_block"),
            new Wood("crimson", "crimson_stem"),
            new Wood("warped", "warped_stem")
    );

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        String profile = System.getenv("USERPROFILE");
        Path clientJar = args.length >= 2 ? Path.of(args[1])
                : Path.of(profile == null ? System.getProperty("user.home") : profile,
                        ".gradle", "caches", "fabric-loom", "26.3", "minecraft-client.jar");
        try (FileSystem minecraft = FileSystems.newFileSystem(clientJar)) {
            generate(root, minecraft.getPath("/assets/minecraft/textures/block"));
        }
    }

    private static void generate(Path root, Path vanillaBlocks) throws Exception {
        Path textureRoot = root.resolve("src/main/resources/assets/hardwrought/textures/block");
        Path modelRoot = root.resolve("src/main/resources/assets/hardwrought/models/block");
        Files.createDirectories(textureRoot);
        List<BufferedImage> atlasCells = new ArrayList<>();
        BufferedImage craftingTop = firstFrame(read(vanillaBlocks.resolve("crafting_table_top.png")));
        BufferedImage oakPalette = firstFrame(read(vanillaBlocks.resolve("stripped_oak_log.png")));

        for (Wood wood : WOODS) {
            String stripped = "stripped_" + wood.vanilla;
            BufferedImage vanillaBark = firstFrame(read(vanillaBlocks.resolve(wood.vanilla + ".png")));
            BufferedImage vanillaSide = firstFrame(read(vanillaBlocks.resolve(stripped + ".png")));
            BufferedImage vanillaTop = firstFrame(read(vanillaBlocks.resolve(stripped + "_top.png")));
            BufferedImage top = workedTop(craftingTop, vanillaTop, vanillaSide, oakPalette);
            BufferedImage side = workedSide(vanillaBark, vanillaSide, wood.id.hashCode());

            write(textureRoot.resolve("hewn_workbench_top_" + wood.id + ".png"), top);
            write(textureRoot.resolve("hewn_workbench_side_" + wood.id + ".png"), side);
            writeModel(modelRoot.resolve("hewn_workbench_" + wood.id + ".json"), wood);
            atlasCells.add(side);
            atlasCells.add(top);
        }

        write(root.resolve("art_source/hewn_workbench_pixel_atlas.png"), atlas(atlasCells));
        System.out.println("Generated " + atlasCells.size() + " vanilla-faithful hewn workbench textures and updated "
                + WOODS.size() + " models.");
    }

    private static BufferedImage workedTop(BufferedImage craftingTop, BufferedImage vanillaTop,
                                            BufferedImage woodPalette, BufferedImage oakPalette) {
        ensure16(craftingTop);
        ensure16(vanillaTop);
        ensure16(woodPalette);
        ensure16(oakPalette);
        BufferedImage output = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        double[] target = averageColour(woodPalette);
        double[] oak = averageColour(oakPalette);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int source = craftingTop.getRGB(x, y);
                int tinted = recolour(source, target, oak);
                boolean outerEdge = x == 0 || y == 0 || x == 15 || y == 15;
                // Keep the exact vanilla crafting-table structure through most of the face. At the
                // outside pixel only, mix in end grain so the top still belongs to the log below it.
                output.setRGB(x, y, outerEdge
                        ? blend(vanillaTop.getRGB(x, y), tinted, 0.45)
                        : blend(vanillaTop.getRGB(x, y), tinted, 0.92));
            }
        }
        return output;
    }

    private static double[] averageColour(BufferedImage image) {
        double red = 0;
        double green = 0;
        double blue = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = image.getRGB(x, y);
                red += red(pixel);
                green += green(pixel);
                blue += blue(pixel);
            }
        }
        return new double[]{red / 256.0, green / 256.0, blue / 256.0};
    }

    private static int recolour(int pixel, double[] target, double[] sourcePalette) {
        int red = clamp((int) Math.round(red(pixel) * target[0] / sourcePalette[0]));
        int green = clamp((int) Math.round(green(pixel) * target[1] / sourcePalette[1]));
        int blue = clamp((int) Math.round(blue(pixel) * target[2] / sourcePalette[2]));
        return (alpha(pixel) << 24) | (red << 16) | (green << 8) | blue;
    }

    private static BufferedImage workedSide(BufferedImage vanillaBark, BufferedImage vanillaSide, int seed) {
        ensure16(vanillaBark);
        ensure16(vanillaSide);
        BufferedImage output = copy(vanillaBark);

        // There is no separate model layer: this one continuous face changes from stripped wood to
        // bark at a deliberately uneven height. Every column is cut two to four pixels deep, which
        // removes the ruler-straight seam while keeping almost the whole side vanilla bark.
        int[] cutDepth = {3, 3, 2, 2, 3, 4, 4, 3, 3, 2, 3, 3, 4, 4, 3, 2};
        int offset = Math.floorMod(seed, cutDepth.length);
        for (int x = 0; x < 16; x++) {
            int depth = cutDepth[(x + offset) % cutDepth.length];
            for (int y = 0; y < depth; y++) {
                output.setRGB(x, y, vanillaSide.getRGB(x, y));
            }
        }

        int chip = 6 + Math.floorMod(seed / 5, 4);
        output.setRGB(chip, 0, adjust(output.getRGB(chip, 0), 0.78));
        output.setRGB(Math.min(15, chip + 1), 1, adjust(output.getRGB(Math.min(15, chip + 1), 1), 1.08));
        return output;
    }

    private static int blend(int first, int second, double secondAmount) {
        double firstAmount = 1.0 - secondAmount;
        int alpha = (int) Math.round(alpha(first) * firstAmount + alpha(second) * secondAmount);
        int red = (int) Math.round(red(first) * firstAmount + red(second) * secondAmount);
        int green = (int) Math.round(green(first) * firstAmount + green(second) * secondAmount);
        int blue = (int) Math.round(blue(first) * firstAmount + blue(second) * secondAmount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int adjust(int pixel, double factor) {
        int r = clamp((int) Math.round(red(pixel) * factor));
        int g = clamp((int) Math.round(green(pixel) * factor));
        int b = clamp((int) Math.round(blue(pixel) * factor));
        return (alpha(pixel) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
    private static int alpha(int pixel) { return pixel >>> 24; }
    private static int red(int pixel) { return pixel >>> 16 & 0xff; }
    private static int green(int pixel) { return pixel >>> 8 & 0xff; }
    private static int blue(int pixel) { return pixel & 0xff; }

    private static BufferedImage atlas(List<BufferedImage> cells) {
        BufferedImage output = new BufferedImage(32, cells.size() / 2 * 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        for (int index = 0; index < cells.size(); index++) {
            graphics.drawImage(cells.get(index), index % 2 * 16, index / 2 * 16, null);
        }
        graphics.dispose();
        return output;
    }

    private static void writeModel(Path path, Wood wood) throws IOException {
        String model = """
                {
                  "parent": "minecraft:block/block",
                  "textures": {
                    "bark_top": "minecraft:block/%s_top",
                    "worked": "hardwrought:block/hewn_workbench_side_%s",
                    "top": "hardwrought:block/hewn_workbench_top_%s",
                    "particle": "minecraft:block/%s"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "faces": {
                        "north": { "texture": "#worked", "cullface": "north" },
                        "south": { "texture": "#worked", "cullface": "south" },
                        "east":  { "texture": "#worked", "cullface": "east" },
                        "west":  { "texture": "#worked", "cullface": "west" },
                        "up":    { "texture": "#top", "cullface": "up" },
                        "down":  { "texture": "#bark_top", "cullface": "down" }
                      }
                    }
                  ]
                }
                """.formatted(wood.vanilla, wood.id, wood.id, wood.vanilla);
        Files.writeString(path, model);
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return output;
    }

    private static BufferedImage read(Path path) throws IOException {
        BufferedImage image;
        try (InputStream input = Files.newInputStream(path)) {
            image = ImageIO.read(input);
        }
        if (image == null) throw new IOException("Not a PNG: " + path);
        return image;
    }

    private static BufferedImage firstFrame(BufferedImage image) {
        if (image.getWidth() == 16 && image.getHeight() >= 16 && image.getHeight() % 16 == 0) {
            return image.getSubimage(0, 0, 16, 16);
        }
        return image;
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    private static void ensure16(BufferedImage image) {
        if (image.getWidth() != 16 || image.getHeight() != 16) {
            throw new IllegalArgumentException("Expected 16x16, got " + image.getWidth() + "x" + image.getHeight());
        }
    }
}
