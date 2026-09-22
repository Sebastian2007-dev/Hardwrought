# Milestone 1: Player survival

Milestone 1 is implemented as a server-authoritative survival simulation. Player values live in
the world's `hardwrought:core` saved data and the client receives display-only snapshots once per
second. Disconnecting or restarting a world does not reset the values.

## Included systems

- **Stamina:** sprinting, swimming, climbing, jumping, attacks, mining and excess carried weight consume
  stamina. Low stamina reduces movement, jump strength and mining speed. Recovery depends on
  hydration, calories, fatigue, body temperature and load. It is deliberately a long-term reserve:
  a well-supplied player can sprint continuously for roughly eight minutes, while full idle recovery
  takes about seven minutes in ideal conditions and longer when hungry, thirsty, tired, hot, cold or
  overloaded. A full bar also covers roughly 200 attacks, 330 jumps or 1,000 mined blocks.
- **Hydration:** water drains continuously and faster during sprinting, swimming, physical work,
  heat and while wearing heavy armor. Water bottles restore hydration. The waterskin restores 24
  points, holds eight drinks and can be refilled by sneaking and using it on a water source.
- **Nutrition:** food supplies calories, protein, carbohydrates, fat, micronutrients and optional
  hydration after consumption. Ten common foods ship with datapack profiles; other vanilla foods
  use a bounded fallback based on their food component. Cold, work and excess load raise energy
  demand. Energy and dietary quality remain separate server-authoritative values.

  **Calories drive the vanilla hunger bar.** There were two hungers before this: calories, which
  nothing displayed and nothing enforced, and vanilla's shanks, which drained on their own schedule
  and meant nothing here. `SurvivalSystem.applyHunger` writes one onto the other every metabolism
  pass, so everything vanilla hangs off that bar — no sprinting when it is low, no regeneration
  until it is nearly full, starvation damage when it is empty — applies to the real number without a
  line of code for any of it. The bar reads full from **2000 kcal** up, so the four hundred above
  that are a reserve it does not draw and eating at a full bar is never wasted. Saturation carries
  dietary quality rather than a second energy figure, which is what the four nutrient tracks were
  gathered for: a player living on one food heals badly however full they are. Creative is the one
  place the coupling stands back.

  The basal rate is **1.0 kcal per second**, so a full reserve burns away in forty minutes of doing
  nothing at all — two Minecraft days. Work, cold, load and sprinting add to it, and every point of
  stamina spent is charged as 1.6 kcal, which is what ties a morning of breaking rock to being
  hungry at the end of it.
- **Carry weight:** all 41 player inventory slots are weighed. The base capacity is **85 kg**; excess
  weight slows movement and jumping and raises stamina cost. Every item says what it weighs in its
  tooltip, and the vitals panel (**H**) shows the load against the allowance.

  The numbers are game numbers, not physics — a Minecraft block is a cubic metre of stone and would
  weigh tonnes. What they are sized against is a working trip: three stacks of blocks, four tools and
  a stack of bread comes to 78 kg and is carried entirely free. A full inventory of rubble is 800 kg
  and is meant to be felt.

  Both penalties ramp gently and stop well short of crippling: movement at −30 %, jumping at −25 %,
  however much is carried. An overloaded player is heavy, not broken — they still sprint, and they
  still clear a single block. This was not always true: a block used to weigh 0.75 kg against a 45 kg
  allowance, so **one stack of cobblestone put a fresh player permanently over the limit** and the
  penalties reached their old caps of −55 % and −65 % at about two and a half stacks. The effect was
  a player at full stamina who could not sprint and could barely jump, with nothing on screen saying
  why. Hence both the softer numbers and the two readouts.
- **Fatigue:** a day-scale value. Staying awake through one full Minecraft day and night costs about
  30 of 100, so a whole cycle can always be seen through without being forced to sleep, and one night
  of good sleep clears more than a day of being awake builds up. Bad air adds to it but stays in the
  same order of magnitude as normal waking.
- **Sleep:** `V` toggles sleeping on a safe, dry surface. Vanilla beds work too, **at any hour** —
  section 11 asks that a player be able to sleep almost anywhere, and since Hardwrought sleep never
  skips the night there is nothing to protect against by refusing a bed at noon. A night shift is a
  legitimate way to live. The bed rule itself is changed rather than the two places that read it,
  because vanilla re-checks it on every tick of `Player#tick` and would otherwise throw a daytime
  sleeper straight back out. Only `when_dark` becomes `always`: a bed whose rule is `never` keeps it,
  so beds in the Nether and the End still explode. Sleep quality uses surface, shelter, bed, safety, nearby noise, ambient
  temperature, wetness and restlessness. Time never jumps: the server accelerates real ticks
  according to the percentage of sleeping players, reaching 100 TPS when everyone sleeps. Weather,
  crops, fire and every later simulation system therefore advance normally.
- **Restlessness:** nothing forces a sleeper awake. Once fatigue reaches zero the player is told
  once that they are rested, and sleeping on from there stops being rest: it turns into
  restlessness, a 0–100 value of its own. Restlessness halves stamina recovery at its maximum and
  makes the next sleep worse, and it works itself off over an ordinary waking day — far more slowly
  than it builds up. Oversleeping is a real mistake with a real cost, and staying in bed is still
  the player's decision to make.
- **Basic temperature:** biome temperature, altitude, night, rain, water, nearby fire, activity,
  sunlight, clothing and wetness determine ambient and body temperature. Thermal stress reduces
  stamina recovery, cold raises calorie demand and extreme core temperatures inflict damage. Wind was
  a declared neutral input here and is supplied for real by the Milestone-3 environment model, which
  also took over the ambient temperature; this system now only adds what is local to one player.

## Content and assets

The waterskin is crafted from four leather and one glass bottle. Its item texture is stored
at `assets/hardwrought/textures/item/filled_waterskin.png`; the generated source is retained in
`art_source/filled_waterskin_generated.png`. HUD bars are drawn in code so they scale cleanly.

## HUD layout

Ten green stamina drops sit directly above the vanilla hearts and ten blue hydration drops directly
above the hunger bar, which is itself the calorie reserve. The hydration row disappears underwater so
Minecraft's air bubbles stay clear. Empty and partially filled drops make both values readable
without a large status panel. `H` toggles a compact panel in the upper-left corner. It contains a
0–2400 kcal bar with the exact figure underneath, a thin 0–100 fatigue bar with its number, a
blue-to-red body-temperature scale with a position marker and the exact Celsius value, a 0–100
restlessness bar and the carried load. `V` remains the sleep/wake key.

Generated texture prompt:

> Create a single Minecraft-style pixel art inventory item sprite: a rugged medieval filled
> leather waterskin for a hardcore survival mod named Hardwrought. Dark brown stitched leather
> pouch, short corked neck, small cool-blue water droplet emblem, muted earthy palette, worn
> handcrafted look, strong readable silhouette. Transparent background, object centered, no text,
> no border, no shadow outside the sprite, square canvas, crisp hard pixel edges, suitable to
> downscale to 32x32 pixels.

## Main extension points

- `PlayerVitals` defines the persistent schema.
- `SurvivalSystem` owns formulas and server-side state changes.
- `CarryWeight` owns the initial fallback item-weight rules.
- `ItemWeightDefinitions` loads exact `hardwrought/item_weights/*.json` overrides.
- `FoodNutritionDefinitions` loads exact `hardwrought/food_nutrition/*.json` profiles.
- `SurvivalSnapshotPayload` is the bounded server-to-client view.
- `SurvivalHud` renders the snapshot and owns the sleep key binding.

Additional nutrition and weight profiles can be supplied by datapacks without changing the saved
player schema or network authority model. Reloads reject duplicate or invalid entries as a unit.

## Verification

The server GameTests cover persistence, validation, registry loading, item-mass overrides,
the eight reusable waterskin drinks and the HUD protocol. The client integration test sends the real sleep packet, verifies that the player
enters sleep, observes 100 TPS in single-player, wakes the player again, confirms restoration to 20
TPS, checks save/reopen behavior and records the rendered HUD screenshot.
