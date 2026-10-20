import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Writes the textures, models, blockstates, item definitions, loot tables and recipes of automation
 * (docs/electricity-roadmap.md, stage E5): the conveyor, the assembler, the second marks of the coil
 * winder, wire drawer and glass kiln, machine oil, the service kit, the drawing and the gripper arm.
 *
 * <p>Run from the repository root with a JDK, after {@code tools/HavenAssets.java}:
 * {@code java tools/AutomationAssets.java}. It overwrites what it writes and touches nothing else.
 */
public final class AutomationAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final Path ITEM_TEX = ASSETS.resolve("textures/item");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block/hardwrought");
    private static final String TEX = "hardwrought:block/hardwrought/";
    private static final String[] FACINGS = {"north", "east", "south", "west"};
    private static final int RUBBER = 0x24242A, STEEL = 0xA8AEB6, IRON_DARK = 0x55595F, BRASS = 0xC8A03C, OIL = 0x4A2E18,
            PAPER = 0xEDE7D4, INK = 0x2A4A8A;

    public static void main(String[] args) throws IOException {
        conveyor();
        assembler();
        stations();
        items();
        recipes();
        System.out.println("Automation assets written.");
    }

    private static void conveyor() throws IOException {
        // The belt runs north (the model's front); the blockstate turns it.
        text(ASSETS.resolve("models/block/conveyor.json"), """
                {
                  "parent": "minecraft:block/block",
                  "textures": {
                    "belt": "%sconveyor_belt",
                    "side": "%sconveyor_side",
                    "particle": "%sconveyor_side"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 6, 16],
                      "faces": {
                        "north": {"texture": "#side", "uv": [0, 10, 16, 16]},
                        "south": {"texture": "#side", "uv": [0, 10, 16, 16]},
                        "east": {"texture": "#side", "uv": [0, 10, 16, 16]},
                        "west": {"texture": "#side", "uv": [0, 10, 16, 16]},
                        "up": {"texture": "#belt"},
                        "down": {"texture": "#side"}
                      }
                    }
                  ]
                }
                """.formatted(TEX, TEX, TEX));
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            variants.add("    \"facing=" + FACINGS[i] + "\": {\n      \"model\": \"hardwrought:block/conveyor\""
                    + (i == 0 ? "" : ",\n      \"y\": " + i * 90) + "\n    }");
        }
        text(ASSETS.resolve("blockstates/conveyor.json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
        itemDefinition("conveyor", "hardwrought:block/conveyor");
        selfDrop("conveyor");
    }

    private static void assembler() throws IOException {
        for (boolean on : new boolean[] {false, true}) {
            text(ASSETS.resolve("models/block/assembler" + (on ? "_on" : "") + ".json"), """
                    {
                      "parent": "minecraft:block/orientable",
                      "textures": {
                        "front": "%sassembler_front%s",
                        "side": "%sappliance_casing",
                        "top": "%sappliance_casing",
                        "particle": "%sappliance_casing"
                      }
                    }
                    """.formatted(TEX, on ? "_on" : "", TEX, TEX, TEX));
        }
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (boolean on : new boolean[] {false, true}) {
                variants.add("    \"facing=" + FACINGS[i] + ",on=" + on + "\": {\n      \"model\": \"hardwrought:block/assembler"
                        + (on ? "_on" : "") + "\"" + (i == 0 ? "" : ",\n      \"y\": " + i * 90) + "\n    }");
            }
        }
        text(ASSETS.resolve("blockstates/assembler.json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
        itemDefinition("assembler", "hardwrought:block/assembler");
        selfDrop("assembler");
    }

    /** The second marks: the first with the second mark's band, as the machines before them. */
    private static void stations() throws IOException {
        Path blocks = ASSETS.resolve("models/block");
        Path states = ASSETS.resolve("blockstates");
        Path tables = DATA.resolve("loot_table/blocks");
        for (String station : List.of("coil_winder", "wire_drawer", "glass_kiln")) {
            String mk2 = station + "_mk2";
            text(blocks.resolve(mk2 + ".json"), Files.readString(blocks.resolve(station + ".json")).replace(TEX + "dynamo_iron\"", TEX + "dynamo_iron_mk2\""));
            text(states.resolve(mk2 + ".json"), Files.readString(states.resolve(station + ".json"))
                    .replace("hardwrought:block/" + station + "\"", "hardwrought:block/" + mk2 + "\""));
            text(tables.resolve(mk2 + ".json"), Files.readString(tables.resolve(station + ".json")).replace("hardwrought:" + station + "\"", "hardwrought:" + mk2 + "\""));
            itemDefinition(mk2, "hardwrought:block/" + mk2);
        }
    }

    private static void items() throws IOException {
        generated("machine_oil");
        generated("service_kit");
        generated("drawing");
        generated("gripper_arm");
    }

    private static void recipes() throws IOException {
        shaped("conveyor", 4, new String[] {"BBB", "PSP"}, "B", "hardwrought:rubber", "P", "hardwrought:iron_plate", "S", "hardwrought:shaft");
        shapeless("machine_oil", 1, "hardwrought:heavy_oil");
        shapeless("service_kit", 1, "hardwrought:bearing", "hardwrought:bearing", "hardwrought:rubber_gasket", "hardwrought:rubber_gasket",
                "hardwrought:machine_oil");
        shapeless("drawing", 4, "minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:black_dye");
        shaped("gripper_arm", 1, new String[] {"JRC"}, "J", "hardwrought:joint_actuator", "R", "hardwrought:steel_rod", "C", "hardwrought:chuck");
        shaped("assembler", 1, new String[] {"SAS", "GBG", "SMS"}, "S", "hardwrought:steel_plate", "A", "hardwrought:advanced_circuit",
                "G", "hardwrought:gripper_arm", "B", "hardwrought:assembly_bench", "M", "hardwrought:electric_motor");
        for (String station : List.of("coil_winder", "wire_drawer", "glass_kiln")) {
            shaped(station + "_mk2", 1, new String[] {" C ", "GMG", " E "}, "C", "hardwrought:advanced_circuit", "G", "hardwrought:silicone_gasket",
                    "M", "hardwrought:" + station, "E", "hardwrought:electric_motor");
        }
    }

    // ------------------------------------------------------------------ helpers

    private interface Paint {
        void on(BufferedImage img, Random rng);
    }

    private interface Draw {
        void on(BufferedImage img);
    }

    private static BufferedImage noisy(int base, int spread, long seed, Paint paint) {
        Random rng = new Random(seed);
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, argb(shadeRgb(base, rng.nextInt(spread * 2 + 1) - spread)));
        if (paint != null) paint.on(img, rng);
        return img;
    }

    private static void item(String name, Draw draw) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        draw.on(img);
        write(img, ITEM_TEX.resolve(name + ".png"));
        generated(name);
    }

    private static void generated(String name) throws IOException {
        text(ASSETS.resolve("models/item/" + name + ".json"), """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "hardwrought:item/%s"
                  }
                }
                """.formatted(name));
        itemDefinition(name, "hardwrought:item/" + name);
    }

    private static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int colour) {
        for (int x = Math.max(0, x0); x <= Math.min(15, x1); x++) for (int y = Math.max(0, y0); y <= Math.min(15, y1); y++) img.setRGB(x, y, argb(colour));
    }

    private static void disc(BufferedImage img, double cx, double cy, double r, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if (Math.hypot(x - cx, y - cy) < r) img.setRGB(x, y, argb(colour));
    }

    private static int shadeRgb(int colour, int by) {
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

    private static void shapeless(String name, int count, String... ingredients) throws IOException {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < ingredients.length; i++) {
            if (i > 0) list.append(",\n");
            list.append("    \"").append(ingredients[i]).append('"');
        }
        text(DATA.resolve("recipe/" + name + ".json"), "{\n  \"type\": \"minecraft:crafting_shapeless\",\n  \"category\": \"redstone\",\n"
                + "  \"ingredients\": [\n" + list + "\n  ],\n  \"result\": {\n    \"id\": \"hardwrought:" + name + "\""
                + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n}\n");
    }

    private static void text(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
