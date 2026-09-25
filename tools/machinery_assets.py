"""Writes the models, blockstates, item definitions, loot tables and recipes of milestone 10's parts.

Every part that turns is drawn by a renderer from a model lying about its local Y axis; the block
itself only shows what stands still (bearings, housings) or nothing but particles. Vanilla textures
throughout — see textureRequirements.md for the proper ones still wanted.

Run from the repository root:  python tools/machinery_assets.py
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/hardwrought"
DATA = RES / "data/hardwrought"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def box(frm, to, texture, rotation=None, faces=None):
    element = {"from": frm, "to": to,
               "faces": {face: {"texture": texture} for face in (faces or ["north", "south", "east", "west", "up", "down"])}}
    if rotation is not None:
        element["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": rotation}
    return element


def model(name, textures, elements, parent="minecraft:block/block", display=None):
    value = {"parent": parent, "textures": textures, "elements": elements}
    if display:
        value["display"] = display
    write(ASSETS / f"models/block/{name}.json", value)


def gui_scale(scale):
    return {
        "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [scale * 0.8] * 3},
        "fixed": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [scale] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [scale * 0.6] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [scale * 0.7] * 3},
    }


def parts():
    axle = box([6, 0, 6], [10, 16, 10], "#axle")
    # Small gear: a disc with eight teeth, two of the teeth bars turned 45 degrees.
    model("cogwheel_gear", {"axle": "hardwrought:block/hardwrought/shaft_side", "wheel": "hardwrought:block/hardwrought/gear_wood",
                            "particle": "hardwrought:block/hardwrought/gear_wood"}, [
        axle,
        box([3, 6.5, 3], [13, 9.5, 13], "#wheel"),
        box([3, 6.5, 3], [13, 9.5, 13], "#wheel", rotation=45),
        box([0, 7, 6.5], [16, 9, 9.5], "#wheel"),
        box([6.5, 7, 0], [9.5, 9, 16], "#wheel"),
        box([0, 7, 6.5], [16, 9, 9.5], "#wheel", rotation=45),
        box([6.5, 7, 0], [9.5, 9, 16], "#wheel", rotation=45),
    ], display=gui_scale(0.6))
    # Large gear: twice the size, sixteen teeth.
    large = [axle,
             box([-4, 6.5, -4], [20, 9.5, 20], "#wheel"),
             box([-4, 6.5, -4], [20, 9.5, 20], "#wheel", rotation=45)]
    for angle in (None, 45, 22.5, -22.5):
        large.append(box([-8, 7, 6.5], [24, 9, 9.5], "#wheel", rotation=angle))
        large.append(box([6.5, 7, -8], [9.5, 9, 24], "#wheel", rotation=angle))
    model("large_cogwheel_gear", {"axle": "hardwrought:block/hardwrought/shaft_side", "wheel": "hardwrought:block/hardwrought/gear_wood",
                                  "particle": "hardwrought:block/hardwrought/gear_wood"}, large, display=gui_scale(0.35))
    # Water wheel: hub, eight spokes, eight paddles, three blocks across.
    wheel = [box([5, 0, 5], [11, 16, 11], "#axle")]
    for angle in (None, 45):
        wheel.append(box([-16, 7, 7], [32, 9, 9], "#spoke", rotation=angle))
        wheel.append(box([7, 7, -16], [9, 9, 32], "#spoke", rotation=angle))
        wheel.append(box([26, 2, 4], [32, 14, 12], "#paddle", rotation=angle))
        wheel.append(box([-16, 2, 4], [-10, 14, 12], "#paddle", rotation=angle))
        wheel.append(box([4, 2, 26], [12, 14, 32], "#paddle", rotation=angle))
        wheel.append(box([4, 2, -16], [12, 14, -10], "#paddle", rotation=angle))
    model("water_wheel_rim", {"axle": "hardwrought:block/hardwrought/shaft_side", "spoke": "hardwrought:block/hardwrought/machine_boards",
                              "paddle": "hardwrought:block/hardwrought/wet_paddle", "particle": "hardwrought:block/hardwrought/machine_boards"},
          wheel, display=gui_scale(0.25))
    # Sails: four spars with a cloth on one side of each, drawn scaled up to five blocks across.
    model("windmill_sails", {"spar": "hardwrought:block/hardwrought/dark_handle", "cloth": "hardwrought:block/hardwrought/sailcloth",
                             "particle": "hardwrought:block/hardwrought/sailcloth"}, [
        box([5, 0, 5], [11, 16, 11], "#spar"),
        box([-16, 9, 7], [32, 11, 9], "#spar"),
        box([7, 9, -16], [9, 11, 32], "#spar"),
        box([12, 10, 9], [32, 11, 17], "#cloth"),
        box([-16, 10, -1], [4, 11, 7], "#cloth"),
        box([-1, 10, 12], [7, 11, 32], "#cloth"),
        box([9, 10, -16], [17, 11, 4], "#cloth"),
    ], display=gui_scale(0.25))
    model("belt_strip", {"belt": "hardwrought:block/hardwrought/leather_belt", "particle": "hardwrought:block/hardwrought/leather_belt"}, [
        box([0, 7.5, 6], [16, 8.5, 10], "#belt"),
    ])
    for part in ("cogwheel_gear", "large_cogwheel_gear", "water_wheel_rim", "windmill_sails", "belt_strip"):
        write(ASSETS / f"blockstates/{part}.json", {"variants": {"": {"model": f"hardwrought:block/{part}"}}})


def blocks():
    # Gears are drawn wholly by their renderer; the block keeps only its particles.
    for name in ("cogwheel", "large_cogwheel"):
        write(ASSETS / f"models/block/{name}.json", {"textures": {"particle": "hardwrought:block/hardwrought/gear_wood"}})
        write(ASSETS / f"blockstates/{name}.json", {"variants": {
            f"axis={axis}": {"model": f"hardwrought:block/{name}"} for axis in ("x", "y", "z")}})
    write(ASSETS / "models/block/gearbox.json", {
        "parent": "minecraft:block/cube_column",
        "textures": {"side": "hardwrought:block/hardwrought/gearbox_side",
                     "end": "hardwrought:block/hardwrought/gearbox_end"}})
    write(ASSETS / "blockstates/gearbox.json", {"variants": {"": {"model": "hardwrought:block/gearbox"}}})
    # The wheel's hub stands in two bearings, like a shaft's.
    variants = {}
    for w in ("true", "false"):
        variants[f"axis=x,waterlogged={w}"] = {"model": "hardwrought:block/shaft", "x": 90, "y": 90}
        variants[f"axis=z,waterlogged={w}"] = {"model": "hardwrought:block/shaft", "x": 90}
    write(ASSETS / "blockstates/water_wheel.json", {"variants": variants})
    model("windmill", {"housing": "hardwrought:block/hardwrought/gearbox_side", "particle": "hardwrought:block/hardwrought/gearbox_side"}, [
        box([4, 4, 8], [12, 12, 16], "#housing"),
    ])
    write(ASSETS / "blockstates/windmill.json", {"variants": {
        "facing=north": {"model": "hardwrought:block/windmill"},
        "facing=east": {"model": "hardwrought:block/windmill", "y": 90},
        "facing=south": {"model": "hardwrought:block/windmill", "y": 180},
        "facing=west": {"model": "hardwrought:block/windmill", "y": 270}}})


def items():
    shown = {"cogwheel": "cogwheel_gear", "large_cogwheel": "large_cogwheel_gear", "gearbox": "gearbox",
             "water_wheel": "water_wheel_rim", "windmill": "windmill_sails"}
    for item, block_model in shown.items():
        write(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"hardwrought:block/{block_model}"}})
    write(ASSETS / "items/belt.json", {"model": {"type": "minecraft:model", "model": "hardwrought:item/belt"}})
    write(ASSETS / "models/item/belt.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "hardwrought:item/belt"}})
    # A coiled leather belt.
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(icon)
    draw.ellipse([1, 3, 14, 12], outline=(74, 45, 26, 255), width=3)
    draw.ellipse([2, 4, 13, 11], outline=(128, 82, 47, 255), width=1)
    draw.point([(4, 5), (8, 4), (11, 6)], fill=(166, 116, 72, 255))
    icon.save(ASSETS / "textures/item/belt.png")


def loot():
    for name in ("cogwheel", "large_cogwheel", "gearbox", "water_wheel", "windmill"):
        write(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"hardwrought:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def recipes():
    write(DATA / "recipe/cogwheel.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                          "ingredients": ["hardwrought:shaft", "#minecraft:planks"],
                                          "result": {"id": "hardwrought:cogwheel"}})
    write(DATA / "recipe/large_cogwheel.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                "ingredients": ["hardwrought:cogwheel", "#minecraft:planks", "#minecraft:planks"],
                                                "result": {"id": "hardwrought:large_cogwheel"}})
    write(DATA / "recipe/gearbox.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                         "key": {"P": "#minecraft:planks", "C": "hardwrought:cogwheel"},
                                         "pattern": [" P ", "CPC", " P "], "result": {"id": "hardwrought:gearbox"}})
    write(DATA / "recipe/belt.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                      "key": {"L": "minecraft:leather"}, "pattern": ["LLL"],
                                      "result": {"id": "hardwrought:belt", "count": 2}})
    write(DATA / "recipe/water_wheel.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                             "key": {"S": "minecraft:stick", "P": "#minecraft:planks", "H": "hardwrought:shaft"},
                                             "pattern": ["SPS", "PHP", "SPS"], "result": {"id": "hardwrought:water_wheel"}})
    write(DATA / "recipe/windmill.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                          "key": {"W": "#minecraft:wool", "S": "minecraft:stick", "H": "hardwrought:shaft"},
                                          "pattern": ["WSW", "SHS", "WSW"], "result": {"id": "hardwrought:windmill"}})


def lang():
    names = {
        "en_us": {
            "block.hardwrought.cogwheel": "Cogwheel",
            "block.hardwrought.large_cogwheel": "Large Cogwheel",
            "block.hardwrought.gearbox": "Gearbox",
            "block.hardwrought.water_wheel": "Water Wheel",
            "block.hardwrought.windmill": "Windmill",
            "item.hardwrought.belt": "Belt",
            "message.hardwrought.belt_started": "Now use the belt on a second shaft beside this one.",
            "message.hardwrought.belt_cannot": "A belt runs between two free, parallel shafts side by side, at most 8 blocks apart.",
            "message.hardwrought.belt_laid": "The belt is laid.",
            "message.hardwrought.kinetic.still": "Standing still: nothing drives this line.",
            "message.hardwrought.kinetic.jammed": "Jammed: the gearing or the sources work against each other.",
            "message.hardwrought.kinetic.overstressed": "Overstressed: load %s of %s — too heavy for its sources.",
            "message.hardwrought.kinetic.running": "%s RPM · load %s of %s",
        },
        "de_de": {
            "block.hardwrought.cogwheel": "Zahnrad",
            "block.hardwrought.large_cogwheel": "Großes Zahnrad",
            "block.hardwrought.gearbox": "Getriebe",
            "block.hardwrought.water_wheel": "Wasserrad",
            "block.hardwrought.windmill": "Windmühle",
            "item.hardwrought.belt": "Riemen",
            "message.hardwrought.belt_started": "Jetzt den Riemen an einer zweiten Welle daneben benutzen.",
            "message.hardwrought.belt_cannot": "Ein Riemen läuft zwischen zwei freien, parallelen Wellen nebeneinander, höchstens 8 Blöcke auseinander.",
            "message.hardwrought.belt_laid": "Der Riemen ist aufgelegt.",
            "message.hardwrought.kinetic.still": "Steht still: nichts treibt diesen Strang an.",
            "message.hardwrought.kinetic.jammed": "Blockiert: Übersetzung oder Antriebe arbeiten gegeneinander.",
            "message.hardwrought.kinetic.overstressed": "Überlastet: Last %s von %s – zu schwer für die Antriebe.",
            "message.hardwrought.kinetic.running": "%s U/min · Last %s von %s",
        },
    }
    for lang_name, entries in names.items():
        path = ASSETS / f"lang/{lang_name}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(entries)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parts()
    blocks()
    items()
    loot()
    recipes()
    lang()
    print("done")
