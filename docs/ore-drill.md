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

Using the drill head opens its nine slots (hoppers draw from any side; nothing goes in), and says its
tier and state in the action bar and what the chunk holds for it, share by share, in the chat. With
the frame incomplete it says how many blocks are missing.

## Recipes

At the nailed workbench. The journal entry **Down Through the Rock** points to them once a player has
held a crusher or a nailed bench.

| Result | Recipe |
| --- | --- |
| Drill frame (4) | 4 ingots of its metal, 4 sticks |
| Ore drill | 6 iron ingots, a chest, a shaft, an iron pickaxe head |

## Tests

`GeologyGameTests`: across a granite region a bronze drill reaches only coal and iron and finds
something in most chunks, the chunks hold different mixes, the titanium drill reaches emerald but
less of it than iron, and a body under the drill dominates its table. `MetalGameTests`: an iron drill
reaches tin and not aluminium. `KineticsGameTests`: the drill is no drill with a frame block missing,
is bronze with one bronze block among iron ones, is driven by a crank box underneath and brings up the
chunk's ore. `MachineryClientGameTest` photographs it.
