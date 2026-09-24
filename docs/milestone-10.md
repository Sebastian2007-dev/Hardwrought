# Milestone 10: Mechanical Age

Specification milestone 10 — crank, gears, shafts, belts, water wheel, windmill — and sections 72
to 74 and 92. The crank, the shafts, the crusher and the bellows existed before as an on/off
driveline; this milestone gives the lines speed and strength and adds everything that connects and
drives them.

## Lines, speed and strength

A **line** is every turning part connected to every other: axles through touching faces, gear
teeth, belts. `Kinetics` walks it (at most 256 parts) whenever something about it changes — a part
placed or broken, a crank started or let go, a wheel's water or a mill's wind changing — never every
tick.

- Every part turns at a fixed **ratio** of every other. The line has one speed; the parts follow.
- **Sources** set the speed and give **strength** (stress units). With several sources the fastest
  sets the speed and their strength adds up.
- **Machines** take strength: their impact times the speed they are driven at. Gearing a machine up
  makes it faster *and* heavier to drive — torque against speed in one rule.
- A line asked for more than its sources give is **overstressed** and stops. A line whose gearing
  contradicts itself, or whose sources turn against each other, is **jammed** and stops.

Crouching with an empty hand on any turning part reads the line: speed, load and capacity, or why it
stands still.

| Source | Speed (RPM) | Strength |
| --- | --- | --- |
| Hand crank, while cranked | 16 | 160 |
| Crank box (creative) | 32 | 4096 |
| Water wheel | up to 32, by the current | up to 768 — about 460 with three paddles in a river |
| Windmill | 4 – 24, by the wind | up to 1024 |

| Machine | Impact (per RPM) | At a hand crank's 16 RPM |
| --- | --- | --- |
| Starter crusher | 8 | 128 — one fits on a hand crank, two do not |
| Bellows | 4 | 64 |

The crusher works in proportion to its speed: at 16 RPM a chunk takes four seconds, at 32 two.

## Parts

| Part | Connects |
| --- | --- |
| Shaft | its two ends, along its axis |
| Cogwheel | like a shaft, plus teeth: another cogwheel straight beside it on a parallel axle turns the other way at the same speed |
| Large cogwheel | like a shaft, plus teeth: a small cogwheel across its corner turns the other way at twice its speed |
| Gearbox | an axle on every face; a line turns a corner through it. Opposite faces turn opposite ways |
| Belt (item) | used on a shaft, then on a second parallel shaft beside it (same axis, up to 8 blocks away): both turn together. Breaking either end drops the belt |

## Water: the current

The water in this world is finite and settles, so a river's flow is not its water moving — that
would drain it into the sea. `WaterCurrent` lays a current over the water instead:

- **River current**: when a chunk in a river biome is loaded the first time, the long axis of its
  water at sea level is the river's line, and the direction along it is towards the nearer sea
  (looked for up to 768 blocks both ways; with none in reach, a direction fixed per 256-block
  region). Saved with the chunk and synced to clients.
- **Running water**: every transfer of the water simulation leaves a current along it for two
  seconds — draining channels and falling water.

The current is added to vanilla's fluid flow, so players, items and boats drift with a river.

## Water wheel

A hub on a horizontal axle; the wheel around it is three blocks across and drawn by a renderer.
Each of the eight cells around the hub that holds water adds the push of that water's current along
the way the paddle there moves. A wheel across a river with its lower paddles in the water turns; one
along the river does not; still water turns nothing; water falling past one side turns it like an
overshot wheel. A solid block in any of the eight cells jams it.

## Windmill

A hub facing into the wind with the axle leaving by its back. The sails sweep five blocks across.
Wind is stronger with height (from ×0.25 at sea level to ×3 at sea level + 192), ×1.25 in rain, ×1.6
in a thunderstorm, and rises and falls over five minutes — each 64-block region at its own moment.
Every block in the sails' circle takes a share; below 60 % open air they do not turn.

## Recipes

All at the nailed workbench.

| Result | Recipe |
| --- | --- |
| Cogwheel | shaft + planks |
| Large cogwheel | cogwheel + 2 planks |
| Gearbox | 2 cogwheels, 3 planks |
| Belt (2) | 3 leather in a row |
| Water wheel | shaft in the middle, planks and sticks round it |
| Windmill | shaft in the middle, wool and sticks round it |

## Tests

`KineticsGameTests`: gear ratios and direction, the gearbox corner, belts and dropping them, a hand
crank carrying one crusher and overstressed by two, a water wheel still in still water and turning in
running water, jammed by a block, a river current kept with its chunk and pushing through the fluid
flow, and a windmill turning high in open air but not walled in. `MachineryClientGameTest`
photographs gears, belt, wheel and sails.
