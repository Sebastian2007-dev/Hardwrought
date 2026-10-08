"""Writes the casts: a clay plate for every shape the smeltery pours, and the metal that fills it.

A cast is a flat plate with a hole in the exact outline of what it makes — the bar's or the part's own
texture is the stencil. The metal rising in it (`casting_fill`) is the same outline in white, tinted
with the metal's colour, so what is poured already looks like what comes out. Safe to run again:

    python tools/cast_assets.py
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/hardwrought")
DATA = os.path.join(ROOT, "src/main/resources/data")
MOD = "hardwrought"

# Kept in step with Casts: the bar, then every ToolParts.Part. Shape, German, English.
SHAPES = [("ingot", "Barrenform", "Ingot Cast"),
          ("pickaxe_head", "Spitzhackenkopf-Form", "Pickaxe Head Cast"),
          ("axe_head", "Axtkopf-Form", "Axe Head Cast"),
          ("shovel_head", "Schaufelblatt-Form", "Shovel Head Cast"),
          ("hoe_head", "Hackenblatt-Form", "Hoe Head Cast"),
          ("sword_blade", "Schwertklingen-Form", "Sword Blade Cast"),
          ("dagger_blade", "Dolchklingen-Form", "Dagger Blade Cast"),
          ("greatsword_blade", "Bidenhänderklingen-Form", "Greatsword Blade Cast"),
          ("halberd_head", "Hellebardenkopf-Form", "Halberd Head Cast"),
          ("hammer_head", "Hammerkopf-Form", "Hammer Head Cast"),
          ("helmet_shell", "Helmschalen-Form", "Helmet Shell Cast"),
          ("cuirass", "Kürass-Form", "Cuirass Cast"),
          ("greaves", "Beinschienen-Form", "Greaves Cast"),
          ("sabatons", "Schuhplatten-Form", "Sabatons Cast")]

# Clay before and after the furnace.
UNFIRED = (206, 190, 162)
FIRED = (160, 91, 55)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as file:
        json.dump(data, file, indent=2, ensure_ascii=False)
        file.write("\n")


def save(img, name):
    target = os.path.join(ASSETS, "textures/item", name + ".png")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    img.save(target)


def stencil(shape):
    """The outline a cast is cut to: where the thing it makes has a pixel. The plate keeps its rim."""
    # Every bar in the mod is drawn to vanilla's outline; steel's stands for them all.
    source = Image.open(os.path.join(ASSETS, "textures/item", "steel_ingot.png" if shape == "ingot" else f"iron_{shape}.png"))
    alpha = source.convert("RGBA").getchannel("A").load()
    return [[1 <= x <= 14 and 1 <= y <= 14 and alpha[x, y] > 0 for x in range(16)] for y in range(16)]


def shade(colour, by):
    return tuple(max(0, min(255, c + by)) for c in colour) + (255,)


def plate(body, hole):
    """A clay plate, lit from the upper left, with the outline cut out of it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def open_at(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and hole is not None and hole[y][x]

    for y in range(16):
        for x in range(16):
            if open_at(x, y) or (x in (0, 15) and y in (0, 15)):
                continue
            by = ((x * 7 + y * 11 + (x ^ y) * 3) % 9) - 4
            if y == 0 or x == 0:
                by += 26
            elif y == 15 or x == 15:
                by -= 34
            # The hole is sunk in: its upper and left lips lie in shadow, the lower and right ones catch the light.
            if open_at(x, y + 1) or open_at(x + 1, y):
                by -= 40
            elif open_at(x, y - 1) or open_at(x - 1, y):
                by += 22
            px[x, y] = shade(body, by)
    return img


def fill(hole):
    """The metal in the cast: the outline in near-white, to be tinted, a little brighter in the middle."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if not hole[y][x]:
                continue
            edge = any(not (0 <= x + dx < 16 and 0 <= y + dy < 16 and hole[y + dy][x + dx])
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            value = (212 if edge else 244) + ((x * 5 + y * 3) % 4) * 3
            px[x, y] = (value, value, value, 255)
    return img


def generated(name):
    write_json(f"{ASSETS}/models/item/{name}.json", {"parent": "minecraft:item/generated",
                                                    "textures": {"layer0": f"{MOD}:item/{name}"}})


def item(name):
    generated(name)
    write_json(f"{ASSETS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}})


def main():
    lang = {"de_de": {}, "en_us": {}}

    def name(key, german, english):
        lang["de_de"][key] = german
        lang["en_us"][key] = english

    recipes = f"{DATA}/{MOD}/recipe"
    save(plate(UNFIRED, None), "cast_blank")
    item("cast_blank")
    name(f"item.{MOD}.cast_blank", "Formrohling", "Cast Blank")
    write_json(f"{recipes}/cast_blank.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                              "pattern": ["FF", "FF"], "key": {"F": f"{MOD}:fireclay"},
                                              "result": {"id": f"{MOD}:cast_blank", "count": 1}})

    cases = []
    for shape, german, english in SHAPES:
        hole = stencil(shape)
        save(plate(UNFIRED, hole), f"unfired_{shape}_cast")
        save(plate(FIRED, hole), f"{shape}_cast")
        save(fill(hole), f"casting_fill/{shape}")
        item(f"unfired_{shape}_cast")
        item(f"{shape}_cast")
        generated(f"casting_fill/{shape}")
        cases.append({"when": shape, "model": {"type": "minecraft:model", "model": f"{MOD}:item/casting_fill/{shape}",
                                               "tints": [{"type": "minecraft:custom_model_data", "index": 0,
                                                          "default": 0xD9521E}]}})
        name(f"item.{MOD}.unfired_{shape}_cast", "Ungebrannte " + german, "Unfired " + english)
        name(f"item.{MOD}.{shape}_cast", german, english)
        write_json(f"{recipes}/{shape}_cast_from_smelting.json", {
            "type": "minecraft:smelting", "category": "misc", "cookingtime": 200, "experience": 0.2,
            "ingredient": f"{MOD}:unfired_{shape}_cast", "result": {"id": f"{MOD}:{shape}_cast"}})
    # One item for the metal in every cast: its shape and its colour ride on the stack.
    write_json(f"{ASSETS}/items/casting_fill.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": cases, "fallback": cases[0]["model"]}})
    name(f"item.{MOD}.casting_fill", "Flüssiges Metall", "Molten Metal")

    name(f"message.{MOD}.cast.imprinted", "In den Ton gedrückt: %s", "Pressed into the clay: %s")
    name(f"message.{MOD}.cast.no_part", "%s lässt sich nicht in diese Form gießen.", "%s cannot be cast in this shape.")
    name(f"message.{MOD}.cast.nothing", "Im Tank ist nichts, was sich gießen lässt.", "Nothing in the tank can be cast.")
    name(f"message.{MOD}.cast.no_cast", "Auf dem Gießtisch fehlt eine gebrannte, leere Form.",
         "The casting table wants a fired, empty cast.")
    name(f"tooltip.{MOD}.cast_blank", "Auf den Gießtisch legen und einen Barren oder ein Werkzeugteil hineindrücken.",
         "Lay it on a casting table and press a bar or a tool part into it.")
    name(f"tooltip.{MOD}.cast_unfired", "Im Ofen brennen.", "Fire it in a furnace.")
    name(f"tooltip.{MOD}.cast_amount", "Fasst %s Barren.", "Holds %s bars.")
    name(f"tooltip.{MOD}.cast_rough", "Gegossene Teile sind grob: am Schleifstein nacharbeiten.",
         "Cast parts come out rough: work them over at a grindstone.")
    name(f"tooltip.{MOD}.polish", "Geschliffen: +%s %% (%s von %s Durchgängen)", "Ground: +%s%% (%s of %s passes)")
    name(f"tooltip.{MOD}.forged_armor_stats", "Schutz %s %% · Haltbarkeit %s %%", "Protection %s%% · Durability %s%%")
    name(f"tooltip.{MOD}.treatment.air_hot", "Noch heiß: weder gehärtet noch abgekühlt",
         "Still hot: neither hardened nor cooled")
    name(f"message.{MOD}.ground_out", "An diesem Teil ist nichts mehr abzuschleifen.",
         "There is nothing left to grind off this part.")
    name(f"gui.{MOD}.grinding", "Schleifstein", "Grindstone")
    name(f"gui.{MOD}.grinding.quality", "Handwerkskunst %s %% (davon geschliffen +%s)", "Craftsmanship %s%% (ground +%s)")
    name(f"gui.{MOD}.grinding.hint", "Maustaste oder Leertaste halten drückt an, loslassen lässt nach. Halte die Marke im Grünen.",
         "Hold the mouse button or space to press on, let go to ease off. Keep the marker in the green.")
    name(f"gui.{MOD}.grinding.steady", "Ruhige Hand: %s %%", "Steady hand: %s%%")
    name(f"gui.{MOD}.grinding.passes", "Noch %s Durchgänge", "%s passes left")
    name(f"gui.{MOD}.grinding.done", "Fertig geschliffen.", "Ground as far as it goes.")
    name(f"gui.{MOD}.grinding.perfect", "Perfekt! +5", "Dead on! +5")
    name(f"gui.{MOD}.grinding.good", "Sauber. +3", "Clean. +3")
    name(f"gui.{MOD}.grinding.poor", "Knapp daneben. +1", "Just off. +1")
    name(f"gui.{MOD}.grinding.miss", "Kratzer! −2", "A scratch! −2")
    name(f"gui.{MOD}.smeltery.pours_next", "Gießt: %s", "Pours: %s")
    for code, entries in lang.items():
        path = os.path.join(ASSETS, "lang", code + ".json")
        raw = open(path, "rb").read()
        existing = json.loads(raw.decode("utf-8"))
        existing.update(entries)
        with open(path, "w", encoding="utf-8", newline="\r\n" if b"\r\n" in raw else "\n") as file:
            json.dump(existing, file, indent=2, ensure_ascii=False)
            file.write("\n")
    print("cast assets written")


if __name__ == "__main__":
    main()
