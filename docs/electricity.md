# Electricity

Section 76 of the specification. First step: power is there and can be used — a dynamo on the
driveline, bare copper strung between insulators, a lamp, and a meter to read it all with. Second
step: masts to carry a line overhead, a battery to hold it up, and the first machine that runs on
current, the basic crusher. Third step: the machines — electric furnace, sawmill and plate press
beside the crusher, each with a screen. Fourth: the grid at 240 V — transformers, porcelain,
switchgear and insulated wire. Fifth: motors that turn the driveline. The devices that run on all
this — lights, heaters, coolers, fans, cold stores, the floodlight and the sickbed — are in
[haven.md](haven.md).

## The parts

| Part | What it is |
|---|---|
| **Dynamo** | A machine on the driveline. Its axle runs through it; the wire comes off the post on top. |
| **Copper coil** | The connection between a line and a dynamo, a battery or a machine: set against one, it is where that block's wires go. |
| **Insulator** | The support a line is strung from. Stands out from the face it is set against. Nothing electrically. |
| **Line mast** | A timber pole four blocks tall with an insulator on its crossarm, set up in one piece. |
| **Battery** | Lead plates in acid: takes current while the line stands above its voltage, gives it back below. |
| **Basic crusher** | Iron jaws with a motor on them. Ore in, powder out, like the starter crusher, on current. |
| **Electric furnace** | A heating coil in a brick chamber: whatever a furnace smelts, with no fuel. |
| **Sawmill** | A blade on a motor: half as many boards again out of a log as a hand gets. |
| **Plate press** | An ingot in, a plate out, with no hammer and no heat. |
| **Electric lamp** | Light with no fuel in it, as bright as the voltage that reaches it. |
| **Copper wire** | Thin: 0.04 Ω a block, 4 A, spans 16 blocks. |
| **Copper cable** | Four wires' worth: 0.01 Ω a block, 16 A, spans 24 blocks. |
| **Meter** | Held to any point: voltage, current, how hard its wires are worked, what the network gives and loses. |

A coil is used on one point and then on a second; it strings 8 blocks, and a longer run takes more
coils. Nothing may stand in the way. Every point holds four wires. Shears take the wires off a point
and give the coils back — and touch the line while they do it.

## How it is worked out

The earth is the way back, so one wire is a circuit: every dynamo drives its voltage against the
ground and every lamp lets current through to it. From each wire's resistance the voltage at every
point and the current in every wire are solved exactly, twice a second and at once when anything
changes. Nothing is approximate: a lamp at the end of a long thin line really is dimmer than the one
beside the dynamo, and the meter shows why.

### The copper coil

A dynamo, a battery and a machine take no wire themselves. A coil is set against any face of one —
on top, on a side, underneath — and the wire is strung to the coil: what comes down the wire goes
through the coil into the block it sits on, and what the block gives goes out the same way. Several
coils on one block are several ways into it, and current passes from one to the other through the
block, which is how a line is carried on past a machine. A coil against anything else is a stubby
support. It is bare copper and bites like any live point; the block under it does not. Lamps,
insulators and masts take wire directly, as before.

### The dynamo

- **2 V for every turn per minute.** A hand crank's 16 turns give 32 V; a crank box or a fast water
  wheel at 32 gives 64 V; geared up two to one, 128 V. Voltage is bought with gearing.
- **Heavier to turn the more current is drawn**: its impact on the driveline is 1 + 6 for every
  ampere. Current is bought with strength, so the strength a line has to spare is the power it gives.
  A water wheel in a good river runs about four lamps; a hand on a crank one, dimly.
- **It lets go instead of stalling the line.** Where the driveline cannot turn it against the current
  asked of it, the dynamo gives nothing for five seconds and then tries again. Lamps that flash every
  few seconds are a dynamo with too much hung on it; the meter says so.

### The lamp

120 Ω, made for 60 V, where it takes half an ampere and gives full light. Below that it is dimmer,
below 12 V dark. Above 75 V its filament strains and goes within seconds to minutes; at 110 V it goes
at once. A coil of thin wire on a dead lamp winds it a new one.

### The mast

Four blocks of pole placed as one item, where all four have room; any block of it broken, the whole
comes down and its wires with it. Its head is the point of the network, and a coil, a meter or shears
used anywhere on the pole reach it. A wire from mast to mast hangs, at its lowest, about a block above
a standing player's head — which is the whole point of it. The pole is timber: a hand on it feels
nothing.

### The battery

A source of its own: 54 V empty, rising to 62 V full, behind 2 Ω. Nothing switches it. Where the line
stands above its voltage current runs into it; where the line stands below, it gives. So:

- **It charges only from more than its own voltage.** A dynamo at a hand crank's 32 V never fills
  one; a wheel's 64 V does, at about three amperes into an empty battery and ever less as it fills.
- **A dynamo cuts out** rather than be driven backwards: with the wheel stopped, the battery feeds
  the lamps and nothing runs back through the dynamo.
- It holds 72 000 watt-seconds — two lamps for twenty minutes — and gives back four fifths of what
  went in. The gauge on its side shows quarters; the meter shows per cent.
- **More than 8 A in or out and the acid boils** (steam over it); kept up for some seconds, the case
  bursts. A geared-up dynamo straight onto a battery does that.

### The machines

Four so far, one block and one screen each, all the same thing electrically: a resistance made for
60 V that takes its current only while there is something in it to work on and room for what it
makes. Standing idle a machine takes nothing.

| Machine | Takes | Gives | Draws at 60 V | A piece takes |
|---|---|---|---|---|
| Basic crusher | what the starter crusher breaks | its powder | 1.5 A (40 Ω) | 2 s |
| Electric furnace | whatever has a smelting recipe | what that gives | 2 A (30 Ω) | 5 s |
| Sawmill | a log | its boards, half as many again | 1.5 A (40 Ω) | 3 s |
| Plate press | an ingot of any metal that has stock | its plate | 2 A (30 Ω) | 4 s |

The crusher's powder is what the furnace smelts, so the two stand in a row with a hopper between.

On less than its voltage a machine works by the square of what it gets — half the voltage, a quarter
of the work — and under 30 V it hums, takes its current and does nothing. Over 75 V it runs faster and
its windings cook; at 110 V they go at once, and a coil of thin wire rewinds them.

**The screen** has the input on the left, the output on the right and an arrow filling between them,
and beside them a voltmeter: a column from nought to 120 V with the band the machine is made for
marked on it, the figure in volts and amperes, and one word for how it is — ready, running, too
little voltage, burnt out. Hoppers feed a machine from the top and sides and draw from the bottom.
Its casing is earthed: it can be opened while live.

One kind of block entity serves all of them; `Machine` says what each takes and gives.

## What goes wrong

- **Overloaded wire burns through.** Over its rating a wire heats with the square of the current: a
  quarter over, it lasts a quarter of a minute; at twice its rating, three seconds. It glows first.
  When it parts, what drips from it sets fire to whatever lies under its middle.
- **Bare wire bites.** From 30 V, anything touching a strung wire is hurt — players, animals, monsters —
  by a twentieth of the voltage in half hearts, twice a second, and its muscles lock (slowness). Armor
  is no help. So is a bare hand on a live dynamo, lamp or insulator. A line belongs above head height.
- **Water lets current creep.** A point with water on any side of it leaks through 20 Ω: the line
  loses power, the wire feeding it may overload, and everything standing in water within four blocks
  is shocked. Rain does the same more gently (150 Ω) to a dynamo or a lamp with open sky above it; a
  block over it is roof enough. Insulators are made for the weather.

## The grid at 240 volts

Stage E1 of the [roadmap](electricity-roadmap.md): power carried from where it is made to where it is
used, and the parts that keep a line from burning the house down.

| Part | What it is |
|---|---|
| **Transformer** | Two windings on a core of electrical steel, 1:4. Steps 60 V up to 240 V and, the other way round, down again. |
| **Bushing** | A coil in a porcelain sleeve. Where a line at high voltage goes into a transformer. |
| **Porcelain insulator** | The insulator for high voltage. |
| **High-voltage mast** | A line mast with a porcelain insulator on its crossarm. |
| **Insulated wire** | Thin wire in rubber: bites nobody, goes through walls, spans 12 blocks. |
| **Knife switch** | Joins the coils set against it while closed. Thrown by hand, or follows a redstone signal. |
| **Fuse box** | The same, with a 4 A cartridge that blows; a new cartridge mends it. |
| **Breaker** | The same at 16 A, closed again by hand. |
| **Coil winder** | A machine: eight lengths of enamelled wire make one winding. |
| **Mk IV machines** | Crusher, furnace, sawmill and press wound for 240 V. |

### The transformer

The block itself is the low side and is joined to a line through coils on any face, as a machine is.
The high side is the coil, or better the bushing, on its marked face — the face that pointed away from
whoever set it down. Fed from the bushing, it steps down.

It is worked out exactly with the rest of the network: an ideal 1:4 transformer with 1 Ω of winding on
the high side. What stands on the low side stands four times over on the high side; what is drawn from
the high side is drawn four times over from the low. So the same power crosses a line on a quarter of
the current and loses a sixteenth as much on the way, and a thin wire at 240 V carries what a cable
carries at 60.

It is rated for 4 A out of its high side (16 A on the low). Above that it hums and smokes, and kept up,
its windings char: it is burnt out and couples nothing until a new **winding** is used on it. Its tank is
earthed and can be touched while it works.

### High voltage and rain

Glazed brick stands 150 V in the rain. Above that, rain on an insulator, a mast's head or a plain coil
flashes over: it crackles and lets current to the ground through 20 Ω, as standing water does. Porcelain
— the porcelain insulator, the bushing and the high-voltage mast — stands 1000 V. Under a roof nothing
flashes over.

240 V on bare wire bites for twelve half hearts twice a second. The meter shows the voltage before a hand
touches anything.

### Switchgear

A switch, a fuse box and a breaker are blocks a line is led through: in on a coil on one face, out on a
coil on another. Closed, they join their coils; open, the two sides are two networks, and the side without
a source goes dark.

- A **fuse** blows after half a second at half again its 4 A — sooner than the wire behind it burns
  through, which is what it is for. It takes a new **fuse cartridge**.
- A **breaker** trips at 16 A after a little longer and is closed again by hand; it can be opened by hand
  too.
- A **knife switch** is set down open, so that nothing goes live before it is wired.

The meter says on each whether it is closed and how much of its rating runs through it.

### The fourth marks

Mk IV machines are wound for 240 V: below 120 V they only hum, and they strain only above 300. They are
twice as fast as Mk III again. Their screen's voltmeter reads to 480 V. Built from the Mk III with the
elite circuit, two windings, silicone gaskets and electrical steel.

### Parts

| Part | Made |
|---|---|
| Electrical steel (2) | arc furnace, small or large: 2 steel and raw silicon |
| Electrical steel plate (2) | plate press: electrical steel |
| Transformer core | 8 electrical steel plates in a ring |
| Enamelled wire (4) | 4 copper wire and resin |
| Winding | coil winder: 8 enamelled wire |
| Porcelain blank (2) | fireclay, clay and quartz |
| Porcelain | a porcelain blank fired in any furnace |
| Fuse cartridge (4) | porcelain, copper wire, a glass pane |
| Insulated wire (2) | 2 copper wire and rubber |

The transformer itself: two windings, a core, a bushing and steel plates.

## Motors on the driveline

Stage E2 of the [roadmap](electricity-roadmap.md): every mechanical machine runs off the grid, anywhere.

| Part | What it is |
|---|---|
| **Electric drive** | A motor in a housing on the driveline: the dynamo the other way round. Joined to a line by a copper coil. |
| **Motor controller** | A circuit and a bank of resistors, fitted to a drive by hand. |
| **Stator, rotor, commutator, graphite brush, motor housing** | What an electric motor is now built from. |

### The drive

- **Speed follows voltage**: a turn a minute for every two volts at its coil, the dynamo's rule read
  backwards. 60 V turn it at 30; a gearbox does the rest.
- **Strength follows current**: it gives its line 6 stress units for every ampere it may draw, up to
  4 A, at every turn a minute — at 60 V about as much as a good water wheel. It draws what the line
  asks of it: an idle drive takes a tenth of an ampere, one carrying its full load four.
- **Starting and stalling.** For two seconds after it starts it draws five times its load. Held still —
  asked for more strength than it has, or on a jammed line — it draws five times its rating, twenty
  amperes, for as long as it is held, and trips a breaker.
- **Its brushes wear** while it turns: two hours of running (one in Ultra). Worn through, it stops and
  the meter says why; two graphite brushes renew them once the old are down to a quarter.
- Over 75 V its windings cook as a machine's do, and a coil of thin wire rewinds them. Its housing is
  earthed.

### The controller

Fitted to a drive (use it on one), it starts the drive gently, without the inrush; it holds a stalled
drive to its four amperes, so that it only hums; and an empty hand on the drive sets its speed to
three quarters, a half, a quarter and back to the whole of the voltage. What it does not let through
it burns in its resistors: a drive turned down to half takes as much current for half the speed. It
comes off with the drive.

### Parts

| Part | Made |
|---|---|
| Stator | 4 iron plates, 2 redstone coils, 2 copper wire |
| Rotor | 2 iron plates, 2 copper wire and a shaft |
| Commutator | lathe: a copper rod |
| Graphite brush (8) | lathe: a graphite electrode |
| Motor housing | 8 iron plates |
| Electric motor | housing, stator, rotor, commutator, 2 graphite brushes, 2 bearings |
| Electric drive | an electric motor, 2 shafts, 6 iron plates |
| Motor controller | 3 resistors, a basic circuit, 2 copper wire, 3 iron plates |

Every machine that took an electric motor still does; the motor now costs its parts.

## Tests

`ElectricityGameTests`: a dynamo on the driveline lights a lamp and is heavier to turn for it; a long
thin line loses voltage and a cable a quarter as much; wet points leak and the overloaded wire feeding
them glows and burns through; a dynamo a hand cannot turn lets go without stopping the line; geared
up, it gives more than a lamp stands; a battery fills from the line, holds it up when the dynamo stops,
gives nothing empty and takes nothing full; coils join a line to the blocks they sit on and carry it
on through them; a mast is one thing and carries a line overhead from its
foot; the basic crusher breaks ore on current and takes none standing idle; every machine makes what
it is for, and the furnace smelts the crusher's powder. `ElectricityClientGameTest` photographs a line by day and night, and a machine opened.

`MotorGameTests`: a drive turns its line at a turn a minute for every two volts and draws what one
crusher asks of it, and the crusher crushes; held still by four crushers it trips its breaker, and with a
controller it is held to its rating and the breaker stays in; a controller turned down to half halves its
speed, worn brushes stop it and its line, and new brushes go in only once the old are worn.

`GridGameTests`: a transformer steps a line up to four times its voltage and a second steps it down
for two lamps, with a quarter of the current on the line; an overloaded transformer burns out and couples
nothing, and within its rating never does; a fuse blows before the wire it guards and leaves the far side
dead, and a knife switch cuts a lamp off; insulated wire goes through a wall and does not bite a pig that
bare wire bites; porcelain stands what glazed brick does not; a Mk IV only hums on 60 V; the coil winder
waits for its eighth length of wire. Flashover in the rain is not tested in a game, since tests share
the weather.

The assets are written by `tools/electricity_assets.py`; those of the grid by `tools/GridAssets.java`
(`java tools/GridAssets.java`), those of the motors by `tools/MotorAssets.java`.
