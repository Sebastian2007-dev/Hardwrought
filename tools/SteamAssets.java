import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * Writes everything of steam (docs/steam.md) that is not code: the textures of the firebox, the boiler,
 * the engine, the valves and the whistle, drawn here pixel by pixel; their models, blockstates and item
 * definitions; loot tables, crafting recipes, the pickaxe tag and the scalding damage type.
 *
 * <p>Run from the repository root with a JDK: {@code java tools/SteamAssets.java}. It overwrites what it
 * writes and touches nothing else, except that it adds its blocks to existing tags. Names are kept by hand.
 */
public final class SteamAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final Path ITEM_TEX = ASSETS.resolve("textures/item");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block");
    private static final Path BLOCKS = ASSETS.resolve("models/block");

    private static final int IRON = 0x6E6A66, IRON_DARK = 0x3A3734, IRON_LIGHT = 0x9A958E, RIVET = 0xB8B2AA,
            BRICK = 0x8E4A34, SOOT = 0x1E1C1A, FIRE = 0xF08A20, EMBER = 0xFFD050, BRASS = 0xC89A3A, BRASS_DARK = 0x7A5A1E,
            BRASS_LIGHT = 0xF0D080, PAINT = 0x3E5A44, COPPER = 0xB8683A;

    public static void main(String[] args) throws IOException {
        textures();
        models();
        loot();
        recipes();
        data();
        System.out.println("Steam assets written.");
    }

    // ------------------------------------------------------------------ painting

    private interface Draw {
        void on(BufferedImage img);
    }

    private static BufferedImage blank() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private static BufferedImage noisy(int base, int spread, long seed) {
        Random rng = new Random(seed);
        BufferedImage img = blank();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, shade(base, rng.nextInt(spread * 2 + 1) - spread));
        return img;
    }

    private static void px(BufferedImage img, int x, int y, int colour) {
        if (x >= 0 && x < 16 && y >= 0 && y < 16) img.setRGB(x, y, argb(colour));
    }

    private static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int colour) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) px(img, x, y, colour);
    }

    private static void frame(BufferedImage img, int x0, int y0, int x1, int y1, int colour) {
        for (int x = x0; x <= x1; x++) {
            px(img, x, y0, colour);
            px(img, x, y1, colour);
        }
        for (int y = y0; y <= y1; y++) {
            px(img, x0, y, colour);
            px(img, x1, y, colour);
        }
    }

    private static void line(BufferedImage img, int x0, int y0, int x1, int y1, int colour) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            px(img, (int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), colour);
        }
    }

    private static void disc(BufferedImage img, double cx, double cy, double r, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) px(img, x, y, colour);
        }
    }

    private static void ring(BufferedImage img, double cx, double cy, double r, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
            if (d <= r && d > r - 1.0) px(img, x, y, colour);
        }
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

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = blank();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) out.setRGB(x, y, source.getRGB(x, y));
        return out;
    }

    private static void write(BufferedImage img, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "png", path.toFile());
    }

    private static void block(String name, BufferedImage img) throws IOException {
        write(img, BLOCK_TEX.resolve(name + ".png"));
    }

    private static void item(String name, Draw draw) throws IOException {
        BufferedImage img = blank();
        draw.on(img);
        write(img, ITEM_TEX.resolve(name + ".png"));
    }

    /** Riveted plate: a seam round the edge, rivets along it. */
    private static BufferedImage plate(int base, long seed) {
        BufferedImage img = noisy(base, 6, seed);
        frame(img, 0, 0, 15, 15, shade(base, -35));
        for (int i = 2; i < 15; i += 4) {
            px(img, i, 1, RIVET);
            px(img, i, 14, RIVET);
            px(img, 1, i, RIVET);
            px(img, 14, i, RIVET);
        }
        return img;
    }

    // ------------------------------------------------------------------ textures

    private static void textures() throws IOException {
        // The firebox: an iron box over a band of brick; its front a door with a grate, glowing when lit.
        BufferedImage firebox = plate(IRON_DARK, 1);
        fill(firebox, 1, 12, 14, 14, BRICK);
        for (int x = 1; x <= 14; x += 4) px(firebox, x, 13, shade(BRICK, -30));
        line(firebox, 1, 12, 14, 12, shade(BRICK, 25));
        block("firebox", firebox);
        block("firebox_top", plate(IRON_DARK, 2));
        for (boolean lit : new boolean[] {false, true}) {
            BufferedImage front = copy(firebox);
            fill(front, 3, 3, 12, 10, SOOT);
            frame(front, 2, 2, 13, 11, IRON_LIGHT);
            for (int x = 4; x <= 11; x += 2) line(front, x, 4, x, 9, lit ? (x % 4 == 0 ? FIRE : EMBER) : 0x2C2A28);
            if (lit) {
                for (int x = 3; x <= 12; x++) px(front, x, 10, x % 3 == 0 ? EMBER : FIRE);
            }
            fill(front, 12, 6, 13, 7, RIVET);
            block("firebox_front" + (lit ? "_on" : ""), front);
        }
        // The boiler: a drum of riveted plate, a seam round its middle, a gauge glass on one side.
        BufferedImage boiler = plate(IRON, 3);
        line(boiler, 0, 7, 15, 7, shade(IRON, -35));
        for (int x = 2; x < 15; x += 3) px(boiler, x, 6, RIVET);
        for (int x = 2; x < 15; x += 3) px(boiler, x, 8, RIVET);
        fill(boiler, 11, 3, 12, 12, 0x2A3A48);
        fill(boiler, 11, 8, 12, 12, 0x4A7AB0);
        frame(boiler, 10, 2, 13, 13, BRASS_DARK);
        block("boiler", boiler);
        BufferedImage boilerTop = plate(IRON, 4);
        disc(boilerTop, 8, 8, 4.5, shade(IRON, -15));
        ring(boilerTop, 8, 8, 4.5, IRON_DARK);
        for (int[] r : new int[][] {{8, 4}, {8, 11}, {4, 8}, {11, 8}}) px(boilerTop, r[0], r[1], RIVET);
        block("boiler_top", boilerTop);
        BufferedImage boilerBottom = plate(SOOT, 5);
        for (int gx = 0; gx < 3; gx++) for (int gy = 0; gy < 3; gy++) {
            int x = 3 + gx * 4, y = 3 + gy * 4;
            fill(boilerBottom, x, y, x + 1, y + 1, COPPER);
            px(boilerBottom, x + 1, y + 1, shade(COPPER, -60));
        }
        block("boiler_bottom", boilerBottom);
        // The engine: a frame painted green, the cylinder's end with its studs at the front, the
        // flywheel's rim on the sides.
        BufferedImage engine = noisy(PAINT, 5, 6);
        frame(engine, 0, 0, 15, 15, shade(PAINT, -30));
        line(engine, 0, 12, 15, 12, shade(PAINT, -25));
        block("steam_engine", engine);
        BufferedImage engineFront = copy(engine);
        disc(engineFront, 8, 7, 5.5, IRON_LIGHT);
        disc(engineFront, 8, 7, 4.0, IRON);
        for (int a = 0; a < 6; a++) {
            double angle = a * Math.PI / 3;
            px(engineFront, (int) Math.round(7.5 + 4.7 * Math.cos(angle)), (int) Math.round(6.5 + 4.7 * Math.sin(angle)), BRASS_LIGHT);
        }
        disc(engineFront, 8, 7, 1.2, IRON_DARK);
        block("steam_engine_front", engineFront);
        BufferedImage engineSide = copy(engine);
        ring(engineSide, 8, 7, 6.5, IRON_DARK);
        ring(engineSide, 8, 7, 5.5, IRON_LIGHT);
        for (int a = 0; a < 4; a++) {
            double angle = a * Math.PI / 2 + Math.PI / 4;
            line(engineSide, 8, 7, (int) Math.round(7.5 + 5 * Math.cos(angle)), (int) Math.round(6.5 + 5 * Math.sin(angle)), IRON);
        }
        disc(engineSide, 8, 7, 1.5, BRASS);
        block("steam_engine_side", engineSide);
        BufferedImage engineTop = copy(engine);
        fill(engineTop, 6, 2, 9, 13, IRON);
        line(engineTop, 6, 2, 6, 13, IRON_LIGHT);
        line(engineTop, 9, 2, 9, 13, IRON_DARK);
        disc(engineTop, 8, 4, 1.6, BRASS);
        block("steam_engine_top", engineTop);
        // Brass for the safety valve and the whistle.
        BufferedImage brass = noisy(BRASS, 8, 7);
        for (int y = 0; y < 16; y += 4) line(brass, 0, y, 15, y, BRASS_LIGHT);
        for (int y = 3; y < 16; y += 4) line(brass, 0, y, 15, y, BRASS_DARK);
        block("brass_fitting", brass);
        // The cylinder in hand: a barrel with flanges and the piston rod out of it.
        item("steam_cylinder", img -> {
            fill(img, 3, 4, 11, 11, IRON);
            line(img, 3, 4, 11, 4, IRON_LIGHT);
            line(img, 3, 11, 11, 11, IRON_DARK);
            fill(img, 2, 3, 3, 12, IRON_DARK);
            fill(img, 11, 3, 12, 12, IRON_DARK);
            for (int y = 4; y <= 11; y += 3) {
                px(img, 2, y, RIVET);
                px(img, 12, y, RIVET);
            }
            line(img, 13, 7, 15, 7, IRON_LIGHT);
            line(img, 13, 8, 15, 8, IRON_DARK);
            fill(img, 6, 1, 8, 3, BRASS);
            px(img, 6, 1, BRASS_LIGHT);
        });
    }

    // ------------------------------------------------------------------ models, states, definitions

    private static void models() throws IOException {
        // Firebox: like a furnace, front lit or dark.
        for (boolean lit : new boolean[] {false, true}) {
            String name = "firebox" + (lit ? "_on" : "");
            text(BLOCKS.resolve(name + ".json"), """
                    {
                      "parent": "minecraft:block/orientable",
                      "textures": {
                        "front": "hardwrought:block/firebox_front%s",
                        "side": "hardwrought:block/firebox",
                        "top": "hardwrought:block/firebox_top"
                      }
                    }
                    """.formatted(lit ? "_on" : ""));
        }
        facingState("firebox", "lit", "firebox_on");
        itemDefinition("firebox", "hardwrought:block/firebox");
        // Boiler: the drum, a manhole on top, the fire tubes' ends underneath.
        text(BLOCKS.resolve("boiler.json"), """
                {
                  "parent": "minecraft:block/cube_bottom_top",
                  "textures": {
                    "top": "hardwrought:block/boiler_top",
                    "bottom": "hardwrought:block/boiler_bottom",
                    "side": "hardwrought:block/boiler"
                  }
                }
                """);
        oneModel("boiler", "hardwrought:block/boiler");
        itemDefinition("boiler", "hardwrought:block/boiler");
        // Engine: front the cylinder's end, flywheels at the sides.
        text(BLOCKS.resolve("steam_engine.json"), """
                {
                  "parent": "minecraft:block/cube",
                  "textures": {
                    "particle": "hardwrought:block/steam_engine",
                    "north": "hardwrought:block/steam_engine_front",
                    "south": "hardwrought:block/steam_engine",
                    "east": "hardwrought:block/steam_engine_side",
                    "west": "hardwrought:block/steam_engine_side",
                    "up": "hardwrought:block/steam_engine_top",
                    "down": "hardwrought:block/steam_engine"
                  }
                }
                """);
        facingState("steam_engine", "running", "steam_engine");
        itemDefinition("steam_engine", "hardwrought:block/steam_engine");
        // Safety valve: a flange, the body, the spring's cap, and the lever with its weight.
        text(BLOCKS.resolve("safety_valve.json"), elements("hardwrought:block/brass_fitting",
                box(4, 0, 4, 12, 2, 12), box(6, 2, 6, 10, 9, 10), box(5, 9, 5, 11, 11, 11), box(7.5, 11, 7.5, 8.5, 14, 8.5),
                box(8, 12, 7.5, 15, 13, 8.5), box(13, 10.5, 7, 15, 12, 9)));
        oneModel("safety_valve", "hardwrought:block/safety_valve");
        itemDefinition("safety_valve", "hardwrought:block/safety_valve");
        // Whistle: a pipe stub, the bell, its cap, and the pull lever.
        text(BLOCKS.resolve("steam_whistle.json"), elements("hardwrought:block/brass_fitting",
                box(6.5, 0, 6.5, 9.5, 4, 9.5), box(5, 4, 5, 11, 12, 11), box(6, 12, 6, 10, 14, 10),
                box(7.5, 14, 7.5, 8.5, 15, 8.5), box(9, 3, 7.5, 14, 4, 8.5)));
        oneModel("steam_whistle", "hardwrought:block/steam_whistle");
        itemDefinition("steam_whistle", "hardwrought:block/steam_whistle");
        // Gas valve: iron line, with a red hand wheel on a stem that stands up while it is open.
        for (boolean open : new boolean[] {true, false}) {
            double lift = open ? 2 : 0;
            text(BLOCKS.resolve("gas_valve/wheel_" + (open ? "open" : "shut") + ".json"), """
                    {
                      "parent": "minecraft:block/block",
                      "textures": {
                        "stem": "minecraft:block/iron_block",
                        "wheel": "minecraft:block/red_concrete",
                        "particle": "minecraft:block/iron_block"
                      },
                      "elements": [
                    %s
                      ]
                    }
                    """.formatted(String.join(",\n", List.of(
                    element(7, 12.5, 7, 9, 13.5 + lift, 9, "#stem"),
                    element(3.5, 13 + lift, 3.5, 12.5, 14 + lift, 4.5, "#wheel"),
                    element(3.5, 13 + lift, 11.5, 12.5, 14 + lift, 12.5, "#wheel"),
                    element(3.5, 13 + lift, 4.5, 4.5, 14 + lift, 11.5, "#wheel"),
                    element(11.5, 13 + lift, 4.5, 12.5, 14 + lift, 11.5, "#wheel"),
                    element(4.5, 13.25 + lift, 7.5, 11.5, 13.75 + lift, 8.5, "#wheel"),
                    element(7.5, 13.25 + lift, 4.5, 8.5, 13.75 + lift, 11.5, "#wheel")))));
        }
        String line = Files.readString(ASSETS.resolve("blockstates/gas_line_iron.json"));
        int close = line.lastIndexOf(']');
        String wheels = """
                ,
                    {
                      "when": { "open": "true", "powered": "false" },
                      "apply": { "model": "hardwrought:block/gas_valve/wheel_open" }
                    },
                    {
                      "when": { "open": "false" },
                      "apply": { "model": "hardwrought:block/gas_valve/wheel_shut" }
                    },
                    {
                      "when": { "open": "true", "powered": "true" },
                      "apply": { "model": "hardwrought:block/gas_valve/wheel_shut" }
                    }
                  """;
        text(ASSETS.resolve("blockstates/gas_valve.json"), line.substring(0, close).stripTrailing() + wheels + "]" + line.substring(close + 1));
        text(ASSETS.resolve("items/gas_valve.json"), """
                {
                  "model": {
                    "type": "minecraft:composite",
                    "models": [
                      { "type": "minecraft:model", "model": "hardwrought:block/gas_line_iron/item" },
                      { "type": "minecraft:model", "model": "hardwrought:block/gas_valve/wheel_open" }
                    ]
                  }
                }
                """);
        // The cylinder in hand.
        text(ASSETS.resolve("models/item/steam_cylinder.json"), """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "hardwrought:item/steam_cylinder"
                  }
                }
                """);
        itemDefinition("steam_cylinder", "hardwrought:item/steam_cylinder");
    }

    private static String box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return element(x0, y0, z0, x1, y1, z1, "#all");
    }

    private static String element(double x0, double y0, double z0, double x1, double y1, double z1, String texture) {
        StringBuilder faces = new StringBuilder();
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            if (faces.length() > 0) faces.append(", ");
            faces.append('"').append(face).append("\": { \"texture\": \"").append(texture).append("\" }");
        }
        return "    { \"from\": [" + n(x0) + ", " + n(y0) + ", " + n(z0) + "], \"to\": [" + n(x1) + ", " + n(y1) + ", " + n(z1)
                + "], \"faces\": { " + faces + " } }";
    }

    private static String n(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }

    private static String elements(String texture, String... boxes) {
        return """
                {
                  "parent": "minecraft:block/block",
                  "textures": {
                    "all": "%s",
                    "particle": "%s"
                  },
                  "elements": [
                %s
                  ]
                }
                """.formatted(texture, texture, String.join(",\n", boxes));
    }

    /** One model for every state of the block. */
    private static void oneModel(String name, String model) throws IOException {
        text(ASSETS.resolve("blockstates/" + name + ".json"), """
                {
                  "multipart": [
                    {
                      "apply": {
                        "model": "%s"
                      }
                    }
                  ]
                }
                """.formatted(model));
    }

    /** Turned with its facing; with the flag set, the second model. */
    private static void facingState(String name, String flag, String flagged) throws IOException {
        String[] facings = {"north", "east", "south", "west"};
        int[] turns = {0, 90, 180, 270};
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (boolean on : new boolean[] {false, true}) {
                StringBuilder out = new StringBuilder("    \"facing=" + facings[i] + "," + flag + "=" + on
                        + "\": {\n      \"model\": \"hardwrought:block/" + (on ? flagged : name) + "\"");
                if (turns[i] != 0) out.append(",\n      \"y\": ").append(turns[i]);
                variants.add(out.append("\n    }").toString());
            }
        }
        text(ASSETS.resolve("blockstates/" + name + ".json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
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

    // ------------------------------------------------------------------ loot, tags, damage

    private static final List<String> BLOCK_NAMES = List.of("firebox", "boiler", "safety_valve", "steam_engine", "steam_whistle", "gas_valve");

    private static void loot() throws IOException {
        Path tables = DATA.resolve("loot_table/blocks");
        String self = Files.readString(tables.resolve("engine_casing.json"));
        for (String block : BLOCK_NAMES) text(tables.resolve(block + ".json"), self.replace("engine_casing", block));
    }

    private static void data() throws IOException {
        addToTag(RES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"), BLOCK_NAMES);
        text(DATA.resolve("damage_type/scalding.json"), """
                {
                  "message_id": "hardwrought.scalding",
                  "scaling": "never",
                  "exhaustion": 0.0,
                  "effects": "burning"
                }
                """);
        addToTag(RES.resolve("data/minecraft/tags/damage_type/bypasses_shield.json"), List.of("scalding"));
        addToTag(RES.resolve("data/minecraft/tags/damage_type/no_knockback.json"), List.of("scalding"));
    }

    // ------------------------------------------------------------------ recipes

    private static void recipes() throws IOException {
        shaped("firebox", 1, new String[] {"PPP", "P P", "BBB"}, "P", "hardwrought:iron_plate", "B", "hardwrought:refractory_brick");
        shaped("boiler", 1, new String[] {"PGP", "CBC", "PPP"}, "P", "hardwrought:iron_plate", "G", "minecraft:glass_pane",
                "C", "hardwrought:copper_plate", "B", "minecraft:bucket");
        shaped("safety_valve", 1, new String[] {"NRN", " B "}, "N", "minecraft:iron_nugget", "R", "hardwrought:iron_rod",
                "B", "hardwrought:bronze_plate");
        shaped("gas_valve", 1, new String[] {" R ", "LBL"}, "R", "hardwrought:iron_rod", "L", "hardwrought:gas_line_iron",
                "B", "hardwrought:bronze_plate");
        shaped("steam_cylinder", 1, new String[] {"PPP", "RBR", "PPP"}, "P", "hardwrought:iron_plate", "R", "hardwrought:iron_rod",
                "B", "hardwrought:bronze_plate");
        shaped("steam_engine", 1, new String[] {"CSW", "PPP"}, "C", "hardwrought:steam_cylinder", "S", "hardwrought:shaft",
                "W", "hardwrought:cogwheel", "P", "hardwrought:iron_plate");
        shaped("steam_whistle", 1, new String[] {"B", "B", "L"}, "B", "hardwrought:bronze_plate", "L", "hardwrought:gas_line_iron");
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
        text(DATA.resolve("recipe/" + name + ".json"), "{\n  \"type\": \"minecraft:crafting_shaped\",\n  \"category\": \"misc\",\n"
                + "  \"key\": {\n" + keys + "\n  },\n  \"pattern\": [\n" + rows + "\n  ],\n  \"result\": {\n    \"id\": \"hardwrought:"
                + name + "\"" + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n}\n");
    }

    // ------------------------------------------------------------------ files

    private static void addToTag(Path tag, List<String> names) throws IOException {
        String json = Files.exists(tag) ? Files.readString(tag) : "{\n  \"values\": [\n  ]\n}\n";
        TreeSet<String> missing = new TreeSet<>();
        for (String name : names) if (!json.contains("\"hardwrought:" + name + "\"")) missing.add("hardwrought:" + name);
        if (missing.isEmpty()) return;
        int close = json.lastIndexOf(']');
        String before = json.substring(0, close).stripTrailing();
        boolean empty = before.endsWith("[");
        StringBuilder added = new StringBuilder();
        for (String name : missing) {
            if (!empty || added.length() > 0) added.append(",");
            added.append("\n    \"").append(name).append('"');
        }
        text(tag, before + added + "\n  ]" + json.substring(close + 1));
    }

    private static void text(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
