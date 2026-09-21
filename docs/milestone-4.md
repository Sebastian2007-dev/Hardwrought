# Milestone 4: Water

Milestone 4 implements specification section 23 and the aquifers of section 68: water has a finite
supply, a quality, a table it sits above underground, a sky that gives it back as rain, and a sun
that takes it away again.

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
Water enters the world only where something puts it there: a bucket being emptied, a spring seeping
up out of the water table, or rain falling on ground that is open to the sky.

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

Waterlogged blocks, bubble columns and water plants participate in the same finite-volume model.
A newly encountered carrier contains one block (1000 mB); partial amounts live in the chunk table.
When the last water leaves, a waterlogged stair becomes a dry stair, while a bubble column or plant
disappears. None of these blocks can act as an inexhaustible hidden source.

### Bounded work

Still water costs nothing: only disturbed cells are looked at. Vanilla already schedules a fluid tick
on water whenever anything beside it changes, and that scheduled tick — the one thing kept from the
old behaviour — is exactly the signal that a cell has been disturbed.

Past that, no more than 1024 cells are moved per tick, no tick may spend more than 1.5 milliseconds
on flow before deferring the remaining cells, and at most 32,768 disturbed cells are remembered at
once. Work rotates between dimensions so a busy Overworld cannot starve the Nether or End. Within a
dimension, cells within 32 blocks of a player run before cells within 96 blocks, which run before
distant water. Small repeated batches let a newly woken pressure wave continue during the same tick
instead of advancing only one cell per tick. A very large disturbance is therefore bounded rather
than freezing the server, while water the player is watching remains responsive.

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

Section 23.2. The question is answered in order. A mark left on the block by something that was
poured or seeped into it decides first. Then a datapack profile for the biome;
`hardwrought/water_quality/*.json` lists biomes and what their standing and running water contains.
Without a profile, the surroundings decide:

| Where | Quality |
| --- | --- |
| Ocean biomes | salt |
| River biomes, and any flowing water | river |
| Below the local water table | what the region's aquifer holds — usually fresh |
| Any other still pool | swamp |
| A large still body | river |

Nothing taken off the surface is fresh. Fresh water comes out of the ground or out of a pot, which
is exactly what gives a well and a fire their point.

### Water carries what is in it

Quality used to be re-derived from the biome every time anyone looked. That works for a lake sitting
where it was generated and for nothing else: seawater carried inland became clean the moment it was
poured out, a spring lifted out of a well was swamp water again, and salting a pond was impossible.

Water now carries what is dissolved in it, and carries it while it moves. Every transfer the solver
makes takes the quality along, and what arrives is mixed into what was already there:

```text
the worse of the two wins as soon as it makes up a quarter of the mixture
```

Contamination travels the easy way and cleanliness the hard way. A bucket of seawater ruins a barrel;
a cup of it disappears into a lake; and washing a spoiled cell out again takes several times its own
volume of clean water. That rule is pure arithmetic on two amounts, so it is checked without a world.

Only cells that differ from their surroundings are stored, exactly as only partial amounts are — the
table is a chunk attachment, ordinary water in an ordinary lake costs nothing, a cell diluted back to
what its surroundings would say drops out of the table again, and water that is gone takes its mark
with it. A chunk holds at most 4096 marked cells; past that a mark is not recorded and the water
reads as its surroundings, so a flooded mine cannot grow an unbounded table.

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

A waterskin, a glass bottle and a bucket all record what was poured into them, in a
`hardwrought:water_quality` item component. Without that, water would silently become clean the
moment it left the block it came from, and the whole section would have nothing to say. Filling a
bottle and filling a bucket are both taken over from vanilla for that reason: the quality has to be
read at the moment and the place the container is dipped. Emptying a marked container puts that
water back into the world with its quality, which is what makes salting a pond possible.

Filling a bucket has a second reason to be taken over. Vanilla removes the whole block whatever is
in it, which destroys the surplus a cell under pressure carries — up to three extra blocks of water
at the bottom of a tall column. A bucket now takes exactly one thousand millibuckets, and refuses
water that is not there rather than inventing the rest.

Filling anything takes that volume out of the world. A bucket needs a whole block and leaves what is
over; a bottle takes a tenth, so a puddle is enough for one; a skin takes up to eight hundred and
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

A region has a table and it has a **reserve**. The table says where water stands, the reserve says
how much is left — and the reserve is what makes the table move:

```text
natural table   seed and biome, the same answer every time the same region is asked
drawdown        up to 12 blocks lower as the reserve empties
reserve         finite millibuckets, taken by springs, put back by weather
```

The natural table is derived from the world seed per 64-block region, so it needs no stored table for
the world. It is read **at the centre of the region**, not at the block, or a well would work on one
side of a garden and be dry on the other. It lies 6 to 32 blocks below sea level, and a datapack
profile moves it from there.

Without a finite reserve a well would be exactly the infinite water source section 23.1 removed. With
one, every spring, every well and every flooded shaft draws on the same regional store, and as it
empties the table falls with it: the well that worked last summer is dry until it is dug deeper, and
the mine that flooded day and night drains the ground around it and eventually falls quiet.

Ordinary ground holds five hundred buckets per region. Rain, melt and the season put it back — which
is the first piece of the water cycle of section 23.3 actually running: frozen winter ground takes
almost nothing, the spring melt fills an aquifer, and a summer downpour mostly runs off. Only regions
a player is standing in are ticked; everywhere else catches up from its saved timestamp when it is
next asked about, and stops catching up after three days so a year away is not a year simulated. Only
regions somebody has drawn on are saved at all, at most 256 of them, and a region that has filled
back up is forgotten rather than stored forever as "full".

### What the ground is made of

`hardwrought/aquifer/*.json` states, per biome, how much water the ground holds and how fast it
returns (`richness`), where its table lies relative to the seed (`table_offset`), and what the water
in it is (`quality`).

| Shipped profile | Richness | Table | Water |
| --- | --- | --- | --- |
| Desert and badlands | 0.3 | 24 blocks lower | fresh — fossil water, clean and scarce |
| Beaches, shores and ocean | 1.5 | natural | **salt** — a coastal well yields brine |
| Swamp and mangrove | 1.8 | 10 blocks higher | swamp |
| Jungle | 1.6 | 8 blocks higher | fresh |

A profile with `richness: 0` is dry rock: no amount of digging opens a spring there.

### Opening the ground

Below the table, air that touches natural ground fills with water, and how fast depends on how far
below the table it is:

```text
dug out below the table, walls of stone, gravel, dirt or sand → seeps
24 blocks or more under the table                             → floods, four times as fast
the same shaft lined with planks or bricks                    → stays dry
```

That is a well when you wanted it and a flooded mine when you did not, and lining the walls is still
the first countermeasure, before the drainage and pumps the specification lists. Water that comes up
out of the ground is marked with what the region's aquifer holds, so it stays spring water when it is
carried away — or brine, if the well was dug by the sea.

## Rain

Section 23.3, the visible half of the cycle. Rain is water arriving: the same finite millibuckets as
everything else, which then run downhill and collect where the ground is lowest.

Rain used to soak into the ground and nothing else, which left the loop open at its most obvious
point — a puddle the sun took never came back, and a farm channel that had evaporated stayed empty
through a downpour. Now a slow pass picks a few columns at random out of a 17×17 box around each
player and puts a visible amount of water on each:

| | Columns per pass | Per column |
| --- | --- | --- |
| Rain | 6 | 125 mB |
| Thunderstorm | 14 | 250 mB |

It is deliberately not spread evenly. A film thinner than the smallest step vanilla can draw is
invisible, does not spread, and would cost a block update per column for nothing; over a long rain
the same water arrives either way, but arriving in drops means it *runs somewhere*. Puddles appear in
the low places, not as a sheen on every surface.

Where it lands is vanilla's own answer: a roof or an overhang keeps it out, a desert gets nothing,
and a cold biome gets snow instead — so a frozen landscape fills no puddles until a thaw this model
does not have yet. **Leaves are not a roof**: the rain runs off the topmost block that is not
foliage, so a forest floor gets wet and no pond collects in a canopy. Water that is not yet a full
block is simply deepened, which is what refills a farm channel the sun had taken; anything else is
laid on top of what it landed on and finds its own way down.

Rain lands only in empty air or in water. Flowing water washes grass, flowers and crops out of its
way — right for a burst dam, quite wrong for weather, which would otherwise clear a wheat field every
time it rained on it. What the growth catches, it keeps.

Rain never presses a cell past a full block. Water arrives from the sky, not from a pump.

Rainwater is **fresh** — it has touched nothing yet — but it mixes into whatever it lands in by the
rule above, so a downpour on a swamp makes more swamp water, and a puddle on bare rock is a drink.

Nothing special makes a river burst its banks; it follows from water being conserved. What falls on
the channel and what runs off the land beside it both end up in it, and when that arrives faster than
the channel passes it on, the low ground next to it goes under. When the rain stops, the flood water
drains back or is taken by the sun, because a shallow sheet on land is exactly the puddle evaporation
already removes.

The amounts are small against an ocean — a hundred hours of play adds a fraction of a block to sea
level — so this does not slowly drown the world.

## Evaporation

Section 23.4 makes evaporation depend on sunlight, temperature, wind, biome, season and the size of
the body. Biomes are grouped into cold, neutral and warm climate bands. A deterministic 48-day year
contains twelve days each of spring, summer, autumn and winter; it comes directly from Minecraft's
persisted Overworld clock and needs no additional ticking or save data. The season changes the shared
outdoor temperature as well as the drying rate. Summer dries strongly, spring and autumn moderately,
and winter almost not at all.

Only a puddle open to the sky, above the water table, in warm moving air and not under local rain
loses anything, and then only its topmost block. Nearby players cannot evaporate the same surface
more than once in a pass. Connected water found during the bounded body scan is remembered for that
sample, so a lake is not flood-filled again for every one of its visible blocks.

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
WATER | MEASURED volume=14 sources=3 exposure=0.57 quality=swamp | groundwater Y=48 climate=neutral season=summer evaporation=12.4%
WATER | aquifer minecraft:overworld@2,-5 | table Y=44 (natural 48) reserve=331/512L quality=fresh
```

`/hardwrought water` adds the same reserve to the flow report, because a dry well and a well dug too
high look identical from the outside and only the reserve of the region tells them apart.

## Not in this milestone

- Pumping water higher than it came from. Pressure carries water up to the level it entered at, the
  way a connected vessel does; anything beyond that needs the pumps of the mechanical milestones.
- The water cycle of section 23.3 as a closed loop. Rain feeds the ground and the surface, the ground
  feeds springs and the sun takes puddles back — but what evaporates is not counted anywhere, so the
  sky is still an open end rather than a reservoir that can run out.
- Snow and the thaw. Cold biomes get snow instead of rain, and snow melts into nothing so far.
- Soil moisture (section 23.5), which needs the crops and irrigation of the agricultural milestones.
- Sand, charcoal and ceramic filters and distillation (section 23.2), and freezing.
- Drainage and pumps against a flooded mine (section 68); only lining the walls helps so far.

## Verification

`gradlew.bat runGameTest` runs 89 server tests, including the millibucket
arithmetic and how it maps onto the eight steps vanilla can draw, water falling down a shaft and
arriving with exactly the volume it started with, water levelling out along a trough without ever
making more, a bucket pressed into the bottom of a full shaft coming out at the top with nothing lost — once
through the solver directly and once through nothing but real ticks — a cell refusing more than it can
hold, two connected shafts coming to rest at the same height, how the carried amount grows with what
stands on top, exact partial amounts surviving being stored and read back, the ordering of the water
qualities and what boiling does to each, the bundled profiles loading and rejecting bad data, water
bodies being measured on a really built pool, the water table being regional and repeatable from the
seed, which ground lets water through and which does not, containers remembering what was poured into
them, the registered jobs, the diagnostic channel, the four calendar boundaries, and the ordering of
evaporation by biome climate and season.

Five of them cover the rain: the storm putting down more than a drizzle, a puddle really collecting
on a built platform in clean water and deepening over a second shower, water that is already full
taking the next drop on top of itself instead of being pressed, an evaporated channel filling back
up, a roof keeping the rain out, a standing wheat field surviving a downpour, and the registered job.
They place the rain themselves rather than waiting for the sky, so they never touch the shared world
weather the other batches are running in.

Six of them cover the ground and what is dissolved in the water: the aquifer arithmetic (drawing,
recharging, the catch-up bound, the drawdown curve and how the yield grows with depth), the shipped
aquifer profiles and their defaults, a region really being drained until it yields nothing and its
table standing twelve blocks lower for it, the mixing rule from both sides, and a marked cell whose
mark travels into its neighbour through one pass of the real solver and is gone once the water is.

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
