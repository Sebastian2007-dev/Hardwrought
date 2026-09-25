"""Writes the look of Milestone 11: oil and chemistry.

Item textures are drawn from their nearest vanilla relatives and recoloured - a bucket of crude oil
is a water bucket with black in it, the fractions are potion bottles, bitumen is coal gone glossy
black, sulfur is glowstone dust gone dull yellow, salt is sugar - so they sit in the game's own
style. The canisters are drawn from scratch. The rig and the still are models built from vanilla
block textures.

Run from the repository root:  python tools/oil_assets.py
"""
import io
import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/hardwrought"
JAR = Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar"


def vanilla(path):
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def recolour(image, palette, select=lambda r, g, b: True):
    """Maps the brightness of selected pixels onto a dark-to-light palette."""
    out = image.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0 or not select(r, g, b):
                continue
            light = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            index = min(len(palette) - 1, int(light * len(palette)))
            px[x, y] = palette[index] + (a,)
    return out


def bottle(colour):
    """A glass bottle with this liquid in it: the potion bottle with its overlay tinted."""
    base = vanilla("item/potion")
    overlay = vanilla("item/potion_overlay")
    out = base.copy()
    for y in range(16):
        for x in range(16):
            r, g, b, a = overlay.getpixel((x, y))
            if a == 0:
                continue
            shade = (r + g + b) / (3 * 255.0)
            out.putpixel((x, y), tuple(int(c * shade) for c in colour) + (a,))
    return out


def canister(full):
    """A squat iron can with a valve on top; a full one has a yellow band round it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dark, mid, light = (70, 74, 78, 255), (130, 136, 140, 255), (190, 196, 200, 255)
    for y in range(4, 15):
        for x in range(4, 12):
            colour = mid
            if x == 4 or y == 14:
                colour = dark
            elif x == 11:
                colour = dark
            elif x == 5:
                colour = light
            img.putpixel((x, y), colour)
    for x in range(5, 11):
        img.putpixel((x, 4), light)
        img.putpixel((x, 3), dark)
    for y in range(1, 3):
        for x in range(7, 9):
            img.putpixel((x, y), (40, 40, 44, 255))
    img.putpixel((9, 1), (200, 60, 50, 255))
    if full:
        for y in range(8, 11):
            for x in range(5, 11):
                img.putpixel((x, y), (214, 170, 40, 255) if y != 10 else (160, 120, 20, 255))
    return img


def items():
    textures = ASSETS / "textures/item"
    water = lambda r, g, b: b > r + 20
    recolour(vanilla("item/water_bucket"), [(20, 16, 14), (38, 30, 24), (60, 46, 34)], water) \
        .save(textures / "crude_oil_bucket.png")
    bottle((236, 228, 170)).save(textures / "light_fraction.png")
    bottle((214, 150, 40)).save(textures / "fuel_fraction.png")
    bottle((70, 46, 26)).save(textures / "heavy_oil.png")
    recolour(vanilla("item/coal"), [(10, 10, 12), (22, 22, 26), (40, 40, 46), (90, 90, 100)]).save(textures / "bitumen.png")
    recolour(vanilla("item/glowstone_dust"), [(110, 96, 40), (150, 132, 52), (184, 166, 70), (210, 196, 110)]) \
        .save(textures / "raw_sulfur.png")
    recolour(vanilla("item/glowstone_dust"), [(170, 150, 20), (212, 192, 30), (240, 222, 60), (255, 244, 130)]) \
        .save(textures / "sulfur.png")
    recolour(vanilla("item/sugar"), [(170, 170, 176), (206, 206, 212), (232, 232, 236), (252, 252, 255)]) \
        .save(textures / "salt.png")
    canister(False).save(textures / "gas_canister.png")
    canister(True).save(textures / "natural_gas_canister.png")
    for name in ("crude_oil_bucket", "light_fraction", "fuel_fraction", "heavy_oil", "bitumen", "raw_sulfur",
                 "sulfur", "salt", "gas_canister", "natural_gas_canister"):
        write(ASSETS / f"models/item/{name}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"hardwrought:item/{name}"}})
        write(ASSETS / f"items/{name}.json",
              {"model": {"type": "minecraft:model", "model": f"hardwrought:item/{name}"}})


def box(lo, hi, texture, faces=("north", "south", "west", "east", "up", "down")):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    uv = {
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
    }
    return {"from": list(lo), "to": list(hi),
            "faces": {f: {"uv": uv[f], "texture": texture} for f in faces}}


def blocks():
    # The rig: a timber platform, four iron legs to a crown, and the drill string down the middle.
    rig = [box((0, 0, 0), (16, 3, 16), "#platform")]
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rig.append(box((x, 3, z), (x + 2, 15, z + 2), "#frame", ("north", "south", "west", "east")))
    rig.append(box((1, 15, 1), (15, 16, 15), "#frame"))
    rig.append(box((6, 3, 6), (10, 15, 10), "#pipe", ("north", "south", "west", "east")))
    write(ASSETS / "models/block/drilling_rig.json", {
        "parent": "minecraft:block/block",
        "__comment": "Generated by tools/oil_assets.py.",
        "textures": {"platform": "hardwrought:block/hardwrought/machine_boards_dark", "frame": "hardwrought:block/hardwrought/iron_brace",
                     "pipe": "hardwrought:block/hardwrought/drill_steel", "particle": "hardwrought:block/hardwrought/iron_brace"},
        "elements": rig,
    })
    write(ASSETS / "blockstates/drilling_rig.json",
          {"variants": {"running=false": {"model": "hardwrought:block/drilling_rig"},
                        "running=true": {"model": "hardwrought:block/drilling_rig"}}})
    write(ASSETS / "items/drilling_rig.json",
          {"model": {"type": "minecraft:model", "model": "hardwrought:block/drilling_rig"}})

    # The still: a round-shouldered copper pot, its neck and the swan's neck pipe off the top.
    still = [
        box((2, 0, 2), (14, 10, 14), "#copper"),
        box((5, 10, 5), (11, 14, 11), "#copper"),
        box((7, 14, 7), (9, 16, 9), "#pipe", ("north", "south", "west", "east", "up")),
        box((9, 14, 7), (15, 16, 9), "#pipe", ("north", "south", "up", "down", "east")),
    ]
    write(ASSETS / "models/block/still.json", {
        "parent": "minecraft:block/block",
        "__comment": "Generated by tools/oil_assets.py.",
        "textures": {"copper": "hardwrought:block/hardwrought/hammered_copper", "pipe": "hardwrought:block/hardwrought/copper_pipe",
                     "particle": "hardwrought:block/hardwrought/hammered_copper"},
        "elements": still,
    })
    write(ASSETS / "blockstates/still.json",
          {"variants": {"running=false": {"model": "hardwrought:block/still"},
                        "running=true": {"model": "hardwrought:block/still"}}})
    write(ASSETS / "items/still.json", {"model": {"type": "minecraft:model", "model": "hardwrought:block/still"}})


if __name__ == "__main__":
    items()
    blocks()
    print("oil and chemistry assets written")
