# Magic, stages M2 to M6

What [magic-roadmap.md](magic-roadmap.md) planned beyond [magic-1.md](magic-1.md) and
[sigils-1.md](sigils-1.md), as it is built. Tests: `MagicGameTests` and `SigilGameTests`.

## M2 – Wands and void crystal

| Wand | Recipe | Tier | Steadiness | Durability | Overload cost |
| --- | --- | --- | --- | --- | --- |
| Mithril | golden wand, 2 mithril ingots, 2 amethyst shards | 6 | +0.18 | 500 | 0.15 per tier |
| Void crystal | mithril wand, 2 void crystal shards, an echo shard | 8 | +0.25 | 300 | **0.075** per tier |

**Void ink** (a void crystal shard and an ink sac make 2) is a fifth enchanting ink (+1 level over the
limit, like mithril powder) and the better ink for scrolls (+0.05 stability).

Code: `WandTier` (`overloadCost()`), `Magic`, `VoidCrystal.INK`, `RuneWork.Ink.VOID`.

## M3 – The Mage Table and scrolls

Within 3 blocks of a Mage Table the parchment works as a bench (`CastQuality.Bench.TABLE`):
**+0.10 stability**, **no hesitation penalty**, **one tier more** than the wand carries. The client says
it drew at a table; the server checks (`MagicTableBlock.near`).

**Scrolls**: with parchment (3 paper and leather make 3) and rune ink (ink sac, glowstone dust and
amethyst shard make 2) or void ink in the inventory, the canvas at a table shows **Bind to a scroll**.
The drawing is judged once; if every spell in it holds (success or weakened) it is bound, otherwise it
goes off on the spot as a cast would. A scroll is read from the hand: it casts its drawing at once,
with the stored quality, no wand needed; the place it is read in counts (M6). It is used up; one second
between scrolls.

Code: `Scrolls`, `ScrollItem`, `SpellCaster.cast(…, mode)` and `SpellCaster.read`, `CastSpellPayload`
modes `HAND`, `TABLE`, `BIND`.

## M4 – Sigils S2: instructions in the ring

| In the ring | Instruction | Cost |
| --- | --- | --- |
| Wind | range × 1.5 each (directed sigils) | 1 |
| Water | sphere, burst or ring × 1.4 each | 1 |
| Earth | lasts twice as long each; two or more need at least a hexagon | 1 |
| Dark | acts only on monsters | 2 |
| Dark struck through | acts only on the inscriber's side | 2 |
| Lightning | 1.5 s between trigger and effect each | 2 |
| Two small squares | contact: only whoever stands on the sigil | – |
| Small circle and small triangle | a ring round the sigil | – |

Code: `SigilDesign.Program`, `SketchReader.readSigil`, `SigilBlockEntity.act` and `targets`.

## M5 – Sigils S3: links, signals, conditions

- **Linking**: sneak and use a wand on one sigil, then on another within 24 blocks: the first now
  signals the second. The same pair again unlinks them. Up to four links out; a faint thread of
  particles shows them.
- **Signals**: a sigil sends a signal along its links whenever it acts. A sigil with a link into it is
  **driven**: it never acts by itself, only when a signal arrives, after its own delay.
- **Conditions**, runes drawn outside the outer circle (2 points each):

  | Outside | Acts only when |
  | --- | --- |
  | Dark | light 7 or less |
  | Dark struck through | light 8 or more |
  | Fire | air 25 °C or warmer |
  | Fire struck through | air 5 °C or colder |
  | Lightning | a monster within 8 blocks |
  | Water | it rains on it |

- **Damaged sigils**: an explosion weakens a sigil (power × 0.6, a directed one turns a quarter round)
  instead of wiping it away; one already too weak is gone.

Not built: the **rune order** of §43 (instructions applied in the order drawn). Size and shape are
independent today, so order changes nothing yet; it is listed under Later in the roadmap.

Code: `Sigils.link`, `SigilBlockEntity` (`links`, `sources`, `signal`, `driven`, `conditionsHold`,
`damaged`), `SigilBlock.onExplosionHit`.

## M6 – World and combat

**Environment** (`MagicWorld.surroundings`), on the power of spells, scrolls read and sigils laid:

| Where | Effect |
| --- | --- |
| under water | Fire × 0.3, Water × 1.4 |
| rain | Fire × 0.6, Water and Lightning × 1.3 |
| the Nether | Water × 0.5, Fire × 1.2 |
| the Underworld | Light × 0.7, Dark × 1.3 |
| the End | Dark × 1.2 |
| open sky (overworld) | by day Light × 1.2, Dark × 0.8; by night the other way round |
| a roof overhead (not the Nether) | Earth × 1.2, Wind × 0.8 |
| above y 150 | Wind × 1.3 |

"Open sky" is read from the heightmap, so a roof counts the moment it is laid.

**Damage types** (`SpellEffects.damage`): fire spells do `hardwrought:spell_fire` (tagged `is_fire`:
fire protection holds, the fireproof feel nothing, it burns), fire turned around does
`hardwrought:spell_frost` (`is_freezing`: frostbite), lightning is `hardwrought:electrocution`, earth
is `hardwrought:spell_strike`, a blunt blow armour takes as it takes a hammer. Light, dark, water and
wind stay magic. Electrocution no longer opens wounds and counts as electric damage.

**Interference** (`MagicWorld.interference`), off the stability of spells and sigils: metal armour 0.02
a piece, a chestplate 0.04; a shield in the off hand 0.04; a two-handed weapon (item tag
`hardwrought:two_handed`: greatswords and halberds) 0.03. Metal is read from the item's name (iron,
chainmail, gold, copper, bronze, steel, netherite, mithril, titanium, tungsten, adamant); leather, cloth
and the Witch King's mantle cost nothing.

**Mobs**:
- an **evoker** within 12 blocks costs a cast 0.1 stability; its fangs rising inside a shield break it;
- **endermites** walk to far gates standing open within 24 blocks; within 3 blocks of a sigil they cost
  it 3 % of its power every second, and near a standing shield they wear it down by 1 a second.

**Learning runes**:
- the **rune tome**, found in dungeon, stronghold library and ancient city chests (one in four) and in
  the Underworld's ruins, teaches one rune its reader does not know yet and is used up; to one who
  knows every rune it teaches nothing and is kept;
- **rune ruins** now also stand in caves of the Nether and the Underworld (`rune_ruin_deep`: the
  feature looks down from where it is placed for a floor with room above).

Code: `MagicWorld`, `RuneTomeItem`, `MagicShields.unsettle`, `SigilBlockEntity.unsettle`,
`Portals.openGates`, `ModDamageTypes.SPELL_*`.
