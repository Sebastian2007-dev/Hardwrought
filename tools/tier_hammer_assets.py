"""Writes the smith's hammers above steel: stainless steel, titanium and tungsten steel, whose heads
are forged, and netherite, mithril and adamant, which are worked up at the smithing table.

Textures are the iron hammer and its head recoloured in each metal's shades (see alloy_assets and
upgrade_assets), with models, definitions and recipes. The names are in the language files.

Safe to run again. Run from the project root, then the glow script for the three new heads:

    python tools/tier_hammer_assets.py
    python tools/glow_textures.py stainless_steel_hammer_head titanium_hammer_head tungsten_steel_hammer_head
"""
import os

import alloy_assets as alloys
import upgrade_assets as upgrades

MOD = alloys.MOD

# Forged: the head on the anvil, then set on the hammer below in place of its own.
FORGED = [("stainless_steel", "steel"), ("titanium", "stainless_steel"), ("tungsten_steel", "titanium")]
# Worked up: (tier, the hammer below, template, what the template is fed).
UPGRADED = [
    ("netherite", "tungsten_steel", "minecraft:netherite_upgrade_smithing_template", "#minecraft:netherite_tool_materials"),
    ("mithril", "netherite", f"{MOD}:mithril_upgrade_smithing_template", f"{MOD}:mithril_ingot"),
    ("adamant", "mithril", f"{MOD}:adamant_upgrade_smithing_template", f"{MOD}:adamantium_ingot"),
]

# Netherite has no shades of its own in the other scripts: a dark, faintly warm grey.
alloys.PALETTES["netherite"] = [(20, 16, 18), (48, 42, 46), (76, 68, 72), (108, 98, 104), (156, 146, 152)]
for tier, shades in upgrades.PALETTES.items():
    alloys.PALETTES[tier] = shades


def hammer(metal):
    alloys.save(alloys.recolour(alloys.image(MOD, "item/iron_hammer"), metal), MOD, f"item/{metal}_hammer")
    alloys.item(f"{metal}_hammer", "minecraft:item/handheld")


for metal, below in FORGED:
    alloys.save(alloys.recolour(alloys.image(MOD, "item/iron_hammer_head"), metal), MOD, f"item/{metal}_hammer_head")
    alloys.item(f"{metal}_hammer_head")
    hammer(metal)
    alloys.write_json(os.path.join(alloys.DATA, MOD, f"recipe/{metal}_hammer.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        # As the steel hammer is made from the iron one: the handle and binding of the hammer below,
        # with the new head in place of the old.
        "key": {"H": f"{MOD}:{metal}_hammer_head", "I": f"{MOD}:{below}_hammer"},
        "pattern": ["H", "I"],
        "result": {"id": f"{MOD}:{metal}_hammer"},
    })

for tier, below, template, bar in UPGRADED:
    hammer(tier)
    alloys.write_json(os.path.join(alloys.DATA, MOD, f"recipe/{tier}_hammer_smithing.json"), {
        "type": "minecraft:smithing_transform",
        "template": template,
        "base": f"{MOD}:{below}_hammer",
        "addition": bar,
        "result": {"id": f"{MOD}:{tier}_hammer"},
    })

print("tier hammer assets written")
