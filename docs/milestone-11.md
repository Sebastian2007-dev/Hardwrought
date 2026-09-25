# Milestone 11: Oil & Chemistry

Specification milestone 11 — oil reservoirs, natural gas, drilling, refining, chemical raw materials
— and sections 60 to 65. It builds on three milestones before it: the geology of Milestone 6 decides
where the oil is, the driveline of Milestone 10 turns the rig that reaches it, and the gas blocks of
the environment carry what escapes from it.

## Reservoirs

`Reservoirs` derives them from the world seed like `Geology` derives its ore bodies: nothing is stored
but how much has been drawn out of each.

- Only **sedimentary** regions carry oil or gas — it forms from what old seas laid down — and 65 % of
  them do. One reservoir per region, kept inside it.
- 60 % are **oil with a gas cap**, 40 % **gas fields**.
- An ellipsoid 40–96 blocks across and 10–20 high, its middle between Y −48 and Y 16, its top domed:
  a well over the middle strikes it soonest.
- It holds its volume times 4 mB (or gas units) per block: a medium reservoir is a few hundred
  buckets. **It runs out.** What has been drawn is saved in `CoreSaveData` under its region, and its
  pressure falls as it empties — to 5 % of its first pressure just before the end, then nothing.

## Prospecting

Sneaking with a pickaxe on natural rock (Milestone 6) now also reads for oil in sedimentary rock:

| Tool | Reads |
| --- | --- |
| Any pickaxe | standing over a reservoir: that it is there, oil or gas, and the height of its top; otherwise a faint smell of it within 96 blocks |
| Iron and better | bearing, distance and the height of its top, within 160 blocks |

Operator diagnostics report the reservoir under the player or the nearest one on the ore channel,
with its pressure and how much is left.

## The drilling rig

A machine on the line (`DrillingRigBlock`), driven from any side. Impact **12 per RPM** — 192 at a hand
crank's 16 RPM, more than the crank's 160: a rig needs a water wheel or a windmill.

1. **Boring.** It counts its way down through the rock under it at half a block a second at 16 RPM
   (faster or slower with the line). The blocks are not dug out; a borehole is a hand's width.
2. **Striking.** At the top of the reservoir under its column it becomes a **well**. Over nothing, it
   bores to the bottom of the world and comes up **dry**.
3. **Pumping.** Oil into a 16-bucket tank at 50 mB/s at full pressure and 16 RPM, gas into a
   64-unit store at 1 unit/s. An oil well brings the gas of its cap up with the oil (a fifth of a gas
   well's rate).

Using it with an empty hand reads it: depth, what it struck, tank and store. An **empty bucket** draws
a **bucket of crude oil**, an **empty gas canister** a **natural gas canister** (16 units).

### Gas: section 61's risks

What the store cannot hold is let go once a second:

- with a **gas pipe** on top of the rig (Milestone 10's pipes join rigs now), up the pipe and out at
  its open end;
- with **no pipe**, into the air right at the rig — as methane gas blocks, with everything the gas
  system makes of methane: it suffocates in a shed and **explodes at the first flame**.

A well whose gas has nowhere to go chokes: past twice its store it stops pumping until the gas is let
out or drawn off. Natural gas is modelled as methane, which it mostly is.

## The still

A copper pot still (`StillBlock`) with a screen. It works only with fire **directly under it** — a lit
furnace of any kind, a campfire, a burning forge, fire, lava or magma — and a batch takes 20 s. It only
starts a batch when there is room for everything it can give, so it never spills. Hoppers: charge in
from the top, bottles from the sides, products and empty containers out of the bottom.

| Charge | Needs | Gives |
| --- | --- | --- |
| Bucket of crude oil | 3 glass bottles | light spirit, fuel, heavy oil (bottled), bitumen; 35 %: raw sulfur (55–80 % pure); the bucket back |
| Bucket of **seawater** | — | salt (85–95 % pure); the bucket back |
| Raw sulfur | — | sulfur, refined: what was not sulfur shrinks to a twelfth (70 % → 97.5 %) |

## Products and purity

| Item | Use now |
| --- | --- |
| Fuel | furnace fuel, 3200 ticks (two coal), gives its bottle back |
| Natural gas canister | furnace fuel, 12000 ticks, gives its canister back |
| Light spirit, heavy oil, bitumen | chemical feedstock for later milestones (solvents, lubricants, road and waterproofing) |
| Raw sulfur, sulfur, salt | section 64's raw materials, with **purity** (section 65) |

**Purity** is a data component (`hardwrought:purity`, 0–1) shown in the tooltip. Stacks only join
at the same purity, rounded to a tenth of a percent.

## Recipes

All at the nailed workbench (unlisted, so the strict default of `BenchTier` applies). The journal
entry **Black Gold** points to them once a player has held a water wheel or a windmill.

| Result | Recipe |
| --- | --- |
| Drilling rig | logs, iron ingots, two shafts, an iron pickaxe head |
| Still | 5 copper ingots and a glass |
| Gas canister (2) | 6 iron ingots |

## Tests

`OilGameTests` (7): reservoirs only under sedimentary rock, in about two thirds of it, inside their
region and the same every time; a reservoir's domed top and its falling yield down to nothing; a rig
boring down to the depth of a reservoir, striking it, pumping oil and gas and filling a bucket, with the
draw saved; gas escaping at a rig with no pipe and out of the pipe's end with one; a still over a fire
parting crude oil into its four fractions while one without fire does nothing; salt from seawater and
refined sulfur from raw; the reservoir draws surviving a save.
`MachineryClientGameTest` photographs the rig, the still over a campfire and the still's screen.

## Not in this milestone

- **Fluids in the world.** Oil does not flow and is not poured; it moves in buckets. Tanks, pumps,
  valves and pipes for liquids are section 66's unified fluid system, and later.
- **Uses for the feedstocks** beyond fuel: lubricant for machines, solvents, asphalt, sulfur's
  chemistry — the processes that will ask for purity.
- **Blowouts, well fires and flaring** as their own events. The gas risks are the gas system's.
- The remaining raw materials of section 64 — limestone, quartz, graphite, phosphates — which vanilla
  partly has already and which nothing asks for yet.
