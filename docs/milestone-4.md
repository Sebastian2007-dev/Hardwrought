# Milestone 4: Water

Milestone 4 implements specification section 23 and the aquifers of section 68: water has a finite
supply, a quality, a table it sits above underground, and a sun that takes it away again.

Like the previous milestones it is server-authoritative and bounded by construction — one flood fill
per lookup, a fixed sample box per player per slow pass, and never a chunk load from a tick job.

## Water is a quantity

Section 23.1, in full: **there are no source blocks at all.** Water is an amount that moves, measured
in millibuckets.

| | mB |
| --- | --- |
| A full block | 1000 |
| A bucket | 1000 |
| A glass bottle | 100 |
| A waterskin | 800 — eight drinks of a hundred |

Vanilla's spread is what made water infinite, and it is gone. It never moved water: it re-derived
every flowing block from whichever source it could still see, so one block wetted an unlimited area
for ever. In its place is a simulation that only ever *moves* what is already there, applied to every
cell that has been disturbed:

```text
1. fall    — give everything the block below can still take
2. rise    — pass anything above a full block on upward
3. level   — share what is left with the neighbours that have less
```

Nothing is produced or destroyed by either rule. A lake that drains into a cave ends up in the cave.
Water enters the world only where something puts it there: a bucket being emptied, or a spring
seeping up out of the water table.

This is unconditional rather than left to the vanilla `waterSourceConversion` game rule. That rule
keeps its meaning for lava, which is untouched and still spreads the way it always did.

### Pressure

Water has weight, and the weight of what stands above a cell is what pushes water up somewhere else.
A cell carries a little more than a full block for every block resting on it, and passes anything
beyond that upward:

```text
stable amount of the lower of two stacked cells:
  below a block          all of it sits in the lower cell
  above a block          the lower cell takes a little more than its share
```

That little more is the whole mechanism. Without it every cell of a column holds exactly one block,
nothing is ever in surplus, and **communicating vessels do not communicate** — a tall column beside an
empty shaft just stands there. With it the column presses down, the pressure travels along the floor
and up the other side, and the two come to rest at the same height. A test builds exactly that: two
shafts joined only at the floor, all the water in one of them, and both have to end up level.

The same mechanism makes a tank fill from underneath and come out at the top, and it is what happens
when a bucket is emptied into water that is already there. A cell that cannot take a whole bucket even
under pressure refuses it and says so, rather than swallowing water that then no longer exists.

Emptying a bucket is taken over from vanilla for the same reason the bottle was: vanilla replaces the
block with a full one, which would invent six hundred millibuckets when poured into a cell holding
four hundred, and lose the whole bucket when poured into a full one.

A cell under pressure is drawn as an ordinary full block. The extra millibuckets a deep cell carries
cannot be shown, so the bottom of a tall column holds a little more than it looks like it does.

### Where the amount lives

Only blocks that are **not** completely full need storing. A full block is a thousand millibuckets by
definition, and so is every ocean and lake a world was generated with — which means an existing world
needs no conversion and no stored data at all. The table holds the moving edge of the water and
nothing else, and it hangs off the chunk, so it saves and loads with the chunk it describes.

Vanilla can only draw eight steps of water in a block, so the block state is the display and the
stored amount is the truth. A bottle's worth of water is drawn at the lowest step vanilla has, and a
cell under pressure is drawn as an ordinary full block.

The table is a chunk attachment, and the attachment type is registered in the mod initialiser rather
than wherever the class first happens to be touched. An attachment registered any later is unknown
while chunks are being read, and everything stored in them is discarded on load — which shows up as
water quietly rounding up to full and pressure not existing at all, since a pressed cell is the one
value that cannot be recovered from the block state.

### What water flows into

Empty space, water that is not yet full, and **anything a fluid washes away** — grass tufts, flowers,
torches, snow layers. Vanilla keeps that list in the `washed_away_by_fluids` tag and the same tag is
used here, so those blocks are dropped and the water runs on through.

That last part is not a detail. Treating growth as a wall left a bucket of water sitting motionless
on open hillside, because every direction it could have gone was occupied by a plant. A test covers
exactly that scene: a full block on an overgrown ledge has to wash the growth out of its way, run
off, and still add up to the thousand millibuckets that went in.

A waterlogged block is the other case: the water in it is real but not free to move, so the
simulation leaves it where it is.

### Bounded work

Still water costs nothing: only disturbed cells are looked at. Vanilla already schedules a fluid tick
on water whenever anything beside it changes, and that scheduled tick — the one thing kept from the
old behaviour — is exactly the signal that a cell has been disturbed.

Past that, 1024 cells are moved per pass and at most 32,768 disturbed cells are remembered at once.
A very large disturbance is therefore slow rather than expensive: draining an ocean takes an ocean's
worth of time, which is what it should take.

## Water bodies

`WaterBody` is a bounded flood fill over connected water, the counterpart of the environmental cell
of Milestone 3. Section 23.4 makes evaporation depend on the size of a body, so the size has to be a
measured value rather than a guess from the block underfoot.

```text
scan from a water block
  ├─ no water there        → NONE
  ├─ closes within 512     → MEASURED, with volume, sources, sky exposure and depth
  └─ runs past the budget  → LARGE: a lake, a river, an ocean
```

A body of at most 24 blocks is a **puddle**: small enough that losing a block to the sun is a real
change to it. An ocean is never counted block by block, and never touched.

## Water quality

Section 23.2. A datapack profile for the biome decides first; `hardwrought/water_quality/*.json`
lists biomes and what their standing and running water contains. Without a profile:

| Where | Quality |
| --- | --- |
| Ocean biomes | salt |
| River biomes, and any flowing water | river |
| Below the local water table | fresh |
| Any other still pool | swamp |
| A large still body | river |

Nothing taken off the surface is fresh. Fresh water comes out of the ground or out of a pot, which
is exactly what gives a well and a fire their point.

| Quality | One drink is worth | Illness risk |
| --- | --- | --- |
| Fresh | 100 % | — |
| River | 90 % | 12 % |
| Swamp | 65 % | 45 % |
| Salt | **−60 %** | 5 % |

Seawater is worse than nothing: drinking it costs the body more water than it supplies. Illness is a
vanilla poison effect for now — the disease system of section 12 replaces it when it exists.

Vanilla has tags for ocean and river but none for swamp, so the biomes whose standing water is
organic are named in a shipped profile rather than guessed at from temperature or anything else.

### Containers remember

A waterskin and a glass bottle both record what was poured into them, in a `hardwrought:water_quality`
item component. Without that, water would silently become clean the moment it left the block it came
from, and the whole section would have nothing to say. Filling a bottle is taken over from vanilla
for that one reason: the quality has to be read at the moment and the place the bottle is dipped.

Filling anything takes that volume out of the world. A bucket needs a whole block and leaves nothing
behind; a bottle takes a tenth, so a puddle is enough for one; a skin takes up to eight hundred and
fills part way when there is less. Nothing can be filled from water that is not there.

### Boiling

Section 23.2 lists boiling as the first purification step. Using a waterskin on any lit fire — a
campfire, a furnace, open flame — boils what is in it and makes river and swamp water drinkable.
Boiling seawater only concentrates the salt, which is why the specification lists distillation
separately; that belongs to the later purification chain together with sand, charcoal and ceramic
filters.

## Groundwater and aquifers

Section 23.6 gives a region a water table; section 68 makes opening one flood a mine. Both are the
same mechanic seen from two sides.

The table is derived from the world seed per 64-block region, so it is the same every time the same
place is asked and no table has to be stored for the world. It is read **at the centre of the
region**, not at the block, or a well would work on one side of a garden and be dry on the other.
It lies 6 to 32 blocks below sea level, 24 further down in badlands and hot biomes and 8 higher in
jungle — which is where regional water scarcity comes from.

Below the table, air that touches natural ground slowly fills with water:

```text
dug out below the table, walls of stone, gravel, dirt or sand → seeps
the same shaft lined with planks or bricks                    → stays dry
```

That is a well when you wanted it and a flooded mine when you did not, and lining the walls is the
first countermeasure, before the drainage and pumps the specification lists. Water that has come up
from below the table is groundwater, and groundwater is clean.

## Evaporation

Section 23.4 makes evaporation depend on sunlight, temperature, wind and the size of the body — and
Milestone 3 already supplies temperature and wind. Only a puddle open to the sky, above the water
table, in warm moving air and not in the rain, loses anything, and then only its topmost block.

A lake is never touched. At this scale it is fed as fast as it loses, and taking blocks out of one
would be a change to the world nobody asked for.

## Operator diagnostics

The water channel reported the raw vanilla fluid level from Phase 1 on. It now reports the measured
body and the regional table:

```mcfunction
/hardwrought debug on
/hardwrought status
```

```text
WATER | MEASURED volume=14 sources=3 exposure=0.57 quality=swamp | groundwater Y=48
```

## Not in this milestone

- Pumping water higher than it came from. Pressure carries water up to the level it entered at, the
  way a connected vessel does; anything beyond that needs the pumps of the mechanical milestones.
- Waterlogged blocks. A waterlogged stair holds water that is not free to move, so the simulation
  leaves it alone rather than draining it.
- The water cycle of section 23.3 as a closed loop — rain feeds nothing back yet.
- Soil moisture (section 23.5), which needs the crops and irrigation of the agricultural milestones.
- Sand, charcoal and ceramic filters and distillation (section 23.2), and freezing.
- Drainage and pumps against a flooded mine (section 68); only lining the walls helps so far.

## Verification

`gradlew.bat runGameTest` runs 74 server tests, 20 of them new for this milestone: the millibucket
arithmetic and how it maps onto the eight steps vanilla can draw, water falling down a shaft and
arriving with exactly the volume it started with, water levelling out along a trough without ever
making more, a bucket pressed into the bottom of a full shaft coming out at the top with nothing lost — once
through the solver directly and once through nothing but real ticks — a cell refusing more than it can
hold, two connected shafts coming to rest at the same height, how the carried amount grows with what
stands on top, exact partial amounts surviving being stored and read back, the ordering of the water
qualities and what boiling does to each, the bundled profiles loading and rejecting bad data, water
bodies being measured on a really built pool, the water table being regional and repeatable from the
seed, which ground lets water through and which does not, containers remembering what was poured into
them, the registered jobs, and the diagnostic channel.

The conservation tests are the ones that matter: they start with a known volume, run the simulation,
and require the total afterwards to be the same number.

One of them runs no solver call of its own at all. It puts a block of water on an overgrown ledge and
lets only real ticks pass, so it covers the path that decides whether any of this works in play:
something disturbs the water, the disturbance reaches the simulation, and the water actually goes.

It also asserts that the simulation job is still running and has run repeatedly. A job that throws is
switched off for the rest of the session by design, and water then stops wherever the last pass left
it — which looks exactly like a simulation that was never written. Checking that the water moved at
all is not enough to catch it: a single transfer before a crash already looks like movement.

`gradlew.bat runClientGameTest` builds a real stone trough in a live world with a full block of water
at each end and a gap between them. It then checks the three things that can only be checked with the
game actually running: the water spreads into the gap, the gap never becomes a full block out of
nothing, and the total across the trough is still exactly the two thousand millibuckets that went in.
