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

/** Rebuilds Hardwrought's 16x16 metal texture set from the matching local Minecraft client jar. */
public final class MetalTextureGenerator {
    private record Palette(int dark, int middle, int light) { }
    private record MetalSpec(String id, String pattern, Palette ore, Palette raw, Palette ingot) { }

    private static final Palette TIN = palette("596269", "b7bec0", "eef0e9");
    private static final Palette CASSITERITE = palette("332d2a", "62574e", "aaa091");
    private static final Palette ZINC = palette("394b58", "728896", "b8c5c8");
    private static final Palette LEAD = palette("282c31", "555b61", "92979a");
    private static final Palette MANGANESE = palette("211b28", "513b5a", "8e6c98");
    private static final Palette MAGNESIUM = palette("72766f", "acb0a7", "dde0d3");
    private static final Palette ALUMINUM = palette("7f898c", "c9d0cf", "f3f5ee");
    private static final Palette NICKEL = palette("665f43", "a59c70", "ded6a4");
    private static final Palette COBALT = palette("14294f", "285496", "5787c2");
    private static final Palette CHROMIUM = palette("67747c", "b6c5ca", "f2f7f4");
    private static final Palette MERCURY = palette("5d1115", "b51f20", "ef4b37");
    private static final Palette TITANIUM = palette("4d5a66", "8e9eaa", "d2dde0");
    private static final Palette TUNGSTEN = palette("23272a", "485055", "788083");
    private static final Palette URANIUM = palette("25311b", "53652d", "89923f");
    private static final Palette THORIUM = palette("29352a", "546947", "889368");
    private static final Palette PLATINUM = palette("6a7581", "bbc9d2", "eff5f6");
    private static final Palette BRONZE = palette("5b2e18", "a65c2d", "dc9650");

    private static final List<MetalSpec> METALS = List.of(
            metal("tin", "copper", TIN, CASSITERITE, TIN),
            metal("zinc", "iron", ZINC),
            metal("lead", "coal", LEAD),
            metal("manganese", "coal", MANGANESE),
            metal("magnesium", "lapis", MAGNESIUM),
            metal("aluminum", "iron", ALUMINUM),
            metal("nickel", "iron", NICKEL),
            metal("cobalt", "lapis", COBALT),
            metal("chromium", "emerald", CHROMIUM),
            metal("mercury", "redstone", MERCURY),
            metal("titanium", "iron", TITANIUM),
            metal("tungsten", "diamond", TUNGSTEN),
            metal("uranium", "emerald", URANIUM),
            metal("thorium", "emerald", THORIUM),
            metal("platinum", "gold", PLATINUM)
    );

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        String profile = System.getenv("USERPROFILE");
        Path clientJar = args.length >= 2 ? Path.of(args[1])
                : Path.of(profile == null ? System.getProperty("user.home") : profile,
                        ".gradle", "caches", "fabric-loom", "26.3", "minecraft-client.jar");
        try (FileSystem minecraft = FileSystems.newFileSystem(clientJar)) {
            generate(root, minecraft.getPath("/assets/minecraft/textures"));
        }
    }

    private static void generate(Path root, Path vanilla) throws Exception {
        Path blocks = root.resolve("src/main/resources/assets/hardwrought/textures/block");
        Path items = root.resolve("src/main/resources/assets/hardwrought/textures/item");
        Path blockModels = root.resolve("src/main/resources/assets/hardwrought/models/block");
        Path itemModels = root.resolve("src/main/resources/assets/hardwrought/models/item");
        Files.createDirectories(blocks);
        Files.createDirectories(items);

        BufferedImage stone = read(vanilla.resolve("block/stone.png"));
        BufferedImage deepslate = read(vanilla.resolve("block/deepslate.png"));
        BufferedImage rawIron = read(vanilla.resolve("item/raw_iron.png"));
        BufferedImage ironIngot = read(vanilla.resolve("item/iron_ingot.png"));
        List<BufferedImage> atlasCells = new ArrayList<>();

        for (MetalSpec metal : METALS) {
            BufferedImage ore = recolorOre(stone,
                    read(vanilla.resolve("block/" + metal.pattern + "_ore.png")), metal.ore);
            BufferedImage deepOre = recolorOre(deepslate,
                    read(vanilla.resolve("block/deepslate_" + metal.pattern + "_ore.png")), metal.ore);
            BufferedImage raw = recolorSprite(rawIron, metal.raw);
            BufferedImage ingot = recolorSprite(ironIngot, metal.ingot);
            write(blocks.resolve(metal.id + "_ore.png"), ore);
            write(blocks.resolve("deepslate_" + metal.id + "_ore.png"), deepOre);
            write(items.resolve("raw_" + metal.id + ".png"), raw);
            write(items.resolve(metal.id + "_ingot.png"), ingot);
            replaceTexture(blockModels.resolve(metal.id + "_ore.json"), "all",
                    "hardwrought:block/" + metal.id + "_ore");
            replaceTexture(blockModels.resolve("deepslate_" + metal.id + "_ore.json"), "all",
                    "hardwrought:block/deepslate_" + metal.id + "_ore");
            replaceTexture(itemModels.resolve("raw_" + metal.id + ".json"), "layer0",
                    "hardwrought:item/raw_" + metal.id);
            replaceTexture(itemModels.resolve(metal.id + "_ingot.json"), "layer0",
                    "hardwrought:item/" + metal.id + "_ingot");
            atlasCells.add(ore);
            atlasCells.add(deepOre);
            atlasCells.add(raw);
            atlasCells.add(ingot);
        }

        BufferedImage bronzeMixture = bronzeMixture(read(vanilla.resolve("item/raw_copper.png")));
        BufferedImage bronzeIngot = recolorSprite(read(vanilla.resolve("item/copper_ingot.png")), BRONZE);
        BufferedImage bronzeHatchet = recolorGoldTool(read(vanilla.resolve("item/golden_axe.png")));
        BufferedImage bronzePickaxe = recolorGoldTool(read(vanilla.resolve("item/golden_pickaxe.png")));
        write(items.resolve("bronze_mixture.png"), bronzeMixture);
        write(items.resolve("bronze_ingot.png"), bronzeIngot);
        write(items.resolve("bronze_hatchet.png"), bronzeHatchet);
        write(items.resolve("bronze_pickaxe.png"), bronzePickaxe);
        for (String bronzeItem : List.of("bronze_mixture", "bronze_ingot", "bronze_hatchet", "bronze_pickaxe")) {
            replaceTexture(itemModels.resolve(bronzeItem + ".json"), "layer0",
                    "hardwrought:item/" + bronzeItem);
        }
        atlasCells.add(bronzeMixture);
        atlasCells.add(bronzeIngot);
        atlasCells.add(bronzeHatchet);
        atlasCells.add(bronzePickaxe);

        write(root.resolve("art_source/metals_pixel_atlas.png"), atlas(atlasCells, 4));
        System.out.println("Generated " + atlasCells.size() + " metal textures and the source atlas.");
    }

    private static BufferedImage recolorOre(BufferedImage host, BufferedImage template, Palette palette) {
        ensure16(host);
        ensure16(template);
        boolean[][] mineral = new boolean[16][16];
        int min = 255;
        int max = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int source = template.getRGB(x, y);
                int base = host.getRGB(x, y);
                if (colorDistance(source, base) >= 18) {
                    mineral[x][y] = true;
                    int luminance = luminance(source);
                    min = Math.min(min, luminance);
                    max = Math.max(max, luminance);
                }
            }
        }
        BufferedImage output = copy(host);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (mineral[x][y]) {
                    double value = max == min ? 0.5 : (luminance(template.getRGB(x, y)) - min) / (double) (max - min);
                    output.setRGB(x, y, shade(palette, value, 255));
                }
            }
        }
        return output;
    }

    private static BufferedImage recolorSprite(BufferedImage source, Palette palette) {
        ensure16(source);
        int min = 255;
        int max = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = source.getRGB(x, y);
                if (alpha(pixel) > 0) {
                    min = Math.min(min, luminance(pixel));
                    max = Math.max(max, luminance(pixel));
                }
            }
        }
        BufferedImage output = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = source.getRGB(x, y);
                int alpha = alpha(pixel);
                if (alpha > 0) {
                    double value = max == min ? 0.5 : (luminance(pixel) - min) / (double) (max - min);
                    output.setRGB(x, y, shade(palette, value, alpha));
                }
            }
        }
        return output;
    }

    private static BufferedImage bronzeMixture(BufferedImage source) {
        ensure16(source);
        BufferedImage copper = recolorSprite(source, BRONZE);
        BufferedImage output = copy(copper);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = source.getRGB(x, y);
                if (alpha(pixel) > 0 && Math.floorMod(x * 17 + y * 31, 7) < 2) {
                    double value = Math.max(0.12, Math.min(0.92, luminance(pixel) / 255.0));
                    output.setRGB(x, y, shade(TIN, value, alpha(pixel)));
                }
            }
        }
        return output;
    }

    private static BufferedImage recolorGoldTool(BufferedImage source) {
        ensure16(source);
        BufferedImage output = copy(source);
        int min = 255;
        int max = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = source.getRGB(x, y);
                if (isGold(pixel)) {
                    min = Math.min(min, luminance(pixel));
                    max = Math.max(max, luminance(pixel));
                }
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = source.getRGB(x, y);
                if (isGold(pixel)) {
                    double value = max == min ? 0.5 : (luminance(pixel) - min) / (double) (max - min);
                    output.setRGB(x, y, shade(BRONZE, value, alpha(pixel)));
                }
            }
        }
        return output;
    }

    private static boolean isGold(int pixel) {
        if (alpha(pixel) == 0) return false;
        int r = red(pixel);
        int g = green(pixel);
        int b = blue(pixel);
        return r > 110 && g > 70 && r > b * 1.45 && g > b * 1.15;
    }

    private static BufferedImage atlas(List<BufferedImage> cells, int columns) {
        int rows = (cells.size() + columns - 1) / columns;
        BufferedImage atlas = new BufferedImage(columns * 16, rows * 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        for (int index = 0; index < cells.size(); index++) {
            graphics.drawImage(cells.get(index), index % columns * 16, index / columns * 16, null);
        }
        graphics.dispose();
        return atlas;
    }

    private static int shade(Palette palette, double value, int alpha) {
        value = Math.max(0.0, Math.min(1.0, value));
        int from = value < 0.5 ? palette.dark : palette.middle;
        int to = value < 0.5 ? palette.middle : palette.light;
        double amount = value < 0.5 ? value * 2.0 : (value - 0.5) * 2.0;
        int r = mix(red(from), red(to), amount);
        int g = mix(green(from), green(to), amount);
        int b = mix(blue(from), blue(to), amount);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static int colorDistance(int first, int second) {
        int r = red(first) - red(second);
        int g = green(first) - green(second);
        int b = blue(first) - blue(second);
        return (int) Math.sqrt(r * r + g * g + b * b);
    }

    private static int luminance(int pixel) {
        return (red(pixel) * 54 + green(pixel) * 183 + blue(pixel) * 19) / 256;
    }

    private static int alpha(int pixel) { return pixel >>> 24; }
    private static int red(int pixel) { return pixel >>> 16 & 0xff; }
    private static int green(int pixel) { return pixel >>> 8 & 0xff; }
    private static int blue(int pixel) { return pixel & 0xff; }
    private static int mix(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return output;
    }

    private static void ensure16(BufferedImage image) {
        if (image.getWidth() != 16 || image.getHeight() != 16) {
            throw new IllegalArgumentException("Expected a 16x16 texture, got "
                    + image.getWidth() + "x" + image.getHeight());
        }
    }

    private static BufferedImage read(Path path) throws IOException {
        BufferedImage image;
        try (InputStream input = Files.newInputStream(path)) {
            image = ImageIO.read(input);
        }
        if (image == null) throw new IOException("Not a PNG: " + path);
        return image;
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    private static void replaceTexture(Path path, String layer, String texture) throws IOException {
        String source = Files.readString(path);
        if (source.contains("\"" + layer + "\": \"" + texture + "\"")) return;
        String pattern = "(\\\"" + layer + "\\\"\\s*:\\s*\\\")[^\\\"]+(\\\")";
        String updated = source.replaceFirst(pattern, "$1" + texture + "$2");
        if (source.equals(updated)) {
            throw new IOException("Texture layer " + layer + " was not found in " + path);
        }
        Files.writeString(path, updated);
    }

    private static Palette palette(String dark, String middle, String light) {
        return new Palette(rgb(dark), rgb(middle), rgb(light));
    }

    private static int rgb(String hex) {
        return 0xff000000 | Integer.parseInt(hex, 16);
    }

    private static MetalSpec metal(String id, String pattern, Palette palette) {
        return metal(id, pattern, palette, palette, palette);
    }

    private static MetalSpec metal(String id, String pattern, Palette ore, Palette raw, Palette ingot) {
        return new MetalSpec(id, pattern, ore, raw, ingot);
    }
}
