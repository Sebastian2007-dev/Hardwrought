import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Exports the AI-created powder atlas to crisp 16x16 Minecraft item textures. */
public final class PowderTextureExporter {
    private static final String[] NAMES = {
            // Redstone remains in the source atlas ordering, but is deliberately skipped on export:
            // Minecraft already provides redstone dust.
            "coal", "copper", "iron", "gold", "redstone", "lapis_lazuli", "diamond",
            "emerald", "nether_gold", "nether_quartz", "ancient_debris", "tin", "zinc",
            "lead", "manganese", "magnesium", "aluminum", "nickel", "cobalt", "chromium",
            "mercury", "titanium", "tungsten", "uranium", "thorium", "platinum"
    };

    private PowderTextureExporter() { }

    public static void main(String[] args) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        BufferedImage atlas = ImageIO.read(root.resolve("art_source/powder_atlas.png").toFile());
        Path output = root.resolve("src/main/resources/assets/hardwrought/textures/item");
        Files.createDirectories(output);

        for (int index = 0; index < NAMES.length; index++) {
            if (NAMES[index].equals("redstone")) continue;
            int column = index % 13;
            int row = index / 13;
            int x0 = column * atlas.getWidth() / 13;
            int x1 = (column + 1) * atlas.getWidth() / 13;
            int y0 = row * atlas.getHeight() / 2;
            int y1 = (row + 1) * atlas.getHeight() / 2;
            BufferedImage sprite = exportCell(atlas, x0, y0, x1, y1);
            ImageIO.write(sprite, "png", output.resolve(NAMES[index] + "_powder.png").toFile());
            writeItemModels(root, NAMES[index]);
            writeMaterialTag(root, NAMES[index]);
        }
        writePowdersTag(root);
        writePreview(output, root.resolve("art_source/powder_textures_preview.png"));

        BufferedImage bronzeSource = ImageIO.read(root.resolve("art_source/bronze_mixture_source.png").toFile());
        BufferedImage bronzeMixture = exportCell(bronzeSource, 0, 0,
                bronzeSource.getWidth(), bronzeSource.getHeight());
        ImageIO.write(bronzeMixture, "png", output.resolve("bronze_mixture.png").toFile());
        writeScaledPreview(bronzeMixture, root.resolve("art_source/bronze_mixture_preview.png"));
    }

    private static void writePreview(Path textures, Path target) throws IOException {
        int scale = 10;
        BufferedImage preview = new BufferedImage(13 * 16 * scale, 2 * 16 * scale,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int previewIndex = 0;
        for (String name : NAMES) {
            if (name.equals("redstone")) continue;
            BufferedImage texture = ImageIO.read(textures.resolve(name + "_powder.png").toFile());
            int x = (previewIndex % 13) * 16 * scale;
            int y = (previewIndex / 13) * 16 * scale;
            graphics.drawImage(texture, x, y, 16 * scale, 16 * scale, null);
            previewIndex++;
        }
        graphics.dispose();
        ImageIO.write(preview, "png", target.toFile());
    }

    private static void writeScaledPreview(BufferedImage texture, Path target) throws IOException {
        BufferedImage preview = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(texture, 0, 0, 160, 160, null);
        graphics.dispose();
        ImageIO.write(preview, "png", target.toFile());
    }

    private static void writeItemModels(Path root, String name) throws IOException {
        String id = name + "_powder";
        Path itemDefinition = root.resolve("src/main/resources/assets/hardwrought/items/" + id + ".json");
        Path itemModel = root.resolve("src/main/resources/assets/hardwrought/models/item/" + id + ".json");
        Files.createDirectories(itemDefinition.getParent());
        Files.createDirectories(itemModel.getParent());
        Files.writeString(itemDefinition, """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "hardwrought:item/%s"
                  }
                }
                """.formatted(id));
        Files.writeString(itemModel, """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "hardwrought:item/%s"
                  }
                }
                """.formatted(id));
    }

    private static void writeMaterialTag(Path root, String name) throws IOException {
        Path tag = root.resolve("src/main/resources/data/c/tags/item/powders/" + name + ".json");
        Files.createDirectories(tag.getParent());
        Files.writeString(tag, """
                {
                  "values": [
                    "hardwrought:%s_powder"
                  ]
                }
                """.formatted(name));
    }

    private static void writePowdersTag(Path root) throws IOException {
        Path tag = root.resolve("src/main/resources/data/c/tags/item/powders.json");
        Files.createDirectories(tag.getParent());
        String values = Arrays.stream(NAMES)
                .filter(name -> !name.equals("redstone"))
                .map(name -> "    \"#c:powders/" + name + "\"")
                .collect(Collectors.joining(",\n"));
        Files.writeString(tag, "{\n  \"values\": [\n" + values + "\n  ]\n}\n");
    }

    private static BufferedImage exportCell(BufferedImage source, int x0, int y0, int x1, int y1) {
        int left = x1;
        int right = x0;
        int top = y1;
        int bottom = y0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((source.getRGB(x, y) >>> 24) >= 48) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < left || bottom < top) throw new IllegalStateException("Empty powder atlas cell");

        int sourceWidth = right - left + 1;
        int sourceHeight = bottom - top + 1;
        double scale = Math.min(14.0 / sourceWidth, 13.0 / sourceHeight);
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        int targetX = (16 - targetWidth) / 2;
        int targetY = 15 - targetHeight;

        BufferedImage result = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int ty = 0; ty < targetHeight; ty++) {
            for (int tx = 0; tx < targetWidth; tx++) {
                double sx0 = left + tx * sourceWidth / (double) targetWidth;
                double sx1 = left + (tx + 1) * sourceWidth / (double) targetWidth;
                double sy0 = top + ty * sourceHeight / (double) targetHeight;
                double sy1 = top + (ty + 1) * sourceHeight / (double) targetHeight;
                int argb = average(source, sx0, sy0, sx1, sy1);
                result.setRGB(targetX + tx, targetY + ty, argb);
            }
        }
        return result;
    }

    private static int average(BufferedImage image, double x0, double y0, double x1, double y1) {
        long alpha = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        int samples = 0;
        for (int y = (int) y0; y < Math.ceil(y1); y++) {
            for (int x = (int) x0; x < Math.ceil(x1); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() < 48) continue;
                alpha += color.getAlpha();
                red += color.getRed();
                green += color.getGreen();
                blue += color.getBlue();
                samples++;
            }
        }
        if (samples == 0 || alpha / samples < 80) return 0;
        int a = alpha / samples > 160 ? 255 : 0;
        if (a == 0) return 0;
        int r = quantize((int) (red / samples));
        int g = quantize((int) (green / samples));
        int b = quantize((int) (blue / samples));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int quantize(int channel) {
        return Math.min(255, ((channel + 8) / 17) * 17);
    }
}
