# Texture requirements

Every Hardwrought item that still needs its own artwork, and what that artwork has to be.

Items marked **placeholder** are fully implemented and playable right now: their model points at an
existing vanilla sprite so nothing is invisible or missing in game. They are only visually wrong.

## How a placeholder is replaced

1. Put the finished PNG at the path in the table, for example
   `src/main/resources/assets/hardwrought/textures/item/iron_dagger.png`.
2. In `src/main/resources/assets/hardwrought/models/item/<item>.json` change the single line
   `"layer0"` from the vanilla path to `hardwrought:item/<item>`.
3. Nothing else changes. No Java, no recipe, no data file.

Keep the generated source image next to the others in `art_source/` so a later re-export does not
have to start from nothing.

## Shared rules for every sprite

- Native **16x16** square canvas so the inventory renderer does not discard detail while scaling;
  crisp hard pixel edges, no anti-aliased blur, no resampling softness.
- Transparent background, object centred, no text, no border, no drop shadow outside the sprite.
- Minecraft inventory item style: a strong, immediately readable silhouette that still works at
  16x16 in a hotbar.
- Muted, earthy palette. Hardwrought is a hardcore survival conversion, not a bright fantasy set.
  Avoid saturated primary colours and glow effects unless the item actually emits light.
- Diagonal items point from the lower left to the upper right, matching vanilla tools.

## Open requirements

| Item | Texture path (`assets/hardwrought/textures/item/`) | Status | Currently shows |
| --- | --- | --- | --- |
| Flint dagger | `flint_dagger.png` | **done** | own texture |
| Iron dagger | `iron_dagger.png` | **done** | own texture |
| Iron greatsword | `iron_greatsword.png` | **done** | own texture |
| Iron halberd | `iron_halberd.png` | **done** | own texture |
| Safety lamp | `safety_lamp.png` | **done** | own texture |
| Flint shard | `flint_shard.png` | **done** | own texture |
| Filled waterskin | `filled_waterskin.png` | **done** | own texture |

The greatsword and the halberd should read as clearly **larger** than a vanilla iron sword at a
glance, because that is what distinguishes their weapon class in the hotbar. The two daggers should
read as clearly smaller.

## Per-item briefs

### Flint dagger

A knapped stone blade lashed to a short wooden handle. The earliest real weapon in the game, made
from two flint shards and a stick.

> Create a single Minecraft-style pixel art inventory item sprite: a primitive knapped flint dagger
> for a hardcore survival mod named Hardwrought. Short grey-brown chipped stone blade with visible
> conchoidal flake facets, lashed with dark sinew cord to a stubby worn wooden handle, crude
> handmade look, muted earthy palette, clearly smaller and cruder than a metal sword, strong
> readable silhouette, blade pointing to the upper right. Transparent background, object centred, no
> text, no border, no shadow outside the sprite, square canvas, crisp hard pixel edges, designed
> natively for 16x16 pixels.

### Iron dagger

A short, fast side arm. Narrow blade, simple crossguard, wrapped grip.

> Create a single Minecraft-style pixel art inventory item sprite: a short iron dagger for a hardcore
> survival mod named Hardwrought. Narrow tapering forged iron blade with a subtle central fuller,
> small plain crossguard, dark leather-wrapped grip, small round pommel, cool desaturated steel grey
> with muted brown leather, clearly shorter than a sword, strong readable silhouette, blade pointing
> to the upper right. Transparent background, object centred, no text, no border, no shadow outside
> the sprite, square canvas, crisp hard pixel edges, designed natively for 16x16 pixels.

### Iron greatsword

A two-handed sword. It must fill the sprite corner to corner so its size is obvious.

> Create a single Minecraft-style pixel art inventory item sprite: a two-handed iron greatsword for a
> hardcore survival mod named Hardwrought. Long broad straight forged blade spanning the full
> diagonal of the canvas, wide straight crossguard, long dark leather-wrapped two-handed grip, heavy
> disc pommel, cool desaturated steel grey with muted brown leather, visibly far larger and heavier
> than an ordinary sword, strong readable silhouette, blade pointing to the upper right. Transparent
> background, object centred, no text, no border, no shadow outside the sprite, square canvas, crisp
> hard pixel edges, designed natively for 16x16 pixels.

### Iron halberd

A polearm: a long shaft with a combined axe blade, spike and rear hook at the head.

> Create a single Minecraft-style pixel art inventory item sprite: an iron halberd polearm for a
> hardcore survival mod named Hardwrought. Long dark wooden shaft spanning the full diagonal of the
> canvas, iron head at the upper right combining a curved axe blade, a straight forward thrusting
> spike and a small rear hook, iron langets running down the shaft, cool desaturated steel grey with
> dark weathered wood, clearly the longest weapon in the set, strong readable silhouette. Transparent
> background, object centred, no text, no border, no shadow outside the sprite, square canvas, crisp
> hard pixel edges, designed natively for 16x16 pixels.

### Safety lamp

A miner's flame safety lamp. It is the instrument that makes bad air readable, so the gauze cylinder
around the flame is the feature that has to read at a glance.

> Create a single Minecraft-style pixel art inventory item sprite: a miner's brass flame safety lamp
> for a hardcore survival mod named Hardwrought. Upright cylindrical lamp with a heavy oil reservoir
> base, a glass window showing a small warm flame, a distinctive fine wire gauze cylinder above the
> glass, a protective iron cage of vertical bars and a curved carrying hook on top, aged brass and
> dark iron with a single small warm flame highlight, muted earthy palette, sooty and used, strong
> readable silhouette. Transparent background, object centred, no text, no border, no shadow outside
> the sprite, square canvas, crisp hard pixel edges, designed natively for 16x16 pixels.

### Flint shard

Still on the vanilla flint sprite from Milestone 0. It should read as a thin struck flake rather than
a rounded lump, so it is distinguishable from vanilla flint in the inventory.

> Create a single Minecraft-style pixel art inventory item sprite: a sharp knapped flint shard for a
> hardcore survival mod named Hardwrought. Thin angular struck stone flake with a razor edge and
> visible conchoidal fracture facets, dark grey-brown with a paler fractured face, clearly flatter
> and sharper than a rounded flint nodule, muted earthy palette, strong readable silhouette.
> Transparent background, object centred, no text, no border, no shadow outside the sprite, square
> canvas, crisp hard pixel edges, designed natively for 16x16 pixels.

## Not yet required

These are named in the specification but have no item in the code yet, so they need no artwork until
the milestone that adds them:

- War hammer, poleaxe, glaive, throwing weapons (specification section 30, listed there as later).
- Buckler and tower shield (section 31); only the vanilla shield has a profile so far.
- Cloth armor (section 36); there is no cloth armor item.
- Lantern, oil lamp, gas lamp and electric lamp of the lighting progression (section 20).
- Ventilation shafts, mechanical fans and gas detectors (sections 18.3 and 19).
