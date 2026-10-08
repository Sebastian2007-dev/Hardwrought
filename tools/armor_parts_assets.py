"""Writes what forged armor needs, and takes diamond equipment out of the game.

Armor is forged in parts on the anvil, like tool heads (smithing.ToolParts): a helmet shell, a cuirass,
greaves and sabatons, of every metal that has armor. At the bench a part and some leather — padding and
straps — make the piece of armor, keeping how well the part was forged. This writes:

- the part textures: each piece's own icon, darker and duller, as metal fresh off the anvil;
- their models and item definitions, and their names in both languages;
- the recipes that put a part and leather together, over vanilla's for iron, gold and copper and over
  the plate recipes of the metals above iron (tools/alloy_assets.py);
- recipes that never load (Fabric's "fabric:false" load condition) over vanilla's diamond tools,
  weapons and armor, and over the recipe book's unlocks for them;
- netherite upgrades from tungsten steel instead of diamond, and from iron where tungsten steel has
  no such piece.

Safe to run again. Run from the project root, then python tools/glow_textures.py for the hot look:

    python tools/armor_parts_assets.py
"""
import io
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import alloy_assets as alloys  # noqa: E402

from PIL import Image, ImageEnhance  # noqa: E402

MOD = alloys.MOD
DATA = alloys.DATA

# Part id, the armor piece it becomes, leather for padding and straps, German, English.
PARTS = [
    ("helmet_shell", "helmet", 1, "helmschale", "Helmet Shell"),
    ("cuirass", "chestplate", 3, "kürass", "Cuirass"),
    ("greaves", "leggings", 2, "beinschienen", "Greaves"),
    ("sabatons", "boots", 1, "schuhplatten", "Sabatons"),
]
# Metal: (namespace and prefix of its armor, prefix of its parts, German, English)
METALS = {
    "iron": ("minecraft", "iron", "Eisen", "Iron"),
    "gold": ("minecraft", "golden", "Gold", "Gold"),
    "copper": ("minecraft", "copper", "Kupfer", "Copper"),
    "steel": (MOD, "steel", "Stahl", "Steel"),
    "stainless_steel": (MOD, "stainless_steel", "Edelstahl", "Stainless Steel"),
    "titanium": (MOD, "titanium", "Titan", "Titanium"),
    "tungsten_steel": (MOD, "tungsten_steel", "Wolframstahl", "Tungsten Steel"),
}

DIAMOND_GEAR = ["sword", "pickaxe", "axe", "shovel", "hoe", "spear", "helmet", "chestplate", "leggings", "boots"]
# Netherite piece: what is upgraded into it now.
NETHERITE_BASE = {
    "sword": f"{MOD}:tungsten_steel_sword", "pickaxe": f"{MOD}:tungsten_steel_pickaxe",
    "axe": f"{MOD}:tungsten_steel_axe", "shovel": f"{MOD}:tungsten_steel_shovel", "hoe": f"{MOD}:tungsten_steel_hoe",
    "helmet": f"{MOD}:tungsten_steel_helmet", "chestplate": f"{MOD}:tungsten_steel_chestplate",
    "leggings": f"{MOD}:tungsten_steel_leggings", "boots": f"{MOD}:tungsten_steel_boots",
    "spear": "minecraft:iron_spear", "horse_armor": "minecraft:iron_horse_armor",
    "nautilus_armor": "minecraft:iron_nautilus_armor",
}


def vanilla_texture(path):
    return Image.open(io.BytesIO(alloys.JAR.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")


def rough(source):
    """Fresh off the anvil: darker, duller, its highlights not yet polished up."""
    img = ImageEnhance.Color(source).enhance(0.7)
    img = ImageEnhance.Brightness(img).enhance(0.78)
    return ImageEnhance.Contrast(img).enhance(0.85)


def recipe(path, data):
    alloys.write_json(os.path.join(DATA, path), data)


def main():
    for metal, (namespace, prefix, de, en) in METALS.items():
        for part, piece, leather, pde, pen in PARTS:
            source = vanilla_texture(f"item/{prefix}_{piece}") if namespace == "minecraft" \
                else alloys.image(MOD, f"item/{prefix}_{piece}")
            alloys.save(rough(source), MOD, f"item/{metal}_{part}")
            alloys.item(f"{metal}_{part}")
            # German compounds start lower case after the metal: "Eisenhelmschale", "Stahlkürass".
            alloys.name(f"item.{MOD}.{metal}_{part}", f"{de}{pde}", f"{en} {pen}")
            recipe(f"{namespace}/recipe/{prefix}_{piece}.json", {
                "type": "minecraft:crafting_transmute", "category": "equipment",
                "input": f"{MOD}:{metal}_{part}", "material": "minecraft:leather",
                "material_count": {"min": leather, "max": leather},
                "result": {"id": f"{namespace}:{prefix}_{piece}"}})

    never = [{"condition": "fabric:false"}]
    for gear in DIAMOND_GEAR:
        recipe(f"minecraft/recipe/diamond_{gear}.json", {
            "fabric:load_conditions": never,
            "type": "minecraft:crafting_shapeless", "category": "equipment",
            "ingredients": ["minecraft:diamond"], "result": {"id": f"minecraft:diamond_{gear}"}})
    # The book's unlock for each would name a recipe that is not there; those never load either.
    for gear in DIAMOND_GEAR:
        group = "tools" if gear in ("pickaxe", "axe", "shovel", "hoe") else "combat"
        recipe(f"minecraft/advancement/recipes/{group}/diamond_{gear}.json", {
            "fabric:load_conditions": never,
            "parent": "minecraft:recipes/root",
            "criteria": {"impossible": {"trigger": "minecraft:impossible"}},
            "requirements": [["impossible"]]})
    for piece, base in NETHERITE_BASE.items():
        recipe(f"minecraft/recipe/netherite_{piece}_smithing.json", {
            "type": "minecraft:smithing_transform",
            "addition": "#minecraft:netherite_tool_materials",
            "base": base,
            "result": {"id": f"minecraft:netherite_{piece}"},
            "template": "minecraft:netherite_upgrade_smithing_template"})

    for code, entries in alloys.lang.items():
        path = os.path.join(alloys.ASSETS, MOD, "lang", code + ".json")
        import json
        with open(path, encoding="utf-8") as file:
            existing = json.load(file)
        existing.update(entries)
        alloys.write_json(path, existing)
    print("armor parts written, diamond equipment taken out")


if __name__ == "__main__":
    main()
