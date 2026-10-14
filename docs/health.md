# Diseases

Being ill: catching something, how it runs, what it does, wounds and keeping them clean, and medicine from
the first herb tea to antibiotics.

## The course of a disease (`health/Diseases`, `Health`, `Disease`)

Each disease has a **load** from 0 to 100 and an **age** in seconds.

1. **Taking hold** — for its incubation nothing is felt.
2. Then it **grows**: `growth × (1 − time since it broke out / course)`, plus neglect, minus
   `recovery × care` every second. The body's own fight makes it grow less and less, so a body that is
   looked after throws it off by itself.
3. Stages by load: **ill**, **badly ill** at 50 (it takes its worse name), **critical** at 80, and
   **getting better** once it has fallen 5 below its peak.
4. At 0 it is gone: the player is proof against it for **10 minutes** and from then on knows it by name.

| Disease | Worse form | Caught from | Incubation | Course | What it does |
|---|---|---|---|---|---|
| Stomach bug | Dysentery | bad water (instead of the old thirst), raw meat, rotten flesh, spider eye, poisonous potato | 60 s | 10 min | costs water (the thirst effect, stronger when severe), nausea now and then |
| Cold | Pneumonia | two minutes with the body below 35.5 °C, or wet and below 36.5 °C | 120 s | 15 min | fever, coughing that costs stamina; keeps growing in the cold and wet |
| Inflamed wound | Blood poisoning | a scald that turns (see `Injuries`), dirt in an open wound (below) | 90 s | 15 min | fever, weakness; from load 60 one health every 30 s, never below three hearts |

**Critical**, any disease: one health every 20 seconds, with no floor. Only reached by neglect.

Health an illness costs is taken from within, not dealt as a blow: it does not wake a sleeper. Only the
last point is dealt as damage (`hardwrought:illness`), so the death message reads "succumbed to illness".

**Care** multiplies recovery: sleeping ×3, standing still ×1.3; short of water or of food ×0.6 each; a
body far from its temperature beyond what the fever accounts for ×0.5; the right tea ×3 (chamomile for
the stomach) or ×2 (willow bark for a cold or a wound; fever pills ×2.5); antibiotics ×4 against a cold or
a wound, ×2 against a stomach bug.

**Fever** drives the body's target temperature up by 0.8 to 3 °C with the load of a cold or a wound;
willow bark cuts it to 40 %, fever pills to 20 %.

## Being ill at all

- hunger ×1.5, fatigue ×1.8, breath (stamina) back ×0.7 as fast;
- lying in bed past being rested does not make an ill player restless, and they are not told it would.

## The first medicines (`health/Herbs`, `TeaItem`)

- **Chamomile:** an oxeye daisy dried on the drying rack.
- **Willow bark:** comes off a birch log, half the time, when an axe strips it.
- A bowl of water and the herb make an **infusion** (crafting); on a campfire it boils into **tea** in 20
  seconds, hot like boiled water. Drunk, it is purified water and works for five minutes: *Settled
  Stomach* (chamomile) or *Fever Eased* (willow bark).

## Wounds (`health/Wounds`)

- A **bite** (zombie and its kin, wolf, spider, silverfish, endermite, polar bear; 1 damage or more) or a
  **heavy blow** from a monster (4 or more) leaves an **open wound** for two minutes. A bite is dirtier:
  twice the chance.
- While it is open and not bandaged, every two seconds 0.8 % (bite 1.6 %) that dirt gets in: a wound
  infection. Over the two minutes about four in ten for a blow.
- A **bandage** closes it for five minutes; a new bite or blow tears it off.

## Medicine

| Item | How | Does |
|---|---|---|
| Bandage | 3 leaf string → 2, or any wool → 4 | closes an open wound for five minutes |
| Spirit-soaked bandage | bandage + spirit or antiseptic | as a bandage, and cleans 15 of a wound infection |
| Mash | 3 wheat, sugar, a water bucket → 4 | distilled in the still with a glass bottle into spirit |
| Bottle of spirit | still: mash + 1 bottle | poured on a wound: stings (1), closes it, cleans 10; the bottle comes back |
| Antiseptic | chemical reactor: caustic soda, a glass bottle, 2 chlorine | closes a wound, cleans 25; the bottle comes back |
| Fever pills (4) | chemical reactor: 2 willow bark, 50 mB naphtha | willow bark, stronger, for ten minutes |
| Antibiotics (2) | chemical reactor: brown mushroom, sugar, 50 mB naphtha | ten minutes of antibiotics |
| Electrolyte drink | a bowl of water, salt, sugar | purified water and five minutes of hydration regeneration |

Bandages and medicine are used like a drink: held for a moment.

## Eating past full (`survival/Overeating`)

- With a full hunger bar a player can eat on, up to as much again as the bar holds (20 points). What does
  not fit in the bar is kept as **stuffed**, shown as gold shanks over the hunger bar, and digested into
  the bar first, a point a second: the bar does not fall while any is left.
- A full belly weighs, by how much of the room past full is taken:

| Stuffed | Movement | Stamina used | Also |
|---|---|---|---|
| from a quarter | −8 % | ×1.15 | |
| from half | −18 % | ×1.35 | |
| from three quarters | −30 % | ×1.6 | no jumping, no stepping up a slab or stair |

- Death ends it, like being ill.

## Seen on the body (`health/Appearance`, client `BodyMarks`)

Everyone sees, of everyone: a bandage round the right arm, an open or infected wound on the left, shadows
under the eyes and sweat on the brow of a sick player — drawn over the skin like armour — and a stuffed
player with a belly: the torso, and only the torso, up to 80 % deeper and 15 % wider. Each mark is painted
on both layers of the skin, so a sleeve, jacket or hat cannot hide it.

## Shown

- The portrait in the details panel (H) has a sickly green frame while anything is felt.
- Under it, one line per disease: its name and stage once the player has been through it before;
  before that only what it does ("Fever and a cough").
- Chat tells when it is first felt, worsens, turns critical, turns for the better, and is gone.

What a player has is kept on the player (an attachment, synced to their own client only), so death ends it.

## Tests

`HealthGameTests`: a stomach bug taking hold unnoticed, felt, making the player hungrier and wearier and not
restless, thrown off without turning critical, then known and not caught again; chamomile and water
helping, thirst hindering; a cold caught in the cold and wet, bringing a fever that willow bark lowers, and
growing worse left in the cold; bad water and raw chicken upsetting the stomach, cooked chicken not; a bite
opening a wound that gets infected left open and not when bandaged, and a new bite tearing the bandage off;
antiseptic and spirit cleaning an infection, antibiotics fighting a cold, fever pills, mash distilled to
spirit and the reactor's three medicines; a tea drunk and working.
