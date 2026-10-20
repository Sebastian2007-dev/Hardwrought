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
 * Writes the textures, models, blockstates, item definitions, loot tables, recipes, tags and weights of
 * carried power (docs/electricity-roadmap.md, stage E4): the cells, the devices, their parts, the
 * charging station and the assembly bench.
 *
 * <p>Run from the repository root with a JDK: {@code java tools/CarriedAssets.java}. It overwrites what it
 * writes and touches nothing else. Names are kept by hand.
 */
public final class CarriedAssets {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hardwrought");
    private static final Path DATA = RES.resolve("data/hardwrought");
    private static final Path ITEM_TEX = ASSETS.resolve("textures/item");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block/hardwrought");
    private static final String TEX = "hardwrought:block/hardwrought/";

    private static final int IRON = 0x8A8E94, IRON_DARK = 0x55595F, COPPER = 0xC0703A, LEAD = 0x6C6A84, NICKEL = 0xC9C49C,
            SILVER = 0xDDE4EA, ZINC = 0xA8B8B8, PLASTIC = 0x2E3136, ALUMINUM = 0xC8CDD3, STEEL = 0xA8AEB6, TUNGSTEN_STEEL = 0x6E7483,
            LEATHER = 0x7A4E2E, GLASS = 0xCFE6EE, NICHROME = 0xB9B4A0, ACID = 0xD8E070, POTASH = 0xD8D2C4, BRASS = 0xC8A03C,
            GREEN_BOARD = 0x2E6B3A, BLUE = 0x4FB4FF, TITANIUM = 0xB7AECB, ORANGE = 0xE07A20;

    /** What only the assembly bench makes. */
    private static final List<String> ASSEMBLED = List.of("lead_acid_pack", "nickel_iron_cell", "silver_zinc_cell", "headlamp",
            "night_vision_goggles", "exoskeleton", "electric_drill", "chainsaw", "ore_scanner", "heated_lining", "cooled_lining");

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("textures-only")) {
            items();
            return;
        }
        items();
        blocks();
        recipes();
        data();
        System.out.println("Carried power assets written.");
    }

    // ------------------------------------------------------------------ items

    private interface Draw {
        void on(BufferedImage img);
    }

    private static void items() throws IOException {
        // Cells: a case with its terminals and a band of the metal inside.
        cell("lead_acid_pack", 0x3A3A44, LEAD, 0x4C4C58);
        cell("nickel_iron_cell", 0x5A5048, NICKEL, 0x6A5E54);
        cell("silver_zinc_cell", 0x2E3A4A, SILVER, 0x3C4C60);
        item("headlamp", img -> {
            ring(img, 7.5, 9, 5.5, 6.8, LEATHER);
            fill(img, 4, 3, 11, 8, PLASTIC);
            disc(img, 7.5, 5.5, 2.2, 0xFFF2C0);
            fill(img, 5, 3, 10, 3, shadeRgb(PLASTIC, 30));
        });
        item("night_vision_goggles", img -> {
            fill(img, 1, 6, 14, 7, LEATHER);
            fill(img, 2, 4, 13, 10, PLASTIC);
            disc(img, 5, 7, 2.6, 0x204A20);
            disc(img, 11, 7, 2.6, 0x204A20);
            img.setRGB(4, 6, argb(0x7FFF7F));
            img.setRGB(10, 6, argb(0x7FFF7F));
        });
        item("exoskeleton", img -> {
            fill(img, 4, 1, 11, 3, TITANIUM);
            for (int x : new int[] {4, 10}) {
                fill(img, x, 3, x + 1, 14, TITANIUM);
                fill(img, x - 1, 7, x + 2, 9, IRON_DARK);
                fill(img, x - 1, 14, x + 2, 15, IRON_DARK);
            }
            fill(img, 6, 2, 9, 4, BLUE);
        });
        item("electric_drill", img -> {
            fill(img, 2, 4, 10, 8, ORANGE);
            fill(img, 2, 4, 10, 4, shadeRgb(ORANGE, 30));
            fill(img, 5, 8, 8, 13, 0x2A2A2A);
            fill(img, 11, 5, 12, 7, IRON_DARK);
            fill(img, 13, 6, 15, 6, STEEL);
            fill(img, 4, 12, 9, 13, LEAD);
        });
        item("chainsaw", img -> {
            fill(img, 1, 6, 7, 11, ORANGE);
            fill(img, 2, 4, 6, 6, 0x2A2A2A);
            fill(img, 7, 7, 15, 10, STEEL);
            for (int x = 7; x <= 15; x += 2) {
                img.setRGB(x, 6, argb(IRON_DARK));
                img.setRGB(x, 11, argb(IRON_DARK));
            }
        });
        item("ore_scanner", img -> {
            fill(img, 4, 2, 11, 13, 0x3A4048);
            fill(img, 5, 3, 10, 8, 0x0E2A2E);
            disc(img, 7.5, 5.5, 2.4, 0x0E2A2E);
            img.setRGB(6, 4, argb(0x4FF0FF));
            img.setRGB(9, 6, argb(0x4FF0FF));
            fill(img, 6, 10, 9, 11, BRASS);
        });
        lining("heated_lining", NICHROME, ORANGE);
        lining("cooled_lining", 0x5A7A9A, 0x8FD0FF);
        bit("steel_drill_bit", STEEL);
        bit("tungsten_steel_drill_bit", TUNGSTEN_STEEL);
        item("saw_chain", img -> {
            for (int i = 0; i < 12; i++) {
                double a = i / 12.0 * Math.PI * 2;
                int x = (int) Math.round(7.5 + Math.cos(a) * 5.5), y = (int) Math.round(7.5 + Math.sin(a) * 3.5);
                fill(img, x, y, x + 1, y, i % 2 == 0 ? STEEL : IRON_DARK);
            }
        });
        item("battery_acid", img -> {
            fill(img, 6, 2, 9, 3, 0x8A6A4A);
            fill(img, 5, 4, 10, 4, GLASS);
            fill(img, 4, 5, 11, 13, GLASS);
            fill(img, 5, 8, 10, 12, ACID);
        });
        item("potash", img -> {
            Random rng = new Random(401);
            for (int x = 3; x < 13; x++) for (int y = 7; y < 13; y++) {
                if (Math.hypot((x - 7.5) / 1.4, y - 11) < 4) img.setRGB(x, y, shade(POTASH, rng.nextInt(24) - 12));
            }
        });
        item("cell_case", img -> {
            fill(img, 3, 3, 12, 13, PLASTIC);
            fill(img, 4, 4, 11, 12, 0x1E2024);
            fill(img, 5, 1, 6, 2, LEAD);
            fill(img, 9, 1, 10, 2, LEAD);
        });
        item("toggle_switch", img -> {
            fill(img, 4, 7, 11, 13, 0xF2EFE6);
            fill(img, 7, 3, 8, 8, STEEL);
            fill(img, 6, 2, 9, 3, 0x2A2A2A);
        });
        item("device_housing", img -> {
            fill(img, 2, 4, 13, 12, ALUMINUM);
            fill(img, 2, 4, 13, 4, shadeRgb(ALUMINUM, 25));
            fill(img, 3, 12, 13, 12, shadeRgb(ALUMINUM, -40));
            for (int x = 4; x < 12; x += 2) img.setRGB(x, 8, argb(shadeRgb(ALUMINUM, -50)));
        });
        item("reduction_gear", img -> {
            gear(img, 5.5, 8.5, 4.5, BRASS);
            gear(img, 11, 5, 2.8, STEEL);
        });
        item("chuck", img -> {
            fill(img, 4, 4, 11, 11, STEEL);
            fill(img, 4, 4, 11, 4, shadeRgb(STEEL, 25));
            for (int y = 5; y < 11; y += 2) fill(img, 4, y, 11, y, shadeRgb(STEEL, -30));
            fill(img, 6, 11, 9, 14, IRON_DARK);
        });
        item("saw_bar", img -> {
            for (int i = 0; i < 11; i++) fill(img, 2 + i, 11 - i, 3 + i, 13 - i, STEEL);
            for (int i = 0; i < 11; i += 2) img.setRGB(2 + i, 13 - i, argb(IRON_DARK));
        });
        item("strap", img -> {
            for (int i = 0; i < 12; i++) {
                int y = 8 + (int) Math.round(Math.sin(i * 0.6) * 2);
                fill(img, 2 + i, y, 2 + i, y + 2, LEATHER);
            }
            fill(img, 6, 7, 8, 11, BRASS);
        });
        item("nichrome_mesh", img -> {
            for (int x = 2; x < 14; x++) for (int y = 2; y < 14; y++) {
                if ((x + y) % 3 == 0 || (x - y + 15) % 3 == 0) img.setRGB(x, y, argb(NICHROME));
            }
        });
        item("thermoelectric_pad", img -> {
            fill(img, 2, 4, 13, 12, 0xE8E4D8);
            for (int x = 3; x < 13; x += 2) for (int y = 5; y < 12; y += 2) img.setRGB(x, y, argb((x + y) % 4 == 0 ? COPPER : 0x4A6A8A));
            fill(img, 0, 6, 1, 6, 0xC03030);
            fill(img, 0, 9, 1, 9, 0x303030);
        });
        item("regulator", img -> {
            fill(img, 2, 4, 13, 12, GREEN_BOARD);
            fill(img, 4, 6, 7, 9, 0x1A1A1A);
            fill(img, 9, 5, 11, 10, 0x3A6EA8);
            fill(img, 3, 11, 12, 11, COPPER);
        });
        item("image_tube", img -> {
            fill(img, 3, 5, 12, 10, GLASS);
            fill(img, 3, 5, 3, 10, 0x8FB4C2);
            fill(img, 10, 6, 11, 9, 0x2F8F2F);
            fill(img, 13, 6, 14, 9, IRON_DARK);
        });
        item("pulse_coil", img -> {
            ring(img, 7.5, 7.5, 3.5, 6.5, 0x9A4A26);
            ring(img, 7.5, 7.5, 4.5, 5.0, 0x7A3418);
            disc(img, 7.5, 7.5, 2.0, IRON_DARK);
        });
        item("exo_frame", img -> {
            fill(img, 3, 2, 4, 14, TITANIUM);
            fill(img, 11, 2, 12, 14, TITANIUM);
            fill(img, 3, 2, 12, 3, TITANIUM);
            fill(img, 5, 8, 10, 8, ALUMINUM);
        });
        item("joint_actuator", img -> {
            disc(img, 7.5, 7.5, 5.0, IRON_DARK);
            disc(img, 7.5, 7.5, 3.5, 0x3C4A6E);
            gear(img, 7.5, 7.5, 2.0, BRASS);
            fill(img, 12, 6, 15, 9, TITANIUM);
        });
    }

    private static void cell(String name, int body, int metal, int band) throws IOException {
        item(name, img -> {
            fill(img, 3, 3, 12, 14, body);
            fill(img, 3, 3, 12, 3, shadeRgb(body, 30));
            fill(img, 4, 7, 11, 10, band);
            fill(img, 4, 8, 11, 9, metal);
            fill(img, 4, 1, 6, 2, 0xC03030);
            fill(img, 9, 1, 11, 2, 0x303030);
            fill(img, 4, 12, 4 + 2, 12, BLUE);
        });
    }

    private static void lining(String name, int mesh, int glow) throws IOException {
        item(name, img -> {
            fill(img, 2, 3, 13, 13, LEATHER);
            fill(img, 3, 4, 12, 12, shadeRgb(LEATHER, 25));
            for (int x = 4; x < 12; x += 2) for (int y = 5; y < 12; y += 2) img.setRGB(x, y, argb(mesh));
            fill(img, 6, 13, 9, 14, glow);
        });
    }

    private static void bit(String name, int metal) throws IOException {
        item(name, img -> {
            for (int i = 0; i < 10; i++) {
                int x = 3 + i, y = 12 - i;
                img.setRGB(x, y, argb(metal));
                img.setRGB(x + 1, y, argb(shadeRgb(metal, -30)));
                if (i % 2 == 0) img.setRGB(x, y - 1, argb(shadeRgb(metal, 40)));
            }
            fill(img, 2, 12, 4, 14, IRON_DARK);
        });
    }

    private static void gear(BufferedImage img, double cx, double cy, double r, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            double d = Math.hypot(x - cx, y - cy);
            double a = Math.atan2(y - cy, x - cx);
            double edge = r + (Math.cos(a * 8) > 0.2 ? 0.8 : 0.0);
            if (d < edge && d > r * 0.35) img.setRGB(x, y, argb(colour));
        }
    }

    private static void item(String name, Draw draw) throws IOException {
        if (!java.util.Set.of("lead_acid_pack", "nickel_iron_cell", "silver_zinc_cell",
                "heated_lining", "cooled_lining", "steel_drill_bit",
                "tungsten_steel_drill_bit", "saw_chain", "battery_acid", "potash",
                "cell_case", "toggle_switch", "device_housing", "reduction_gear", "chuck",
                "saw_bar", "strap", "nichrome_mesh", "thermoelectric_pad", "regulator",
                "image_tube", "pulse_coil", "exo_frame", "joint_actuator").contains(name)) {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            draw.on(img);
            write(img, ITEM_TEX.resolve(name + ".png"));
        }
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

    // ------------------------------------------------------------------ blocks

    private static void blocks() throws IOException {
        text(ASSETS.resolve("models/block/assembly_bench.json"), """
                {
                  "parent": "minecraft:block/cube",
                  "textures": {
                    "up": "%sassembly_bench_top",
                    "down": "%sassembly_bench_side",
                    "north": "%sassembly_bench_side",
                    "south": "%sassembly_bench_side",
                    "east": "%sassembly_bench_side",
                    "west": "%sassembly_bench_side",
                    "particle": "%sassembly_bench_side"
                  }
                }
                """.formatted(TEX, TEX, TEX, TEX, TEX, TEX, TEX));
        facingStates("assembly_bench", "hardwrought:block/assembly_bench", "");
        itemDefinition("assembly_bench", "hardwrought:block/assembly_bench");
        selfDrop("assembly_bench");

        Path blocks = ASSETS.resolve("models/block");
        for (boolean on : new boolean[] {false, true}) {
            String front = TEX + "charging_station_front" + (on ? "_on" : "");
            text(blocks.resolve("charging_station" + (on ? "_on" : "") + ".json"), """
                    {
                      "parent": "minecraft:block/block",
                      "textures": {
                        "front": "%s",
                        "top": "%scharging_station_top",
                        "side": "%sappliance_casing",
                        "particle": "%sappliance_casing"
                      },
                      "elements": [
                        {
                          "from": [1, 0, 2],
                          "to": [15, 10, 14],
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
                    """.formatted(front, TEX, TEX, TEX));
        }
        String[] facings = {"north", "east", "south", "west"};
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (boolean on : new boolean[] {false, true}) {
                variants.add("    \"facing=" + facings[i] + ",on=" + on + "\": {\n      \"model\": \"hardwrought:block/charging_station"
                        + (on ? "_on" : "") + "\"" + (i == 0 ? "" : ",\n      \"y\": " + i * 90) + "\n    }");
            }
        }
        text(ASSETS.resolve("blockstates/charging_station.json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
        itemDefinition("charging_station", "hardwrought:block/charging_station");
        selfDrop("charging_station");
    }

    private static void facingStates(String block, String model, String extra) throws IOException {
        String[] facings = {"north", "east", "south", "west"};
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            variants.add("    \"facing=" + facings[i] + extra + "\": {\n      \"model\": \"" + model + "\""
                    + (i == 0 ? "" : ",\n      \"y\": " + i * 90) + "\n    }");
        }
        text(ASSETS.resolve("blockstates/" + block + ".json"), "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n");
    }

    // ------------------------------------------------------------------ recipes

    private static void recipes() throws IOException {
        // Parts, at the nailed bench.
        shaped("cell_case", 2, new String[] {"P P", "PPP"}, "P", "hardwrought:plastic_sheet");
        shaped("toggle_switch", 2, new String[] {"L", "C", "P"}, "L", "minecraft:lever", "C", "hardwrought:copper_plate",
                "P", "hardwrought:porcelain");
        shaped("device_housing", 1, new String[] {"APA", "A A"}, "A", "hardwrought:aluminum_plate", "P", "hardwrought:plastic_sheet");
        shaped("reduction_gear", 1, new String[] {"GSG"}, "G", "hardwrought:fine_gear", "S", "hardwrought:steel_plate");
        shaped("chuck", 1, new String[] {"RRR", " G "}, "R", "hardwrought:steel_rod", "G", "hardwrought:fine_gear");
        shaped("saw_bar", 1, new String[] {"  S", " S ", "S  "}, "S", "hardwrought:steel_plate");
        shaped("strap", 2, new String[] {"LNL"}, "L", "minecraft:leather", "N", "minecraft:iron_nugget");
        shaped("nichrome_mesh", 1, new String[] {"NNN", "N N", "NNN"}, "N", "hardwrought:nichrome_wire");
        shaped("thermoelectric_pad", 1, new String[] {"CWC", "NWN"}, "C", "hardwrought:copper_plate", "W", "hardwrought:silicon_wafer",
                "N", "hardwrought:nickel_plate");
        shaped("regulator", 1, new String[] {"RTR", " C "}, "R", "hardwrought:resistor", "T", "hardwrought:transistor",
                "C", "hardwrought:basic_circuit");
        shaped("image_tube", 1, new String[] {"GSC"}, "G", "hardwrought:glass_tube", "S", "hardwrought:silver_plate",
                "C", "hardwrought:capacitor");
        shaped("pulse_coil", 1, new String[] {"PWP", " C "}, "P", "hardwrought:electrical_steel_plate", "W", "hardwrought:winding",
                "C", "hardwrought:capacitor");
        shaped("exo_frame", 1, new String[] {"T T", "TAT", "T T"}, "T", "hardwrought:titanium_plate", "A", "hardwrought:aluminum_rod");
        shaped("joint_actuator", 1, new String[] {"BMB", " G "}, "B", "hardwrought:bearing", "M", "hardwrought:electric_motor",
                "G", "hardwrought:reduction_gear");
        shaped("steel_drill_bit", 1, new String[] {"P", "R", "R"}, "P", "hardwrought:steel_plate", "R", "hardwrought:steel_rod");
        shaped("tungsten_steel_drill_bit", 1, new String[] {"P", "R", "R"}, "P", "hardwrought:tungsten_steel_plate",
                "R", "hardwrought:tungsten_steel_rod");
        shaped("saw_chain", 1, new String[] {"RPR", "P P", "RPR"}, "R", "hardwrought:steel_rod", "P", "hardwrought:steel_plate");
        shaped("charging_station", 1, new String[] {"PWP", "RCR", "PPP"}, "P", "hardwrought:iron_plate", "W", "hardwrought:winding",
                "R", "hardwrought:relay", "C", "hardwrought:basic_circuit");
        shaped("assembly_bench", 1, new String[] {"SSS", "RNR", "R R"}, "S", "hardwrought:steel_plate", "N", "hardwrought:nailed_workbench",
                "R", "hardwrought:steel_rod");
        smelting("potash", "minecraft:charcoal");
        // At the assembly bench: the cells.
        shaped("lead_acid_pack", 1, new String[] {"LAL", "LCL"}, "L", "hardwrought:lead_plate", "A", "hardwrought:battery_acid",
                "C", "hardwrought:cell_case");
        shaped("nickel_iron_cell", 1, new String[] {"NPI", "NCI"}, "N", "hardwrought:nickel_plate", "P", "hardwrought:potash",
                "I", "hardwrought:iron_plate", "C", "hardwrought:cell_case");
        shaped("silver_zinc_cell", 1, new String[] {"SPZ", "SCZ"}, "S", "hardwrought:silver_plate", "P", "minecraft:paper",
                "Z", "hardwrought:zinc_plate", "C", "hardwrought:cell_case");
        // ...and the devices.
        shaped("headlamp", 1, new String[] {"SRS", "HLT"}, "S", "hardwrought:strap", "R", "hardwrought:reflector",
                "H", "hardwrought:device_housing", "L", "hardwrought:light_bulb", "T", "hardwrought:toggle_switch");
        shaped("night_vision_goggles", 1, new String[] {"I I", "SOS", "HTH"}, "I", "hardwrought:image_tube", "S", "hardwrought:strap",
                "O", "hardwrought:quartz_oscillator", "H", "hardwrought:device_housing", "T", "hardwrought:toggle_switch");
        shaped("electric_drill", 1, new String[] {"CGM", "HTH"}, "C", "hardwrought:chuck", "G", "hardwrought:reduction_gear",
                "M", "hardwrought:electric_motor", "H", "hardwrought:device_housing", "T", "hardwrought:toggle_switch");
        shaped("chainsaw", 1, new String[] {"BMH", "GTH"}, "B", "hardwrought:saw_bar", "M", "hardwrought:electric_motor",
                "H", "hardwrought:device_housing", "G", "hardwrought:reduction_gear", "T", "hardwrought:toggle_switch");
        shaped("ore_scanner", 1, new String[] {"PCP", "HTH"}, "P", "hardwrought:pulse_coil", "C", "hardwrought:advanced_circuit",
                "H", "hardwrought:device_housing", "T", "hardwrought:toggle_switch");
        shaped("heated_lining", 1, new String[] {"NRN", "LLL"}, "N", "hardwrought:nichrome_mesh", "R", "hardwrought:regulator",
                "L", "minecraft:leather");
        shaped("cooled_lining", 1, new String[] {"PRP", "LLL"}, "P", "hardwrought:thermoelectric_pad", "R", "hardwrought:regulator",
                "L", "minecraft:leather");
        shaped("exoskeleton", 1, new String[] {"FCF", "JRJ", "S S"}, "F", "hardwrought:exo_frame", "C", "hardwrought:advanced_circuit",
                "J", "hardwrought:joint_actuator", "R", "hardwrought:regulator", "S", "hardwrought:strap");
    }

    // ------------------------------------------------------------------ data

    private static void data() throws IOException {
        StringBuilder products = new StringBuilder();
        for (String name : ASSEMBLED) products.append(products.isEmpty() ? "" : ",\n").append("    \"hardwrought:").append(name).append('"');
        text(DATA.resolve("tags/item/assembly_products.json"), "{\n  \"values\": [\n" + products + "\n  ]\n}\n");
        text(DATA.resolve("hardwrought/item_weights/carried_power.json"), """
                {
                  "weights": {
                    "hardwrought:lead_acid_pack": 2.5,
                    "hardwrought:nickel_iron_cell": 2.0,
                    "hardwrought:silver_zinc_cell": 0.6,
                    "hardwrought:headlamp": 0.4,
                    "hardwrought:night_vision_goggles": 0.9,
                    "hardwrought:exoskeleton": 9.0,
                    "hardwrought:electric_drill": 3.0,
                    "hardwrought:chainsaw": 5.0,
                    "hardwrought:ore_scanner": 1.5,
                    "hardwrought:heated_lining": 0.8,
                    "hardwrought:cooled_lining": 0.9
                  }
                }
                """);
    }

    // ------------------------------------------------------------------ helpers

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
        for (int x = Math.max(0, x0); x <= Math.min(15, x1); x++) for (int y = Math.max(0, y0); y <= Math.min(15, y1); y++) img.setRGB(x, y, argb(colour));
    }

    private static void disc(BufferedImage img, double cx, double cy, double r, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if (Math.hypot(x - cx, y - cy) < r) img.setRGB(x, y, argb(colour));
    }

    private static void ring(BufferedImage img, double cx, double cy, double inner, double outer, int colour) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            double d = Math.hypot(x - cx, y - cy);
            if (d < outer && d > inner) img.setRGB(x, y, argb(colour));
        }
    }

    private static int shade(int colour, int by) {
        return argb(shadeRgb(colour, by));
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
        text(DATA.resolve("recipe/" + name + ".json"), "{\n  \"type\": \"minecraft:crafting_shaped\",\n  \"category\": \"equipment\",\n"
                + "  \"key\": {\n" + keys + "\n  },\n  \"pattern\": [\n" + rows + "\n  ],\n  \"result\": {\n    \"id\": \"hardwrought:"
                + name + "\"" + (count > 1 ? ",\n    \"count\": " + count : "") + "\n  }\n}\n");
    }

    private static void smelting(String name, String from) throws IOException {
        text(DATA.resolve("recipe/" + name + "_from_smelting.json"), """
                {
                  "type": "minecraft:smelting",
                  "category": "misc",
                  "cookingtime": 200,
                  "experience": 0.1,
                  "ingredient": "%s",
                  "result": {
                    "id": "hardwrought:%s"
                  }
                }
                """.formatted(from, name));
    }

    private static void text(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
