import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the textures, models, item definitions and recipes of the magic roadmap's stages
 * (docs/magic-roadmap.md): the mithril and void crystal wands and void ink (M2), parchment, rune ink and
 * scrolls (M3), and the rune tome (M6).
 *
 * <p>Run from the repository root with a JDK: {@code java tools/MagicAssets.java}. It overwrites what it
 * writes and touches nothing else. Placeholders: see textureRequirements.md.
 */
public final class MagicAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final Path ITEM_TEX = ASSETS.resolve("textures/item");

    public static void main(String[] args) throws IOException {
        wands();
        scrolls();
        tome();
        System.out.println("Magic assets written.");
    }

    // ------------------------------------------------------------------ M2

    private static void wands() throws IOException {
        BufferedImage gold = ImageIO.read(ITEM_TEX.resolve("golden_wand.png").toFile());
        write(tint(gold, 0x73E6D1), ITEM_TEX.resolve("mithril_wand.png"));
        write(tint(gold, 0x8A5AD8), ITEM_TEX.resolve("void_crystal_wand.png"));
        for (String wand : new String[] {"mithril_wand", "void_crystal_wand"}) {
            text(ASSETS.resolve("models/item/" + wand + ".json"), """
                    {
                      "parent": "minecraft:item/handheld",
                      "textures": {
                        "layer0": "hardwrought:item/%s"
                      }
                    }
                    """.formatted(wand));
            itemDefinition(wand);
        }
        BufferedImage ink = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(ink, 6, 2, 9, 3, 0x6A4A2A);
        fill(ink, 5, 4, 10, 4, 0xCFE6EE);
        fill(ink, 4, 5, 11, 13, 0xCFE6EE);
        fill(ink, 5, 7, 10, 12, 0x3A1A6A);
        ink.setRGB(7, 9, argb(0xA670F0));
        ink.setRGB(8, 10, argb(0xC8A0FF));
        write(ink, ITEM_TEX.resolve("void_ink.png"));
        generated("void_ink");
        shapeless("mithril_wand", 1, "hardwrought:golden_wand", "hardwrought:mithril_ingot", "hardwrought:mithril_ingot",
                "minecraft:amethyst_shard", "minecraft:amethyst_shard");
        shapeless("void_crystal_wand", 1, "hardwrought:mithril_wand", "hardwrought:void_crystal_shard", "hardwrought:void_crystal_shard",
                "minecraft:echo_shard");
        shapeless("void_ink", 2, "hardwrought:void_crystal_shard", "minecraft:ink_sac");
    }

    // ------------------------------------------------------------------ M3

    private static void scrolls() throws IOException {
        int parchment = 0xE8D8B0, edge = 0xB89A6A, ink = 0x2A3A6A;
        BufferedImage sheet = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(sheet, 3, 2, 12, 13, parchment);
        fill(sheet, 3, 13, 12, 13, edge);
        fill(sheet, 12, 2, 12, 13, edge);
        write(sheet, ITEM_TEX.resolve("parchment.png"));
        generated("parchment");
        BufferedImage bottle = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(bottle, 6, 2, 9, 3, 0x6A4A2A);
        fill(bottle, 5, 4, 10, 4, 0xCFE6EE);
        fill(bottle, 4, 5, 11, 13, 0xCFE6EE);
        fill(bottle, 5, 7, 10, 12, ink);
        bottle.setRGB(7, 9, argb(0xFFD978));
        write(bottle, ITEM_TEX.resolve("rune_ink.png"));
        generated("rune_ink");
        BufferedImage scroll = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(scroll, 3, 3, 12, 12, parchment);
        fill(scroll, 2, 2, 13, 3, edge);
        fill(scroll, 2, 12, 13, 13, edge);
        for (int i = 0; i < 5; i++) scroll.setRGB(5 + i, 9 - i, argb(0xFFD978));
        fill(scroll, 5, 6, 9, 6, ink);
        fill(scroll, 6, 10, 10, 10, ink);
        write(scroll, ITEM_TEX.resolve("scroll.png"));
        generated("scroll");
        shapeless("parchment", 3, "minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:leather");
        shapeless("rune_ink", 2, "minecraft:ink_sac", "minecraft:glowstone_dust", "minecraft:amethyst_shard");
    }

    // ------------------------------------------------------------------ M6

    /** A rune tome: a dark leather book with brass corners and one rune glowing on the cover. Found, not crafted. */
    private static void tome() throws IOException {
        int leather = 0x4A2E2A, dark = 0x2E1A18, pages = 0xE8D8B0, brass = 0xC8A048, rune = 0x8AD8FF;
        BufferedImage book = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(book, 3, 2, 12, 13, leather);
        fill(book, 3, 2, 4, 13, dark);
        fill(book, 12, 3, 13, 13, pages);
        fill(book, 3, 13, 13, 13, dark);
        book.setRGB(3, 2, argb(brass));
        book.setRGB(11, 2, argb(brass));
        book.setRGB(3, 12, argb(brass));
        book.setRGB(11, 12, argb(brass));
        for (int i = 0; i < 4; i++) book.setRGB(6 + i, 9 - i, argb(rune));
        fill(book, 6, 5, 9, 5, rune);
        write(book, ITEM_TEX.resolve("rune_tome.png"));
        generated("rune_tome");
    }

    // ------------------------------------------------------------------ helpers

    /** Keeps each pixel's lightness and gives it this colour. */
    static BufferedImage tint(BufferedImage source, int colour) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Color target = new Color(colour);
        for (int x = 0; x < source.getWidth(); x++) for (int y = 0; y < source.getHeight(); y++) {
            Color c = new Color(source.getRGB(x, y), true);
            if (c.getAlpha() == 0) continue;
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            // Only the metal takes the colour: the wooden grip keeps its own.
            boolean metal = hsb[1] < 0.6 || hsb[0] > 0.09 && hsb[0] < 0.2;
            if (!metal) {
                out.setRGB(x, y, c.getRGB());
                continue;
            }
            double lum = (0.3 * c.getRed() + 0.59 * c.getGreen() + 0.11 * c.getBlue()) / 170.0;
            out.setRGB(x, y, (c.getAlpha() << 24) | (clamp(target.getRed() * lum) << 16)
                    | (clamp(target.getGreen() * lum) << 8) | clamp(target.getBlue() * lum));
        }
        return out;
    }

    static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int colour) {
        for (int x = Math.max(0, x0); x <= Math.min(15, x1); x++) for (int y = Math.max(0, y0); y <= Math.min(15, y1); y++) img.setRGB(x, y, argb(colour));
    }

    static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }

    static void write(BufferedImage img, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "png", path.toFile());
    }

    static void generated(String name) throws IOException {
        text(ASSETS.resolve("models/item/" + name + ".json"), """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "hardwrought:item/%s"
                  }
                }
                """.formatted(name));
        itemDefinition(name);
    }

    static void itemDefinition(String name) throws IOException {
        text(ASSETS.resolve("items/" + name + ".json"), """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "hardwrought:item/%s"
                  }
                }
                """.formatted(name));
    }

    static void shapeless(String name, int count, String... ingredients) throws IOException {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < ingredients.length; i++) {
            if (i > 0) list.append(",\n");
            list.append("    \"").append(ingredients[i]).append('"');
        }
        text(DATA.resolve("recipe/" + name + ".json"), "{\n  \"type\": \"minecraft:crafting_shapeless\",\n  \"category\": \"equipment\",\n"
                + "  \"ingredients\": [\n" + list + "\n  ],\n  \"result\": {\n    \"id\": \"hardwrought:" + name + "\""
                + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n}\n");
    }

    static void text(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
