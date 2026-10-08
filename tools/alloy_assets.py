"""Writes everything the metals above iron need: steel, stainless steel, titanium and tungsten steel.

Textures are the iron ones recoloured: every grey (metal) pixel is mapped by its brightness onto the
metal's own shades, every coloured pixel (a wooden handle, a leather strap) is left as it is. Also
written: item models and definitions, the
armor's equipment assets, the recipes, tags, material definitions, weapon and armor profiles, item
weights and the names in both languages. The ingots themselves come out of the smeltery
(tools/smeltery_assets.py).

Safe to run again. Run from the project root, then python tools/glow_textures.py for the hot look:

    python tools/alloy_assets.py
"""
import colorsys
import io
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets")
DATA = os.path.join(ROOT, "src/main/resources/data")
MOD = "hardwrought"
VANILLA_JAR_DIR = os.path.join(ROOT, ".gradle/loom-cache/minecraftMaven/net/minecraft")

# Dark to light. Steel is a cool blue-grey, stainless bright and neutral, titanium a warm grey with a
# violet cast, tungsten steel a dark gunmetal.
PALETTES = {
    "steel": [(28, 32, 40), (66, 74, 86), (112, 122, 136), (164, 174, 188), (214, 222, 232)],
    "stainless_steel": [(58, 60, 64), (118, 122, 128), (178, 182, 188), (222, 226, 230), (252, 253, 255)],
    "titanium": [(42, 38, 50), (92, 86, 104), (140, 132, 152), (188, 180, 198), (230, 224, 236)],
    "tungsten_steel": [(16, 18, 22), (40, 44, 52), (72, 78, 88), (108, 116, 128), (152, 160, 172)],
}
NAMES = {
    "steel": ("Stahl", "Steel"),
    "stainless_steel": ("Edelstahl", "Stainless Steel"),
    "titanium": ("Titan", "Titanium"),
    "tungsten_steel": ("Wolframstahl", "Tungsten Steel"),
}
PARTS = ["pickaxe_head", "axe_head", "shovel_head", "hoe_head", "sword_blade", "dagger_blade",
         "greatsword_blade", "halberd_head"]
PART_NAMES = {
    "pickaxe_head": ("spitzhackenkopf", "Pickaxe Head"), "axe_head": ("axtkopf", "Axe Head"),
    "shovel_head": ("schaufelblatt", "Shovel Head"), "hoe_head": ("hackenblatt", "Hoe Head"),
    "sword_blade": ("schwertklinge", "Sword Blade"), "dagger_blade": ("dolchklinge", "Dagger Blade"),
    "greatsword_blade": ("zweihänderklinge", "Greatsword Blade"), "halberd_head": ("hellebardenkopf", "Halberd Head"),
}
# Tool: (source namespace, source texture, part, handles, German, English)
TOOLS = {
    "pickaxe": ("minecraft", "iron_pickaxe", "pickaxe_head", 2, "spitzhacke", "Pickaxe"),
    "axe": ("minecraft", "iron_axe", "axe_head", 2, "axt", "Axe"),
    "shovel": ("minecraft", "iron_shovel", "shovel_head", 2, "schaufel", "Shovel"),
    "hoe": ("minecraft", "iron_hoe", "hoe_head", 2, "hacke", "Hoe"),
    "sword": ("minecraft", "iron_sword", "sword_blade", 1, "schwert", "Sword"),
    "dagger": (MOD, "iron_dagger", "dagger_blade", 1, "dolch", "Dagger"),
    "greatsword": (MOD, "iron_greatsword", "greatsword_blade", 2, "zweihänder", "Greatsword"),
    "halberd": (MOD, "iron_halberd", "halberd_head", 2, "hellebarde", "Halberd"),
}
ARMOR = {
    "helmet": ("helm", "Helmet", ["PPP", "P P"], 5),
    "chestplate": ("brustplatte", "Chestplate", ["P P", "PPP", "PPP"], 8),
    "leggings": ("beinschutz", "Leggings", ["PPP", "P P", "P P"], 7),
    "boots": ("stiefel", "Boots", ["P P", "P P"], 4),
}
# Weights in kg: the iron piece scaled by density (iron 7874).
DENSITY = {"steel": 7850, "stainless_steel": 8000, "titanium": 4506, "tungsten_steel": 8700}
IRON_WEIGHTS = {"pickaxe": 1.6, "axe": 1.8, "shovel": 1.3, "hoe": 1.1, "sword": 1.3, "dagger": 0.5,
                "greatsword": 3.2, "halberd": 3.6, "helmet": 2.5, "chestplate": 9.0, "leggings": 6.5, "boots": 2.2}
WEAPON_PROFILES = {"sword": "sword", "dagger": "knife", "greatsword": "two_handed", "halberd": "polearm", "axe": "axe"}


def vanilla_jar():
    for base, _, files in os.walk(VANILLA_JAR_DIR):
        for name in files:
            if name.startswith("minecraft-clientOnly") and name.endswith(".jar") and not name.endswith("-sources.jar"):
                return zipfile.ZipFile(os.path.join(base, name))
    raise SystemExit("Minecraft client jar not found; run a Gradle build first")


JAR = vanilla_jar()


def image(namespace, path):
    local = os.path.join(ASSETS, namespace, "textures", path + ".png")
    if namespace == MOD and os.path.exists(local):
        return Image.open(local).convert("RGBA")
    return Image.open(io.BytesIO(JAR.read(f"assets/{namespace}/textures/{path}.png"))).convert("RGBA")


def shade(palette, t):
    t = max(0.0, min(1.0, t)) * (len(palette) - 1)
    i = min(int(t), len(palette) - 2)
    f = t - i
    a, b = palette[i], palette[i + 1]
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def recolour(source, metal):
    palette = PALETTES[metal]
    out = source.copy()
    px = out.load()
    greys = []
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a and colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] < 0.22:
                greys.append((r + g + b) / 765)
    lo, hi = (min(greys), max(greys)) if greys else (0, 1)
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if not a or colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] >= 0.22:
                continue
            t = ((r + g + b) / 765 - lo) / max(1e-6, hi - lo)
            px[x, y] = shade(palette, t) + (a,)
    return out


def save(img, namespace, path):
    target = os.path.join(ASSETS, namespace, "textures", path + ".png")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    img.save(target)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as file:
        json.dump(data, file, indent=2, ensure_ascii=False)
        file.write("\n")


def item(name, parent="minecraft:item/generated"):
    write_json(os.path.join(ASSETS, MOD, "models/item", name + ".json"),
               {"parent": parent, "textures": {"layer0": f"{MOD}:item/{name}"}})
    definition = os.path.join(ASSETS, MOD, "items", name + ".json")
    # A definition the glow script has already wrapped is kept; it points at the same model.
    if not os.path.exists(definition):
        write_json(definition, {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}})


lang = {"de_de": {}, "en_us": {}}


def name(key, german, english):
    lang["de_de"][key] = german
    lang["en_us"][key] = english


def textures():
    for metal, (de, en) in NAMES.items():
        if metal != "titanium":
            save(recolour(image("minecraft", "item/iron_ingot"), metal), MOD, f"item/{metal}_ingot")
            item(f"{metal}_ingot")
            name(f"item.{MOD}.{metal}_ingot", f"{de}barren", f"{en} Ingot")
            for form, (fde, fen) in (("rod", ("stange", "Rod")), ("plate", ("platte", "Plate"))):
                save(recolour(image(MOD, f"item/iron_{form}"), metal), MOD, f"item/{metal}_{form}")
                item(f"{metal}_{form}")
                name(f"item.{MOD}.{metal}_{form}", f"{de}{fde}", f"{en} {fen}")
        for part in PARTS:
            save(recolour(image(MOD, f"item/iron_{part}"), metal), MOD, f"item/{metal}_{part}")
            item(f"{metal}_{part}")
            pde, pen = PART_NAMES[part]
            name(f"item.{MOD}.{metal}_{part}", f"{de}{pde}", f"{en} {pen}")
        for tool, (ns, source, _part, _handles, tde, ten) in TOOLS.items():
            save(recolour(image(ns, f"item/{source}"), metal), MOD, f"item/{metal}_{tool}")
            item(f"{metal}_{tool}", "minecraft:item/handheld")
            name(f"item.{MOD}.{metal}_{tool}", f"{de}{tde}", f"{en} {ten}")
        for piece, (ade, aen, _pattern, _plates) in ARMOR.items():
            save(recolour(image("minecraft", f"item/iron_{piece}"), metal), MOD, f"item/{metal}_{piece}")
            item(f"{metal}_{piece}")
            name(f"item.{MOD}.{metal}_{piece}", f"{de}{ade}", f"{en} {aen}")
        for layer in ("humanoid", "humanoid_leggings"):
            save(recolour(image("minecraft", f"entity/equipment/{layer}/iron"), metal), MOD,
                 f"entity/equipment/{layer}/{metal}")
        write_json(os.path.join(ASSETS, MOD, "equipment", metal + ".json"), {"layers": {
            "humanoid": [{"texture": f"{MOD}:{metal}"}],
            "humanoid_leggings": [{"texture": f"{MOD}:{metal}"}]}})



def data():
    materials = os.path.join(DATA, MOD, MOD, "materials")
    write_json(os.path.join(materials, "steel.json"), {"tier": 4, "density_kg_m3": 7850, "melting_point_c": 1450,
               "structure": {"compression_strength_mpa": 400, "tension_strength_mpa": 400, "support_distance_blocks": 10}})
    write_json(os.path.join(materials, "stainless_steel.json"), {"tier": 5, "density_kg_m3": 8000, "melting_point_c": 1450,
               "structure": {"compression_strength_mpa": 450, "tension_strength_mpa": 450, "support_distance_blocks": 10}})
    write_json(os.path.join(materials, "tungsten_steel.json"), {"tier": 6, "density_kg_m3": 8700, "melting_point_c": 1500,
               "structure": {"compression_strength_mpa": 700, "tension_strength_mpa": 600, "support_distance_blocks": 12}})

    recipes = os.path.join(DATA, MOD, "recipe")
    for metal in NAMES:
        ingot = f"{MOD}:titanium_ingot" if metal == "titanium" else f"{MOD}:{metal}_ingot"
        write_json(os.path.join(DATA, MOD, "tags/item", f"{metal}_tool_materials.json"), {"values": [ingot]})
        for tool, (_ns, _source, part, handles, _de, _en) in TOOLS.items():
            write_json(os.path.join(recipes, f"{metal}_{tool}.json"), {
                "type": "minecraft:crafting_transmute", "category": "equipment",
                "input": f"{MOD}:{metal}_{part}", "material": "minecraft:stick",
                "material_count": {"min": handles, "max": handles},
                "result": {"id": f"{MOD}:{metal}_{tool}"}})
        # Armor is forged in parts and put together with leather: tools/armor_parts_assets.py writes its recipes.

    profiles = os.path.join(DATA, MOD, MOD)
    for tool, profile in WEAPON_PROFILES.items():
        path = os.path.join(profiles, "weapon_profiles", profile + ".json")
        with open(path, encoding="utf-8") as file:
            profile_json = json.load(file)
        for metal in NAMES:
            entry = f"{MOD}:{metal}_{tool}"
            if entry not in profile_json["items"]:
                profile_json["items"].append(entry)
        write_json(path, profile_json)
    path = os.path.join(profiles, "armor_profiles", "plate.json")
    with open(path, encoding="utf-8") as file:
        plate = json.load(file)
    for metal in NAMES:
        for piece in ARMOR:
            entry = f"{MOD}:{metal}_{piece}"
            if entry not in plate["items"]:
                plate["items"].append(entry)
    write_json(path, plate)

    weights = {}
    for metal in NAMES:
        factor = DENSITY[metal] / 7874
        for tool in TOOLS:
            weights[f"{MOD}:{metal}_{tool}"] = round(IRON_WEIGHTS[tool] * factor, 2)
        for piece in ARMOR:
            weights[f"{MOD}:{metal}_{piece}"] = round(IRON_WEIGHTS[piece] * factor, 2)
    write_json(os.path.join(profiles, "item_weights", "alloy_equipment.json"), {"weights": weights})


def languages():
    name(f"message.{MOD}.rust_ground", "Der Rost ist abgeschliffen.", "The rust is ground off.")
    name(f"tooltip.{MOD}.rust", "Rost: %s %%", "Rust: %s %%")
    name(f"tooltip.{MOD}.rusts", "Rostet bei Nässe", "Rusts when wet")
    for code, entries in lang.items():
        path = os.path.join(ASSETS, MOD, "lang", code + ".json")
        with open(path, encoding="utf-8") as file:
            existing = json.load(file)
        existing.update(entries)
        write_json(path, existing)


if __name__ == "__main__":
    textures()
    data()
    languages()
    print("alloy assets written")
