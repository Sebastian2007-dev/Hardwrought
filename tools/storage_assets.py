"""Writes the textures, models, blockstates, item definitions, loot tables and recipes of the store
room: storage tube, storage port and storage desk, the lathe and the tube mill that make their
parts, and the three parts themselves.

The blocks are plain boxes painted here; the tube is a core with an arm toward every side it is
joined on, like an item pipe but thinner.

Run from the repository root:  python tools/storage_assets.py
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

IRON = (96, 100, 108)
BRONZE = (176, 122, 62)
BRONZE_DARK = (112, 74, 38, 255)
BRONZE_LIGHT = (214, 160, 92, 255)
STEEL = (150, 154, 162, 255)
SHADOW = (24, 24, 28, 255)
EDGE = (52, 54, 60, 255)
WOOD = (122, 88, 52)
PAPER = (232, 226, 204, 255)
INK = (70, 62, 54, 255)

FACES = ("north", "south", "west", "east", "up", "down")


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
        paint(img, ImageDraw.Draw(img))
    path = ASSETS / f"textures/block/{MOD}/{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def item_texture(name, paint):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paint(img, ImageDraw.Draw(img))
    path = ASSETS / f"textures/item/{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def rivets(img, draw):
    draw.rectangle([0, 0, 15, 15], outline=EDGE)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), (150, 154, 162, 255))


def gear(draw, cx, cy, light, dark):
    """A small gear seen flat: eight teeth round a hub."""
    for dx, dy in ((0, -3), (0, 3), (-3, 0), (3, 0), (-2, -2), (2, -2), (-2, 2), (2, 2)):
        draw.point((cx + dx, cy + dy), fill=dark)
    draw.rectangle([cx - 2, cy - 1, cx + 2, cy + 1], fill=light)
    draw.rectangle([cx - 1, cy - 2, cx + 1, cy + 2], fill=light)
    draw.point((cx, cy), fill=SHADOW)


def textures():
    # ---- the two machines
    def lathe_front(img, draw):
        rivets(img, draw)
        draw.rectangle([2, 4, 13, 12], fill=SHADOW, outline=EDGE)
        # The chuck to the left, the rod turning in it, the cutter coming up from below.
        draw.rectangle([3, 6, 5, 10], fill=STEEL, outline=(70, 72, 78, 255))
        draw.line([(6, 8), (12, 8)], fill=(200, 204, 212, 255))
        draw.line([(9, 11), (9, 9)], fill=BRONZE_LIGHT)

    def mill_front(img, draw):
        rivets(img, draw)
        draw.rectangle([2, 3, 13, 12], fill=SHADOW, outline=EDGE)
        # Three rollers and the tube coming out between them.
        for cx, cy in ((5, 6), (10, 6), (7, 10)):
            draw.ellipse([cx - 2, cy - 2, cx + 1, cy + 1], fill=STEEL, outline=(70, 72, 78, 255))
        draw.rectangle([10, 9, 13, 11], fill=BRONZE_LIGHT, outline=BRONZE_DARK)

    def machine_top(colour):
        def paint(img, draw):
            rivets(img, draw)
            draw.rectangle([3, 3, 12, 12], fill=colour, outline=(40, 40, 44, 255))
        return paint

    noisy("lathe_front", IRON, 7, 301, lathe_front)
    noisy("lathe_top", IRON, 7, 302, machine_top((120, 124, 132, 255)))
    noisy("tube_mill_front", IRON, 7, 303, mill_front)
    noisy("tube_mill_top", IRON, 7, 304, machine_top((176, 122, 62, 255)))

    # ---- the tube
    def tube(img, draw):
        # Rolled bronze with a strong seam and shaded shoulders. The broad,
        # quiet bands stay readable even on the tube's six-pixel-wide arms.
        for x in range(16):
            for y in (3, 4, 5):
                r, g, b, a = img.getpixel((x, y))
                img.putpixel((x, y), (clamp(r * 1.16), clamp(g * 1.16), clamp(b * 1.16), a))
            for y in (10, 11, 12):
                r, g, b, a = img.getpixel((x, y))
                img.putpixel((x, y), (clamp(r * 0.74), clamp(g * 0.74), clamp(b * 0.74), a))
        draw.line([(0, 7), (15, 7)], fill=(232, 178, 106, 255))
        draw.line([(0, 8), (15, 8)], fill=(82, 50, 28, 255))
        for x in (1, 8, 14):
            draw.point((x, 7), fill=(250, 204, 132, 255))

    def trim(img, draw):
        draw.rectangle([0, 0, 15, 15], outline=(28, 30, 36, 255))
        draw.rectangle([2, 2, 13, 13], outline=(104, 108, 118, 255))
        draw.rectangle([4, 4, 11, 11], outline=BRONZE_DARK)
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            draw.point((x, y), fill=STEEL)

    noisy("storage_tube", BRONZE, 8, 305, tube)
    noisy("storage_tube_trim", (70, 72, 80), 6, 306, trim)

    # ---- the port
    def port_side(img, draw):
        rivets(img, draw)
        draw.rectangle([2, 3, 13, 12], fill=(48, 50, 58, 255), outline=(32, 34, 40, 255))
        draw.line([(3, 4), (12, 4)], fill=BRONZE_DARK)
        draw.line([(3, 11), (12, 11)], fill=BRONZE_DARK)
        gear(draw, 7, 8, BRONZE_LIGHT, BRONZE_DARK)
        draw.line([(10, 8), (12, 8)], fill=BRONZE_LIGHT)

    def port_front(img, draw):
        rivets(img, draw)
        # A deep square intake with a heavy bronze lip and directional teeth.
        draw.rectangle([2, 2, 13, 13], fill=(42, 44, 50, 255), outline=(30, 32, 36, 255))
        draw.rectangle([3, 3, 12, 12], outline=BRONZE_LIGHT)
        draw.rectangle([5, 5, 10, 10], fill=(14, 14, 18, 255), outline=BRONZE_DARK)
        for x in (5, 7, 9):
            draw.point((x, 5), fill=STEEL)
            draw.point((x + 1, 10), fill=STEEL)

    def port_back(img, draw):
        rivets(img, draw)
        draw.rectangle([3, 3, 12, 12], fill=(48, 50, 58, 255), outline=(30, 32, 36, 255))
        draw.rectangle([4, 4, 11, 11], outline=BRONZE_DARK)
        draw.rectangle([6, 6, 9, 9], fill=SHADOW, outline=(112, 116, 126, 255))

    noisy("storage_port_side", IRON, 7, 307, port_side)
    noisy("storage_port_front", IRON, 7, 308, port_front)
    noisy("storage_port_back", IRON, 7, 309, port_back)

    # ---- the desk
    def desk_side(img, draw):
        draw.rectangle([0, 0, 15, 15], outline=(38, 34, 32, 255))
        draw.line([(1, 4), (14, 4)], fill=(72, 50, 30, 255))
        draw.line([(1, 11), (14, 11)], fill=(72, 50, 30, 255))
        draw.line([(2, 1), (2, 14)], fill=(154, 108, 58, 255))
        draw.line([(13, 1), (13, 14)], fill=(72, 50, 30, 255))
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
            img.putpixel((x, y), STEEL)

    def desk_front(img, draw):
        desk_side(img, draw)
        # Six broad catalogue drawers are clearer than a noisy nine-drawer grid.
        for row in range(3):
            for column in range(2):
                x, y = 2 + column * 7, 2 + row * 4
                draw.rectangle([x, y, x + 5, y + 3], fill=(92, 62, 36, 255), outline=(48, 34, 24, 255))
                draw.rectangle([x + 1, y + 1, x + 3, y + 1], fill=PAPER)
                draw.point((x + 4, y + 2), fill=BRONZE_LIGHT)

    def desk_top(img, draw):
        desk_side(img, draw)
        # An open inventory ledger beside the bronze tube socket.
        draw.rectangle([2, 3, 9, 12], fill=PAPER, outline=(76, 66, 54, 255))
        draw.line([(5, 3), (5, 12)], fill=(150, 140, 112, 255))
        for y in (5, 7, 9, 11):
            draw.line([(3, y), (4, y)], fill=INK)
            draw.line([(6, y), (8, y)], fill=INK)
        draw.rectangle([10, 5, 14, 10], fill=(42, 44, 50, 255), outline=BRONZE_DARK)
        draw.rectangle([11, 6, 13, 9], fill=SHADOW, outline=BRONZE_LIGHT)

    noisy("storage_desk_side", WOOD, 8, 310, desk_side)
    noisy("storage_desk_front", WOOD, 8, 311, desk_front)
    noisy("storage_desk_top", WOOD, 8, 312, desk_top)

    # ---- the parts
    def fine_gear(img, draw):
        steel, dark = (190, 194, 202, 255), (92, 96, 104, 255)
        for dx, dy in ((0, -6), (0, 5), (-6, 0), (5, 0), (-4, -4), (4, -4), (-4, 4), (4, 4)):
            draw.rectangle([7 + dx, 7 + dy, 8 + dx, 8 + dy], fill=dark)
        draw.ellipse([3, 3, 12, 12], fill=steel, outline=dark)
        draw.ellipse([6, 6, 9, 9], fill=(0, 0, 0, 0), outline=dark)

    def tube_section(img, draw):
        draw.polygon([(3, 12), (10, 3), (13, 5), (6, 14)], fill=BRONZE_LIGHT, outline=BRONZE_DARK)
        draw.line([(5, 12), (11, 5)], fill=(236, 190, 126, 255))
        draw.ellipse([2, 11, 6, 15], fill=SHADOW, outline=BRONZE_DARK)

    def sorting_mechanism(img, draw):
        draw.rectangle([2, 2, 13, 13], fill=(84, 88, 96, 255), outline=(44, 46, 52, 255))
        gear(draw, 6, 6, (200, 204, 212, 255), (110, 114, 122, 255))
        gear(draw, 10, 10, BRONZE_LIGHT, BRONZE_DARK)
        draw.line([(10, 4), (12, 4)], fill=(214, 124, 78, 255))
        draw.line([(3, 11), (5, 11)], fill=(214, 124, 78, 255))

    item_texture("fine_gear", fine_gear)
    item_texture("tube_section", tube_section)
    item_texture("sorting_mechanism", sorting_mechanism)


# ---------------------------------------------------------------- models


def cube(name, textures, faces):
    write(ASSETS / f"models/block/{name}.json", {
        "parent": "minecraft:block/block", "textures": textures,
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                      "faces": {face: {"texture": faces.get(face, faces["*"])} for face in FACES}}]})


def block_item(name, model=None):
    write(ASSETS / f"items/{name}.json",
          {"model": {"type": "minecraft:model", "model": model or f"{MOD}:block/{name}"}})


def drops_itself(name):
    write(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


HORIZONTAL = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}


def machine(name):
    cube(name, {"iron": TEX + "dynamo_iron", "front": TEX + f"{name}_front", "jaw": TEX + f"{name}_top",
                "particle": TEX + "dynamo_iron"}, {"*": "#iron", "north": "#front", "up": "#jaw"})
    variants = {}
    for facing, turn in HORIZONTAL.items():
        for broken in ("false", "true"):
            for running in ("false", "true"):
                variants[f"broken={broken},facing={facing},running={running}"] = {
                    "model": f"{MOD}:block/{name}", **turn}
    write(ASSETS / f"blockstates/{name}.json", {"variants": variants})
    block_item(name)
    drops_itself(name)


def uv(face, lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return {
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
    }[face]


def box(lo, hi, faces=FACES, texture="#pipe", textures=None):
    textures = textures or {}
    return {"from": list(lo), "to": list(hi),
            "faces": {f: {"uv": uv(f, lo, hi), "texture": textures.get(f, texture)} for f in faces}}


def opposite(face):
    return {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}[face]


def tube():
    lo, hi = 5, 11
    arms = {
        "north": ((lo, lo, 0), (hi, hi, lo)), "south": ((lo, lo, hi), (hi, hi, 16)),
        "west": ((0, lo, lo), (lo, hi, hi)), "east": ((hi, lo, lo), (16, hi, hi)),
        "down": ((lo, 0, lo), (hi, lo, hi)), "up": ((lo, hi, lo), (hi, 16, hi)),
    }
    textures = {"pipe": TEX + "storage_tube", "trim": TEX + "storage_tube_trim", "particle": TEX + "storage_tube"}
    folder = ASSETS / "models/block/storage_tube"
    # A dark sorting junction with bronze caps on every unused face. Connected
    # arms cover their cap and carry a matching collar at the block boundary.
    caps = [
        box((5, 5, 3.5), (11, 11, 4.5), texture="#pipe"),
        box((5, 5, 11.5), (11, 11, 12.5), texture="#pipe"),
        box((3.5, 5, 5), (4.5, 11, 11), texture="#pipe"),
        box((11.5, 5, 5), (12.5, 11, 11), texture="#pipe"),
        box((5, 3.5, 5), (11, 4.5, 11), texture="#pipe"),
        box((5, 11.5, 5), (11, 12.5, 11), texture="#pipe"),
    ]
    write(folder / "core.json", {"parent": "minecraft:block/block", "textures": textures,
                                  "elements": [box((4, 4, 4), (12, 12, 12), texture="#trim"), *caps]})
    for name, (arm_lo, arm_hi) in arms.items():
        # The arm's open end touches the next block: no face there, and none where it meets the core.
        around = [f for f in FACES if f not in (name, opposite(name))]
        collar_lo, collar_hi = list(arm_lo), list(arm_hi)
        axis = 2 if name in ("north", "south") else 0 if name in ("west", "east") else 1
        if name in ("north", "west", "down"):
            collar_hi[axis] = 2
        else:
            collar_lo[axis] = 14
        for axis2 in range(3):
            if axis2 != axis:
                collar_lo[axis2], collar_hi[axis2] = 4.5, 11.5
        write(folder / f"arm_{name}.json", {"parent": "minecraft:block/block", "textures": textures,
                                             "elements": [box(arm_lo, arm_hi, around),
                                                          box(tuple(collar_lo), tuple(collar_hi), around,
                                                              texture="#trim")]})
    write(folder / "item.json", {
        "parent": "minecraft:block/block", "textures": textures,
        "display": {"gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}},
        "elements": [
            box((lo, lo, 0), (hi, hi, 16), textures={"north": "#trim", "south": "#trim"}),
            box((4.5, 4.5, 0), (11.5, 11.5, 2), ("west", "east", "up", "down"), texture="#trim"),
            box((4.5, 4.5, 6), (11.5, 11.5, 10), ("west", "east", "up", "down"), texture="#trim"),
            box((4.5, 4.5, 14), (11.5, 11.5, 16), ("west", "east", "up", "down"), texture="#trim")]})
    multipart = [{"apply": {"model": f"{MOD}:block/storage_tube/core"}}]
    for face in arms:
        multipart.append({"when": {face: "true"}, "apply": {"model": f"{MOD}:block/storage_tube/arm_{face}"}})
    write(ASSETS / "blockstates/storage_tube.json", {"multipart": multipart})
    block_item("storage_tube", f"{MOD}:block/storage_tube/item")
    drops_itself("storage_tube")


def port():
    cube("storage_port", {"side": TEX + "storage_port_side", "front": TEX + "storage_port_front",
                          "back": TEX + "storage_port_back", "particle": TEX + "storage_port_side"},
         {"*": "#side", "north": "#front", "south": "#back"})
    turns = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
             "up": {"x": 270}, "down": {"x": 90}}
    write(ASSETS / "blockstates/storage_port.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/storage_port", **turn} for facing, turn in turns.items()}})
    block_item("storage_port")
    drops_itself("storage_port")


def desk():
    cube("storage_desk", {"side": TEX + "storage_desk_side", "front": TEX + "storage_desk_front",
                          "top": TEX + "storage_desk_top", "particle": TEX + "storage_desk_side"},
         {"*": "#side", "north": "#front", "up": "#top"})
    write(ASSETS / "blockstates/storage_desk.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/storage_desk", **turn} for facing, turn in HORIZONTAL.items()}})
    block_item("storage_desk")
    drops_itself("storage_desk")


def flat_item(name):
    write(ASSETS / f"models/item/{name}.json",
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
    write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}})


# ---------------------------------------------------------------- recipes


def shaped(name, pattern, key, count=1, category="redstone"):
    write(DATA / f"recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
        "result": {"id": f"{MOD}:{name}", "count": count}})


def recipes():
    # The two machines, built like the other machines on current: iron, wire and a dynamo for a motor.
    shaped("lathe", ["IRI", "WDW", "III"], {
        "I": "minecraft:iron_ingot", "R": f"{MOD}:steel_rod", "W": f"{MOD}:copper_wire", "D": f"{MOD}:dynamo"})
    shaped("tube_mill", ["IRI", "WDW", "IRI"], {
        "I": "minecraft:iron_ingot", "R": f"{MOD}:iron_rod", "W": f"{MOD}:copper_wire", "D": f"{MOD}:dynamo"})
    # Four gears off the lathe, two steel plates, wire and a hopper's worth of chute.
    shaped("sorting_mechanism", ["GPG", "WHW", "GPG"], {
        "G": f"{MOD}:fine_gear", "P": f"{MOD}:steel_plate", "W": f"{MOD}:copper_wire", "H": "minecraft:hopper"})
    # Six lengths off the tube mill, sealed with leather, make six blocks of tube.
    shaped("storage_tube", ["TTT", "L L", "TTT"], {"T": f"{MOD}:tube_section", "L": "minecraft:leather"}, 6)
    # One port for one chest, and a sorting mechanism in every one of them.
    shaped("storage_port", ["PTP", "TMT", "PCP"], {
        "P": f"{MOD}:iron_plate", "T": f"{MOD}:tube_section", "M": f"{MOD}:sorting_mechanism",
        "C": "minecraft:chest"})
    shaped("storage_desk", ["SGS", "MCM", "STS"], {
        "S": f"{MOD}:steel_plate", "G": "minecraft:glass", "M": f"{MOD}:sorting_mechanism",
        "C": "minecraft:chest", "T": f"{MOD}:storage_tube"})


if __name__ == "__main__":
    textures()
    machine("lathe")
    machine("tube_mill")
    tube()
    port()
    desk()
    for part in ("fine_gear", "tube_section", "sorting_mechanism"):
        flat_item(part)
    recipes()
