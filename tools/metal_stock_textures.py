"""Write rods and plates using the exact Nichrome silhouette and shade map.

Each metal gets its ingot palette. Hot-state sprites keep the same outline.
Run from the repository root: python tools/metal_stock_textures.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/hardwrought"
VANILLA_ITEMS = ROOT / "art_source/vanilla_reference/assets/minecraft/textures/item"


def ingot(identifier):
    namespace, name = identifier.split(":", 1)
    path = VANILLA_ITEMS / f"{name}.png" if namespace == "minecraft" else ASSETS / "textures/item" / f"{name}.png"
    return Image.open(path).convert("RGBA")

VANILLA = {"iron": "minecraft:iron_ingot", "copper": "minecraft:copper_ingot", "gold": "minecraft:gold_ingot"}
MOD_METALS = ["bronze", "steel", "stainless_steel", "tungsten_steel", "tin", "zinc", "lead", "manganese", "magnesium", "aluminum", "nickel", "cobalt",
              "chromium", "titanium", "tungsten", "uranium", "thorium", "platinum", "silver", "mithril",
              "adamantium"]


def palette(image):
    pixels = sorted((image.getpixel((x, y)) for y in range(image.height) for x in range(image.width)
                     if image.getpixel((x, y))[3] > 0), key=lambda p: p[0] + p[1] + p[2])
    pick = lambda share: pixels[min(len(pixels) - 1, int(len(pixels) * share))][:3]
    return pick(0.08), pick(0.35), pick(0.65), pick(0.95)


def paint(template, shades):
    """Recolour opaque pixels while preserving Nichrome's outline and lighting."""
    values = sorted({round(0.299 * r + 0.587 * g + 0.114 * b)
                     for y in range(template.height) for x in range(template.width)
                     for r, g, b, a in (template.getpixel((x, y)),) if a >= 128})
    if len(values) < 2:
        raise ValueError("Nichrome template needs at least two visible shades")
    ranks = {value: index / (len(values) - 1) for index, value in enumerate(values)}
    result = Image.new("RGBA", template.size)
    for y in range(template.height):
        for x in range(template.width):
            r, g, b, a = template.getpixel((x, y))
            if a < 128:
                continue
            value = round(0.299 * r + 0.587 * g + 0.114 * b)
            position = ranks[value] * (len(shades) - 1)
            lower = min(int(position), len(shades) - 2)
            fraction = position - lower
            colour = tuple(round(shades[lower][channel] * (1 - fraction)
                                 + shades[lower + 1][channel] * fraction)
                           for channel in range(3))
            result.putpixel((x, y), colour + (255,))
    return result


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def glow(image):
    """The same pale value map used by the forge's heat tint."""
    out = Image.new("RGBA", image.size)
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            if not a:
                continue
            value = round(255 * (0.55 + 0.45 * (0.299 * r + 0.587 * g + 0.114 * b) / 255))
            out.putpixel((x, y), (value, value, value, a))
    return out


def main():
    sources = dict(VANILLA)
    sources.update({metal: f"hardwrought:{metal}_ingot" for metal in MOD_METALS})
    sources["nichrome"] = "hardwrought:nichrome_ingot"
    templates = {form: Image.open(ASSETS / "textures/item" / f"nichrome_{form}.png").convert("RGBA")
                 for form in ("rod", "plate")}
    for metal, source in sources.items():
        parent_ingot = ingot(source)
        shades = palette(parent_ingot)
        for form, template in templates.items():
            name = f"{metal}_{form}"
            sprite = template.copy() if metal == "nichrome" else paint(template, shades)
            sprite.save(ASSETS / "textures/item" / f"{name}.png")
            write(ASSETS / "models/item" / f"{name}.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"hardwrought:item/{name}"}})
            glow_name = f"hardwrought_{name}"
            glow_path = ASSETS / "textures/item/glow" / f"{glow_name}.png"
            glow_path.parent.mkdir(parents=True, exist_ok=True)
            glow(sprite).save(glow_path)
            write(ASSETS / "models/item/glow" / f"{glow_name}.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"hardwrought:item/glow/{glow_name}"}})
            write(ASSETS / "items" / f"{name}.json",
                  {"model": {"type": "minecraft:condition", "property": "hardwrought:hot",
                             "on_true": {"type": "minecraft:model", "model": f"hardwrought:item/glow/{glow_name}",
                                         "tints": [{"type": "hardwrought:heat"}]},
                             "on_false": {"type": "minecraft:model", "model": f"hardwrought:item/{name}"}}})
    print(f"rods and plates written for {len(sources)} metals")


if __name__ == "__main__":
    main()
