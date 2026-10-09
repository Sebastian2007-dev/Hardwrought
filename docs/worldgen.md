# The shape of the overworld

The world is 1280 blocks tall (see [milestone-5.md](milestone-5.md)), and at first it was vanilla's
land in a taller box: mountains stopped at 256, everything below -64 was solid rock with only the
tunnel carvers through it, and every one of those tunnels was full of lava. This is what was changed.

> **Only chunks generated from now on have the new shape.** In a world that already exists the old
> chunks stay as they are, and where new ones meet them there is a seam: a cliff, or a cave cut off.
> A new world is the clean way to see it.

All of it is vanilla's own terrain, read out of the game and patched by `tools/worldgen_assets.py`, so
an update of the game's terrain is taken over by running the script again.

## Mountains

- **The land may be as high as the world.** Vanilla lets the land thin out to nothing between 240 and
  256 and measures depth only up to 320. Both are moved to the top (1024); the land fades out over the
  last 32 blocks under it.
- **Great ranges.** One very slow noise (`hardwrought:mountain_range`, a first wave 4096 blocks long)
  says where they stand: massifs a few kilometres across, far apart. In a range
  - the land is told to be inland and unworn, so vanilla builds its own mountains there, with its own
    jagged peaks and its own mountain biomes, and
  - an uplift of up to 5.8 of vanilla's units (about 740 blocks) is added under them, growing with the
    square of the range's strength.
- **Why they do not look like towers.** Nothing quick goes into the uplift. A first version hung it on
  vanilla's map of how far inland a place is, which can go from coast to deep inland in four hundred
  blocks, and the range stood up as a wall 800 blocks high. Driven by the slow noise alone, a range
  rises over one to two kilometres.
- A range that stands in the sea comes up out of it as a mountainous island.

Measured over 40 km square (one seed, by the terrain functions, before the jagged peaks are added):
highest land 914; half the land below 66; 35 % under the sea; 17 % above 150; 6.5 % above 320;
0.55 % above 700. With their peaks the highest summits come to between 900 and 980.

Thin air (milestone 5) makes those heights matter: at 900 the outside air has 11.5 % oxygen.

### Finding one

`/hardwrought locate mountain [height]` (game master) gives the nearest place where the land stands at
least that high, 900 where no height is given, within 48 000 blocks. It reads the terrain function
and generates nothing, so it answers at once. The answer is the top of the massif the search hit, with
a teleport that can be clicked into the chat line; the teleport is set 90 blocks above the figure,
because the peaks stand higher than the land they are carved from.

## The deep

- **Caves of every kind, all the way down.** The solid floor vanilla puts at -64 is moved to the
  bottom of this world (solid from -248 down), so the cheese and spaghetti caves and their pillars
  are everywhere below, and the thin noodle tunnels go on to -244.
- **The lower caverns** (about -195 to -85, widest at -140): halls some tens of blocks across. About a
  quarter of the rock is open at the middle of the layer.
- **The abyss** (about -243 to -187): one wide, low, broken hall. Over 40 % is open at its middle.
- Between the two layers, and above them, the rock is as open as vanilla's caves are (7 to 9 %).
- **Lava only under the abyss.** Vanilla fills whatever is open below -54 with lava; with the floor at
  -256 that was every deep cave. The level is now -236, twenty blocks above the floor
  (`worldgen/DeepWorld`, moved by `mixin/NoiseBasedChunkGeneratorLavaMixin` because it is a number in
  the game's code). The abyss has a sea of lava under it. Higher up lava still gathers in pockets
  where vanilla's aquifers put it, and so does water: a cave may be dry, flooded, or have a lava lake
  in it.

## No place to stay

The heights and the depths wear a body down for as long as it is in them. The measure is the strain
in the vitals panel (H), counted to a hundred; it is the same measure a restless sleep adds to.

| Height | What wears | Strain a second | Minutes from rested to failing |
|---|---|---|---|
| 950 | thin air | 0.18 | 9 |
| 900 | thin air | 0.17 | 10 |
| 600 | thin air | 0.07 | 25 |
| up to about 400 | nothing | 0 | |
| down to about -32 | nothing | 0 | |
| -100 | pressure | 0.07 | 23 |
| -200 | pressure | 0.18 | 9 |
| -256 | pressure | 0.24 | 7 |

- **Thin air** is the oxygen model there already was: strain grows with how far the air breathed is
  short of oxygen, so a sealed hut on a summit is no help (its air is the summit's), and bad air in a
  mine wears as thin air does.
- **Pressure** is new: the deep presses one more atmosphere for every 160 blocks below sea level,
  three at the floor. A body bears 1.6 (about Y -32) without harm.
- At 50 and at 75 the player is told. At 100 the body fails: a point of health every four seconds,
  past armor, for as long as it stays. Leaving stops the damage at once.
- Out of the heights and the deep the strain goes down again, awake, by 0.03 a second: from failing to
  rested takes most of an hour. Two trips to the abyss in quick succession are one long trip.
- With thin air on top come what it did before: stamina that drains and comes back slowly, and no
  open fire above about 720.

### Getting used to it

A body gets used to thin air and to pressure, each by itself, by being in them (specification sections
44 and 48). The vitals panel (H) shows both, in per cent, under the strain.

- **What it gives**: up to 70 % of the strain taken away. Fully used to it, the summits are half an
  hour instead of ten minutes and the floor of the world 23 minutes instead of seven. It is never all
  of it: the extremes stay places to come back from.
- **How it grows**: only while the body is under that stress. In the worst of it, fully after two and a
  half hours there, which is many visits; under a little of it more slowly (at height 600 about half
  as fast), which is how living half way up a mountain prepares for its summit. On a diet that is not
  balanced it grows half as fast.
- **How it fades**: whenever the body is out of that stress, all of it in eight hours away.
- The two are separate: being used to the heights is no help in the deep. Bad air in a mine counts as
  thin air does, so miners get used to it too.

| Used to it | Minutes at 900 | Minutes at -256 |
|---|---|---|
| not at all | 10 | 7 |
| half | 15 | 11 |
| fully | 33 | 23 |

## Where it lives

- `tools/worldgen_assets.py`: writes the density functions and noises.
- `data/minecraft/worldgen/density_function/overworld/`: `depth`, `offset`, `continents`, `erosion`,
  `preliminary_surface_level`, `final_density`, `caves/noodle`, vanilla's with the changes above.
- `data/hardwrought/worldgen/density_function/overworld/`: `mountain_range`, `mountain_uplift`,
  `caves/deep_caverns`; `data/hardwrought/worldgen/noise/`: `mountain_range`, `deep_cavern`, `abyss`.
- `worldgen/DeepWorld`, `mixin/NoiseBasedChunkGeneratorLavaMixin`: the lava level.

## Verification

- `WorldgenGameTests`: reads the terrain functions over 40 km square and through six layers of the
  deep, and asserts the figures above within bounds: a summit of 900 or more somewhere, giants rare,
  most land unchanged, the two cavern layers open and the rock between them less so, a solid floor.
- With `HARDWROUGHT_SLICE` set to a file name, the same test draws the world cut open through its
  highest mountain, floor to top, as a picture. That is how the towers were found.
- Not verified: a generated world walked through in the game. The functions are what the generator
  uses, but surface blocks, snow lines, trees at height and how the new caves meet the old carver
  tunnels have only been reasoned about.
