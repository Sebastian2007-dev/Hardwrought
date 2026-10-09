"""Writes the textures, models, blockstates, item definitions, loot tables, recipes and names of
section 76: dynamo, insulator, mast, battery, electric lamp, basic crusher, wire, cable and meter.

The models are written here by hand as boxes; the strung wire itself is a one-block strand that the
renderer stretches piece by piece along the wire's sag.

Run from the repository root:  python tools/electricity_assets.py
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/hardwrought"
DATA = RES / "data/hardwrought"
MOD = "hardwrought"
TEX = f"{MOD}:block/{MOD}/"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def clamp(value):
    return max(0, min(255, int(value)))


def noisy(name, base, spread, seed, paint=None):
    """A 16 by 16 texture of one colour with a little grain, and whatever paint draws on top."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            d = rng.randint(-spread, spread)
            img.putpixel((x, y), (clamp(base[0] + d), clamp(base[1] + d), clamp(base[2] + d), 255))
    if paint:
        paint(img, ImageDraw.Draw(img), rng)
    path = ASSETS / f"textures/block/{MOD}/{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def windings(img, draw, rng):
    # Copper wound round the drum: a darker line between every two turns.
    for y in range(0, 16, 2):
        for x in range(16):
            r, g, b, a = img.getpixel((x, y))
            img.putpixel((x, y), (clamp(r * 0.72), clamp(g * 0.72), clamp(b * 0.72), a))
    for x in (0, 15):
        for y in range(16):
            img.putpixel((x, y), (92, 52, 34, 255))


def rivets(img, draw, rng):
    draw.rectangle([0, 0, 15, 15], outline=(52, 54, 60, 255))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), (150, 154, 162, 255))


def glaze(img, draw, rng):
    # A shine down one side, a shadow down the other.
    for y in range(16):
        img.putpixel((3, y), (250, 250, 246, 255))
        img.putpixel((12, y), (176, 176, 170, 255))
        img.putpixel((13, y), (160, 160, 154, 255))


def filament(colour):
    def paint(img, draw, rng):
        draw.line([(6, 11), (6, 6), (8, 4), (10, 6), (10, 11)], fill=colour)
    return paint


def cracks(img, draw, rng):
    draw.line([(2, 3), (6, 7), (5, 11), (9, 14)], fill=(20, 20, 22, 255))
    draw.line([(6, 7), (11, 5), (14, 8)], fill=(20, 20, 22, 255))
    draw.line([(11, 5), (12, 1)], fill=(20, 20, 22, 255))


def textures():
    noisy("dynamo_coil", (196, 110, 70), 10, 1, windings)
    noisy("dynamo_iron", (96, 100, 108), 7, 2, rivets)
    noisy("insulator_ceramic", (226, 226, 218), 5, 3, glaze)
    noisy("lamp_base", (120, 96, 54), 8, 4)
    noisy("lamp_glass_off", (150, 152, 150), 6, 5, filament((70, 62, 54, 255)))
    noisy("lamp_glass_dim", (214, 150, 78), 6, 6, filament((255, 214, 130, 255)))
    noisy("lamp_glass_on", (255, 238, 170), 5, 7, filament((255, 255, 240, 255)))
    noisy("lamp_glass_broken", (86, 86, 88), 8, 8, cracks)
    noisy("wire_copper", (190, 104, 64), 12, 9)
    noisy("wire_hot", (255, 150, 40), 20, 10)

    def item(name, paint):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        paint(img, ImageDraw.Draw(img))
        path = ASSETS / f"textures/item/{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)

    def coil(light, dark, width):
        def paint(img, draw):
            draw.ellipse([2, 3, 13, 13], outline=dark, width=width + 1)
            draw.ellipse([3, 4, 12, 12], outline=light, width=width)
            draw.line([(12, 11), (15, 14)], fill=light, width=width)
        return paint

    item("copper_wire", coil((214, 124, 78, 255), (132, 70, 44, 255), 1))
    item("copper_cable", coil((186, 100, 60, 255), (98, 50, 32, 255), 2))

    def meter(img, draw):
        draw.rectangle([2, 3, 13, 13], fill=(74, 52, 36, 255), outline=(42, 28, 20, 255))
        draw.rectangle([4, 5, 11, 9], fill=(232, 226, 204, 255))
        draw.line([(7, 9), (10, 6)], fill=(170, 30, 30, 255))
        img.putpixel((4, 12), (214, 124, 78, 255))
        img.putpixel((11, 12), (214, 124, 78, 255))
        draw.line([(4, 14), (4, 13)], fill=(214, 124, 78, 255))
        draw.line([(11, 14), (11, 13)], fill=(214, 124, 78, 255))

    item("voltmeter", meter)

    # The second step: battery, and the basic crusher's own faces.
    noisy("battery_lead", (112, 116, 124), 6, 11)

    def battery_top(img, draw, rng):
        draw.rectangle([0, 0, 15, 15], outline=(30, 30, 34, 255))
        for x in (4, 8, 12):
            draw.line([(x, 2), (x, 13)], fill=(34, 34, 38, 255))

    noisy("battery_top", (58, 58, 64), 5, 12, battery_top)
    for quarters in range(5):
        def side(img, draw, rng, quarters=quarters):
            draw.rectangle([0, 0, 15, 15], outline=(30, 30, 34, 255))
            # A gauge glass down the middle, full from the bottom up.
            draw.rectangle([6, 3, 9, 12], fill=(18, 20, 18, 255), outline=(150, 150, 150, 255))
            for step in range(quarters):
                y = 11 - step * 2
                colour = (210, 70, 50, 255) if quarters == 1 else (110, 220, 90, 255)
                draw.rectangle([7, y - 1, 8, y], fill=colour)
        noisy(f"battery_side_{quarters}", (58, 58, 64), 5, 13 + quarters, side)

    def crusher_front(img, draw, rng):
        rivets(img, draw, rng)
        draw.rectangle([4, 9, 11, 14], fill=(24, 24, 28, 255), outline=(60, 62, 68, 255))
        draw.rectangle([3, 3, 12, 6], fill=(190, 104, 64, 255), outline=(110, 58, 38, 255))
        for x in range(4, 12, 2):
            draw.line([(x, 4), (x, 5)], fill=(132, 70, 44, 255))

    noisy("basic_crusher_front", (96, 100, 108), 7, 20, crusher_front)

    def furnace_front(img, draw, rng):
        rivets(img, draw, rng)
        draw.rectangle([3, 4, 12, 12], fill=(30, 22, 20, 255), outline=(60, 62, 68, 255))
        for y in (6, 8, 10):
            draw.line([(4, y), (11, y)], fill=(255, 140, 40, 255))

    def saw_front(img, draw, rng):
        rivets(img, draw, rng)
        draw.rectangle([2, 9, 13, 13], fill=(24, 24, 28, 255), outline=(60, 62, 68, 255))
        draw.ellipse([4, 3, 11, 10], fill=(176, 180, 188, 255), outline=(70, 72, 78, 255))
        img.putpixel((7, 6), (60, 60, 64, 255))
        img.putpixel((8, 6), (60, 60, 64, 255))

    def press_front(img, draw, rng):
        rivets(img, draw, rng)
        draw.rectangle([3, 3, 12, 5], fill=(150, 154, 162, 255), outline=(60, 62, 68, 255))
        draw.rectangle([3, 10, 12, 12], fill=(150, 154, 162, 255), outline=(60, 62, 68, 255))
        draw.rectangle([7, 5, 8, 9], fill=(190, 104, 64, 255))

    def top(colour):
        def paint(img, draw, rng):
            rivets(img, draw, rng)
            draw.rectangle([3, 3, 12, 12], fill=colour, outline=(40, 40, 44, 255))
        return paint

    noisy("electric_furnace_front", (120, 84, 70), 8, 21, furnace_front)
    noisy("sawmill_front", (96, 100, 108), 7, 22, saw_front)
    noisy("plate_press_front", (96, 100, 108), 7, 23, press_front)
    noisy("electric_furnace_top", (120, 84, 70), 8, 24, top((60, 30, 24, 255)))
    noisy("sawmill_top", (96, 100, 108), 7, 25, top((150, 112, 70, 255)))
    noisy("plate_press_top", (96, 100, 108), 7, 26, top((170, 174, 182, 255)))


def box(frm, to, texture, **other):
    """A box of one texture; other faces by name, as in box(..., up="#top")."""
    return {"from": frm, "to": to, "faces": {face: {"texture": other.get(face, texture)}
                                             for face in ("north", "south", "east", "west", "up", "down")}}


def model(name, textures, elements):
    write(ASSETS / f"models/block/{name}.json",
          {"parent": "minecraft:block/block", "textures": textures, "elements": elements})


# How a model that stands on the ground is turned to stand out from each face.
STANDING = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90},
            "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}


def models():
    model("insulator", {"ceramic": TEX + "insulator_ceramic", "iron": TEX + "dynamo_iron", "copper": TEX + "wire_copper",
                        "particle": TEX + "insulator_ceramic"}, [
        box([6, 0, 6], [10, 1, 10], "#iron"),
        box([6.5, 1, 6.5], [9.5, 8, 9.5], "#ceramic"),
        box([5.5, 2, 5.5], [10.5, 3.5, 10.5], "#ceramic"),
        box([5.5, 5, 5.5], [10.5, 6.5, 10.5], "#ceramic"),
        box([7, 8, 7], [9, 9, 9], "#copper"),
    ])
    for look in ("off", "dim", "on", "broken"):
        model(f"electric_lamp_{look}", {"base": TEX + "lamp_base", "glass": TEX + f"lamp_glass_{look}",
                                        "particle": TEX + f"lamp_glass_{look}"}, [
            box([5, 0, 5], [11, 2, 11], "#base"),
            box([6.5, 2, 6.5], [9.5, 4, 9.5], "#base"),
            box([5, 4, 5], [11, 10, 11], "#glass"),
            box([6, 10, 6], [10, 11, 10], "#glass"),
        ])
    # The dynamo's axle lies north to south.
    model("dynamo", {"coil": TEX + "dynamo_coil", "iron": TEX + "dynamo_iron", "axle": TEX + "shaft_side",
                     "particle": TEX + "dynamo_iron"}, [
        box([0, 0, 0], [16, 2, 16], "#iron"),
        box([1, 2, 2], [15, 14, 14], "#coil"),
        box([0, 1.5, 1], [16, 14.5, 2], "#iron"),
        box([0, 1.5, 14], [16, 14.5, 15], "#iron"),
        box([6.5, 6.5, 0], [9.5, 9.5, 16], "#axle"),
        box([4, 14, 4], [12, 16, 12], "#iron"),
    ])
    # The coil stands on the ground; turned to stand out from each face like the insulator.
    model("copper_coil", {"coil": TEX + "dynamo_coil", "iron": TEX + "dynamo_iron", "copper": TEX + "wire_copper",
                          "particle": TEX + "dynamo_coil"}, [
        box([5, 0, 5], [11, 1, 11], "#iron"),
        box([5.5, 1, 5.5], [10.5, 6, 10.5], "#coil", up="#iron", down="#iron"),
        box([5, 6, 5], [11, 7, 11], "#iron"),
        box([7, 7, 7], [9, 8, 9], "#copper"),
    ])
    write(ASSETS / "blockstates/copper_coil.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/copper_coil", **turn} for facing, turn in STANDING.items()}})
    write(ASSETS / "items/copper_coil.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:block/copper_coil"}})
    write(DATA / "loot_table/blocks/copper_coil.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:copper_coil"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    shaped("copper_coil", [" W ", "WIW", " W "], {"W": f"{MOD}:copper_wire", "I": "minecraft:iron_nugget"}, 2)
    for name, texture, width in (("wire_strand", "wire_copper", 0.8), ("wire_strand_hot", "wire_hot", 0.8),
                                 ("cable_strand", "wire_copper", 1.8), ("cable_strand_hot", "wire_hot", 1.8)):
        low, high = 8 - width / 2, 8 + width / 2
        model(name, {"wire": TEX + texture, "particle": TEX + texture}, [box([0, low, low], [16, high, high], "#wire")])
        write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})


def second_step():
    wood = {"wood": "minecraft:block/spruce_log", "end": "minecraft:block/spruce_log_top",
            "ceramic": TEX + "insulator_ceramic", "copper": TEX + "wire_copper", "particle": "minecraft:block/spruce_log"}
    pole = dict(up="#end", down="#end")
    model("mast_base", wood, [box([6, 0, 6], [10, 16, 10], "#wood", **pole), box([5, 0, 5], [11, 3, 11], "#wood", **pole)])
    model("mast_pole", wood, [box([6, 0, 6], [10, 16, 10], "#wood", **pole)])
    model("mast_top", wood, [
        box([6, 0, 6], [10, 12, 10], "#wood", **pole),
        box([1, 9, 6.5], [15, 11.5, 9.5], "#wood", east="#end", west="#end"),
        box([6.5, 12, 6.5], [9.5, 15, 9.5], "#ceramic"),
        box([5.5, 13, 5.5], [10.5, 14, 10.5], "#ceramic"),
        box([7, 15, 7], [9, 16, 9], "#copper"),
    ])
    write(ASSETS / "blockstates/mast.json", {"variants": {
        "segment=0": {"model": f"{MOD}:block/mast_base"}, "segment=1": {"model": f"{MOD}:block/mast_pole"},
        "segment=2": {"model": f"{MOD}:block/mast_pole"}, "segment=3": {"model": f"{MOD}:block/mast_top"}}})

    for quarters in range(5):
        model(f"battery_{quarters}", {"side": TEX + f"battery_side_{quarters}", "top": TEX + "battery_top",
                                      "particle": TEX + "battery_top"}, [
            box([0, 0, 0], [16, 16, 16], "#side", up="#top", down="#top"),
        ])
    write(ASSETS / "blockstates/battery.json", {"variants": {
        f"charge={quarters}": {"model": f"{MOD}:block/battery_{quarters}"} for quarters in range(5)}})

    # Every machine faces north; its wire post stands at the back of its top.
    turns = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
    for name, body, hopper in (("basic_crusher", "dynamo_iron", "crusher_jaw"),
                               ("electric_furnace", "electric_furnace_top", "electric_furnace_top"),
                               ("sawmill", "dynamo_iron", "sawmill_top"),
                               ("plate_press", "dynamo_iron", "plate_press_top")):
        model(name, {"iron": TEX + ("dynamo_iron" if body == "dynamo_iron" else "lamp_base"), "front": TEX + f"{name}_front",
                     "jaw": TEX + hopper,
                     "particle": TEX + "dynamo_iron"}, [
            # Thermal-style machine casing: a closed, tileable block with the
            # machine face and work surface integrated into the shell.
            box([0, 0, 0], [16, 16, 16], "#iron", north="#front", up="#jaw"),
        ])
        write(ASSETS / f"blockstates/{name}.json", {"variants": {
            f"broken={broken},facing={facing},running={running}": {"model": f"{MOD}:block/{name}", **turn}
            for facing, turn in turns.items() for broken in ("false", "true") for running in ("false", "true")}})

    for name, block_model in (("mast", "mast_top"), ("battery", "battery_0"), ("basic_crusher", "basic_crusher"),
                              ("electric_furnace", "electric_furnace"), ("sawmill", "sawmill"), ("plate_press", "plate_press")):
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{block_model}"}})
        conditions = [{"condition": "minecraft:survives_explosion"}]
        if name == "mast":
            # All four blocks of a mast break together, and each would drop one: only the foot does.
            conditions.append({"condition": "minecraft:block_state_property", "block": f"{MOD}:mast",
                               "properties": {"segment": "0"}})
        write(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}], "conditions": conditions}]})

    shaped("mast", ["I", "L", "L"], {"I": f"{MOD}:insulator", "L": "#minecraft:logs"})
    shaped("battery", [" W ", "LSL", "LGL"], {"W": f"{MOD}:copper_wire", "L": f"{MOD}:lead_ingot",
                                             "S": f"{MOD}:sulfur", "G": "minecraft:glass"})
    shaped("basic_crusher", ["I I", "WCW", "IDI"], {"I": "minecraft:iron_ingot", "W": f"{MOD}:copper_wire",
                                                   "C": f"{MOD}:starter_crusher", "D": f"{MOD}:dynamo"})
    shaped("electric_furnace", ["BBB", "WFW", "BIB"], {"B": "minecraft:brick", "W": f"{MOD}:copper_wire",
                                                      "F": "minecraft:furnace", "I": "minecraft:iron_ingot"})
    shaped("sawmill", ["IFI", "WDW", "III"], {"I": "minecraft:iron_ingot", "F": "minecraft:flint",
                                             "W": f"{MOD}:copper_wire", "D": f"{MOD}:dynamo"})
    shaped("plate_press", ["IPI", "WDW", "IPI"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston",
                                                 "W": f"{MOD}:copper_wire", "D": f"{MOD}:dynamo"})
    for tool, names in (("pickaxe", ("battery", "basic_crusher", "electric_furnace", "sawmill", "plate_press")), ("axe", ("mast",))):
        path = RES / f"data/minecraft/tags/block/mineable/{tool}.json"
        tag = json.loads(path.read_text(encoding="utf-8"))
        for name in names:
            if f"{MOD}:{name}" not in tag["values"]:
                tag["values"].append(f"{MOD}:{name}")
        write(path, tag)


def blockstates():
    write(ASSETS / "blockstates/insulator.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/insulator", **turn} for facing, turn in STANDING.items()}})
    lamp = {}
    for facing, turn in STANDING.items():
        for light in range(16):
            for broken in (False, True):
                look = "broken" if broken else "off" if light == 0 else "dim" if light < 9 else "on"
                lamp[f"broken={str(broken).lower()},facing={facing},light={light}"] = {
                    "model": f"{MOD}:block/electric_lamp_{look}", **turn}
    write(ASSETS / "blockstates/electric_lamp.json", {"variants": lamp})
    write(ASSETS / "blockstates/dynamo.json", {"variants": {
        "facing=north": {"model": f"{MOD}:block/dynamo"}, "facing=south": {"model": f"{MOD}:block/dynamo"},
        "facing=east": {"model": f"{MOD}:block/dynamo", "y": 90}, "facing=west": {"model": f"{MOD}:block/dynamo", "y": 90}}})


def items():
    for name, block_model in (("insulator", "insulator"), ("electric_lamp", "electric_lamp_off"), ("dynamo", "dynamo")):
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{block_model}"}})
        write(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for name in ("copper_wire", "copper_cable", "voltmeter"):
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}})
        write(ASSETS / f"models/item/{name}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})


def shaped(name, pattern, key, count=1, result=None):
    write(DATA / f"recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "redstone", "key": key,
                                         "pattern": pattern, "result": {"id": f"{MOD}:{result or name}", "count": count}})


def recipes():
    # (the coil's recipe is written with its model)
    shaped("copper_wire", ["CCC"], {"C": "minecraft:copper_ingot"}, 6)
    shaped("copper_cable", ["WW", "WW"], {"W": f"{MOD}:copper_wire"})
    shaped("insulator", ["W", "B", "B"], {"W": f"{MOD}:copper_wire", "B": "minecraft:brick"}, 2)
    shaped("electric_lamp", ["G", "W", "I"],
           {"G": "minecraft:glass", "W": f"{MOD}:copper_wire", "I": "minecraft:iron_nugget"})
    shaped("dynamo", ["WWW", "ISI", "WWW"],
           {"W": f"{MOD}:copper_wire", "I": "minecraft:iron_ingot", "S": f"{MOD}:shaft"})
    shaped("voltmeter", [" G ", "WCW", " I "], {"G": "minecraft:glass_pane", "W": f"{MOD}:copper_wire",
                                               "C": "minecraft:compass", "I": "minecraft:iron_ingot"})


def tags():
    path = RES / "data/minecraft/tags/block/mineable/pickaxe.json"
    tag = json.loads(path.read_text(encoding="utf-8"))
    for name in ("insulator", "electric_lamp", "dynamo", "copper_coil"):
        if f"{MOD}:{name}" not in tag["values"]:
            tag["values"].append(f"{MOD}:{name}")
    write(path, tag)
    write(DATA / "damage_type/electrocution.json", {"message_id": f"{MOD}.electrocution", "scaling": "never", "exhaustion": 0.1})
    path = RES / "data/minecraft/tags/damage_type/bypasses_armor.json"
    tag = json.loads(path.read_text(encoding="utf-8"))
    if f"{MOD}:electrocution" not in tag["values"]:
        tag["values"].append(f"{MOD}:electrocution")
    write(path, tag)


LANG = [
    ("block.{m}.dynamo", "Dynamo", "Dynamo"),
    ("block.{m}.insulator", "Isolator", "Insulator"),
    ("block.{m}.electric_lamp", "Elektrische Lampe", "Electric Lamp"),
    ("item.{m}.copper_wire", "Kupferdraht", "Copper Wire"),
    ("item.{m}.copper_cable", "Kupferkabel", "Copper Cable"),
    ("item.{m}.voltmeter", "Messgerät", "Meter"),
    ("message.{m}.wire.started", "Jetzt den Draht an einem zweiten Anschluss benutzen.", "Now use the wire on a second point."),
    ("message.{m}.wire.strung", "Der Draht ist gespannt.", "The wire is strung."),
    ("message.{m}.wire.same", "Das ist derselbe Anschluss.", "That is the same point."),
    ("message.{m}.wire.not_a_point", "Der erste Anschluss ist nicht mehr da.", "The first point is gone."),
    ("message.{m}.wire.already", "Zwischen diesen beiden hängt schon ein Draht.", "A wire already runs between these two."),
    ("message.{m}.wire.full", "An einem Anschluss ist kein Platz mehr: höchstens 4 Drähte.", "One of the points is full: four wires at most."),
    ("message.{m}.wire.too_far", "Zu weit: dieser Draht spannt höchstens %s Blöcke.", "Too far: this wire spans %s blocks at most."),
    ("message.{m}.wire.blocked", "Da ist etwas im Weg.", "Something is in the way."),
    ("message.{m}.wire.needs_coil", "Dynamo, Akku und Maschinen brauchen eine Kupferspule als Anschluss.",
     "A dynamo, a battery and a machine take their wire through a copper coil."),
    ("block.{m}.copper_coil", "Kupferspule", "Copper Coil"),
    ("message.{m}.wire.short", "Für diese Strecke brauchst du %s Rollen.", "This run takes %s coils."),
    ("message.{m}.meter.dead", "Spannungslos.", "Dead."),
    ("message.{m}.meter.tripped", "Dynamo ausgekuppelt: Die Welle schafft die Last nicht.", "Dynamo let go: the line cannot turn it against this load."),
    ("message.{m}.meter.reading", "%s V · %s A · Draht %s %% · Netz %s W, %s %% Verlust", "%s V · %s A · wire %s %% · network %s W, %s %% lost"),
    ("message.{m}.meter.wet", " · nass: Kriechstrom", " · wet: leaking"),
    ("block.{m}.mast", "Leitungsmast", "Line Mast"),
    ("block.{m}.battery", "Akku", "Battery"),
    ("block.{m}.basic_crusher", "Einfacher Brecher", "Basic Crusher"),
    ("block.{m}.electric_furnace", "Elektroofen", "Electric Furnace"),
    ("block.{m}.sawmill", "Sägewerk", "Sawmill"),
    ("block.{m}.plate_press", "Plattenpresse", "Plate Press"),
    ("gui.{m}.machine.reading", "%s V · %s A", "%s V · %s A"),
    ("gui.{m}.machine.idle", "Bereit", "Ready"),
    ("gui.{m}.machine.running", "Läuft", "Running"),
    ("gui.{m}.machine.stalled", "Zu wenig Spannung", "Too little voltage"),
    ("gui.{m}.machine.burnt", "Durchgebrannt", "Burnt out"),
    ("message.{m}.meter.battery", "Akku %s %%", "battery %s %%"),
    ("message.{m}.meter.motor_burnt", "durchgebrannt: mit Kupferdraht neu wickeln", "burnt out: rewind it with copper wire"),
    ("message.{m}.meter.motor_idle", "steht: nichts zu tun", "idle: nothing to do"),
    ("message.{m}.meter.motor_stalled", "brummt nur: unter %s V läuft sie nicht", "only hums: below %s V it does not run"),
    ("message.{m}.meter.motor_running", "läuft mit %s %%", "running at %s %%"),
    ("death.attack.{m}.electrocution", "%1$s bekam einen Stromschlag", "%1$s was electrocuted"),
    ("death.attack.{m}.electrocution.player", "%1$s bekam im Kampf mit %2$s einen Stromschlag", "%1$s was electrocuted while fighting %2$s"),
]


def lang():
    for code, column in (("de_de", 1), ("en_us", 2)):
        path = ASSETS / f"lang/{code}.json"
        raw = path.read_bytes()
        existing = json.loads(raw.decode("utf-8"))
        for entry in LANG:
            existing[entry[0].format(m=MOD)] = entry[column]
        with open(path, "w", encoding="utf-8", newline="\r\n" if b"\r\n" in raw else "\n") as file:
            json.dump(existing, file, indent=2, ensure_ascii=False)
            file.write("\n")


if __name__ == "__main__":
    textures()
    models()
    blockstates()
    items()
    recipes()
    second_step()
    tags()
    lang()
    print("electricity assets written")
