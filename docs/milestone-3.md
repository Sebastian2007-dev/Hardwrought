# Milestone 3: Environment

Milestone 3 adds the unified environmental model of specification section 13 and the systems that
depend on it: oxygen, carbon dioxide, methane, smoke, fire that needs air, room temperature and a
carried light that actually lights the way.

Like the previous milestones it is server-authoritative. The server decides what the air at a
position is; the client renders the result and, without an instrument, is deliberately not told the
numbers.

## The environmental cell

Section 13 explicitly rules out simulating every air block every tick and asks for room volumes,
environmental cells and cached periodic updates instead. `RoomScan` is that cell: one bounded flood
fill from a position.

```text
scan from a position
  ├─ reaches the open sky        → OPEN   outside baseline, no cell at all
  ├─ leaves the loaded chunks    → OPEN   (a tick job never loads a chunk to find out)
  ├─ exceeds 512 blocks          → LARGE  a cave or hall: still has air, tracked per chunk region
  └─ closes inside the budget    → ROOM   its own atmosphere, identified by where the fill closed
```

A large space is deliberately **not** reported as open air. Running out of budget means "we did not
finish counting", not "this is outdoors", and a deep cave is exactly where section 18 wants gases to
collect. Since such a space has no single stable point to name it by, it is identified by its chunk
region instead — the third option section 13 offers next to room volumes and environmental cells. Its
volume counts as the full budget, so breathing alone never uses up the air of a cave.

### Walls, apertures and holes

A wall does not have to be airtight to make a room, and this is where playing the game corrected the
model twice. First, collision is the wrong test in both directions: an open door still has a thin
collision slab, so a hut with its door wide open was counted as sealed. Then, treating anything
non-airtight as "outdoors" was wrong the other way — a room with a window is still a room, it simply
exchanges air with the outside.

Every block bordering the fill falls into one of three roles:

| Role | Blocks | Effect |
| --- | --- | --- |
| **Part of the room** | air, torches, carpets, ladders, signs, plants | counts toward the volume |
| **Aperture** | an **open** door, trapdoor or fence gate; iron bars, fences, decorative walls | bounds the room and lets gas through |
| **Seal** | any full block, glass panes, a **closed** door, trapdoor or fence gate, water and lava | bounds the room and holds the air in |

Doors, trapdoors and fence gates are judged by their open state; everything else is judged **face by
face**, so a slab ceiling seals upward while the same slab leaves the space above it open, and a
carpet seals downward while staying part of the room. Glass panes are the one exception to the face
rule: a pane window connects to its neighbours into a continuous sheet of glass and holds the air in,
even though it never fills a whole block.

An aperture is not simply present or absent — how much it lets through is what matters:

| Aperture | Weight |
| --- | --- |
| An open door, trapdoor or fence gate | 1.00 |
| Iron bars | 0.80 |
| A fence | 0.70 |
| A decorative wall | 0.40 |

An open door is not a partial obstruction. The leaf swings flat against the wall and what is left is
an ordinary doorway, so it counts as a whole block of opening and the room simply breathes with the
outside.

Only a real hole — a missing block the fill can walk through until it sees the sky — makes a position
outdoors.

The fill also collects, in the same pass and without a second scan: the enclosed volume, the average
insulation of the boundary blocks, how much exposed coal is in the walls, and the position of
everything burning inside (at most 32).

Whether the sky is open is read from the **heightmap**, not from sky light. The light engine updates
asynchronously, so a roof closed this tick would otherwise keep counting as open until the light
caught up.

### Bounded work

- At most one flood fill per player per 40 ticks, and only when they have moved more than 2 blocks.
- Only cells that currently hold a player are simulated at all.
- A room nobody is standing in is caught up from its saved timestamp the next time someone walks in,
  capped at 10 minutes of catch-up.
- At most 128 live cells and 256 saved ones; the room unvisited longest is forgotten first.

## Gases

`GasMixture` carries oxygen, carbon dioxide and methane as volume fractions and smoke as a density.
Every operation is bounded, so a cell can never reach a state the rest of the simulation would have
to guess about.

Per second, divided by the room volume in blocks (balancing starting values):

| Source | O₂ | CO₂ | Smoke | Methane |
| --- | --- | --- | --- | --- |
| Each occupant | −0.005 | +0.003 | — | — |
| Each open fire, lava, lit furnace | −0.25 | +0.21 | +0.22 | — |
| Each torch or candle | 15 % of an open fire | | | |
| Coal-bearing walls under deep cover | — | — | — | up to +0.020 |

Even a shut cell still leaks 0.4 % toward outside air per second, and the opening area adds to that:

```text
ventilation        = 0.4 %/s + 120 %/s × aperture area / volume
equilibrium oxygen = 20.9 % − (rate / volume) / ventilation
```

That relationship, not the raw rates, is what the breathing values are calibrated for and what the
tests assert:

| Space, one occupant | Ventilation | Settles at O₂ | Settles at CO₂ | Verdict |
| --- | --- | --- | --- | --- |
| 30 blocks, shut | 0.4 %/s | 16.7 % | 2.5 % | stuffy, survivable indefinitely |
| 30 blocks, **door open** | 4.4 %/s | 20.5 % | 0.3 % | outside air, for all practical purposes |
| 15 blocks, shut | 0.4 %/s | 12.6 % | 5.0 % | impaired, not a death trap by itself |
| 8 blocks, shut | 0.4 %/s | 5.3 % | 9.4 % | lethal |
| 8 blocks, **door open** | 15.4 %/s | 20.5 % | 0.3 % | fine |
| A cave (budget volume) | 0.4 %/s | 20.8 % | 0.1 % | breathing alone never uses it up |

Breathing is deliberately the slow part; a fire is what makes air disappear quickly. And not every
flame eats the same amount: an open fire or lava is the full measure, a campfire or furnace counts
40 % because it burns contained and carries its own smoke upward, a torch or candle 15 %.

| Campfire in 30 blocks | Settles at O₂ | Outcome |
| --- | --- | --- |
| shut | 6.7 % | the air is used up and the fire goes out |
| door open | 19.6 % | it keeps burning |

That is section 19's `Campfire → Chimney → Ventilation` progression in its first form: a fire indoors
has to be ventilated, not merely enclosed.

| Threshold | Value | Effect |
| --- | --- | --- |
| O₂ impaired | 16.0 % | stamina recovery drops, breathing costs calories |
| O₂ dangerous | 12.0 % | |
| O₂ lethal | 8.5 % | `hardwrought:bad_air` damage |
| CO₂ noticeable | 1.0 % | fatigue rises |
| CO₂ severe | 4.0 % | |
| CO₂ lethal | 8.0 % | `hardwrought:bad_air` damage |
| Methane flammable | 5 % – 15 % | ignites at any flame in the room |
| Smoke choking | 0.30 | `hardwrought:smoke` damage, the view closes in |

Both hazards have their own datapack damage types tagged `bypasses_armor`, so a death message names
the real cause and no armor protects against a gas. They are also in `hardwrought:is_suffocating`
together with vanilla drowning and being crushed inside a block, so the combat feed reports them as
suffocation rather than as a blunt impact.

## Gases have weight

A cell stores one average mixture, but gases do not mix evenly, and where a gas sits is half of what
makes it dangerous. Carbon dioxide is half again as heavy as air (44 against 29 g/mol) and pools in
the low places of a mine — which is why blackdamp kills people in shafts and cellars. Methane is far
lighter (16 g/mol) and collects against the roof, which is why firedamp is found there and why a
safety lamp is held **up**. Oxygen (32 g/mol) is close enough to air to stay even, and smoke rises
with the heat that makes it.

```text
ceiling   methane ×1.6   CO₂ ×0.4   ← firedamp gathers here
middle    the stored average
floor     methane ×0.4   CO₂ ×1.6   ← blackdamp gathers here
```

The air is sampled at the player's own head height, so crouching in a shaft is worse for carbon
dioxide and standing tall is worse for methane. Sampling creates no gas and destroys none: the floor
and the ceiling average back to the stored value exactly.

The same applies to ignition. Firedamp is checked at the height of each flame, so a torch high on a
wall can set off a concentration that a campfire on the floor sits entirely below.

## Fire

Section 19 asks for fire that interacts with oxygen, smoke and temperature. A fire consumes the
oxygen of its room, produces carbon dioxide, smoke and heat, and **goes out below 13 % oxygen**:
fires and soul fires are removed, campfires, furnaces and candles lose their lit state.

A torch is never taken away from a player, because vanilla has no unlit torch. It keeps burning and
keeps consuming air. A light source that can go out belongs to the lighting progression of section
20 together with the lamp items it describes.

## Methane

Section 18.3 puts methane in coal regions and deep caves. The geological regions arrive in Milestone
6, so until then the honest available signal is exposed coal in the walls of a deep room.

Depth is measured as the rock actually covering the room — the heightmap above it — rather than an
absolute height, so the model behaves the same in a normal world, a superflat one and any later
vertical world. Below 24 blocks of cover nothing seeps at all; it reaches full strength at 84.

The rate is deliberately slow: a fully coal-lined 27-block room needs roughly a minute of standing
still to reach the flammability window, bare rock several times that. It has to be a hazard a player
can walk away from, not an instant bomb. A concentration inside the real flammability window,
together with any flame in the room, then sets it off: an explosion scaled by the concentration,
which burns the gas and part of the oxygen and leaves smoke behind.

## Temperature and wind

Room temperature is the outdoor temperature plus the heat of whatever is burning inside, scaled by
the room volume and by how well the boundary insulates. The insulation table follows the ordering
section 17 states directly — wool excellent, wood and earth good, stone medium, glass poor, metal
very poor — and is the extension point for the real per-material thermal constants that already exist
in the material definitions.

The outdoor temperature formula moved out of the survival system into the environment model, where it
belongs. The survival system now only adds what is local to one player: being in water, and the
radiant heat of a nearby flame before the whole room has warmed up.

**Wind** was declared a neutral input in Milestone 1 and is now a real value derived from sky
exposure, altitude and weather. Cold plus wind plus wet clothing is what makes a cold rain genuinely
dangerous, which is what section 15 asks for.

## Effects on the player

The dependency runs one way: the environment system never touches a player's values. It publishes a
typed `EnvironmentReading` and the survival system applies it.

- Stamina recovery is multiplied by an air factor — the oxygen input section 7 declared from the
  start and left neutral until now.
- Thin air costs extra calories; carbon dioxide raises fatigue long before it is lethal.
- Bad air drains the stamina reserve directly, so resting cannot out-recover it.
- Lethal thresholds and choking smoke deal their own damage.

## Carried light

Section 20 asks that a carried lit torch emit actual light. The light block is placed **only in the
client's own copy of the world**: nothing is written to the save file and no block update reaches the
server or any other player, so a crash can never leave stray light blocks behind. It is removed again
when the holder moves, puts the item away, or the block is no longer ours.

The brightness comes from the item itself — a block item lights as brightly as its block — so no
table has to be synchronized to the client. Only the safety lamp, which is not a block item, has an
explicit value.

`dynamicLight=false` in `config/hardwrought.properties` turns it off. A shared dynamic light that
other players can see belongs to the lighting progression.

## The safety lamp

Section 18.2 says carbon dioxide should be difficult to detect without tools, and section 18.3 lists
primitive safety methods as the first countermeasure. The safety lamp is that first step.

Without it a player gets **symptoms only**: the view closes in as the air gets worse, and stamina and
fatigue misbehave for no visible reason. Carrying one anywhere in the inventory turns the exact
oxygen, carbon dioxide and methane readings on in the HUD panel and enables the firedamp warning.
Using it reports the values in chat and calls out firedamp or spent air.

It is crafted from two iron ingots, two glass panes and a torch, and it is a light source. Fuel, a
gauze flame that reacts on its own and the later electronic detectors belong to the technology
milestones.

## Milestone 2 weapon classes completed

Three weapon classes of section 30 had profiles but no items, because vanilla has nothing that fits
them. They exist now, with placeholder sprites listed in
[textureRequirements.md](../textureRequirements.md):

| Item | Class | Character |
| --- | --- | --- |
| Flint dagger | knife | the first real weapon; two flint shards and a stick |
| Iron dagger | knife | very fast, very cheap in stamina, low reach |
| Iron greatsword | two-handed | high damage and reach, slow, breaks guards |
| Iron halberd | polearm | the longest reach, weak inside 2.4 blocks, breaks guards |

Flint is a new tool material: sharp and quick, but brittle and quickly worn out.

## Operator diagnostics

The gas and temperature channels of the debug HUD have reported `unavailable` since Phase 1 because
no system supplied them. They are supplied now:

```mcfunction
/hardwrought debug on
/hardwrought status
```

```text
GAS | O2=17.42% CO2=1.203% CH4=0.000% smoke=0.00 volume=27 insulation=0.50
TEMPERATURE | room=18.4C outdoor=14.1C fires=1.0
```

A space that has not been simulated yet says exactly that instead of showing the outdoor baseline as
if it had been measured. The structure and ore channels still report `unavailable`, because those
systems still do not exist.

`/hardwrought air` reports the reading for the player directly, and `/hardwrought air set <gas>
<value>` replaces one component of the air in the sealed space they are standing in. The interesting
states of this model otherwise take minutes of standing in a sealed room to reach, which makes both
balancing and testing slow; the command is what the suffocation test uses to reach them at once.

## Periodic hazards

The two periodic hazards — air damage every 2 seconds, body-temperature damage every 10 — count
their own simulation passes. They must not be gated on the saved simulation clock: the save is only
written after the scheduler has finished a tick, so inside a pass that clock is always one tick
behind and a test such as `savedTicks % 40 == 0` can never be true. A test locks that down.

## Not in this milestone

- Humidity and airflow as their own fields of section 13; only temperature and the gases are modelled.
- Steam and toxic industrial gases (section 18) — they belong to the chemistry milestones.
- Chimneys, ventilation shafts, mechanical fans and detectors (sections 18.3 and 19).
- Fire spread driven by wind, fuel and building material (section 19).
- True darkness, moon phases and dark adaptation (sections 20 to 22); only the carried light of the
  Milestone 3 list is implemented.
- Smoke as a moving body of gas between rooms; it stays inside its own cell.

## Verification

`gradlew.bat runGameTest` runs 54 server tests, 23 of them new for this milestone: gas bounds and the
monotone stress functions, ventilation converging on outside air, the methane flammability window,
sealed-space versus open-air detection on a really built shell, fire and coal found in the walls, the
insulation ordering of section 17, the saved room table staying bounded and forgetting the oldest
room first, the gas and temperature diagnostic channels now being supplied while the others still say
`unavailable`, the two damage types being registered and bypassing armor, the three completed weapon
classes, and the network protocol.

Seven of them come straight from playing the game: an opening leaving the room a room while a closed
door keeps it shut, a pane window sealing it and a missing block making it outdoors; the per-face wall
rules; how much each kind of opening actually lets through; a closed space past the budget being a
large space rather than outside air; an open door leaving a room within half a percent of outside
air and a fire needing it; and the gas layering, including why a safety lamp is held up. Three more
are regressions found the same way: that the
saved clock is not a usable phase source inside a simulation pass, that the fatigue budget leaves
room for a full day and night, and that air at the observed live values really is past the lethal
thresholds.

`gradlew.bat runClientGameTest` builds a real sealed stone room around the player in a live world and
checks the whole path end to end: the server recognises the enclosed 27-block space, its oxygen falls
below the outdoor baseline, the client is told without being given numbers it has no instrument for,
a safety lamp turns those numbers on, and a carried torch places a light block in the client level.
It then drives the oxygen below the lethal threshold with the operator command and confirms the
player actually loses health, before opening the roof again and confirming the room reads as open
air. Without the pass-counting fix that last check times out, which is exactly how the missing damage
was caught.
