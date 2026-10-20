# Electricity – Roadmap

**All six stages are built**: [electricity.md](electricity.md) (E1, E2), [haven.md](haven.md) (E3),
[carried-power.md](carried-power.md) (E4), [automation.md](automation.md) (E5) and [tesla.md](tesla.md) (E6).

What [electricity.md](electricity.md), [power.md](power.md) and [materials.md](materials.md) built is a
working grid: dynamos and diesel, wire solved exactly, batteries, four machines in three marks, the
chemistry line and the first circuits. What it does not have yet is a reason. Power today makes
powder, plates and silicon — things that make more machines — and one electric motor goes into the
ritual. This is the path from there to power the player feels in every hour of play.

## The idea

**Power is how the player pushes the world back.** Every hardship the mod has — the dark, torches that
burn out, heat and cold, gas in the cellar, wounds that fester, food that rots, the load on the back,
monsters that see and hear — gets an electric answer, one stage at a time. At the end the base is a
place where the world's rules no longer bite, and the player carries some of that place on their body.

**The price is parts.** Electricity is not hard because the numbers are hard. It is hard because every
device is assembled from many small pieces, each made in its own way, from its own material, in its
own machine. A desk lamp is a filament, a glass bulb, a socket, a switch and a cord; a heated suit is
a heating mesh, a cell, a regulator and a lining. The player who wants the comfort has to build the
supply chain for the pieces first. The last stage hands that work to machines — which are themselves
the most part-hungry things in the game.

```text
E1 GRID ──▶ E2 MOTORS ──▶ E3 SAFE HAVEN ──▶ E4 CARRIED POWER ──▶ E5 AUTOMATION ──▶ E6 TESLA TOWER
long lines   power turns    the base pushes   the player carries    parts make         power without
240 V        the driveline  the world back    the base with them    themselves         wire, 960 V
```

Stages E3 and E4 are where the payoff is; E1 and E2 are what they need, and E5 and E6 are the reward
for having built the supply chains.

## Rules for the micro-crafting

These hold for every stage, so that many parts means a lot to work out, not a lot of tedium.

1. **Every part has one way of being made, and the way matters.** A filament is drawn on the wire
   drawer, a bulb blown at the glass kiln, a winding wound on the coil winder. A new part should
   ideally need a station or a step the player does not yet have, not just a new shape in the grid.
2. **Parts are shared.** A motor goes into a fan, a compressor, a drill and an assembler. Building a
   supply line for one part pays off across many devices. No part is made for one recipe only, except
   the final one in a device.
3. **Small parts come in batches.** Resistors by four, screws by eight, filaments by four. The count is
   in the number of *kinds* of part, not the number of crafts.
4. **Depth grows with the stage.** E1 devices are two levels deep (part → device), E3 three, E4 and E5
   four, E6 five. Each stage adds about four to six new parts and re-uses the earlier ones.
5. **The compendium carries the tree.** Every part's page shows what it goes into and what it is made
   of, as the machine bookmarks already do. Planning the chain is part of the game; looking it up is
   not a chore.
6. **Parts can fail, and are mended part by part.** A burnt filament is a new filament, not a new lamp
   (as a lamp is rewound today). This keeps parts in demand after the device is built and is what
   machine wear (specification section 78) hangs on.

### New intermediate materials

These run through several stages and are worth having early:

| Material | Made from | Where | Used for |
|---|---|---|---|
| Enamelled wire | copper wire, resin varnish | coil winder | every winding: transformers, motors, solenoids |
| Electrical steel plate | steel and silicon in the arc furnace, then pressed | plate press | transformer and motor cores |
| Porcelain | kaolin/fireclay, fired | kiln | bushings, high-voltage insulators, fuse bodies |
| Insulated wire | copper wire, rubber | coil winder | indoor wiring, devices |
| Nichrome wire | nickel and chromium alloy, drawn | wire drawer | every heating element |
| Tungsten filament | tungsten, drawn fine | wire drawer | bulbs, the arc lamp's electrodes |
| Glass bulb / tube | glass | glass kiln | bulbs, fuses, vacuum tubes |
| Graphite brush | graphite (from the electrode line) | lathe | motor commutators |
| Screws and housings | steel, aluminium, plastic sheet | lathe, plate press | every finished device |

**New stations** in this roadmap, each a small multi-step workstation in the manner of the forge
and the grinding screen: **coil winder** (turns of wire on a core; more turns, more voltage), **wire
drawer** (rods into wire, then finer), **glass kiln** (blowing bulbs and tubes), **assembly bench**
(the device grid: a device is laid out part by part on a drawing, not shaped in a crafting table).

## E1 – The grid grows up (60 V → 240 V)

**Status: done** — see [electricity.md](electricity.md#the-grid-at-240-volts). Where it differs from
the plan below: the bushing is a block (a coil in porcelain) rather than a part, and is what a line at
high voltage goes into; the transformer has no insulating oil yet; there is no separate relay, since the
knife switch follows redstone; the coil winder is a plain machine, not yet a station with a skill step;
the wire drawer, glass kiln and assembly bench are left for the stages that need them.

**Gives the player:** power from where it is made to where it is used. The water wheel in the valley,
the windmills on the ridge and the diesel shed far from the house feed one home.

- **Transformer.** Two windings on an electrical-steel core: ratio 1:4 steps 60 V up to 240 V for the
  line and back down at the end. Losses on a line go with the square of the current, so the same power
  at four times the voltage loses a sixteenth. Overloaded, it hums, heats and its oil smokes.
  Parts: core (electrical steel plates), primary and secondary winding (enamelled wire), bushings
  (porcelain), tank and insulating oil (from the refinery).
- **Insulated wire.** Laid along walls and floors, through a block, does not bite and does not leak
  in rain, but does not span: it needs a face to lie on, every block. Bare wire stays the cheap way
  outdoors.
- **High-voltage insulator.** 240 V on a plain insulator arcs in rain; masts carrying 240 V need
  porcelain.
- **Fuse and breaker.** A fuse (glass tube, thin wire) burns instead of the wire and is replaced; a
  breaker (solenoid, spring, contacts of silver) trips and is reset by hand. Wires stop burning houses
  down once a player has learnt to protect them.
- **Switch** and **redstone relay**: lamps and machines switched by hand or by signal.
- **240 V machines.** The elite circuit gets its use: Mk IV machines run on 240 V directly.

**Danger grows with it.** 240 V on bare wire kills in a few seconds; a dropped line in water is a
death zone. The meter shows the voltage before the player touches anything.

**Tests:** a long 240 V line loses a sixteenth of what the same 60 V line loses; a transformer steps
up and down and overheats when overloaded; insulated wire neither bites nor leaks; a fuse saves the
wire behind it.

## E2 – Motors that turn the driveline

**Status: done** — see [electricity.md](electricity.md#motors-on-the-driveline). Where it differs from
the plan: the controller is a part fitted to the drive, not a block of its own, and sets the speed in
quarters; it is the controller that limits a stalled drive's current, so the inrush and the stall are
what trip a breaker. The stator is wound on iron with redstone coils, since electrical steel comes much
later than the first motor.

**Gives the player:** every mechanical machine already built runs off the grid, anywhere — the ore
pump in the deep mine, the crusher in the cellar — without a river or a wind over it.

- **Electric drive.** The dynamo in reverse: current in, turns out. Speed follows voltage (2 rpm per
  volt, as the dynamo), strength follows current. A gearbox does the rest.
- **Motor controller.** A basic circuit and a resistor bank: sets the speed, starts the motor gently
  so it does not draw five times its current when stalled. Without one, a motor started under load
  trips its breaker.
- **Universal motor upgrade.** The existing electric motor part splits into its pieces — stator
  (enamelled winding on electrical steel), rotor, commutator, two graphite brushes, bearings,
  housing. The brushes wear and are changed: the first part that is a consumable.

**Tests:** a motor drives a shaft at the speed its voltage gives; stalled without a controller, it
trips the breaker; the crusher runs on a motor.

## E3 – The safe haven

**Status: done** — see [haven.md](haven.md). Where it differs from the plan: food spoilage did not
exist yet and was built with the cold stores; the sickbed is any bed beside a bedside monitor; the
water heater is left out, since the electric purifier already makes bad water clean; refrigerant is
ammonia only, and a cooler does not leak; the wire drawer and glass kiln are plain machines.

**Gives the player:** the base stops being a place where the world's rules still apply. Each device
answers one hardship that already exists in the code.

| Device | Answers | System it works against | Key parts |
|---|---|---|---|
| **Electric light, tier 2** (tungsten bulb, socket, switch) | the dark, torches that burn out | `BurntLight`, true darkness | filament, bulb, socket, insulated wire |
| **Floodlight / arc lamp** | monsters at the perimeter | `Perception`, mob spawning | tungsten electrodes, reflector, ballast (winding) |
| **Motion lamp and alarm bell** | being crept up on | `Breaching`, `InvestigateNoiseGoal` | sensor (circuit + quartz oscillator), relay, bell |
| **Electric heater** | cold rooms, winter | `Thermal`, `RoomScan`, seasons | nichrome element, thermostat (bimetal strip), housing |
| **Ventilation fan** | CO, firedamp, chlorine in rooms | `Gases`, `CellAtmosphere`, `Flues` | motor, fan blades, duct, controller |
| **Air cooler** | heat, Nether-bound preparation | `Thermal`, `RadiantHeat` | compressor (motor, piston, valves), refrigerant, coils |
| **Ice box → refrigerator → freezer** | food that rots | food spoilage | compressor, refrigerant, insulated cabinet, thermostat |
| **Infirmary** (autoclave, heat lamp, sickbed) | wounds, infection, disease | `Wounds`, `Ailments`, `Disease` | autoclave (pressure vessel, heater, gauge), sickbed (frame, monitor circuit) |
| **Water heater / sterilizer** | bad water | water quality, thirst | heating element, tank, gauge |

How the room devices work: a heater, fan or cooler acts on the room `RoomScan` finds around it,
sized by the room's volume — a heater warms one room well, a hall poorly. A badly sealed room (doors
open, fences) loses what the device puts in, so building matters as much as wiring.

The **infirmary** is the most valuable device here. Sterilised dressings from the autoclave close a
wound cleanly; lying in the sickbed under a heat lamp heals wounds and shortens illness several times
faster than lying in bed. Under Ultra (no natural healing) it becomes the reliable way back to health
besides a good night.

**Refrigerant** is a chemistry task: ammonia first (from the reactor — poisonous if a cooler leaks),
later a chlorine-based refrigerant that is safe but needs the chlorine line.

**Tests:** a heated room reaches its set temperature and a room with an open door does not; a fan
clears CO from a room; a refrigerator slows spoilage and a freezer stops it; the sickbed heals a wound
faster than a bed; a floodlit area keeps monsters from spawning and makes them keep away.

## E4 – Carried power

**Status: done** — see [carried-power.md](carried-power.md). Where it differs from the plan: the assembly
bench is a rung of the bench ladder with shaped recipes rather than a layout of its own; a lining goes
into a chestplate; the headlamp and goggles are worn in the head slot; the ore scanner marks ores with
particles for its holder rather than on the atlas.

**Gives the player:** the haven on their body. Light, warmth, speed at work, and in the end an
exoskeleton that takes the weight off.

### Cells

The heart of E4, and its weight is the trade-off, because carry weight already counts:

| Cell | Made of | Holds | Weight | Notes |
|---|---|---|---|---|
| Lead-acid pack | lead plates, acid, case | low | very heavy | first, cheap, spills acid if broken |
| Nickel–iron cell | nickel, iron, potash electrolyte | medium | heavy | nearly indestructible, slow to charge |
| Silver–zinc cell | silver, zinc, cellulose separator | high | light | expensive, wears out in a few hundred charges |

A **charging station** fills cells from the grid. A worn piece takes one cell in a slot; the cell's
charge shows on its tooltip and in the HUD.

### Devices

Each is assembled at the assembly bench from a housing, a cell slot, a switch and the parts that do
the work:

- **Headlamp** – carried light that does not burn out and that everyone sees (the carried-light system
  exists). Parts: bulb, reflector, strap.
- **Electric drill** – mines faster than any hand tool of its tier, and drains its cell doing so. The
  bit wears and is changed (tungsten steel bits last). Parts: motor, gearbox, chuck, bit, trigger switch.
- **Chainsaw** – fells a tree in one go. Parts: motor, chain, bar, guard.
- **Heated or cooled suit lining** – keeps body temperature in a band, at the cell's expense. Goes into
  existing armour as an insert; the Nether cooling suit gets its electric mark here. Parts: nichrome
  mesh or a thermoelectric pad, regulator circuit, lining.
- **Night vision goggles** – a vacuum-tube image intensifier: sees in true darkness without the
  adaptation wait, blinds in bright light. Parts: two image tubes, oscillator, lenses.
- **Ore scanner** – a pulse coil and a circuit: shows the ores within a radius on the next pulse. Ties
  into exploration and the atlas.
- **Exoskeleton** (the E4 capstone) – a frame on the legs and back with motors at the joints:
  takes most of the carry-weight penalty, softens the walking penalty from wounds, and lets the
  wearer break the next block a little faster. It needs every earlier part: motors, gearboxes,
  bearings, a controller (advanced circuit), a silver–zinc cell pack, a frame of titanium or
  aluminium. Without charge it is a heavy frame and makes everything worse.

**Tests:** cells charge at the station and drain in use; a drill without charge is a block of metal;
the suit lining keeps temperature in band until its cell is empty; the exoskeleton removes the
carry-weight penalty only while charged.

## E5 – Automation: parts make themselves

**Status: done** — see [automation.md](automation.md). Where it differs from the plan: a drawing is
drawn of what the player has studied rather than strictly crafted; the stations' Mk II are the automated
stations, since the first marks were machines already; machine wear applies to the electric machines,
with the drives' brushes from E2.

**Gives the player:** relief from the micro-crafting they spent E1 to E4 doing. This is the payoff of
the part design: everything the player has made by hand can now be made by a line they designed.

- **Assembler.** Holds a drawing (a pattern copied from the assembly bench once the player has made
  the thing by hand once — a knowledge gate, as research is meant to be) and makes it from the parts
  fed to it. Parts: motors, a gripper arm (servo, gearbox, claw), controller (advanced circuit), frame.
- **Automated wire drawer, coil winder and glass kiln** – the hand stations as machines, slower than a
  skilled hand at first, then faster at Mk II.
- **Conveyor** – belts on a motor, for lines inside a hall where item pipes are clumsy.
- **Storage desk ordering.** The storage desk gets a power port: with current, it can ask the
  assemblers on its network for what is missing and wait for it. This is the end point — a request
  at the desk becomes a part made by a line the player built.
- **Machine wear** (specification section 78) arrives here: bearings, brushes and gaskets of running
  machines wear, lubrication matters, a neglected line loses speed, heats and seizes. Automated lines
  keep consuming parts, so the supply lines do not become pointless.

**Tests:** an assembler makes a part from its drawing and stops when a part is missing; the storage
desk orders a missing part and receives it; a machine without lubrication wears faster and seizes.

## E6 – The Tesla tower (capstone)

**Status: done** — see [tesla.md](tesla.md). Where it differs from the plan: the tower is five blocks
stacked and a capacitor bank beside them, checked by its foot; its power is 900 W, what two transformers
carry; the defence strikes three monsters a second at most.

**Gives the player:** power without wire, around the home, and a guard that never sleeps.

A multiblock: a tall coil, a capacitor bank, a spark gap and a top load, fed at 960 V through a
step-up transformer from the 240 V line.

- **Wireless charge** – every cell worn or carried within its radius charges slowly. The exoskeleton,
  the drill and the headlamp stop needing the charging station near home.
- **Wireless lamps** – lamps of a matching kind within the radius light without wire.
- **Defence** – hostile mobs that come within its inner radius are struck by arcs, at the cost of a
  great deal of power per strike. Under load the lights dim — the player sees what the defence costs.
- **Danger** – the player is not immune: metal armour near the top load draws arcs too, and in a
  thunderstorm the tower calls the lightning.

Parts: elite circuits, a resonant coil (hundreds of turns of enamelled wire on a porcelain former),
the capacitor bank (dozens of capacitors in oil), a tungsten spark gap, a copper top load, the
transformer. It is the deepest tree in the mod, and the place where Arcane Engineering (milestone 20)
can join later: a void-crystal resonator in place of the spark gap is the obvious hook.

**Tests:** a cell within the radius charges and one outside does not; a wireless lamp lights in range;
a hostile mob in range is struck and the network's power drops for it.

## Ultra hooks

Ultra makes every system harder; each stage leaves a place for it. Suggestions, to be decided when
Ultra is done:

- E1: wires burn through sooner, fuses are the only protection (no breaker), transformers lose more.
- E2: motor brushes wear twice as fast.
- E3: devices work on a smaller room volume; the sickbed only heals with sterile dressings in use.
- E4: cells hold less and lose charge sitting; the exoskeleton's frame weighs more.
- E5: an assembler needs a drawing made by hand *at a quality*, not just once.
- E6: the tower's defence costs more per strike.

## Open questions

- Which hand stations get a minigame (as smithing has), and which are a simple screen? The coil winder
  and wire drawer are good candidates for a short skill step; the assembly bench should be a layout,
  not a minigame, or it becomes tedious.
- Is the oscillator-and-tube path for night vision too modern? A simple dark-adaptation boost from a
  red headlamp would be a cheaper middle step.
- Uranium and thorium are in the ground. A reactor is beyond this roadmap; thorium could go into gas
  mantles for the petroleum lamp instead.
- Ammonia refrigerant: poisonous leak via the gas system, yes or no?
