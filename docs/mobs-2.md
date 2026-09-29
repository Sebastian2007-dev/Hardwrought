# Mobs II

The rest of `Hardwrought_Mob_System.md`, after [mobs-1.md](mobs-1.md). Every section of the
specification is listed here with what it became. As before, players in creative or spectator mode are
ignored by all of it.

## Shared layer

| § | What it is now |
|---|---|
| 2 Shared aggro | A mob that sets on a player tells its kind (`SharedAggro`). Organised fighters — guardians, piglins, illagers and other raiders — take the same target at once, within 24 blocks (an elder guardian: 48). Everything else within 12 blocks only hears the commotion and comes to look. |
| 2 Movement | A sprinting player is noticed from 25 % farther. Swimming and splashing are heard (§ 7). |
| 3 Anti-cheese | Covered by the pieces below: ranged mobs that relocate and keep distance (pillars), tool zombies and cave spiders (holes), breaching mobs (doors), the drowned (water), statics (skybases). |
| 46 Weather | Rain muffles sound to 70 % of its reach, a thunderstorm to 50 %. |

## Panic: no mob can simply be chased down

Mobs that back away — the ranged fighters of §§ 8–10, 16, 33 and 34, the cave spider and the vex —
would be easy prey if they only ever ran. A fleeing mob therefore panics and turns on its pursuer
(`Panic`) when

- it is hit while fleeing,
- fleeing gets it nowhere: after a second on the run it is no farther away, and
- it has had to flee three times within ten seconds.

For five seconds after that it neither flees nor repositions: an archer shoots at point blank, a cave
spider keeps biting, a vex keeps stabbing. It calls out when it turns. Then it may flee again, and panic
again.

## Overworld

| § | Mob | Behaviour |
|---|---|---|
| 6 | Husk | Follows 50 % farther. Its hit draws 4 hydration and leaves the player *winded* for five seconds: stamina recovers at half pace. |
| 7 | Drowned | 25 % faster. A drowned hit pulls a swimmer down. A drowned whose target sits in a boat swims to the boat and batters it until it breaks. |
| 8 | Skeleton | Backs away from a target closer than five blocks; every seven seconds of fighting takes a new position around the target at shooting distance, preferring high ground (`RangedTacticsGoal`). Each shot is quick (loose), normal, accurate (tight) or charged (flies half again as fast, and so hits half again as hard). Aim spreads with distance, darkness around the target, the target's speed and cover in between (`SkeletonAim`). |
| 9 | Stray | Shoots like a skeleton; its arrow also takes 1.2 °C of body heat and leaves the player winded. Slowness stays vanilla's. |
| 10 | Bogged | Shoots like a skeleton, but picks positions the target cannot see — keeping obstacles between itself and the player. Its poison arrows stay vanilla's. |
| 11 | Spider | In a fight spins a web where its target stands, at most every 20 seconds; the web comes away after a minute unless something else replaced it. Only with mob griefing on. |
| 12 | Cave spider | After a hit that lands, backs off for three seconds. |
| 13–14 | Creeper | Leap before the blast; see mobs-1. |
| 15 | Enderman | An angry enderman teleports behind or beside its target now and then. Torches, lanterns and candles are among the blocks it takes, so an exposed light may go missing. Machines and built blocks are not. |
| 16 | Witch | Picks the potion for the target: harming for one nearly dead, weakness for one in heavy armor, slowness for a fast one, poison otherwise. Keeps its distance. Every ten seconds, among three or more fighting monsters, throws strength into their midst. Healing raiders stays vanilla's. |
| 17 | Slime | Deals no damage. A big one shoves the player aside instead, which blocks a corridor as well as a body can. Blunt blows do 60 % against it, cuts and thrusts 130 %, by the weapon's share of each. Splitting is unchanged. |
| 18 | Silverfish | Mining stone below y 0 may break into a nest: 0.2 %, plus 0.006 % per block of depth, releases three to six silverfish set on the miner. Infested stone within two blocks of a block being mined is shaken open. |
| 19 | Endermite | Endermites within 32 blocks go to where an ender pearl lands. |
| 20 | Phantom | Sleep debt; see mobs-1. |
| 39 | Creaking | Forest spawns; see mobs-1. In the dark it is a quarter faster. |

## Nether

| § | Mob | Behaviour |
|---|---|---|
| 21 | Blaze | Heats the air around it: 14 °C beside it, nothing at eight blocks. Several in a room add up (to at most 35 °C). Setting players and wood alight stays vanilla's, and fire uses up the room's air through the environment model. |
| 22 | Magma cube | Heats the air around it by size. The ground it lands on stays hot for ten seconds and burns anyone standing on it who is not sneaking. Metal armor makes heat worse already, since the metabolism counts armor mass against it. |
| 23 | Ghast | Notices targets from 30 % farther. Its fireball's effect on structures is by material, as vanilla's explosion resistance already is. |
| 24 | Piglin | Shared alarm (above). A quarter of the melee piglins carry a shield. |
| 25 | Piglin brute | 80 % knockback resistance, shakes a stagger off in a third of the time, and breaks through wooden doors and barriers with its axe — nothing a golden axe could not. |
| 26 | Hoglin | Charges from four to twelve blocks: a straight rush that hits for one and a half times its damage and throws the target back. A player crouching behind a spear braces it: the charger takes twice the blow and is staggered. |
| 27 | Zoglin | Charges the same way, with 90 % knockback resistance, and a stagger holds it only a fifth as long. |
| 28 | Wither skeleton | Bow variant; see mobs-1. |

## Water, End and raids

| § | Mob | Behaviour |
|---|---|---|
| 29 | Guardian | Shared alarm across 24 blocks. |
| 30 | Elder guardian | Its alarm reaches 48 blocks: the whole monument. Mining fatigue stays vanilla's. |
| 31 | Shulker | When one fires, every other shulker within 16 blocks on the same target fires too — at most once in a second and a half each — so their angles cross. |
| 32 | Vindicator | Breaks through wooden doors and barriers with its axe. |
| 33 | Pillager | Keeps its distance and relocates like a skeleton; shared alarm with the other raiders. |
| 34 | Evoker | Keeps its distance; shared alarm. |
| 35 | Vex | After a hit, pulls back for two seconds: the window to strike back. |
| 36 | Ravager | Breaks through what an iron axe could: doors, fences, wooden barricades, light timber — never masonry or steel. |

## Deep and bosses

| § | Mob | Behaviour |
|---|---|---|
| 37 | Warden | Running machinery within a chunk of a warden sends out a vibration every two seconds, and a collapsing structure is one heavy vibration; the warden follows both. It breaks weak wooden obstacles, not stone. It shakes off every stagger. |
| 38 | Breeze | A wind burst — its own or a player's — puts out fire, blows out lit candles and campfires, and drives gas away from where it hits, which may help as much as hurt. |
| 40 | Wither | Calls three wither skeletons at half its health and three more at a quarter. Breaking a load-bearing block brings down what it held, through the statics. |
| 41 | Ender dragon | Phase 2 below 75 %: four endermen come to its defence. Phase 3 below 45 %: it lands and fights on the ground. Phase 4 below 20 %: hits often turn it into a charge at the nearest player. |

## Composition (§ 44)

A zombie that spawns with a tool comes with a skeleton one time in five, and a third of those bring a
witch as well: the zombie at the door, the archer covering it, the witch backing both. Mixed piglin
groups are vanilla's bastions, now with shields and alarms.

## Depending on systems that do not exist yet

- § 10 and § 19: Hardwrought's own poison tolerance and magic are not built; the bogged keeps vanilla
  poison, and endermites have no magic to disturb.
- § 14: a charged creeper's electrical disturbance waits for the electricity milestone.
- § 34: the evoker's interaction with Hardwrought magic, likewise.
- § 46: the remaining open questions (seasons, altitude, smell, factions, new mobs) stay open design.

## Verification

`MobGameTests`, besides the mobs-1 checks: the witch's potion choice, slime resistances and stagger
resistance, a blaze heating the air, a gust putting out fire and a candle, and skeleton aim spreading
with distance and tightening for an accurate shot.
