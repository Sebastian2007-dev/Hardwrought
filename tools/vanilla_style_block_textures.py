"""Build purpose-specific, vanilla-adjacent 16x16 surfaces for Hardwrought blocks.

The shapes already read like Minecraft models.  This pass replaces only the borrowed block
surfaces that made a machine look as if it were assembled from wool, pistons or full iron blocks.
Ordinary dirt and the actual wood species on workbenches stay vanilla on purpose.

Run from the repository root after the machinery/oil/forge model generators:
    python tools/vanilla_style_block_textures.py
"""
import io
import json
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/hardwrought"
MODEL_ROOT = ASSETS / "models/block"
OUT = ASSETS / "textures/block/hardwrought"
JAR = Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar"


def vanilla(name):
    with zipfile.ZipFile(JAR) as jar:
        data = jar.read(f"assets/minecraft/textures/block/{name}.png")
    return Image.open(io.BytesIO(data)).convert("RGBA").crop((0, 0, 16, 16))


def recolour(image, palette):
    out = image.copy()
    pixels = out.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = pixels[x, y]
            if not a:
                continue
            # Vanilla's block palette lives mostly in the middle values. Lift the source before
            # palette lookup so these surfaces stay readable in caves without looking emissive.
            light = min(.999, (r * 3 + g * 6 + b) / 2550 * 1.15 + .08)
            pixels[x, y] = palette[min(len(palette) - 1, int(light * len(palette)))] + (a,)
    return out


def lines(image, entries):
    draw = ImageDraw.Draw(image)
    for xy, colour in entries:
        draw.line(xy, fill=colour)
    return image


def rivet(draw, x, y):
    draw.point((x, y), fill=(205, 208, 199, 255))
    draw.point((x + 1, y), fill=(75, 78, 76, 255))
    draw.point((x, y + 1), fill=(91, 94, 91, 255))
    draw.point((x + 1, y + 1), fill=(43, 45, 45, 255))


def timber(side=True, dark=False):
    palette = ((72, 49, 28), (105, 72, 39), (143, 99, 54), (181, 132, 76), (218, 169, 106))
    if dark:
        palette = tuple(tuple(int(c * .82) for c in colour) for colour in palette)
    base = recolour(vanilla("stripped_oak_log" if side else "stripped_oak_log_top"), palette)
    if side:
        return lines(base, [((2, 1, 2, 14), (83, 55, 30, 255)),
                            ((8, 0, 8, 15), (207, 153, 91, 255)),
                            ((13, 2, 13, 13), (98, 64, 35, 255))])
    draw = ImageDraw.Draw(base)
    draw.rectangle((4, 4, 11, 11), outline=(54, 37, 24, 255))
    draw.rectangle((6, 6, 9, 9), outline=(126, 84, 45, 255))
    draw.point((7, 7), fill=(37, 28, 21, 255))
    return base


def anvil_wood(top=False):
    image = timber(not top, dark=True)
    draw = ImageDraw.Draw(image)
    if top:
        # Compressed fibres and old hammer dents make the working face read immediately.
        draw.rectangle((2, 2, 13, 13), outline=(51, 35, 24, 255))
        for x, y in ((4, 5), (10, 4), (7, 9), (11, 11), (3, 12)):
            draw.point((x, y), fill=(28, 24, 20, 255))
            draw.point((x + 1, y), fill=(112, 78, 46, 255))
    else:
        draw.line((3, 4, 6, 3), fill=(154, 105, 58, 255))
        draw.line((10, 11, 14, 9), fill=(42, 29, 20, 255))
    return image


def boards(dark=False, iron=False):
    palette = ((69, 45, 25), (101, 67, 36), (139, 94, 51), (178, 128, 74), (216, 169, 108))
    if dark:
        palette = tuple(tuple(int(c * .84) for c in colour) for colour in palette)
    image = recolour(vanilla("oak_planks"), palette)
    draw = ImageDraw.Draw(image)
    draw.line((0, 5, 15, 5), fill=(80, 51, 27, 255))
    draw.line((0, 11, 15, 11), fill=(80, 51, 27, 255))
    if iron:
        for x in (1, 13):
            draw.rectangle((x, 0, x + 1, 15), fill=(57, 59, 57, 255))
        rivet(draw, 1, 2)
        rivet(draw, 13, 12)
    return image


def gearbox_end():
    image = boards(dark=False, iron=False)
    draw = ImageDraw.Draw(image)
    # Broad straps, corner plates and a central bearing copy the readable reference silhouette.
    draw.rectangle((0, 1, 15, 3), fill=(111, 120, 124, 255))
    draw.line((0, 1, 15, 1), fill=(205, 211, 207, 255))
    draw.rectangle((0, 12, 15, 14), fill=(72, 78, 81, 255))
    draw.line((0, 12, 15, 12), fill=(163, 171, 169, 255))
    for x, y in ((1, 1), (13, 1), (1, 12), (13, 12)):
        rivet(draw, x, y)
    draw.ellipse((3, 3, 12, 12), fill=(75, 82, 86, 255), outline=(210, 216, 211, 255))
    draw.ellipse((5, 5, 10, 10), fill=(20, 24, 26, 255), outline=(126, 135, 137, 255))
    return image


def gearbox_side_texture():
    image = boards(dark=False, iron=False)
    draw = ImageDraw.Draw(image)
    draw.line((0, 0, 0, 15), fill=(88, 93, 94, 255))
    draw.line((15, 0, 15, 15), fill=(63, 68, 70, 255))
    for x, y in ((0, 2), (14, 12)):
        rivet(draw, x, y)
    return image


def crank_case(top=False, turning=False):
    image = boards(dark=False, iron=False)
    draw = ImageDraw.Draw(image)
    if top:
        draw.rectangle((4, 4, 11, 11), fill=(120, 82, 45, 255), outline=(69, 48, 29, 255))
        draw.rectangle((6, 6, 9, 9), fill=(57, 60, 59, 255), outline=(169, 174, 168, 255))
        if turning:
            draw.point((5, 4), fill=(224, 176, 105, 255))
            draw.point((11, 10), fill=(74, 51, 30, 255))
    else:
        draw.line((2, 0, 2, 15), fill=(96, 62, 32, 255))
        draw.line((13, 0, 13, 15), fill=(205, 151, 88, 255))
    return image


def plain_iron(end=False):
    image = recolour(vanilla("iron_block"),
                     ((77, 82, 84), (112, 119, 120), (151, 158, 157), (190, 196, 191), (226, 227, 218)))
    draw = ImageDraw.Draw(image)
    if end:
        draw.rectangle((3, 3, 12, 12), fill=(91, 98, 100, 255), outline=(210, 214, 207, 255))
        draw.rectangle((6, 6, 9, 9), fill=(27, 31, 32, 255))
    else:
        draw.line((0, 2, 15, 2), fill=(218, 221, 214, 255))
        draw.line((0, 13, 15, 13), fill=(72, 78, 80, 255))
    return image


def gear():
    image = recolour(vanilla("spruce_planks"),
                     ((74, 47, 24), (109, 71, 37), (151, 104, 57), (193, 143, 85), (229, 184, 119)))
    draw = ImageDraw.Draw(image)
    draw.line((0, 8, 15, 8), fill=(70, 43, 23, 255))
    draw.line((8, 0, 8, 15), fill=(164, 114, 64, 255))
    draw.rectangle((6, 6, 10, 10), outline=(49, 32, 20, 255))
    draw.line((8, 6, 8, 10), fill=(211, 165, 99, 255))
    return image


def wet_wood():
    image = recolour(vanilla("spruce_planks"),
                     ((43, 43, 34), (61, 67, 50), (79, 91, 68), (101, 116, 85), (130, 142, 101)))
    draw = ImageDraw.Draw(image)
    draw.line((2, 2, 13, 2), fill=(105, 119, 96, 255))
    # Strong blue-gray runs are deliberate: at 16 px a subtle tint does not read as wet.
    draw.line((4, 1, 4, 7), fill=(82, 139, 162, 255))
    draw.point((3, 7), fill=(119, 179, 197, 255))
    draw.line((12, 8, 12, 14), fill=(65, 116, 140, 255))
    draw.point((11, 14), fill=(112, 169, 187, 255))
    return image


def canvas():
    image = recolour(vanilla("white_wool"),
                     ((83, 76, 63), (124, 114, 93), (165, 153, 127), (201, 191, 165), (225, 216, 191)))
    draw = ImageDraw.Draw(image)
    draw.line((7, 0, 7, 15), fill=(103, 83, 58, 255))
    for y in range(1, 16, 3):
        draw.point((6, y), fill=(222, 197, 146, 255))
        draw.point((8, y + 1 if y < 15 else y), fill=(83, 65, 45, 255))
    return image


def leather():
    image = recolour(vanilla("brown_wool"),
                     ((58, 28, 22), (91, 42, 31), (126, 57, 40), (161, 79, 52), (195, 108, 69)))
    draw = ImageDraw.Draw(image)
    for x in range(1, 16, 3):
        draw.point((x, 1), fill=(190, 137, 79, 255))
        draw.point((x + 1 if x < 15 else x, 14), fill=(74, 42, 29, 255))
    draw.line((0, 2, 15, 2), fill=(55, 30, 22, 255))
    draw.line((0, 13, 15, 13), fill=(137, 86, 51, 255))
    return image


def folded_leather():
    image = recolour(vanilla("brown_wool"),
                     ((66, 29, 24), (101, 42, 33), (139, 57, 42), (177, 79, 55), (211, 111, 73)))
    draw = ImageDraw.Draw(image)
    # Alternating light and dark wedges remain visible on the narrow bellows element.
    for y in range(1, 16, 4):
        draw.line((0, y, 7, min(15, y + 2)), fill=(218, 126, 82, 255))
        draw.line((15, y, 8, min(15, y + 2)), fill=(89, 35, 29, 255))
    draw.line((0, 0, 15, 0), fill=(232, 147, 96, 255))
    draw.line((0, 15, 15, 15), fill=(72, 28, 24, 255))
    return image


def iron_brace():
    image = recolour(vanilla("iron_block"),
                     ((63, 68, 70), (94, 101, 103), (132, 140, 141), (174, 181, 179), (219, 221, 213)))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), outline=(45, 48, 49, 255))
    draw.line((1, 14, 14, 1), fill=(86, 95, 99, 255), width=2)
    draw.line((1, 1, 14, 14), fill=(185, 193, 191, 255))
    rivet(draw, 2, 2)
    rivet(draw, 12, 12)
    return image


def dark_steel():
    image = recolour(vanilla("anvil"),
                     ((37, 41, 44), (55, 61, 64), (79, 86, 89), (110, 118, 120), (151, 157, 155)))
    draw = ImageDraw.Draw(image)
    # Alternating diagonals suggest the spiral flutes shown on the reference drill.
    for y in range(-4, 17, 5):
        draw.line((0, y, 15, y + 8), fill=(179, 187, 187, 255), width=2)
        draw.line((0, y + 2, 15, y + 10), fill=(48, 54, 58, 255))
    return image


def crusher_jaw():
    image = recolour(vanilla("grindstone_side"),
                     ((58, 58, 55), (86, 85, 80), (118, 116, 108), (151, 148, 137), (188, 182, 167)))
    draw = ImageDraw.Draw(image)
    for y in (2, 6, 10, 14):
        draw.polygon(((0, y), (5, y - 2), (10, y), (15, y - 2)), fill=(55, 54, 51, 255))
        draw.line((0, y + 1, 15, y - 1), fill=(142, 137, 124, 255))
    return image


def copper(pipe=False):
    image = recolour(vanilla("cut_copper" if pipe else "copper_block"),
                     ((84, 45, 32), (126, 60, 39), (172, 81, 47), (215, 112, 65), (246, 158, 101)))
    draw = ImageDraw.Draw(image)
    if pipe:
        draw.line((0, 3, 15, 3), fill=(74, 45, 35, 255))
        draw.line((0, 12, 15, 12), fill=(219, 135, 80, 255))
    else:
        draw.line((0, 7, 15, 7), fill=(105, 49, 34, 255))
        draw.line((0, 8, 15, 8), fill=(238, 137, 80, 255))
        for x, y in ((3, 3), (11, 2), (7, 8), (13, 12), (2, 13)):
            draw.point((x, y), fill=(232, 151, 93, 255))
            draw.point((x + 1, y), fill=(92, 48, 34, 255))
    return image


def masonry(base_name, pale=False, soot=False):
    if pale:
        palette = ((104, 93, 72), (142, 128, 98), (178, 162, 126), (211, 196, 158), (239, 228, 194))
    elif soot:
        palette = ((29, 30, 32), (44, 45, 46), (62, 61, 59), (82, 77, 72), (105, 91, 80))
    else:
        palette = ((73, 38, 30), (108, 50, 36), (147, 66, 43), (185, 86, 55), (219, 118, 75))
    image = recolour(vanilla(base_name), palette)
    if not pale:
        draw = ImageDraw.Draw(image)
        draw.point((2, 1), fill=(38, 35, 34, 255))
        draw.point((13, 6), fill=(55, 47, 43, 255))
        draw.point((7, 12), fill=(35, 33, 32, 255))
    return image


def coal(hot=False):
    base = recolour(vanilla("coal_block"),
                    ((16, 18, 20), (27, 29, 32), (42, 44, 46), (61, 62, 62), (86, 84, 79)))
    draw = ImageDraw.Draw(base)
    if hot:
        for x, y in ((2, 3), (6, 2), (10, 5), (13, 2), (4, 9), (8, 12), (13, 11)):
            draw.point((x, y), fill=(255, 214, 92, 255))
            draw.point((x + 1, y), fill=(232, 91, 25, 255))
            if y < 15:
                draw.point((x, y + 1), fill=(122, 37, 20, 255))
    else:
        for x, y in ((2, 4), (7, 2), (12, 7), (4, 12), (10, 13)):
            draw.point((x, y), fill=(119, 113, 104, 255))
    return base


def animated_coal():
    frames = []
    embers = [((2, 3), (6, 2), (10, 5), (13, 2), (4, 9), (8, 12), (13, 11)),
              ((3, 3), (6, 3), (11, 5), (13, 3), (5, 9), (8, 11), (12, 11)),
              ((3, 4), (7, 3), (11, 6), (12, 2), (5, 10), (9, 11), (12, 10)),
              ((2, 4), (7, 2), (10, 6), (12, 3), (4, 10), (9, 12), (13, 10))]
    for points in embers:
        frame = coal(False)
        draw = ImageDraw.Draw(frame)
        for x, y in points:
            draw.point((x, y), fill=(255, 229, 112, 255))
            draw.point((x + 1, y), fill=(255, 125, 31, 255))
            if y < 15:
                draw.point((x, y + 1), fill=(167, 49, 21, 255))
        frames.append(frame)
    image = Image.new("RGBA", (16, 64))
    for index, frame in enumerate(frames):
        image.paste(frame, (0, index * 16))
    return image


def make_textures():
    OUT.mkdir(parents=True, exist_ok=True)
    textures = {
        "shaft_side": timber(True), "shaft_end": timber(False),
        "wooden_anvil_side": anvil_wood(False), "wooden_anvil_top": anvil_wood(True),
        "dark_handle": timber(True, True), "machine_boards": boards(),
        "machine_boards_dark": boards(True), "gearbox_side": gearbox_side_texture(),
        "gearbox_end": gearbox_end(), "crank_case_side": crank_case(),
        "crank_case_top": crank_case(top=True), "crank_case_turning": crank_case(top=True, turning=True),
        "bellows_nozzle_side": plain_iron(), "bellows_nozzle_end": plain_iron(end=True),
        "gear_wood": gear(), "wet_paddle": wet_wood(),
        "sailcloth": canvas(), "leather_belt": leather(), "bellows_leather": folded_leather(),
        "iron_brace": iron_brace(), "drill_steel": dark_steel(), "crusher_jaw": crusher_jaw(),
        "hammered_copper": copper(False), "copper_pipe": copper(True),
        "forge_brick": masonry("bricks"), "refractory_masonry": masonry("mud_bricks", pale=True),
        "forge_base": masonry("cobblestone", soot=True), "soot": masonry("coal_block", soot=True),
        "coal_bed": coal(False), "coal_bed_hot": animated_coal(),
    }
    for name, image in textures.items():
        image.save(OUT / f"{name}.png")
    (OUT / "coal_bed_hot.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 4}}, indent=2) + "\n",
                                                  encoding="utf-8")

    # A deterministic contact sheet is useful when tuning the pack later.
    names = list(textures)
    rows = (len(names) + 5) // 6
    sheet = Image.new("RGBA", (16 * 6, 16 * rows), (0, 0, 0, 0))
    for index, name in enumerate(names):
        sheet.paste(textures[name].crop((0, 0, 16, 16)), ((index % 6) * 16, (index // 6) * 16))
    source = ROOT / "art_source/machinery"
    source.mkdir(parents=True, exist_ok=True)
    sheet.save(source / "vanilla_style_surfaces.png")


def replace_in(relative, replacements):
    path = MODEL_ROOT / relative
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    for old, new in replacements.items():
        text = text.replace(f'"{old}"', f'"hardwrought:block/hardwrought/{new}"')
    path.write_text(text, encoding="utf-8")


def rewrite_models():
    replace_in("bellows.json", {"minecraft:block/oak_planks": "machine_boards",
                                 "minecraft:block/brown_wool": "bellows_leather",
                                 "minecraft:block/iron_block": "iron_brace"})
    replace_in("belt_strip.json", {"minecraft:block/brown_wool": "leather_belt"})
    for name in ("cogwheel.json", "cogwheel_gear.json", "large_cogwheel.json", "large_cogwheel_gear.json"):
        replace_in(name, {"minecraft:block/stripped_oak_log": "shaft_side",
                          "minecraft:block/spruce_planks": "gear_wood"})
    for name in ("shaft.json", "shaft_bar.json"):
        replace_in(name, {"minecraft:block/stripped_oak_log": "shaft_side",
                          "minecraft:block/stripped_oak_log_top": "shaft_end"})
    for name in ("crank_box.json", "crank_box_turning.json"):
        replace_in(name, {"minecraft:block/oak_planks": "gearbox_side",
                          "minecraft:block/stripped_oak_log": "shaft_side",
                          "minecraft:block/stripped_oak_log_top": "gearbox_end"})
    for name in ("hand_crank.json", "hand_crank_handle.json"):
        replace_in(name, {"minecraft:block/oak_planks": "machine_boards",
                          "minecraft:block/stripped_oak_log": "shaft_side",
                          "minecraft:block/stripped_oak_log_top": "shaft_end",
                          "minecraft:block/stripped_spruce_log": "dark_handle"})
    replace_in("gearbox.json", {"minecraft:block/barrel_side": "gearbox_side",
                                 "minecraft:block/barrel_bottom": "gearbox_end"})
    replace_in("water_wheel_rim.json", {"minecraft:block/stripped_oak_log": "shaft_side",
                                         "minecraft:block/oak_planks": "machine_boards",
                                         "minecraft:block/spruce_planks": "wet_paddle"})
    replace_in("windmill_sails.json", {"minecraft:block/dark_oak_log": "dark_handle",
                                        "minecraft:block/white_wool": "sailcloth"})
    replace_in("windmill.json", {"minecraft:block/dark_oak_planks": "gearbox_side"})
    replace_in("starter_crusher.json", {"minecraft:block/stripped_oak_log": "shaft_side",
                                         "minecraft:block/oak_planks": "machine_boards",
                                         "minecraft:block/grindstone_side": "crusher_jaw",
                                         "minecraft:block/cobblestone": "forge_base"})
    replace_in("drying_rack.json", {"minecraft:block/stripped_oak_log": "shaft_side",
                                     "minecraft:block/oak_log": "dark_handle"})
    replace_in("drilling_rig.json", {"minecraft:block/spruce_planks": "machine_boards_dark",
                                      "minecraft:block/iron_block": "iron_brace",
                                      "minecraft:block/anvil": "drill_steel"})
    replace_in("still.json", {"minecraft:block/copper_block": "hammered_copper",
                               "minecraft:block/cut_copper": "copper_pipe"})
    replace_in("ore_drill.json", {"minecraft:block/iron_block": "iron_brace",
                                   "minecraft:block/piston_top": "drill_steel",
                                   "minecraft:block/piston_bottom": "drill_steel"})
    for name in ("wooden_anvil.json", "wooden_anvil_cracked.json", "wooden_anvil_split.json"):
        replace_in(name, {"minecraft:block/oak_log": "wooden_anvil_side",
                          "minecraft:block/stripped_oak_log_top": "wooden_anvil_top"})

    for path in (MODEL_ROOT / "gas_pipe").glob("*.json"):
        relative = path.relative_to(MODEL_ROOT).as_posix()
        replace_in(relative, {"minecraft:block/cut_copper": "copper_pipe",
                              "minecraft:block/coal_block": "soot"})

    forge_files = list(MODEL_ROOT.glob("forge*.json")) + list((MODEL_ROOT / "forge").glob("*.json"))
    forge_files += list((MODEL_ROOT / "forge_hood").glob("*.json"))
    for path in forge_files:
        relative = path.relative_to(MODEL_ROOT).as_posix()
        replace_in(relative, {"minecraft:block/coal_block": "coal_bed" if "bed_" in path.name else "soot",
                              "minecraft:block/magma": "coal_bed_hot"})


def verify():
    unresolved = []
    for path in MODEL_ROOT.rglob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        for key, value in data.get("textures", {}).items():
            if isinstance(value, str) and value.startswith("minecraft:block/"):
                relative = path.relative_to(MODEL_ROOT).as_posix()
                forge_material = relative.startswith("forge") and value in {
                    "minecraft:block/bricks", "minecraft:block/mud_bricks", "minecraft:block/cobblestone"
                }
                if not (relative.startswith("hewn_workbench_") or relative.startswith("dirt_slab")
                        or forge_material):
                    unresolved.append(f"{relative}: {key}={value}")
    if unresolved:
        raise RuntimeError("Unexpected borrowed surfaces remain:\n" + "\n".join(unresolved))


if __name__ == "__main__":
    make_textures()
    rewrite_models()
    verify()
    print("wrote purpose-specific vanilla-style block textures")
