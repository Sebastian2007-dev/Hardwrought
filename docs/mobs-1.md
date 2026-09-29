# Mobs I

The first part of `Hardwrought_Mob_System.md`: the shared perception layer (§ 2) and the confirmed
changes of § 47. Hostile mobs get more dangerous through what they can do, never through more health or
damage.

Players in **creative or spectator mode are ignored** by everything here, as vanilla already ignores
them as targets: they are neither seen nor heard, and nothing breaks through to them.

## Perception (§ 2)

**Hearing.** What a survival player does makes noise, carrying this far in the open:

| Sound | Blocks |
|---|---|
| Explosion (any cause) | 48 |
| Mining a block | 16 |
| Shooting, lighting a fuse | 12 |
| Placing a block, a door, taking damage | 10 |
| Footsteps, landing | 8 |
| Opening a container | 6 |

Every whole block between the sound and a listener muffles it to 55 % of its reach, so mining behind
rock is heard by what is close, not by the whole cave. A hostile mob without a target that hears it walks
to the spot; what it finds there, its eyes decide. A new noise elsewhere redirects it. Sneaking steps are
already silent in vanilla, so sneaking is how to move unheard.

**Sight in the dark.** How far a hostile mob notices a player scales with the light the player stands in:
vanilla's range in full light, 30 % of it in darkness. A player carrying a light — a torch, a lantern, the
safety lamp — is seen as if standing in it.

## Zombies and their tools (§ 5)

A naturally spawned zombie may hold a tool, in hand and so always visible:

- the chance starts at 3 % and grows by 0.4 % a day, plus 4 % below y 0 and 8 % below y −32, up to 25 %;
- the tool is an axe (40 %), a shovel (35 %) or a pickaxe (25 %);
- its tier runs from flint through stone and bronze to iron as the world ages and deepens, the lower
  tiers staying likelier; it comes worn.

Every zombie can pick up tools it walks over, and then breaks what that tool breaks.

**Breaking through** (`Breaching`). What a tool opens follows the player's rules: an axe wood, a pickaxe
stone, a shovel soil — and a tool too soft for a material makes no impression on it at all, so a flint
pickaxe never gets through iron. Without a fitting tool a zombie gets through only what bare hands could:
loose soil, weak vegetation, glass. Blocks holding a block entity (chests, machines) are never broken,
and nothing is when `mobGriefing` is off. Vanilla's door breaking on hard difficulty is switched off, so a
bare-handed zombie no longer beats down wooden doors.

Zombies work at a quarter of a player's pace with the same tool. Several at the same block share its
progress with diminishing returns: n zombies work n^0.6 times as fast — two 1.5×, five 2.6×, twenty 6×.
Each tool at the block wears by one use per block broken, and a worn-out tool breaks, and with it the threat.

**Choosing where** (§ 5.3, `BreachGoal`). A zombie starts only once its path to the target has failed for
two seconds, so one that can walk round does. It picks the quickest block in the way, weighing doors,
gates and trapdoors at 60 % of their cost, and blocks at walking height before the floor or ceiling unless
the target is above or below. Every three seconds it asks whether a way has opened, and takes it.

## Creeper leap (§ 13)

About half a second before the blast, an ignited creeper on the ground leaps at a target within six blocks
it can see — announced by a high hiss, aimed where the target stood, not steered in the air. A charged
creeper leaps 30 % farther. It does not seek out walls.

## Phantoms and sleep debt (§ 20)

Phantoms follow sleep debt instead of time since rest. Getting up after a night of at least 2400 ticks:

| Night | Debt |
|---|---|
| Good (quality ≥ 70 %) | −1.5 |
| Mediocre (40–69 %) | +0.25 |
| Poor (< 40 %) | +1 |

The debt is kept on the player, capped at 6, and survives death. Below 1.5 no phantom comes — a single bad
night is never enough; at 2 the chance per attempt is about one in five, above even from 4. Staying awake
adds nothing.

## Wither skeletons and creakings (§§ 28, 39)

One wither skeleton in five spawns with a bow and fights as an archer; its arrows burn, as vanilla's do.

Creakings spawn naturally in the dark: very rarely in any forest (weight 1), rarely in dark and old-growth
forests (3), and commonly in the pale garden (30).

## The rest

Everything else of the mob specification is in [mobs-2.md](mobs-2.md).

## Verification

`MobGameTests`: tool chance and tiers, what each tool breaks, an axe zombie cutting into a plank box
while a bare-handed one cannot, the creeper's leap, sleep debt, noise muffled by walls and heard by an idle
zombie — but not from a player in creative mode — and creakings in the forest spawn list.
