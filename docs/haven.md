# The Safe Haven

Stage E3 of the [electricity roadmap](electricity-roadmap.md): the base stops being a place where the
world's rules still apply. Every device answers a hardship the mod already has, takes its current from
a wire made fast to it directly (insulated wire runs through walls to it), and is built from parts that
each have their own way of being made.

## The devices

| Device | Rated | Answers | What it does |
|---|---|---|---|
| **Electric light** | 60 V, 25 W | the dark, torches that burn out | Full light from a tungsten bulb. The bulb burns out after four hours of light; a new **light bulb** mends it. |
| **Arc floodlight** | 240 V, 960 W | monsters at the perimeter | No monster spawns within 32 blocks of it, not even in the autumn fog; monsters within 24 walk out of its light unless they are within 3 blocks of their target. Its **arc electrodes** last two hours of light. |
| **Motion lamp** | 60 V, 25 W | being crept up on | Dark until something alive moves within 8 blocks; then lit for ten seconds and giving a full redstone signal. |
| **Alarm bell** | 60 V, 30 W | sleeping through an attack | Rings while it has a redstone signal: wakes everyone asleep within 32 blocks — and every idle monster that hears it comes to look. |
| **Electric heater** | 60 V, 120 W | cold rooms, winter | Holds its room at its thermostat. |
| **Air cooler** | 60 V, 180 W | heat | Holds its room at its thermostat, pumping out two and a half times what it draws. |
| **Ventilation fan** | 60 V, 40 W | CO, firedamp in rooms | Set in a wall facing in: draws gas from the open air in front of it (10 blocks) and blows it out behind, 8 units a second. |
| **Ice box** | — | food that rots | Food goes off at half the speed while there is ice in it. Ice lasts half a day, packed ice four times, blue ice sixteen. |
| **Refrigerator** | 60 V, 60 W | food that rots | Food goes off at a quarter of the speed. Takes current only while it holds food. |
| **Freezer** | 60 V, 100 W | food that rots | Food does not go off at all. |
| **Heat lamp** | 60 V, 100 W | cold, frostbite | Warms like a working fire; frostbite thaws under it. |
| **Bedside monitor** | 60 V, 15 W | wounds, illness | A bed within two blocks is a **sickbed**. |
| **Autoclave** | 60 V machine | infection | Steams a bandage into a **sterile dressing**. |

Every device works by the square of its voltage and does nothing below half of it. A quarter over its
voltage it strains, and at nearly twice it the part that wears goes at once. That part also mends it:
used on the broken device, a light bulb, arc electrode, relay, heating element, compressor, fan blades
or basic circuit puts it right. Their casings are earthed. A meter on any of them says what it is
doing and what it draws.

### Rooms: heater and cooler

A heater or cooler works on the room the room scan finds in front of it, or above it. What it puts in is
spread over the room's volume — 10 °C per watt per block of room — so a 120-watt heater warms a room of
sixty blocks by about twenty degrees, and a hall poorly. The walls keep it by their insulation, and every
opening loses it: a room with one open door keeps a third of it, so it does not reach what the
thermostat asks. In the open air it does nothing. A room a device works in is kept warm or cold whether
anyone is in it or not.

The thermostat is a bimetal strip with half a degree of play. An empty hand sets it, 10 °C to 30 °C in
steps of two.

### The sickbed

Asleep in a bed beside a working bedside monitor, a player heals half a heart a second on top of
anything else, and is cared for two and a half times as well against what ails them (on top of the
threefold care of sleep). In Ultra, where nothing heals by itself, the sickbed heals only a wound kept
dressed — under a bandage or a sterile dressing.

A **sterile dressing** cleans a wound as antiseptic does and keeps it bound twice as long as a bandage.

### Water

Bad water already has its electric answer: the electric purifier (see
[water-quality.md](water-quality.md)), so there is no separate water heater.

## Food spoils

Section 107 of the specification, built with the cold stores. Food has a shelf life in days; the moment
it first comes into a player's hands it is marked with when it will be rotten, rounded up to the next
quarter of a day so that what was picked or cooked within a few hours still stacks. From then on it goes
off wherever it is.

| Food | Keeps |
|---|---|
| Raw fish | 2 days |
| Raw meat, stews and soups | 3 / 2 days |
| Cooked fish, pumpkin pie, melon | 4 days |
| Baked potato | 5 days |
| Cooked meat, bread, berries | 6 days |
| Anything else | 8 days |
| Apples, beetroot, cookies | 16 days |
| Carrots | 20 days |
| Potatoes | 24 days |
| Honey, dried kelp, golden food, chorus fruit, spider eyes, rotten flesh | forever |

Past three quarters of its life food is **stale** and upsets the stomach one time in five on top of what
it does anyway; rotten food three times in five. The next time a player has it, or opens what holds it,
rotten food is gone — meat and fish to rotten flesh, everything else to **spoiled food**, which the
composter takes. The tooltip says how many days it has left.

## The stations

| Station | Makes |
|---|---|
| **Wire drawer** | tungsten rod → 4 tungsten filament; nichrome rod → 4 nichrome wire; copper rod → 3 copper wire |
| **Glass kiln** | glass → 4 glass bulbs; glass pane → 2 glass tubes |
| **Autoclave** | bandage → sterile dressing |
| **Lathe** (new work) | tungsten rod → 2 arc electrodes |

All three are machines on current like the lathe, opened like a furnace.

**Nichrome** is a new alloy: four parts nickel to one of chromium in the smeltery, at 1400 °C. It has
rods and plates like every metal.

## Parts

| Part | Made |
|---|---|
| Tungsten filament (4) | wire drawer: tungsten rod |
| Nichrome wire (4) | wire drawer: nichrome rod |
| Glass bulb (4) | glass kiln: glass |
| Lamp socket (2) | porcelain, copper plate |
| Light bulb | tungsten filament, glass bulb, lamp socket |
| Reflector | 5 aluminum plates, a glass pane |
| Ballast | a winding, 2 electrical steel plates, 3 iron plates |
| Arc electrode (2) | lathe: tungsten rod |
| Motion sensor | 2 quartz oscillators, a basic circuit, a glass pane, 2 copper wire |
| Relay (2) | silver plate, redstone coil, iron plate |
| Heating element | 6 nichrome wire, 3 porcelain |
| Bimetal strip (2) | copper plate, iron plate |
| Thermostat | bimetal strip, porcelain, copper wire |
| Fan blades | 4 aluminum plates, a steel rod |
| Compressor | an electric motor, 2 engine pistons, 4 steel plates, 2 rubber gaskets |
| Refrigerant | chemical reactor: 4 bone meal and a steel plate — ammonia driven out of bone |
| Cooling coil | 8 copper plates |
| Insulated panel (4) | 6 steel plates, 3 wool |
| Pressure vessel | 7 steel plates, a silicone gasket |
| Pressure gauge | glass pane, 2 copper plates, a fine gear |

The motor is in four devices (compressor, fan), the thermostat in three, the light bulb in three, the
heating element in two: supply lines pay off across devices.

## Tests

`HavenGameTests`: a heated closed room warms well above the outside and one with its door open much
less; a fan draws carbon monoxide out of a room; a refrigerator puts food's rotting back by three
quarters of the time and a freezer by all of it, and rotten food turns; an ice box keeps food at half
the speed while its ice melts; an arc floodlight on a stepped-up line burns, bars spawning within its
reach and not beyond, and is found by monsters to keep away from; a motion lamp that saw something lights,
signals and rings the bell beside it, and an electric light whose bulb is worn goes dark; a sickbed heals
whoever sleeps in it; the wire drawer, glass kiln and autoclave make their parts, and nichrome is an
alloy of nickel and chromium.

The assets are written by `tools/HavenAssets.java`.
