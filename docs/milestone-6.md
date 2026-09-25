# Milestone 6: Geology

Milestone 6 implements specification sections 51 to 54 as a layer **underneath** the blocks: the
ground is made of something, some of that ground is far richer than the rest, what a body is worth
varies inside it, and the way to find the rich ground is to read the rock.

Ore blocks themselves are placed by **ordinary world generation and are not touched**. An earlier
version of this milestone replaced vanilla's veins with generated deposits; it made a world that read
as empty, and it is gone. What is left does the same job without rearranging the world: strip mining
still finds ore, but it finds *poor* ore, and knowing where the rock is rich is worth more than
digging further.

## Rock regions

A region is 192 blocks square and is made of one rock. Which one is derived from the world seed, so
nothing is stored and the same place always answers the same way.

Geology is deliberately **independent of biomes**. What grows on the ground and what the ground is
made of are two different questions, and a granite massif does not stop being granite because a
forest grows on it.

`hardwrought/rock/*.json` says what each rock carries. The mapping follows real geology where vanilla
has an ore for it:

| Rock | Carries | Why |
| --- | --- | --- |
| Granite | iron, gold, emerald | the old hard core of a mountain; gold sits in its quartz veins |
| Volcanic | copper, redstone, diamond | where the deep came up — diamond pipes are volcanic |
| Sedimentary | coal, copper, lapis | laid down in water, which is the only place coal comes from |

Each entry states the ore, its weight, the depth band it can sit in, how large a body of it can be,
the range its grade falls in and which drill can reach it — so what a region is worth is a design
decision in a datapack, not a constant in the code.

## Rich ground

A region holds **two to five bodies**, each an ellipsoid: wide and flat, the way a seam lies. A body
is not where the ore is; it is where the ore is worth the effort.

```text
region 192 × 192
├─ rock type          from the seed
├─ 2–5 bodies         ore, place, size, depth and grade, all from the seed
└─ each body          20–128 blocks across, a third of that high
```

Roughly a third to a half of the ground has a rich patch under it somewhere — enough that prospecting
pays off regularly, little enough that finding a good one still means something. That is asked as a
number rather than a feeling: a test samples over four thousand columns and fails below a fifth or
above four fifths.

Each body is derived from the seed and the region and never stored, so the prospector reading the
rock, the block being broken in it and the diagnostics all compute the same thing from the same
numbers.

## Ore grade

Section 53: ore quality varies. A body has a core grade from its profile's range, and the rock assays
richest in the middle, falling to about a third of that at the rim.

```text
core   18.7 %   worth sinking a shaft for
rim     6.5 %   barely worth the pick
```

Grade pays in yield: breaking an ore block inside a body drops extra of whatever the block drops, one
more for every twelfth of grade, up to four extra. Ordinary ground pays exactly what vanilla pays; a
rich core pays several times over. **That is the whole reward loop** — the ore is in the same places
it always was, and knowing which of those places is rich is what a prospector sells.

Silk touch is excluded. It drops the ore block itself, and multiplying that would be a duplication
bug rather than a rich seam.

The processing chain of section 53 — crushing, screening, washing, concentration — is later
technology. Grade is the number it will act on when it exists.

## Drilling

A mine runs out; the rock does not. A drill works the whole thickness of ground under a chunk rather
than a seam, so it keeps producing — slowly, and only what that rock actually holds. That is the
endless supply the late game needs without turning a finite world into an infinite one: **the rate is
the limit, not the amount.**

What comes up is the geology of the chunk read back, not a table invented for the machine:

```text
rock of the region      decides which ores are in the ground at all, and in what proportion
a body under the chunk  weighs its own ore up fivefold, and the chunk works faster
the drill's tier        decides how much of that it can reach
```

Since then the drill has five tiers and a chunk-by-chunk mix of ores, and the machine exists; see
[ore-drill.md](ore-drill.md).

## Prospecting

Section 54, the early methods: the colour of the rock and the traces in it. **Sneak and use a pickaxe
on natural rock** — stone, dirt, sand, gravel or terracotta. A wall of bricks says nothing about the
ground it stands on.

| Tool | What it reads |
| --- | --- |
| Any pickaxe (`#hardwrought:prospecting_tools`) | the rock type, and traces of ore within 48 blocks |
| Iron and better (`#hardwrought:precise_prospecting_tools`) | rich ground within 160 blocks, with bearing, distance and depth |

Standing in rich ground it reports the ore and what the rock assays **here**, which is what tells a
miner whether this is the place to open a mine. Both tag lists are datapack files, so what counts as
a prospecting tool is tunable.

That is the progression the specification asks for: a flint pickaxe tells you what you are standing
on, an iron one tells you where to dig, and the prospecting hammer, drill cores and the chemical and
magnetic methods of section 54 come later.

## Operator diagnostics

The ore channel reported "not implemented" from Phase 1 on. It now reports the ground:

```mcfunction
/hardwrought debug on
/hardwrought status
```

```text
ORE | granite region | nearest rich minecraft:iron_ore ground 140 blocks away at Y 43 (core 19.4%)
ORE | volcanic region | rich in minecraft:copper_ore, grade 21.8% here (core 24.0%, 76 blocks across)
```

## Not in this milestone

- **The drill machine.** Built later; see [ore-drill.md](ore-drill.md).
- **Ore generation of our own.** Where ore blocks sit is vanilla's business again. If the rich ground
  should one day also *look* different — larger clusters, surface indicators — that is a worldgen
  change to make deliberately, and separately.
- **The processing chain** of section 53: crushing, screening, washing, concentration.
- **The later prospecting methods** of section 54 — hammer, geological samples, drill cores, chemical
  tests, magnetic and seismic surveys.
- **Oil, gas and salt** (sections 51 and 56), and the rock types that would carry them.

## Verification

`gradlew.bat runGameTest` runs 104 server tests, eight of them for this milestone.

They cover the model, which is pure arithmetic over a seed: a region being one rock and repeating,
bodies being few, large, inside their own region and true to the ore their rock carries, a grade that
falls from the middle outward and is zero outside, finding a body from nothing but a coordinate, the
shipped rock profiles loading and a bad depth band aborting the reload, the payout curve, and a first
drill bringing up iron while the same rock keeps its emerald for a better machine.

Two of them are about balance rather than correctness, because a model that is right and invisible is
still wrong in play: how much of the ground is rich, and that a drill's table really changes when it
stands over a body.

What the tests cannot show is how the rich ground reads when you are standing in it with a pickaxe.
That wants an eye in a real world.
