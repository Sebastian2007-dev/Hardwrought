# Refining and gas lines

The still is the early way to part crude oil. The refinery column parts it at scale, and the cracker
and reformer work its cuts further. The gas it gives off, and the natural gas of a drilling rig, go
through gas lines by pressure alone, without a pump.

## The products

Each product is a real liquid: still and flowing fluid, a block in the world, a bucket, and a furnace
fuel. They are registered in `refining/RefineryFluids` from the enum `RefineryProduct`, and they all
burn the way crude oil does.

| Product | Where from | Bucket burns (ticks) |
| --- | --- | --- |
| Bitumen | the column's foot | 6000 |
| Heavy oil | tray 1 | 24000 |
| Diesel | tray 2, cracker | 20000 |
| Kerosene | tray 3 | 16000 |
| Naphtha | tray 4 | 12000 |
| Gasoline | cracker, reformer | 18000 |
| Light spirit | the still | 8000 |
| Fuel | the still | 14000 |
| LPG (gas) | tray 5, cracker, reformer | canister 16000 |

The fluid tank shows each product in its own colour.

The still's three bottles (light spirit, fuel, heavy oil) are not just items. Each one holds a third of
a bucket of its liquid, like a water bottle does. A bottle pours into a tank, and a tank fills an empty
glass bottle. Pipes can do the same through the transfer API.

## The refinery column

A **refinery base** stands over a fire: fire, lava, magma, a lit furnace or campfire, or a lit forge,
the same as for the still. On top of it stand one to five **refinery trays**. Crude oil is pumped into
the base, which holds 8 buckets of it. Every second the base boils 100 mB of crude oil:

| Where | Catches (mB per 100 mB) |
| --- | --- |
| Base | bitumen 15 |
| Tray 1 | heavy oil 25 |
| Tray 2 | diesel 25 |
| Tray 3 | kerosene 10 |
| Tray 4 | naphtha 20 |
| Tray 5 | LPG, 2 gas units |

A tray catches the cut that matches its height, so the same block becomes a diesel tray or a naphtha
tray depending on where it stands. A column with fewer than five trays catches only the cuts it has
trays for. What would condense higher **is flared off at the top**, with flame, smoke and a roar. A
column waits when the outlet of any cut it catches is full (4 buckets each). Fluid pipes in extract
mode draw the cuts off the trays and the bitumen off the base. A gas line takes the LPG off the fifth
tray.

## Windows

Right-clicking with an empty hand opens a window (`RefineryMenu`, `RefineryScreen`) on every block of
the refinery and on the gas tank. Each window shows:

- whether the block is over a fire and working;
- a progress bar for the current batch;
- a gauge for each tank, in the colour of what it holds, labelled with the name and amount, with a
  tooltip giving the exact figures.

Each block adds its own line:

- The base names its tray count and which cuts are flared off.
- A tray names its height and the cut it catches.
- The gas tank names its pressure.

The figures go to the client as the menu's data, so the window updates while the block works.

## Compendium

`RefiningRecipes` describes every process to the compendium as in-world recipes, with the machine as
the station:

- the rig: crude oil and natural gas;
- the still: its four products;
- the column: every cut, with the number of trays it needs, and LPG;
- the cracker and the reformer;
- filling canisters at the gas tank;
- bottles filled from a tank.

Liquids appear as the bucket or bottle they are carried in. Each machine gets its own bookmark at
the top of the page, with the machine on it, instead of the shared log. Work done by hand stays under
the log.

## Cracker and reformer

Both stand over a fire, take their input in by pipe (4 buckets) and give their products to pipes and
their gas to a gas line.

| Machine | In | Out | Gas | Time |
| --- | --- | --- | --- | --- |
| Cracker | heavy oil 100 mB | diesel 45 + gasoline 35 | 2 LPG | 2 s |
| Reformer | naphtha 100 mB | gasoline 85 | 1 LPG | 1.5 s |

Lamp oil from kerosene comes later.

## Burning stack

The burning stack disposes of what nobody wants. It stands on a liquid tank or a gas tank. Light it
with flint and steel or a fire charge, and each second it burns up to 250 mB of liquid or 8 units of
gas, from three sources in this order:

1. gas that a **gas line** brings it, natural gas or LPG. It takes gas in at 2 bar, so a line's gas
   runs toward it;
2. **methane in the air** touching it (its sides and top). The flame draws the methane in and burns it
   quietly into carbon dioxide, a few units at a time. This is the safe way to get rid of firedamp: the
   stack does not count as a flame that sets a pocket off. A torch beside it still does;
3. whatever burns in the **tank under it**: crude oil, any refinery product, natural gas, LPG. It leaves
   water and lava alone.

Burning gives off a lot of carbon dioxide, 3 units a second (180 a minute), released over its top.
It is heavy, so it sinks and pools around the stack. With nothing left to burn, the stack goes out
and has to be lit again. Its window shows whether it is lit, what it is burning, the tank under it and
its gas line intake. Recipe: flint and steel over iron plates, two iron fluid pipes and red dye.

## Gas lines

Gas lines look like the other pipes, with a yellow trim, and come in the same five metals. They need
no pump. Every store on a line is a gas tank, a rig, a column's top or a cracker. Each store stands at
a pressure: how full it is times the pressure it is built for. Each tick, gas goes from the highest
pressure to the lowest store that takes that gas, until the pressures meet. A line carries only one
kind of gas at a time, natural gas or LPG.

| Line | Holds (bar) | Flow |
| --- | --- | --- |
| Bronze | 6 | 0.5 |
| Iron | 12 | 1 |
| Nickel | 20 | 2 |
| Chromium | 32 | 4 |
| Titanium | 50 | 8 |

If a store on the line stands above what its weakest pipe holds, the line bursts there, on about one
second in four. The pipe is gone and 16 units of the gas come out as **methane** into the world. A full
gas tank stands at 20 bar and a rig at 10, so bronze is only for low pressure.

The **gas tank** holds 256 units at 20 bar. An empty gas canister held to it draws 16 units into a
canister of the gas it holds, natural gas or LPG, and a full canister empties back into it.

The copper flue gas pipe of the forge is a separate system and unchanged.

## Methane

Methane in the world now looks like carbon dioxide, a haze, but yellow.

**Firedamp.** A flame sets off methane from 3 units per block (6 %) upward. That includes gas too rich
to explode on its own, because air thins it into the window at its edge. A torch, copper torch or
lantern in either hand also sets it off, and so does a player on fire, when the player's head or feet
are in methane. The safety lamp does not. Placed lanterns and copper torches count as flames as well.

**Through what one walks through.** Gas cannot share a block with another block, but it passes through
blocks with no collision: torches, grass, flowers, pressure plates, open doors. It moves on to the
first free place behind them, and a player standing in such a block breathes the fullest gas next to
it.

## Recipes

| Result | Recipe |
| --- | --- |
| Gas line (8) | metal plate, copper ingot, metal plate |
| Gas tank | 8 iron plates around a gas canister |
| Refinery base | iron plates, bricks, a still in the middle, bricks below |
| Refinery tray (2) | iron plates and 2 iron fluid pipes |
| Cracker | iron plates, copper ingot, bucket |
| Reformer | iron plates, gold ingot, bucket |

## Tests

`RefineryGameTests`:

- two tanks even out over a gas line;
- an over-pressured bronze line bursts;
- a two-tray column catches heavy oil and diesel and leaves bitumen;
- a five-tray column sends LPG out of the top;
- a column without a fire stays cold;
- the cracker and the reformer;
- a torch in hand sets off firedamp;
- carbon dioxide passes a pressure plate into a pit;
- the still's bottles are liquids a tank takes and gives, and every product has a gauge look and a
  bucket;
- the compendium lists the oil processes;
- a burning stack burns diesel into carbon dioxide, leaves water alone and goes out, burns firedamp
  without an explosion, and burns LPG that a gas line brings it.

`MachineryClientGameTest` photographs the compendium's gasoline page and the windows of the base, a
tray, the cracker and the gas tank.
