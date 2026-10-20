# The Underworld – Design

The third goal of the [endgame roadmap](endgame.md): a dimension of its own beneath the world, and the
Witch King at the bottom of it. This is the plan to build from; what is marked *open* is still to decide.

## The idea in one line

A cave dimension like the Nether, but cold and silent instead of loud and hot: almost no light, almost
no life, and always the feeling of being watched.

## Getting there: through the floor of the abyss

The Underworld lies under the world, so the way in is down.

1. Reach the **abyss** (−243 to −187), the deepest hall of the Overworld, with its lava sea and the
   bedrock under it.
2. Build the **Underworld seal** on the bedrock floor: a **seal stone** (chiseled deepslate, gold and
   four **echo shards** from the ancient cities of the deep dark — the first echo of what lies below),
   with four **soul lanterns** on deepslate pillars around it, and set the **nether star** into the
   stone.
3. The star burns out, the bedrock cracks outward from the seal over a few seconds — cracks glowing
   soul-blue, the ground shaking, deep groaning — and opens into a **shaft** of darkness.
4. Stepping into the shaft is the way down: a fall through black, souls rushing upwards past the
   player, and landing in the Underworld.

**The seal stays open** once made: a permanent way down, for everyone on the server, marked by the
cracked, glowing bedrock around it.

**The way back up** is not the same hole. In the Underworld, some ruins hold an **ascent shaft**: a
column of faint pale light rising into the rock, gone dim. Rekindled with a **pale shard** — a
crystal that grows only down here (see below) — it carries the player up into the abyss near where
they went down. The first trip down is therefore a commitment: the way home has to be found, and
what lights it has to be found down there too.

**Pale shards** grow as small, faintly glowing crystal clusters on the walls of the Stone Wastes and
the Echo Chasm, rarely, and lie in some ruins. They give a little light where they grow, one of the
few lights there are.

## The world down there

A cave dimension with a rock roof and floor, like the Nether, but taller and wider: great halls,
narrow passages between them, chasms. **No sky, no day, no weather.** Cold — around 8 °C — and damp.

### Biomes

| Biome | What it is |
|---|---|
| **Stone Wastes** | Bare stone and deepslate as far as the light reaches, and nothing else — but ore: very common, and in this biome ore also lies **open on the cave walls** (no rule against generating exposed to air), so it glints at the edge of the light. The richest place in the game for metal, and the emptiest. |
| **Overgrown Halls** | Like lush caves, but grown wild: azalea, hanging roots, clay, spore blossoms, dripleaf over still water. The only green down here, and the only food. |
| **Dripstone Depths** | Dangerous: dripstone from floor and ceiling, dense, and stalactites that **break loose on their own** now and then and fall — a crack, a moment, a crash. Pointed dripstone on the floor wounds what falls on it. |
| **Mossy Grove** | Moss over everything, quiet and soft, with **glow berries** hanging from the roof: the only natural light worth the name, a pale green. A place to rest — the safest biome. |
| **Graveyard** | The only place anything spawns. Old graves, gravestones, rotting wood, soul soil, bones in the ground, iron fences fallen over. The undead live here, and come out of the ground. |
| **Flooded Halls** | An underground lake, black and still: stagnant water to the walls, islands of rock, the roof reflected in it. Water down here is never clean. |
| **Echo Chasm** | A chasm too wide and deep to see across, narrow ledges along its walls, and every sound coming back twice, from far off. Pale shards grow here. |
| **Deep Dark** | Vanilla's own: sculk spreading over everything, sensors, shriekers — and the **ancient cities**, which generate here too. The one place where something answers noise: the Warden still rises when the shriekers call it. |
| **Ossuary** | The Graveyard's deeper part: halls whose walls are bone, skulls stacked to the roof, narrow passages. It leads down to the Witch King. |

### Ancient cities

The ancient cities of the deep dark generate in the Underworld's Deep Dark as they do in the Overworld,
with their loot (echo shards among it). Their structure is placed at a fixed depth in vanilla, so the
Underworld gets its own placement of the same city, set at the depth its Deep Dark lies at.

### Light

Light is **rare**. Apart from glow berries in the Mossy Grove there is almost none: a few glowing
lichen patches, a faint glow from some ores. Torches burn shorter here (the mod's burning lights,
faster in the damp cold), so light is a resource to carry and to ration.

### Sound

The ambience down here is the most important thing about the place.

- **Mostly silence.** No mob noise outside the Graveyard.
- **Cave ambience**: dripping water, distant settling rock, a low wind through passages, the deep
  groan of the earth now and then.
- **Real reverb.** Minecraft's sound engine plays everything dry. Underneath it is OpenAL, whose
  effects extension (EFX) has reverb; the mod hooks into the sound engine on the client and sends
  every sound in the Underworld through a reverb whose size follows the space the player is in:
  - measured around the player — how far the rock is in every direction — so a narrow passage is
    tight and close, a great hall long and booming;
  - by biome on top: the Echo Chasm with a long, distinct echo (every sound coming back twice), the
    Flooded Halls wet and ringing over the water, the Mossy Grove soft and damped by the moss;
  - and a little muffling of sounds behind rock, so what is around a corner sounds around a corner.
  Mods have done this before (Sound Physics), so it is possible; it is client-side only. Where a
  system's sound driver lacks the extension, the echo falls back to delayed, quieter copies.

### Mobs

They spawn **only in the Graveyard**: zombies and skeletons, in older, rotting variants, rising out of
the soul soil. Everywhere else the Underworld is empty — and that is what makes it uneasy. The one
exception is the **Warden** in the Deep Dark, which does not spawn but is called up by shriekers, as
in vanilla.

## Unease

The atmosphere should be eerie and unsettling, never comfortable:

- **Watching eyes** — pairs of faint glowing points in the dark, at the edge of the light. They do not
  move while looked at. Look away and back, and they are somewhere else, or gone. Come close and they
  close. (A rare block-like apparition, not a mob: harmless, and never explained.)
- **Footsteps that are not yours** — stopping when you stop, a beat late.
- **Breathing** close by, when standing still in the dark for long.
- **A torch going out** behind you, by itself, now and then.
- **Something falling** in the distance — a stone, a stalactite — in a hall you have not been to.
- **Whispers** at the very edge of hearing, in the Graveyard and near ruins.
- **The compass and the clock spin** — there is no north and no time down here.

None of it hurts. All of it is there to be noticed.

## Ruins

Scattered through every biome, half fallen in: the remains of those who came down before.

- **Small ruins**: a few walls, a collapsed roof, a cold fireplace, a chest.
- **Camps**: a bedroll, a burnt-out campfire, a few barrels, old rope.
- **Watchtowers**: taller ruins at chasm edges, a lookout over a hall.
- **Ascent-shaft ruins**: the way home (see above), rarer, and worth finding.

**Loot** — little, and worn: water bottles (bad water), rotten food, bread gone stale, iron and at most
steel tools, **worn** (half their durability or less), candles, torches, rope, flint, bandages.
And **journals**: pages from those who came before — what they saw, the eyes, the Graveyard, and,
deeper in, the Witch King — a trail of lore that also points the way.

## The Witch King

At the bottom: the **Witch King's citadel**, reached through the Graveyard and the Ossuary below it.
His fight is the third goal and gives the **way to fly** (endgame § 4).

The fight ([WitchKing](../src/main/java/de/ipnats/hardwrought/witchking/WitchKing.java)) has three phases: *elusive*
(hovering, slipping away through rifts; swords in volleys, fans and rings that close), *warded* (behind a ward over the
middle of the hall until four soul crystals are broken; sword rain, hunting souls, his dead, a lance shown as a line of
light before it strikes, the floor breaking open along spokes; every broken crystal makes him quicker and tears the floor
open round whoever broke it) and *unbound* (a giant on the floor; blows wound up over a mark and struck up to three in a
row, leaps across the hall that land in a shockwave, lines of eruptions racing towards the target, rifts behind it;
below 30 % he rages and everything comes faster). A curse over the whole hall falls again and again from phase 2 on;
only a magic shield stops it. He chooses each move by weight from what suits the distance and the fight, never the
same twice in a row, and everything aimed is marked before it strikes.

His harm is its own damage type, `hardwrought:hex`: armour does not hold it (half of it against his blows), and
protective runes count only half, at most 40 % instead of 80 %.

**The way he leaves open** ([ThronePassage](../src/main/java/de/ipnats/hardwrought/witchking/ThronePassage.java)): beaten,
his throne goes cold and stays a way out and back in for good. Sitting on it (use it; two seconds of souls gathering,
sneak to get up before) carries one up to its **seat** in the overworld, the Witch King's seat; the first time, or when
its seat is no longer standing, the seat is set down beside where the player sleeps (the world's spawn without a bed in
the overworld). Sitting on the seat carries one down to the foot of the throne, which from then on leads back up to that
seat. The seat cannot be broken; sneaking with empty hands picks it up, still bound to its throne, to set down elsewhere.

## Built so far

**Stage 1 — the dimension** ([Underworld](../src/main/java/de/ipnats/hardwrought/underworld/Underworld.java), data under
`data/hardwrought/{dimension,dimension_type,worldgen}`): `hardwrought:underworld`, y -64 to 256, bedrock floor and roof,
no sky, no time, no light of its own, no fog (black where unlit, like a moonless night: only light decides what is seen), cave mood sounds and otherwise silence, about 6 °C (warmer deeper,
as the rock warms). Taller halls than the Nether's. Stone, deepslate below y 0. All nine biomes, each with its own ground
(bone and calcite in the Ossuary, moss in the Mossy Grove, soul soil and podzol in the Graveyard, mud and clay under the
Flooded Halls' lakes, deepslate in the Echo Chasm, banded rock in the Stone Wastes) and features (dense ore open to the
air in the Stone Wastes, lush cave growth, dripstone, glow berries, sculk). The Echo Chasm's chasms are cut with ledges;
the Flooded Halls stand in stagnant water up to y 8, walled off with rock at the biome's edge so it never spills (no
other water down here); pale shards grow on the walls of the Echo Chasm, and in the Stone Wastes only in one chunk in eight (and no glow lichen
there), so the Wastes are truly dark.
Monsters spawn only in the Graveyard and the Ossuary. The Deep Dark is vanilla's own, ancient cities with it.

No dark adaptation down here: without a light it stays black however long one waits; only what glows is seen.

**Stage 2 — the way in and out** ([UnderworldGate](../src/main/java/de/ipnats/hardwrought/underworld/UnderworldGate.java)):
the seal stone (echo shards, gold, chiseled deepslate) between four pillars of worked deepslate two blocks out at the
corners, each with a soul lantern, anywhere in the overworld below y -180; in the compendium as the multiblock "Underworld
Seal". A nether star on the stone opens it over five seconds: cracking rings, heartbeats, souls, then the ground falls in — a
pit three deep, walled with the glowing cracked seal, with the shaft at its bottom, drawn by a shader of its own (`underworld_shaft.fsh`): the shaft going on
down below the pit, its walls the cracked seal with glowing veins fading into black, soul-blue mist hanging in it in
layers, slowly turning, pale souls rising out of the deep, all of it pulsing slowly. It stays open for good. Into the shaft is down into the Underworld (darkness, souls, title). Ascent ruins, broken deepslate
rings round a dim ascent shaft, lie scattered in every Underworld biome; a pale shard rekindles one, and standing in its
light for two seconds carries one up beside the shaft they came down. Going down again later lands one back where one last went up, beside that ascent shaft
(never in its light, which would carry one straight back up); only the first time is it under the seal.

**Stage 3 — atmosphere** (client: [client/underworld](../src/client/java/de/ipnats/hardwrought/client/underworld/); server:
[UnderworldAtmosphere](../src/main/java/de/ipnats/hardwrought/underworld/UnderworldAtmosphere.java)):

- *Reverb* through OpenAL EFX (EAX reverb), hooked into the sound engine (`Library.init`, `Channel.play`). The space is
  measured every half second with 30 rays; decay, reflections and delays follow it; the biome colours it (Echo Chasm
  long, Flooded Halls bright and ringing, Mossy Grove and Overgrown Halls damped, Ossuary dry). Sounds behind rock lose
  their highs and some loudness. No EFX: plain delayed echoes instead.
- *Echo*: in the Echo Chasm every sound comes back twice, late by the time sound takes to the far wall and back.
- *Ambience*: drips from the roof (often in the Dripstone Depths, into the water in the Flooded Halls), rock settling far
  off, a low wind that swells and is louder where the rock closes in, the earth groaning deep below, a stalactite falling
  in a distant hall, whispers in the Graveyard, the Ossuary and near dim ascent shafts.
- *Unease*: watching eyes (frozen while looked at, elsewhere or gone after looking away, closing when approached or lit),
  footsteps behind that walk when you walk and stop a beat late and vanish when you turn, breathing when standing still
  in the dark for 20 s, a light going out behind you now and then, every compass spinning (the clock already does).
  Nothing of it in the Mossy Grove but the drips.
- *Falling dripstone*: in the Dripstone Depths stalactites near players crack and fall on their own (vanilla's falling
  stalactite, which hurts).

**Stage 4 — ruins, loot, journals** ([UnderworldRuins](../src/main/java/de/ipnats/hardwrought/underworld/UnderworldRuins.java),
[Journals](../src/main/java/de/ipnats/hardwrought/underworld/Journals.java)): small ruins (7×7, broken walls, the roof
fallen in as rubble and slabs, a cold campfire, a chest, a lectern with a journal one time in three, cobwebs, a burnt
torch, sometimes a skull) and camps (bedrolls of carpet by a burnt-out campfire, barrels, string for old rope, a burnt
lantern) in every biome but the Echo Chasm, which has watchtowers at its edges instead (a deepslate tower over a drop of
14 and more, broken top, a ladder with rungs missing, windows over the chasm, a chest and sometimes a lectern at the
top). Stone bricks above y 0, deepslate below, mossy in the green biomes. Loot (`loot_table/chests/underworld_*`):
stale bread (new; a little food, a quarter chance of a stomach bug), rotten flesh, bad water (waterskins and bottles of
swamp quality), worn iron tools, worn and rusted steel, candles, torches, leaf cord, flint, bandages, bones; arrows,
a bow and a worn spyglass in the towers. Journals (loot function `hardwrought:underworld_journal`): eight tattered
books of the Lantern Company, translated, about the arrival, the ore and the bad water, the eyes and the steps, the
Graveyard and the Ossuary, the Mossy Grove, the Witch King, the last survivor (the ascent shafts), and one scratched.
About a dozen ruins and as many camps in 21×21 chunks.

**Stage 5 — the Graveyard's undead** ([GraveUndead](../src/main/java/de/ipnats/hardwrought/underworld/GraveUndead.java),
[Graveyard](../src/main/java/de/ipnats/hardwrought/underworld/Graveyard.java)): two kinds of their own, drawn with
vanilla's models in rotted, soil-stained skins. The *grave walker* (a zombie: 28 health, slower, armour, knockback
resistance, never a baby, never burns or drowns into anything; its bite opens wounds like a zombie's and one time in
three carries grave dirt in, a wound infection) and the *grave skeleton* (a skeleton: an old bow, or two times in five
a worn iron sword). They are the Graveyard's only spawns (walkers 100, skeletons 70) and the Ossuary's (skeletons 100,
walkers 15). Spawned naturally in the Underworld they come up out of the ground: a second of the soil shifting —
its own break sound, soil thrown up, then a deep crack — and two seconds of rising through it with soil and a soul
or two, doing nothing else meanwhile (saved if the chunk unloads mid-rise). Undead and zombies/skeletons by tag;
drops rotten flesh, bones, arrows, rarely iron nuggets or a skull. Spawn eggs. The Graveyard got its graves: sunken
mounds of soul soil, coarse or rooted dirt and podzol with a headstone (walls, a cracked block, or a slab fallen flat
beside), unlit candles, skulls, bones through the soil; iron-bar fences with gaps; rotting trunks with mushrooms.
The Ossuary got skull heaps (a wither skull among them now and then) and bone pillars to the roof.

## Order of work

1. The dimension: noise, roof and floor, the nine biomes (with vanilla's Deep Dark), no light, cold;
   pale shards; ancient cities placed for its depth.
2. The way in and out: seal, shaft, ascent shafts.
3. Atmosphere: the reverb in the sound engine, the ambient sounds, the eyes and the other unease.
4. Ruins and their loot, journals.
5. The Graveyard's undead.
6. The Witch King and his citadel.
