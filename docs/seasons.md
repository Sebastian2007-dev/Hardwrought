# Temperature and seasons

Specification sections 14–17 (temperature, wetness, clothing, heat transfer), 23 (water) and 24
(seasons). Written for whoever next has to change these numbers.

The thing to understand before anything else: **a season does not move every biome by the same
amount.** If it did, the tundra would be worse than the plains by a fixed margin forever, which is a
calendar nobody can play with. Real continents do not work that way either — the tropics barely
notice the year while the far north swings forty degrees across it — so `Season` carries an offset
*per climate band*, and the consequence is the whole point of the system: **a cold biome is
somewhere you can go in summer and somewhere that will kill you in winter, and the desert is the
other way round.**

## The year

`SeasonCycle` — twelve Minecraft days a season, forty-eight a year, spring first. It is derived
from the world clock and stores nothing, so it survives reloads for free and cannot drift out of
step with the day/night cycle.

`SeasonState` is the holder two things need that `SeasonCycle` cannot serve: vanilla's `Biome`
decides rain-or-snow without being handed a world, and the client has to draw a fog the server
rolled. The server sets it each tick and broadcasts it twice a second; the client adopts it.

| Season | Cold biome | Temperate | Hot | Growth | Rain | Evaporation | Fog |
|---|---:|---:|---:|---:|---:|---:|---:|
| Spring | +2 °C | 0 | 0 | 125 % | ×1.00 | 0.80 | — |
| Summer | **+14 °C** | +7 | +5 | 145 % | ×0.55 | 1.60 | — |
| Autumn | +2 °C | −1 | 0 | 100 % | ×1.45 | 0.55 | 35 % a day |
| Winter | **−22 °C** | −14 | −6 | 50 % | ×1.00 | 0.12 | — |

## What the body does with it

`Thermal` is the whole model, as pure functions, so it can be read in one place and tested without
a world. `SurvivalSystem` gathers the inputs; `Thermal` decides what they mean.

**The cold is not linear.** Every degree below comfort costs more than the one before it, so the
distance from comfort is raised to the power 1.5 before being scaled. This is what lets a single
rule say both *a cold morning is unpleasant* and *a winter tundra kills you*. A straight line can
only say one of them, and whichever it says it gets the other badly wrong — the previous linear
model put a naked player in a −35 °C blizzard at 34.4 °C of core temperature, which is to say
nowhere near dead.

**Water is not cold air.** A clothed body is in balance with air at 20 °C and with water at 33 °C,
because still water takes heat off a body roughly twenty-five times faster. From that one
substitution comes everything the water does: a desert lake sits a body at 36.4 °C — pleasantly
*under* its own temperature — and a winter tundra lake at 32 °C, which is lethal. Neither case is
written down anywhere; they both fall out of `waterTemperature` plus a different balance point. A
body of water takes on only 45 % of the air's swing and never goes below freezing, which is why
tundra water is at zero however cold the night above it gets.

**Clothing can be worth less than nothing.** `ArmorProfile.insulation` now runs from −1 to 1 and
the sign is the design. Leather is +0.85; chain is −0.25; plate is −0.45, because a steel plate is
a radiator strapped to a person. Insulation is also weighted by the same coverage the damage
resistances use rather than summed — it used to be summed, which made four pieces of anything come
to about 0.5 whatever they were made of, and left plate insulating *better than leather*.

On the hot side there is no good answer: insulation traps the body's own heat, metal carries the
sun's in, and all of it has to be carried. Section 36 asks that there be no universally perfect
armor; this is where that is paid.

### What it comes to

Core temperature, by biome and season. `!` is where thermal damage starts.

| Biome / season | Air | Bare | Leather | Iron |
|---|---:|---:|---:|---:|
| Snowy plains / spring | 1.6 | 34.2 | 36.6 | 33.3 ! |
| Snowy plains / summer | 13.6 | 35.8 | 36.8 | 35.6 |
| Snowy plains / **winter** | −22.4 | **30.0 !** | 35.8 | **30.0 !** |
| Plains / winter | 0.0 | 34.0 ! | 36.5 | 32.9 ! |
| Desert / summer (noon) | 43.6 | 40.2 | 41.4 ! | 41.3 ! |
| Desert / winter | 32.6 | 38.2 | 38.7 | 38.7 |

Thermal damage scales with how far gone the body is rather than being a flat tick, so a player who
walks into a winter tundra in iron is not given the same quarter of an hour as one who is merely
chilly.

### Fire, and the rune

`RadiantHeat` replaced a flat *+8 °C somewhere in a 7×5×7 box* with a square falloff over five
blocks, and — the part the old one got wrong — **a campfire that has been put out gives no heat**,
and neither does an unlit furnace. There is now a right distance to sit at and a player can find it.

A fire alone does not make a winter tundra survivable (bare, beside a hearth: 31.6 °C, still
fatal); a fire plus clothing does (leather: 36.2 °C). That is deliberate — the answer to winter is
to have brought something to wear, and the fire is what makes the camp work.

`Temperance` is the armor rune for players who have been beaten by the weather often enough. It
neither warms nor cools: it moves the air the body argues with towards what the body would have
chosen, which is the same thing from the inside and much the better thing from the design's side —
one enchantment covers the tundra and the desert with no special case for either. Graded I–III and
weighted by coverage, so one helmet is a seventh of it and a full set at level three is as near
weatherproof as the game offers. Never quite all the way: there is always a little weather left.

## Where snow and ice are worked out

Both ride **vanilla's own per-chunk precipitation tick** (`ServerLevelWeatheringMixin` →
`Weathering`), not a pass of their own. They used to walk outward from each player, and that was
wrong in a way anybody notices within a minute of playing: snow piled up in a small circle that
followed the player about and the world ten blocks further on had none. Covering a circle of radius
twelve at ten columns every ten seconds takes the better part of ten minutes, by which time the
player has walked off and started a new circle.

`tickChunk` rolls a position in *every* ticking chunk and hands it to the precipitation tick, which
is uniform coverage of the whole loaded world at a rate the game already budgets for — and with no
notion of where anybody is standing. A lake is frozen because it is winter, not because somebody is
looking at it.

## Snow

`Snowfall`. Vanilla snow is one layer and then it stops, because `maxSnowAccumulationHeight` is 1
and because a snow layer may only sit on another one when that one is a full eight layers deep.
Both of those are worked *with*: this adds layers the way vanilla would, and starts the next block
on top when one is full.

Depth depends on the sky. Under a canopy snow barely gathers (3 layers); in the open it reaches a
block outside winter and **three blocks by deep winter**, scaled by how far into the season it is.
That difference is the point — treelines and overhangs stay passable while the fields do not, which
makes snow an obstacle with a shape rather than a uniform nuisance.

It melts above **4 °C**, and that number is higher than it looks like it should be because of the
sun. `outdoorTemperature` adds three degrees to an open, sunlit place, so a winter plains at 0 °C
reads as 3 °C at midday — and the first melting point of 1.5 had snow quietly evaporating off the
fields every winter afternoon faster than it could fall. Four leaves a winter day below it and a
spring day (14 °C, 17 in the sun) far above it, which is where a thaw belongs.

## Ice

`ThinIceBlock`, `FrozenWater`, `Freezing`. Freezing is the one thing the finite-water system could
never do, and the reason was always the same: vanilla ice is a whole block and Hardwrought water is
measured in millibuckets. Freezing a one-eighth puddle with `minecraft:ice` would hand the player
seven eighths of a bucket they never had, and a frozen lake would stand above its own banks.

`ThinIceBlock` has the same eight steps water is drawn in and freezes at the height the water stood
at. The exact millibucket figure waits in `FrozenWater`, a chunk attachment of the same shape as
the liquid one — kept separate so the flow solver cannot see frozen water and try to move it. **A
cell that freezes and thaws is the same cell afterwards, to the millibucket**, which is the
property the whole water system exists to keep and is what `SeasonGameTests` guards.

What decides it is the **air over the water, not the water's own temperature**, and that
distinction cost a winter. `Thermal.waterTemperature` models the bulk of a lake — slow, deep, and
taking on under half the air's swing — which is exactly right for deciding what a lake does to a
body in it and quite wrong as a freezing test: it meant the air had to reach −15.5 °C before a
surface would skin over, so a winter plains at 0 °C never froze at all. A surface freezes when the
air above it is below freezing; the depths of the lake have nothing to do with it. Freezing and
thawing use a band (−2 °C and +2 °C) rather than one threshold, because a winter plains swings
either side of zero between noon and midnight and a single figure would have the lake freezing and
thawing twice a day.

Only the surface freezes, so a frozen lake still has a lake under it and cutting a hole in one is
worth doing — which is also why winter is survivable in a cold biome at all. `BiomeFreezeMixin`
takes vanilla out of the ice business entirely, frost walker included, for the same reason: a boot
enchantment should not be able to conjure water either.

## Autumn fog

Rolled once a day from the world seed and the day number — derived, not stored, like the calendar.
(`/hardwrought weather fog` is the one exception, and a deliberately short-lived one.)
It begins at first light because a real one does and because a fog that could begin at any hour is
one nobody can plan around, and it lasts between about 2 500 and 9 000 ticks: a morning, never into
the night.

Past `SeasonFogRules.SMOTHERING` (0.45) it stops being weather and becomes an event: the sun no
longer burns the undead, and monsters may spawn in daylight. Only the light rule gives way — every
other spawn condition still applies, so the fog is dangerous rather than a flood. The two rules
share one threshold so they arrive together and a player can tell which kind of fog they are
standing in by looking at it.

### How the look turns

Two things carry the season's colour, because no one hook reaches everything.

**Biome colour** — grass, leaves, vines, lily pads and water. `BiomeColorsMixin` takes the colour
vanilla worked out and washes it, so biome differences survive underneath: a winter jungle is still
greener than a winter savanna, both are simply much greyer. These colours are **ARGB**, and the
first version rebuilt them from three channels and dropped the top byte — alpha zero is invisible,
so every leaf and every surface of water in the world stopped being drawn, while grass, being
opaque anyway, went on looking fine and hid it. `CoreClientGameTest.verifySeasonTint` exists so
that cannot happen twice.

**Sky light** — dirt, stone, planks and everything else carries a fixed texture no tint reaches.
Those are coloured by the light falling on them instead, which is cheaper and truer: a winter
afternoon is not a world repainted, it is a world under colder light. It stops at the cave mouth,
which is right.

The turn between two seasons is a **ramp, not a step**: the world starts looking like the next
season over the last three days of the current one. The light follows that smoothly every frame;
the chunk colours are baked into the meshes, so they follow it in ten steps, each one a repaint of
what the player can see. Ten repaints across three game days is one every few minutes.

### Growth and weather

`SeasonalGrowth` changes how many random ticks a plant gets rather than reaching inside every crop,
so mod crops, datapack crops and other mods' crops all follow the calendar without knowing it
exists. Which blocks count is the `hardwrought:seasonal_growth` tag — explicitly a tag, so that it
can never touch a block that merely happens to take random ticks. Gas blocks and budding crystals
get their ticks whatever the month is.

The hook sits on `BlockStateBase.randomTick`, which is as hot a path as the game has, so the
checks are ordered by cost: the season factor first, then the dice, and only then the tag. Getting
that order wrong is measurable — it showed up as unrelated machinery tests going intermittently
red.

`SeasonWeather` does not take the weather over. Vanilla still decides when the sky turns and rolls
how long the new spell lasts; this stretches or shrinks that roll. Both halves are scaled, because
shortening the showers alone would give a summer that was merely less wet rather than one that was
dry. If `advance_weather` is off the pass stands down.

`BiomePrecipitationMixin` decides rain or snow: in winter a temperate place gets snow (the plains
turn white) and a hot one gets rain — the one quarter of the year a desert sees water fall, which
overrides `has_precipitation` deliberately, because a biome declaring it never rains is describing
its ordinary year and winter is not that. In summer a cold place gets rain rather than snow,
because in summer it is not cold.

## Commands

Two for players, two for whoever is building or testing.

| Command | Who | What |
|---|---|---|
| `/day` | anyone | Day of the world, year, and the day within the season |
| `/season` | anyone | The season, how much of it is left, and what it is doing to growth and rain |
| `/hardwrought season <season> [day]` | game master | Runs the calendar forward to that day of that season |
| `/hardwrought weather fog [thickness\|clear]` | game master | Puts an autumn morning on the world now |

`/day` and `/season` are not gated and have no item to craft first. A player is asked to plan
around this calendar — to sow in spring, lay in fuel before winter, cross a tundra in the one
quarter it can be crossed — and none of that is possible if the only way to know the date is to
count sunrises. A person knows roughly what time of year it is by looking around; these are that.

Setting the season **moves the world clock forward**, by whole days, because the season is derived
from the clock and from nothing else. Forward, because the rest of the game assumes a clock that
only increases; whole days, because the result then happens at the hour it was asked at, so a world
set to winter at noon is still at noon and the winter can actually be looked at. Up to a year of
game time can pass in one command, which is why it is a debug tool.

`weather fog` lasts until the next dawn and is never saved — a forced fog cannot quietly outlive
the session it was made in. Above `SeasonFogRules.SMOTHERING` the command says so, because that is
the point where the fog stops being scenery and starts keeping the sun off the undead.

## Where to change things

- `Season` — the table above. Everything else reads it.
- `Thermal` — the body model. The exponent and the three transfer scales are the dials.
- `armor_profiles/*.json` — `insulation`, −1 to 1.
- `data/hardwrought/tags/block/seasonal_growth.json` — what follows the calendar.
- `Snowfall.maxDepth` — how deep drifts get and where.
- `Freezing.FREEZES_AT` / `THAWS_AT` — water temperature, not air.
- `SeasonFogRules.SMOTHERING` — where fog stops being weather.

`ThermalGameTests` and `SeasonGameTests` pin the *outcomes* rather than the constants — a tundra
you can visit in summer and not in winter, leather beating steel in the cold, water that conserves
itself across a freeze. Change the numbers freely; those tests are there to tell you when a change
has quietly cost one of the properties the system was built for.
