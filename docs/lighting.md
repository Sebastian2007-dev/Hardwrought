# Lighting

Sections 20 to 22 of the specification: true darkness, moonlight that depends on the phase, eyes that
adapt, and carried light that everyone can see. Milestone 3 implemented only the carried torch of the
local player; this document covers the rest of the vision side. The lamps of the lighting progression
follow separately.

Everything here changes **only what a client draws**. The light engine, mob spawning, plant growth and
every other rule keep reading the real light levels, so two players can disagree about how well they
see, never about what the world is.

## True darkness

Vanilla gives the overworld an ambient glow (`#0a0a0a`) and lets the brightness slider lift it further,
so an unlit cave is never black. That glow is removed: where there is neither sky nor block light,
nothing is visible until the eyes have adapted.

Dimensions whose ambient light is brighter than `0.05` — the Nether, the End, and any datapack
dimension meant to glow — keep their own ambient light.

## Moonlight

The night part of the sky light factor is scaled by the moon phase. The day keeps its full sky light.

| Phase | Share of vanilla night sky light |
|---|---|
| Full moon | 100 % |
| Gibbous | 60 % |
| Quarter | 30 % |
| Crescent | 10 % |
| New moon | 0 % |

Cloud cover hides the moon: in full rain only 30 % of that share is left. The transition follows the
keyframes of vanilla's own `sky_light_factor` track, so dusk and dawn stay where they were.

## Dark adaptation

The light reaching the eyes is measured the way the lightmap shader adds it up. Below `0.02` the eyes
adapt fully, above `0.2` not at all. They open up with a time constant of 12 s — little after 2 s,
most of the way after 30 s — and close again within about a second in bright light. Lighting a torch
therefore costs the night vision you had built up.

Adapted vision is dim and bluish. What it shows is the same whatever the brightness slider says: the
ambient value is solved against the shader's brightness curve, so the slider still brightens lit places
but can no longer turn a moonless night into dusk.

## Shared carried light

Every client lights the torches it can see being carried: its own player's, other players', and those
of mobs holding one. Nothing is sent over the network; each client places the light blocks in its own
copy of the world only, as before. At most 24 carriers within 48 blocks are lit, the nearest first,
because each moving light costs a light-engine update. Invisible carriers and spectators do not light.

`dynamicLight=false` in `config/hardwrought.properties` still turns carried light off.

## Not yet

- The lamps of the progression: candle, oil lamp (lamp oil from Milestone 11), gas lamp, and
  electric lamp (Milestone 13). Fuel and burn time belong to them.
- Carried light for dropped items and burning entities.
- Darkness as a gameplay input, for example mobs that react to moonless nights.

## Verification

`CoreClientGameTest.verifyDarkness` sets a clear new-moon midnight, checks that the moon scales only
the night sky, that the eyes are still mostly unadapted after 2 s and mostly adapted after 15 s, and
that a torch in hand undoes the adaptation. It takes the screenshot `hardwrought-lighting-new-moon`.
