import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Writes the textures, models, blockstates, item definitions, loot tables and recipes of the Tesla tower
 * (docs/electricity-roadmap.md, stage E6): its foot, the resonant coil, the top load, the capacitor bank
 * and the spark gap.
 *
 * <p>Run from the repository root with a JDK, after {@code tools/HavenAssets.java}:
 * {@code java tools/TeslaAssets.java}. It overwrites what it writes and touches nothing else.
 */
public final class TeslaAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block/hardwrought");
    private static final String TEX = "hardwrought:block/hardwrought/";
    private static final int ENAMEL = 0x8A2E1E, PORCELAIN = 0xF2EFE6, COPPER = 0xD08048, STEEL = 0xA8AEB6, TUNGSTEN = 0x8C8F99;

    public static void main(String[] args) throws IOException {
        textures();
        models();
        recipes();
        System.out.println("Tesla assets written.");
    }

    private static void textures() throws IOException {
        text(ASSETS.resolve("models/item/spark_gap.json"), """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "hardwrought:item/spark_gap"
                  }
                }
                """);
        itemDefinition("spark_gap", "hardwrought:item/spark_gap");
    }

    private static void models() throws IOException {
        Path blocks = ASSETS.resolve("models/block");
        text(blocks.resolve("resonant_coil.json"), """
                {
                  "parent": "minecraft:block/cube_column",
                  "textures": {
                    "side": "%sresonant_coil",
                    "end": "%sresonant_coil_end"
                  }
                }
                """.formatted(TEX, TEX));
        text(blocks.resolve("top_load.json"), """
                {
                  "parent": "minecraft:block/cube_all",
                  "textures": {
                    "all": "%stop_load"
                  }
                }
                """.formatted(TEX));
        text(blocks.resolve("capacitor_bank.json"), """
                {
                  "parent": "minecraft:block/cube_all",
                  "textures": {
                    "all": "%scapacitor_bank"
                  }
                }
                """.formatted(TEX));
        for (String plain : List.of("resonant_coil", "top_load", "capacitor_bank")) {
            text(ASSETS.resolve("blockstates/" + plain + ".json"), """
                    {
                      "variants": {
                        "": {
                          "model": "hardwrought:block/%s"
                        }
                      }
                    }
                    """.formatted(plain));
            itemDefinition(plain, "hardwrought:block/" + plain);
            selfDrop(plain);
        }
        for (boolean on : new boolean[] {false, true}) {
            text(blocks.resolve("tesla_tower" + (on ? "_on" : "") + ".json"), """
                    {
                      "parent": "minecraft:block/orientable",
                      "textures": {
                        "front": "%stesla_tower_front%s",
                        "side": "%sappliance_casing",
                        "top": "%sresonant_coil_end",
                        "particle": "%sappliance_casing"
                      }
                    }
                    """.formatted(TEX, on ? "_on" : "", TEX, TEX, TEX));
        }
        String[] facings = {"north", "east", "south", "west"};
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (boolean on : new boolean[] {false, true}) {
                variants.add("    \"facing=" + facings[i] + ",on=" + on + "\": {\n      \"model\": \"hardwrought:block/tesla_tower"
                        + (on ? "_on" : "") + "\"" + (i == 0 ? "" : ",\n      \"y\": " + i * 90) + "\n    }");
            }
        }
        text(ASSETS.resolve("blockstates/tesla_tower.json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
        itemDefinition("tesla_tower", "hardwrought:block/tesla_tower");
        selfDrop("tesla_tower");
    }

    private static void recipes() throws IOException {
        shaped("spark_gap", 1, new String[] {"T T", " P "}, "T", "hardwrought:tungsten_rod", "P", "hardwrought:porcelain");
        shaped("resonant_coil", 1, new String[] {"PEP", "EWE", "PEP"}, "P", "hardwrought:porcelain", "E", "hardwrought:enamelled_wire",
                "W", "hardwrought:winding");
        shaped("top_load", 1, new String[] {"CCC", "CBC", "CCC"}, "C", "hardwrought:copper_plate", "B", "minecraft:copper_block");
        shaped("capacitor_bank", 1, new String[] {"SCS", "COC", "SCS"}, "S", "hardwrought:steel_plate", "C", "hardwrought:capacitor",
                "O", "hardwrought:heavy_oil");
        shaped("tesla_tower", 1, new String[] {"ESE", "TWT", "PPP"}, "E", "hardwrought:elite_circuit", "S", "hardwrought:spark_gap",
                "T", "hardwrought:transformer", "W", "hardwrought:winding", "P", "hardwrought:steel_plate");
    }

    // ------------------------------------------------------------------ helpers

    private interface Paint {
        void on(BufferedImage img, Random rng);
    }

    private static BufferedImage noisy(int base, int spread, long seed, Paint paint) {
        Random rng = new Random(seed);
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, argb(shade(base, rng.nextInt(spread * 2 + 1) - spread)));
        if (paint != null) paint.on(img, rng);
        return img;
    }

    private static int shade(int colour, int by) {
        return (clamp(((colour >> 16) & 0xFF) + by) << 16) | (clamp(((colour >> 8) & 0xFF) + by) << 8) | clamp((colour & 0xFF) + by);
    }

    private static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }

    private static void write(BufferedImage img, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "png", path.toFile());
    }

    private static void itemDefinition(String name, String model) throws IOException {
        text(ASSETS.resolve("items/" + name + ".json"), """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s"
                  }
                }
                """.formatted(model));
    }

    private static void selfDrop(String block) throws IOException {
        Path tables = DATA.resolve("loot_table/blocks");
        text(tables.resolve(block + ".json"), Files.readString(tables.resolve("insulator.json")).replace("hardwrought:insulator", "hardwrought:" + block));
    }

    private static void shaped(String name, int count, String[] pattern, String... key) throws IOException {
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i < key.length; i += 2) {
            if (i > 0) keys.append(",\n");
            keys.append("    \"").append(key[i]).append("\": \"").append(key[i + 1]).append('"');
        }
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < pattern.length; i++) {
            if (i > 0) rows.append(",\n");
            rows.append("    \"").append(pattern[i]).append('"');
        }
        text(DATA.resolve("recipe/" + name + ".json"), "{\n  \"type\": \"minecraft:crafting_shaped\",\n  \"category\": \"redstone\",\n"
                + "  \"key\": {\n" + keys + "\n  },\n  \"pattern\": [\n" + rows + "\n  ],\n  \"result\": {\n    \"id\": \"hardwrought:"
                + name + "\"" + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n}\n");
    }

    private static void text(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
