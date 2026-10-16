# Endgame – Roadmap

Phase 22 of the development plan leaves the endgame open: world bosses, expeditions, megaprojects,
dimension resources. This is the path through it. Each goal unlocks the next, and each leans on the
systems the earlier milestones built rather than on bigger numbers.

```text
NETHER ──▶ WITHER ──▶ UNDERWORLD + WITCH KING ──▶ END + DRAGON ──▶ EXPEDITION ──▶ MEGAPROJECT ──▶ RITUAL
 heat        nether star     the way to fly            void           relics          all dimensions   credits
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
rods) runs on current from 48 V, strung to it like a lamp, anywhere: it glows, sparks and hums. A running
one draws a wisp into the portal it holds; a stable portal stops crackling, and crossing it shows
*Stable Portal — everything came through*.

**Gives.** Nether quartz for electricity, netherite as the high-end alloy, and the wither skeleton
skulls the next goal needs. Fortresses are already guarded by bow wither skeletons (mob system § 28).

## 2. Defeating the Wither

**Already there** ([Bosses.java](../src/main/java/de/ipnats/hardwrought/mobs/Bosses.java)): adds at
half and a quarter of its health, and structural collapse when it breaks a load-bearing block.

**Gives.** The **nether star** — the key to the Underworld.

## 3. The Underworld and the Witch King

**A dimension of its own**, entered by a ritual or portal that consumes the nether star; the magic
portals ([Portals.java](../src/main/java/de/ipnats/hardwrought/magic/Portals.java)) are the starting
point.

**The Witch King** is a magic boss: he cannot be beaten without magic shields, whatever the weapon.

**Gives.** The magic way to fly (see § 4).

## 4. The End and the dragon

The End is void: bedrock and the End islands, nothing else to stand on. Getting there therefore needs
flight **before** the End, and vanilla's elytra — found in End cities — cannot be that way in. There are
two, after the plan's baseline + specialisation:

- **Magic:** the Witch King's drop.
- **Technology:** a jetpack or glider of steel and electrics, burning fuel from the refinery. Range is
  fuel, and fuel is planning.

**Already there:** the dragon's four phases — aerial, endermen, ground, charges.

## 5. The End goal: expedition, megaproject, ritual

Long, but not hard. The challenge is gathering and planning, not fighting.

1. **Expedition.** Relics lie scattered over far-flung End islands. Reaching them is a matter of
   flight range and fuel.
2. **Megaproject.** The relics are parts of one great structure, which also takes materials from every
   dimension and every system: steel, chemistry, void crystals, the nether star, the dragon egg.
3. **Ritual.** Completing it sets off a visually very impressive ritual. At its end the player is put
   into creative mode, and the game says *Thanks for playing*.

## Order of work

1. Nether — smallest step, built almost entirely on existing systems (temperature, chemistry, steel,
   mobs).
2. Wither — mostly done; the nether star becomes the Underworld key.
3. Underworld and Witch King — by far the largest: a new dimension and a new boss.
4. Flight, both ways, then the End.
5. Expedition, megaproject, ritual.

## Still open

- Stabilisers: how much power, from which tier of electricity; does a power cut while someone passes
  make the portal unstable?
- The cool biome: its name, how common, and whether an unstable portal drops the player near one on
  purpose.
- Does going home through a portal set the spawn point back in the Overworld (to the bed, if there is
  one)?
- How the Underworld looks and what lives there besides the Witch King.
- What the megaproject is: what it is called, and what it looks like.
- What the ritual shows.
- Whether creative mode applies to everyone on a server, or only to whoever completed it.
