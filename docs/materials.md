# Materials, circuits and big machines

This is the second step of the oil-era tier, after [power.md](power.md). It covers the materials of the
oil age, the chemistry machines that make them, circuits, faster marks of the electric machines, and
the first two multiblocks.

## Materials (`materials/Materials`)

| Material | Made in | From |
| --- | --- | --- |
| Caustic soda | electrolyser | salt and 250 mB of water; also gives 4 units of chlorine |
| Salt | salt pan | seawater dried in the sun or over a fire: 2 salt a bucket (`SaltPan`); seawater by the bucket, or by pipe from a pump set in the sea |
| Plastic pellets (4) | chemical reactor | 250 mB of naphtha and 2 units of LPG |
| Plastic sheet | plate press | plastic pellets |
| Rubber (2) | chemical reactor | 2 resin and sulfur |
| Raw silicon | arc furnace | 2 quartz and coke |
| Silicon wafer (4) | sawmill | raw silicon |
| Silicone (2) | chemical reactor | raw silicon, 100 mB of naphtha and 4 units of chlorine |

**Chlorine** is a new gas (`GasKind.CHLORINE`). It travels in gas lines but does not burn, has no canister,
and a burst chlorine line releases nothing into the world. Burners and burning stacks do not take it in.

## Chemistry machines (`ChemMachine`, `ChemMachineBlockEntity`)

All three run on current through a copper coil set against them, the same way as the crusher:

- they draw current only while they have work they can do;
- they work at full speed at 60 V, slower as the square of the voltage drops, and not at all below 30 V.

| Machine | Resistance | Takes liquid | Takes gas |
| --- | --- | --- | --- |
| Electrolyser | 20 Ω | water | – (gives chlorine) |
| Chemical reactor | 30 Ω | naphtha | LPG, chlorine |
| Arc furnace | 10 Ω (about 6 A: needs cable) | – | – |

How things get in and out:

- **Items:** hoppers and item pipes fill the two inputs from the top and sides and draw the two outputs
  from the bottom.
- **Liquid:** by pipe or bucket.
- **Gas:** comes in through gas lines at the sides and goes out to a gas line on top.

The window shows the gauges, the slots, the progress arrow and the voltage. The recipe logic is in
`ChemWork`, which the large arc furnace shares.

## Parts and circuits (`parts/Parts`)

| Part | Recipe |
| --- | --- |
| Rubber gasket (4) / silicone gasket (4) | 4 rubber / 4 silicone in a ring |
| Circuit board (2) | 2 plastic sheets, copper plate, caustic soda (to etch it) |
| Resistor (4) | copper wire, coke, plastic pellets |
| Capacitor (4) | copper wire, aluminium plates, plastic sheet |
| Transistor (4) | copper wire, gold nugget, silicon wafer, plastic pellets |
| Microchip (2) | gold nuggets, redstone, plastic sheets, silicon wafer |
| Quartz oscillator (2) | gold nugget, quartz, plastic pellets |
| Basic circuit | circuit board, 2 resistors, capacitor, redstone |
| Advanced circuit | basic circuit, microchip, 2 transistors, 2 capacitors |
| Elite circuit | advanced circuit, 2 microchips, quartz oscillator, platinum rods |

The elite circuit goes into the Mk IV machines of the 240 V grid ([electricity.md](electricity.md)).

## Mk II and Mk III

Each of the crusher, electric furnace, sawmill and plate press has two further marks, built on the
mark before:

| Mark | Speed | Current | Built from |
| --- | --- | --- | --- |
| Mk II | 2× | about 1.4× | previous mark, basic circuit, 2 rubber gaskets, electric motor |
| Mk III | 4× | about 2× | Mk II, advanced circuit, 2 silicone gaskets, bearing |

The Mk III crusher also gives two powders per ore. Each mark has a coloured band on its casing.

## Multiblocks (`multiblock/`)

Both multiblocks are a 3×3×3 hull of casing with the controller in the middle of one side, its face
outward. The controller checks the hull every second and, with an empty hand, opens the machine's
window or says what is missing and where.

Ports take the place of any casing block in the hull:

- **Fluid port:** a pipe connects to it.
- **Item port:** an item pipe or hopper connects to it. Inputs go in, outputs come out.
- **Power port:** joins the machine to the line through a copper coil set against it.

The first power port in the hull is the one that serves the machine.

**Large diesel generator.** It is built of engine casing round an **engine core** (injectors, glow plugs,
pistons, crankshaft) and needs a fluid port and a power port. Compared with the small generator:

- it gives the same 64 V through 0.1 Ω instead of 0.5 Ω, so five times the current;
- it uses a fifth less fuel per joule;
- it holds 16 buckets.

The exhaust comes out over the hull.

**Large arc furnace.** It is built of refractory casing round a hollow hearth, with a **graphite
electrode** in the middle of the roof, and needs an item port and a power port. It draws about 13 A,
so it needs cable. Its recipes:

- 2 quartz and coke give 2 silicon, three times as fast as the small furnace;
- 2 iron and coke give 2 steel, the electric way to make steel.

## Burning stack: electric igniter

A burning stack is now a consumer of 240 Ω. With current on it through a coil (30 V or more), it lights
itself again within a second whenever there is something to burn. Its window shows the igniter's
voltage.

## Creative tabs

The one tab is split into six:

- Survival & Tools
- Metals & Smithing
- Machines & Power
- Oil & Chemistry
- Parts & Circuits
- Magic

## Compendium

- **Machine bookmarks.** Every in-world recipe worked in a machine gets a bookmark of its own at the
  top, showing the machine (as a shadow until studied). Work done by hand stays under the log.
- **Liquids and gases as themselves.** They are shown the way recipe viewers show them: a liquid as its
  texture, a gas as a cloud of its colour, with the amount per batch in the tooltip.
  - `FluidIcons` carries them as stacks of a hidden item with a data component.
  - A click opens the page of the bucket or canister.
  - A bucket's or bottle's page finds the recipes of its liquid.
- **New entries.** The refinery, materials and multiblock processes are listed, the old ones with
  shorter instructions. Both multiblocks are in the structure view, with one example port each.
- **Shelves.** Oil and chemistry items go on the chemistry shelf; engines, parts and big machines on
  engineering; circuits and electric machines on electricity.
- **Scrolling.** Text that does not fit is cut off above the buttons and scrolls with the wheel. This
  applies to:
  - the left page of the structure view;
  - a page of recipe cards taller than the paper;
  - a journal note longer than both pages.

  A thin bar shows where the page is scrolled to. The wheel scrolls only where there is more than fits.
  Otherwise it does what it did before: it zooms the structure, turns the recipe page or goes to the
  next note. At the end of a long page or note it turns the page. The scrawl of an unread note is kept
  to its page.

## Tests

`MaterialGameTests`:

- the electrolyser makes caustic soda and chlorine;
- the reactor makes plastic and rubber;
- the arc furnace makes silicon;
- nothing works without current;
- the marks are faster, and the Mk III crusher doubles;
- the press makes plastic sheet and the sawmill makes wafers.

`MultiblockGameTests`:

- the large generator forms and runs;
- a gap in the hull is reported;
- the large arc furnace makes silicon on the large generator;
- the compendium has both structures.

`PowerGameTests` adds a test that the burning stack lights itself on current.
