# Carried Power

Stage E4 of the [electricity roadmap](electricity-roadmap.md): the haven on the player's body. Devices run
on cells, cells are charged at a charging station, and every device is laid out part by part on the
**assembly bench**.

## Cells

| Cell | Holds | Takes a charge at | Weighs | Wear |
|---|---|---|---|---|
| **Lead-acid pack** | 10 Wh | 60 W | 2.5 kg | none |
| **Nickel–iron cell** | 20 Wh | 20 W | 2.0 kg | none |
| **Silver–zinc cell** | 40 Wh | 150 W | 0.6 kg | worn out after 300 full charges |

A cell is put into a device by clicking it onto the device in an inventory; the one that was in it
comes out onto the cursor. A right click on a device with nothing held takes its cell out. The device's
bar is its cell's charge, and its tooltip says how many watt-hours are left. With no cell, or a flat one,
a device does nothing. Carry weight counts the cell and the device separately, by their own weights.

The **charging station** (60 V, up to 240 W) has five places for cells, devices with their cells in them,
or chestplates with a lining. It shares its power out, each cell taking no more than its own rate, and
gets four fifths of it into them. With nothing to charge it takes nothing.

## Devices

| Device | Worn / held | Draws | Does |
|---|---|---|---|
| **Headlamp** | head | 5 W | A carried light of 13 on the brow that does not burn out. Like any light, it shows its wearer to what is watching. |
| **Night vision goggles** | head | 8 W | Sees in true darkness at once; bright light (12 and more) blinds through them. |
| **Exoskeleton** | legs | 15 W standing, 35 W walking | Carries three times as much before the weight tells, a wound slows its wearer half as much, blocks break 15 % faster. Flat, it is a heavy frame and its wearer carries less. |
| **Electric drill** | held | 120 Ws a block | Digs stone and earth: a steel bit about twice as fast as a steel pickaxe, a tungsten-steel bit faster and through what only tungsten steel cuts. The motor does the labour of the stroke. |
| **Chainsaw** | held | 150 Ws a log | Cuts wood fast; a log cut at the foot of a tree brings down every log joined above it (up to 160). Sneaking, one log. |
| **Ore scanner** | held | 600 Ws a pulse | Every ore within 12 blocks glows for its holder, and the pulse says what it found. |
| **Heated / cooled lining** | in a chestplate | 3 W a degree | Pulls what its wearer feels toward comfort, by up to 15 °C: the heated one in the cold, the cooled one in the heat. |

Worn devices are put on as armour is and switched on or off by sneaking and using them. A lining is
clicked onto a chestplate in an inventory (a lining already in it comes out), and charged with the
chestplate in the charging station.

The parts that wear in a device have their own durability and are clicked onto it like a cell: a
**steel drill bit** lasts 600 blocks, a **tungsten-steel drill bit** 2400, a **saw chain** 800 logs. Worn
through, the part is gone and the device does nothing until a new one is fitted.

## The assembly bench

A new rung on the bench ladder (`BenchTier.ASSEMBLY`), above the nailed bench: a steel table with a vice
and a drawing board, built on a nailed bench. It makes everything lower benches make, and the cells and
devices only it makes (the item tag `hardwrought:assembly_products`). Like the nailed bench it keeps what
lies on it. The compendium shows it as the station for those recipes.

## Parts

| Part | Made |
|---|---|
| Battery acid | chemical reactor: 2 sulfur, a glass bottle |
| Potash | charcoal, smelted |
| Cell case (2) | 5 plastic sheet |
| Toggle switch (2) | lever, copper plate, porcelain |
| Device housing | 4 aluminum plates, a plastic sheet |
| Reduction gear | 2 fine gears, a steel plate |
| Chuck | 3 steel rods, a fine gear |
| Saw bar | 3 steel plates |
| Strap (2) | 2 leather, an iron nugget |
| Nichrome mesh | 8 nichrome wire |
| Thermoelectric pad | 2 copper plates, 2 silicon wafers, 2 nickel plates |
| Regulator | 2 resistors, a transistor, a basic circuit |
| Image tube | glass tube, silver plate, capacitor |
| Pulse coil | 2 electrical steel plates, a winding, a capacitor |
| Exoskeleton frame | 6 titanium plates, an aluminum rod |
| Joint actuator | an electric motor, a reduction gear, 2 bearings |
| Drill bits | a plate and 2 rods of steel or tungsten steel |
| Saw chain | 4 steel rods, 4 steel plates |

At the assembly bench: lead-acid pack (4 lead plates, battery acid, cell case), nickel–iron cell
(2 nickel plates, 2 iron plates, potash, cell case), silver–zinc cell (2 silver plates, 2 zinc plates,
paper as separator, cell case); headlamp, goggles, drill, chainsaw, scanner, linings and the exoskeleton
from the parts above, a motor, reflectors, bulbs and circuits.

## Tests

`CarriedGameTests`: a charging station fills a nickel–iron cell no faster than its 20 W and a silver–zinc
cell in a drill much faster, wearing it; a drill without a cell, flat or without a bit does not dig, and
charged with a bit digs fast, a block costing its charge and a little bit; a chainsaw brings a whole tree
down at a log's cost each; the ore scanner finds the ore around it and not the stone; the exoskeleton
carries, softens a wound and draws charge, and flat makes things worse; a headlamp is lit while on and
charged; goggles see in the dark; a heated lining makes up fifteen degrees of cold from the cell in it
and a cooled one cools; devices need the assembly bench, their parts do not, and the bench is that rung.

The assets are written by `tools/CarriedAssets.java`.
