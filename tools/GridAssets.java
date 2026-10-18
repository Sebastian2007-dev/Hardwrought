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
 * Writes the textures, models, blockstates, item definitions, loot tables and recipes of the grid
 * (docs/electricity-roadmap.md, stage E1): transformer, porcelain insulator, bushing, high-voltage mast,
 * knife switch, fuse box, breaker, coil winder, the Mk IV machines, insulated wire and the parts.
 *
 * <p>Run from the repository root with a JDK: {@code java tools/GridAssets.java}. It overwrites what it
 * writes and touches nothing else. Names and tags are kept by hand.
 */
public final class GridAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final String TEX = "hardwrought:block/hardwrought/";
    private static final String[] MK4 = {"basic_crusher", "electric_furnace", "sawmill", "plate_press"};

    public static void main(String[] args) throws IOException {
        textures();
        items();
        models();
        blockstates();
        loot();
        recipes();
        System.out.println("Grid assets written.");
    }

    // ------------------------------------------------------------------ textures

    private static void textures() throws IOException {
        blockTexture("porcelain", noisy(0xF2EFE6, 6, 11, (img, rng) -> {
            // A brown glaze ring round the middle, as on line insulators.
            for (int x = 0; x < 16; x++) for (int y = 7; y < 9; y++) img.setRGB(x, y, shade(0x7A4A2A, rng.nextInt(10) - 5));
        }));
        blockTexture("transformer_tank", noisy(0x6E7A6C, 7, 21, (img, rng) -> {
            border(img, 0x4A544A);
            for (int[] r : new int[][] {{2, 2}, {13, 2}, {2, 13}, {13, 13}}) img.setRGB(r[0], r[1], argb(0x9AA59A));
        }));
        blockTexture("transformer_tank_burnt", noisy(0x3A3C36, 9, 22, (img, rng) -> {
            border(img, 0x222420);
            for (int i = 0; i < 30; i++) img.setRGB(rng.nextInt(16), rng.nextInt(16), argb(0x161614));
        }));
        blockTexture("transformer_fins", noisy(0x6E7A6C, 5, 23, (img, rng) -> {
            for (int x = 0; x < 16; x += 2) for (int y = 0; y < 16; y++) img.setRGB(x, y, shade(0x4A544A, rng.nextInt(6) - 3));
        }));
        blockTexture("switchgear_side", noisy(0x7C8088, 6, 31, (img, rng) -> border(img, 0x50545A)));
        blockTexture("switchgear_top", noisy(0x868A92, 5, 32, (img, rng) -> {
            border(img, 0x50545A);
            for (int x = 3; x < 13; x++) img.setRGB(x, 8, argb(0x5C6066));
        }));
        // A knife switch: two copper blades on slate, down into their jaws when closed, up when open.
        for (boolean closed : new boolean[] {true, false}) {
            final boolean shut = closed;
            blockTexture("knife_switch_" + (closed ? "closed" : "open"), noisy(0x3C4048, 5, 41, (img, rng) -> {
                border(img, 0x50545A);
                for (int x : new int[] {5, 10}) {
                    fill(img, x - 1, 11, x + 1, 13, 0xF2EFE6);
                    if (shut) fill(img, x, 3, x, 12, 0xC87A3C);
                    else for (int i = 0; i < 7; i++) img.setRGB(x + i / 3, 9 - i, argb(0xC87A3C));
                }
                fill(img, 4, shut ? 3 : 2, 11, shut ? 3 : 2, 0x2A2A2A);
            }));
        }
        // A fuse box: the cartridge behind a little window; blown, the window is sooted.
        for (boolean closed : new boolean[] {true, false}) {
            final boolean whole = closed;
            blockTexture("fuse_box_" + (closed ? "closed" : "open"), noisy(0x7C8088, 6, 51, (img, rng) -> {
                border(img, 0x50545A);
                fill(img, 4, 4, 11, 11, 0x2E3034);
                fill(img, 5, 6, 10, 9, 0xF2EFE6);
                fill(img, 5, 6, 5, 9, 0xB0B4BA);
                fill(img, 10, 6, 10, 9, 0xB0B4BA);
                if (!whole) {
                    for (int i = 0; i < 14; i++) img.setRGB(5 + rng.nextInt(6), 5 + rng.nextInt(6), argb(0x141414));
                }
            }));
        }
        // A breaker: a black case and a white toggle, up for on.
        for (boolean closed : new boolean[] {true, false}) {
            final boolean on = closed;
            blockTexture("breaker_" + (closed ? "closed" : "open"), noisy(0x26282C, 4, 61, (img, rng) -> {
                border(img, 0x3A3C42);
                fill(img, 6, 4, 9, 11, 0x141518);
                fill(img, 6, on ? 4 : 8, 9, on ? 7 : 11, 0xE8E8E0);
                fill(img, 3, 13, 12, 13, on ? 0x3FA34D : 0xD03A2B);
            }));
        }
        blockTexture("coil_winder_front", noisy(0x5A5E64, 6, 71, (img, rng) -> {
            border(img, 0x3C4046);
            // A spool of wire, end on.
            for (int x = 3; x < 13; x++) for (int y = 3; y < 13; y++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d < 5.2) img.setRGB(x, y, d < 1.8 ? argb(0x2A2A2A) : shade(0x9A4A26, (int) (Math.sin(d * 3) * 18)));
            }
        }));
        blockTexture("coil_winder_top", noisy(0x5A5E64, 6, 72, (img, rng) -> {
            border(img, 0x3C4046);
            for (int y = 4; y < 12; y++) for (int x = 3; x < 13; x++) img.setRGB(x, y, shade(0x9A4A26, y % 2 == 0 ? -20 : 8));
        }));
        blockTexture("wire_rubber", noisy(0x24242A, 4, 81, null));
        blockTexture("wire_rubber_hot", noisy(0x24242A, 4, 82, (img, rng) -> {
            for (int i = 0; i < 40; i++) img.setRGB(rng.nextInt(16), rng.nextInt(16), argb(0xE0702A));
        }));
        // The fourth mark's band: the third's, turned from violet to brass.
        BufferedImage mk3 = ImageIO.read(ASSETS.resolve("textures/block/hardwrought/dynamo_iron_mk3.png").toFile());
        BufferedImage mk4 = copy(mk3);
        for (int x = 0; x < mk3.getWidth(); x++) for (int y = 0; y < mk3.getHeight(); y++) {
            Color c = new Color(mk3.getRGB(x, y), true);
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            if (hsb[1] > 0.25f) {
                int rgb = Color.HSBtoRGB(0.11f, Math.min(1f, hsb[1] * 1.1f), Math.min(1f, hsb[2] * 1.15f));
                mk4.setRGB(x, y, (c.getAlpha() << 24) | (rgb & 0xFFFFFF));
            }
        }
        write(mk4, ASSETS.resolve("textures/block/hardwrought/dynamo_iron_mk4.png"));

        // Items: most are an existing picture in another colour.
        Path items = ASSETS.resolve("textures/item");
        write(tint(read(items.resolve("steel_ingot.png")), 0x7E8EA8), items.resolve("electrical_steel.png"));
        write(tint(read(items.resolve("steel_plate.png")), 0x8494AE), items.resolve("electrical_steel_plate.png"));
        write(tint(read(items.resolve("copper_wire.png")), 0x8A2E1E), items.resolve("enamelled_wire.png"));
        write(tint(read(items.resolve("copper_wire.png")), 0x34343C), items.resolve("insulated_wire.png"));
        write(tint(read(items.resolve("fireclay.png")), 0xC8C2B4), items.resolve("porcelain_blank.png"));
        write(tint(read(items.resolve("fireclay.png")), 0xFFFCF4), items.resolve("porcelain.png"));
        BufferedImage core = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 2; x < 14; x++) for (int y = 2; y < 14; y++) {
            boolean ring = x < 5 || x > 10 || y < 5 || y > 10;
            if (ring) core.setRGB(x, y, shade(0x7E8EA8, (x + y) % 2 == 0 ? 10 : -14));
        }
        write(core, items.resolve("transformer_core.png"));
        BufferedImage winding = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 3; x < 13; x++) {
            fill(winding, x, 2, x, 3, 0x6A6A70);
            fill(winding, x, 12, x, 13, 0x6A6A70);
            for (int y = 4; y < 12; y++) winding.setRGB(x, y, shade(0x9A4A26, y % 2 == 0 ? -18 : 10));
        }
        write(winding, items.resolve("winding.png"));
        BufferedImage fuse = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 10; i++) {
            int x = 3 + i, y = 12 - i;
            int colour = i < 2 || i > 7 ? 0xB0B4BA : 0xF2EFE6;
            fill(fuse, x, y - 1, x + 1, y, colour);
        }
        write(fuse, items.resolve("fuse_cartridge.png"));
    }

    private interface Paint {
        void on(BufferedImage img, Random rng);
    }

    private static BufferedImage noisy(int base, int spread, long seed, Paint paint) {
        Random rng = new Random(seed);
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
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
        BufferedImage out = copy(source);
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

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < source.getWidth(); x++) for (int y = 0; y < source.getHeight(); y++) out.setRGB(x, y, source.getRGB(x, y));
        return out;
    }

    private static BufferedImage read(Path path) throws IOException {
        return ImageIO.read(path.toFile());
    }

    private static void blockTexture(String name, BufferedImage img) throws IOException {
        write(img, ASSETS.resolve("textures/block/hardwrought/" + name + ".png"));
    }

    private static void write(BufferedImage img, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "png", path.toFile());
    }

    // ------------------------------------------------------------------ item definitions

    private static void items() throws IOException {
        for (String item : List.of("electrical_steel", "electrical_steel_plate", "transformer_core", "enamelled_wire", "winding",
                "porcelain_blank", "porcelain", "fuse_cartridge", "insulated_wire")) {
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
        for (String block : List.of("transformer", "porcelain_insulator", "bushing", "coil_winder")) {
            itemDefinition(block, "hardwrought:block/" + block);
        }
        itemDefinition("high_voltage_mast", "hardwrought:block/high_voltage_mast_top");
        for (String gear : List.of("knife_switch", "fuse_box", "breaker")) itemDefinition(gear, "hardwrought:block/" + gear + "_closed");
        for (String machine : MK4) itemDefinition(machine + "_mk4", "hardwrought:block/" + machine + "_mk4");
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
        // The transformer: a finned tank on a plinth, a porcelain bushing on its high face (north).
        List<String> elements = new ArrayList<>();
        elements.add(box(1, 0, 1, 15, 2, 15, "#tank"));
        elements.add(box(2, 2, 3, 14, 13, 13, "#tank"));
        elements.add(box(1.5, 13, 2.5, 14.5, 14, 13.5, "#tank"));
        for (int z = 4; z <= 12; z += 2) {
            elements.add(box(14, 3, z, 15.5, 12, z + 1, "#fins"));
            elements.add(box(0.5, 3, z, 2, 12, z + 1, "#fins"));
        }
        elements.add(box(6.5, 5.5, 0.5, 9.5, 9.5, 3, "#porcelain"));
        elements.add(box(5.5, 6.5, 1.2, 10.5, 7.5, 2.2, "#porcelain"));
        elements.add(box(7.5, 7, 0, 8.5, 8, 0.5, "#copper"));
        model(blocks.resolve("transformer.json"), """
                "tank": "%stransformer_tank",
                "fins": "%stransformer_fins",
                "porcelain": "%sporcelain",
                "copper": "%swire_copper",
                "particle": "%stransformer_tank"
                """.formatted(TEX, TEX, TEX, TEX, TEX), elements);
        child(blocks.resolve("transformer_burnt.json"), "hardwrought:block/transformer", """
                "tank": "%stransformer_tank_burnt",
                "fins": "%stransformer_tank_burnt",
                "porcelain": "%sporcelain",
                "copper": "%swire_copper",
                "particle": "%stransformer_tank_burnt"
                """.formatted(TEX, TEX, TEX, TEX, TEX));

        // Switchgear: a box with its working on the front (north).
        String gear = """
                {
                  "parent": "minecraft:block/block",
                  "elements": [
                    {
                      "from": [1, 0, 1],
                      "to": [15, 15, 15],
                      "faces": {
                        "north": {"texture": "#front"},
                        "south": {"texture": "#side"},
                        "east": {"texture": "#side"},
                        "west": {"texture": "#side"},
                        "up": {"texture": "#top"},
                        "down": {"texture": "#side"}
                      }
                    }
                  ]
                }
                """;
        text(blocks.resolve("switchgear.json"), gear);
        for (String kind : List.of("knife_switch", "fuse_box", "breaker")) {
            for (String state : List.of("closed", "open")) {
                child(blocks.resolve(kind + "_" + state + ".json"), "hardwrought:block/switchgear", """
                        "front": "%s%s_%s",
                        "side": "%sswitchgear_side",
                        "top": "%sswitchgear_top",
                        "particle": "%sswitchgear_side"
                        """.formatted(TEX, kind, state, TEX, TEX, TEX));
            }
        }

        // Porcelain where glazed brick was: the insulator, the bushing and the mast's head.
        String porcelain = "\"ceramic\": \"%sporcelain\",\n\"iron\": \"%sdynamo_iron\",\n\"copper\": \"%swire_copper\",\n\"particle\": \"%sporcelain\"\n"
                .formatted(TEX, TEX, TEX, TEX);
        child(blocks.resolve("porcelain_insulator.json"), "hardwrought:block/insulator", porcelain);
        child(blocks.resolve("bushing.json"), "hardwrought:block/insulator", porcelain);
        for (String part : List.of("base", "pole", "top")) {
            String original = Files.readString(blocks.resolve("mast_" + part + ".json"));
            text(blocks.resolve("high_voltage_mast_" + part + ".json"),
                    original.replace(TEX + "insulator_ceramic", TEX + "porcelain"));
        }

        // The fourth marks are the third with a brass band; the coil winder is the lathe with a spool.
        for (String machine : MK4) {
            String original = Files.readString(blocks.resolve(machine + "_mk3.json"));
            text(blocks.resolve(machine + "_mk4.json"), original.replace("dynamo_iron_mk3", "dynamo_iron_mk4"));
        }
        String lathe = Files.readString(blocks.resolve("lathe.json"));
        text(blocks.resolve("coil_winder.json"), lathe.replace(TEX + "lathe_front", TEX + "coil_winder_front")
                .replace(TEX + "lathe_top", TEX + "coil_winder_top"));

        // The strand of insulated wire: a little thicker than bare wire, black.
        for (String hot : List.of("", "_hot")) {
            String texture = TEX + "wire_rubber" + hot;
            model(blocks.resolve("insulated_strand" + hot + ".json"), """
                    "wire": "%s",
                    "particle": "%s"
                    """.formatted(texture, texture), List.of(box(0, 7.4, 7.4, 16, 8.6, 8.6, "#wire")));
        }
    }

    private static String box(double x0, double y0, double z0, double x1, double y1, double z1, String texture) {
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
                    }""".formatted(n(x0), n(y0), n(z0), n(x1), n(y1), n(z1),
                texture, texture, texture, texture, texture, texture);
    }

    private static String n(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private static void model(Path path, String textures, List<String> elements) throws IOException {
        text(path, "{\n  \"parent\": \"minecraft:block/block\",\n  \"textures\": {\n" + indent(textures, 4)
                + "  },\n  \"elements\": [\n" + String.join(",\n", elements) + "\n  ]\n}\n");
    }

    private static void child(Path path, String parent, String textures) throws IOException {
        text(path, "{\n  \"parent\": \"" + parent + "\",\n  \"textures\": {\n" + indent(textures, 4) + "  }\n}\n");
    }

    private static String indent(String lines, int by) {
        StringBuilder out = new StringBuilder();
        for (String line : lines.strip().split("\n")) out.append(" ".repeat(by)).append(line.strip()).append('\n');
        return out.toString();
    }

    // ------------------------------------------------------------------ blockstates

    private static final String[] HORIZONTAL = {"north", "east", "south", "west"};

    private static void blockstates() throws IOException {
        Path states = ASSETS.resolve("blockstates");
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (boolean burnt : new boolean[] {false, true}) {
                variants.add(variant("burnt=" + burnt + ",facing=" + HORIZONTAL[i],
                        "hardwrought:block/transformer" + (burnt ? "_burnt" : ""), 0, i * 90));
            }
        }
        variants(states.resolve("transformer.json"), variants);

        for (String kind : List.of("knife_switch", "fuse_box", "breaker")) {
            variants.clear();
            for (int i = 0; i < 4; i++) {
                for (boolean closed : new boolean[] {false, true}) {
                    for (boolean powered : new boolean[] {false, true}) {
                        variants.add(variant("closed=" + closed + ",facing=" + HORIZONTAL[i] + ",powered=" + powered,
                                "hardwrought:block/" + kind + (closed ? "_closed" : "_open"), 0, i * 90));
                    }
                }
            }
            variants(states.resolve(kind + ".json"), variants);
        }

        String insulator = Files.readString(states.resolve("insulator.json"));
        text(states.resolve("porcelain_insulator.json"), insulator.replace("hardwrought:block/insulator", "hardwrought:block/porcelain_insulator"));
        String coil = Files.readString(states.resolve("copper_coil.json"));
        text(states.resolve("bushing.json"), coil.replace("hardwrought:block/copper_coil", "hardwrought:block/bushing"));
        String mast = Files.readString(states.resolve("mast.json"));
        text(states.resolve("high_voltage_mast.json"), mast.replace("hardwrought:block/mast_", "hardwrought:block/high_voltage_mast_"));
        for (String machine : MK4) {
            String original = Files.readString(states.resolve(machine + "_mk3.json"));
            text(states.resolve(machine + "_mk4.json"), original.replace(machine + "_mk3", machine + "_mk4"));
        }
        text(states.resolve("coil_winder.json"), Files.readString(states.resolve("lathe.json"))
                .replace("hardwrought:block/lathe", "hardwrought:block/coil_winder"));
    }

    private static String variant(String key, String model, int x, int y) {
        StringBuilder out = new StringBuilder("    \"" + key + "\": {\n      \"model\": \"" + model + "\"");
        if (x != 0) out.append(",\n      \"x\": ").append(x);
        if (y != 0) out.append(",\n      \"y\": ").append(y);
        return out.append("\n    }").toString();
    }

    private static void variants(Path path, List<String> variants) throws IOException {
        text(path, "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
    }

    // ------------------------------------------------------------------ loot

    private static void loot() throws IOException {
        Path tables = DATA.resolve("loot_table/blocks");
        String plain = Files.readString(tables.resolve("insulator.json"));
        List<String> blocks = new ArrayList<>(List.of("transformer", "porcelain_insulator", "bushing", "knife_switch",
                "fuse_box", "breaker", "coil_winder"));
        for (String machine : MK4) blocks.add(machine + "_mk4");
        for (String block : blocks) text(tables.resolve(block + ".json"), plain.replace("hardwrought:insulator", "hardwrought:" + block));
        text(tables.resolve("high_voltage_mast.json"), Files.readString(tables.resolve("mast.json"))
                .replace("hardwrought:mast", "hardwrought:high_voltage_mast"));
    }

    // ------------------------------------------------------------------ recipes

    private static void recipes() throws IOException {
        shaped("transformer_core", 1, new String[] {"PPP", "P P", "PPP"}, "P", "hardwrought:electrical_steel_plate");
        shapeless("enamelled_wire", 4, "hardwrought:copper_wire", "hardwrought:copper_wire", "hardwrought:copper_wire",
                "hardwrought:copper_wire", "minecraft:resin_clump");
        shapeless("porcelain_blank", 2, "hardwrought:fireclay", "minecraft:clay_ball", "minecraft:quartz");
        text(DATA.resolve("recipe/porcelain_from_smelting.json"), """
                {
                  "type": "minecraft:smelting",
                  "category": "misc",
                  "cookingtime": 200,
                  "experience": 0.2,
                  "ingredient": "hardwrought:porcelain_blank",
                  "result": {
                    "id": "hardwrought:porcelain"
                  }
                }
                """);
        shaped("porcelain_insulator", 2, new String[] {"W", "P", "P"}, "W", "hardwrought:copper_wire", "P", "hardwrought:porcelain");
        shaped("bushing", 1, new String[] {"R", "P", "P"}, "R", "hardwrought:copper_rod", "P", "hardwrought:porcelain");
        shapeless("high_voltage_mast", 1, "hardwrought:mast", "hardwrought:porcelain_insulator");
        shaped("transformer", 1, new String[] {"TWT", "TCT", "TBT"}, "T", "hardwrought:steel_plate",
                "W", "hardwrought:winding", "C", "hardwrought:transformer_core", "B", "hardwrought:bushing");
        shapeless("insulated_wire", 2, "hardwrought:copper_wire", "hardwrought:copper_wire", "hardwrought:rubber");
        shapeless("fuse_cartridge", 4, "hardwrought:porcelain", "hardwrought:copper_wire", "minecraft:glass_pane");
        shaped("knife_switch", 1, new String[] {"CLC", "PPP"}, "C", "hardwrought:copper_plate", "L", "minecraft:lever",
                "P", "hardwrought:porcelain");
        shaped("fuse_box", 1, new String[] {"III", "PFP", "III"}, "I", "hardwrought:iron_plate", "P", "hardwrought:porcelain",
                "F", "hardwrought:fuse_cartridge");
        shaped("breaker", 1, new String[] {"ILI", "SRS", "III"}, "I", "hardwrought:iron_plate", "L", "minecraft:lever",
                "S", "hardwrought:silver_plate", "R", "hardwrought:redstone_coil");
        shaped("coil_winder", 1, new String[] {"IMI", "RSR", "ICI"}, "I", "hardwrought:iron_plate", "M", "hardwrought:electric_motor",
                "R", "hardwrought:steel_rod", "S", "hardwrought:shaft", "C", "hardwrought:basic_circuit");
        for (String machine : MK4) {
            shaped(machine + "_mk4", 1, new String[] {"GEG", "WMW", "GPG"}, "G", "hardwrought:silicone_gasket",
                    "E", "hardwrought:elite_circuit", "W", "hardwrought:winding", "M", "hardwrought:" + machine + "_mk3",
                    "P", "hardwrought:electrical_steel_plate");
        }
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

    private static void shapeless(String name, int count, String... ingredients) throws IOException {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < ingredients.length; i++) {
            if (i > 0) list.append(",\n");
            list.append("    \"").append(ingredients[i]).append('"');
        }
        text(DATA.resolve("recipe/" + name + ".json"), "{\n  \"type\": \"minecraft:crafting_shapeless\",\n  \"category\": \"redstone\",\n"
                + "  \"ingredients\": [\n" + list + "\n  ],\n" + result(name, count) + "}\n");
    }

    private static String result(String name, int count) {
        return "  \"result\": {\n    \"id\": \"hardwrought:" + name + "\"" + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n";
    }

    private static void text(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
