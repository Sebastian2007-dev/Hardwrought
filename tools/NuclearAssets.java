import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/**
 * Writes everything of nuclear engineering that is not code (docs/nuclear.md): the textures of its blocks
 * and parts, drawn here pixel by pixel or tinted from what the mod already has; block and item models,
 * blockstates, item definitions; loot tables, crafting recipes and the tags the blocks need; zircaloy's
 * stock and molten metal.
 *
 * <p>Run from the repository root with a JDK: {@code java tools/NuclearAssets.java}. It overwrites what it
 * writes and touches nothing else, except that it adds its blocks to existing tags. Names are kept by hand.
 */
public final class NuclearAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final Path ITEM_TEX = ASSETS.resolve("textures/item");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block");
    private static final Path JAR = Path.of(System.getProperty("user.home"),
            ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar");

    private static final String[] CUBES = {"fluorite_ore", "deepslate_fluorite_ore", "reactor_vessel_wall", "fuel_channel",
            "turbine_casing", "turbine_rotor", "condenser", "centrifuge_casing", "corium", "radioactive_debris", "gas_port"};
    /** Blocks with a top of their own: name, and whether the bottom shares the top. */
    private static final String[] COLUMNS = {"control_rod_drive", "gas_centrifuge"};
    private static final String[][] CONTROLLERS = {{"reactor_controller", "reactor_vessel_wall"},
            {"turbine_controller", "turbine_casing"}, {"centrifuge_controller", "centrifuge_casing"}};
    private static final String[] PARTS = {"fluorite", "hydrofluoric_acid", "yellowcake", "uranium_dioxide", "green_pellet",
            "fuel_pellet", "depleted_uranium", "thorium_dioxide", "thorium_pellet", "zircon", "zirconium_sponge", "hafnium",
            "cladding_tube", "plenum_spring", "end_plug", "spacer_grid", "top_nozzle", "bottom_nozzle", "fuel_rod",
            "thorium_breeder_rod", "irradiated_thorium_rod", "control_rod", "turbine_blade", "impeller", "fission_chamber",
            "geiger_mueller_tube", "centrifuge_rotor", "fuel_assembly", "spent_fuel_assembly", "geiger_counter"};
    private static final int ZIRCALOY = 0xA7AEB4;

    public static void main(String[] args) throws IOException {
        blockTextures();
        itemTextures();
        zircaloy();
        models();
        loot();
        recipes();
        tags();
        grid();
        System.out.println("Nuclear assets written.");
    }

    // ------------------------------------------------------------------ painting

    private interface Draw {
        void on(BufferedImage img);
    }

    private static BufferedImage blank() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    /** A face of one colour, each pixel a little lighter or darker. */
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

    /** A line from one pixel to another, both included. */
    private static void line(BufferedImage img, int x0, int y0, int x1, int y1, int colour) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            px(img, (int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), colour);
        }
    }

    /** A rod lying from lower left to upper right, as items lie: a light edge above, a dark one below. */
    private static void rod(BufferedImage img, int from, int to, int light, int mid, int dark) {
        for (int i = from; i <= to; i++) {
            int x = i, y = 15 - i;
            px(img, x, y, mid);
            px(img, x + 1, y, light);
            px(img, x, y + 1, dark);
            px(img, x + 1, y + 1, mid);
        }
    }

    private static void disc(BufferedImage img, double cx, double cy, double r, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) px(img, x, y, colour);
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

    private static BufferedImage read(Path path) throws IOException {
        return ImageIO.read(path.toFile());
    }

    private static BufferedImage vanilla(String name) throws IOException {
        try (ZipFile jar = new ZipFile(JAR.toFile())) {
            var entry = jar.getEntry("assets/minecraft/textures/block/" + name + ".png");
            BufferedImage img = ImageIO.read(jar.getInputStream(entry));
            BufferedImage out = blank();
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) out.setRGB(x, y, img.getRGB(x, y));
            return out;
        }
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

    // ------------------------------------------------------------------ block textures

    private static final int STEEL = 0xA8AEB6, STEEL_DARK = 0x5E646C, CONCRETE = 0x8E8E86, ZIRC = 0xB8C0C6, COPPER = 0xC0703A,
            PAINT = 0x5E7468, ALUMINIUM = 0xD4D8DC, YELLOW = 0xE0C030, BLACK = 0x22252A;

    private static void blockTextures() throws IOException {
        // Fluorite: purple and green cubes in the rock.
        for (String rock : List.of("stone", "deepslate")) {
            BufferedImage img = vanilla(rock);
            int[][] spots = {{3, 3}, {10, 2}, {6, 8}, {12, 10}, {2, 12}, {9, 13}};
            int[] colours = {0x8E4FC8, 0xB27BE0, 0x5E2E96, 0x79C08E};
            for (int i = 0; i < spots.length; i++) {
                int x = spots[i][0], y = spots[i][1], c = colours[i % colours.length];
                fill(img, x, y, x + 1, y + 1, c);
                px(img, x, y, shade(c, 40));
                px(img, x + 1, y + 1, shade(c, -40));
            }
            block(rock.equals("stone") ? "fluorite_ore" : "deepslate_fluorite_ore", img);
        }
        // The vessel wall: concrete faced with a riveted steel plate.
        BufferedImage wall = noisy(CONCRETE, 6, 11);
        fill(wall, 1, 1, 14, 14, shade(STEEL, -10));
        frame(wall, 1, 1, 14, 14, STEEL_DARK);
        for (int[] r : new int[][] {{3, 3}, {12, 3}, {3, 12}, {12, 12}}) px(wall, r[0], r[1], 0xD8DCE0);
        line(wall, 1, 8, 14, 8, shade(STEEL, -30));
        block("reactor_vessel_wall", wall);
        // A fuel channel seen end on: a grid of zircaloy tubes.
        BufferedImage channel = noisy(0x3A4450, 4, 12);
        for (int gx = 0; gx < 3; gx++) for (int gy = 0; gy < 3; gy++) {
            int cx = 3 + gx * 5, cy = 3 + gy * 5;
            fill(channel, cx - 1, cy - 1, cx + 1, cy + 1, ZIRC);
            px(channel, cx, cy, 0x2A3038);
            px(channel, cx - 1, cy - 1, 0xE0E6EA);
        }
        frame(channel, 0, 0, 15, 15, STEEL_DARK);
        block("fuel_channel", channel);
        // The rod drive: a motor housing, its top a flange with the rod's latch in the middle.
        BufferedImage drive = noisy(PAINT, 5, 13);
        fill(drive, 4, 0, 11, 15, shade(STEEL, -20));
        line(drive, 4, 0, 4, 15, STEEL_DARK);
        line(drive, 11, 0, 11, 15, STEEL_DARK);
        for (int y = 2; y < 15; y += 3) line(drive, 5, y, 10, y, shade(STEEL, 15));
        fill(drive, 6, 6, 9, 8, YELLOW);
        block("control_rod_drive", drive);
        BufferedImage driveTop = noisy(STEEL, 5, 14);
        frame(driveTop, 0, 0, 15, 15, STEEL_DARK);
        disc(driveTop, 8, 8, 5, shade(STEEL, -25));
        disc(driveTop, 8, 8, 2.5, BLACK);
        px(driveTop, 7, 7, 0x606870);
        block("control_rod_drive_top", driveTop);
        // Turbine casing: painted green-grey panels, bolted.
        BufferedImage turbine = noisy(PAINT, 5, 15);
        frame(turbine, 0, 0, 15, 15, shade(PAINT, -30));
        line(turbine, 0, 7, 15, 7, shade(PAINT, -25));
        for (int[] r : new int[][] {{2, 2}, {13, 2}, {2, 13}, {13, 13}, {2, 9}, {13, 9}}) px(turbine, r[0], r[1], shade(PAINT, 40));
        block("turbine_casing", turbine);
        // The rotor: rows of blades seen from the side.
        BufferedImage rotor = noisy(0x404850, 4, 16);
        for (int row = 0; row < 4; row++) {
            for (int x = 0; x < 16; x += 2) line(rotor, x, row * 4, x + 1, row * 4 + 2, shade(STEEL, row * 6));
        }
        line(rotor, 0, 15, 15, 15, STEEL_DARK);
        block("turbine_rotor", rotor);
        // The condenser: a bundle of copper tube ends.
        BufferedImage condenser = noisy(0x50585E, 4, 17);
        for (int gx = 0; gx < 4; gx++) for (int gy = 0; gy < 4; gy++) {
            int x = 2 + gx * 4, y = 2 + gy * 4;
            fill(condenser, x, y, x + 1, y + 1, COPPER);
            px(condenser, x, y, shade(COPPER, 40));
            px(condenser, x + 1, y + 1, shade(COPPER, -50));
        }
        frame(condenser, 0, 0, 15, 15, STEEL_DARK);
        block("condenser", condenser);
        // Centrifuge casing: white panels of a clean hall.
        BufferedImage hall = noisy(ALUMINIUM, 3, 18);
        frame(hall, 0, 0, 15, 15, 0x9AA0A6);
        line(hall, 7, 0, 7, 15, 0xB0B6BC);
        fill(hall, 10, 11, 13, 13, 0x3C7FD0);
        block("centrifuge_casing", hall);
        // A gas centrifuge: a tall silver cylinder; its top a disc.
        BufferedImage centrifuge = noisy(0x6A7076, 3, 19);
        for (int x = 3; x <= 12; x++) {
            int c = shade(0xC8CED4, (int) (40 * Math.cos((x - 6.5) / 4.0)) - 30);
            line(centrifuge, x, 0, x, 15, c);
        }
        line(centrifuge, 3, 0, 3, 15, STEEL_DARK);
        line(centrifuge, 12, 0, 12, 15, STEEL_DARK);
        line(centrifuge, 3, 1, 12, 1, 0x3C7FD0);
        line(centrifuge, 3, 14, 12, 14, 0x3C7FD0);
        block("gas_centrifuge", centrifuge);
        BufferedImage centrifugeTop = noisy(0x6A7076, 3, 20);
        disc(centrifugeTop, 8, 8, 5, 0xC8CED4);
        disc(centrifugeTop, 8, 8, 2, STEEL_DARK);
        block("gas_centrifuge_top", centrifugeTop);
        // Corium: black glassy rock, cracked through to an orange glow.
        BufferedImage corium = noisy(0x2A2622, 8, 21);
        int[][] cracks = {{1, 3, 7, 6}, {7, 6, 14, 4}, {5, 10, 11, 14}, {7, 6, 6, 11}, {11, 14, 15, 12}};
        for (int[] c : cracks) line(corium, c[0], c[1], c[2], c[3], 0xF07818);
        for (int[] c : cracks) px(corium, c[2], c[3], 0xFFD040);
        block("corium", corium);
        // Debris: grey rubble flecked with the green-yellow of uranium salts.
        BufferedImage debris = vanilla("gravel");
        Random rng = new Random(22);
        for (int i = 0; i < 9; i++) px(debris, rng.nextInt(16), rng.nextInt(16), i % 3 == 0 ? 0xB8E040 : 0x8FB030);
        block("radioactive_debris", debris);
        // The gas port: the fluid port in the yellow of gas piping.
        block("gas_port", tint(read(BLOCK_TEX.resolve("fluid_port.png")), 0x9C8A3C));
        // The controllers: their casing with a desk of lamps and a screen; lit when the machine runs.
        for (String[] controller : CONTROLLERS) {
            for (boolean on : new boolean[] {false, true}) {
                BufferedImage front = read(BLOCK_TEX.resolve(controller[1] + ".png"));
                fill(front, 2, 2, 13, 9, 0x1C2024);
                frame(front, 1, 1, 14, 10, STEEL_DARK);
                if (on) {
                    // A trace across the screen, and the lamps green.
                    int[] trace = {7, 6, 6, 5, 6, 4, 5, 5, 4, 4, 3, 4};
                    for (int x = 0; x < trace.length; x++) px(front, 2 + x, trace[x], 0x60F080);
                }
                for (int i = 0; i < 4; i++) {
                    int c = on ? (i == 3 ? 0xF0C040 : 0x40E060) : 0x3A3A3A;
                    fill(front, 2 + i * 3, 12, 3 + i * 3, 13, c);
                }
                block(controller[0] + (on ? "_on" : ""), front);
            }
        }
    }

    // ------------------------------------------------------------------ item textures

    private static void itemTextures() throws IOException {
        BufferedImage powder = read(ITEM_TEX.resolve("uranium_powder.png"));
        item("fluorite", img -> {
            int[][] cubes = {{2, 7, 0x8E4FC8}, {7, 3, 0xB27BE0}, {8, 9, 0x79C08E}};
            for (int[] c : cubes) {
                fill(img, c[0], c[1], c[0] + 5, c[1] + 5, c[2]);
                line(img, c[0], c[1], c[0] + 5, c[1], shade(c[2], 45));
                line(img, c[0], c[1], c[0], c[1] + 5, shade(c[2], 30));
                line(img, c[0], c[1] + 5, c[0] + 5, c[1] + 5, shade(c[2], -50));
                line(img, c[0] + 5, c[1], c[0] + 5, c[1] + 5, shade(c[2], -40));
            }
        });
        write(tint(read(ITEM_TEX.resolve("battery_acid.png")), 0xD8E8E0), ITEM_TEX.resolve("hydrofluoric_acid.png"));
        write(tint(powder, 0xE8C838), ITEM_TEX.resolve("yellowcake.png"));
        write(tint(powder, 0x403C38), ITEM_TEX.resolve("uranium_dioxide.png"));
        write(tint(powder, 0xECE8DC), ITEM_TEX.resolve("thorium_dioxide.png"));
        pellets("green_pellet", 0x7A7E70);
        pellets("fuel_pellet", 0x34383C);
        pellets("thorium_pellet", 0xD8D4C8);
        write(tint(read(ITEM_TEX.resolve("uranium_ingot.png")), 0x6E7276), ITEM_TEX.resolve("depleted_uranium.png"));
        write(tint(read(ITEM_TEX.resolve("uranium_ingot.png")), 0xC6CDD4), ITEM_TEX.resolve("hafnium.png"));
        item("zircon", img -> {
            int[][] grains = {{3, 9}, {7, 5}, {10, 10}, {5, 12}, {11, 4}, {8, 12}};
            for (int[] g : grains) {
                fill(img, g[0], g[1], g[0] + 1, g[1] + 2, 0x9C4A2C);
                px(img, g[0], g[1], 0xD87A48);
                px(img, g[0] + 1, g[1] + 2, 0x5C2A18);
            }
        });
        item("zirconium_sponge", img -> {
            disc(img, 8, 8.5, 6, 0x8A9096);
            Random rng = new Random(31);
            for (int i = 0; i < 18; i++) {
                int x = 3 + rng.nextInt(10), y = 3 + rng.nextInt(11);
                if ((img.getRGB(x, y) >>> 24) != 0) px(img, x, y, rng.nextBoolean() ? 0x4A5056 : 0xC0C6CC);
            }
        });
        item("cladding_tube", img -> rod(img, 1, 13, 0xE6ECF0, ZIRCALOY, 0x6A7278));
        item("plenum_spring", img -> {
            for (int i = 0; i < 6; i++) {
                line(img, 3 + i * 2, 4, 4 + i * 2, 12, i % 2 == 0 ? 0xC8D0D8 : 0x8A9298);
            }
            line(img, 3, 4, 14, 4, 0x6A7278);
            line(img, 3, 12, 14, 12, 0x6A7278);
        });
        item("end_plug", img -> {
            fill(img, 5, 4, 10, 11, ZIRCALOY);
            fill(img, 6, 2, 9, 3, 0xD0D6DA);
            line(img, 5, 4, 5, 11, 0xE6ECF0);
            line(img, 10, 4, 10, 11, 0x6A7278);
            line(img, 5, 11, 10, 11, 0x5A6268);
        });
        item("spacer_grid", img -> {
            frame(img, 1, 1, 14, 14, 0x8A9298);
            for (int i = 1; i < 4; i++) {
                line(img, 1 + i * 13 / 4, 1, 1 + i * 13 / 4, 14, ZIRCALOY);
                line(img, 1, 1 + i * 13 / 4, 14, 1 + i * 13 / 4, ZIRCALOY);
            }
            frame(img, 0, 0, 15, 15, 0x5A6268);
        });
        item("top_nozzle", img -> {
            fill(img, 2, 5, 13, 11, 0xB4BAC0);
            fill(img, 4, 7, 11, 9, 0x50565C);
            frame(img, 2, 5, 13, 11, 0x6A7076);
            fill(img, 3, 2, 4, 4, 0x9AA0A6);
            fill(img, 11, 2, 12, 4, 0x9AA0A6);
        });
        item("bottom_nozzle", img -> {
            fill(img, 2, 4, 13, 10, 0xB4BAC0);
            for (int x = 4; x <= 11; x += 2) line(img, x, 6, x, 8, 0x50565C);
            frame(img, 2, 4, 13, 10, 0x6A7076);
            fill(img, 3, 11, 4, 13, 0x9AA0A6);
            fill(img, 11, 11, 12, 13, 0x9AA0A6);
        });
        item("fuel_rod", img -> {
            rod(img, 1, 13, 0xE6ECF0, ZIRCALOY, 0x6A7278);
            for (int i = 3; i <= 11; i += 2) px(img, i + 1, 15 - i, 0x5A6066);
        });
        item("thorium_breeder_rod", img -> {
            rod(img, 1, 13, 0xF4F2EC, 0xD0CCBE, 0x7A7468);
            for (int i = 3; i <= 11; i += 2) px(img, i + 1, 15 - i, 0x9A9488);
        });
        item("irradiated_thorium_rod", img -> {
            rod(img, 1, 13, 0x9FE8E0, 0x5A8C88, 0x2E4A48);
            for (int i = 2; i <= 12; i += 3) px(img, i + 1, 15 - i, 0xB8FFF4);
        });
        item("control_rod", img -> {
            rod(img, 1, 13, 0x8A9096, 0x4A5056, 0x24282C);
            fill(img, 12, 1, 14, 3, 0xC8CED4);
        });
        item("turbine_blade", img -> {
            for (int i = 0; i < 11; i++) {
                int x = 3 + i, y = 13 - i;
                int width = 1 + (i < 8 ? i / 3 : (10 - i));
                for (int w = 0; w < width; w++) px(img, x - w, y - w / 2, w == 0 ? 0xE0E6EA : 0xA8AEB4);
                px(img, x + 1, y + 1, 0x6A7076);
            }
            fill(img, 2, 12, 4, 14, 0x6A7076);
        });
        item("impeller", img -> {
            disc(img, 8, 8, 6.5, 0x9AA0A6);
            for (int a = 0; a < 6; a++) {
                double angle = a * Math.PI / 3;
                line(img, 8, 8, 8 + (int) Math.round(Math.cos(angle) * 6), 8 + (int) Math.round(Math.sin(angle) * 6), 0xD8DEE4);
            }
            disc(img, 8, 8, 2, 0x50565C);
        });
        item("fission_chamber", img -> {
            fill(img, 5, 2, 10, 13, 0xC0C6CC);
            fill(img, 5, 6, 10, 8, YELLOW);
            line(img, 5, 2, 5, 13, 0xE6ECF0);
            line(img, 10, 2, 10, 13, 0x6A7076);
            line(img, 7, 14, 7, 15, COPPER);
            line(img, 8, 14, 8, 15, shade(COPPER, -40));
        });
        item("geiger_mueller_tube", img -> {
            fill(img, 4, 3, 11, 12, 0xCFE6EE);
            frame(img, 4, 3, 11, 12, 0x8FB4C2);
            line(img, 7, 4, 7, 11, 0x8C8F99);
            line(img, 8, 4, 8, 11, 0x5A5E66);
            fill(img, 5, 13, 10, 14, 0x50565C);
            line(img, 7, 15, 8, 15, COPPER);
        });
        item("centrifuge_rotor", img -> {
            for (int x = 5; x <= 10; x++) line(img, x, 1, x, 14, shade(0xC8CED4, (int) (30 * Math.cos((x - 7.5) / 2.5)) - 20));
            line(img, 5, 1, 10, 1, 0x3C7FD0);
            line(img, 5, 14, 10, 14, 0x3C7FD0);
            line(img, 4, 1, 4, 14, 0x6A7076);
            line(img, 11, 1, 11, 14, 0x6A7076);
        });
        assembly("fuel_assembly", ZIRCALOY, 0xE6ECF0, 0x5A6268, false);
        assembly("spent_fuel_assembly", 0x5E6A70, 0x8890A0, 0x283038, true);
        item("geiger_counter", img -> {
            fill(img, 2, 5, 11, 14, YELLOW);
            frame(img, 2, 5, 11, 14, 0x7A6410);
            fill(img, 4, 7, 9, 10, 0xF0EEDC);
            line(img, 5, 10, 8, 7, 0x202020);
            fill(img, 4, 12, 5, 13, 0x303030);
            fill(img, 8, 12, 9, 13, 0xC03030);
            line(img, 11, 6, 13, 4, 0x303030);
            fill(img, 12, 1, 14, 3, 0x8A9298);
        });
    }

    /** Three pellets, as a stack seen at an angle. */
    private static void pellets(String name, int colour) throws IOException {
        item(name, img -> {
            int[][] at = {{3, 8}, {7, 5}, {9, 10}};
            for (int[] p : at) {
                fill(img, p[0], p[1], p[0] + 3, p[1] + 3, colour);
                line(img, p[0], p[1], p[0] + 3, p[1], shade(colour, 45));
                line(img, p[0], p[1] + 3, p[0] + 3, p[1] + 3, shade(colour, -45));
                px(img, p[0] + 3, p[1] + 1, shade(colour, -25));
            }
        });
    }

    /** A fuel assembly upright: nozzles at the ends, a spacer grid across, rods between. */
    private static void assembly(String name, int rodColour, int light, int dark, boolean glow) throws IOException {
        item(name, img -> {
            for (int x = 4; x <= 11; x++) line(img, x, 2, x, 13, x % 2 == 0 ? rodColour : shade(rodColour, -30));
            line(img, 4, 2, 4, 13, light);
            line(img, 11, 2, 11, 13, dark);
            fill(img, 3, 0, 12, 1, 0x9AA0A6);
            fill(img, 3, 14, 12, 15, 0x9AA0A6);
            line(img, 3, 7, 12, 7, dark);
            if (glow) {
                // The blue of Cherenkov light it gives off under water.
                line(img, 2, 2, 2, 13, 0x4A8CFF);
                line(img, 13, 2, 13, 13, 0x4A8CFF);
            }
        });
    }

    // ------------------------------------------------------------------ zircaloy

    private static void zircaloy() throws IOException {
        for (String form : List.of("ingot", "rod", "plate")) {
            write(tint(read(ITEM_TEX.resolve("nichrome_" + form + ".png")), ZIRCALOY), ITEM_TEX.resolve("zircaloy_" + form + ".png"));
            text(ASSETS.resolve("items/zircaloy_" + form + ".json"),
                    Files.readString(ASSETS.resolve("items/nichrome_" + form + ".json")).replace("nichrome", "zircaloy"));
            text(ASSETS.resolve("models/item/zircaloy_" + form + ".json"),
                    Files.readString(ASSETS.resolve("models/item/nichrome_" + form + ".json")).replace("nichrome", "zircaloy"));
            Path glow = ITEM_TEX.resolve("glow/hardwrought_nichrome_" + form + ".png");
            if (Files.exists(glow)) {
                write(tint(read(glow), ZIRCALOY), ITEM_TEX.resolve("glow/hardwrought_zircaloy_" + form + ".png"));
                text(ASSETS.resolve("models/item/glow/hardwrought_zircaloy_" + form + ".json"),
                        Files.readString(ASSETS.resolve("models/item/glow/hardwrought_nichrome_" + form + ".json")).replace("nichrome", "zircaloy"));
            }
        }
        Path molten = BLOCK_TEX.resolve("molten");
        write(tint(read(molten.resolve("nickel.png")), ZIRCALOY), molten.resolve("zircaloy.png"));
        Files.copy(molten.resolve("nickel.png.mcmeta"), molten.resolve("zircaloy.png.mcmeta"), StandardCopyOption.REPLACE_EXISTING);
        text(ASSETS.resolve("models/block/molten/zircaloy.json"), Files.readString(ASSETS.resolve("models/block/molten/nickel.json"))
                .replace("molten/nickel", "molten/zircaloy"));
        text(DATA.resolve("hardwrought/materials/zircaloy.json"), """
                {
                  "tier": 5,
                  "density_kg_m3": 6550,
                  "melting_point_c": 1850
                }
                """);
    }

    // ------------------------------------------------------------------ models, states, definitions

    private static void models() throws IOException {
        Path blocks = ASSETS.resolve("models/block");
        for (String cube : CUBES) {
            text(blocks.resolve(cube + ".json"), """
                    {
                      "parent": "minecraft:block/cube_all",
                      "textures": {
                        "all": "hardwrought:block/%s"
                      }
                    }
                    """.formatted(cube));
            plainState(cube);
            itemDefinition(cube, "hardwrought:block/" + cube);
        }
        for (String column : COLUMNS) {
            text(blocks.resolve(column + ".json"), """
                    {
                      "parent": "minecraft:block/cube_column",
                      "textures": {
                        "end": "hardwrought:block/%s_top",
                        "side": "hardwrought:block/%s"
                      }
                    }
                    """.formatted(column, column));
            plainState(column);
            itemDefinition(column, "hardwrought:block/" + column);
        }
        for (String[] controller : CONTROLLERS) {
            for (boolean on : new boolean[] {false, true}) {
                String name = controller[0] + (on ? "_on" : "");
                text(blocks.resolve(name + ".json"), """
                        {
                          "parent": "minecraft:block/orientable",
                          "textures": {
                            "front": "hardwrought:block/%s",
                            "side": "hardwrought:block/%s",
                            "top": "hardwrought:block/%s"
                          }
                        }
                        """.formatted(name, controller[1], controller[1]));
            }
            controllerState(controller[0]);
            itemDefinition(controller[0], "hardwrought:block/" + controller[0]);
        }
        for (String part : PARTS) {
            text(ASSETS.resolve("models/item/" + part + ".json"), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "hardwrought:item/%s"
                      }
                    }
                    """.formatted(part));
            itemDefinition(part, "hardwrought:item/" + part);
        }
    }

    private static void plainState(String name) throws IOException {
        text(ASSETS.resolve("blockstates/" + name + ".json"), """
                {
                  "variants": {
                    "": {
                      "model": "hardwrought:block/%s"
                    }
                  }
                }
                """.formatted(name));
    }

    /** As every controller: facing, formed and running; lit while it runs. */
    private static void controllerState(String name) throws IOException {
        String[] facings = {"north", "east", "south", "west"};
        int[] turns = {0, 90, 180, 270};
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (boolean formed : new boolean[] {false, true}) {
                for (boolean running : new boolean[] {false, true}) {
                    StringBuilder out = new StringBuilder("    \"facing=" + facings[i] + ",formed=" + formed + ",running=" + running
                            + "\": {\n      \"model\": \"hardwrought:block/" + name + (running ? "_on" : "") + "\"");
                    if (turns[i] != 0) out.append(",\n      \"y\": ").append(turns[i]);
                    variants.add(out.append("\n    }").toString());
                }
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

    // ------------------------------------------------------------------ loot

    private static void loot() throws IOException {
        Path tables = DATA.resolve("loot_table/blocks");
        String self = Files.readString(tables.resolve("engine_casing.json"));
        List<String> blocks = new ArrayList<>(List.of(CUBES));
        blocks.addAll(List.of(COLUMNS));
        for (String[] controller : CONTROLLERS) blocks.add(controller[0]);
        blocks.removeAll(List.of("fluorite_ore", "deepslate_fluorite_ore", "gas_port"));
        for (String block : blocks) text(tables.resolve(block + ".json"), self.replace("engine_casing", block));
        text(tables.resolve("gas_port.json"), self.replace("engine_casing", "gas_port"));
        String ore = Files.readString(tables.resolve("uranium_ore.json"));
        text(tables.resolve("fluorite_ore.json"), ore.replace("hardwrought:uranium_ore", "hardwrought:fluorite_ore")
                .replace("hardwrought:raw_uranium", "hardwrought:fluorite"));
        text(tables.resolve("deepslate_fluorite_ore.json"), ore.replace("hardwrought:uranium_ore", "hardwrought:deepslate_fluorite_ore")
                .replace("hardwrought:raw_uranium", "hardwrought:fluorite"));
    }

    // ------------------------------------------------------------------ recipes

    private static void recipes() throws IOException {
        // The small parts.
        shaped("plenum_spring", 4, new String[] {"R", "R"}, "R", "hardwrought:stainless_steel_rod");
        shaped("end_plug", 8, new String[] {"RR"}, "R", "hardwrought:zircaloy_rod");
        shaped("spacer_grid", 2, new String[] {"P P", " N ", "P P"}, "P", "hardwrought:zircaloy_plate", "N", "hardwrought:nichrome_wire");
        shaped("top_nozzle", 1, new String[] {"PSP", "P P"}, "P", "hardwrought:stainless_steel_plate", "S", "hardwrought:plenum_spring");
        shaped("bottom_nozzle", 1, new String[] {"P P", "PMP"}, "P", "hardwrought:stainless_steel_plate", "M", "hardwrought:nichrome_mesh");
        shapeless("fuel_rod", 1, "hardwrought:cladding_tube", "hardwrought:fuel_pellet", "hardwrought:fuel_pellet",
                "hardwrought:fuel_pellet", "hardwrought:fuel_pellet", "hardwrought:plenum_spring", "hardwrought:end_plug",
                "hardwrought:end_plug");
        shapeless("thorium_breeder_rod", 1, "hardwrought:cladding_tube", "hardwrought:thorium_pellet", "hardwrought:thorium_pellet",
                "hardwrought:thorium_pellet", "hardwrought:thorium_pellet", "hardwrought:plenum_spring", "hardwrought:end_plug",
                "hardwrought:end_plug");
        shaped("fuel_assembly", 1, new String[] {"RTR", "RGR", "RBR"}, "R", "hardwrought:fuel_rod", "T", "hardwrought:top_nozzle",
                "G", "hardwrought:spacer_grid", "B", "hardwrought:bottom_nozzle");
        shaped("control_rod", 1, new String[] {"PHP", "PHP", "PHP"}, "P", "hardwrought:stainless_steel_plate", "H", "hardwrought:hafnium");
        // Silver with a little indium and cadmium is the other absorber: a rod of it for those without hafnium.
        shaped("control_rod_from_silver", "control_rod", 1, new String[] {"PSP", "PSP", "PSP"},
                "P", "hardwrought:stainless_steel_plate", "S", "hardwrought:silver_ingot");
        shaped("turbine_blade", 4, new String[] {"  P", " P ", "P  "}, "P", "hardwrought:stainless_steel_plate");
        shaped("impeller", 1, new String[] {"P P", " R ", "P P"}, "P", "hardwrought:stainless_steel_plate", "R", "hardwrought:stainless_steel_rod");
        shaped("fission_chamber", 1, new String[] {" G ", "UCU", " W "}, "G", "hardwrought:glass_tube", "U", "hardwrought:uranium_dioxide",
                "C", "hardwrought:capacitor", "W", "hardwrought:copper_wire");
        shaped("geiger_mueller_tube", 1, new String[] {"G", "F", "W"}, "G", "hardwrought:glass_tube", "F", "hardwrought:tungsten_filament",
                "W", "hardwrought:copper_wire");
        shaped("geiger_counter", 1, new String[] {" T ", "CHC", " B "}, "T", "hardwrought:geiger_mueller_tube", "C", "hardwrought:capacitor",
                "H", "hardwrought:device_housing", "B", "hardwrought:basic_circuit");
        shaped("centrifuge_rotor", 1, new String[] {" B ", "STS", " B "}, "B", "hardwrought:bearing", "S", "hardwrought:stainless_steel_rod",
                "T", "hardwrought:tube_section");
        // The blocks.
        shaped("reactor_vessel_wall", 4, new String[] {"PCP", "CLC", "PCP"}, "P", "hardwrought:steel_plate", "C", "minecraft:gray_concrete",
                "L", "hardwrought:lead_ingot");
        shaped("fuel_channel", 2, new String[] {"P P", "PGP", "P P"}, "P", "hardwrought:zircaloy_plate", "G", "hardwrought:spacer_grid");
        shaped("control_rod_drive", 1, new String[] {" M ", "GRG", "PCP"}, "M", "hardwrought:electric_motor", "G", "hardwrought:reduction_gear",
                "R", "hardwrought:control_rod", "P", "hardwrought:steel_plate", "C", "hardwrought:redstone_coil");
        shaped("reactor_controller", 1, new String[] {"FEF", "MIM", "WGW"}, "F", "hardwrought:fission_chamber", "E", "hardwrought:elite_circuit",
                "M", "hardwrought:electric_motor", "I", "hardwrought:impeller", "W", "hardwrought:reactor_vessel_wall",
                "G", "hardwrought:pressure_gauge");
        shaped("turbine_casing", 4, new String[] {"SPS", "P P", "SPS"}, "S", "hardwrought:stainless_steel_plate", "P", "hardwrought:steel_plate");
        shaped("turbine_rotor", 1, new String[] {"BBB", "QSQ", "BBB"}, "B", "hardwrought:turbine_blade", "Q", "hardwrought:bearing",
                "S", "hardwrought:shaft");
        shaped("condenser", 1, new String[] {"TTT", "PCP", "TTT"}, "T", "hardwrought:tube_section", "P", "hardwrought:copper_plate",
                "C", "hardwrought:cooling_coil");
        shaped("turbine_controller", 1, new String[] {"GCG", "SRS", "WWW"}, "G", "hardwrought:pressure_gauge", "C", "hardwrought:advanced_circuit",
                "S", "hardwrought:stator", "R", "hardwrought:rotor", "W", "hardwrought:turbine_casing");
        shaped("centrifuge_casing", 4, new String[] {"PAP", "A A", "PAP"}, "P", "hardwrought:steel_plate", "A", "hardwrought:aluminum_plate");
        shaped("gas_centrifuge", 1, new String[] {" R ", "TMT", " C "}, "R", "hardwrought:centrifuge_rotor", "T", "hardwrought:tube_section",
                "M", "hardwrought:electric_motor", "C", "hardwrought:motor_controller");
        shaped("centrifuge_controller", 1, new String[] {"GCG", "MXM", "WWW"}, "G", "hardwrought:pressure_gauge", "C", "hardwrought:advanced_circuit",
                "M", "hardwrought:microchip", "X", "hardwrought:compressor", "W", "hardwrought:centrifuge_casing");
        shaped("gas_port", 1, new String[] {" L ", "LCL", " L "}, "L", "hardwrought:gas_line_iron", "C", "hardwrought:engine_casing");
    }

    private static void shaped(String name, int count, String[] pattern, String... key) throws IOException {
        shaped(name, name, count, pattern, key);
    }

    private static void shaped(String file, String name, int count, String[] pattern, String... key) throws IOException {
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
        text(DATA.resolve("recipe/" + file + ".json"), "{\n  \"type\": \"minecraft:crafting_shaped\",\n  \"category\": \"misc\",\n"
                + "  \"key\": {\n" + keys + "\n  },\n  \"pattern\": [\n" + rows + "\n  ],\n" + result(name, count) + "}\n");
    }

    private static void shapeless(String name, int count, String... ingredients) throws IOException {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < ingredients.length; i++) {
            if (i > 0) list.append(",\n");
            list.append("    \"").append(ingredients[i]).append('"');
        }
        text(DATA.resolve("recipe/" + name + ".json"), "{\n  \"type\": \"minecraft:crafting_shapeless\",\n  \"category\": \"misc\",\n"
                + "  \"ingredients\": [\n" + list + "\n  ],\n" + result(name, count) + "}\n");
    }

    private static String result(String name, int count) {
        return "  \"result\": {\n    \"id\": \"hardwrought:" + name + "\"" + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n";
    }

    // ------------------------------------------------------------------ the power station's grid

    /**
     * What a power station needs on its line: the heavy cable and the overhead conductor, the power
     * transformer, and the battery bank.
     */
    private static void grid() throws IOException {
        Path blocks = ASSETS.resolve("models/block");
        Path wires = BLOCK_TEX.resolve("hardwrought");
        // The overhead conductor: aluminium, bright and bare.
        write(tint(read(wires.resolve("wire_copper.png")), 0xC8CCD0), wires.resolve("wire_aluminium.png"));
        // Strands: the heavy cable thick and black, the conductor as thin as a cable but grey.
        String insulated = Files.readString(blocks.resolve("insulated_strand.json"));
        String insulatedHot = Files.readString(blocks.resolve("insulated_strand_hot.json"));
        text(blocks.resolve("heavy_cable_strand.json"), thicker(insulated));
        text(blocks.resolve("heavy_cable_strand_hot.json"), thicker(insulatedHot));
        text(blocks.resolve("overhead_strand.json"), Files.readString(blocks.resolve("cable_strand.json"))
                .replace("block/hardwrought/wire_copper", "block/hardwrought/wire_aluminium"));
        text(blocks.resolve("overhead_strand_hot.json"), Files.readString(blocks.resolve("cable_strand_hot.json")));
        // The high-power cable: thicker again, in the dull grey of its lead sheath.
        write(tint(read(wires.resolve("wire_rubber.png")), 0x6E747A), wires.resolve("wire_lead.png"));
        text(blocks.resolve("power_cable_strand.json"), thickest(insulated).replace("block/hardwrought/wire_rubber", "block/hardwrought/wire_lead"));
        text(blocks.resolve("power_cable_strand_hot.json"), thickest(insulatedHot));
        for (String strand : List.of("heavy_cable_strand", "heavy_cable_strand_hot", "overhead_strand", "overhead_strand_hot",
                "power_cable_strand", "power_cable_strand_hot")) {
            plainState(strand);
        }
        // The coils in hand.
        BufferedImage cable = read(ITEM_TEX.resolve("copper_cable.png"));
        write(tint(cable, 0x3A3A3E), ITEM_TEX.resolve("heavy_power_cable.png"));
        write(tint(cable, 0xC8CCD0), ITEM_TEX.resolve("overhead_conductor.png"));
        write(tint(cable, 0x6E747A), ITEM_TEX.resolve("high_power_cable.png"));
        for (String coil : List.of("heavy_power_cable", "overhead_conductor", "high_power_cable")) {
            text(ASSETS.resolve("models/item/" + coil + ".json"), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "hardwrought:item/%s"
                      }
                    }
                    """.formatted(coil));
            itemDefinition(coil, "hardwrought:item/" + coil);
        }
        // The power transformer: the transformer's shape in a dark green, oil-cooled tank.
        write(tint(read(wires.resolve("transformer_tank.png")), 0x4E6450), wires.resolve("power_transformer_tank.png"));
        write(tint(read(wires.resolve("transformer_fins.png")), 0x5E7460), wires.resolve("power_transformer_fins.png"));
        text(blocks.resolve("power_transformer.json"), """
                {
                  "parent": "hardwrought:block/transformer",
                  "textures": {
                    "tank": "hardwrought:block/hardwrought/power_transformer_tank",
                    "fins": "hardwrought:block/hardwrought/power_transformer_fins",
                    "porcelain": "hardwrought:block/hardwrought/porcelain",
                    "copper": "hardwrought:block/hardwrought/wire_copper",
                    "particle": "hardwrought:block/hardwrought/power_transformer_tank"
                  }
                }
                """);
        text(blocks.resolve("power_transformer_burnt.json"), Files.readString(blocks.resolve("transformer_burnt.json")));
        text(ASSETS.resolve("blockstates/power_transformer.json"), Files.readString(ASSETS.resolve("blockstates/transformer.json"))
                .replace("block/transformer", "block/power_transformer"));
        itemDefinition("power_transformer", "hardwrought:block/power_transformer");
        // The battery bank: steel and grey plastic, racks of cells inside, a charge display on its controller.
        BufferedImage casing = noisy(0x7A8086, 4, 40);
        fill(casing, 2, 2, 13, 13, 0x5A6066);
        frame(casing, 0, 0, 15, 15, STEEL_DARK);
        for (int y = 4; y <= 11; y += 7) line(casing, 3, y, 12, y, 0x9AA0A6);
        fill(casing, 11, 12, 13, 13, YELLOW);
        block("battery_bank_casing", casing);
        BufferedImage stack = noisy(0x2E3236, 3, 41);
        for (int row = 0; row < 2; row++) for (int cell = 0; cell < 3; cell++) {
            int x = 1 + cell * 5, y = 1 + row * 8;
            fill(stack, x, y + 1, x + 3, y + 6, 0xD8D8D0);
            frame(stack, x, y + 1, x + 3, y + 6, 0x9A9A90);
            px(stack, x + 1, y, 0xC03030);
            px(stack, x + 2, y, 0x303030);
        }
        block("battery_cell_stack", stack);
        for (boolean on : new boolean[] {false, true}) {
            BufferedImage front = read(BLOCK_TEX.resolve("battery_bank_casing.png"));
            fill(front, 2, 2, 13, 9, 0x1C2024);
            frame(front, 1, 1, 14, 10, STEEL_DARK);
            for (int bar = 0; bar < 4; bar++) fill(front, 3 + bar * 3, 4, 4 + bar * 3, 7, on ? 0x60F080 : 0x2E4A34);
            fill(front, 3, 12, 4, 13, on ? 0x40E060 : 0x3A3A3A);
            block("battery_bank_controller" + (on ? "_on" : ""), front);
        }
        for (String cube : List.of("battery_bank_casing", "battery_cell_stack")) {
            text(blocks.resolve(cube + ".json"), """
                    {
                      "parent": "minecraft:block/cube_all",
                      "textures": {
                        "all": "hardwrought:block/%s"
                      }
                    }
                    """.formatted(cube));
            plainState(cube);
            itemDefinition(cube, "hardwrought:block/" + cube);
        }
        for (boolean on : new boolean[] {false, true}) {
            String name = "battery_bank_controller" + (on ? "_on" : "");
            text(blocks.resolve(name + ".json"), """
                    {
                      "parent": "minecraft:block/orientable",
                      "textures": {
                        "front": "hardwrought:block/%s",
                        "side": "hardwrought:block/battery_bank_casing",
                        "top": "hardwrought:block/battery_bank_casing"
                      }
                    }
                    """.formatted(name));
        }
        controllerState("battery_bank_controller");
        itemDefinition("battery_bank_controller", "hardwrought:block/battery_bank_controller");
        // Loot, tags and recipes.
        Path tables = DATA.resolve("loot_table/blocks");
        String self = Files.readString(tables.resolve("engine_casing.json"));
        for (String block : List.of("power_transformer", "battery_bank_casing", "battery_cell_stack", "battery_bank_controller")) {
            text(tables.resolve(block + ".json"), self.replace("engine_casing", block));
        }
        addToTag(RES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"),
                List.of("power_transformer", "battery_bank_casing", "battery_cell_stack", "battery_bank_controller"));
        shaped("heavy_power_cable", 1, new String[] {"CC", "CC", "RR"}, "C", "hardwrought:copper_cable", "R", "hardwrought:rubber");
        shaped("high_power_cable", 1, new String[] {"HH", "HH", "LS"}, "H", "hardwrought:heavy_power_cable",
                "L", "hardwrought:lead_plate", "S", "hardwrought:silicone");
        shaped("overhead_conductor", 2, new String[] {"AAA", "ASA", "AAA"}, "A", "hardwrought:aluminum_rod", "S", "hardwrought:steel_rod");
        shaped("power_transformer", 1, new String[] {"BPB", "WCW", "KCK"}, "B", "hardwrought:bushing", "P", "hardwrought:steel_plate",
                "W", "hardwrought:winding", "C", "hardwrought:transformer_core", "K", "hardwrought:cooling_coil");
        shaped("battery_bank_casing", 4, new String[] {"PSP", "S S", "PSP"}, "P", "hardwrought:steel_plate", "S", "hardwrought:plastic_sheet");
        shaped("battery_cell_stack", 1, new String[] {"LAL", "LCL", "LAL"}, "L", "hardwrought:lead_plate", "A", "hardwrought:battery_acid",
                "C", "hardwrought:cell_case");
        shaped("battery_bank_controller", 1, new String[] {"VCV", "BRB", "PPP"}, "V", "hardwrought:voltmeter",
                "C", "hardwrought:advanced_circuit", "B", "hardwrought:battery", "R", "hardwrought:relay", "P", "hardwrought:battery_bank_casing");
    }

    /** A strand model made thicker still: the high-power cable. */
    private static String thickest(String model) {
        return model.replace("7.4", "6.2").replace("8.6", "9.8");
    }

    /** A strand model made thicker: the heavy cable is twice a wire's width. */
    private static String thicker(String model) {
        return model.replace("7.4", "6.8").replace("8.6", "9.2");
    }

    // ------------------------------------------------------------------ tags

    private static void tags() throws IOException {
        List<String> all = new ArrayList<>(List.of(CUBES));
        all.addAll(List.of(COLUMNS));
        for (String[] controller : CONTROLLERS) all.add(controller[0]);
        addToTag(RES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"), all);
        addToTag(RES.resolve("data/minecraft/tags/block/needs_stone_tool.json"), List.of("fluorite_ore", "deepslate_fluorite_ore"));
        addToTag(RES.resolve("data/minecraft/tags/block/needs_iron_tool.json"), List.of("reactor_vessel_wall", "corium"));
        addToTag(RES.resolve("data/minecraft/tags/block/ores.json"), List.of("fluorite_ore", "deepslate_fluorite_ore"));
    }

    /** Adds these to a tag's values, keeping what is there and its order, each once. */
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
