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

/** Builds every timber variant of the joined bench directly from its hewn predecessor. */
public final class NailedWorkbenchTextureGenerator {
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
        Path textures = root.resolve("src/main/resources/assets/hardwrought/textures/block");
        Path models = root.resolve("src/main/resources/assets/hardwrought/models/block");
        List<BufferedImage> atlas = new ArrayList<>();
        for (int index = 0; index < WOODS.size(); index++) {
            Wood wood = WOODS.get(index);
            BufferedImage hewnTop = read(textures.resolve("hewn_workbench_top_" + wood.id + ".png"));
            BufferedImage hewnSide = read(textures.resolve("hewn_workbench_side_" + wood.id + ".png"));
            BufferedImage vanillaBark = firstFrame(readAny(vanillaBlocks.resolve(wood.vanilla + ".png")));
            BufferedImage vanillaSide = firstFrame(readAny(
                    vanillaBlocks.resolve("stripped_" + wood.vanilla + ".png")));
            BufferedImage vanillaBottom = firstFrame(readAny(
                    vanillaBlocks.resolve(wood.vanilla + "_top.png")));
            int seed = 37 * index + 11;
            // The joined bench is still the same log: its top is exactly the hewn work surface and
            // its underside is the untouched end grain of the source log.
            BufferedImage top = copy(hewnTop);
            BufferedImage bottom = copy(vanillaBottom);
            BufferedImage side = workedSide(vanillaBark, vanillaSide, hewnSide, seed, false);
            BufferedImage front = workedSide(vanillaBark, vanillaSide, hewnSide, seed, true);
            write(textures.resolve("nailed_workbench_top_" + wood.id + ".png"), top);
            write(textures.resolve("nailed_workbench_side_" + wood.id + ".png"), side);
            write(textures.resolve("nailed_workbench_front_" + wood.id + ".png"), front);
            write(textures.resolve("nailed_workbench_bottom_" + wood.id + ".png"), bottom);
            writeModel(models.resolve("nailed_workbench_" + wood.id + ".json"), wood.id);
            atlas.add(top); atlas.add(side); atlas.add(front); atlas.add(bottom);
        }
        writeBlockstate(root.resolve("src/main/resources/assets/hardwrought/blockstates/nailed_workbench.json"));
        writeItemDefinition(root.resolve("src/main/resources/assets/hardwrought/items/nailed_workbench.json"));
        write(root.resolve("art_source/bench_tiers/nailed_workbench_pixel_atlas.png"), atlas(atlas));
        System.out.println("Generated 52 nailed-workbench textures for 13 timber variants.");
    }

    private static BufferedImage workedSide(BufferedImage bark, BufferedImage stripped,
                                             BufferedImage hewnSide, int seed, boolean front) {
        BufferedImage out = copy(bark);
        int[] depths = {8, 8, 7, 7, 8, 9, 9, 8, 8, 7, 8, 9, 9, 8, 7, 8};
        int offset = Math.floorMod(seed, depths.length);

        // Continue the same uneven transition as the hewn bench, but cut about twice as deep. The
        // final boundary row retains some bark so the face still reads as one worked log.
        for (int x = 0; x < 16; x++) {
            int depth = depths[(x + offset) % depths.length];
            for (int y = 0; y < depth; y++) {
                double strippedAmount = y == depth - 1 ? 0.62 : 0.94;
                int worked = blend(bark.getRGB(x, y), stripped.getRGB(x, y), strippedAmount);
                if (y < 2) worked = blend(worked, hewnSide.getRGB(x, y), 0.28);
                out.setRGB(x, y, worked);
            }
        }

        int workedDark = adjust(average(stripped), 0.62);
        int workedLight = adjust(average(stripped), 1.14);
        int barkDark = adjust(average(bark), 0.68);
        toolMark(out, 1 + Math.floorMod(seed, 2), 2, 4, workedDark, workedLight);
        toolMark(out, 9, 3 + Math.floorMod(seed / 3, 2), 4, workedDark, workedLight);
        toolMark(out, 4, 6, 3, workedDark, workedLight);
        out.setRGB(2 + Math.floorMod(seed, 11), 8, barkDark);
        out.setRGB(3 + Math.floorMod(seed / 7, 9), 9, barkDark);

        if (front) {
            // Three irregularly placed, round hammered heads identify the joined tier. Their cool
            // highlight keeps them legible as iron instead of reading as holes in the timber, and
            // the asymmetric placement avoids making a face on the front of the block.
            nail(out, 2, 2);
            nail(out, 11, 3);
            nail(out, 6, 5);
        }
        return out;
    }

    private static void toolMark(BufferedImage image, int x, int y, int length,
                                 int dark, int light) {
        for (int i = 0; i < length && x + i < 16; i++) {
            int yy = y + (i >= length / 2 ? 1 : 0);
            image.setRGB(x + i, yy, dark);
            if (yy > 0 && i > 0 && i < length - 1) image.setRGB(x + i, yy - 1, light);
        }
    }

    private static void nail(BufferedImage image, int x, int y) {
        // A five-pixel diamond reads round at native resolution. The bright centre is deliberately
        // small and desaturated: forged iron, not a shiny screw or a black cavity in the wood.
        image.setRGB(x + 1, y, 0xff626966);
        image.setRGB(x, y + 1, 0xff555c59);
        image.setRGB(x + 1, y + 1, 0xffaeb7b2);
        image.setRGB(x + 2, y + 1, 0xff343938);
        image.setRGB(x + 1, y + 2, 0xff292e2d);
    }

    private static void line(BufferedImage image, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++)
            image.setRGB(x1 + (x2 - x1) * i / Math.max(1, steps),
                    y1 + (y2 - y1) * i / Math.max(1, steps), color);
    }

    private static int average(BufferedImage image) {
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int p = image.getRGB(x, y); r += p >>> 16 & 255; g += p >>> 8 & 255; b += p & 255;
        }
        return 0xff000000 | (int) (r / 256) << 16 | (int) (g / 256) << 8 | (int) (b / 256);
    }

    private static int adjust(int pixel, double factor) {
        return 0xff000000 | clamp((int) ((pixel >>> 16 & 255) * factor)) << 16
                | clamp((int) ((pixel >>> 8 & 255) * factor)) << 8
                | clamp((int) ((pixel & 255) * factor));
    }

    private static int blend(int first, int second, double amount) {
        int r = (int) ((first >>> 16 & 255) * (1 - amount) + (second >>> 16 & 255) * amount);
        int g = (int) ((first >>> 8 & 255) * (1 - amount) + (second >>> 8 & 255) * amount);
        int b = (int) ((first & 255) * (1 - amount) + (second & 255) * amount);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    private static void writeModel(Path path, String wood) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "parent": "minecraft:block/orientable_with_bottom",
                  "textures": {
                    "top": "hardwrought:block/nailed_workbench_top_%s",
                    "side": "hardwrought:block/nailed_workbench_side_%s",
                    "front": "hardwrought:block/nailed_workbench_front_%s",
                    "bottom": "hardwrought:block/nailed_workbench_bottom_%s",
                    "particle": "hardwrought:block/nailed_workbench_side_%s"
                  }
                }
                """.formatted(wood, wood, wood, wood, wood));
    }

    private static void writeBlockstate(Path path) throws IOException {
        StringBuilder json = new StringBuilder("{\n  \"variants\": {\n");
        boolean first = true;
        for (Wood wood : WOODS) for (String facing : List.of("north", "east", "south", "west")) {
            if (!first) json.append(",\n"); first = false;
            int rotation = switch (facing) { case "east" -> 90; case "south" -> 180; case "west" -> 270; default -> 0; };
            json.append("    \"facing=").append(facing).append(",wood=").append(wood.id)
                    .append("\": {\"model\": \"hardwrought:block/nailed_workbench_").append(wood.id).append("\"");
            if (rotation != 0) json.append(", \"y\": ").append(rotation);
            json.append("}");
        }
        json.append("\n  }\n}\n"); Files.createDirectories(path.getParent()); Files.writeString(path, json);
    }

    private static void writeItemDefinition(Path path) throws IOException {
        StringBuilder json = new StringBuilder("{\n  \"model\": {\n    \"type\": \"minecraft:select\",\n"
                + "    \"property\": \"minecraft:block_state\",\n    \"block_state_property\": \"wood\",\n    \"cases\": [\n");
        for (int i = 1; i < WOODS.size(); i++) {
            Wood wood = WOODS.get(i); if (i > 1) json.append(",\n");
            json.append("      {\"when\": \"").append(wood.id).append("\", \"model\": {\"type\": \"minecraft:model\", \"model\": \"hardwrought:block/nailed_workbench_").append(wood.id).append("\"}}");
        }
        json.append("\n    ],\n    \"fallback\": {\"type\": \"minecraft:model\", \"model\": \"hardwrought:block/nailed_workbench_oak\"}\n  }\n}\n");
        Files.createDirectories(path.getParent()); Files.writeString(path, json);
    }

    private static BufferedImage atlas(List<BufferedImage> cells) {
        BufferedImage out = new BufferedImage(64, WOODS.size() * 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (int i = 0; i < cells.size(); i++) g.drawImage(cells.get(i), i % 4 * 16, i / 4 * 16, null);
        g.dispose(); return out;
    }

    private static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null || image.getWidth() != 16 || image.getHeight() != 16)
            throw new IOException("Expected 16x16 PNG: " + path);
        return image;
    }

    private static BufferedImage readAny(Path path) throws IOException {
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
        if (image.getWidth() != 16 || image.getHeight() != 16) {
            throw new IllegalArgumentException("Expected 16x16 texture, got "
                    + image.getWidth() + "x" + image.getHeight());
        }
        return image;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics(); g.drawImage(source, 0, 0, null); g.dispose(); return out;
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent()); ImageIO.write(image, "png", path.toFile());
    }
}
