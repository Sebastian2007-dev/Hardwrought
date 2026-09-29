# Milestone 17 – Building Physics

Section 39 asks for simplified structural physics: no trivial skybases, realistic supports rewarded,
creative freedom kept, and no massive base standing on a single dirt block. Section 40, leaves that do
not carry players, was already done in Milestone 3.

## What takes part

Only **built** blocks. A block placed from an item — by a player or a dispenser — is recorded as built
in a table on its chunk. Everything else is natural terrain, and terrain is the ground buildings stand
on: it never collapses, however it has been mined. Blocks built before this milestone are not in the
table and count as terrain, so an existing base does not fall down on the day of the update.

A built block that falls — collapsed, or sand a player put there — is still built where it lands.
Otherwise every collapse would turn rubble into anchored ground to build on.

Leaves, and any block without a full collision shape that is not built, are not ground.

### Loose pieces of natural ground

Terrain is ground only while it is part of the land. When a player breaks a block, the natural blocks
next to it are followed through the natural blocks they touch. A piece that runs on past 1024 blocks is
part of the land and stays, however it has been dug at. A smaller piece is on its own — a tree cut at
the foot, a boulder undermined — and is judged like a building, its blocks as members of their
material: it holds if something built props it up, and otherwise cracks and falls like anything else.
Leaves never hold a piece up; they decay by themselves once the logs have gone.

Only breaks by a player are followed up. Commands, worldgen and flowing water change blocks by the
thousand, and checking after each of those would flood the check for nothing.

## Support

Support flows out of the ground:

- A block resting on terrain has full support.
- Stacking passes it up unchanged.
- Every block spanned sideways costs `1 / (support distance + 1)` of it, so a material reaches out
  exactly as many blocks as its support distance.
- A block set into a terrain wall starts one span down; one below a terrain ceiling likewise, if its
  material has tensile strength.
- A block can hang below another only if its material has tensile strength.

A block no path of support reaches is **unsupported**.

## Load

Each block passes its own weight and everything resting on it down the path its support came from.
Weight is density × the volume of the collision shape × 9.81 m/s²; a slab weighs half a block.
A block resting on something may carry its compressive strength over one square metre; a hanging
block its tensile strength. A block spanned from the side has no single strength that governs — the
support distance already stands in for bending, which is not modelled.

With the bundled numbers, only the weak materials ever reach their limit: a player-built dirt column
carries itself for twenty blocks, and twelve blocks of stone on one dirt block crush it.

## Stress, cracks, collapse

A failing block takes stress every tick, shown as the block-breaking crack overlay, with a creak at
each new stage:

| Failure | Time to collapse |
|---|---|
| Unsupported | 2 s |
| Overloaded | 20 s just past its strength, down to 2 s at ten times it |

At full stress a block with free space under it falls as a falling block and hurts what it lands on,
as an anvil does. A block with something under it — an overloaded block always has — is crushed where
it stands and drops as an item; as a falling block it would only land in its own place again. A block
with a block entity (a chest) breaks and spills too. The fall changes the structure, so what
depended on it is judged again: a collapse spreads.

A stressed block that is relieved keeps its cracks but takes no more stress. Stress is saved with the
chunk; whether a block is still failing is worked out again when the chunk loads.

## Structural anchors

A **structural anchor** (two from an iron ingot and four nuggets, on the joined bench) is used on a
built block. The block then holds on to any neighbour that is held itself — terrain or a supported
block — without paying for the span. That is how a lintel, a balcony or an overhang stays up where its
material alone would not reach.

An anchor holds 50 kN, about five tonnes. A chain of anchored blocks could otherwise reach out for
ever, but every block of it hangs on the first anchor, which gives way long before: one anchored stone
is fine, four in a row are not. The anchor stays with the block until the block is gone; a block that
collapses and lands loses it. Terrain needs no anchor, and a block that carries nothing has nothing to
tie. The debug HUD shows `(anchored)` and `held=anchored`.

## Shared cells

Stairs, slabs, doors and trapdoors no longer keep panes, iron bars and carpets out of their cell. The
two share a **shared block** that keeps both, draws both with their own models, and has both shapes.

Where the thin block goes follows the click:

- on a face inside the half block's cell — the top of a slab, the step of a stair — into that cell;
- on a block face that borders the half block's cell, into that cell, which vanilla would have refused.

A pane clicked onto the top of a slab or a trapdoor still goes above it, as it always has, since a sill
with a window on it is the common case. A carpet lies on the half block's floor where that covers the
whole cell — on a bottom slab, a stair's lower step, a closed trapdoor — and on the floor otherwise,
under a top slab or through a doorway. It is not put into the upper half of a door.

Panes beside a shared cell join the pane inside it. A door or trapdoor in a shared cell still opens by
hand and by redstone, and a door's two halves stay one door. Mining the cell takes the thin part out at
its own pace and leaves the half block; an explosion drops both. A built half block stays built, and
anchored, when something is set into it or taken out again.

## Materials

`hardwrought/materials` gains the structural materials, with the optional `structure` fields that
Phase 1 already defined. `hardwrought/structure` says which blocks are made of which: a file names a
material and lists blocks and block tags. A block named directly wins over a tag; among tags, the
higher `priority` wins, so `#minecraft:wooden_stairs` (wood, priority 10) beats `#minecraft:stairs`
(stone, 0).

| Material | Density kg/m³ | Compression MPa | Tension MPa | Support distance |
|---|---|---|---|---|
| wood | 600 | 30 | 40 | 5 |
| stone | 2600 | 100 | 5 | 2 |
| brick | 1900 | 20 | 2 | 2 |
| concrete | 2400 | 30 | 3 | 3 |
| soil | 1500 | 0.3 | 0 | 1 |
| sand | 1600 | 0.3 | 0 | 0 |
| glass | 2500 | 50 | 5 | 1 |
| wool | 150 | 0.05 | 1 | 1 |
| ice | 917 | 3 | 1 | 1 |
| obsidian | 2400 | 200 | 10 | 3 |
| copper | — | 200 | 200 | 5 |
| gold | — | 100 | 100 | 3 |
| iron | — | 250 | 250 | 8 |
| netherite | — | 1000 | 1000 | 12 |
| unclassified | 1000 | 10 | 1 | 1 |

Metals keep their existing density. `unclassified` is what a full block no profile names is built of —
a modded block, a workbench. Blocks without a full shape that no profile names (torches, rails,
carpets, doors) carry nothing and are not recorded.

## Performance

Nothing is scanned. A structure is only judged when a block in or beside it changes, and then only as
far as it is connected, up to 4096 blocks; larger ones are left alone. At most 16 structures are judged
and 64 blocks collapse per tick, the rest the next tick. Chunks that hold no built block are skipped
at once. Beyond the loaded world everything counts as ground, so no building falls at a border.

## Diagnostics

The `STRUCTURE` channel of the debug HUD is no longer `unavailable`. It reports for the inspected
block whether it is terrain, carries nothing, or is built, and for a built block its material,
support, how it is held, load against capacity, stress, and when it falls.

## Not in this milestone

- Pistons: a pushed built block arrives as terrain.
- Explosions that weaken rather than remove.
- Bending and torsion; cantilevers are limited by support distance only.
- Cave-ins inside the land: only pieces that have come away from it fall, not a cave roof that is
  still part of it.
- Loose pieces after explosions, fire or commands; only a player's break is followed up.
- Mortar, beams and bracing as dedicated building parts.

## Verification

`BuildingGameTests` checks the arithmetic on planned structures — spans per material, a bridge held
from both ends, hanging, the dirt column and the castle on one dirt block — then the datapack mapping,
and in the world: an overreaching stone beam cracks, falls after two seconds and is still built where
it lands; a wooden pillar falls when the rock under it is mined away; an overloaded block is crushed;
an anchored block stays where it would fall. `SharedCellGameTests` places a pane into a stair and a
carpet onto a slab through the player's own interaction, checks the joining pane, the carpet's height,
breaking, and doors and trapdoors that still open. The client test takes the screenshot
`hardwrought-shared-cells`.
