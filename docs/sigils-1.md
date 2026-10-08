# Sigils, Milestone S1

Sigils are the second magic system of the specification (`Hardwrought_Sigil_System.md`):
- prepared, geometric constructions that are laid on the ground and keep working by themselves;
- like a spell's trap, but more elaborate (section 1: "a small magical program").

S1 covers the core set: element, stabiliser and capacity, direction, shape, trigger, pulse and
strength. Conditions, signals and linked sigils (sections 39 to 42) are later milestones.

## Drawing a sigil

A sigil is drawn like a spell, on the parchment or in action casting. What makes it a sigil is its
frame: **two circles, one inside the other** (section 2). Cast with a wand while looking at solid
ground within 6 blocks, it is inscribed there. Without ground under the gaze it is refused, with a
word.

| Part | Where | Meaning |
| --- | --- | --- |
| Main element | inner circle | what the sigil is made of: any element rune; struck through, turned around |
| Stabiliser | inner circle, around the element | a figure of 3 to 8 corners; carries one instruction per corner (sections 5 and 6) |
| Arrows | ring between the circles | direction, all arrows summed. A short stroke across an arrow's tail is its foot and costs nothing. |
| Short stroke | ring | +35 % strength each (section 13) |
| Small circle | ring | shape: sphere, the effect reaches all round |
| Small triangle | ring | shape: burst |
| Small square | ring | trigger: waits until someone comes within 2.5 blocks (as a spell's square makes a trap) |
| Spiral | ring | pulse: acts twice as often |

These are the spell auxiliary runes again, so that a rune means the same everywhere (section 9).

**Direction** (sections 10, 11):
- Up the page is ahead of whoever inscribes the sigil.
- Arrows that cancel out, such as four pointing in from four sides as in the specification's example,
  leave the element **hovering 1.3 blocks above the sigil**, acting on whatever comes into it.
- Arrows that point somewhere make it **send its element off** that way, up to 16 blocks, again and
  again, stopping at walls and at the first living thing.

## Capacity, judging, failure (sections 6, 7, 49)

**Capacity**:
- every instruction costs 1; the stabiliser carries as many as it has corners;
- a sigil's tier (wand load) is corners − 2: a triangle or square is light, an octagon is tier 6.

**Stability** is accuracy + wand steadiness, then reduced:
- −0.15 per tier of wand overload;
- −0.25 per instruction beyond capacity;
- −0.08 per stroke that makes no sense in the sigil;
- × 0.8 per rune the inscriber does not know;
- × 0.5 if there is no stabiliser at all.

**Outcomes**, by stability:

| Stability | Outcome |
| --- | --- |
| ≥ 0.68 | inscribed cleanly |
| ≥ 0.50 | weakened: power × 0.6 |
| ≥ 0.32, with over-capacity or no stabiliser | **unstable**: the sigil lies down and gives way 3 to 7 s later, bursting where it lies |
| ≥ 0.32, otherwise | **turned aside**: a hovering sigil gains a direction, a directed one turns a quarter round |
| < 0.32 | it bursts at once where it was to lie |

Inscribing wears the wand like a spell. Unknown runes drawn well are learned, as with spells.

## At work

`SigilBlock` is light lying on a block:
- invisible to the block renderer, no collision;
- broken by hand in an instant, drops nothing;
- gone if its ground goes.

`SigilBlockEntity` keeps the program and carries it out:
- **hovering**: acts every second (pulse: twice a second) on what is within reach of its point
  (1 block, a sphere 3.5, a burst bursting);
- **directed**: shoots every 1.5 s (pulse: 0.75 s);
- **waiting**: idle until someone other than its maker comes near. Each approach uses one of
  8 + 2 × strength charges;
- **lifetime**: twenty minutes for a sigil that works all the time, an hour for a waiting one;
- **after a restart**: it keeps working;
- **damage source**: its magic counts as its maker's while they are online, and as nobody's
  otherwise.

## What it looks like

The clients lay **the drawing itself** on the ground in glowing ink, in the element's colour:
- 2.8 blocks across, the right way round for whoever drew it, so a sigil can be read like a diagram
  (section 52);
- a hovering element shows above it: flame and sparks, drops, a whirl of air, dust, glints, smoke or
  crackling sparks;
- a directed or waiting sigil shows only a faint glint;
- a little light comes from the shader lights.

## Lightning (new element)

- **Rune**: a zigzag: down to the left, a step to the right, down to the left again.
- **What it does**: strikes for 3.5 × power and stuns for a moment, then leaps to the nearest living
  thing within 5 blocks, 60 % as strong at every leap. It makes two leaps, one more for a strong spell
  and one more with a Lightning amplifier. Targets in water or rain take half again.
- **Earth grounds Lightning**: each Earth–Lightning pair cancels, as Light and Dark do.
- **Struck through**: it charges instead (speed and haste).
- **On oneself** (across a circle): a shock. Struck through on oneself: charged.

## Files

- `magic/`:
  - reading: `SigilDesign`, and `SketchReader.readSigil` (circles, polygon corners by Douglas–Peucker,
    instructions);
  - inscribing and working: `Sigils` (inscribing), `SigilBlock`, `SigilBlockEntity`.
- `client/magic/ClientSigils` draws the ink and the hovering element.

## Verification

- `SigilGameTests`:
  - reading the specification's example and variants;
  - a hovering fire burning what steps in;
  - a directed sigil hitting a target;
  - a waiting sigil waiting;
  - an overfull triangle giving way;
  - Lightning grounded by Earth, and leaping on.
- `MagicClientGameTest` photographs the example sigil from the side and from above.
