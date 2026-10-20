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
 * Writes the models, blockstates, item definitions, loot table and recipes of the motors
 * (docs/electricity-roadmap.md, stage E2): the electric drive, the motor controller and the parts the
 * electric motor is now built from — stator, rotor, commutator, graphite brush and housing.
 *
 * <p>Run from the repository root with a JDK: {@code java tools/MotorAssets.java}. It overwrites what it
 * writes and touches nothing else. Names and tags are kept by hand.
 */
public final class MotorAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final String TEX = "hardwrought:block/hardwrought/";
    private static final String[] PARTS = {"stator", "rotor", "commutator", "graphite_brush", "motor_housing", "motor_controller"};

    public static void main(String[] args) throws IOException {
        items();
        models();
        blockstates();
        loot();
        recipes();
        System.out.println("Motor assets written.");
    }

    // ------------------------------------------------------------------ textures

    private static final int IRON = 0x8A8E94, IRON_DARK = 0x55595F, COPPER = 0xC0703A, COPPER_DARK = 0x7E4220,
            ENAMEL = 0x8A2E1E, ENAMEL_DARK = 0x5A1A10, GRAPHITE = 0x3A3A3E, PAINT = 0x50667A, PAINT_DARK = 0x34444F;

    private interface Paint {
        void on(BufferedImage img, Random rng);
    }

    private static BufferedImage blank() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private static BufferedImage noisy(int base, int spread, long seed, Paint paint) {
        Random rng = new Random(seed);
        BufferedImage img = blank();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, shade(base, rng.nextInt(spread * 2 + 1) - spread));
        if (paint != null) paint.on(img, rng);
        return img;
    }

    private static void border(BufferedImage img, int colour) {
        for (int i = 0; i < 16; i++) {
            img.setRGB(i, 0, argb(colour));
            img.setRGB(i, 15, argb(colour));
            img.setRGB(0, i, argb(colour));
            img.setRGB(15, i, argb(colour));
        }
    }

    private static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int colour) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) img.setRGB(x, y, argb(colour));
    }

    /** Keeps each pixel's lightness and gives it this colour. */
    private static BufferedImage tint(BufferedImage source, int colour) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Color target = new Color(colour);
        for (int x = 0; x < source.getWidth(); x++) for (int y = 0; y < source.getHeight(); y++) {
            Color c = new Color(source.getRGB(x, y), true);
            if (c.getAlpha() == 0) continue;
            double lum = (0.3 * c.getRed() + 0.59 * c.getGreen() + 0.11 * c.getBlue()) / 160.0;
            out.setRGB(x, y, (c.getAlpha() << 24) | (clamp(target.getRed() * lum) << 16)
                    | (clamp(target.getGreen() * lum) << 8) | clamp(target.getBlue() * lum));
        }
        return out;
    }

    private static int shade(int colour, int by) {
        return argb((clamp(((colour >> 16) & 0xFF) + by) << 16) | (clamp(((colour >> 8) & 0xFF) + by) << 8)
                | clamp((colour & 0xFF) + by));
    }

    private static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }

    private static BufferedImage read(Path path) throws IOException {
        return ImageIO.read(path.toFile());
    }

    private static void write(BufferedImage img, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "png", path.toFile());
    }

    // ------------------------------------------------------------------ item definitions

    private static void items() throws IOException {
        for (String item : PARTS) {
            text(ASSETS.resolve("models/item/" + item + ".json"), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "hardwrought:item/%s"
                      }
                    }
                    """.formatted(item));
            itemDefinition(item, "hardwrought:item/" + item);
        }
        itemDefinition("electric_drive", "hardwrought:block/electric_drive");
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

    // ------------------------------------------------------------------ models

    private static void models() throws IOException {
        Path blocks = ASSETS.resolve("models/block");
        // The drive: the dynamo's frame, painted and wound in enamel.
        String dynamo = Files.readString(blocks.resolve("dynamo.json"));
        String drive = dynamo.replace(TEX + "dynamo_coil", TEX + "drive_coil").replace(TEX + "dynamo_iron", TEX + "drive_housing");
        text(blocks.resolve("electric_drive.json"), drive);
        // With a controller: a black box on its housing beside the terminal, its dial to the front.
        List<String> controller = new ArrayList<>();
        controller.add(box(12.5, 13, 3, 15.5, 16, 9, "#controller_side", "#controller_front"));
        int end = drive.lastIndexOf(']');
        String controlled = drive.substring(0, end).stripTrailing() + ",\n" + String.join(",\n", controller) + "\n  ]" + drive.substring(end + 1);
        controlled = controlled.replaceFirst("\"textures\"\\s*:\\s*\\{",
                "\"textures\": {\n    \"controller_side\": \"" + TEX + "motor_controller_side\",\n    \"controller_front\": \""
                        + TEX + "motor_controller_front\",");
        text(blocks.resolve("electric_drive_controlled.json"), controlled);
    }

    /** A box with one texture on its north face and another on the rest. */
    private static String box(double x0, double y0, double z0, double x1, double y1, double z1, String side, String front) {
        return """
                    {
                      "from": [%s, %s, %s],
                      "to": [%s, %s, %s],
                      "faces": {
                        "north": {"texture": "%s"},
                        "south": {"texture": "%s"},
                        "east": {"texture": "%s"},
                        "west": {"texture": "%s"},
                        "up": {"texture": "%s"},
                        "down": {"texture": "%s"}
                      }
                    }""".formatted(n(x0), n(y0), n(z0), n(x1), n(y1), n(z1), front, side, side, side, side, side);
    }

    private static String n(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    // ------------------------------------------------------------------ blockstates

    private static void blockstates() throws IOException {
        // As the dynamo: the axle along the facing, so north and south share a model, east and west too.
        String[] facings = {"north", "south", "east", "west"};
        int[] turns = {0, 0, 90, 90};
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (boolean controlled : new boolean[] {false, true}) {
                StringBuilder out = new StringBuilder("    \"controlled=" + controlled + ",facing=" + facings[i]
                        + "\": {\n      \"model\": \"hardwrought:block/electric_drive" + (controlled ? "_controlled" : "") + "\"");
                if (turns[i] != 0) out.append(",\n      \"y\": ").append(turns[i]);
                variants.add(out.append("\n    }").toString());
            }
        }
        text(ASSETS.resolve("blockstates/electric_drive.json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
    }

    // ------------------------------------------------------------------ loot

    private static void loot() throws IOException {
        Path tables = DATA.resolve("loot_table/blocks");
        String plain = Files.readString(tables.resolve("insulator.json"));
        text(tables.resolve("electric_drive.json"), plain.replace("hardwrought:insulator", "hardwrought:electric_drive"));
    }

    // ------------------------------------------------------------------ recipes

    private static void recipes() throws IOException {
        shaped("stator", 1, new String[] {"PCP", "W W", "PCP"}, "P", "hardwrought:iron_plate", "C", "hardwrought:redstone_coil",
                "W", "hardwrought:copper_wire");
        shaped("rotor", 1, new String[] {" P ", "WSW", " P "}, "P", "hardwrought:iron_plate", "W", "hardwrought:copper_wire",
                "S", "hardwrought:shaft");
        shaped("motor_housing", 1, new String[] {"PPP", "P P", "PPP"}, "P", "hardwrought:iron_plate");
        shaped("electric_motor", 1, new String[] {"GHG", "BSB", " RC"}, "G", "hardwrought:graphite_brush",
                "H", "hardwrought:motor_housing", "B", "hardwrought:bearing", "S", "hardwrought:stator",
                "R", "hardwrought:rotor", "C", "hardwrought:commutator");
        shaped("electric_drive", 1, new String[] {"PPP", "SMS", "PPP"}, "P", "hardwrought:iron_plate", "S", "hardwrought:shaft",
                "M", "hardwrought:electric_motor");
        shaped("motor_controller", 1, new String[] {"RRR", "WCW", "PPP"}, "R", "hardwrought:resistor", "W", "hardwrought:copper_wire",
                "C", "hardwrought:basic_circuit", "P", "hardwrought:iron_plate");
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
                + "  \"key\": {\n" + keys + "\n  },\n  \"pattern\": [\n" + rows + "\n  ],\n" + result(name, count) + "}\n");
    }

    private static String result(String name, int count) {
        return "  \"result\": {\n    \"id\": \"hardwrought:" + name + "\"" + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n";
    }

    private static void text(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
