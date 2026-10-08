"""Generate the data and vanilla-style textures for every Hardwrought raw-metal storage block."""
import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/hardwrought"
DATA = RES / "data"
JAR = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"

METALS = {
    "stone": ["tin", "zinc", "lead", "manganese", "magnesium", "aluminum"],
    "iron": ["nickel", "cobalt", "chromium", "mercury", "titanium", "uranium", "thorium", "silver"],
    "diamond": ["tungsten", "platinum", "mithril", "adamantium"],
}
ALL = [metal for metals in METALS.values() for metal in metals]


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def palette(sprite):
    colors = sorted((r, g, b) for r, g, b, a in sprite.getdata() if a > 32)
    colors.sort(key=lambda c: c[0] * 3 + c[1] * 6 + c[2])
    return [colors[int((len(colors) - 1) * point)] for point in (0.08, 0.50, 0.92)]


def mix(a, b, amount):
    return tuple(round(a[i] + (b[i] - a[i]) * amount) for i in range(3))


def recolor(template, shades):
    pixels = list(template.convert("RGBA").getdata())
    levels = [r * 0.299 + g * 0.587 + b * 0.114 for r, g, b, a in pixels if a]
    low, high = min(levels), max(levels)
    result = Image.new("RGBA", template.size)
    out = []
    for r, g, b, a in pixels:
        value = ((r * 0.299 + g * 0.587 + b * 0.114) - low) / max(1, high - low)
        color = mix(shades[0], shades[1], value * 2) if value < 0.5 else mix(shades[1], shades[2], value * 2 - 1)
        out.append((*color, a))
    result.putdata(out)
    return result


def tag_values(values):
    return {"replace": False, "values": values}


def generate():
    with zipfile.ZipFile(JAR) as jar:
        with jar.open("assets/minecraft/textures/block/raw_iron_block.png") as source:
            template = Image.open(source).convert("RGBA")

    for metal in ALL:
        block = f"raw_{metal}_block"
        raw = f"raw_{metal}"
        sprite = Image.open(ASSETS / f"textures/item/{raw}.png").convert("RGBA")
        target = ASSETS / f"textures/block/{block}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        recolor(template, palette(sprite)).save(target)

        write(ASSETS / f"blockstates/{block}.json",
              {"variants": {"": {"model": f"hardwrought:block/{block}"}}})
        write(ASSETS / f"models/block/{block}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"hardwrought:block/{block}"}})
        write(ASSETS / f"items/{block}.json",
              {"model": {"type": "minecraft:model", "model": f"hardwrought:block/{block}"}})
        write(DATA / f"hardwrought/loot_table/blocks/{block}.json", {
            "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
                {"type": "minecraft:item", "name": f"hardwrought:{block}"}]}]})
        write(DATA / f"hardwrought/recipe/{block}.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###", "###", "###"],
            "key": {"#": f"hardwrought:{raw}"}, "result": {"id": f"hardwrought:{block}"}})
        write(DATA / f"hardwrought/recipe/{raw}_from_block.json", {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [f"hardwrought:{block}"], "result": {"id": f"hardwrought:{raw}", "count": 9}})
        write(DATA / f"c/tags/item/raw_materials/{metal}.json",
              tag_values([f"hardwrought:{raw}"]))
        write(DATA / f"c/tags/block/storage_blocks/raw_{metal}.json",
              tag_values([f"hardwrought:{block}"]))
        write(DATA / f"c/tags/item/storage_blocks/raw_{metal}.json",
              tag_values([f"hardwrought:{block}"]))

    for metal in ("iron", "copper", "gold"):
        write(DATA / f"hardwrought/recipe/raw_{metal}_block.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###", "###", "###"],
            "key": {"#": f"minecraft:raw_{metal}"}, "result": {"id": f"minecraft:raw_{metal}_block"}})
        write(DATA / f"hardwrought/recipe/raw_{metal}_from_block.json", {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [f"minecraft:raw_{metal}_block"],
            "result": {"id": f"minecraft:raw_{metal}", "count": 9}})

    ids = [f"hardwrought:raw_{metal}_block" for metal in ALL]
    write(DATA / "hardwrought/tags/block/raw_metal_blocks.json", tag_values(ids))
    write(DATA / "hardwrought/tags/item/raw_metal_blocks.json", tag_values(
        ["minecraft:raw_iron_block", "minecraft:raw_copper_block", "minecraft:raw_gold_block"] + ids))
    for hardness, metals in METALS.items():
        write(DATA / f"hardwrought/tags/block/raw_metal_blocks/{hardness}.json",
              tag_values([f"hardwrought:raw_{metal}_block" for metal in metals]))
    optional_raw = [{"id": f"#c:raw_materials/{metal}", "required": False} for metal in ALL]
    optional_blocks = [{"id": f"#c:storage_blocks/raw_{metal}", "required": False} for metal in ALL]
    write(DATA / "c/tags/item/raw_materials.json", tag_values(optional_raw))
    write(DATA / "c/tags/block/storage_blocks.json", tag_values(optional_blocks))
    write(DATA / "c/tags/item/storage_blocks.json", tag_values(optional_blocks))


if __name__ == "__main__":
    generate()
    print(f"generated {len(ALL)} raw-metal blocks and recipes for 3 vanilla raw blocks")
