# The ore drill

The machine Milestone 6 laid the rules for: a drill head in a metal frame that brings ore up out of
the whole ground under a chunk, for ever, at a rate.

## Building it

```text
layer 2   F F F        F  a drill frame, of any of the five metals
          F F F        D  the drill head (ore drill)
          F F F
layer 1   F F F        driven from below: a shaft coming up to the head, or a crank box under it
          F D F
          F F F
```

Seventeen frame blocks, all present. The **weakest** frame block is the drill's tier.

| Tier | Frame | Reaches (adds) | Out of ordinary rock, 16 RPM | Load per RPM |
| --- | --- | --- | --- | --- |
| 1 | Bronze | coal, iron | one piece per 30 s | 10 — a hand crank's 160 at 16 RPM |
| 2 | Iron | copper, tin, zinc, lead | per 25 s | 14 |
| 3 | Nickel | gold, redstone, lapis, manganese, magnesium, aluminium, nickel | per 20 s | 18 |
| 4 | Chromium | diamond, emerald, cobalt, chromium, mercury | per 16 s | 22 |
| 5 | Titanium | titanium, tungsten, uranium, thorium, platinum | per 12 s | 26 |

Turned faster it works faster; over a body of ore it works in 60 % of the time. Which tier an ore needs
is `drill_tier` in the rock profiles, 1 to 5, and the metal table agrees with them.

## Every chunk its own mix

`DrillYield.forChunk` reads the chunk's own ore mix from the world seed — nothing is stored:

- The **rock of the region** sets the tone: the ores it carries natively count four times their
  profile weight; every other ore any rock carries is there as a **trace**.
- The **chunk** decides how much of each it really has: a quarter of the ores are **missing** from any
  given chunk, the rest are there at a quarter to three times their usual amount.
- A **body** under the chunk makes its ore certain and weighs it up fivefold.

So one granite chunk may be two thirds iron with a little emerald, the next without emerald at all and
with more coal, and a chunk over an iron body almost nothing but iron. A first drill finds something in
most chunks; now and then one holds nothing it can reach, and moving it a chunk over is the answer.

## Using it

The drill keeps nothing. The head is set down facing its placer, and the frame block in front of it
carries a chute. Every piece of ore goes out through it: into whatever offers item storage just
outside the chute (a chest, a hopper, any mod's pipe, through Fabric's transfer API), or onto the
ground where nothing does. A full chest holds the piece back and stops the drill until there is room.

It is driven by rotation into any side of the head. An electric motor (milestone 13) will be one
more rotation source, so the drill needs nothing new to run on power.

Using any block of the drill opens its screen: tier, state, rate, drive speed, where the ore goes,
and every ore the chunk holds. Ores this frame reaches show their share of what comes up; the others
show the tier they need. Ores the player has never met stay a black shape with "???". The screen asks
for fresh numbers once a second while it is open.

## Recipes

At the nailed workbench. The journal entry **Down Through the Rock** points to them once a player has
held a crusher or a nailed bench.

| Result | Recipe |
| --- | --- |
| Drill frame (2) | 5 rods of its metal, crossed: `R R` / ` R ` / `R R` |
| Ore drill | 6 iron ingots, a chest, a shaft, an iron pickaxe head |

## Tests

`GeologyGameTests`: across a granite region a bronze drill reaches only coal and iron and finds
something in most chunks, the chunks hold different mixes, the titanium drill reaches emerald but
less of it than iron, and a body under the drill dominates its table. `MetalGameTests`: an iron drill
reaches tin and not aluminium. `KineticsGameTests`: the drill is no drill with a frame block missing,
is bronze with one bronze block among iron ones, is driven by a crank box underneath and brings up the
chunk's ore. `MachineryClientGameTest` photographs it.

## Finished look and use

Once the frame is complete the drill is drawn as one machine: a casing round the head, a drive housing
over it, a derrick with a crown on top. Each frame block carries a `part` (1–17, in the order of
`OreDrillBlockEntity.framePositions`) and draws its piece of the whole in plate of its own metal, so a
weaker block still shows. The head gets `formed=true`. Using any block of the finished drill opens it.
Setting down the last frame block, or taking one out, reshapes the drill at once. Models come from
`tools/ore_drill_formed.py`, which `tools/drill_assets.py` also runs.

## Structure view in the compendium

`knowledge/Multiblocks` lays out the ore drill and the hooded forge layer by layer. On the page of any
of their blocks the compendium shows **View structure** once a block of every kind in it is studied.
The view turns by dragging, zooms with the wheel and steps through the layers with ▲/▼ (the arrow keys
work too). The whole structure is drawn finished; a single layer is drawn as the loose blocks that go in.
Where several blocks will do, it cycles through the ones the player has studied.
