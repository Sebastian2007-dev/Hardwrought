# Hardwrought
## Mob System Specification

---

# 1. Design Goal

Hardwrought should make hostile mobs more dangerous through behavior, interaction with the world, coordination, and specialization rather than through simple health and damage inflation.

Core principle:

> Mobs should solve problems, but they should not be omniscient.

They should react to:

- sight
- sound
- movement
- damage
- light
- nearby allies
- terrain
- doors and obstacles
- player behavior

The goal is to reduce simple Minecraft cheese tactics while keeping enemy behavior understandable and fair.

A player should generally be able to recognize why a mob is dangerous.

---

# 2. Shared Perception System

Many hostile mobs can use a shared perception layer.

```text
PERCEPTION
├─ Vision
├─ Hearing
├─ Movement Detection
├─ Damage Direction
├─ Light Detection
└─ Shared Aggro
```

Possible behavior:

```text
Player mines ore
↓
Mining sound propagates
↓
Nearby mob hears sound
↓
Mob investigates
↓
Mob may discover player
```

Mobs should not simply know the player's position through walls without a reason.

---

# 3. Shared Anti-Cheese Philosophy

Hardwrought should counter common cheese methods through world rules and mob behavior rather than invisible buffs.

Examples:

```text
Pillar Cheese
→ ranged mobs
→ climbing mobs
→ structure-aware mobs
→ knockback threats
```

```text
1×1 Hole
→ small mobs
→ tool-using zombies
→ waiting / path adaptation
```

```text
Door Cheese
→ some mobs can break doors
→ some mobs search alternate openings
```

```text
Water Cheese
→ drowned
→ stamina loss
→ cold
→ currents
```

```text
Skybase
→ structural physics
→ flying mobs
→ altitude hazards
→ resource logistics
```

---

# 4. Mob Role Categories

Hostile mobs can be grouped into broad roles.

```text
HUNTER
→ actively pursues players

SIEGE
→ attacks or bypasses defensive structures

RANGED
→ pressures players from distance

SUPPORT
→ buffs allies or controls areas

CONTROL
→ slows, displaces, poisons, blinds or traps

AMBUSH
→ hides or waits for good attack opportunities

TANK
→ absorbs damage and pressures space

ARTILLERY
→ long-range area pressure

ENVIRONMENTAL
→ becomes dangerous because of interaction with heat, gas, terrain or weather
```

A mob can belong to more than one category.

---

# 5. Zombie

Primary role:

```text
Hunter
+
Siege
```

Zombies should be persistent, slow pressure enemies.

## 5.1 Tool-Dependent Block Breaking

A zombie's ability to destroy blocks depends on the item it spawned with.

This should be visible to the player.

### Zombie Without Tool

A zombie without a tool can only break or manipulate materials that the player could also reasonably handle with bare hands.

Examples:

- loose soil
- weak vegetation
- selected fragile blocks
- weak barricades if appropriate

It cannot mine stone or chop through proper wooden structures.

### Zombie With Axe

```text
Zombie + Axe
→ can attack wooden structures
```

Possible targets:

- wooden doors
- planks
- logs
- wooden barricades
- fences
- wooden trapdoors
- weak timber supports

Efficiency depends on the quality of the axe.

Example:

```text
Wooden / Stone Axe
→ slow

Iron Axe
→ medium

High-Quality Metal Axe
→ faster
```

### Zombie With Pickaxe

```text
Zombie + Pickaxe
→ can attack stone structures
```

Possible targets:

- cobblestone
- stone
- weak masonry
- certain ore-bearing blocks

The pickaxe material still matters.

A primitive tool should not let a zombie destroy reinforced late-game walls.

### Zombie With Shovel

```text
Zombie + Shovel
→ excels at soil and loose terrain
```

Possible targets:

- dirt
- sand
- gravel
- clay
- mud

This allows zombies to dig through weak terrain around simple defenses.

## 5.2 Group Pressure

Multiple zombies attacking the same structure can reduce the time required to break through it.

The scaling should have diminishing returns so large hordes do not instantly delete structures.

Example concept:

```text
1 Zombie
→ base speed

2 Zombies
→ noticeably faster

5 Zombies
→ significantly faster

20 Zombies
→ not 20x faster
```

## 5.3 Path Selection

If normal pathfinding fails:

```text
Path blocked
↓
Check nearby openings
↓
Check doors
↓
Check weak structure
↓
Use tool if appropriate
```

Zombies should prefer valid weak points over randomly destroying blocks.

---

# 6. Husk

Primary role:

```text
Hunter
+
Endurance Pressure
```

Husks should remain dangerous in hot, dry areas.

Possible properties:

- very high heat tolerance
- long pursuit time
- reduced need to disengage
- strong resistance to desert conditions

Their attacks can cause:

```text
Hydration Loss
+
Stamina Recovery Reduction
```

This makes combat in deserts more dangerous without simply increasing damage.

---

# 7. Drowned

Primary role:

```text
Aquatic Hunter
+
Control
```

Drowned should remove water as a universal escape tool.

Possible behavior:

- improved swimming speed
- attacks from below
- detects movement in water
- can pull or pressure players downward
- damages boats
- uses deep water tactically

Trident variants:

- maintain range
- pressure players toward deeper water
- target boats
- reposition frequently

---

# 8. Skeleton

Primary role:

```text
Ranged
```

Skeletons should behave more like active ranged fighters.

Possible behaviors:

- seek cover
- keep distance
- strafe
- relocate after repeated shots
- retreat from melee
- prefer elevated positions

Possible shot types:

```text
Quick Shot
→ fast
→ lower precision

Accurate Shot
→ slower
→ better aim

Charged Shot
→ slow
→ higher penetration
```

Skeletons should not have perfect aim.

Accuracy can depend on:

- distance
- movement
- darkness
- obstruction
- target speed

---

# 9. Stray

Primary role:

```text
Ranged
+
Control
```

Strays can specialize in cold-based pressure.

Their arrows can cause:

- movement slowing
- reduced stamina regeneration
- reduced body temperature

This becomes especially dangerous in already cold environments.

---

# 10. Bogged

Primary role:

```text
Ambush
+
Poison Control
```

Possible behavior:

- uses vegetation as cover
- prefers swampy or overgrown terrain
- poison arrows
- retreats after firing
- attempts to keep obstacles between itself and the player

Its poison should use Hardwrought's actual poison and tolerance systems.

Repeated exposure may eventually increase poison resistance.

---

# 11. Spider

Primary role:

```text
Climber
+
Ambush
```

Possible abilities:

- climb walls
- traverse ceilings
- jump gaps
- detect nearby movement
- approach from unexpected angles

Spiders can create limited webbing.

Webs can:

- slow players
- obstruct routes
- interfere with doors
- create dangerous chokepoints

Web creation should be limited so forests and bases do not become completely covered.

---

# 12. Cave Spider

Primary role:

```text
Ambush
+
Poison
+
Small-Space Hunter
```

Possible abilities:

- move through very small openings
- use darkness
- attack from walls and ceilings
- retreat after poisoning
- exploit ventilation shafts and cave cracks

A 1×1 opening should not automatically guarantee safety from cave spiders.

---

# 13. Creeper

Primary role:

```text
Close-Range Burst
```

The Creeper should not become an intelligent base demolition unit.

That would make Mob Griefing too destructive.

Instead, its main upgrade should make the explosion harder to avoid.

## 13.1 Explosion Leap

When a Creeper has already ignited, it can perform a short leap toward the player shortly before detonation.

```text
Creeper ignites
↓
short delay
↓
leap toward player's direction
↓
explosion
```

Goals:

- make simple backpedaling less reliable
- preserve the Creeper's original identity
- avoid deliberate structure targeting
- keep Mob Griefing manageable

The leap should be readable through animation and sound.

It should not perfectly home onto the player after leaving the ground.

---

# 14. Charged Creeper

Primary role:

```text
Enhanced Burst Threat
```

Charged Creepers should remain rare.

Possible enhancements:

- stronger leap
- larger electrical disturbance
- greater explosion intensity

If electrical systems are implemented later, charged explosions may temporarily interfere with nearby electrical devices.

This should remain secondary to the Creeper's basic explosion role.

---

# 15. Enderman

Primary role:

```text
Mobility
+
Position Control
```

Possible behavior:

- aggressive teleport positioning
- projectile avoidance
- attempts to appear behind or beside the player
- changes combat distance rapidly

Possible interaction with lighting:

```text
Nearby exposed light source
→ chance to remove or displace it
```

Important restriction:

Endermen should not randomly dismantle critical machines or complex player infrastructure.

Their block interaction should be limited to suitable natural or simple blocks.

---

# 16. Witch

Primary role:

```text
Support
+
Control
+
Alchemy
```

Witches should become dangerous because they adapt their potion selection to the combat situation.

Possible rough decision logic:

```text
Heavy Armor
→ weakening / corrosion style effect

Fast Player
→ slowing effect

Low Health Target
→ offensive potion

Large Mob Group
→ ally support potion
```

Possible behavior:

- buffs nearby mobs
- heals selected allies
- uses poison or smoke clouds
- seeks cover
- stays behind melee mobs
- retreats when pressured

---

# 17. Slime

Primary role:

```text
Space Control
+
Swarm
```

Possible properties:

- passes through relatively small gaps depending on size
- large slimes push players
- large slimes can block corridors
- splitting remains central
- BUT nolonger deal damage

Physical resistances can depend on attack type.

Example:

```text
Blunt
→ less effective

Cutting / Piercing
→ more effective
```

Exact balance remains open.

---

# 18. Silverfish

Primary role:

```text
Mining Ambush
+
Swarm
```

Silverfish should be strongly connected to underground activity.

Possible triggers:

- mining vibration
- disturbed infested stone
- excavation near nests

Example:

```text
Deep mining
↓
Nest disturbed
↓
Silverfish swarm emerges
```

They should be individually weak but dangerous in groups.

Possible later interaction:

- damage weak structural supports
- spread through cracks

This should be limited enough to avoid uncontrollable destruction.

---

# 19. Endermite

Primary role:

```text
Arcane Disruption
```

Possible behavior:

- attracted to teleportation
- interferes with unstable magic
- destabilizes nearby rune effects
- appears around dimensional anomalies

This can make Endermites a meaningful enemy for advanced magic users.

---

# 20. Phantom

Primary role:

```text
Sleep Quality Punishment
```

Phantoms should no longer punish the player simply for choosing not to skip the night.

Hardwrought should reverse the vanilla logic.

## 20.1 Trigger

Phantoms are associated with repeated poor sleep quality.

Example:

```text
Good Sleep
→ recovery
→ no Phantom pressure

Repeated Poor Sleep
→ Sleep Debt
→ Exhaustion
→ Phantom Risk
```

A single bad night should usually not trigger them.

Risk increases after several consecutive poor-quality sleeps.

## 20.2 Sleep Quality

Possible conceptual ranges:

```text
70–100%
→ good recovery

40–69%
→ mediocre recovery

0–39%
→ poor recovery
```

Repeated low-quality sleep increases a hidden or semi-hidden:

```text
Sleep Debt
```

Phantom spawn chance can depend on Sleep Debt.

## 20.3 Design Principle

Players should not be punished for staying awake and engaging with dangerous nighttime gameplay.

They should be punished for repeatedly neglecting proper recovery.

This makes:

- shelter
- bedding
- warmth
- dryness
- safety
- quality sleep

meaningful.

---

# 21. Blaze

Primary role:

```text
Ranged
+
Environmental Heat
```

Blazes should interact with Hardwrought's temperature system.

Possible behavior:

```text
Blaze nearby
→ local temperature rises
```

Attacks can:

- ignite players
- ignite flammable materials
- increase room temperature
- consume oxygen through fire

Groups of Blazes in enclosed spaces should become especially dangerous.

---

# 22. Magma Cube

Primary role:

```text
Heat Pressure
+
Space Control
```

Possible behavior:

- creates temporarily hot surfaces
- strongly heats nearby environment
- can heat metal armor

Heavy armor can become a disadvantage in extreme heat.

---

# 23. Ghast

Primary role:

```text
Artillery
```

Ghasts should remain long-range pressure enemies.

Possible improvements:

- better target selection
- attacks from long distance
- uses explosions to force movement
- attempts to flush players from exposed positions

Explosion damage to structures should depend on material resistance.

Massive stone or reinforced structures should withstand more than weak construction.

---

# 24. Piglin

Primary role:

```text
Organized Fighter
```

Piglins should behave more like a coordinated group.

Possible roles:

```text
Melee
Crossbow
Shield
Leader
```

Possible behavior:

- melee units protect ranged units
- crossbow units seek firing positions
- groups sound alarms
- patrol areas
- react strongly to theft
- pursue intruders

---

# 25. Piglin Brute

Primary role:

```text
Tank
+
Guard Breaker
```

Possible behavior:

- high stagger resistance
- strong melee pressure
- guard break attacks
- aggressive pursuit
- low mobility compared with lighter mobs

It can attack doors and weak barriers but should not become a universal mining mob.

---

# 26. Hoglin

Primary role:

```text
Charge
+
Impact
```

Hoglins should use powerful charge attacks.

```text
Charge
↓
Impact
↓
Knockback
```

This creates strong synergy with Hardwrought's spear Brace mechanic.

```text
Braced Spear
+
Charging Hoglin
→ powerful counterattack
```

---

# 27. Zoglin

Primary role:

```text
Berserker
```

Possible properties:

- very aggressive
- attacks almost everything
- high stagger resistance
- reduced pain reaction
- difficult to stop once charging

---

# 28. Wither Skeleton

Primary role:

```text
Elite Melee
+
Optional Ranged
```

Wither Skeletons should have equipment variation.

## 28.1 Sword Variant

Most Wither Skeletons can still spawn with melee weapons.

Possible behavior:

- aggressive melee
- uses reach well
- occasional defensive movement
- causes Wither / necrotic effects
- attempts to pressure weakened players

## 28.2 Bow Variant

Wither Skeletons should have a chance to spawn with bows.

Conceptual spawn split:

```text
Majority
→ Sword

Minority
→ Bow
```

Exact percentages should be tuned later.

Bow Wither Skeletons should use the unusual vanilla behavior associated with Wither Skeletons using bows:

```text
Bow Variant
→ fires flaming arrows
```

This creates ranged pressure inside Nether Fortresses.

## 28.3 Group Roles

```text
Sword Wither Skeleton
→ melee pressure

Bow Wither Skeleton
→ ranged fire support
```

Groups should become more dangerous through role composition rather than inflated stats.

---

# 29. Guardian

Primary role:

```text
Aquatic Territory Control
```

Possible behavior:

- coordinate with nearby Guardians
- protect specific water regions
- pressure boats and swimmers
- exploit open water mobility
- maintain distance

Water should strongly favor Guardians.

---

# 30. Elder Guardian

Primary role:

```text
Territorial Controller
```

Possible additions:

- larger territorial influence
- stronger coordination of nearby Guardians
- area debuffs
- environmental pressure

Its presence should make underwater structures feel controlled rather than randomly populated.

---

# 31. Shulker

Primary role:

```text
Defensive Turret
+
Control
```

Possible behavior:

- uses defensive positions
- creates crossfire
- coordinates projectile timing
- exploits Levitation to expose players

Multiple Shulkers should become dangerous because they combine firing angles.

---

# 32. Vindicator

Primary role:

```text
Melee Assault
```

Possible behavior:

- high melee pressure
- flank attempts
- door breaking
- attacks weak wooden barriers

It should behave like a dedicated close-combat raider.

---

# 33. Pillager

Primary role:

```text
Organized Ranged
```

Possible behavior:

- uses cover
- maintains distance
- focus fire
- retreats when rushed
- alarms nearby Illagers
- fights in formation

---

# 34. Evoker

Primary role:

```text
Battle Mage
+
Support
+
Control
```

Possible behavior:

- summons Vexes
- creates dangerous control zones
- protects allied Illagers
- maintains range
- disrupts player positioning

Later, advanced Evokers may interact with Hardwrought's magic system.

---

# 35. Vex

Primary role:

```text
Mobile Assassin
```

Vexes can continue to bypass walls, but they should require attack windows.

Possible behavior:

```text
Intangible movement
↓
Materialize
↓
Attack
↓
Retreat
```

This gives the player a predictable moment to counterattack.

---

# 36. Ravager

Primary role:

```text
Siege
+
Impact
```

Ravagers are appropriate structure-pressure mobs.

Possible targets:

- doors
- fences
- wooden barricades
- weak timber walls
- lightweight construction

They should not destroy reinforced masonry or steel without limit.

---

# 37. Warden

Primary role:

```text
Extreme Environmental Predator
```

The Warden should remain something the player generally wants to avoid rather than farm casually.

Possible detection:

- footsteps
- mining
- explosions
- machinery
- heavy impacts
- structural collapse

Example:

```text
Heavy machine operating underground
↓
Vibration
↓
Warden activity increases
```

Possible abilities:

- tracks vibration
- investigates machinery
- damages weak obstacles
- pressures players out of fortified positions

The Warden should not simply mine directly through every material.

---

# 38. Breeze

Primary role:

```text
Displacement
+
Environmental Interaction
```

Wind attacks can:

- push players
- alter projectile paths
- extinguish weak flames
- disperse smoke
- move gas concentrations
- disturb loose materials

Example:

```text
Smoke cloud
+
Breeze attack
→ smoke disperses
```

or:

```text
Methane pocket
+
Breeze attack
→ gas distribution changes
```

Its environmental effect can sometimes accidentally help the player.

---

# 39. Creaking

Primary role:

```text
Forest Stalker
+
Ambush
```

The Creaking should not be restricted exclusively to its main biome.

## 39.1 Spawn Distribution

It should have a small spawn chance in any suitable forest.

Concept:

```text
Normal Forest
→ very rare

Dark / Dense Forest
→ rare

Old-Growth Forest
→ rare

Primary Creaking Biome
→ much more common
```

The exact biome names and percentages can be configured later.

## 39.2 Behavior

Possible behavior:

- moves when not observed
- uses trees and vegetation for concealment
- becomes more dangerous at low visibility
- works especially well with Hardwrought's darkness system

The goal is to make forests occasionally feel unsafe even outside the primary biome.

Its main biome remains the location where players should expect to encounter it regularly.

---

# 40. Wither

Primary role:

```text
Boss
+
Mobile Catastrophe
+
Siege
```

The Wither should not be easily trivialized by trapping it under indestructible terrain.

Possible behavior:

- destroys appropriate blocks
- flies aggressively
- changes altitude
- creates adds
- attacks structures
- has several combat phases

Hardwrought's structural physics should matter.

Example:

```text
Wither destroys load-bearing column
↓
Structural support fails
↓
Partial building collapse
```

The Wither should feel capable of devastating a settlement.

---

# 41. Ender Dragon

Primary role:

```text
Major Boss
```

The fight should be expanded into multiple phases.

Possible structure:

```text
Phase 1
→ aerial combat

Phase 2
→ crystals and Endermen

Phase 3
→ ground pressure

Phase 4
→ aggressive final phase
```

Multiplayer can add additional mechanics rather than only additional health.

Exact Ender Dragon redesign remains open for later specification.

---

# 42. Mob Equipment as Gameplay Information

Whenever mob capabilities depend on equipment, the equipment should remain clearly visible.

Example:

```text
Zombie with Axe
→ player knows wooden walls are threatened

Zombie with Pickaxe
→ player knows stone may be threatened

Wither Skeleton with Bow
→ player expects ranged fire pressure
```

This is a key fairness principle.

The player should normally be able to understand the threat before the threat activates.

---

# 43. Mob Griefing Philosophy

Mob Griefing should create meaningful danger without making base building pointless.

Rules:

- only suitable mobs attack structures
- mobs prefer relevant obstacles rather than random blocks
- equipment controls what some mobs can damage
- material strength matters
- high-end construction should provide real protection
- Creepers should not deliberately act as demolition tools
- structural damage should be understandable and predictable

The aim is:

```text
Weak improvised shelter
→ temporary safety

Well-designed house
→ substantial safety

Fortified structure
→ very strong safety

No structure
→ absolute immunity
```

A base should become safer as the player's engineering improves.

---

# 44. Difficulty Through Composition

Hardwrought should sometimes create dangerous mixed groups.

Example:

```text
Zombie with Axe
+
Skeleton
+
Witch
```

Possible interaction:

```text
Zombie pressures door
Skeleton covers approach
Witch supports group
```

Another example:

```text
Piglin Brute
+
Crossbow Piglins
+
Shield Piglin
```

This creates complexity through enemy roles.

It is preferable to:

```text
Same Mob
+
300% HP
+
300% Damage
```

---

# 45. Spawn Rarity and Threat Readability

Powerful variants should not be constant.

Rare mob equipment or special variants should make individual encounters memorable.

Examples:

- pickaxe zombie
- bow Wither Skeleton
- charged Creeper
- unusual Witch support group
- Creaking outside its main biome

Rare variants should be noticeable and identifiable.

---

# 46. Open Mob-System Questions

Still to design in more detail:

- exact Zombie tool spawn chances
- exact block hardness vs Zombie tool matrix
- whether zombies can pick up tools and gain new breaking capabilities
- whether tool durability applies to mob block breaking
- exact Creeper leap distance and timing
- exact Phantom Sleep Debt formula
- exact Wither Skeleton bow spawn chance
- exact Creaking forest spawn probabilities
- mob hearing ranges
- sound propagation through blocks
- smell system or whether it should be omitted
- stealth interaction
- mob reactions to player-made light
- faction behavior between hostile mobs
- mob behavior during weather
- mob interaction with seasons
- mob adaptation to altitude and deep caves
- boss-specific AI
- Nether and End mob redesigns
- new Hardwrought-exclusive mobs

---

# 47. Current Confirmed Changes

```text
ZOMBIE
→ block breaking depends on spawned tool
→ no tool = only hand-breakable materials
→ axe = wood
→ pickaxe = stone
→ shovel = loose terrain

CREEPER
→ no deliberate base breaching
→ short leap toward player before explosion

PHANTOM
→ punishment for repeatedly poor sleep quality
→ not punishment for simply staying awake
→ Sleep Debt controls risk

WITHER SKELETON
→ chance to spawn with bow
→ bow variant fires flaming arrows
→ ranged support role

CREAKING
→ can very rarely spawn in ordinary forests
→ much higher spawn chance in its primary biome
```

---

# 48. Overall Design Principle

Hardwrought mobs should make the player think:

> What can this enemy do, and how do I prepare for it?

Not:

> How much HP does this version have?

Difficulty should come from:

- behavior
- equipment
- terrain
- coordination
- environment
- positioning
- preparation
- player knowledge
- readable special abilities
