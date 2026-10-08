# Magic, Milestone M1: Runes, Drawing, Casting, Failing

M1 implements the core of the magic specification (`runeconzept/Hardwrought_Magic_System.md`):
knowledge instead of mana, runes drawn with the mouse, a grammar that turns drawings into spells,
casting that is judged rather than passed or failed, wands that carry a limited load, and runes that
have to be found. The Mage Table, scrolls, long-range teleportation and the Enchanter are later
milestones.

## Runes

**Element runes** (section 3) say what a spell is made of. Each is a single stroke, copied from the
rune sheets in `runeconzept`:

| Rune | Shape | What it does to what it reaches | Struck through |
| --- | --- | --- | --- |
| Fire | slash rising to the right | burns | cools: freezes, slows, puts out fire |
| Water | lying wave | pushes, puts out fire, hurts water-sensitive mobs | dries: hunger |
| Wind | arch, open below | throws back and up | draws in |
| Earth | level line with a notch | strikes hard, slows | lifts off the ground (levitation) |
| Light | upright line | heals the living, burns the undead | harms the living, heals the undead |
| Lightning | zigzag, either way round | strikes, stuns, leaps on to the nearest | charges: speed and haste |
| Dark | crescent, open to the right | weakens, blinds | reveals: glowing, ends invisibility |

**Auxiliary runes** say how and where a spell acts. They are read by geometry, not by template:

| Auxiliary rune | Effect |
| --- | --- |
| **Arrow** | the spell acts where the arrow ends. Up the page is exactly where the crosshair points, pitch included; right is the caster's right, down is behind; the arrow's length against the page is the distance (up to 24 blocks, more with Wind amplifiers). |
| **Circle, rune running over its edge** | the spell acts on the caster's own body. |
| **Circle, rune wholly inside** | a shield with that element. |
| **Empty circle** | a plain shield. |
| **Circle (rune over the edge) + arrow** | the caster steps through nothing to the arrow's end (short teleport, 10 blocks). |
| **Triangle** | the spell bursts outward all round where it acts. |
| **Spiral** | the spell lingers there as a field and acts every second. |
| **Square** | the spell is laid on the ground as a trap and goes off when someone steps on it. |
| **Strike-through** (a straight line across an earlier rune) | turns that rune's element around. |

An arrow may be drawn in one stroke (shaft out, head back) or as a line and a separate head; either
way it is one auxiliary rune, coloured and counted once on the parchment. **Only in action casting**,
where the pen never lifts, may one stroke hold two things (on the parchment every stroke is one thing): a **straight stem that bends
into a rune** is that rune on an arrow pointing the way the stem was drawn, and a **loop that closes
and runs on into a rune** is that rune with its circle.

## Streams and blends

**Streams.** A spell without auxiliary runes pours its element from the hand at what is in front,
up to 6 blocks:
- it hits everything in the narrow cone along the way, and what stands where it lands;
- each element has its own look: a gout of flame, a gush of water, a gust, flung stone, a ray of
  light, a billow of darkness, a leaping bolt;
- each does its work where the stream lands: fire catches, fire is put out, a light is left hanging.

**Blends.** Every element of a spell other than its core acts as well, at 40 % of the strength, and
shows in the stream: Fire with Water burns and splashes. The usual amplifier effects (reach, area,
steadiness …) still apply.

**Preview.** Below the parchment, a line says what the drawing will do before it is cast, for
example "→ Fire stream" or "→ Light shield + Dark trap".

**Arrows** may be drawn in any way the hand draws them: in one stroke, as a shaft and a head, or as
a shaft with each half of the head drawn on its own. Every half-head stroke at the tip belongs to the
arrow, and the whole arrow counts once.

## Several spells in one drawing

A drawing can hold several spells, and all of them are cast at once:
- **Every circle, triangle or square with runes in it is a spell of its own**, made of those runes:
  - a circle with a rune wholly inside is a shield with that element;
  - a circle with a rune running over its edge acts on the caster;
  - a square with a rune in it is a trap;
  - a triangle with a rune in it is a burst.
- **The runes outside any shape are one more spell.**
- **Empty shapes:**
  - an empty triangle or square shapes the runes outside it, as before;
  - an empty circle is a plain shield.
- **Arrows and spirals** belong to the spell nearest them.
- **Light and Dark** cancel only within one spell.

The wand carries the load of all spells together: their tiers add up. Each spell is judged on its
own runes, and the feedback names every spell.

Example: Light in a circle, Dark in a square and Fire in a triangle are a light shield, a trap of
darkness and a burst of fire, all three cast together.

## Grammar

- The **first element rune** is the core.
- **Every further element rune amplifies** the core:
  - the core's own rune again: power × 1.6;
  - Wind: reach × 1.5;
  - Earth: steadier, lasts longer;
  - Water: larger area;
  - Fire: power × 1.3, but less steady;
  - Light: steadier;
  - Dark: lasts longer.
- **Light and Dark cancel each other**, pair by pair. Two Earth runes per pair stabilise them into
  *twilight*: power × 1.6 per pair, two extra tiers, double danger. This is the first "master magic"
  of section 7.
- A spell's **tier** is the number of runes (element and auxiliary) plus two per twilight pair.

Without any auxiliary rune a spell acts just in front of the caster (section 33's small flame, light
and push).

## Gates (teleports, section 24)

A circle that an arrow **starts from** is a gate rather than a shield. The arrow's tail must lie
within 1.35 radii of the circle's middle, and its tip outside that.

| Drawing | Spell |
|---|---|
| circle → arrow | **short gate** (`SHIFT`): the caster steps the way the arrow points; its length gives the distance, at most 10 blocks |
| circle → arrow → circle | **far gate** (`PORTAL`): a dialog asks for X/Y/Z; it reaches 160 blocks, scaled by the cast's power |
| either of these + a rune across the shaft | **anchored**: the rune steadies the way instead of acting as an element |

- No element rune is needed. Runes inside the gate's circles go along as elements, as they did
  for the old circle-and-arrow shift.
- **Anchors**: any element rune whose stroke crosses the shaft (`SketchReader.Gate.ANCHOR`).
  - Earth: stability +0.15, and a far gate lands within about 1 % of its distance.
  - Every other rune: stability +0.05, and halves the scatter.
  - What each rune gives on arrival: Dark makes you invisible, Wind gives slow falling and
    reach × 1.5, Water gives water breathing and puts out fire, Fire gives fire resistance, Light
    gives night vision, Lightning gives speed, Earth gives resistance.
- **Unanchored far gate**: stability −0.12. It lands up to 12 % of its distance off target and
  leaves the traveller with nausea and slowness.
- The far gate stays open for 30 seconds. It closes if the caster walks more than 6 blocks away or
  changes dimension. A target beyond its reach is shortened to the reach.
- The server finds a place to stand near the target: two free blocks over solid ground, never lava,
  searched 32 blocks up and down, and otherwise the surface of the column.
- Outcomes:
  - WEAKENED: reach × 0.7;
  - ASTRAY: scatter × 3;
  - SURGE: reach × 1.3;
  - BACKFIRE: hurts the caster and leaves them with nausea.
- In one stroke, a shaft that wavers is still an arrow when it starts at a circle (straightness
  0.7 instead of 0.85). Before this change, such an arrow was read as Lightning.

## Recognition

`RuneRecognizer` is a template matcher in the manner of the $1 recognizer:
1. The stroke is resampled to 48 evenly spaced points.
2. It is centred and scaled so its larger side is 1.
3. It is compared point by point with each template, forwards and backwards.

It is deliberately **not** rotation-invariant, because orientation is part of a rune: Fire against
Light, Wind against Dark.

`SketchReader` reads the whole drawing:
- closed strokes by their corners: circle, triangle, square;
- spirals by their winding;
- arrows by a straight shaft with a head drawn back;
- compound strokes, which it splits;
- which runes lie in or across which circle;
- which lines strike which runes.

Recognition runs on the client to label the strokes as they are drawn, but **only the server's
reading counts**: the client sends the raw strokes (`CastSpellPayload`), never what it thinks they
were.

## Casting

- **Controlled casting** (sections 8 to 10): right-click with a wand opens the parchment. Draw one rune
  per press-drag-release, each in its own colour, then cast with Enter, Space, right-click on the
  parchment or the button. Backspace undoes, and "Rune Lore" shows the runes the caster knows.
- **Action casting** (section 11): hold **G** (rebindable) with a wand in hand.
  - The mouse draws on a faint sheet in the middle of the view instead of turning the head.
  - A left click ends one rune and starts the next.
  - Letting go of G casts at once.

## Judging a cast (sections 12 to 16)

`CastQuality` turns the drawing into three numbers:
- **accuracy**: how close the strokes were to their runes;
- **stability**: accuracy, plus the spell's own steadiness, plus the wand's; minus overload and
  hesitation (strokes over 2.5 s). It is multiplied by 0.8 for every rune the caster does not know,
  and by 0.6 for a stroke that read as nothing;
- **power**: 0.4 + 0.63 × accuracy, +3 % for a fast clean cast. This reproduces the specification's
  example: 96 % accuracy gives about 103 % power.

The outcome follows from stability:

| Stability | Outcome |
| --- | --- |
| ≥ 0.68 | success |
| ≥ 0.50 | weakened: power × 0.6, reach × 0.7 |
| ≥ 0.32 | medium failure, by the worst-drawn rune (see below) |
| < 0.32 | backfire: the spell acts where the caster stands, on them too |

A medium failure depends on which rune was drawn worst:
- the core → the **wrong element**, namely the rune it most resembled;
- an auxiliary rune → **astray**: turned 40°, or sent outward instead of onto the caster;
- an amplifier → a **surge**: stronger, and some of it comes back through the hand.

None of this is random. How bad a backfire is depends on the spell's *danger* (element × shape ×
power): a light spell gone wrong heals; a strong fire spell gone wrong is an explosion. Explosions
never break blocks.

## Wands (sections 17 to 20)

| Wand | Recipe | Tier | Steadiness | Durability |
| --- | --- | --- | --- | --- |
| Wooden | pointed stick, stick, leaf string | 2 | +0 | 64 |
| Iron | wooden wand, 2 iron ingots | 4 | +0.04 | 250 |
| Golden | wooden wand, 2 gold ingots, amethyst shard | 5 | +0.12 | 120 |

- Every cast wears the wand by 1, plus 6 per tier of overload. Each tier of overload also costs 0.15
  stability.
- A wand overloaded by four tiers or more, or worn out by the cast, **breaks**, and the spell
  destabilises: astray, or a backfire from two tiers over.
- After a cast the wand needs a short rest (6 ticks + 4 per rune). A cast in that time is refused
  with a word, not silently.
- Mithril and void crystal wands wait for those materials.

## Knowledge (sections 1, 4, 5)

`RuneKnowledge` is a Fabric attachment on the player:
- one bit per glyph (12: six element runes, six auxiliary runes);
- persistent, kept through death;
- synced only to the player it belongs to.

A new player knows nothing. There are two ways to learn:
- **Ruins**: a broken ring of old stone with a rune stone on a plinth, about one in 48 chunks in the
  overworld. Right-click the carving to learn its rune. Every glyph, auxiliary ones included, has a
  stone.
- **Experiment**: a rune the caster does not know still works if drawn well, only less steadily. If it
  is drawn at 60 % accuracy or better and the spell does not fizzle, it is understood afterwards.

Names in the cast feedback and on the parchment stay "?" until a rune is known.

Operators can use `/hardwrought magic learn <player> <rune|all>` and `/hardwrought magic forget <player>`.

## Environment (section 22)

Water magic has half the power in the Nether.

## Shields (section 25)

A shield is a sphere that moves with its caster:
- Living things are pushed out of it.
- Projectiles crossing it in either direction stop at it.
- A blow crossing it, from outside in or from inside out, lands on the shield instead. The caster
  cannot attack out of it.
- It holds 8 × power × duration harm and stands 7 s × duration.
- With an element, attackers who strike it feel that element at half strength.

The clients draw it as a real tessellated sphere (`FxDecal.Kind.SPHERE`, `fx_sphere.fsh`), seen from
inside as well as out. It lives exactly as long as the server's shield (`ShieldPayload`: up, hit,
down), flares when struck and shatters when it breaks.

- **Standing shield**: a shield drawn with a spiral (e.g. Light in a circle + a spiral) stays where
  it is raised, at the caster's position or at the arrow's end:
  - radius 3.6 instead of 1.7, three times the life, 1.5 times the strength;
  - players pass freely; everything else is pushed out, and blows and projectiles stop at it as
    usual;
  - one standing and one personal shield per caster.

## Effects

Spells use the FX module. New presets:
- `LIGHT_ORB`: the plain Light rune, a hovering light;
- `TELEPORT`: drawn in at the start, a streak of sparks, a burst on arrival;
- `SIGIL`: a faint rune on the ground marking a trap or a field while it lasts.

## Files

- `magic/`:
  - drawing and reading: `Rune`, `Sign`, `Glyph`, `RuneRecognizer`, `SketchReader`;
  - spells and judging: `Spell`, `CastQuality`;
  - casting and effects: `SpellCaster`, `SpellEffects`, `MagicFields`, `MagicShields`, `MagicTasks`;
  - wands and knowledge: `WandTier`, `WandItem`, `RuneKnowledge`;
  - in the world: `RuneStoneBlock`, `RuneRuins`;
  - gates: `Portals`;
  - networking: `CastSpellPayload`, `ShieldPayload`, `GatePayload`, `GateTargetPayload`;
  - registration: `Magic`.
- `client/magic/`:
  - drawing: `RuneCanvasScreen`, `ActionCasting`, `InkStroke`, `RuneInk`;
  - shields: `ShieldSpheres`;
  - gates: `GateScreen` (coordinate entry);
  - setup: `MagicClient`.
- `client/mixin/MouseHandlerMixin`: the pen takes the mouse while drawing.

## Verification

- `MagicGameTests`:
  - reading every rune, auxiliary runes and compound strokes;
  - the grammar and the judging;
  - self-heal and learning by experiment;
  - shields both ways, wand wear and breakage;
  - the arrow, the teleport and the trap;
  - the Nether and the ruins;
  - gates: reading them (short, far, anchored, wavering arrow), the short step, and the far step
    to named coordinates.
- `MagicClientGameTest` photographs action casting, a shield from inside and out, a teleport, and
  the far gate's coordinate dialog, then steps through it.
