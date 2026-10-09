"""Writes everything mithril and adamant equipment needs (see smithing/UpgradeEquipment).

Tool textures are the iron ones recoloured, as for the alloys (tools/alloy_assets.py). The armor, worn
and as an item, and the templates are vanilla's netherite ones in the metal's colours. Also written: item models and definitions, the
armor's equipment assets, the smithing recipes (netherite to mithril to adamant), the templates'
recipes, tags, weapon and armor profiles, item weights and the names.

It also puts every tool and armor piece of the mod's own tiers — the alloys and these two — into
vanilla's item tags (pickaxes, swords, head armor, ...). Those tags are what an enchantment asks to
know whether it suits a thing; without them a steel pickaxe took no rune.

Safe to run again:

    python tools/upgrade_assets.py
"""
import json
import os

import alloy_assets as base

MOD = base.MOD
ASSETS = os.path.join(base.ASSETS, MOD)
DATA = base.DATA

# Dark to light: mithril a sea-green silver, adamant a deep crimson.
PALETTES = {
    "mithril": [(18, 54, 58), (40, 110, 112), (84, 176, 168), (140, 224, 208), (214, 250, 240)],
    "adamant": [(40, 10, 22), (96, 24, 48), (160, 44, 78), (210, 84, 116), (246, 170, 186)],
}
NAMES = {"mithril": ("Mithril", "Mithril"), "adamant": ("Adamant", "Adamant")}
BARS = {"mithril": f"{MOD}:mithril_ingot", "adamant": f"{MOD}:adamantium_ingot"}
# What each tier is worked up from, piece by piece.
BELOW = {"mithril": lambda piece: f"minecraft:netherite_{piece}", "adamant": lambda piece: f"{MOD}:mithril_{piece}"}
TOOLS = {"pickaxe": ("spitzhacke", "Pickaxe"), "axe": ("axt", "Axe"), "shovel": ("schaufel", "Shovel"),
         "hoe": ("hacke", "Hoe"), "sword": ("schwert", "Sword")}
ARMOR = {"helmet": ("helm", "Helmet"), "chestplate": ("brustplatte", "Chestplate"),
         "leggings": ("beinschutz", "Leggings"), "boots": ("stiefel", "Boots")}
# Kilograms: netherite is heavy; mithril weighs little more than half of it, adamant somewhat more.
WEIGHT = {"mithril": 0.55, "adamant": 1.15}
IRON_WEIGHTS = base.IRON_WEIGHTS
# Vanilla's tags by the kind of piece.
TAGS = {"pickaxe": "pickaxes", "axe": "axes", "shovel": "shovels", "hoe": "hoes", "sword": "swords",
        "helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor", "boots": "foot_armor"}
ALLOY_TAGS = dict(TAGS, dagger="swords", greatsword="swords", halberd="axes")


def add_to(path, entries, key="values", fresh=None):
    data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else (fresh or {"replace": False, key: []})
    for entry in entries:
        if entry not in data[key]:
            data[key].append(entry)
    base.write_json(path, data)


def recolour_all(source, metal):
    """Every pixel by its brightness onto the metal's shades: for a thing that is all one material."""
    out = source.copy()
    px = out.load()
    lit = [sum(px[x, y][:3]) / 765 for y in range(out.height) for x in range(out.width) if px[x, y][3]]
    lo, hi = min(lit), max(lit)
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = base.shade(PALETTES[metal], ((r + g + b) / 765 - lo) / max(1e-6, hi - lo)) + (a,)
    return out


def main():
    lang = {"de_de": {}, "en_us": {}}
    base.PALETTES.update(PALETTES)
    tagged = {}
    weights = {}
    for metal, (de, en) in NAMES.items():
        for tool, (tde, ten) in TOOLS.items():
            base.save(base.recolour(base.image("minecraft", f"item/iron_{tool}"), metal), MOD, f"item/{metal}_{tool}")
            base.item(f"{metal}_{tool}", "minecraft:item/handheld")
            lang["de_de"][f"item.{MOD}.{metal}_{tool}"] = f"{de}{tde}"
            lang["en_us"][f"item.{MOD}.{metal}_{tool}"] = f"{en} {ten}"
            weights[f"{MOD}:{metal}_{tool}"] = round(IRON_WEIGHTS[tool] * WEIGHT[metal] * 1.2, 2)
        for piece, (ade, aen) in ARMOR.items():
            # Armor beyond netherite is netherite's own in cut — the heavier pauldrons, the ridged helmet — in
            # the metal's colours.
            base.save(recolour_all(base.image("minecraft", f"item/netherite_{piece}"), metal), MOD, f"item/{metal}_{piece}")
            base.item(f"{metal}_{piece}")
            lang["de_de"][f"item.{MOD}.{metal}_{piece}"] = f"{de}{ade}"
            lang["en_us"][f"item.{MOD}.{metal}_{piece}"] = f"{en} {aen}"
            weights[f"{MOD}:{metal}_{piece}"] = round(IRON_WEIGHTS[piece] * WEIGHT[metal] * 1.2, 2)
        for layer in ("humanoid", "humanoid_leggings"):
            base.save(recolour_all(base.image("minecraft", f"entity/equipment/{layer}/netherite"), metal), MOD,
                      f"entity/equipment/{layer}/{metal}")
        base.write_json(os.path.join(ASSETS, "equipment", metal + ".json"), {"layers": {
            "humanoid": [{"texture": f"{MOD}:{metal}"}], "humanoid_leggings": [{"texture": f"{MOD}:{metal}"}]}})

        template = f"{metal}_upgrade_smithing_template"
        base.save(recolour_all(base.image("minecraft", "item/netherite_upgrade_smithing_template"), metal), MOD, f"item/{template}")
        base.item(template)
        lang["de_de"][f"item.{MOD}.{template}"] = f"{de}-Schmiedevorlage"
        lang["en_us"][f"item.{MOD}.{template}"] = f"{en} Upgrade Template"
        # Four bars round a blank of fireclay: the template is the metal's own pattern, set in clay.
        base.write_json(os.path.join(DATA, MOD, "recipe", template + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" M ", "MBM", " M "],
            "key": {"M": BARS[metal], "B": f"{MOD}:cast_blank"}, "result": {"id": f"{MOD}:{template}", "count": 1}})
        base.write_json(os.path.join(DATA, MOD, "tags/item", f"{metal}_tool_materials.json"), {"values": [BARS[metal]]})
        for piece in list(TOOLS) + list(ARMOR):
            base.write_json(os.path.join(DATA, MOD, "recipe", f"{metal}_{piece}_smithing.json"), {
                "type": "minecraft:smithing_transform", "template": f"{MOD}:{template}", "base": BELOW[metal](piece),
                "addition": BARS[metal], "result": {"id": f"{MOD}:{metal}_{piece}"}})
            tagged.setdefault(TAGS[piece], []).append(f"{MOD}:{metal}_{piece}")

    for alloy in base.NAMES:
        for piece, tag in ALLOY_TAGS.items():
            tagged.setdefault(tag, []).append(f"{MOD}:{alloy}_{piece}")
    for tag, entries in tagged.items():
        add_to(os.path.join(DATA, "minecraft/tags/item", tag + ".json"), entries)

    profiles = os.path.join(DATA, MOD, MOD)
    for tool, profile in (("sword", "sword"), ("axe", "axe")):
        add_to(os.path.join(profiles, "weapon_profiles", profile + ".json"), [f"{MOD}:{metal}_{tool}" for metal in NAMES], "items")
    add_to(os.path.join(profiles, "armor_profiles", "plate.json"),
           [f"{MOD}:{metal}_{piece}" for metal in NAMES for piece in ARMOR], "items")
    base.write_json(os.path.join(profiles, "item_weights", "upgrade_equipment.json"), {"weights": weights})

    lang["de_de"][f"tooltip.{MOD}.upgrade_template"] = "Am Schmiedetisch: Werkstück in die Hand, Vorlage und Barren ins Inventar."
    lang["en_us"][f"tooltip.{MOD}.upgrade_template"] = "At a smithing table: the piece in hand, template and bar in the inventory."
    for code, entries in lang.items():
        path = os.path.join(ASSETS, "lang", code + ".json")
        raw = open(path, "rb").read()
        existing = json.loads(raw.decode("utf-8"))
        existing.update(entries)
        with open(path, "w", encoding="utf-8", newline="\r\n" if b"\r\n" in raw else "\n") as file:
            json.dump(existing, file, indent=2, ensure_ascii=False)
            file.write("\n")
    print("upgrade assets written")


if __name__ == "__main__":
    main()
