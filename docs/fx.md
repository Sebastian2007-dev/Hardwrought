# FX module

The groundwork for magic: light, particles and screen effects that go beyond vanilla. No library
does this on Minecraft 26.3 yet (Veil, Lodestone and Satin stop at 1.21.x), so Hardwrought has its
own module on top of Fabric's render events. It is written so a library could later take over
underneath it without the effects changing.

## What it draws

Every primitive is a quad whose look is computed in its own fragment shader; no textures are
involved. Light is additive and writes no depth, so overlapping effects brighten each other.

| Render type | Shader | What it is |
|---|---|---|
| `GLOW` | `fx_glow` | Soft point of light with a white-hot core and an optional four-pointed glint. Stretched along its motion, the same quad becomes a spark. |
| `RING` | `fx_ring` | Band of light on any plane: shockwaves, halos. |
| `BEAM` | `fx_beam` | Ribbon of streaming energy facing the camera; lightning bolts are made of it. |
| `RUNE` | `fx_rune` | Procedural magic circle: rings, glyph band, hexagram, inner triangle, each turning at its own pace, drawn stroke by stroke. |
| `SHIELD` | `fx_shield` | Sphere of force: bright rim, hex lattice, ripples. |
| `SMOKE` | `fx_smoke` | Soft noisy puff that covers instead of shining, lit by the light around it. |

Each type also has pipelines for improved transparency (OIT); vanilla switches to them whenever
that option is on.

## Engine

`FxEngine` (client only) simulates particles, beams, decals, lights and tasks once per tick;
`FxRenderer` interpolates them to the frame. Particles have colour and size over life, fade in
and out, drag, gravity, turbulence, attraction and swirl, and can bounce off blocks. At most
16 000 particles exist at once.

## Light

Effects cast real, coloured light, computed in the shaders rather than with light blocks: per
pixel on terrain, per vertex on mobs, items, block entities and particles. It moves smoothly with
its source, lights slabs, grass and water alike, and falls off softly to nothing at its radius.

Up to 32 lights per frame are appended to vanilla's global uniform buffer, which every world
pipeline already binds (`GlobalSettingsUniformMixin`, `minecraft:include/globals.glsl`); the ones
that matter most to the view (bright, large, near) win. To reach terrain, entities, items, block
entities and particles, their vanilla shaders are overridden in `assets/minecraft/shaders/core`
with the light added and nothing else changed.

Limits: the light has no shadows, so it shines through walls within its radius. Sodium replaces
the terrain shaders with its own, so with Sodium terrain would not receive it.

## Post-processing

`FxPost` runs at the end of the main pass, and only while something glows or bends:

1. Every light-type primitive is drawn again into an HDR target, depth-tested against the scene.
2. That target is blurred down five half-size levels and back up (dual Kawase): a wide, soft glow
   around the magic only, i.e. selective bloom.
3. Warp decals (`WARP_RING`, `WARP_HEAT`, `WARP_SWIRL`, `WARP_LENS`) and heat particles are drawn
   into a distortion field of screen offsets, scaled by how much of the view they cover.
4. The scene is composited through the distortion field, split into its colours where the air
   bends hardest, with the bloom laid over it. `FxScreen.aberration` and `FxScreen.saturation`
   add a brief split of the whole view and a burst of colour for big impacts.

`FxScreen` adds camera shake (only the camera turns, never the player), a flash, and a glow at the
edges of the view in the effect's colour. All of it fades with the distance to the effect.

## Presets and the server

The server never simulates an effect. `Fx.play(level, effect, pos, target, colour, scale, follow)`
sends an `FxPayload` to every player within 160 blocks; each client plays the named preset from
`FxPresets`. Presets: `nova`, `fireball`, `arcane_bolt`, `lightning`, `chain_lightning`, `beam`,
`rune_circle`, `vortex`, `frost_nova`, `shield`, `heal`, `sparkle`, plus `shake`, `flash`, `clear`
and `showcase`.

These are showpieces for trying the module out; the magic system will compose its own effects
from the same primitives.

## Commands

- `/hardwrought fx play <effect> [colour] [scale]` aims where the player looks: area effects at the
  block looked at, rays from the hand to it, self effects on the player.
- `/hardwrought fx at <pos> <effect> [colour] [scale]`
- `/hardwrought fx showcase` plays every effect in turn in front of the player.
- `/hardwrought fx list`, `/hardwrought fx clear`

A colour is a name (`fire`, `frost`, `arcane`, `holy`, `void`, `nature`, `blood`, `red` …) or six
hex digits such as `ff8800`.

## Not yet

- Shadows for the dynamic lights.
- Carried torches still light through light blocks (`DynamicLight`); they could use the shader
  lights too.
- Soft particles that fade where they cut into blocks.

## Verification

`FxClientGameTest` plays each effect at night and photographs it; the first frame also proves that
every pipeline compiles, since a broken one stops the game from loading its shaders.
