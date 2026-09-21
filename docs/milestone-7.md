# Milestone 7: Early Progression

Milestone 7 implements specification section 71 and its sub-sections: the world stops giving way to
fists. What a player can take out of the ground, the timber and the stone now depends on what they
are holding, and the first tools matter from the first minutes.

Section 71.1 states the purpose, and it is not gatekeeping: *the goal is to make the physical
interaction with the world believable and to make primitive tools important*.

## Tool-gated breaking

Vanilla lets almost every block be broken by hand given enough time. Hardwrought separates three
questions vanilla answers together — whether the block breaks at all, what it costs, and whether
anything usable comes out (section 71.1.10):

| Verdict | Speed | Stamina | Yield |
| --- | --- | --- | --- |
| **Proper** — the right tool | normal | normal | normal drops |
| **Loose** — plants, fibre, snow (71.1.1) | normal | half | normal drops |
| **Diggable** — soil by hand (71.1.2) | ×0.30 | ×4 | a handful of loose ground |
| **Improvised** — a tool, but the wrong one | ×0.25 | ×3 | often ruined, and hard on the tool |
| **Shatters** — glass by a fist (71.1.7) | normal | normal | nothing |
| **Impossible** — bare hands on solid material | **nothing happens** | — | — |

The classification is read off tags, and mostly off vanilla's own: a block that wants a pickaxe or an
axe is solid material — stone, ore, timber, and anything built out of them (71.1.3 to 71.1.6). Three
small Hardwrought tags cover the rest: `hand_gatherable`, `hand_diggable` and `shatters_by_hand`.

Whether the held item is the *right kind* of tool is asked of the item: anything that mines this
block faster than a fist would has an affinity for it. That works for tools this mod never heard of,
and it leaves the tier question with vanilla, which already refuses the drops of a block mined with
too soft a tool.

The speed rule lives in a mixin on the player rather than in a server event, because the client
computes the same number to draw the cracks. A block that visibly breaks on the client and then does
not break on the server would be worse than no rule at all.

Hitting a block that will not move says so in the action bar. A rule that silently does nothing reads
as a bug.

## Earth and stone come apart into pieces

Dirt, grass and stone no longer drop a clean block. They break into the loose material they are made
of, and a block is something a player puts back together:

```text
dirt   → 4 dirt blobs        4 blobs → 1 dirt        2 blobs → 1 dirt slab
stone  → 4 cobblestone pieces   4 pieces → 1 cobblestone
```

Dug **by hand** the yield is worse still: a couple of blobs, and not every time. Silk touch is the
exception that lifts the whole block, which is also how it stays useful.

## Fire, fibre and the first edge

Section 71.1.8 is a hard requirement: tool rules must never be able to strand a player. The chain
back up from nothing is therefore made of things bare hands can always reach.

```text
leaves      → sticks, and fibre for binding
gravel      → flint shards and loose stones
flint + stick + fibre    → the first hatchet, in the 2×2 grid every player carries
```

**Every tool needed to reach a workbench fits the 2×2 grid.** That is not a detail: the workbench
itself is gated (below), so a recipe that needed one to make the tool that makes it would lock the
game shut.

## Timber is worked, not punched

Section 71.1.3: a log does not fall apart when it is hit enough times, and it is not broken by hand
at all. Boards come from the ordinary recipe; what a log gives up to an axe held against it is a
workbench, and nothing else.

## Flint is a beginning, not a shortcut

Flint tools are deliberately worse than wood at mining: slower than a wooden pickaxe, and about as
fragile as gold. They exist to open the first door, not to stay useful.

## The first workbench is hewn out of a log

A workbench is not four boards in a square. **Crouch and hold an iron hatchet against a standing
log** and keep working: each repetition of the interaction is one stroke, every stroke costs stamina,
and after enough of them the log *becomes* a workbench where it stands. Strokes are remembered per
player and forgotten after five seconds, so a bench cannot be cut across three sessions.

It looks like what it is, in three layers rather than two: bark at the bottom, a band of the squared
wood of the same tree above it, and the worked face at the top. A single line between bark and
workbench read as two blocks glued together; the stripped band in between is the cut itself. **The
wood travels with the block** — a birch bench keeps birch bark, a crimson stem keeps its stem —
through breaking and placing it again.

Only a tool in the `hardwrought:crafting_tools` tag can cut a flat working surface out of round
timber; a knapped flint edge cannot. That tag is where the saws, chisels and hammers of the later
technology tree will go.

**Crouching against a log never strips it**, whatever is in hand. Bark coming off halfway through a
cut reads as a bug, and stripping is what the same axe does when the player is not crouching.

That rule runs on **both sides** on purpose. The client predicts an interaction before the server
answers, so a rule that only existed on the server stripped the log locally and then visibly snapped
it back — which is worse than either outcome on its own.

The ordinary crafting table is still craftable, but it is joinery: **six boards, three wide**, so it
needs a bench to make a bench. The first one always comes out of a tree.

### Tools in recipes are used, not used up

A tool an ingredient list asks for comes back out of the grid one point of wear worse, and only
disappears when it finally wears through. Nothing needs it yet now that the workbench is hewn rather
than crafted, but the rule is in place for the recipes that will.

## The fire pit closes the chain

Gating the workbench behind iron would lock the game shut on its own: iron needs smelting, a furnace
needs a 3×3 grid, and a 3×3 grid needs a workbench. So the first smelting happens before any of that.

A **campfire is a fire pit**, and it is scraped together from two sticks and two split logs in the
2×2 grid. Ore laid in it becomes metal — slowly, one piece at a time, a full minute each:

```text
raw iron, raw copper, raw gold, cassiterite   → ingots, 1200 ticks apiece
```

That is section 55 at its most primitive: it works, it is miserable, and the furnace that replaces it
is worth building the moment there is a workbench to build one on.

```text
leaves + gravel → flint hatchet (2×2)
    → worked logs → boards and sticks
    → fire pit (2×2) → first metal
    → iron hatchet (2×2) → workbench → furnace, and everything after it
```

## The metals

Section 55 names fifteen metals beyond the ones vanilla has, and section 56 asks for alloys made of
them. All fifteen are in the ground, each with an ore in stone, the same ore in deepslate, a raw
material and an ingot.

They come out of **one table** — `metallurgy/Metal.java` — and everything else is derived from it:
the blocks, the items, the models, the loot tables, the smelting recipes, the tags that decide which
pickaxe bites, the ore generation and the rock that carries them. Adding a metal is one line and a
texture.

| Metal | Band | Veins × size | Pickaxe | Drill | Rock |
| --- | --- | --- | --- | --- | --- |
| tin | -40 … 90 | 6 × 8 | stone | 1 | sedimentary |
| zinc | -40 … 80 | 5 × 8 | stone | 1 | sedimentary |
| lead | -60 … 60 | 5 × 8 | stone | 1 | sedimentary |
| manganese | -50 … 60 | 4 × 7 | stone | 2 | sedimentary |
| magnesium | -30 … 70 | 4 × 7 | stone | 2 | sedimentary |
| aluminum | 20 … 130 | 5 × 9 | stone | 2 | sedimentary |
| nickel | -80 … 20 | 3 × 6 | iron | 2 | volcanic |
| cobalt | -100 … 0 | 3 × 5 | iron | 2 | volcanic |
| chromium | -120 … -20 | 3 × 5 | iron | 2 | volcanic |
| mercury | -120 … 0 | 2 × 4 | iron | 3 | volcanic |
| titanium | -140 … -20 | 2 × 5 | iron | 3 | granite |
| tungsten | -200 … -60 | 2 × 4 | diamond | 3 | granite |
| uranium | -200 … -40 | 2 × 4 | iron | 3 | granite |
| thorium | -220 … -80 | 2 × 4 | iron | 3 | granite |
| platinum | -220 … -100 | 1 × 3 | diamond | 3 | volcanic |

The depth bands follow real geology as far as Minecraft leaves room for it: bauxite weathers near the
surface, lead and zinc travel together through sedimentary rock, nickel and the platinum group sit in
what came up from the deep, tungsten belongs to granite, and the radioactive metals are deepest and
rarest.

**Nothing vanilla was taken away.** These are additions, placed in their own bands; every vanilla
vein is still exactly where it was. That is a deliberate correction of an earlier attempt at
replacing vanilla ore generation, which made a world that read as empty.

Three things follow from the same table without being written twice:

- **Hardness.** A stone pickaxe is enough for tin, zinc and lead; nickel and everything around it
  wants iron; tungsten and platinum want diamond.
- **The fire pit.** Only the metals the early game may reach — the bronze-age ones — smelt over a
  campfire. Everything else waits for a furnace, which waits for a workbench.
- **Geology.** Every metal is written into the rock profile of Milestone 6, so a prospector finds
  rich ground for it and a drill of the right tier brings it up.

## Copper, tin and bronze

Copper tools are vanilla's own, so the copper tier needed nothing but a way to smelt the ingot. Above
it sits bronze, and bronze needs tin.

Tin has an ore of its own like every other metal, and it has a second source besides: cassiterite is
washed out of **river gravel**, one grain in twenty, which is where the bronze age actually got most
of it. Every player digging gravel for flint is already prospecting for tin without knowing it.

```text
3 copper ingots + 1 tin ingot  → 4 bronze mixture     (2×2, no workbench)
bronze mixture                 → bronze ingot          (fire pit or furnace)
```

Bronze sits where it belongs: harder than copper, a little short of iron, and the first metal worth
replacing a stone edge with.

| | Durability | Mining speed |
| --- | --- | --- |
| Flint | 32 | 1.25 — slower than wood |
| Bronze | 375 | 6.0 |
| Iron (vanilla) | 250 | 6.0 |

The items are registered and craftable; **their textures are placeholders on vanilla art** and are
listed in `textureRequirements.md`.

## Not in this milestone

- **Bronze weapons and armour.** The tier exists as tools; the combat profiles of Milestone 2 have
  no bronze entry yet.
- **A real bloomery.** The fire pit is deliberately crude — one slot, one minute, no efficiency. The
  smelting chain of section 53 and the furnaces that follow it are later technology.
- Different hardness per rock type (71.1.4) and per ore host rock (71.1.5): the tier rule is
  vanilla's, and the geology of Milestone 6 is not yet consulted for it.
- Cut injuries from breaking glass by hand (71.1.7).
- Hand-powered technology (section 72) — grinder, mortar, crank, drill.

## Verification

`gradlew.bat runGameTest` runs 115 server tests, eleven of them for this milestone.

They cover the rule rather than the wiring, because the rule is the milestone: fists doing nothing to
stone, timber, ore, planks, brick and metal; the right tool being the one that works and the wrong
one being slower, costlier and harder on itself; soil and growth staying within reach of hands; glass
breaking without being harvested; flint being slower than wood and as fragile as gold; earth and
stone dropping only their parts, with silk touch lifting the whole block; a log taking more strokes
with a flint edge than with iron and none at all with a fist; a workbench being hewable only with a
joiner's hatchet and only for more work than splitting the same log; the ordinary crafting table no
longer fitting the grid a player carries; and a tool in a recipe coming back worn rather than
consumed.

Two of them walk the chain out of nothing — leaves, gravel, flint, the first hatchet, the fire pit,
the first metal — and assert that **every step of it fits the 2×2 grid**, because section 71.1.8 is
the one rule here that must never be wrong and a workbench gated behind iron is exactly how it would
be broken.

Five cover the metals: every one of them having an ore, a raw material and an ingot; the hardness
bands really differing, so a stone pick takes tin and not nickel and iron does not touch tungsten;
every ore feature loading and reaching an overworld biome **while vanilla's veins stay untouched**;
the fire pit smelting only what the early game may reach; and the rock profiles carrying each metal
at the drill tier its own table states.

One more covers the stamina threshold: being worn out costs a player nothing until the last four
drops of the bar are all that is left. A penalty that starts at the first missing point means every
player is permanently slightly slowed for no reason they can see.
