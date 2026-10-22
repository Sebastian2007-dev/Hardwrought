# Steam

Development plan section 85 (phase 12): boilers, pressure, pistons, valves and steam engines, and what
goes wrong with them — leaks, failure, explosions. The idea is borrowed from Create's boiler and steam
engine: fire under a vessel of water, an engine on it that turns a shaft. Hardwrought works the vessel out
physically instead: heat, a boiling point that climbs with the pressure, a drum that can burst.

The code is in `de.ipnats.hardwrought.steam`; the stop valve is a gas line's (`gasline.GasValveBlock`).
The assets are written by `tools/SteamAssets.java`.

```text
firebox ─ heat ─ boiler ─ steam by gas line ─ (stop valve) ─ steam engine ─ shaft
              ↑ water by pipe or bucket     └─ safety valve on top          └─ whistle on the line
```

## Blocks

| Block | Recipe | Does |
|---|---|---|
| Firebox | 5 iron plates over 3 refractory bricks | burns coal, charcoal, coke or logs: 10 kW to what stands on it |
| Boiler | iron plates round a gauge glass (glass pane), 2 copper plates and a bucket | water in, steam out, at a pressure |
| Safety valve | iron rod between 2 iron nuggets over a bronze plate | on top of a boiler, lets off what is above its setting |
| Steam cylinder | 6 iron plates, 2 iron rods, a bronze plate | the engine's part |
| Steam engine | steam cylinder, shaft, cogwheel over 3 iron plates | shaft source, 32 RPM, up to 3072 strength |
| Stop valve | iron rod over 2 iron gas lines and a bronze plate | parts a gas line, by hand wheel or redstone |
| Steam whistle | 2 bronze plates over an iron gas line | sounds on steam, by hand or redstone |

## Firebox (`FireboxBlock`)

Fuel goes in by hand or hopper (one slot, nothing comes out). Fuel lasts as long as in a forge: coal and
charcoal 80 s, coke 120 s, a log 15 s. Laid fuel has to be lit with a flint and steel, a fire charge or
lighting sticks; then it keeps going while fuel follows, and goes out when it runs out.

A redstone signal closes the damper: the fire smoulders at 20 % of its heat and of its fuel. It gives off
a unit of CO₂ every ten seconds of full fire, out over the boiler, or up a flue pipe set on the boiler.

Other heat under a boiler: gas burner 8 kW, lit forge 5 kW, lit furnace 3 kW, lava 3 kW, fire 2 kW, lit
campfire 1.5 kW, magma 1 kW.

## Boiler (`BoilerBlock`)

Holds 4 buckets of water (pipe or bucket, any water) and 100 units of steam, which stand at 20 bar full.
Gas lines draw its steam; nothing draws its water.

Worked out every tick:

- **Heat.** The iron has 4 kJ/K, each millibucket of water 2 J/K more. The fire's heat goes in, less what
  the scale keeps back; 8 W/K is lost through the lagging above 20 °C.
- **Boiling.** The boiling point is `100 °C × (1 + p)^0.25` at `p` bar: 100 °C open, 149 °C at 4 bar,
  182 °C at 10 bar. Heat above it becomes steam: 4 kJ and 2 mB of water a unit, as a reactor's steam.
  Steam drawn off drops the pressure and with it the boiling point, so the hot water flashes to more:
  the boiler is a heat store that rides out a sudden load.
- From cold, a full firebox brings 4 buckets to the boil in about a minute and a half; at full fire it
  then makes about 2 units of steam a second.
- An empty hand reads temperature, pressure, water, scale and damage.

## Steam engine (`SteamEngineBlock`)

Takes steam by gas line on any side but the back, where its axle leaves. Its chest holds 20 units at the
boiler's scale of pressure. It turns at **32 RPM**; from 1.5 bar it turns over, and its strength climbs in
twelve steps to its full **3072** at 8 bar.

A governor lets in what the load asks: every second it works out its line and takes 0.2 units running
light plus up to 3 units at full strength, in proportion to the share of the line's stress that falls to
it. A full firebox keeps about two thirds of an engine at full strength. A redstone signal shuts the
throttle. An empty hand reads inlet pressure, speed and load.

## Valves and the whistle

- **Safety valve** (`SafetyValveBlock`): sits only on a boiler. Settings 4, 6, 8 (default), 10 or 12 bar,
  turned by an empty hand. Above its setting it blows off up to 3 units a tick, which is more than any
  fire makes, and reseats 0.3 bar below.
- **Stop valve** (`GasValveBlock`): an iron gas line with a hand wheel. The wheel's stem stands up while
  it is open. Shut, by hand or by a redstone signal, it ends the line from both sides
  (`GasNetwork.collect`), so the two halves are two lines. It bursts as iron line does.
- **Steam whistle** (`SteamWhistleBlock`): a small steam store on a line. Pulled by hand or on a rising
  redstone signal, with at least 1 bar behind it, it sounds a chime and takes 1 unit; higher pressure,
  higher and louder note.

## When it goes wrong

| Cause | What happens |
|---|---|
| Pressure above the burst pressure | 16 bar, less half of it at full damage. A third of the seconds above it the boiler explodes: power 1.5 + p/5 + water/2000 mB (at most 6), with a cloud of steam that scalds. Above 85 % of it the iron strains (damage). |
| Run dry | Bare iron heats without limit; above 300 °C it takes damage fast. |
| Fed while red hot | Water on iron above 350 °C flashes at once: explosion. |
| Scale | Each bucket put in brings scale for later: purified 0, fresh 2 %, river 3 %, swamp 8 %, seawater 25 % of a furred-up boiler. It settles as the water boils off. It keeps up to half the heat from the water; above half furred up the iron under it overheats (slow damage). A pickaxe chips it all off a boiler under 50 °C. |
| Damage ≥ 25 % | Leaks: steam lost at the seams in proportion to damage and pressure, and a scald for whoever stands within 1.5 blocks, every second. An iron plate patches a quarter of the damage, on a boiler under 100 °C with no pressure. |
| Gas line too weak | As before, a line bursts at its weakest pipe when a store on it stands above its limit (bronze 6 bar, iron 12). Steam out of a burst line now scalds everyone within 2.5 blocks (`Scalding`). |

Scalding is its own damage type: armour helps a little, a shield not at all, and it does not knock back.

## Tests

`SteamGameTests`:

- hot water in a boiler flashes to the pressure its heat is worth, and a lit firebox raises it further, from purified water without scale;
- a safety valve lets a drum at 14 bar down to its 4 bar setting, and the boiler stands;
- a boiler without a safety valve bursts above its limit;
- water fed into red-hot iron bursts the boiler;
- seawater leaves scale, which a pickaxe chips off a cooled boiler but not a hot one;
- a damaged boiler leaks and loses steam, and an iron plate takes a quarter of its damage off;
- a boiler run dry over magma is ruined;
- an engine drives its shaft at full strength on steam from a boiler through a line and an open valve,
  and stops on redstone; behind a shut valve it gets nothing;
- a burst bronze steam line scalds a pig beside it;
- a whistle is silent without steam, sounds with it, and takes steam for it.
