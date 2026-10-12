# The measured bucket, and a levelling attempt that was taken out again

Two pieces of work on the water system. One shipped; one did not, and is written down because the
next person to try it should start from where it failed rather than from the beginning.

## The measured bucket

A bucket holds `hardwrought:water_amount` millibuckets, 0 to 1000, rather than being full or empty.
Vanilla's two items cannot share a world with finite water: dipping into a puddle holding a third of
a block either invents two thirds or refuses the puddle, and pouring into a cell with room for half
either loses half a bucket or refuses to pour. Both were real behaviours here.

- **Click water** — takes what is there, up to a bucketful, and tops up a bucket that already has
  something in it. It never refuses for want of water.
- **Sneak and click** — pours. It pours what fits and keeps the rest, so a nearly full cell can be
  topped up. Sneaking is the whole of the difference: without it there is no way to pour *into*
  water, because clicking water would always be a dip.
- An unmarked water bucket reads as **full**. Creative, other mods and loot tables hand those out;
  full is the reading that never invents water and never destroys it.

### Showing it

Three things, because no one of them is enough:

| | Shows | Why not on its own |
|---|---|---|
| Item model | 3 steps | The bucket is opaque below its rim; three rows inside the mouth is all the sprite can show |
| Bar under the item | 13 pixels, blue | Blue because this is not wear — a bucket at a tenth is nearly empty, not nearly broken |
| Tooltip | the exact figure | Has to be asked for |

The model dispatches on `minecraft:custom_model_data`, which is the one property an item model may
branch on that a mod can set. The partial sprites contain only water; the bucket body is vanilla's
own sprite, referenced as a second layer rather than copied.

### Sealing the vanilla ways

A part-full bucket is still `minecraft:water_bucket`, so anything that knows the vanilla item would
treat fifty millibuckets as a block. That is a way of minting water, which is what section 23.1
exists to stop — leaving it open would make the rest of the water system decorative.

- **Dispenser** — taught rather than blocked (`BucketMachinery`). It pours what the bucket has and
  keeps the rest; an empty one dips for what it can reach. A machine that behaves differently from
  the hand is a bug waiting to be found.
- **Cauldron** — only a full bucket may fill one (`CauldronBucketMixin`). Emptying a cauldron is
  untouched: a full cauldron is exactly a block and a block is exactly a bucketful.
- **Recipes** — a part-full bucket is not a valid ingredient (`IngredientBucketMixin`). A hot path,
  so it answers for everything that is not a part-full water bucket in one comparison.

`WaterGameTests.aBucketHoldsWhateverItCouldGet` pins the two sums: water out of the world equals
water in the bucket, and back again.

## Why the water did not go very far

Measured in a played world with `/hardwrought water`, not reasoned about:

```
Simulation: laeuft | wartende Zellen 32768 | Zellfehler 0
Letzter Tick: 560 Zellen in 1.531 ms (Budget 1.500 ms)
Servermittel 6.214 ms
Weckrufe: 2024407 | davon mit Weltzugriff 362116 (17.9 %)
```

**The queue was saturated.** 32768 is exactly `MAX_ACTIVE`, and at that point `activate` starts
dropping cells on the floor. One transfer wakes eleven cells; the solver was getting through about
550 a tick before the 1.5 ms guard stopped it. It produced work several times faster than it
consumed it, so the queue stayed full and whatever fell off the end simply stopped moving. That is
the whole of "the water does not go very far" — not the spreading rate, not the algorithm.

**And it was starving next to an idle server.** The same reading has the server tick at 6.2 ms of
its 50. The simulation was being held to 1.5 ms beside eight times its own budget in spare capacity.

So the budget went to **5 ms and 4096 cells**. The congestion backoff is deliberately untouched: at
a 35 ms server tick water drops to 0.5 ms and at 45 ms to 0.1 ms, so a heavier world still cannot
be dragged under by it. Raising a budget is only safe because that valve exists.

### Two smaller findings from the same reading

**A cell can hold one thing and be drawn as another.** One cell held 115 mB while its block state
said level 0 — a full block. Everything in `WaterStorage` writes the amount and the display
together, and no write path that could produce this was found. Rather than leave played worlds
carrying it, `healDisplay` now checks any cell the solver picks up and corrects it: one state read
on a cell already in hand, and it repairs rather than merely reporting. `/hardwrought water` reports
it too, across the neighbourhood, so a recurrence is visible rather than inferred.

**The spread threshold gated more than it was meant to.** `SPREAD_THRESHOLD` exists so that a film
does not creep across a floor forever and get drawn as an eighth of a block that is not there. It
was gating the whole of the levelling rule, so a cell under an eighth would not level with anything
— including a neighbour holding four times as much. It now applies only to wetting a *dry*
neighbour; two cells that both already hold water settle with each other, with
`LEVELLING_THRESHOLD` stopping the jitter. This is reasoning rather than a measured fix: the pit
test levels either way, so it is not proven to be what anyone saw.

## The levelling solver, and why it was taken out

### The problem it was for

`WaterFlow` moves water a cell at a time and caps sideways transfer at `MAX_SPREAD_PER_PASS = 60`
mB a pass. A block is 1000 mB, so a block of water takes about seventeen passes — the better part of
a second — to move one step sideways. Water goes nowhere, slowly, and a pond settles the way treacle
settles.

That cap is **not** a performance guard. The same cells are visited either way; the cap only decides
how much each hands on, and the cost is in `setBlock`, which only fires on an eighth boundary.
Raising it is very nearly free. That remains the cheapest available improvement and was never tried.

### What was built

`WaterBasin`: find the connected sheet of water, work out where a flat surface belongs, and move a
third of the way there each pass. It worked for what it covered and is gone from the tree. The
reason is in the next section; the reason it is worth writing down is in the one after.

### Why it was taken out

**It only solved one height.** Water standing on water is a pressure problem, and the solver had no
model of pressure — it filled the cells it collected from the bottom up, and collected upwards only
through water already there, so it could not see the empty shaft next door that the water ought to
rise into. Left unrestricted it flattened two connected shafts into their bottom row.

Restricting it to a single layer made the existing tests pass. It also meant that **a pit more than
one block deep — which is the thing a player actually digs — was handed straight back to the cell
rules.** So it did not improve the case it was built for, while costing a bounded flood fill on
every cell that declined.

### What the next attempt needs to do differently

A levelling solver has to be **multi-layer from the start**, and that means collecting the air
column above the water as part of the basin, bounded by the level the volume could possibly reach
(total volume divided by footprint). That is the piece that makes a deep pit and a U-tube the same
problem with the same answer. Building the single-layer version first does not get you part of the
way there; it gets you something that has to be thrown away, which is what happened.

### Three bugs it produced, all worth avoiding again

- **A conservation check that ran after the mutation.** `pour` filled cells as it went and checked
  the total at the end, so when the sum did not come out it had already written half a basin and the
  difference was gone — about a bucketful per occurrence, in a real world. Build the whole plan,
  prove the sum, *then* write.
- **Writing cells without waking them.** The cell rules wake both ends of every transfer, so water
  keeps being looked at until it settles. Anything that writes many cells at once must disturb them
  itself, or water spread to the lip of a drop sits there and never falls off it.
- **Equilibrium applied to water that could still fall.** The answer is then a thin sheet over every
  cell the water will eventually reach — and spread that thin it falls under the threshold the cell
  rules will touch, and stops, stranded half way down a hillside.

### Where to look

- `WaterAmounts.MAX_SPREAD_PER_PASS` — the spreading rate. The untried cheap lever.
- `docs/water-performance.md` — the measurements behind the current budget.
- `WaterFlowGameTests` — in particular `twoConnectedShaftsComeToRestAtTheSameHeight` and
  `waterPutOnALedgeRunsOffIt`, which between them caught every one of the bugs above.
