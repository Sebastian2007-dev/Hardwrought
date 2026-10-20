# Pipes

Item pipes and fluid pipes share one way of joining what is around them (`ConnectedPipeBlock`), set with
a **pipe wrench**. The idea is borrowed from the Pipez mod: every side of a pipe is set on its own.

## Sides

A pipe joins pipes of its own kind beside it (any metal), and any block that holds what it carries.
Each side is one of:

| Side | Shown as | Means |
|---|---|---|
| joined to a pipe | plain arm | part of the same run |
| towards a block, **filling** (default) | plain arm | the run puts things into the block |
| towards a block, **drawing** | arm with an orange ring | the run takes things out of the block, and never fills it |
| cut off | no arm | nothing passes; stays cut off whatever is placed there |

**Pipe wrench** (four iron ingots: a V of two over a handle of two), used on a side of a pipe — the arm, or the face
of the core facing that way:

- towards a block: filling → drawing → cut off → filling;
- towards another pipe: joined → cut off → joined. A cut between two pipes holds from both ends.

## Item pipes

As before: five metals (`ItemPipeTier`), one batch per pipe, nearest taker first, never back into the block
a batch came from. New:

- A **drawing** side takes a batch out of the block there whenever the pipe is empty, at its metal's pace,
  taking the drawing sides in turn.
- A block the run draws from is never filled by it.
- Hoppers and anything else that pushes items still put them in on any side.

## Fluid pipes

Five metals (`FluidPipeTier`), recipe: plate, glass pane, plate → 8. A cyan ring tells them from item pipes.

| Metal | mB/s through one pipe |
|---|---|
| Bronze | 100 |
| Iron | 250 |
| Nickel | 500 |
| Chromium | 1000 |
| Titanium | 2000 |

- **Downhill by weight.** A drawing side at a **tank** needs no pump: where the liquid in the tank (and the
  tanks joined to it) stands higher than a block the run fills, it runs there by its own weight —
  100 mB/s per block of drop, measured from the liquid's surface to the floor of the block it fills (to
  the surface, for another tank), and no faster than the narrowest pipe on the way. Two tanks joined by
  a pipe on one level therefore even out, as joined vessels do. (`FluidPipeBlockEntity.fall`)
- **Pumps drive a run** on top of that, and are the only thing that lifts. Everything a run draws by pump in
  a tick, at all its drawing sides together, is at most what its pumps push. Uphill, a run without a
  running pump stands still.
- A pipe holds nothing: what is drawn goes at once along the run to the **nearest** block that takes it,
  then the next, no faster than the narrowest pipe on the way.
- A drawing side takes from a tank, a well (drilling rig), or a **lake**: a source block of oil or lava
  beside it is taken up a bucket at a time, once the pumps have pushed a bucket's worth. Water is left
  alone by pipes, because Hardwrought's own water is measured by the millibucket: a pump set in it draws
  it instead (below).

### Pumps in water

A pump with water against any of its sides draws it up itself, as much as it pushes (sharing that with the
run's drawing sides), and sends it along the run to the nearest block that takes it (`WaterIntake`).

- **Endless source:** where the water it touches is a body of at least **100 blocks** joined together within
  12 blocks of the pump — a lake, a river, the sea — nothing it takes is missed.
- **Smaller ponds** are drawn from by the millibucket, as a bucket draws from them, and run dry.
- Water is only drawn for what the run has room for, so a full tank never drains a pond.
- The water keeps its kind: a pump in the sea sends **seawater** down its pipes (water carrying the
  `water_quality` component). A tank holds one kind, so seawater and fresh water do not go in together;
  an empty hand on a tank names it. Machines that take water take either.

### Pumps

Pipes join a pump on every side; it can sit anywhere in the run.

| Pump | Driven by | Pushes | Recipe |
|---|---|---|---|
| Mechanical pump | a shaft, gear or crank on any side; 6 stress per RPM (a hand crank is enough) | 250 mB/s at 16 RPM, in proportion to speed | iron plates, two shafts, a piston |
| Electric pump | current, through a copper coil set against it like the other machines; 40 Ω | 1000 mB/s at 60 V, less as the voltage drops (`MachineBlockEntity.rate`) | mechanical pump, dynamo, copper wire, iron |

### What holds liquids

| Block | Holds | Pipes |
|---|---|---|
| **Tank** (iron plates and glass) | 16 buckets of one liquid, shown behind the glass; buckets fill and empty it; an empty hand reads it; comparators read it | in and out, any side |
| **Drilling rig** | its oil tank | can only be drawn from |
| **Salt pan** (iron plates: `P P` over `PPP`) | four buckets of seawater, and nothing else; dries to 2 salt a bucket — in the sun (open sky, daylight, no rain) in 2 minutes, over the same fires as a still in 30 s, both together faster; up to 64 salt, taken out by hand or by a hopper under it | filled from any side, seawater only |
| **Still** | one bucket of crude oil piped in, distilled like a bucket in its charge slot when that slot is empty (no bucket comes back) | can only be filled, with crude oil |

A liquid a tank shows needs a look in `FluidDisplayBlock` (water, lava, crude oil so far).

## Tests

`PipeGameTests`: a wrench cycling a side towards a chest and between two pipes; an item pipe drawing a
chest into another; a fluid run lifting nothing into a higher tank with its pump standing and its pump's
pace once turned, with nothing lost; a raised tank emptying downhill without a pump at the pace of its
drop; two tanks on one level evening out through a pipe without the second rising above the first; a
pump in a basin of a hundred blocks filling a tank at its pace without the basin losing anything, and in
a pond of nine drawing the pond down by what the tank gained; a run drawing a well dry and a lake a bucket into a tank; a still taking exactly one
bucket of crude oil from a pipe. `MachineryGameTests` keeps the item pipes' pace.
