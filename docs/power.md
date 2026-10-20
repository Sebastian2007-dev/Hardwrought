# Power from oil and gas, and machine parts

This is the first step of the oil-era technology tier: oil and gas burnt for power, heat and light,
and the small parts that machines are now built from.

## Parts (`parts/Parts`)

| Part | Recipe | Goes into |
| --- | --- | --- |
| Redstone coil (2) | copper wire, redstone, iron rod | motor, furnace heating, injector |
| Electric motor | housing, stator, rotor, commutator, 2 graphite brushes, 2 bearings ([electricity.md](electricity.md#motors-on-the-driveline)) | crusher, sawmill, press, electric pump, electric drive |
| Spark plug (2) | copper rod, fireclay, steel plate | gasoline engine (wears), gas burner |
| Glow plug (2) | redstone, fireclay, steel plate | diesel generator |
| Engine piston (2) | 3 steel plates, 2 steel rods | engines |
| Crankshaft | 3 steel rods, 2 bearings | engines |
| Fuel injector | bronze, steel rods, 2 redstone coils | diesel generator |
| Bearing (4) | 4 steel plates, iron nugget | crankshaft, gasoline engine |

The old electric machines are built from the parts now:

- the crusher, sawmill, plate press and electric pump take an **electric motor** instead of a dynamo;
- the crusher, electric furnace and electric pump take **redstone coils** in place of some wire.

The dynamo stays as the generator it is.

## Diesel generator

An engine and dynamo in one housing. It gives **64 V** through 0.5 Ω and is joined to a line through a
copper coil, as a dynamo is. It runs whenever it has fuel, and a redstone signal stops it.

**Fuel.** It takes diesel, the still's fuel or kerosene, by pipe or bucket (4 buckets). Diesel goes
furthest, kerosene gives 90 % and the still's fuel 75 %. It burns 0.25 mB a second idling plus
1 mB per kJ given, so a bucket runs a 500 W load for about half an hour.

**Exhaust.** For every 20 mB burnt it gives off a unit of CO₂ and now and then one of CO. The exhaust
comes out over the generator, or out of the open end of a flue pipe set on it.

## Gasoline engine

A shaft source, like a water wheel, that runs anywhere: **48 RPM** with a strength of **6144**. The
axle leaves by the back.

**Fuel.** It takes gasoline, or the still's light spirit at 70 %, by pipe or bucket. It burns 2 mB a
second, so a bucket lasts about 8 minutes.

**Spark plug.** It needs a spark plug, which wears out after an hour of running. Hold a new one to the
engine to change it; this works once the old one is down to a quarter.

**Exhaust.** It runs richer than the diesel, so it gives off more CO. Redstone stops it.

## Gas burner

A clean heat source for the still, the refinery base, the cracker and the reformer: it counts as
"over the fire". It burns natural gas or LPG from a gas line or from a canister held to it, and a
canister lasts ten minutes. It lights itself while it has gas, and redstone switches it off. It gives
off a little CO₂ and no CO. Its flame is open, so firedamp goes off at it.

## Petroleum lamp

A brass lantern that burns kerosene. Fill it with a bucket; it holds two buckets and burns 100 mB a day,
so one bucket lasts ten days. An empty hand turns it on or off and shows the fuel level. It is an open
flame.

## Windows

The generator, the engine and the burner open the same kind of window as the refinery blocks. The
generator shows its watts, the engine its spark plug and speed, the burner its gas.

## Tests

`PowerGameTests`:

- the generator runs on diesel, stops on redstone, and takes the still's fuel but not gasoline;
- the engine waits for a spark plug, then drives its shaft at 48 RPM and wears the plug;
- the burner heats a still;
- the lamp is filled and lights.

## Next

Materials, circuits, the Mk II and Mk III machines and the multiblocks are in [materials.md](materials.md).
The 240 V grid, which the elite circuit is made for, is in [electricity.md](electricity.md).
