"""Writes the steel hammer and its head: the iron ones recoloured in steel's shades (see alloy_assets),
their models and definitions, the hammer's recipe and the names in both languages.

Safe to run again. Run from the project root, then python tools/glow_textures.py steel_hammer_head:

    python tools/steel_hammer_assets.py
"""
import json
import os

import alloy_assets as alloys

for name in ("hammer_head", "hammer"):
    alloys.save(alloys.recolour(alloys.image(alloys.MOD, f"item/iron_{name}"), "steel"), alloys.MOD, f"item/steel_{name}")
alloys.item("steel_hammer_head")
alloys.item("steel_hammer", "minecraft:item/handheld")

alloys.write_json(os.path.join(alloys.DATA, alloys.MOD, "recipe/steel_hammer.json"), {
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    # Like the iron hammer from the stone one, the steel one is made from the iron one: its handle and
    # binding, with a steel head in place of the iron.
    "key": {"H": "hardwrought:steel_hammer_head", "I": "hardwrought:iron_hammer"},
    "pattern": ["H", "I"],
    "result": {"id": "hardwrought:steel_hammer"},
})

alloys.name("item.hardwrought.steel_hammer", "Stahlhammer", "Steel Hammer")
alloys.name("item.hardwrought.steel_hammer_head", "Stahlhammerkopf", "Steel Hammer Head")
for code, entries in alloys.lang.items():
    path = os.path.join(alloys.ASSETS, alloys.MOD, "lang", code + ".json")
    with open(path, encoding="utf-8") as file:
        existing = json.load(file)
    existing.update(entries)
    alloys.write_json(path, existing)
print("steel hammer assets written")
