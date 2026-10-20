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
 * Writes the textures, models, blockstates, item definitions, loot tables and recipes of the safe haven
 * (docs/electricity-roadmap.md, stage E3): nichrome, the wire drawer, the glass kiln and the autoclave,
 * the parts of the devices, the devices themselves, and the cold stores.
 *
 * <p>Run from the repository root with a JDK: {@code java tools/HavenAssets.java}. It overwrites what it
 * writes and touches nothing else. Names and tags are kept by hand.
 */
public final class HavenAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final Path ITEM_TEX = ASSETS.resolve("textures/item");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block/hardwrought");
    private static final String TEX = "hardwrought:block/hardwrought/";

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("textures-only")) {
            nichrome();
            partTextures();
            stations();
            appliances();
            iceBox();
            return;
        }
        nichrome();
        partTextures();
        stations();
        appliances();
        iceBox();
        recipes();
        System.out.println("Haven assets written.");
    }

    // ------------------------------------------------------------------ nichrome

    private static final int NICHROME = 0xB9B4A0;

    /** Nichrome is nickel's stock in a duller, browner grey, and the molten metal the same. */
    private static void nichrome() throws IOException {
        for (String form : List.of("ingot", "rod", "plate")) {
            generatedItem("nichrome_" + form);
        }
        Path molten = ASSETS.resolve("textures/block/molten");
        write(tint(read(molten.resolve("nickel.png")), NICHROME), molten.resolve("nichrome.png"));
        Files.copy(molten.resolve("nickel.png.mcmeta"), molten.resolve("nichrome.png.mcmeta"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        text(ASSETS.resolve("models/block/molten/nichrome.json"), Files.readString(ASSETS.resolve("models/block/molten/nickel.json"))
                .replace("molten/nickel", "molten/nichrome"));
        // The molten states are numbered in MoltenMetals.ORDER; nichrome came last.
        Path states = ASSETS.resolve("blockstates/molten_metal.json");
        String molten27 = Files.readString(states);
        if (!molten27.contains("molten/nichrome")) {
            int end = molten27.lastIndexOf('}', molten27.lastIndexOf('}') - 1);
            int last = molten27.lastIndexOf('}', end - 1);
            molten27 = molten27.substring(0, last + 1) + ",\n    \"metal=27\": {\n      \"model\": \"hardwrought:block/molten/nichrome\"\n    }"
                    + molten27.substring(last + 1);
            text(states, molten27);
        }
        text(DATA.resolve("hardwrought/materials/nichrome.json"), """
                {
                  "tier": 2,
                  "density_kg_m3": 8400,
                  "melting_point_c": 1400
                }
                """);
    }

    // ------------------------------------------------------------------ parts

    private static final int IRON = 0x8A8E94, IRON_DARK = 0x55595F, COPPER = 0xC0703A, COPPER_DARK = 0x7E4220,
            PORCELAIN = 0xF2EFE6, GLASS = 0xCFE6EE, GLASS_EDGE = 0x8FB4C2, TUNGSTEN = 0x8C8F99, BRASS = 0xC8A03C,
            STEEL = 0xA8AEB6, STEEL_DARK = 0x6A7078, AMMONIA = 0x7FB8C8, SILVER = 0xDDE4EA;

    private static void partTextures() throws IOException {
        // Tungsten filament: a fine coil, glinting.
        part("tungsten_filament", img -> {
            for (int i = 0; i < 11; i++) {
                int x = 3 + i, y = 7 + (i % 2 == 0 ? -1 : 1);
                img.setRGB(x, y, argb(i % 3 == 0 ? 0xE8ECF0 : TUNGSTEN));
                img.setRGB(x, 7, shade(TUNGSTEN, -20));
            }
            fill(img, 1, 7, 2, 7, IRON_DARK);
            fill(img, 14, 7, 15, 7, IRON_DARK);
        });
        // Nichrome wire: a looser coil, dull grey-brown.
        part("nichrome_wire", img -> {
            for (int i = 0; i < 12; i++) {
                int x = 2 + i, y = (int) Math.round(8 + Math.sin(i * 0.9) * 3);
                img.setRGB(x, y, argb(i % 2 == 0 ? NICHROME : 0x8E8A78));
            }
        });
        // A glass bulb, empty.
        part("glass_bulb", img -> {
            disc(img, 7.5, 6.5, 4.6, GLASS);
            ring(img, 7.5, 6.5, 3.6, 4.6, GLASS_EDGE);
            fill(img, 6, 11, 9, 13, GLASS_EDGE);
            img.setRGB(5, 4, argb(0xFFFFFF));
        });
        // A glass tube.
        part("glass_tube", img -> {
            for (int i = 0; i < 10; i++) fill(img, 3 + i, 11 - i, 4 + i, 12 - i, GLASS);
            for (int i = 0; i < 10; i++) img.setRGB(3 + i, 12 - i, argb(GLASS_EDGE));
        });
        // A porcelain socket with its brass screw thread.
        part("lamp_socket", img -> {
            fill(img, 4, 8, 11, 13, PORCELAIN);
            fill(img, 4, 13, 11, 13, shadeRgb(PORCELAIN, -40));
            fill(img, 5, 4, 10, 8, BRASS);
            for (int y = 4; y <= 8; y += 2) fill(img, 5, y, 10, y, shadeRgb(BRASS, -40));
        });
        // A light bulb: glass, the filament inside, the brass foot.
        part("light_bulb", img -> {
            disc(img, 7.5, 6.0, 4.6, 0xF4F0D8);
            ring(img, 7.5, 6.0, 3.7, 4.6, GLASS_EDGE);
            fill(img, 6, 6, 9, 6, 0xE0A040);
            img.setRGB(6, 7, argb(TUNGSTEN));
            img.setRGB(9, 7, argb(TUNGSTEN));
            fill(img, 6, 10, 9, 13, BRASS);
            fill(img, 6, 11, 9, 11, shadeRgb(BRASS, -40));
            fill(img, 7, 14, 8, 14, IRON_DARK);
        });
        // A reflector: a polished dish.
        part("reflector", img -> {
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d < 7) img.setRGB(x, y, shade(SILVER, (int) (-d * 9)));
            }
            disc(img, 7.5, 7.5, 1.5, 0x404448);
        });
        // A ballast: a winding on a laminated core.
        part("ballast", img -> {
            fill(img, 2, 4, 13, 12, IRON_DARK);
            fill(img, 4, 5, 11, 11, 0x9A4A26);
            for (int y = 5; y <= 11; y += 2) fill(img, 4, y, 11, y, 0x7A3418);
            fill(img, 2, 4, 13, 4, IRON);
        });
        // An arc electrode: a pointed tungsten rod.
        part("arc_electrode", img -> {
            for (int i = 0; i < 11; i++) fill(img, 3 + i, 11 - i, 4 + i, 12 - i, i > 8 ? 0xE8ECF0 : TUNGSTEN);
            for (int i = 0; i < 11; i++) img.setRGB(3 + i, 12 - i, shade(TUNGSTEN, -30));
        });
        // A motion sensor: a black eye on a board.
        part("motion_sensor", img -> {
            fill(img, 2, 5, 13, 12, 0x2E6B3A);
            disc(img, 7.5, 7.5, 3.2, 0x1A1A1A);
            disc(img, 7.5, 7.5, 1.4, 0xC03030);
            img.setRGB(6, 6, argb(0xFFFFFF));
            fill(img, 3, 11, 4, 12, COPPER);
            fill(img, 11, 11, 12, 12, COPPER);
        });
        // A relay: a coil and a contact arm.
        part("relay", img -> {
            fill(img, 3, 9, 12, 13, 0x3A3E44);
            fill(img, 4, 4, 7, 9, 0x9A4A26);
            fill(img, 8, 5, 12, 5, SILVER);
            img.setRGB(12, 6, argb(SILVER));
        });
        // A heating element: nichrome wound on a porcelain former.
        part("heating_element", img -> {
            fill(img, 2, 6, 13, 9, PORCELAIN);
            for (int x = 2; x <= 13; x += 2) fill(img, x, 5, x, 10, NICHROME);
            fill(img, 0, 7, 1, 8, COPPER);
            fill(img, 14, 7, 15, 8, COPPER);
        });
        // A bimetal strip: brass on steel, bent.
        part("bimetal_strip", img -> {
            for (int i = 0; i < 12; i++) {
                int y = 10 - (int) Math.round(Math.sin(i / 11.0 * Math.PI) * 4);
                img.setRGB(2 + i, y, argb(BRASS));
                img.setRGB(2 + i, y + 1, argb(STEEL));
            }
        });
        // A thermostat: a dial on a porcelain body.
        part("thermostat", img -> {
            fill(img, 3, 3, 12, 12, PORCELAIN);
            fill(img, 3, 12, 12, 12, shadeRgb(PORCELAIN, -40));
            disc(img, 7.5, 7.5, 3.0, 0x3A3E44);
            fill(img, 7, 5, 8, 7, 0xD03A2B);
        });
        // Fan blades: three, round a hub.
        part("fan_blades", img -> {
            for (int b = 0; b < 3; b++) {
                double a = b * Math.PI * 2 / 3;
                for (double r = 1.5; r < 7; r += 0.5) {
                    for (double w = -1.2; w <= 1.2; w += 0.6) {
                        int x = (int) Math.round(7.5 + Math.cos(a + w / r) * r), y = (int) Math.round(7.5 + Math.sin(a + w / r) * r);
                        if (x >= 0 && x < 16 && y >= 0 && y < 16) img.setRGB(x, y, shade(STEEL, (int) (r * 3)));
                    }
                }
            }
            disc(img, 7.5, 7.5, 1.6, IRON_DARK);
        });
        // A compressor: a dome on a base, a copper pipe off it.
        part("compressor", img -> {
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                double d = Math.hypot((x - 7.5) / 1.1, y - 8);
                if (d < 5.5 && y <= 12) img.setRGB(x, y, shade(0x3C4A3E, (int) (-d * 4) + (y < 6 ? 15 : 0)));
            }
            fill(img, 2, 12, 13, 13, IRON_DARK);
            fill(img, 11, 4, 13, 6, COPPER);
        });
        // Refrigerant: a steel charge of ammonia, its valve on top.
        part("refrigerant", img -> {
            fill(img, 5, 4, 10, 13, STEEL);
            fill(img, 5, 4, 5, 13, STEEL_DARK);
            fill(img, 6, 7, 9, 10, AMMONIA);
            fill(img, 7, 2, 8, 3, BRASS);
        });
        // A cooling coil: copper tube bent back and forth.
        part("cooling_coil", img -> {
            for (int row = 0; row < 5; row++) {
                int y = 3 + row * 2;
                fill(img, 3, y, 12, y, COPPER);
                img.setRGB(row % 2 == 0 ? 12 : 3, y + 1, argb(COPPER_DARK));
            }
        });
        // An insulated panel: steel skins, wool between.
        part("insulated_panel", img -> {
            fill(img, 2, 2, 13, 13, 0xE4E2DA);
            fill(img, 2, 2, 13, 3, STEEL);
            fill(img, 2, 12, 13, 13, STEEL);
            for (int x = 3; x < 13; x += 3) img.setRGB(x, 8, argb(0xC8C4B4));
        });
        // A pressure vessel: a domed steel drum.
        part("pressure_vessel", img -> {
            fill(img, 4, 4, 11, 12, STEEL);
            disc(img, 7.5, 4.5, 3.8, STEEL);
            fill(img, 4, 4, 4, 12, STEEL_DARK);
            fill(img, 4, 8, 11, 8, STEEL_DARK);
        });
        // A pressure gauge: a dial behind glass.
        part("pressure_gauge", img -> {
            disc(img, 7.5, 7.5, 5.5, BRASS);
            disc(img, 7.5, 7.5, 4.5, 0xF4F2EA);
            for (int i = 0; i < 4; i++) img.setRGB(8 + i, 7 - i, argb(0x1A1A1A));
            img.setRGB(7, 8, argb(0x1A1A1A));
        });
        // Spoiled food: a grey-green mess.
        part("spoiled_food", img -> {
            Random rng = new Random(301);
            for (int x = 3; x < 13; x++) for (int y = 6; y < 13; y++) {
                if (Math.hypot((x - 7.5) / 1.3, y - 9.5) < 3.8) img.setRGB(x, y, shade(0x5E6A3A, rng.nextInt(30) - 15));
            }
            for (int i = 0; i < 6; i++) img.setRGB(4 + rng.nextInt(8), 7 + rng.nextInt(5), argb(0x8A9A4A));
        });
        generatedItem("sterile_dressing");
    }

    private interface Draw {
        void on(BufferedImage img);
    }

    private static void part(String name, Draw draw) throws IOException {
        if (!java.util.Set.of("tungsten_filament", "nichrome_wire", "glass_bulb", "glass_tube",
                "lamp_socket", "light_bulb", "reflector", "ballast", "arc_electrode",
                "motion_sensor", "relay", "heating_element", "bimetal_strip", "thermostat",
                "fan_blades", "compressor", "refrigerant", "cooling_coil", "insulated_panel",
                "pressure_vessel", "pressure_gauge").contains(name)) {
            BufferedImage img = blank();
            draw.on(img);
            write(img, ITEM_TEX.resolve(name + ".png"));
        }
        generatedItem(name);
    }

    private static void ring(BufferedImage img, double cx, double cy, double inner, double outer, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            double d = Math.hypot(x - cx, y - cy);
            if (d < outer && d > inner) img.setRGB(x, y, argb(colour));
        }
    }

    /** {@link #shade} as a plain colour, for {@link #fill}. */
    private static int shadeRgb(int colour, int by) {
        return shade(colour, by) & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ stations

    /** The wire drawer, the glass kiln and the autoclave are machines like the lathe, each with a face of its own. */
    private static void stations() throws IOException {
        blockTexture("wire_drawer_front", noisy(0x5A5E64, 6, 101, (img, rng) -> {
            border(img, 0x3C4046);
            // A die plate with holes of falling size, and a wire coming out of the smallest.
            double[] sizes = {2.8, 2.2, 1.6, 1.1};
            for (int i = 0; i < 4; i++) disc(img, 3.5 + i * 3, 6.5, sizes[i], 0x1A1A1A);
            fill(img, 12, 6, 15, 6, COPPER);
            fill(img, 2, 11, 13, 13, 0x7A7E84);
        }));
        blockTexture("wire_drawer_top", noisy(0x5A5E64, 6, 102, (img, rng) -> {
            border(img, 0x3C4046);
            disc(img, 7.5, 7.5, 4.5, COPPER);
            disc(img, 7.5, 7.5, 2.0, 0x2A2A2A);
        }));
        blockTexture("glass_kiln_front", noisy(0x8A5A3E, 8, 103, (img, rng) -> {
            border(img, 0x5A3A28);
            fill(img, 4, 5, 11, 11, 0x2A1A12);
            fill(img, 5, 7, 10, 10, 0xE07A20);
            fill(img, 6, 8, 9, 9, 0xFFD060);
        }));
        blockTexture("glass_kiln_top", noisy(0x8A5A3E, 8, 104, (img, rng) -> {
            border(img, 0x5A3A28);
            disc(img, 7.5, 7.5, 3.0, 0x2A1A12);
        }));
        blockTexture("autoclave_front", noisy(0xB8BEC6, 5, 105, (img, rng) -> {
            border(img, 0x7A8088);
            disc(img, 7.5, 8.5, 5.0, 0x9AA0A8);
            disc(img, 7.5, 8.5, 4.0, 0x7A8088);
            disc(img, 11.5, 3.5, 2.0, 0xF4F2EA);
            img.setRGB(12, 3, argb(0x1A1A1A));
            fill(img, 2, 8, 3, 9, 0x3A3E44);
        }));
        blockTexture("autoclave_top", noisy(0xB8BEC6, 5, 106, (img, rng) -> {
            border(img, 0x7A8088);
            disc(img, 7.5, 7.5, 5.0, 0x9AA0A8);
            fill(img, 7, 2, 8, 4, BRASS);
        }));
        Path blocks = ASSETS.resolve("models/block");
        String lathe = Files.readString(blocks.resolve("lathe.json"));
        String states = Files.readString(ASSETS.resolve("blockstates/lathe.json"));
        String tables = Files.readString(DATA.resolve("loot_table/blocks/lathe.json"));
        for (String station : List.of("wire_drawer", "glass_kiln", "autoclave")) {
            text(blocks.resolve(station + ".json"), lathe.replace(TEX + "lathe_front", TEX + station + "_front")
                    .replace(TEX + "lathe_top", TEX + station + "_top"));
            text(ASSETS.resolve("blockstates/" + station + ".json"), states.replace("hardwrought:block/lathe", "hardwrought:block/" + station));
            text(DATA.resolve("loot_table/blocks/" + station + ".json"), tables.replace("hardwrought:lathe", "hardwrought:" + station));
            itemDefinition(station, "hardwrought:block/" + station);
        }
    }

    // ------------------------------------------------------------------ appliances

    private static final int ENAMEL = 0xE4E2DA, CASING = 0x5A5E64, SHADE = 0x3C6E4E;
    private static final List<String> APPLIANCES = List.of("electric_light", "arc_floodlight", "motion_lamp", "alarm_bell",
            "electric_heater", "air_cooler", "ventilation_fan", "refrigerator", "freezer", "heat_lamp", "bedside_monitor");

    private static void appliances() throws IOException {
        blockTexture("appliance_enamel", noisy(ENAMEL, 4, 201, (img, rng) -> border(img, 0xC8C4B8)));
        blockTexture("appliance_casing", noisy(CASING, 5, 202, (img, rng) -> border(img, 0x3C4046)));
        blockTexture("lamp_shade", noisy(SHADE, 5, 203, (img, rng) -> {
            for (int x = 0; x < 16; x++) img.setRGB(x, 15, shade(SHADE, -30));
        }));
        blockTexture("brass", noisy(BRASS, 8, 204, null));
        blockTexture("bell_frame", noisy(0x4A3A2A, 5, 219, (img, rng) -> border(img, 0x2E2418)));
        for (boolean on : new boolean[] {false, true}) {
            String lit = on ? "_on" : "";
            blockTexture("floodlight_lens" + lit, noisy(on ? 0xFFF6D8 : 0x9AA4AA, on ? 3 : 6, 210, (img, rng) -> {
                border(img, CASING);
                for (int x = 1; x < 15; x += 3) for (int y = 1; y < 15; y++) img.setRGB(x, y, on ? argb(0xFFFFFF) : shade(0x9AA4AA, 15));
            }));
            blockTexture("motion_eye" + lit, noisy(ENAMEL, 4, 211, (img, rng) -> {
                border(img, 0xC8C4B8);
                disc(img, 7.5, 7.5, 3.5, 0x1A1A1A);
                disc(img, 7.5, 7.5, 1.5, on ? 0xFF4040 : 0x601818);
            }));
            blockTexture("heater_grille" + lit, noisy(CASING, 4, 212, (img, rng) -> {
                border(img, 0x3C4046);
                for (int y = 2; y < 14; y += 2) fill(img, 2, y, 13, y, on ? 0xF08030 : 0x2A2C30);
            }));
            blockTexture("heat_lamp_bulb" + lit, noisy(on ? 0xE03A20 : 0x6A2A20, 5, 213, (img, rng) -> {
                if (on) disc(img, 7.5, 7.5, 3.5, 0xFF8040);
            }));
            blockTexture("monitor_screen" + lit, noisy(0x1A2A1E, 3, 214, (img, rng) -> {
                border(img, 0x3A3E44);
                if (!on) return;
                int[] trace = {9, 9, 9, 8, 4, 12, 9, 9, 9, 9, 7, 9, 9, 9};
                for (int x = 1; x < 15; x++) img.setRGB(x, trace[x - 1], argb(0x60FF80));
            }));
        }
        blockTexture("cooler_vents", noisy(ENAMEL, 4, 215, (img, rng) -> {
            border(img, 0xC8C4B8);
            for (int y = 3; y < 13; y += 2) fill(img, 2, y, 13, y, 0x8A9098);
            img.setRGB(12, 14, argb(0x3A80D0));
        }));
        blockTexture("fan_face", noisy(CASING, 4, 216, (img, rng) -> {
            border(img, 0x3C4046);
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d < 6.8) img.setRGB(x, y, argb(0x1E2024));
                double a = Math.atan2(y - 7.5, x - 7.5);
                if (d < 6.2 && d > 1.4 && Math.abs(Math.sin(a * 1.5 + d * 0.25)) < 0.35) img.setRGB(x, y, argb(STEEL));
            }
            disc(img, 7.5, 7.5, 1.4, IRON);
        }));
        blockTexture("fridge_door", noisy(ENAMEL, 3, 217, (img, rng) -> {
            border(img, 0xC8C4B8);
            fill(img, 12, 4, 12, 11, STEEL);
        }));
        blockTexture("freezer_door", noisy(0xD8E4EC, 3, 218, (img, rng) -> {
            border(img, 0xB4C4D0);
            fill(img, 1, 5, 14, 5, 0xB4C4D0);
            fill(img, 12, 7, 12, 12, STEEL);
            fill(img, 12, 2, 12, 3, STEEL);
        }));

        Path blocks = ASSETS.resolve("models/block");
        String shade = "\"shade\": \"" + TEX + "lamp_shade\",";
        // Each model faces north; the blockstate turns it. Where the front lights up, a second model has the lit face.
        applianceModel(blocks, "electric_light", List.of(
                box(5, 0, 5, 11, 1, 11, "#casing"), box(7.5, 1, 7.5, 8.5, 4, 8.5, "#casing"),
                box(6, 3, 6, 10, 6, 10, "#glass"), box(4, 5, 4, 12, 9, 12, "#shade")),
                shade, "glass", TEX + "lamp_glass_off", TEX + "lamp_glass_on");
        applianceModel(blocks, "arc_floodlight", List.of(
                box(3, 0, 3, 13, 2, 13, "#casing"), box(2, 2, 7, 3, 9, 9, "#casing"), box(13, 2, 7, 14, 9, 9, "#casing"),
                box(3, 4, 3, 13, 14, 13, "#casing", "#lens")),
                "", "lens", TEX + "floodlight_lens", TEX + "floodlight_lens_on");
        applianceModel(blocks, "motion_lamp", List.of(
                box(4, 0, 4, 12, 1, 12, "#casing"), box(4, 1, 4, 12, 8, 12, "#enamel", "#eye"),
                box(5, 8, 5, 11, 10, 11, "#glass")),
                "\"eye\": \"" + TEX + "motion_eye\",", "glass", TEX + "lamp_glass_off", TEX + "lamp_glass_on");
        applianceModel(blocks, "alarm_bell", List.of(
                box(4, 0, 4, 12, 2, 12, "#frame"), box(7, 2, 7, 9, 4, 9, "#frame"),
                box(4, 4, 4, 12, 10, 12, "#brass"), box(5, 10, 5, 11, 12, 11, "#brass")),
                "\"frame\": \"" + TEX + "bell_frame\",\n\"brass\": \"" + TEX + "brass\",", null, null, null);
        applianceModel(blocks, "electric_heater", List.of(
                box(1, 0, 4, 15, 1, 12, "#casing"), box(2, 1, 5, 14, 14, 11, "#casing", "#grille")),
                "", "grille", TEX + "heater_grille", TEX + "heater_grille_on");
        applianceModel(blocks, "air_cooler", List.of(box(1, 0, 1, 15, 15, 15, "#enamel", "#vents")),
                "\"vents\": \"" + TEX + "cooler_vents\",", null, null, null);
        applianceModel(blocks, "ventilation_fan", List.of(box(0, 0, 0, 16, 16, 16, "#casing", "#fan")),
                "\"fan\": \"" + TEX + "fan_face\",", null, null, null);
        applianceModel(blocks, "refrigerator", List.of(box(1, 0, 1, 15, 16, 15, "#enamel", "#door")),
                "\"door\": \"" + TEX + "fridge_door\",", null, null, null);
        applianceModel(blocks, "freezer", List.of(box(1, 0, 1, 15, 16, 15, "#enamel", "#door")),
                "\"door\": \"" + TEX + "freezer_door\",", null, null, null);
        applianceModel(blocks, "heat_lamp", List.of(
                box(5, 0, 5, 11, 1, 11, "#casing"), box(7.5, 1, 7.5, 8.5, 7, 8.5, "#casing"),
                box(4, 7, 4, 12, 12, 12, "#shade"), box(5.5, 6, 5.5, 10.5, 7, 10.5, "#bulb")),
                shade, "bulb", TEX + "heat_lamp_bulb", TEX + "heat_lamp_bulb_on");
        applianceModel(blocks, "bedside_monitor", List.of(
                box(3, 0, 4, 13, 2, 12, "#casing"), box(7, 2, 7, 9, 4, 9, "#casing"),
                box(3, 4, 5, 13, 12, 11, "#casing", "#screen")),
                "", "screen", TEX + "monitor_screen", TEX + "monitor_screen_on");

        String[] facings = {"north", "east", "south", "west"};
        for (String id : APPLIANCES) {
            boolean lights = Files.exists(blocks.resolve(id + "_on.json"));
            List<String> variants = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                for (boolean on : new boolean[] {false, true}) {
                    String model = "hardwrought:block/" + id + (on && lights ? "_on" : "");
                    variants.add("    \"facing=" + facings[i] + ",on=" + on + "\": {\n      \"model\": \"" + model + "\""
                            + (i == 0 ? "" : ",\n      \"y\": " + i * 90) + "\n    }");
                }
            }
            text(ASSETS.resolve("blockstates/" + id + ".json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
            itemDefinition(id, "hardwrought:block/" + id);
            selfDrop(id);
        }
    }

    /** A model of these boxes; where {@code litKey} is given, a second model "_on" with that texture lit. */
    private static void applianceModel(Path blocks, String id, List<String> elements, String extra, String litKey,
                                       String off, String on) throws IOException {
        String textures = extra + "\n\"enamel\": \"" + TEX + "appliance_enamel\",\n\"casing\": \"" + TEX + "appliance_casing\",\n"
                + "\"particle\": \"" + TEX + "appliance_casing\"";
        Files.deleteIfExists(blocks.resolve(id + "_on.json"));
        if (litKey == null) {
            model(blocks.resolve(id + ".json"), textures, elements);
            return;
        }
        model(blocks.resolve(id + ".json"), "\"" + litKey + "\": \"" + off + "\",\n" + textures, elements);
        model(blocks.resolve(id + "_on.json"), "\"" + litKey + "\": \"" + on + "\",\n" + textures, elements);
    }

    // ------------------------------------------------------------------ ice box

    private static void iceBox() throws IOException {
        blockTexture("ice_box_front", noisy(0x7A5A3A, 7, 301, (img, rng) -> {
            border(img, 0x4A3420);
            fill(img, 1, 4, 14, 4, IRON_DARK);
            fill(img, 1, 11, 14, 11, IRON_DARK);
            fill(img, 7, 6, 8, 8, BRASS);
        }));
        blockTexture("ice_box_side", noisy(0x7A5A3A, 7, 302, (img, rng) -> {
            border(img, 0x4A3420);
            fill(img, 1, 4, 14, 4, IRON_DARK);
            fill(img, 1, 11, 14, 11, IRON_DARK);
        }));
        blockTexture("ice_box_top", noisy(0x7A5A3A, 7, 303, (img, rng) -> {
            border(img, 0x4A3420);
            fill(img, 2, 7, 13, 8, 0x5A4028);
        }));
        text(ASSETS.resolve("models/block/ice_box.json"), """
                {
                  "parent": "minecraft:block/orientable",
                  "textures": {
                    "front": "%sice_box_front",
                    "side": "%sice_box_side",
                    "top": "%sice_box_top",
                    "particle": "%sice_box_side"
                  }
                }
                """.formatted(TEX, TEX, TEX, TEX));
        String[] facings = {"north", "east", "south", "west"};
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            variants.add("    \"facing=" + facings[i] + "\": {\n      \"model\": \"hardwrought:block/ice_box\""
                    + (i == 0 ? "" : ",\n      \"y\": " + i * 90) + "\n    }");
        }
        text(ASSETS.resolve("blockstates/ice_box.json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
        itemDefinition("ice_box", "hardwrought:block/ice_box");
        selfDrop("ice_box");
    }

    // ------------------------------------------------------------------ recipes

    private static void recipes() throws IOException {
        // Parts.
        shapeless("lamp_socket", 2, "hardwrought:porcelain", "hardwrought:copper_plate");
        shapeless("light_bulb", 1, "hardwrought:tungsten_filament", "hardwrought:glass_bulb", "hardwrought:lamp_socket");
        shaped("reflector", 1, new String[] {"AAA", "AGA"}, "A", "hardwrought:aluminum_plate", "G", "minecraft:glass_pane");
        shaped("ballast", 1, new String[] {"PWP", "III"}, "P", "hardwrought:electrical_steel_plate", "W", "hardwrought:winding",
                "I", "hardwrought:iron_plate");
        shaped("motion_sensor", 1, new String[] {" G ", "QCQ", "W W"}, "G", "minecraft:glass_pane", "Q", "hardwrought:quartz_oscillator",
                "C", "hardwrought:basic_circuit", "W", "hardwrought:copper_wire");
        shaped("relay", 2, new String[] {"S", "R", "I"}, "S", "hardwrought:silver_plate", "R", "hardwrought:redstone_coil",
                "I", "hardwrought:iron_plate");
        shaped("heating_element", 1, new String[] {"NNN", "PPP", "NNN"}, "N", "hardwrought:nichrome_wire", "P", "hardwrought:porcelain");
        shapeless("bimetal_strip", 2, "hardwrought:copper_plate", "hardwrought:iron_plate");
        shaped("thermostat", 1, new String[] {"B", "P", "W"}, "B", "hardwrought:bimetal_strip", "P", "hardwrought:porcelain",
                "W", "hardwrought:copper_wire");
        shaped("fan_blades", 1, new String[] {"A A", " R ", "A A"}, "A", "hardwrought:aluminum_plate", "R", "hardwrought:steel_rod");
        shaped("compressor", 1, new String[] {"SGS", "PMP", "SGS"}, "S", "hardwrought:steel_plate", "G", "hardwrought:rubber_gasket",
                "P", "hardwrought:engine_piston", "M", "hardwrought:electric_motor");
        shaped("cooling_coil", 1, new String[] {"CCC", "C C", "CCC"}, "C", "hardwrought:copper_plate");
        shaped("insulated_panel", 4, new String[] {"SSS", "WWW", "SSS"}, "S", "hardwrought:steel_plate", "W", "#minecraft:wool");
        shaped("pressure_vessel", 1, new String[] {"SGS", "S S", "SSS"}, "S", "hardwrought:steel_plate", "G", "hardwrought:silicone_gasket");
        shaped("pressure_gauge", 1, new String[] {" G ", "CFC"}, "G", "minecraft:glass_pane", "C", "hardwrought:copper_plate",
                "F", "hardwrought:fine_gear");
        // Stations.
        shaped("wire_drawer", 1, new String[] {"IMI", "DSD", "ICI"}, "I", "hardwrought:iron_plate", "M", "hardwrought:electric_motor",
                "D", "hardwrought:tungsten_steel_plate", "S", "hardwrought:shaft", "C", "hardwrought:basic_circuit");
        shaped("glass_kiln", 1, new String[] {"BBB", "BHB", "BCB"}, "B", "minecraft:bricks", "H", "hardwrought:redstone_coil",
                "C", "hardwrought:basic_circuit");
        shaped("autoclave", 1, new String[] {"GVT", "PHP", "PCP"}, "G", "hardwrought:pressure_gauge", "V", "hardwrought:pressure_vessel",
                "T", "hardwrought:thermostat", "P", "hardwrought:steel_plate", "H", "hardwrought:heating_element",
                "C", "hardwrought:basic_circuit");
        // Devices.
        shaped("electric_light", 1, new String[] {" S ", " B ", "WIW"}, "S", "hardwrought:iron_plate", "B", "hardwrought:light_bulb",
                "W", "hardwrought:insulated_wire", "I", "hardwrought:iron_plate");
        shaped("arc_floodlight", 1, new String[] {"ERE", "SBS", "PTP"}, "E", "hardwrought:arc_electrode", "R", "hardwrought:reflector",
                "S", "hardwrought:steel_plate", "B", "hardwrought:ballast", "P", "hardwrought:porcelain", "T", "hardwrought:steel_rod");
        shaped("motion_lamp", 1, new String[] {" L ", "SMR", " P "}, "L", "hardwrought:light_bulb", "S", "hardwrought:lamp_socket",
                "M", "hardwrought:motion_sensor", "R", "hardwrought:relay", "P", "hardwrought:iron_plate");
        shaped("alarm_bell", 1, new String[] {" B ", "RCR", "PPP"}, "B", "minecraft:bell", "R", "hardwrought:relay",
                "C", "hardwrought:redstone_coil", "P", "hardwrought:iron_plate");
        shaped("electric_heater", 1, new String[] {"PTP", "HHH", "PWP"}, "P", "hardwrought:iron_plate", "T", "hardwrought:thermostat",
                "H", "hardwrought:heating_element", "W", "hardwrought:insulated_wire");
        shaped("air_cooler", 1, new String[] {"PTP", "KCF", "PRP"}, "P", "hardwrought:steel_plate", "T", "hardwrought:thermostat",
                "K", "hardwrought:compressor", "C", "hardwrought:cooling_coil", "F", "hardwrought:fan_blades", "R", "hardwrought:refrigerant");
        shaped("ventilation_fan", 1, new String[] {"PFP", "PMP", "PDP"}, "P", "hardwrought:iron_plate", "F", "hardwrought:fan_blades",
                "M", "hardwrought:electric_motor", "D", "hardwrought:tube_section");
        shaped("refrigerator", 1, new String[] {"NTN", "NKN", "NCR"}, "N", "hardwrought:insulated_panel", "T", "hardwrought:thermostat",
                "K", "hardwrought:compressor", "C", "hardwrought:cooling_coil", "R", "hardwrought:refrigerant");
        shaped("freezer", 1, new String[] {"NCN", "NFN", "NRN"}, "N", "hardwrought:insulated_panel", "C", "hardwrought:cooling_coil",
                "F", "hardwrought:refrigerator", "R", "hardwrought:refrigerant");
        shaped("heat_lamp", 1, new String[] {"RBR", " S ", "PWP"}, "R", "hardwrought:reflector", "B", "hardwrought:light_bulb",
                "S", "hardwrought:steel_rod", "P", "hardwrought:iron_plate", "W", "hardwrought:insulated_wire");
        shaped("bedside_monitor", 1, new String[] {"GGG", "ACO", "PWP"}, "G", "minecraft:glass_pane", "A", "hardwrought:advanced_circuit",
                "C", "hardwrought:basic_circuit", "O", "hardwrought:quartz_oscillator", "P", "hardwrought:iron_plate",
                "W", "hardwrought:insulated_wire");
        shaped("ice_box", 1, new String[] {"PNP", "N N", "PNP"}, "P", "#minecraft:planks", "N", "hardwrought:insulated_panel");
    }

    private static void blockTexture(String name, BufferedImage img) throws IOException {
        write(img, BLOCK_TEX.resolve(name + ".png"));
    }

    // ------------------------------------------------------------------ painting

    private interface Paint {
        void on(BufferedImage img, Random rng);
    }

    static BufferedImage blank() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    static BufferedImage noisy(int base, int spread, long seed, Paint paint) {
        Random rng = new Random(seed);
        BufferedImage img = blank();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, shade(base, rng.nextInt(spread * 2 + 1) - spread));
        if (paint != null) paint.on(img, rng);
        return img;
    }

    static void border(BufferedImage img, int colour) {
        for (int i = 0; i < 16; i++) {
            img.setRGB(i, 0, argb(colour));
            img.setRGB(i, 15, argb(colour));
            img.setRGB(0, i, argb(colour));
            img.setRGB(15, i, argb(colour));
        }
    }

    static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int colour) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) img.setRGB(x, y, argb(colour));
    }

    static void disc(BufferedImage img, double cx, double cy, double r, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if (Math.hypot(x - cx, y - cy) < r) img.setRGB(x, y, argb(colour));
    }

    /** Keeps each pixel's lightness and gives it this colour. */
    static BufferedImage tint(BufferedImage source, int colour) {
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

    static int shade(int colour, int by) {
        return argb((clamp(((colour >> 16) & 0xFF) + by) << 16) | (clamp(((colour >> 8) & 0xFF) + by) << 8)
                | clamp((colour & 0xFF) + by));
    }

    static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }

    static BufferedImage read(Path path) throws IOException {
        return ImageIO.read(path.toFile());
    }

    static void write(BufferedImage img, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "png", path.toFile());
    }

    // ------------------------------------------------------------------ json

    static void generatedItem(String name) throws IOException {
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

    static void itemDefinition(String name, String model) throws IOException {
        text(ASSETS.resolve("items/" + name + ".json"), """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s"
                  }
                }
                """.formatted(model));
    }

    static String box(double x0, double y0, double z0, double x1, double y1, double z1, String texture) {
        return box(x0, y0, z0, x1, y1, z1, texture, texture);
    }

    /** A box with one texture on its north face and another on the rest. */
    static String box(double x0, double y0, double z0, double x1, double y1, double z1, String side, String front) {
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

    static String n(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    static void model(Path path, String textures, List<String> elements) throws IOException {
        text(path, "{\n  \"parent\": \"minecraft:block/block\",\n  \"textures\": {\n" + indent(textures, 4)
                + "  },\n  \"elements\": [\n" + String.join(",\n", elements) + "\n  ]\n}\n");
    }

    static String indent(String lines, int by) {
        StringBuilder out = new StringBuilder();
        for (String line : lines.strip().split("\n")) out.append(" ".repeat(by)).append(line.strip()).append('\n');
        return out.toString();
    }

    static void shaped(String name, int count, String[] pattern, String... key) throws IOException {
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

    static void shapeless(String name, int count, String... ingredients) throws IOException {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < ingredients.length; i++) {
            if (i > 0) list.append(",\n");
            list.append("    \"").append(ingredients[i]).append('"');
        }
        text(DATA.resolve("recipe/" + name + ".json"), "{\n  \"type\": \"minecraft:crafting_shapeless\",\n  \"category\": \"redstone\",\n"
                + "  \"ingredients\": [\n" + list + "\n  ],\n" + result(name, count) + "}\n");
    }

    static String result(String name, int count) {
        return "  \"result\": {\n    \"id\": \"hardwrought:" + name + "\"" + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n";
    }

    static void selfDrop(String block) throws IOException {
        Path tables = DATA.resolve("loot_table/blocks");
        text(tables.resolve(block + ".json"), Files.readString(tables.resolve("insulator.json")).replace("hardwrought:insulator", "hardwrought:" + block));
    }

    static void text(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
