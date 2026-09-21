# Milestone 5: Vertical World Prototype

Milestone 5 implements specification sections 41 to 49: the world becomes 1280 blocks tall, and the
height a player stands at becomes a gameplay input rather than a coordinate.

Section 41 states the condition the whole milestone is judged by — **the extra height and depth must
matter mechanically**. A taller world that plays exactly like a short one is worth nothing, so the
range comes with the three things that make it felt: thin air above, heat below, and wind between.

> **An existing world cannot be converted.** Changing the height of a dimension changes the shape of
> every chunk in it. Worlds created before this milestone have to be started again.

## The range

```text
Y +1024   ceiling (highest block Y 1023)
Y  -256   floor
1280      blocks of world
```

Two datapack files state it, and they have to agree:

| File | What it does |
| --- | --- |
| `data/minecraft/dimension_type/overworld.json` | the world is −256 to +1023, `logical_height` with it |
| `data/minecraft/worldgen/noise_settings/overworld.json` | terrain is *generated* through that whole range |

The dimension type alone would leave the new depth as void — terrain exists only where the noise
settings reach, which is why both are overridden and why a test asserts both.

The noise settings are **vanilla's own overworld settings** with nothing changed but that range. The
terrain is the terrain everyone knows — ordinary land with ordinary mountains — and what is new is
how far the world reaches above and below it. Amplified terrain was tried here and thrown out: it
turns every landscape into a jagged one, which is not what "extreme mountains" is supposed to mean.

What the widened range does to generation, all of it out of vanilla's own rules:

- **Below −64** the terrain density function is clamped hard positive, so the new depth generates as
  solid deepslate rather than air. The abyss is rock you have to go through.
- **Caves come with it.** The vanilla cave carvers start at `above_bottom: 8`, which is relative to
  the floor of the world — so with the floor at −256 they now carve from −248 upward. The deep caves
  of section 49 are not special generation; they are the ordinary carvers reaching the new bottom.
- **Bedrock** is placed by the same relative rule and lands at the new floor.
- **Ores stop at −64**, because vanilla ore placement uses absolute heights. Filling the abyss with
  something worth the trip is Milestone 6.

## Vertical zones

Sections 42 and 49 name the bands. `VerticalZone` is where they live, so pressure, heat, diagnostics
and everything the later milestones add cut the world at the same places instead of each carrying
its own magic numbers.

| Zone | From Y |
| --- | --- |
| Extreme altitude | 700 |
| High mountains | 400 |
| Alpine | 200 |
| Normal surface | 50 |
| Shallow caves | −20 |
| Deep caves | −100 |
| Lower caverns | −180 |
| Abyss | floor |

A zone knows only where it begins. What it *does* is a continuous curve, so a band boundary is a
name for a stretch of it and never a step a player can feel.

## Thin air

Section 43: altitude means less effective oxygen. It needed no new hazard, because Milestone 3
already models oxygen as a fraction of the air and everything downstream reads that one number — the
impaired and lethal thresholds, stamina, the safety lamp, the minimum a flame needs.

So the height changes the *outside air itself*:

```text
pressure halves every 960 blocks above sea level, and never falls below 0.20
outside oxygen = 20.9 % × pressure
```

| Y | Pressure | Outside O₂ | |
| --- | --- | --- | --- |
| 64 | 1.00 | 20.9 % | sea level |
| 300 | 0.84 | 17.6 % | slightly reduced |
| 600 | 0.68 | 14.2 % | low — below the impaired threshold |
| 900 | 0.55 | 11.5 % | very low |

Three consequences fall out of that without a line of special-case code:

- A shelter **built** at altitude contains thin air from the day it is closed. It does not start at
  sea-level air and leak, because there is nothing better outside it to leak from.
- **Airing a room out can only ever reach the air outside it.** Ventilation now exchanges with the
  air at the room's own height; opening a window at Y 800 does not produce sea-level air.
- Above roughly **Y 720 an open fire no longer holds**, by the same oxygen minimum a smoke-filled
  room is judged by. Warmth at extreme altitude is a problem the fire cannot solve.

## Geothermal heat

Section 47 gives the curve and the model follows it: nothing at the surface, about seven degrees at
Y −100, twenty at Y −200 and thirty at the floor, added to the biome temperature.

```text
surface   15 °C
Y -100    22 °C
Y -200    35 °C
Y -256    45 °C
```

It is added to the outdoor temperature, which is what an enclosed cell relaxes toward — so a deep
mine is hot because the rock around it is, not because of a separate rule for mines. Heat, bad
ventilation and carbon dioxide are then the same problem the environment model already knows how to
have.

Going the other way, the air cools with height: the ordinary lapse rate up to Y 320 and nearly
double that above it, where thin air holds heat badly.

## Wind

Section 45: wind grows with elevation. It did already, up to the old world ceiling. Past Y 320 an
altitude term is added on top of the weather, reaching a full gale at Y 720 — so extreme altitude is
windy whatever the sky is doing, and the wind feeds the heat loss and evaporation that were already
built on it. Below Y 320 nothing changed.

## Operator diagnostics

The environment channel gained the vertical reading, and `/hardwrought air` prints it for the player:

```text
ENVIRONMENT | extreme_altitude Y=812 (world -256..1023) | pressure=0.58 outside O2=12.11% | lapse=-7.6C geothermal=+0.0C gale=1.00 | too thin for an open fire
```

## Not in this milestone

- **Altitude adaptation (section 44) and deep adaptation (section 48).** Both are long-term player
  state that belongs with the vitals of the survival model, not with the world model.
- **Terrain that actually fills the new height.** Vanilla peaks stop a couple of hundred blocks above
  sea level, not a thousand. Everything above is open air that the altitude systems already treat
  correctly, but the ground does not go there yet. Real high mountains need density functions written
  for them, which is a change to make deliberately and to look at in a running world.
- **Anything worth finding in the abyss** — ores, giant caverns, special mobs, geological anomalies
  (sections 46 and 49). The space and its hazards exist; what is in it is geology.
- Oxygen equipment and high-altitude clothing (section 44), which are late-game technology.

## Verification

`gradlew.bat runGameTest` runs 104 server tests, six of them for this milestone.

The one that matters most asserts the world itself: the live test level really is 1280 blocks tall
from Y −256, **and** the loaded overworld noise settings state the same range. A dimension type that
is accepted while the generator still stops at Y −64 would leave a void under every world, and it
would not look any different from here without that second assertion.

The rest are the curves, which are pure arithmetic and need no world: the zone bands against the
numbers in sections 42 and 49, air thinning with height and never reaching vacuum, thin air crossing
the impaired threshold and the flame minimum where the specification says it should, the geothermal
curve against the three stated points, the lapse rate steepening above the knee, and the gale
appearing only above the alpine zone.

Two check the integration rather than the arithmetic: a room aired out at Y 900 settles at the thin
air outside it rather than at sea-level air, and the live environment really is more than ten degrees
warmer at Y −200 than at the surface of the same column, colder at Y 800, and windier up there.
