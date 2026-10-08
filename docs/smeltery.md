# The smeltery

A multiblock furnace in the style of Tinkers' Construct. It melts metal into a tank, alloys what it
holds, and a faucet pours the metal into casts. Steel and its alloys are made **only** here. Because it
yields ingots more easily than forge and anvil do, it comes later: it is built from refractory brick
and it melts a metal only at that metal's **full melting point**.

## Building it

- **Smeltery bricks**: 2×2 refractory brick. **Seared glass**: made from bricks and glass.
- **Floor**: smeltery bricks under the whole tank.
- **Walls**: a ring around the tank, corners optional. The ring is made of bricks, glass, drains and one
  controller, and it stands on every layer the tank has.
- **Tank**: the air inside the walls, up to 7×7 wide and 8 high. The tank holds 8 ingots (1152 mB) per
  block.
- **Controller**: set into the wall facing out, it finds the tank behind it. It glows while lit.
- **Drain**: in the wall. A **faucet** on its outside pours into a **casting table** below it.
- **Bellows**: one on any wall block of the structure blows the fire.

The structure is checked again every second, so a broken wall shuts it off.

## Heat

| Fuel | Without bellows | With bellows |
|---|---|---|
| coal / charcoal | 1250 °C | 1650 °C |
| coke (coal smelted in a furnace) | 2000 °C | 4200 °C |
| lava from a smeltery tank | 1600 °C | 2000 °C |

A **smeltery tank** (refractory brick around glass) is a wall block that holds four buckets of lava. You
fill it and empty it with a bucket, and you can see through its window how full it is. Tanks set
against each other join into one vessel. The frame between them goes away, and the lava fills them
from the bottom up. The smeltery burns lava only when its fuel slot is empty. It takes 50 mB at a
time, so one bucket lasts five minutes.

The temperature moves slowly towards the fuel's heat. Burn times: coal 1200 ticks, coke 2400 ticks, a
block of coal 10800 ticks. With coal alone copper, bronze and gold melt; iron needs bellows or coke;
tungsten needs coke *and* bellows.

## Melting and alloying

Anything metal melts: ingots (144 mB), nuggets (16 mB), rods, ores (288 mB), plates and tool parts. Coal
powder dissolves as **carbon** (72 mB) from 1000 °C. An item melts after five seconds once the tank is
at its melting point.

Whatever sits in the tank alloys, 4 mB per tick, as long as it is hot enough:

| Inputs | Result | Heat needed |
|---|---|---|
| 4 iron + 1 carbon | 4 steel | 1540 °C |
| 3 steel + 1 chromium + 1 nickel | 5 stainless steel | 1550 °C |
| 3 steel + 1 tungsten | 4 tungsten steel | 3422 °C |
| 3 copper + 1 tin | 4 bronze | 1085 °C |

## Casting

An **ingot cast** is shaped from fireclay and fired in a furnace. Placed on a casting table, it catches
the metal. Opening the faucet pours the **lowest** metal in the tank, 8 mB per tick, until 144 mB are in
the cast. Two seconds later the bar has set. It comes out hot (70 % of its melting point) and ready for
the anvil.

## Where it lives

- `smeltery/MoltenMetals`: amounts, what melts into what, melting points and alloys.
- `smeltery/SmelteryStructure`: the scan and the drain → controller registry.
- `smeltery/SmelteryControllerBlockEntity`: fuel, heat, melting, alloying and the tank.
- `smeltery/Faucet*`, `smeltery/CastingTable*`: pouring and casting.
- `client/smeltery/`: the screen, the molten layers seen through glass, the stream and the cast.
- `tools/smeltery_assets.py`: textures, models, recipes, loot tables and names.

## Verification

- `SmelteryGameTests`:
  - coal alone never melts iron, and tungsten needs coke and bellows
  - the structure is found from its controller, and it is not found without its floor
  - iron and coal powder alloy to steel, and the faucet casts a steel bar
- `CoreClientGameTest.verifySmeltery`: screenshots of a working smeltery and of its screen.
