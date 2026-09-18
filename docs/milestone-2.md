# Milestone 2: Combat

Milestone 2 turns vanilla melee into the combat model of specification sections 27 to 36. It is
server-authoritative like Milestone 1: the server resolves every hit, block, parry and guard break,
and the client only renders the reported result.

## Design: layered on vanilla, not replacing it

Vanilla keeps what it already does well and Hardwrought adds what the specification asks for:

| Stays with vanilla | Owned by Hardwrought |
| --- | --- |
| Base weapon damage, durability, enchantments, criticals, attack cooldown | Slash / pierce / blunt split of every hit |
| Armor points and toughness | Which damage type a worn material actually answers |
| Knockback, invulnerability frames, front-arc check of a raised shield | Armor penetration, block, parry, guard break, stagger |
| Item attribute modifiers | Relative reach and attack-speed change per weapon class |

This keeps two properties that matter: every vanilla item and every other mod's item still works
without a Hardwrought profile, and the new rules stay auditable because they are one clearly bounded
stage of the damage pipeline.

## Damage pipeline

```text
vanilla damage amount
  ↓  LivingEntity#hurtServer (HEAD)          pure, no state change
     split into slash / pierce / blunt by the weapon profile
     each part meets the worn material, weakened by armor penetration
     × attack power (stamina)  × control (sprinting / airborne)  × close quarters
  ↓  LivingEntity#applyItemBlocking (RETURN)  resolves one real hit
     parry  →  most damage prevented, attacker staggered
     block  →  damage reduced, stamina spent
     guard break → nothing blocked, shield disabled, defender staggered
  ↓  vanilla armor points, absorption, health
```

The first stage is deliberately side-effect free. Vanilla reaches `hurtServer` for hits it later
discards during the damage cooldown, so charging stamina there would be wrong. The second stage runs
inside `applyItemBlocking`, which vanilla calls exactly once per hit that reaches the defender.

Damage that carries the vanilla `bypasses_armor` tag skips the material stage entirely, so falling
and starving keep behaving as before.

## Damage types

`CombatDamageType` holds all nine types of section 28, plus suffocation: section 28 lists what a
weapon or an element deals, and damage that takes the breath away is neither, so reporting it as a
blunt impact would simply be wrong. Only slash, pierce and blunt meet the armor
material; the other six pass the stage unchanged until the environmental, chemical and magic systems
define their own interactions. A hit from an item without a weapon profile is classified from the
vanilla damage source: spent air, smoke, drowning and being crushed inside a block as suffocation,
then fire, freezing, lightning, explosion and magic by their tags, projectiles as pierce, and
everything else as blunt. The `hardwrought:is_suffocating` tag is the extension point, so a later
flooded shaft or toxic gas is classified correctly by being listed there. Nothing is invented — `POISON` is reserved for the later
disease system and no vanilla source currently produces it.

## Weapon classes

`hardwrought/weapon_profiles/*.json` lists the items of one class of section 30 and the values of
section 29. Balancing starting values, not final numbers:

| Class | Items | Slash / Pierce / Blunt | Penetration | Impact | Stamina | Reach | Speed |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Sword | all six swords | 65 / 30 / 5 | 15 % | 2.5 | 0.9 | +0.3 | ×1.0 |
| Axe | all six axes | 70 / 5 / 25 | 20 % | 7.0 | 1.6 | 0 | ×0.9 |
| Mace | mace | 0 / 5 / 95 | 45 % | 9.0 | 2.0 | −0.1 | ×0.75 |
| Spear | trident | 15 / 80 / 5 | 30 % | 3.0 | 0.8 | +1.5 | ×0.95 |
| Bow | bow | 0 / 100 / 0 | 20 % | 1.5 | 0.3 | 0 | ×1.0 |
| Crossbow | crossbow | 0 / 100 / 0 | 45 % | 2.0 | 0.3 | 0 | ×0.9 |

Reach and attack speed are **relative**: reach is added to `entity_interaction_range`, speed is a
factor on `attack_speed`. They compose with the modifiers an item already carries instead of
overwriting them.

Three properties of section 29 have their own mechanics:

- **Impact** decides guard breaks. The axe profile sits above the round shield's guard-break
  threshold and the sword below it, which is section 30's "an axe is useful against shields".
- **Handling** is control quality. A swing made while sprinting or in mid-air loses damage, and a
  high-handling weapon loses less of it.
- **Minimum reach** is section 30's spear weakness: a target inside 2 blocks takes a reduced hit.

Items without a profile are not blocked from combat; they count as an improvised blunt impact with
no penetration.

## Armor

`hardwrought/armor_profiles/*.json` gives a material its answer per damage type. The resistance is a
relative reduction applied on top of the vanilla armor points of the same piece, weighted by a coarse
slot coverage (head 15 %, chest 40 %, legs 30 %, feet 15 %). That coverage is an area share, **not**
the hit zones of section 35, which need their own system.

| Material | Slash | Pierce | Blunt | Stamina drain | Insulation |
| --- | --- | --- | --- | --- | --- |
| Leather | 25 % | 15 % | 10 % | 0.05 | 0.16 |
| Chain | 50 % | 30 % | 10 % | 0.12 | 0.06 |
| Plate (iron, gold, diamond, netherite, turtle) | 70 % | 50 % | 30 % | 0.22 | 0.20 |

Against a full plate set a mace therefore lands roughly 1.75 times as hard as a sword of the same
base damage. Section 36's cloth entry has no vanilla item and is left to the later clothing content
rather than invented here.

**Armor weight** is real mass in `item_weights`, so it counts against the Milestone-1 carry capacity
of 45 kg: a full iron set is 20.2 kg, netherite 23.6 kg, leather 3.8 kg. On top of that the profile's
stamina drain is paid while moving, and the profile's insulation replaces the flat per-piece value
Milestone 1 used for body temperature.

## Blocking and parrying

`hardwrought/shield_profiles/*.json` describes section 34. The vanilla shield ships as a round
shield; buckler and tower shield of section 31 are the same value space and arrive with their items.

```text
raise shield ──5 ticks vanilla delay──► guard active
                                        ├─ hit within 6 ticks → PARRY   95 % prevented, attacker staggered 30 ticks
                                        ├─ later hit          → BLOCK   65 % reduced, 1.2 stamina per damage point
                                        └─ impact > 6.0, or not enough stamina
                                                              → GUARD BREAK  nothing blocked, shield disabled, defender staggered
```

The parry window is counted from the moment the guard becomes active, not from the key press, so it
does not depend on the vanilla block delay staying at 0.25 s. A stagger is a transient movement and
attack-speed penalty, not a potion effect, and it expires through a scheduled job.

## Stamina in combat

- A melee swing costs the stamina of its weapon class; without that stamina the swing is refused.
- Below 35 stamina an attack loses power, down to 55 % at zero.
- Blocking costs stamina proportional to the incoming damage; running out breaks the guard.
- A parry costs a flat 1.5.
- Worn armor drains stamina while moving.

## HUD

A guard bar sits below the crosshair while a shield is raised: grey while the guard comes up, gold
while the parry window is open, steel blue while the guard simply holds. The bar animates from the
local item-use timer so it stays smooth between packets, but the window length itself comes from the
server profile. A short label above the hotbar names the last result — blocked, parried, guard
broken, attack parried, or the type and amount of a hit that got through.

## Operator diagnostics

```mcfunction
/hardwrought combat
```

reports how many profiles are loaded and, for a player, the resistances of the worn armor and the
values of the weapon in hand. `/hardwrought status` now also lists the profile counts.

## Extension points

- `WeaponProfile`, `ArmorProfile` and `ShieldProfile` are the datapack schemas; `ItemProfileDefinitions`
  loads any of them and indexes one profile per item.
- `CombatSystem` owns the formulas and every state change.
- `LivingEntityMixin` holds the two seams into vanilla.
- `CombatSnapshotPayload` is the bounded server-to-client view; `CombatHud` renders it.
- `ItemWeightDefinition` now also accepts a `weights` map so a group of items with different masses
  needs one file instead of one file per item. The single-item form is unchanged.

## Not in this milestone

Deliberately left out, with the specification sections that describe them:

- Knife, two-handed weapon, war hammer and polearms (section 30) need their own items and textures.
- Attack types — thrust, heavy strike, sweep (section 32).
- The brace mechanic against charging enemies (section 33).
- Hit zones and their consequences for mobility and handling (section 35).
- Craftsmanship, heat treatment and wear as per-item state (sections 37, 78).
- Ranged draw weight and the stamina it should cost (section 30).

## Verification

`gradlew.bat runGameTest` runs 30 server tests, 16 of them new: damage-split normalization and
rejection, damage-type classification, weapon and armor and shield codec bounds, the control and
minimum-range formulas, armor coverage from really worn armor, the bundled profiles matching the
weapon classes they describe, both item-weight formats, the network protocol, and that the registered
combat jobs run without failing.

Four of them are end to end. One compares what plate does to a cut against what it does to a crushing
blow. One drives real `hurtServer` damage through the mixin to prove the stage reaches actual health
loss and not only the calculation. One raises a real shield on a defender and walks the whole guard
timeline: a hit seven ticks in is parried and staggers the attacker, the same hit twenty-five ticks in
is an ordinary 65 % block, and an axe then breaks the guard and disables the shield. The last one
confirms that a defender without a raised, profiled shield keeps the untouched vanilla result.

`gradlew.bat runClientGameTest` additionally raises a real shield on the server, confirms the client
receives the guard state and the parry window over the real network, lowers it again, and confirms a
hit is reported with its damage type.
