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
          ("platinum", 0xD8ECF4, "Platin", "Platinum"), ("silver", 0xE7EEF2, "Silber", "Silver"),
          ("mithril", 0x73E6D1, "Mithril", "Mithril"), ("adamantium", 0xCA4868, "Adamantium", "Adamantium"),
          ("netherite", 0x5A4448, "Netherit", "Netherite"),
          ("carbon", 0x2A2626, "Kohlenstoff", "Carbon"),
          # Lava is not tinted: it is vanilla's own, the fuel in a smeltery tank.
          ("lava", None, "Lava", "Lava")]


# A tank face's open edges, as bits of its texture's number.
TANK_EDGES = {"top": 1, "bottom": 2, "left": 4, "right": 8}
# Which neighbour lies beyond each edge of each face, as the face's texture is drawn on the block.
TANK_FACE_EDGES = {"north": ("up", "down", "east", "west"), "south": ("up", "down", "west", "east"),
                   "east": ("up", "down", "south", "north"), "west": ("up", "down", "north", "south"),
                   "up": ("north", "south", "west", "east"), "down": ("south", "north", "west", "east")}


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

    # The tank: a ring of brick three pixels wide round a window, through which the lava is seen.
    # Tanks joined side by side lose the frame towards each other and the window runs through, so a
    # face comes in sixteen versions, one for each set of open edges (see TANK_EDGES).
    gp = glass.load()
    for mask in range(16):
        tank = bricks.copy()
        tp = tank.load()
        x0 = 0 if mask & TANK_EDGES["left"] else 3
        x1 = 15 if mask & TANK_EDGES["right"] else 12
        y0 = 0 if mask & TANK_EDGES["top"] else 3
        y1 = 15 if mask & TANK_EDGES["bottom"] else 12
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                edge = (x == x0 and not mask & TANK_EDGES["left"]) or (x == x1 and not mask & TANK_EDGES["right"]) \
                    or (y == y0 and not mask & TANK_EDGES["top"]) or (y == y1 and not mask & TANK_EDGES["bottom"])
                tp[x, y] = (40, 34, 30, 255) if edge else gp[x, y]
        save(tank, "block/smeltery_tank" if mask == 0 else f"block/smeltery_tank/{mask}")

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
        out = lava.copy()
        if colour is not None:
            c = ((colour >> 16) & 255, (colour >> 8) & 255, colour & 255)
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
    # The casts are drawn by tools/cast_assets.py.


def tank_models():
    """One model per set of joined neighbours: each face picks the frame with the right edges open."""
    sides = ("north", "east", "south", "west", "up", "down")
    variants = {}
    for bits in range(64):
        joined = {side: bool(bits >> i & 1) for i, side in enumerate(sides)}
        textures = {}
        for face, (top, bottom, left, right) in TANK_FACE_EDGES.items():
            mask = sum(TANK_EDGES[edge] for edge, side in (("top", top), ("bottom", bottom), ("left", left),
                                                           ("right", right)) if joined[side])
            textures[face] = f"{MOD}:block/smeltery_tank" if mask == 0 else f"{MOD}:block/smeltery_tank/{mask}"
        textures["particle"] = f"{MOD}:block/smeltery_tank"
        name_ = "smeltery_tank" if bits == 0 else f"smeltery_tank/{bits}"
        write_json(f"{ASSETS}/models/block/{name_}.json", {
            "parent": "minecraft:block/block", "textures": textures,
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                face: {"texture": f"#{face}", "cullface": face} for face in sides}}]})
        key = ",".join(f"{side}={'true' if joined[side] else 'false'}" for side in sides)
        variants[key] = {"model": f"{MOD}:block/{name_}"}
    write_json(f"{ASSETS}/blockstates/smeltery_tank.json", {"variants": variants})


def models():
    blocks = ASSETS + "/blockstates"
    bm = ASSETS + "/models/block"
    for key in ("smeltery_bricks", "smeltery_glass", "smeltery_drain"):
        write_json(f"{bm}/{key}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{key}"}})
        write_json(f"{blocks}/{key}.json", {"variants": {"": {"model": f"{MOD}:block/{key}"}}})
    tank_models()
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
                   {"name": "wall collar", "from": [4, 6, 13], "to": [12, 11, 16], "faces": metal_faces},
                   {"name": "upper channel", "from": [5, 7, 7], "to": [11, 10, 14], "faces": metal_faces},
                   {"name": "down spout", "from": [6, 4, 6], "to": [10, 11, 10], "faces": metal_faces},
                   {"name": "spout lip", "from": [5, 3, 5], "to": [11, 5, 11], "faces": metal_faces},
                   {"name": "handle stem", "from": [7, 10, 9], "to": [9, 13, 11], "faces": metal_faces},
                   {"name": "handle", "from": [4, 12, 9], "to": [12, 14, 11], "faces": metal_faces}]})
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

    for key in ("smeltery_bricks", "smeltery_glass", "smeltery_drain", "smeltery_tank", "smeltery_controller", "faucet",
                "casting_table"):
        write_json(f"{ASSETS}/items/{key}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{key}"}})
        write_json(f"{DATA}/{MOD}/loot_table/blocks/{key}.json", {"type": "minecraft:block", "pools": [{"rolls": 1,
                   "entries": [{"type": "minecraft:item", "name": f"{MOD}:{key}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(f"{ASSETS}/models/item/coke.json", {"parent": "minecraft:item/generated",
                                                    "textures": {"layer0": f"{MOD}:item/coke"}})
    for key in ("coke",):
        write_json(f"{ASSETS}/items/{key}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{key}"}})

    tag = f"{DATA}/minecraft/tags/block/mineable/pickaxe.json"
    values = json.load(open(tag, encoding="utf-8"))["values"] if os.path.exists(tag) else []
    for key in ("smeltery_bricks", "smeltery_glass", "smeltery_drain", "smeltery_tank", "smeltery_controller", "faucet",
                "casting_table"):
        if f"{MOD}:{key}" not in values:
            values.append(f"{MOD}:{key}")
    write_json(tag, {"replace": False, "values": values})
    write_json(f"{DATA}/{MOD}/{MOD}/structure/smeltery.json", {"material": f"{MOD}:brick", "priority": 20, "blocks": [
        f"{MOD}:{key}" for key in ("smeltery_bricks", "smeltery_drain", "smeltery_tank", "smeltery_controller", "casting_table")]})


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
    shaped("smeltery_tank", ["BBB", "BGB", "BBB"], {"B": brick, "G": "minecraft:glass"}, f"{MOD}:smeltery_tank")
    shaped("faucet", ["B B", " B "], {"B": brick}, f"{MOD}:faucet")
    shaped("casting_table", ["BBB", "B B", "B B"], {"B": brick}, f"{MOD}:casting_table")
    shaped("unfired_ingot_cast", [" F ", "F F", " F "], {"F": f"{MOD}:fireclay"}, f"{MOD}:unfired_ingot_cast")
    for key, ingredient, result, time in (("coke_from_smelting", "minecraft:coal", f"{MOD}:coke", 400),):
        write_json(f"{r}/{key}.json", {"type": "minecraft:smelting", "category": "misc", "cookingtime": time,
                                       "experience": 0.2, "ingredient": ingredient, "result": {"id": result}})


def languages():
    for key, de, en in (("smeltery_bricks", "Schmelzziegel", "Smeltery Bricks"),
                        ("smeltery_glass", "Schmelzglas", "Smeltery Glass"),
                        ("smeltery_drain", "Schmelzerei-Abfluss", "Smeltery Drain"),
                        ("smeltery_tank", "Schmelzerei-Tank", "Smeltery Tank"),
                        ("smeltery_controller", "Schmelzerei-Controller", "Smeltery Controller"),
                        ("faucet", "Wasserhahn", "Faucet"), ("casting_table", "Gießtisch", "Casting Table"),
                        ("molten_metal", "Flüssiges Metall", "Molten Metal")):
        name(f"block.{MOD}.{key}", de, en)
    for key, de, en in (("coke", "Koks", "Coke"),):
        name(f"item.{MOD}.{key}", de, en)
    for material, _c, de, en in MOLTEN:
        name(f"molten.{MOD}.{material}", de, en)
    name(f"container.{MOD}.smeltery", "Schmelzerei", "Smeltery")
    name(f"gui.{MOD}.smeltery.unformed", "Nicht fertig gebaut: Boden und Wände aus Schmelzziegeln um einen leeren Innenraum.",
         "Not built: a floor and walls of smeltery bricks around an empty tank.")
    name(f"gui.{MOD}.smeltery.capacity", "%s von %s mB", "%s of %s mB")
    name(f"gui.{MOD}.smeltery.fluid", "%s: %s mB (%s Barren)", "%s: %s mB (%s ingots)")
    name(f"gui.{MOD}.smeltery.heat_hint", "Schmilzt erst am vollen Schmelzpunkt.", "Melts only at the full melting point.")
    name(f"gui.{MOD}.smeltery.heat_coal", "Kohle: 1250 °C, mit Blasebalg 1650 °C", "Coal: 1250 °C, with a bellows 1650 °C")
    name(f"gui.{MOD}.smeltery.heat_coke", "Koks: 2000 °C, mit Blasebalg 4200 °C", "Coke: 2000 °C, with a bellows 4200 °C")
    name(f"gui.{MOD}.smeltery.heat_lava", "Lava aus einem Tank: 1600 °C, mit Blasebalg 2000 °C",
         "Lava from a tank: 1600 °C, with a bellows 2000 °C")
    for code, entries in lang.items():
        path = os.path.join(ASSETS, "lang", code + ".json")
        existing = json.load(open(path, encoding="utf-8"))
        existing.update(entries)
        with open(path, "w", encoding="utf-8", newline="\r\n") as file:
            json.dump(existing, file, indent=2, ensure_ascii=False)
            file.write("\n")


if __name__ == "__main__":
    textures()
    models()
    recipes()
    languages()
    print("smeltery assets written")
