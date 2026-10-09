"""Writes the storage blocks of the metals that have none from vanilla (see metallurgy/MetalBlocks).

The texture is vanilla's iron block, shade for shade in the colours of the metal's own bar, so a
block looks like nine of what went into it. Also written: block state, models, item definition, loot
table, both recipes, the pickaxe tag, the common storage-block tags and the names. Safe to run again:

    python tools/metal_block_assets.py
"""
import json
import os

from PIL import Image

import alloy_assets as base

MOD = base.MOD
ASSETS = os.path.join(base.ASSETS, MOD)
DATA = base.DATA

# Kept in step with MetalBlocks.materials(): bronze, the steels, then every Metal but mercury.
MATERIALS = ["bronze", "steel", "stainless_steel", "tungsten_steel", "tin", "zinc", "lead", "manganese", "magnesium",
             "aluminum", "nickel", "cobalt", "chromium", "titanium", "tungsten", "uranium", "thorium", "platinum",
             "silver", "mithril", "adamantium"]


def palette(material):
    """Five shades of a metal, dark to light, read off its bar."""
    bar = Image.open(os.path.join(ASSETS, "textures/item", material + "_ingot.png")).convert("RGBA")
    shades = sorted((pixel[:3] for pixel in bar.getdata() if pixel[3] > 200), key=sum)
    return [shades[min(len(shades) - 1, int(i * (len(shades) - 1) / 4))] for i in range(5)]


def main():
    iron = base.image("minecraft", "block/iron_block")
    lang = {code: json.load(open(os.path.join(ASSETS, "lang", code + ".json"), encoding="utf-8")) for code in ("de_de", "en_us")}
    tag = os.path.join(DATA, "minecraft/tags/block/mineable/pickaxe.json")
    mineable = json.load(open(tag, encoding="utf-8"))
    missing = [m for m in MATERIALS if not os.path.exists(os.path.join(ASSETS, "textures/item", m + "_ingot.png"))]
    if missing:
        raise SystemExit(f"no bar texture for {missing}")
    for material in MATERIALS:
        name = material + "_block"
        base.PALETTES[material] = palette(material)
        base.save(base.recolour(iron, material), MOD, f"block/{name}")
        base.write_json(os.path.join(ASSETS, "models/block", name + ".json"),
                        {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{name}"}})
        base.write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        base.write_json(os.path.join(ASSETS, "items", name + ".json"),
                        {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})
        base.write_json(os.path.join(DATA, MOD, "loot_table/blocks", name + ".json"), {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        bar = f"{MOD}:{material}_ingot"
        base.write_json(os.path.join(DATA, MOD, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###", "###", "###"],
            "key": {"#": bar}, "result": {"id": f"{MOD}:{name}"}})
        base.write_json(os.path.join(DATA, MOD, "recipe", f"{material}_ingot_from_block.json"), {
            "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [f"{MOD}:{name}"],
            "result": {"id": bar, "count": 9}})
        for kind in ("block", "item"):
            base.write_json(os.path.join(DATA, "c/tags", kind, "storage_blocks", material + ".json"),
                            {"values": [f"{MOD}:{name}"]})
        if f"{MOD}:{name}" not in mineable["values"]:
            mineable["values"].append(f"{MOD}:{name}")
        # "Zinnbarren" -> "Zinnblock", "Tin Ingot" -> "Block of Tin".
        german = lang["de_de"][f"item.{MOD}.{material}_ingot"]
        english = lang["en_us"][f"item.{MOD}.{material}_ingot"]
        lang["de_de"][f"block.{MOD}.{name}"] = (german[:-len("barren")] if german.lower().endswith("barren") else german + "-") + "block"
        lang["en_us"][f"block.{MOD}.{name}"] = "Block of " + (english[:-len(" Ingot")] if english.endswith(" Ingot") else english)
    base.write_json(tag, mineable)
    for code, entries in lang.items():
        path = os.path.join(ASSETS, "lang", code + ".json")
        raw = open(path, "rb").read()
        with open(path, "w", encoding="utf-8", newline="\r\n" if b"\r\n" in raw else "\n") as file:
            json.dump(entries, file, indent=2, ensure_ascii=False)
            file.write("\n")
    print(f"{len(MATERIALS)} metal blocks written")


if __name__ == "__main__":
    main()
