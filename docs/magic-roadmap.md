# Magic – Roadmap

What [magic-1.md](magic-1.md) (runes, drawing, casting, wands, shields, gates), [sigils-1.md](sigils-1.md)
(sigils S1) and [enchanting.md](enchanting.md) built is the core of the magic system. What it does not
have yet, measured against `Hardwrought_Sigil_System.md`, the full mechanics specification (§28–29,
§84–91, milestone 16) and the mob specification (endermite, evoker):

- the **Mage Table** is a reskin of the parchment and changes nothing;
- there are no **scrolls**, the specification's prepared spells (§86);
- **wands** stop at gold, and the **void crystal** is mined but has no magic in it;
- the **sigil** instruction set is five of some thirty, and sigils cannot talk to each other (§39–42);
- the **world** barely touches magic: one environment rule, all spell damage is plain magic, armour
  does not interfere with casting, no mob reacts to magic, runes are found only in small ruins.

The main magic specification (`runeconzept/Hardwrought_Magic_System.md`) is not in the repository; the
rules below are built from what the other documents and the code say, and from judgement where they
are silent.

```text
M2 WANDS ──▶ M3 MAGE TABLE ──▶ M4 SIGILS S2 ──▶ M5 SIGILS S3 ──▶ M6 WORLD & COMBAT
mithril,     table bonus,      more              signals, links,   environment, damage types,
void crystal scrolls           instructions      conditions        interference, mobs, learning
```

Each stage is built with game tests and its own document, as the electricity stages were.

## Principles that hold throughout

1. **Knowledge, not mana.** Nothing here adds a resource to spend; what limits magic is what the
   player knows, how well they draw, what their wand carries and what they prepared.
2. **A rune means the same everywhere** (sigil §9). A new instruction is an existing rune in a new
   place, with a meaning that follows from what it does in a spell, not a new glyph to learn.
3. **Preparation trades for speed.** A scroll or a sigil is drawn carefully once and pays off later;
   action casting stays the fast, risky way.
4. **Every gain has a price.** A better wand costs rare metal; a scroll is consumed; a complex sigil
   needs a bigger stabiliser and more skill.

## M2 – Wands and void crystal

**Status: built** — see [magic-2.md](magic-2.md).

| Wand | Recipe | Tier | Steadiness | Durability |
| --- | --- | --- | --- | --- |
| **Mithril** | golden wand, 2 mithril ingots, 2 amethyst shards | 6 | +0.18 | 500 |
| **Void crystal** | mithril wand, 2 void crystal shards and an echo shard | 8 | +0.25 | 300 |

- The void crystal wand **halves the stability cost of overload** (0.075 per tier instead of 0.15):
  the crystal holds what the wood and metal cannot.
- **Void ink**: a void crystal shard ground with an ink sac. In enchanting it is an ink like mithril
  powder (+1 level over the limit); for scrolls (M3) it binds them steadier (+0.05 stability).
- Tests: the new tiers carry their load; the void wand's overload costs half.

## M3 – The Mage Table and scrolls

**Status: built** — see [magic-2.md](magic-2.md).

**The table** (today a reskin) becomes a place for careful work:

- drawing at the table: **+0.10 stability**, **no hesitation penalty** (a stroke may take as long as it
  takes), and **one more tier** of capacity than the wand alone;
- the server is told the drawing was made at a table, and checks that the caster stands at one.

**Scrolls** (full specification §86: parchment + ink + rune construction + binding):

- **Parchment** (3 paper and leather make 3) and **rune ink** (an ink sac, glowstone dust and an
  amethyst shard make 2; void ink is better).
- With blank parchment and ink in the inventory, a drawing at the table can be **bound** instead of
  cast. It is judged once, then and there; the scroll keeps the drawing and its accuracy, stability
  and power ("Fire bolt — 94 % / 91 % / 108 %").
- **Reading a scroll** casts it at once from the hotbar — no parchment, no drawing, no wand needed —
  with the quality it was bound with. The scroll is used up. Environment (M6) still applies when it is
  read. A short cooldown between scrolls.
- A scroll can hold only what the binder's wand and table could carry; failures at binding are spent
  on the spot, as a cast would be.
- Tests: binding needs the table, parchment and ink; a scroll casts with its stored quality and is gone;
  the table's bonus.

## M4 – Sigils S2: more instructions

**Status: built** — see [magic-2.md](magic-2.md).

New instructions are existing runes in the **ring** between the circles. Fire and Light read as plain
strokes there (strength), so the instruction runes are the others, with the meaning they have as
amplifiers in a spell:

| In the ring | Instruction | Sigil § | Effect |
| --- | --- | --- | --- |
| **Wind** (the arch: the specification's ")" range mark) | range | 15 | a directed sigil reaches × 1.5 per Wind |
| **Water** | size | 14 | sphere and burst radius × 1.4 per Water |
| **Earth** | duration | 19 | lasts twice as long per Earth |
| **Dark** | target: hostile | 20 | acts only on monsters |
| **Dark struck through** | target: friendly | 20, 38 | acts only on the inscriber's side |
| **Lightning** (a broken line) | delay | 22 | waits 1.5 s between trigger and effect per Lightning |
| **Two small squares** | trigger: contact | 21 | fires only on whoever stands on the sigil itself |
| **Small circle with a small triangle** | shape: ring | 16 | a ring round the sigil instead of a ball |

- Every instruction still costs one point of the stabiliser; targets and the delay cost two.
- The presence trigger, which today fires on anyone but its maker, respects the target instructions.
- Sigils that last longer than an hour need at least a hexagon.
- Tests: each instruction read and carried out; capacity counted.

## M5 – Sigils S3: signals, links, conditions

**Status: built** except the rune order (§43, moved to Later) — see [magic-2.md](magic-2.md).

The foundation of arcane engineering (sigil §39–42, §51).

- **Linking** (§40–42): a wand used on one sigil and then another, while sneaking, links the first to
  the second. A faint thread is drawn between them. Each sigil has up to four links out.
- **Signals**: whenever a sigil acts — or, for a sigil with a presence trigger, whenever it notices
  something — it sends a signal along its links.
- **Input**: a sigil with a link into it does nothing by itself; it acts when a signal arrives (one
  action per signal, after its delay). So a detection sigil can fire an attack sigil.
- **Conditions** (§39) are runes drawn **outside** the outer circle, against it:

  | Outside the circle | Acts only when |
  | --- | --- |
  | Dark | the light where it lies is 7 or less |
  | Dark struck through | the light is 8 or more |
  | Fire | the air is 25 °C or warmer |
  | Fire struck through | the air is 5 °C or colder |
  | Lightning | a monster is within 8 blocks |
  | Water | it is raining on it |

  A condition costs two points of the stabiliser.
- **Rune order** (§43): instructions are applied in the order they were drawn where order changes
  the result (size before or after shape).
- **Damaged sigils** (§50): a sigil whose ground is struck (explosion, a block broken under its edge)
  loses strength and may turn aside, rather than vanishing outright.
- Tests: a link carries a signal; an input sigil waits for it; each condition holds and lets through.

## M6 – World and combat

**Status: built** — see [magic-2.md](magic-2.md).

**Environment** (magic specification §22), on the power of a spell or sigil:

| Where | Effect |
| --- | --- |
| rain on the caster | Fire × 0.6, Water × 1.3, Lightning × 1.3 |
| under water | Fire × 0.3, Water × 1.4 |
| the Nether | Water × 0.5 (as now), Fire × 1.2 |
| under open sky by day | Light × 1.2, Dark × 0.8; by night the other way round |
| no sky above (underground) | Earth × 1.2, Wind × 0.8 |
| high up (above y 150) | Wind × 1.3 |
| the Underworld | Light × 0.7, Dark × 1.3 |
| the End | Dark × 1.2 |

**Damage types** (full specification §28): fire spells do fire damage (fire protection holds), cold
spells freeze, lightning is electrocution, earth strikes are blunt; light, dark, water and wind stay
magic.

**Magic interference** (full specification §29): what is worn and held disturbs the hand. Every piece
of metal armour costs 0.02 stability (a chestplate 0.04), a shield in the off hand 0.04, a two-handed
weapon 0.03. Cloth, leather and the Witch King's mantle cost nothing. The cast feedback says so.

**Mobs and magic** (mob specification §19, §34):

- **Endermites** are drawn to open far gates and come to them; one within 3 blocks of a sigil or a
  standing shield unsettles it — a sigil loses strength, a shield takes harm.
- **Evokers** unsettle magic near them: a cast within 12 blocks of one is 0.1 less stable, and their
  fangs break shields.

**More ways to learn runes** (magic specification §4.1):

- **Rune tomes** in loot — dungeons, strongholds, ancient cities, the Underworld's ruins: read, they
  teach one rune the reader does not know yet, chosen at random.
- **Rune ruins** in the Nether and the Underworld too, not only the overworld.

Tests: environment factors; damage types; interference lowers stability; an evoker near unsettles a
cast; a rune tome teaches an unknown rune.

## Later

- **Rune order** (sigil §43): instructions applied in the order they were drawn, once an
  instruction exists whose result depends on it.

- **Master magic** beyond twilight (magic specification §7) — waits for the main specification.
- **Long-range and placed teleportation**: waypoints, cross-dimension gates, teleport networks
  (sigil §51).
- **The Enchanter** as a craft of its own: curses, timed buffs, protection circles, enchanted blocks
  (full specification §88).
- **Arcane engineering** (milestone 20): rune engines, magic with machines, the void-crystal resonator
  for the Tesla tower.
- **Ultra** hooks for magic.
