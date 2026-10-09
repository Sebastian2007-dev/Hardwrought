# Working a piece up: netherite, mithril, adamant

Above tungsten steel nothing is forged. A finished piece is worked up a tier at a smithing table:

| From | To | Template | Bar |
|---|---|---|---|
| tungsten steel | netherite | netherite upgrade (vanilla, found) | netherite ingot |
| netherite | mithril | mithril upgrade template | mithril ingot |
| mithril | adamant | adamant upgrade template | adamantium ingot |

Pickaxe, axe, shovel, hoe, sword and the four armor pieces go this way. A template is used up.

- **Mithril**: 3400 uses, faster and keener than netherite, and it takes enchantment more readily than
  anything but gold (five runes). Armor of it is light.
- **Adamant**: 4800 uses, the fastest tool and the heaviest blow. Neither burns.

## The templates

Mithril's and adamant's templates are made, not found: four bars of the metal round a **cast blank** of
fireclay (` M ` / `MBM` / ` M `).

## At the table

Hold the piece in the main hand, with the template and the bar in the inventory, and use the smithing
table. Without an upgrade to do, the table opens vanilla's own screen as before (armor trims).

The template's pattern lies over the piece as a line. Press the mouse button on the glowing point and
trace the line to its end:

- The point waits until the hand takes hold of it: the button pressed with the pointer on it. Coming
  near it does nothing. With hold of it (the point turns green) the trace runs along with the pointer.
- Behind the pointer the line turns green where the trace was true (within 2.5 pixels of the line),
  yellow where it wandered, red where it left the line.
- Letting go of the button, or straying more than 14 pixels off, lets go of the line. The point then
  waits where the trace stopped, to be taken hold of again.
- There is a time for the whole trace, shown under the piece. What is not traced when it runs out
  counts as missed. Closing the screen half way finishes the upgrade with the rest missed.

| Template | Pattern | Missed beyond | Time |
|---|---|---|---|
| netherite | a wave | 9 pixels | 14 s |
| mithril | a figure of eight | 7.5 pixels | 15 s |
| adamant | a spiral | 6 pixels | 18 s |

## What the trace decides

The upgrade always succeeds, and the piece keeps its enchantments, its hardening and its polish. The
trace decides its craftsmanship: at 75 % it stays what it was; every point above that adds 0.6 points
of craftsmanship, up to +15; every point below takes 0.6, down to −30. A piece nobody forged (found,
bought) counts as 65 % to begin with.

## Where it lives

- `smithing/UpgradeEquipment`: the two tiers, their tools, armor and templates.
- `smithing/Upgrading`: the table, which upgrade a piece is due, and what the trace is worth.
- `client/smithing/UpgradeScreen`: the trace.
- `tools/upgrade_assets.py`: textures, models, recipes, tags, profiles, weights, names. It also puts the
  alloys' and these tiers' tools and armor into vanilla's item tags, which is what lets them take runes.

## Verification

- `UpgradeGameTests`: a tungsten steel pickaxe is worked up to netherite, mithril and adamant with the
  right template each time and keeps its runes; the trace's worth; the mod's own pickaxes take runes;
  nine bars make a block and a block melts as nine.
