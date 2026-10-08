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
block of coal 10800 ticks, a block of coke 21600 ticks. Nine coke make a **block of coke** and a block
gives its nine back; the forge burns coke and its block too. With coal alone copper, bronze and gold melt; iron needs bellows or coke;
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

A cast is a plate of fired clay with a hole in the outline of what it makes. There is one for a bar
and one for every tool, weapon and armor part.

- **Making a cast**: four fireclay make a **cast blank**. Lay the blank on a casting table and press a
  bar or a part into it (right-click with it; it is not used up). The blank takes that shape. Take it
  off and fire it in a furnace. The first part of a kind therefore has to be forged; the cast copies it.
  The ingot cast can still be crafted directly as before.
- **Pouring**: with a fired cast on the table, opening the faucet pours the **lowest** metal in the
  tank, 8 mB per tick, until the cast is full: 144 mB for a bar, and as many bars as the part takes on
  the anvil (a pickaxe head 3, a cuirass 8). The metal stands in the cast in the cast's own outline.
  Two seconds later it has set and comes out hot (70 % of its melting point).
- **Choosing the metal**: in the smeltery's screen, click a layer in the tank. It moves to the bottom
  and is poured next. The screen frames that layer and names it under the tank. Layers are drawn at
  least 11 pixels high, so a single bar in a large tank can still be read and clicked.
- A metal that has no such part (a pickaxe head of tin) does not run; the faucet says why.
- **Unattended**: a hopper under the casting table draws out what has set and leaves the cast. A faucet
  with a redstone signal on it pours again as soon as the cast is empty. The two together cast bars
  into a chest until the tank runs out of that metal.

### Cast parts are rough, and the grindstone

A cast part comes out with a craftsmanship of 20 %. A **grindstone** finishes it, and any forged part
too: right-click a grindstone with the part in the main hand.

- The part is held against the stone: holding the mouse button or the space bar presses it on and a
  marker climbs to the right, letting go eases it off and the marker falls back. A green band wanders
  along the stone, with a gold middle. A pass lasts three seconds and begins with the first press.
- What a pass is worth is how much of it the marker spent in the band (the gold middle counts in full,
  the rest of the band for 72 %): 85 % and more is dead on (+5), 62 % clean (+3), 35 % just off (+1),
  less than that a scratch (−2). The running figure is shown while the pass lasts.
- A part takes **8 passes** and no more. Each pass the band is a little narrower and wanders further.
- The polish is added on top of what the anvil or the cast gave, up to +40. A cast part ends at 60 %
  at best. A perfectly forged part, ground dead on, stands at 140 %.

What the percentage does, on the part and on the tool, weapon or armor it becomes:

| Craftsmanship | Digging speed | Durability | Damage | Armor protection |
|---|---|---|---|---|
| 20 % (fresh from the cast) | 86 % | 77 % | 90 % | 90 % |
| 60 % (cast, ground dead on) | 98 % | 101 % | 100 % | 100 % |
| 100 % (forged perfectly) | 110 % | 125 % | 110 % | 110 % |
| 140 % (and ground dead on) | 122 % | 149 % | 120 % | 120 % |

Quenching and tempering multiply these further (see `smithing/ForgeQuality`). Armor protection scales
the piece's resistance to cuts, stabs and blows; the tooltip of an armor piece shows protection and
durability, that of a tool or weapon speed, durability and damage.

## Where it lives

- `smeltery/MoltenMetals`: amounts, what melts into what, melting points and alloys.
- `smeltery/SmelteryStructure`: the scan and the drain → controller registry.
- `smeltery/SmelteryControllerBlockEntity`: fuel, heat, melting, alloying and the tank.
- `smeltery/Faucet*`, `smeltery/CastingTable*`: pouring and casting.
- `smeltery/Casts`: the casts, what each makes of which metal, and pressing a shape into a blank.
- `smithing/Grinding`: working a part over at a grindstone; `client/smithing/GrindingScreen` is the stone and its spark.
- `client/smeltery/`: the screen, the molten layers seen through glass, the stream and the cast.
- `tools/smeltery_assets.py`: textures, models, recipes, loot tables and names.
- `tools/cast_assets.py`: the casts and the metal standing in them, cut from the parts' own textures.

## Verification

- `SmelteryGameTests`:
  - coal alone never melts iron, and tungsten needs coke and bellows
  - the structure is found from its controller, and it is not found without its floor
  - iron and coal powder alloy to steel, and the faucet casts a steel bar
  - a part pressed into a blank leaves a cast; the part poured in it is rough; a grindstone takes eight passes, and dead on it carries a perfect part past 100 %
- `CoreClientGameTest.verifySmeltery`: screenshots of a working smeltery and of its screen.
- `SmelteryClientGameTest`: screenshots of casts on their tables, empty, half full and set, and of the screen with single bars in a large tank.
