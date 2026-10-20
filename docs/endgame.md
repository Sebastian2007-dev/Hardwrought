# Endgame – Roadmap

Phase 22 of the development plan leaves the endgame open: world bosses, expeditions, megaprojects,
dimension resources. This is the path through it. Each goal unlocks the next, and each leans on the
systems the earlier milestones built rather than on bigger numbers.

```text
NETHER ──▶ WITHER ──▶ UNDERWORLD + WITCH KING ──▶ END + DRAGON ──▶ TEMPLE ──▶ RITUAL
 heat        nether star     the way to fly            void           all systems  credits
```

## 1. Entering the Nether

**Gate.** The Nether is hot enough to kill on the temperature system alone, so going in needs heat
protection: a cooling suit, or a cooling draught from the chemistry line. Obsidian for the portal needs
steel tools.

**No pouring a frame.** Water on lava still sets it, but into **cast obsidian**
([CastObsidian](../src/main/java/de/ipnats/hardwrought/nether/CastObsidian.java)): glassy, cracked, the
heat still glowing in it, and too brittle to hold a portal — lighting a frame of it sparks and says so.
It takes a steel pickaxe like obsidian, and becomes real obsidian only when tempered in the smeltery:
a minute at 1600 °C, coal with bellows or lava. Real obsidian otherwise comes from mining natural
obsidian (steel again) or from piglins.

### Portal stabilisers

An obsidian frame alone opens only an **unstable** portal. It still takes the player through, but:

- no return portal forms on the other side — the player lands on the nearest safe ground instead, and
  anything that is not a player does not go through at all, or a thrown stick would open the way, and
- nothing the player carries makes it through: no items, no armour. It stays behind on the side they
  left — kept for them, nothing lost — and is given back, armour back on, when they come back to that
  side.

A **backpack** — with what is in it — and the **smithing gloves** go through all the same.

The rule is the same both ways: going home through an unstable portal leaves the Nether's gains lying
in the Nether. An unstable portal crackles with sparks and smoke, so it can be told at a glance.

*Built (stage 1):* [NetherCrossing](../src/main/java/de/ipnats/hardwrought/nether/NetherCrossing.java),
[PortalStability](../src/main/java/de/ipnats/hardwrought/nether/PortalStability.java) — every portal is
unstable until the stabilisers exist.

The player arrives in the Nether with nothing, and starts over — under harder conditions than the first
time: heat, scarce water, hostile ground. Getting home means building a portal on the Nether side, which
is a goal in itself. Obsidian comes from piglin bartering (gold for obsidian, as in vanilla since 1.16),
ruined portals and crying obsidian.

### Starting over in the Nether

The Nether must give a fresh start everything it needs, and close by — not ten thousand blocks out:

- **A cooler biome** (name open), common enough to be near any arrival. Water can be placed there
  instead of evaporating, but it is always boiled: only purified water forms. A drink, but not plenty.
- **A tree** grows in it. Its logs are carved into a workbench with an iron axe, as everywhere in the mod; its **bark** makes armour that keeps the
  heat out effectively — the Nether's own answer to the cooling suit.
*Built (stage 2):* the **Ash Grove** ([AshGrove](../src/main/java/de/ipnats/hardwrought/nether/AshGrove.java)) —
about 19 °C, ash drifting, grey Nether grass on its netherrack, no lava — its share of the lava sea is a lake of
boiled water — as common as any vanilla Nether biome, and an unstable crossing sets its
traveller down in the nearest one. Water poured there stays and is always purified, and steams;
everywhere else in the Nether a bucket hisses empty. The **ashcork** ([Ashcork](../src/main/java/de/ipnats/hardwrought/nether/Ashcork.java))
grows there on netherrack, in the dark, and does not burn; an axe strips two pieces of bark off each
log, and a full set of bark armour keeps three quarters of the hot air's heat off
([HeatShield](../src/main/java/de/ipnats/hardwrought/nether/HeatShield.java)).

- **Ordinary ores** in the Nether too, so tools can be made from scratch.

*Built (stage 3):* Nether coal, copper, tin and iron ore in netherrack, all over the Nether
([NetherOres](../src/main/java/de/ipnats/hardwrought/nether/NetherOres.java)); they drop what the overworld
ores drop and want the same pick — flint for coal, stone for the rest. There is no stone in the Nether, so
**blackstone** breaks into blackstone pieces under a pick, and a stone pick, hatchet or hammer is made of
those as of cobblestone pieces. Gravel gives flint, ashcork leaves fibre and sticks. Fortress, bastion and
ruined-portal chests also hold the makings of a start — flint, fibre, sticks, coal, raw copper and iron,
blackstone pieces and ashcork saplings ([NetherLoot](../src/main/java/de/ipnats/hardwrought/nether/NetherLoot.java)).
- **Netherrack can be broken by hand**, very slowly — the first shelter and the way to the first ore do
  not wait for a tool.
- **Loot** in the Nether's ordinary places, within reach of a new start.

There is no clay for a brick furnace either; instead **metal thrown onto a magma block** in the Nether
heats up where it lies, smoking and hissing, up to its working heat — iron to 1300 °C, softer metals to
the top of theirs, never past ([MagmaHeating](../src/main/java/de/ipnats/hardwrought/nether/MagmaHeating.java)).
The drying rack works there without sky, day or night, six times as fast. Netherrack, Nether grass and
both nyliums are dug by hand.

### No way around it

- **Going through a Nether portal sets the spawn point in the Nether.** Dying there does not send the
  player home to their bed and their chests; the restart is not escaped by dying.
- **Heat resistance** for a while on arrival, and again after every respawn in the Nether, so that a
  player does not overheat on the spot and has time to get their bearings: fifteen minutes.

A **stabilised** portal has portal stabilisers built into its frame: machines from the technology line,
running on electricity while the portal is open. Only then does a return portal form, and only then does
the inventory come along.

**Stabilisers are made only from Nether materials.** The first way into the Nether is therefore always
unstable: every player starts over there once, empty-handed, and earns the stabilisers there. Only from
then on can expeditions go back and forth with their gear.

Two kinds:

- **Provisional stabilisers** run on the heat around them — which the Nether has, and the Overworld
  does not. They are the way home from a fresh start, built from what the Nether gives early on.
- **Stabilisers** proper run on electricity, and keep a portal stable anywhere.

*Built (stage 4):* [PortalStabilizers](../src/main/java/de/ipnats/hardwrought/nether/PortalStabilizers.java).
A portal is stable with **two running stabilisers** against its frame — in the frame's plane up to a
block out from it, or on either face. The **provisional** one (4 blackstone pieces, 2 quartz, 2 gold
nuggets, a magma block; hewn bench) runs in the Nether, or wherever lava or magma touches it: its magma
heart glows and throws embers. The **stabiliser** (a provisional one, 4 quartz, 2 gold ingots, 2 blaze
rods) runs on current from 48 V, fed like a machine through a copper coil set against it, anywhere: it glows, sparks and hums. A running
one draws a wisp into the portal it holds; a stable portal stops crackling, and crossing it shows
*Stable Portal — everything came through*.

**Gives.** Nether quartz for electricity, netherite as the high-end alloy, and the wither skeleton
skulls the next goal needs. Fortresses are already guarded by bow wither skeletons (mob system § 28).

## 2. Defeating the Wither

**Already there** ([Bosses.java](../src/main/java/de/ipnats/hardwrought/mobs/Bosses.java)): adds at
half and a quarter of its health, and structural collapse when it breaks a load-bearing block.

*Built:* [SoulSkulls](../src/main/java/de/ipnats/hardwrought/wither/SoulSkulls.java),
[WitherPowers](../src/main/java/de/ipnats/hardwrought/wither/WitherPowers.java).

- **Soul-charged skulls.** A wither skeleton skull is empty until it has lain half a minute in soul fire,
  which does not burn it; souls are seen going into it, and it comes out shimmering. Only three charged
  skulls raise the Wither — empty ones smoke and say so.
- **It teleports**, now and then to come at its target from elsewhere, and at once when walled in by what
  it cannot break. Souls and smoke where it leaves and where it arrives.
- **Soul storm** below a quarter of its health: everyone within 16 blocks is drained, worse the longer
  they stay.
- **Its death:** a last great blast, then its souls come back out of the air from all around and gather
  over four seconds into the nether star, which hangs where it died. 3 to 5 **Wither essence** fall with it.

**Gives.** The **nether star** — the key to the Underworld.

## 3. The Underworld and the Witch King

**A dimension of its own**, entered by a ritual or portal that consumes the nether star; the magic
portals ([Portals.java](../src/main/java/de/ipnats/hardwrought/magic/Portals.java)) are the starting
point.

**The Witch King** is a magic boss: he cannot be beaten without magic shields, whatever the weapon.

**Gives.** The magic way to fly (see § 4).

## 4. The End and the dragon

An air fight. Whoever comes to the End without a way to fly falls into the void; whoever comes with
one fights the dragon in the air over the broken remains of its island. The player is expected to bring
the best there is — adamant, runes, the Witch King's mantle — and it is still meant to be the hardest
fight in the game, far harder than the Witch King.

### Getting there

- **The way in is vanilla's:** a stronghold, eyes of ender, the End portal.
- **Nobody arrives on an island.** The arrival point lies out in the void, well away from every island.
  There is no obsidian platform: a platform of light, drawn by a shader, holds the player for
  **30 seconds** and then fades. Whoever cannot fly by then falls.
- **No blocks can be placed anywhere in the End while the dragon lives.** No pillaring, no bridging, no
  catching a fall with a block: the only way to stay up is to fly.
- **Death in the End** respawns in the Overworld, as in vanilla. Getting back means the portal and the
  flight in again.
- Elytra from End cities are no way in: the cities lie on the outer islands, beyond the fight.

*Built (stage 2):* [EndRules](../src/main/java/de/ipnats/hardwrought/end/EndRules.java),
[VoidLight](../src/main/java/de/ipnats/hardwrought/end/VoidLight.java). The way in sets every traveller down at
(150, 90, 0), looking towards the broken island, on a 5×5 floor of light — a thin translucent plate with veins of light
flowing through it, seamless from block to block, a sigil turning beneath and motes along its edges; violet and
flickering in its last ten seconds — that counts its last ten seconds down and is gone after thirty. No block is
placed and no bucket emptied in the End while the dragon lives (until it is first killed, and whenever one is raised
again); the client is told, so nothing shows that the server refuses.

### The mantle: gliding, driving on, hovering

The Witch King's mantle is the one way to fly in (§ 3). It glides like elytra and drives on with held
jump (built). For the air fight it also **hovers**:

- Tapping jump twice in the air (as a creative player takes off), or its key (X), switches between
  gliding and hovering. Hovering flies as creative flight does: at creative pace every way (twice as fast
  sprinting), rising with jump and sinking with sneak; left alone it hangs on a slow breath of souls.
  Only a fresh press toggles, so a held key does not flicker it. Souls trail from the mantle's hem.
- **Soul charge** limits it: hovering drains it (about 20 seconds from full), gliding and standing on
  ground refill it. Empty, the wearer drops out of the hover into a glide.
- **A ceiling:** whoever flies too high, the mantle gives out — it stops carrying them and they fall,
  and for some seconds it neither glides nor hovers (a cooldown, shown on the item). Over the void that
  is a fall to be caught low enough, or not at all. In the End the ceiling is a fixed height over the
  arena; elsewhere a height above the ground below.
- Driving on still wears the mantle; mended with pale shards, and now with runes (mending).

*Built (stage 1):* [MantleFlight](../src/main/java/de/ipnats/hardwrought/witchking/MantleFlight.java),
[MantleClient](../src/client/java/de/ipnats/hardwrought/client/witchking/MantleClient.java). Key X; 20 s of
hovering on a full charge, sinking at a fifth of the cost, 8 s to fill again; ceiling Y 160 in the End, 64 blocks over
the ground elsewhere; 5 s of cooldown past it.

### The arena: the broken island

The main island is gone. What is left of it hangs in the void as **fragments**, the largest in the
middle holding the exit portal, the rest scattered round it at different heights. The obsidian pillars
stand alone in the void, each with its crystal on top. Fragments and pillars are the only ground: places
to land, to fight on foot, to catch breath — and they do not all last the fight.

The outer islands stay as vanilla's, beyond the void.

*Built (stage 4):* [BrokenIsland](../src/main/java/de/ipnats/hardwrought/end/BrokenIsland.java). The main island is
taken out of the End's terrain (`density_function/end/islands`), and with it the obsidian platform from the biome. In
its place, from the seed alone: a middle fragment flat round the exit portal (top at y 62, eighteen blocks across
half-way), eighteen fragments of four to twelve blocks at heights from 44 to 96 out to 112 blocks, and some forty-five
stones broken off further out — end stone hanging in jagged points, obsidian fused into their undersides, here and
there crying obsidian. The pillars end 34 blocks below their tops in a broken obsidian point, alone in the void.

### The dragon

A boss of its own making, not vanilla's dragon with changes: its own AI, flying freely through the whole
air space round the fragments, choosing among its moves by distance and the state of the fight (as the
Witch King does), every move marked before it strikes. Many shader effects and particles, as for the
Witch King: trails of void fire, shockwaves, a sky that changes as the fight goes on.

1. **The crystals.** The crystals on the pillars shield it and mend it. They are **much harder to
   break** than vanilla's: each has its own ward, which takes several hits or a strong spell to break
   before the crystal can be struck, and which mends while the dragon is near. A broken crystal bursts
   and throws whoever is near away. The dragon patrols between the pillars, knocks gliders off course
   with beats of its wings, and looses volleys of fireballs.
2. **The hunt.** It hunts the flyers: clouds of breath hanging in the air that close off routes, dives
   marked before they come, endermen on the fragments.
3. **The landing.** It comes down on the largest fragment: the window to fight it on foot. It stamps
   until the fragment shakes and crumbles at its edges, and sweeps its breath over the ground.
4. **The breaking** (below a quarter). Fragments break away and fall, the arena shrinks; it rages, and
   the sky tears open with a pull towards the void.

**Its harm** is meant for the best armour there is, harder than the Witch King's: its bites and dives
are blows that armour holds only half of, its breath and fire are magic that armour does not hold at
all and runes only half; and every hit throws the one it hits — in the air, towards the void.

**The rematch.** Once dead, it can be raised again with four crystals on the exit portal, as in
vanilla. The raised dragon is stronger — more health, quicker, harder hitting, and a move it did not
have — and only it gives what the ritual needs (§ 5).

The raising is a sight of its own (`DragonRising`, `DragonRiseFx`), laid over vanilla's sequence and
in step with it: *the call* — titles "Die Leere regt sich", motes drawn in to the point over the
portal, a heart beating there ever quicker, a sigil drawing itself across the island, the sky
beginning to tear; *the pillars* — for each, lightning from that point to its top, a column of light
out of the void beneath it, and as it stands again a burst and thunder; *the summoning* — lightning
out of every crystal, rings closing in, the void swelling black in a cracking shell, the view bent
towards it, the sky torn wide; *the birth* — a flash, waves over the whole island, black wings of
smoke, its roar, and the title "Der Drache erhebt sich".

*Built (stages 5 and 6):* [VoidDragon](../src/main/java/de/ipnats/hardwrought/end/dragon/VoidDragon.java),
[DragonHunt](../src/main/java/de/ipnats/hardwrought/end/dragon/DragonHunt.java),
[CrystalWards](../src/main/java/de/ipnats/hardwrought/end/dragon/CrystalWards.java),
[DragonFx](../src/client/java/de/ipnats/hardwrought/client/end/DragonFx.java).

- *Its own AI, vanilla's body.* Vanilla's dragon keeps its model, its hit boxes, its flight, its death, the exit
  portal and the egg; its phases are replaced by one of Hardwrought's own (`DragonHunt`), added to vanilla's
  list of phases, which vanilla is never let out of but to die. Between attacks it circles the arena; then it
  chooses by weight from what the stage and the distance to its prey allow, never the same twice running.
- *Moves.* **Bolts**: passing its prey it looses bolts of void, turning after them a moment, which burst
  into clouds of void hanging in the air for seven seconds. **Gust**: close up, it holds for a breath and
  beats its wings — a cone of wind that throws everyone in it far away and down. **Dive**: it climbs above
  its prey, marks the place with a sigil and a pillar of light, and falls on it. **Clouds**: it flies
  through the air near its prey breathing clouds that close off the way. **Summon** (from the hunt on): a
  roar, and up to six endermen on the fragments near its prey. **Landing** (below 55 %): it comes down on
  the exit portal, turns its head on its prey, stamps — a mark first, then the fragment shakes, whoever
  stands near is thrown and its edges fall away — and lays its breath in an arc before it; struck for 15 %
  of its health, or after thirteen seconds, it rises. **Skyfall** (raised again only): marks round
  everyone it fights, and bolts falling out of the sky on them.
- *Stages.* While a crystal stands, a fifth of what strikes it reaches it. Below a quarter it rages: it
  comes on faster and harder, a fragment breaks up and falls every eleven seconds, the middle one sheds its
  edges, every three and a half seconds the void pulls whoever is in the air down, and a rift opens in the
  sky over the island. The void itself tears: a post-processing pass (`fx_composite.fsh`) draws cracks of
  light all over the sky and the far fog, held fast to directions in the world, opening, flaring and closing
  again, new ones all the time, the more the nearer the dragon is to death.
- *Its death.* Its own, not vanilla's rays: tendrils of void lightning claw at it, ash and smoke are drawn
  into it, rings close in on it ever faster while the void tears wide open, and at the end everything falls
  into one point and bursts outwards in a wave of magenta and light over the whole island.
- *Wards.* Each crystal's ward holds 40 of harm (60 round a raised dragon) and mends a point and a half a
  second while the dragon is within 48 blocks, broken or not. A crystal that breaks throws whoever is near.
- *Harm.* 600 health. Bites 16, dives 22 and the stamp 15 are blows (`dragon_bite`): armour holds half. Bolts
  12, the gust 7 and its clouds 4 every half second are void (`void`): armour not at all. Runes hold half
  against either; everything that hits throws its victim and knocks them out of their hover.
- *The rematch.* A dragon raised after the first is killed has 900 health, hits 30 % harder, rests less
  between attacks, dives again straight after a dive, and has skyfall; when it dies, its **heart** falls
  over the portal.

### Soulbound

A rune for the runetable that keeps a piece through death. It is written only in **Wither essence**,
which means fighting the Wither for it, and so comes shortly before the End. It works **once**: the
piece stays with its owner through a death and the rune is gone.

*Built (stage 3):* [Soulbound](../src/main/java/de/ipnats/hardwrought/enchanting/Soulbound.java). Wither essence is a
fourth ink at the runetable; soulbinding is written in it and in nothing else, and it writes nothing else — two
measures per rune. Like every treasure rune it wants fifteen shelves and level 25. On death the bound pieces are taken
out before anything drops, the rune burnt off them (its room on the piece free again, every other rune kept), and they
are back in their slots — armour on — when their owner rises. With keep inventory on, nothing is spent.

### Order of work

1. The mantle's hover, soul charge, ceiling and cooldown.
2. The End: arrival in the void, the platform of light, no placing blocks while the dragon lives.
3. Soulbound, in Wither essence.
4. The arena: the broken island, fragments and lone pillars.
5. The dragon: its own AI, its moves, its phases, the crystals and their wards, its effects.
6. The rematch and what it gives.

## 5. The End goal: the temple and the ritual

Long, but not hard: the challenge is building and gathering, not fighting. There is no expedition.

**The temple** ([Temple](../src/main/java/de/ipnats/hardwrought/ritual/Temple.java)) is a multiblock its players
build themselves, anywhere, along its projection from the compendium. It comes in three: of the **plains** (white quartz
and stone under a green copper dome, round towers), of the **desert** (sandstone, lapis and gold under a stepped pyramid,
obelisks) and **stony** (deepslate, tuff and amethyst under a tall spire, battlemented keeps). Each is 59 blocks across
and some thirty high, several thousand blocks: a square terrace with a stair down each side and a parapet, a colonnade
round it under a cloister roof with a gate over each path, four corner towers, and in the middle the round hall of the
ritual — buttressed, windowed, its floor inlaid with rings and rays, eight pillars, and its roof open at the top to the
sky. Every light in it is one that never goes out. The courtyard between colonnade and hall and the foundation are the
builder's own; the plan shows a garden there for each (shown when a temple is set down whole), but none is asked for.
A temple stands when 98 % of its blocks are right, so a stray block does not undo it. Asked while it does not stand
yet, the altar marks every block still wrong red in the world, the block the plan wants drawn over it; each mark goes
as soon as its block is right ([TempleFaults](../src/main/java/de/ipnats/hardwrought/ritual/TempleFaults.java)).

*Under the building physics* ([TempleSupport](../src/main/java/de/ipnats/hardwrought/ritual/TempleSupport.java)) the
terrace, colonnade, towers and hall walls stand by themselves: lintels of gold over the doors and gates, a pillar up
each tower, a pillar every third block of the colonnade. The roof over the hall (dome, pyramid, spire) no stone or copper
would carry that wide; the altar holds it. Every block that is just where the plan has it, round a ritual altar, counts
to the physics as anchored like the ground. So the altar is set first, in the middle of the hall to be. Anything else,
like the courtyard, the builder's own additions, or a temple without its altar, stands or falls like anything built.
In creative a projection set down with a right click is built whole at once, any multiblock, not only the temples.

**The ritual** ([Ritual](../src/main/java/de/ipnats/hardwrought/ritual/Ritual.java)). In the hall the **ritual altar**
stands in the middle on its dais, and eight **offering pedestals** round it, both crafted and set by the players. Each
pedestal, by its place round the altar, wants one offering — the Wither's nether star, the first dragon's egg, a pale
shard of the Underworld, a block of void crystal, an adamantium ingot, an electric motor, a bucket of diesel and a
golden wand — and shows it hanging over it as a pale, breathing ghost until it is laid there; then the offering itself
hangs there, slowly turning. An offering held to a pedestal is only ever laid there or refused, never used on it (no
diesel poured out, no wand cast). The **dragon's heart**, which only a dragon raised again leaves, laid on the altar of a
standing temple with every offering in place, begins the ritual, about a minute long: night falls; the pedestals kindle
one by one and send their light to the altar; the heart rises and rings of light rise round it; a pillar of light goes
up through the open roof into the sky while lightning strikes round the temple and light streams in from all round;
the offerings burn up; a heartbeat, quickening; then a wave of light over the land, fireworks, and dawn.

Once it has begun there is no leaving the chamber: whoever is in it, or comes into it, is held there by a ward at its
wall and under its roof, which sets them back inside. At the end they rise, in a cutscene of their own
([RitualCutscene](../src/client/java/de/ipnats/hardwrought/client/ritual/RitualCutscene.java)): the camera looks up after
them from the chamber floor as they float up through the opening in the roof, then circles them in the open sky and
draws in on them while a **golden outline** kindles round them (their own model drawn again just behind them, so
only its rim shows: not the glowing effect). At the height of it light breaks out of them: light drawn in from all
round, then a white-gold blast, a wave of gold over the land with the sky bent in it, a pillar of light, a crown of
turning rays, a halo, and gold raining down. They are in **creative mode** from then on, flying over the temple; the
camera comes back to their eyes and the game says *Thanks for playing*.

## Order of work

1. Nether — smallest step, built almost entirely on existing systems (temperature, chemistry, steel,
   mobs).
2. Wither — mostly done; the nether star becomes the Underworld key.
3. Underworld and Witch King — by far the largest: a new dimension and a new boss.
4. Flight, both ways, then the End.
5. The temple and the ritual.

## Still open

- Stabilisers: how much power, from which tier of electricity; does a power cut while someone passes
  make the portal unstable?
- The cool biome: its name, how common, and whether an unstable portal drops the player near one on
  purpose.
- Does going home through a portal set the spawn point back in the Overworld (to the bed, if there is
  one)?
- How the Underworld looks and what lives there besides the Witch King.
