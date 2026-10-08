"""Writes the rods and plates of every forgeable metal: textures, models and item definitions.

Each takes four shades from its metal's ingot, so a titanium rod looks like titanium. A rod is a long
bar lying corner to corner; a plate is a flat sheet seen at a slant, bright along its top edge. Both
shapes differ from an ingot by far more than the anvil's smallest job, so each is real work to forge.

Afterwards run  python tools/glow_textures.py  so they glow while hot.

Run from the repository root:  python tools/metal_stock_textures.py
"""
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from drill_assets import ASSETS, ingot  # noqa: E402

VANILLA = {"iron": "minecraft:iron_ingot", "copper": "minecraft:copper_ingot", "gold": "minecraft:gold_ingot"}
MOD_METALS = ["bronze", "tin", "zinc", "lead", "manganese", "magnesium", "aluminum", "nickel", "cobalt",
              "chromium", "titanium", "tungsten", "uranium", "thorium", "platinum", "silver", "mithril",
              "adamantium"]

# Hand-authored value maps derived from the generated source concepts in art_source and the supplied
# shape references. Encoding the lighting in the sprite rather than inferring it from the outline
# keeps the tiny bevels and hammer marks readable at native resolution.
# d/m/l/s/h = dark/mid/light/shine/hammer dent.
ROD = [
    "................", "............dd..", "...........dsld.", "..........dmsld.",
    ".........dmsld..", "........dmlsd...", ".......dmsld....", "......dmsld.....",
    ".....dmlsd......", "....dmsld.......", "...dmsld........", "..dmlsd.........",
    ".dmsld..........", ".dmsd...........", "..dd............", "................",
]
PLATE = [
    "................", "................", ".....dddddd.....", "...ddllssllld...",
    "..dllssssllmmd..", ".dllslllllmmmmd.", "dlllhllllllmmmmd", "dllllllllllmmmmd",
    "dlllhllllllmmmmd", ".dllslllllmmmmd.", "..ddlllllmmmmdd.", "....dddddddd....",
    "................", "................", "................", "................",
]


def palette(image):
    pixels = sorted((p for p in image.getdata() if p[3] > 0), key=lambda p: p[0] + p[1] + p[2])
    pick = lambda share: pixels[min(len(pixels) - 1, int(len(pixels) * share))][:3]
    return pick(0.08), pick(0.35), pick(0.65), pick(0.95)


def paint(mask, shades):
    dark, mid, light, shine = shades
    colours = {"d": dark, "m": mid, "l": light, "s": shine, "h": dark}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(mask):
        for x, c in enumerate(row):
            if c == ".":
                continue
            img.putpixel((x, y), colours[c] + (255,))
    return img


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    sources = dict(VANILLA)
    sources.update({metal: f"hardwrought:{metal}_ingot" for metal in MOD_METALS})
    for metal, source in sources.items():
        shades = palette(ingot(source))
        for form, mask in (("rod", ROD), ("plate", PLATE)):
            name = f"{metal}_{form}"
            paint(mask, shades).save(ASSETS / "textures/item" / f"{name}.png")
            write(ASSETS / "models/item" / f"{name}.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"hardwrought:item/{name}"}})
            write(ASSETS / "items" / f"{name}.json",
                  {"model": {"type": "minecraft:model", "model": f"hardwrought:item/{name}"}})
    print(f"rods and plates written for {len(sources)} metals")


if __name__ == "__main__":
    main()
