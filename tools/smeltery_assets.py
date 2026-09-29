"""Writes everything the smeltery needs besides its code.

Textures (the bricks from vanilla's stone bricks darkened to soot-brown, the molten metals from vanilla's
animated lava, re-tinted per metal), block states, models, item definitions, loot tables, the
pickaxe tag, the recipes, and the names in both languages. Safe to run again:

    python tools/smeltery_assets.py
"""
import io
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/hardwrought")
DATA = os.path.join(ROOT, "src/main/resources/data")
MOD = "hardwrought"
VANILLA_JAR_DIR = os.path.join(ROOT, ".gradle/loom-cache/minecraftMaven/net/minecraft")

# Kept in step with MoltenMetals.ORDER and MoltenColors.
MOLTEN = [("iron", 0xD9521E, "Eisen", "Iron"), ("gold", 0xF6C83A, "Gold", "Gold"),
          ("copper", 0xE8793A, "Kupfer", "Copper"), ("bronze", 0xD9953A, "Bronze", "Bronze"),
          ("steel", 0xC9C2B8, "Stahl", "Steel"), ("stainless_steel", 0xE6E9EE, "Edelstahl", "Stainless Steel"),
          ("tungsten_steel", 0x6E7483, "Wolframstahl", "Tungsten Steel"), ("titanium", 0xC7B8D8, "Titan", "Titanium"),
          ("tungsten", 0x8C8F99, "Wolfram", "Tungsten"), ("tin", 0xDCDDE8, "Zinn", "Tin"),
          ("zinc", 0xB8C8C8, "Zink", "Zinc"), ("lead", 0x7C7A96, "Blei", "Lead"),
          ("manganese", 0xB89A96, "Mangan", "Manganese"), ("magnesium", 0xEDEDE0, "Magnesium", "Magnesium"),
          ("aluminum", 0xD8DDE3, "Aluminium", "Aluminum"), ("nickel", 0xD3CFA8, "Nickel", "Nickel"),
          ("cobalt", 0x5A7DD8, "Kobalt", "Cobalt"), ("chromium", 0xC8D8E4, "Chrom", "Chromium"),
          ("uranium", 0x7FD04F, "Uran", "Uranium"), ("thorium", 0x9FA8A0, "Thorium", "Thorium"),
          ("platinum", 0xD8ECF4, "Platin", "Platinum"), ("netherite", 0x5A4448, "Netherit", "Netherite"),
          ("carbon", 0x2A2626, "Kohlenstoff", "Carbon")]


def jar():
    for base, _, files in os.walk(VANILLA_JAR_DIR):
        for name in files:
            if name.startswith("minecraft-clientOnly") and name.endswith(".jar"):
                return zipfile.ZipFile(os.path.join(base, name))
    raise SystemExit("Minecraft client jar not found; run a Gradle build first")


JAR = jar()


def vanilla(path):
    return Image.open(io.BytesIO(JAR.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as file:
        json.dump(data, file, indent=2, ensure_ascii=False)
        file.write("\n")


def save(img, path):
    target = os.path.join(ASSETS, "textures", path + ".png")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    img.save(target)


def recolour(img, dark, light):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if not a:
                continue
            t = (r + g + b) / 765
            px[x, y] = tuple(int(dark[k] + (light[k] - dark[k]) * t) for k in range(3)) + (a,)
    return out


lang = {"de_de": {}, "en_us": {}}


def name(key, german, english):
    lang["de_de"][key] = german
    lang["en_us"][key] = english


def textures():
    bricks = recolour(vanilla("block/stone_bricks"), (22, 18, 16), (122, 108, 96))
    save(bricks, "block/smeltery_bricks")
    glass = vanilla("block/glass")
    frame = glass.copy()
    fp = frame.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                fp[x, y] = (60, 50, 44, 255)
    save(frame, "block/smeltery_glass")

    # Dark forged iron for the hardware.  Keeping this separate from the brick texture makes the
    # faucet and casting table readable at a glance, even in the deliberately dim smeltery room.
    metal = recolour(vanilla("block/iron_block"), (28, 25, 23), (104, 91, 78))
    mp = metal.load()
    for i in range(16):
        mp[i, 0] = (133, 111, 88, 255)
        mp[i, 15] = (22, 19, 18, 255)
    save(metal, "block/smeltery_metal")
    for lit in (False, True):
        front = bricks.copy()
        p = front.load()
        for y in range(5, 12):
            for x in range(3, 13):
                edge = y in (5, 11) or x in (3, 12)
                inner = (255, 150, 40, 255) if lit else (30, 24, 22, 255)
                p[x, y] = (40, 34, 30, 255) if edge else (inner if (x + y) % 3 else (inner[0] // 2, inner[1] // 2, inner[2] // 2, 255))
        save(front, "block/smeltery_controller_front" + ("_lit" if lit else ""))
    drain = bricks.copy()
    p = drain.load()
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 14:
                p[x, y] = (18, 14, 12, 255)
    save(drain, "block/smeltery_drain")

    lava = vanilla("block/lava_still")
    lava_meta = json.loads(JAR.read("assets/minecraft/textures/block/lava_still.png.mcmeta"))
    for material, colour, _de, _en in MOLTEN:
        c = ((colour >> 16) & 255, (colour >> 8) & 255, colour & 255)
        out = lava.copy()
        px = out.load()
        for y in range(out.height):
            for x in range(out.width):
                r, g, b, a = px[x, y]
                lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
                f = 0.45 + 0.75 * lum
                px[x, y] = tuple(min(255, int(c[k] * f)) for k in range(3)) + (a,)
        save(out, f"block/molten/{material}")
        with open(os.path.join(ASSETS, "textures/block/molten", material + ".png.mcmeta"), "w", newline="\n") as f:
            json.dump(lava_meta, f)

    coke = recolour(vanilla("item/coal"), (30, 32, 38), (150, 156, 168))
    save(coke, "item/coke")
    for key, body, hollow in (("unfired_ingot_cast", (206, 190, 162), (154, 132, 103)),
                              ("ingot_cast", (160, 91, 55), (75, 39, 25))):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        q = img.load()
        for y in range(4, 13):
            for x in range(1, 15):
                q[x, y] = body + (255,)
        for y in range(6, 11):
            for x in range(4, 12):
                q[x, y] = hollow + (255,)
        for x in range(1, 15):
            q[x, 4] = tuple(min(255, v + 25) for v in body) + (255,)
            q[x, 12] = tuple(v * 3 // 4 for v in body) + (255,)
        save(img, f"item/{key}")

        # Tileable material and recessed cavity used by the actual 3-D item model.
        surface = Image.new("RGBA", (16, 16), body + (255,))
        sp = surface.load()
        for y in range(16):
            for x in range(16):
                grain = ((x * 7 + y * 11 + (x ^ y) * 3) % 13) - 6
                edge = 9 if y == 0 else (-12 if y == 15 else 0)
                sp[x, y] = tuple(max(0, min(255, c + grain + edge)) for c in body) + (255,)
        save(surface, f"item/{key}_material")

        cavity = Image.new("RGBA", (16, 16), hollow + (255,))
        cp = cavity.load()
        for y in range(16):
            for x in range(16):
                shade = -12 if x in (0, 1) or y in (0, 1) else (7 if x == 15 or y == 15 else 0)
                cp[x, y] = tuple(max(0, min(255, c + shade)) for c in hollow) + (255,)
        save(cavity, f"item/{key}_cavity")


def models():
    blocks = ASSETS + "/blockstates"
    bm = ASSETS + "/models/block"
    for key in ("smeltery_bricks", "smeltery_glass", "smeltery_drain"):
        write_json(f"{bm}/{key}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{key}"}})
        write_json(f"{blocks}/{key}.json", {"variants": {"": {"model": f"{MOD}:block/{key}"}}})
    for lit in ("", "_lit"):
        write_json(f"{bm}/smeltery_controller{lit}.json", {"parent": "minecraft:block/orientable", "textures": {
            "front": f"{MOD}:block/smeltery_controller_front{lit}", "side": f"{MOD}:block/smeltery_bricks",
            "top": f"{MOD}:block/smeltery_bricks"}})
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in rotations.items():
        for lit in ("false", "true"):
            model = {"model": f"{MOD}:block/smeltery_controller" + ("_lit" if lit == "true" else "")}
            if y:
                model["y"] = y
            variants[f"facing={facing},lit={lit}"] = model
    write_json(f"{blocks}/smeltery_controller.json", {"variants": variants})

    texture = f"{MOD}:block/smeltery_bricks"
    metal = f"{MOD}:block/smeltery_metal"
    faces = {side: {"texture": "#b"} for side in ("north", "south", "east", "west", "up", "down")}
    metal_faces = {side: {"texture": "#m"} for side in ("north", "south", "east", "west", "up", "down")}
    write_json(f"{bm}/faucet.json", {"parent": "minecraft:block/block", "textures": {"b": texture, "m": metal,
                                                                                         "particle": metal},
               "elements": [
                   {"name": "wall collar", "from": [4, 10, 13], "to": [12, 15, 16], "faces": metal_faces},
                   {"name": "upper channel", "from": [5, 11, 7], "to": [11, 14, 14], "faces": metal_faces},
                   {"name": "down spout", "from": [6, 7, 6], "to": [10, 13, 10], "faces": metal_faces},
                   {"name": "spout lip", "from": [5, 6, 5], "to": [11, 8, 11], "faces": metal_faces},
                   {"name": "handle stem", "from": [7, 14, 9], "to": [9, 16, 11], "faces": metal_faces},
                   {"name": "handle", "from": [4, 15, 9], "to": [12, 16, 11], "faces": metal_faces}]})
    write_json(f"{blocks}/faucet.json", {"variants": {f"facing={f}": ({"model": f"{MOD}:block/faucet", "y": y} if y
                                                                       else {"model": f"{MOD}:block/faucet"})
                                                      for f, y in rotations.items()}})
    write_json(f"{bm}/casting_table.json", {"parent": "minecraft:block/block", "textures": {"b": texture, "m": metal,
                                                                                               "particle": texture},
               "elements": [
                   {"name": "stone bed", "from": [1, 10, 1], "to": [15, 13, 15], "faces": faces},
                   {"name": "front rim", "from": [0, 13, 0], "to": [16, 15, 2], "faces": metal_faces},
                   {"name": "back rim", "from": [0, 13, 14], "to": [16, 15, 16], "faces": metal_faces},
                   {"name": "left rim", "from": [0, 13, 2], "to": [2, 15, 14], "faces": metal_faces},
                   {"name": "right rim", "from": [14, 13, 2], "to": [16, 15, 14], "faces": metal_faces},
                   {"name": "front left leg", "from": [1, 0, 1], "to": [4, 10, 4], "faces": faces},
                   {"name": "front right leg", "from": [12, 0, 1], "to": [15, 10, 4], "faces": faces},
                   {"name": "back left leg", "from": [1, 0, 12], "to": [4, 10, 15], "faces": faces},
                   {"name": "back right leg", "from": [12, 0, 12], "to": [15, 10, 15], "faces": faces},
                   {"name": "front brace", "from": [3, 5, 2], "to": [13, 7, 3], "faces": metal_faces},
                   {"name": "back brace", "from": [3, 5, 13], "to": [13, 7, 14], "faces": metal_faces},
                   {"name": "left brace", "from": [2, 5, 3], "to": [3, 7, 13], "faces": metal_faces},
                   {"name": "right brace", "from": [13, 5, 3], "to": [14, 7, 13], "faces": metal_faces}]})
    write_json(f"{blocks}/casting_table.json", {"variants": {"": {"model": f"{MOD}:block/casting_table"}}})

    molten_variants = {}
    for index, (material, _c, _de, _en) in enumerate(MOLTEN):
        write_json(f"{bm}/molten/{material}.json", {"parent": "minecraft:block/cube_all",
                                                     "textures": {"all": f"{MOD}:block/molten/{material}"}})
        molten_variants[f"metal={index}"] = {"model": f"{MOD}:block/molten/{material}"}
    write_json(f"{blocks}/molten_metal.json", {"variants": molten_variants})

    for key in ("smeltery_bricks", "smeltery_glass", "smeltery_drain", "smeltery_controller", "faucet", "casting_table"):
        write_json(f"{ASSETS}/items/{key}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{key}"}})
        write_json(f"{DATA}/{MOD}/loot_table/blocks/{key}.json", {"type": "minecraft:block", "pools": [{"rolls": 1,
                   "entries": [{"type": "minecraft:item", "name": f"{MOD}:{key}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(f"{ASSETS}/models/item/coke.json", {"parent": "minecraft:item/generated",
                                                    "textures": {"layer0": f"{MOD}:item/coke"}})
    for key in ("unfired_ingot_cast", "ingot_cast"):
        material = f"{MOD}:item/{key}_material"
        # A thin open frame, inspired by the readable Tinkers-style cast silhouette.  There is no
        # fake backing plate: on the casting table the refractory bed and poured metal remain visible
        # through the opening.  The table renderer turns the south-facing item upward.
        rim_faces = {side: {"texture": "#material"} for side in ("north", "south", "east", "west", "up", "down")}
        write_json(f"{ASSETS}/models/item/{key}.json", {
            "parent": "minecraft:item/handheld",
            "ambientocclusion": True,
            "textures": {"material": material, "particle": material},
            "elements": [
                {"name": "lower rail", "from": [2, 3, 8], "to": [14, 4.5, 9], "faces": rim_faces},
                {"name": "upper rail", "from": [2, 11.5, 8], "to": [14, 13, 9], "faces": rim_faces},
                {"name": "left rail", "from": [2, 4.5, 8], "to": [3.5, 11.5, 9], "faces": rim_faces},
                {"name": "right rail", "from": [12.5, 4.5, 8], "to": [14, 11.5, 9], "faces": rim_faces}
            ],
            "display": {
                "gui": {"rotation": [25, 0, 0], "translation": [0, 0, 0], "scale": [1.05, 1.05, 1.05]},
                "ground": {"rotation": [-90, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
                "fixed": {"rotation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
                "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.65, 0.65, 0.65]},
                "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.65, 0.65, 0.65]},
                "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1, 3, 1], "scale": [0.75, 0.75, 0.75]},
                "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [-1, 3, 1], "scale": [0.75, 0.75, 0.75]}
            }
        })

    for key in ("coke", "unfired_ingot_cast", "ingot_cast"):
        write_json(f"{ASSETS}/items/{key}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{key}"}})

    tag = f"{DATA}/minecraft/tags/block/mineable/pickaxe.json"
    values = json.load(open(tag, encoding="utf-8"))["values"] if os.path.exists(tag) else []
    for key in ("smeltery_bricks", "smeltery_glass", "smeltery_drain", "smeltery_controller", "faucet", "casting_table"):
        if f"{MOD}:{key}" not in values:
            values.append(f"{MOD}:{key}")
    write_json(tag, {"replace": False, "values": values})
    write_json(f"{DATA}/{MOD}/{MOD}/structure/smeltery.json", {"material": f"{MOD}:brick", "priority": 20, "blocks": [
        f"{MOD}:{key}" for key in ("smeltery_bricks", "smeltery_drain", "smeltery_controller", "casting_table")]})


def recipes():
    r = f"{DATA}/{MOD}/recipe"
    brick = f"{MOD}:refractory_brick"

    def shaped(name_, pattern, key, result, count=1):
        write_json(f"{r}/{name_}.json", {"type": "minecraft:crafting_shaped", "category": "building",
                                         "pattern": pattern, "key": key, "result": {"id": result, "count": count}})

    shaped("smeltery_bricks", ["BB", "BB"], {"B": brick}, f"{MOD}:smeltery_bricks")
    shaped("smeltery_glass", [" B ", "BGB", " B "], {"B": brick, "G": "minecraft:glass"}, f"{MOD}:smeltery_glass")
    shaped("smeltery_controller", ["BBB", "B B", "BBB"], {"B": brick}, f"{MOD}:smeltery_controller")
    shaped("smeltery_drain", ["B B", "B B", "B B"], {"B": brick}, f"{MOD}:smeltery_drain")
    shaped("faucet", ["B B", " B "], {"B": brick}, f"{MOD}:faucet")
    shaped("casting_table", ["BBB", "B B", "B B"], {"B": brick}, f"{MOD}:casting_table")
    shaped("unfired_ingot_cast", [" F ", "F F", " F "], {"F": f"{MOD}:fireclay"}, f"{MOD}:unfired_ingot_cast")
    for key, ingredient, result, time in (("ingot_cast_from_smelting", f"{MOD}:unfired_ingot_cast", f"{MOD}:ingot_cast", 200),
                                          ("coke_from_smelting", "minecraft:coal", f"{MOD}:coke", 400)):
        write_json(f"{r}/{key}.json", {"type": "minecraft:smelting", "category": "misc", "cookingtime": time,
                                       "experience": 0.2, "ingredient": ingredient, "result": {"id": result}})


def languages():
    for key, de, en in (("smeltery_bricks", "Schmelzziegel", "Smeltery Bricks"),
                        ("smeltery_glass", "Schmelzglas", "Smeltery Glass"),
                        ("smeltery_drain", "Schmelzerei-Abfluss", "Smeltery Drain"),
                        ("smeltery_controller", "Schmelzerei-Controller", "Smeltery Controller"),
                        ("faucet", "Wasserhahn", "Faucet"), ("casting_table", "Gießtisch", "Casting Table"),
                        ("molten_metal", "Flüssiges Metall", "Molten Metal")):
        name(f"block.{MOD}.{key}", de, en)
    for key, de, en in (("coke", "Koks", "Coke"), ("unfired_ingot_cast", "Ungebrannte Barrenform", "Unfired Ingot Cast"),
                        ("ingot_cast", "Barrenform", "Ingot Cast")):
        name(f"item.{MOD}.{key}", de, en)
    for material, _c, de, en in MOLTEN:
        name(f"molten.{MOD}.{material}", de, en)
    name(f"container.{MOD}.smeltery", "Schmelzerei", "Smeltery")
    name(f"gui.{MOD}.smeltery.unformed", "Nicht fertig gebaut: Boden und Wände aus Schmelzziegeln um einen leeren Innenraum.",
         "Not built: a floor and walls of smeltery bricks around an empty tank.")
    name(f"gui.{MOD}.smeltery.capacity", "%s von %s mB", "%s of %s mB")
    name(f"gui.{MOD}.smeltery.fluid", "%s: %s mB (%s Barren)", "%s: %s mB (%s ingots)")
    name(f"gui.{MOD}.smeltery.heat_hint", "Schmilzt erst am vollen Schmelzpunkt. Kohle: 1250 °C, mit Blasebalg 1650 °C; Koks: 2000 °C, mit Blasebalg 3600 °C.",
         "Melts only at the full melting point. Coal: 1250 °C, with a bellows 1650 °C; coke: 2000 °C, with a bellows 3600 °C.")
    for code, entries in lang.items():
        path = os.path.join(ASSETS, "lang", code + ".json")
        existing = json.load(open(path, encoding="utf-8"))
        existing.update(entries)
        write_json(path, existing)


if __name__ == "__main__":
    textures()
    models()
    recipes()
    languages()
    print("smeltery assets written")
