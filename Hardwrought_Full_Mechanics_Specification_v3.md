**# Hardwrought**

**## Full Mechanics & Systems Specification**

**---**

**# 1. Project Identity**

****Project Name:**** Hardwrought  

****Minecraft Edition:**** Java Edition  

****Target Version:**** latest supported stable release, currently planned around Minecraft 26.3  

****Modloader:**** Fabric  

****Snapshots:**** not supported  

****Primary Language:**** Java  

****Architecture:**** split client/common sources  

****Data Generation:**** enabled  

****Primary Goal:**** standalone total-conversion style experience

Hardwrought is intended to feel closer to a custom survival game built on Minecraft than to a normal content mod.

Core pillars:

- hardcore survival

- environmental simulation

- geology and realistic resource distribution

- metallurgy

- technology progression

- magic

- player-driven specialization

- research

- adaptation and conditioning

- multiplayer scaling

- building physics

- chemistry

- deep world generation

- advanced industry

Core design principle:

> The character learns what is possible.  

> The player learns how to do it well.  

> The world forces both to adapt.

**---**

**# 2. Standalone Philosophy**

Hardwrought should work as a controlled standalone experience.

Goals:

- no dependence on JEI

- no dependence on external magic mods

- no dependence on external tech mods

- no dependence on external survival mods

- custom recipe and knowledge interface

- controlled progression

- controlled multiplayer behavior

- optional custom launcher later

- dedicated server support

Long-term:

- block unknown gameplay-changing mods

- allow a whitelist for approved visual/performance mods

- validate important files

- optionally verify official files using hashes

Absolute anti-cheat in singleplayer is impossible because the player controls the machine.

Multiplayer should therefore be strongly server-authoritative.

**---**

**# 3. Difficulty Modes**

**## 3.1 Survival**

Death is not permanent, but consequences are serious.

Possible consequences:

- item loss

- equipment damage

- temporary maximum-health reduction

- trauma

- exhaustion

- injuries

- partial experience loss

- encounter lockout

- temporary recovery penalties

Repeated death must not permanently softlock a world.

**## 3.2 Hardcore**

- permanent death

- dead character cannot simply continue

- same mechanical balancing as Survival

- no arbitrary “Hardcore = everything has 2x HP” rule

Hardcore increases stakes, not fake difficulty.

**---**

**# 4. Progression Philosophy**

Hardwrought should avoid simple level gates.

Bad:

```text

You need Smithing Level 30.

```

Preferred:

```text

You technically can attempt it.

You probably cannot do it well yet.

```

Progression should come from:

- research

- material discovery

- player skill

- infrastructure

- environmental adaptation

- equipment

- logistics

- collaboration

**---**

**# 5. Baseline vs Specialization**

Basic gameplay must never be hidden behind specialization.

Every player can:

- create basic tools

- create basic weapons

- create basic armor

- use basic magic

- use simple healing items

- construct simple machines

- perform basic crafting

- survive alone

Specialists gain:

- better quality

- higher efficiency

- advanced options

- more precision

- greater control

- unique expert-level opportunities

Example:

```text

EVERY PLAYER

├─ basic weapons

├─ basic tools

├─ basic armor

├─ basic magic

├─ basic medicine

└─ basic machines

SPECIALIST

├─ higher quality

├─ greater efficiency

├─ complex systems

├─ better control

└─ advanced applications

```

This supports natural multiplayer roles without making solo play impossible.

**---**

**# 6. Three-Layer Character Development**

Hardwrought separates development into three independent systems.

**## 6.1 Knowledge**

What the character knows.

Examples:

- metallurgy theory

- discovered materials

- recipes

- known runes

- chemistry

- engineering concepts

- medicine

- ecology

**## 6.2 Player Mastery**

What the real player can do.

Examples:

- draw runes accurately

- forge a balanced blade

- build a good mechanical system

- identify correct heat-treatment timing

- perform efficient alchemy

- parry consistently

**## 6.3 Character Adaptation**

What the body has physically adapted to.

Examples:

- cardio

- work endurance

- heat adaptation

- cold adaptation

- altitude adaptation

- poison tolerance

- potion tolerance

```text

             CHARACTER DEVELOPMENT

                      │

        ┌─────────────┼─────────────┐

        │             │             │

    KNOWLEDGE      MASTERY      ADAPTATION

        │             │             │

    Research      Player Skill     Body

    Discovery     Practice       Tolerance

    Theory        Experience     Conditioning

```

**---**

**# 7. Stamina**

Stamina is a central gameplay resource.

Affected actions:

- sprinting

- jumping

- swimming

- climbing

- melee attacks

- blocking

- parrying

- heavy attacks

- mining

- smithing

- operating hand-powered machines

- carrying heavy loads

Example:

```text

Stamina

██████████ 100%

Sprint          -8/s

Swim            -6/s

Heavy Attack    -15

Jump            -7

```

Stamina regeneration depends on:

- hydration

- calories

- sleep

- injuries

- body temperature

- oxygen

- conditioning

- carried weight

**---**

**# 8. Hydration**

Players require water.

Example:

```text

Hydration

███████░░░ 72%

```

Water consumption increases from:

- heat

- sprinting

- physical labor

- heavy armor

- sickness

- high temperature

- certain foods

**---**

**# 9. Nutrition**

Food should not only refill a hunger bar.

Suggested categories:

- calories

- protein

- carbohydrates

- fats

- micronutrients

Avoid simulating dozens of individual vitamins.

Energy consumption increases with:

- cold

- work

- combat

- healing

- training

- heavy loads

**---**

**# 10. Carry Weight**

Items should have weight.

Example:

```text

Carry Weight

34 / 45 kg

```

Overloading causes:

- higher stamina drain

- slower movement

- worse jumping

- slower recovery

Transport progression:

```text

Backpack

↓

Cart

↓

Horse

↓

Mechanical Transport

↓

Advanced Transport

```

**---**

**# 11. Sleep System**

Players should be able to sleep almost anywhere.

Possible keybind:

```text

Sleep

```

Sleeping locations may include:

- grass

- stone

- wood

- caves

- forests

- houses

- beds

Sleeping should not instantly skip time.

Instead:

```text

Sleep

→ Time Acceleration

```

During sleep:

- crops grow

- weather changes

- machines operate

- food spoils

- fires consume fuel

- water flows

- animals move

Sleep quality depends on:

```text

Sleep Quality =

Surface

+ Temperature

+ Shelter

+ Safety

+ Noise

+ Wetness

+ Bedding

```

Poor sleep can result in:

- poor stamina recovery

- pain

- fatigue

- illness

- slow healing

Good sleep improves:

- recovery

- training gains

- healing

- endurance

Multiplayer time acceleration can depend on the percentage of sleeping players.

**---**

**# 12. Diseases**

Disease should remain simple enough to avoid excessive micromanagement.

Possible diseases:

- common cold

- influenza

- food poisoning

- wound infection

- pneumonia

Possible causes:

```text

Cold + Wet + Exhausted

→ increased illness chance

```

```text

Dirty Wound

→ infection risk

```

Hardwrought should not attempt a full medical simulation.

**---**

**# 13. Unified Environmental Simulation**

A unified environment model should manage:

```text

ENVIRONMENT

│

├─ Atmosphere

│  ├─ O2

│  ├─ CO2

│  ├─ Methane

│  └─ Smoke

│

├─ Temperature

├─ Humidity

└─ Airflow

```

The system should avoid full per-air-block-per-tick simulation.

Preferred implementation:

- room volumes

- environmental cells

- chunk regions

- periodic updates

- cached simulation

**---**

**# 14. Temperature**

Player temperature depends on:

```text

Body Temperature =

Environment

+ Clothing

+ Activity

+ Sun

+ Wind

+ Wetness

+ Fire

```

Possible states:

```text

Hypothermic

← Cold

← Normal

→ Hot

→ Overheated

```

Effects include:

- stamina penalties

- energy consumption

- reduced healing

- injury risk

- death

**---**

**# 15. Wetness**

Wetness comes from:

- rain

- swimming

- snow

- wet clothing

Wet players lose heat faster.

This allows cold rain to be more dangerous than dry mild cold.

**---**

**# 16. Clothing and Armor Temperature**

Armor should also function as clothing.

Example:

```text

Leather Coat

Armor: Low

Insulation: High

Water Resistance: Medium

Weight: Medium

```

```text

Steel Armor

Armor: Very High

Insulation: Low

Weight: Very High

Heat Retention: High

```

There should be no universally perfect armor.

**---**

**# 17. Heat Transfer**

Heat should move spatially.

Example:

```text

Fire

↓

Room Temperature rises

```

Materials have thermal behavior.

Example concept:

```text

Wool    → Excellent insulation

Wood    → Good

Earth   → Good

Stone   → Medium

Glass   → Poor

Metal   → Very Poor

```

Fire can also provide local radiant heat before the whole room warms up.

**---**

**# 18. Gases**

Important gases include:

- oxygen

- carbon dioxide

- methane

- smoke

- steam

- toxic industrial gases

**## 18.1 Oxygen**

Closed spaces slowly lose usable oxygen when occupied or when combustion occurs.

```text

Small sealed room

+

Players

+

Furnace

+

Torches

↓

O2 decreases

```

Ventilation becomes important.

**## 18.2 Carbon Dioxide**

CO2 may accumulate in poorly ventilated caves.

Possible effects:

- fatigue

- reduced stamina

- breathing problems

- unconsciousness

- death

CO2 should be difficult to detect without tools.

**## 18.3 Methane**

Methane may occur in:

- coal regions

- deep caves

- oil and gas regions

Fire can ignite dangerous concentrations.

```text

Lit Torch

+

Methane

=

Explosion

```

Countermeasures can evolve:

- primitive safety methods

- safer lamps

- ventilation shafts

- mechanical fans

- gas detectors

- electronic monitoring

**## 18.4 Smoke**

Fire generates smoke.

Smoke can:

- reduce visibility

- damage breathing

- displace useful air

- accumulate indoors

House fires are dangerous even without direct flame contact.

**---**

**# 19. Fire**

Fire should interact with:

- oxygen

- temperature

- smoke

- building materials

- wind

- fuel

Technology progression:

```text

Campfire

↓

Chimney

↓

Ventilation

↓

Mechanical Fan

↓

Gas Detector

↓

Electric Ventilation

↓

Climate Control

```

**---**

**# 20. Lighting**

Minecraft lighting should be reworked.

Goals:

- true darkness

- dynamic handheld light

- meaningful moon phases

- technological lighting progression

Light sources:

- torch

- candle

- lantern

- oil lamp

- gas lamp

- electric lamp

A carried lit torch should emit actual light.

**---**

**# 21. Moonlight and Darkness**

Moon phases strongly influence visibility.

```text

Full Moon

→ reasonable natural visibility

Half Moon

→ poor visibility

New Moon

→ near total darkness

```

A moonless night should genuinely require artificial light.

**---**

**# 22. Dark Adaptation**

Player vision adapts to darkness over time.

Example:

```text

0 sec  → almost nothing visible

10 sec → limited visibility

30 sec → improved low-light vision

```

Bright light can reset dark adaptation.

**---**

**# 23. Water System**

**## 23.1 No Infinite Water**

Vanilla infinite water should be removed.

Water has actual volume.

Possible representation:

```text

Water Level 8

Water Level 7

...

Water Level 1

Water Level 0

```

Water can:

- flow

- spread

- evaporate

- freeze

- be pumped

- be consumed

Lakes can theoretically be drained.

**## 23.2 Water Quality**

Possible categories:

- fresh water

- river water

- swamp water

- salt water

Possible purification:

```text

Water

↓

Filtering

↓

Boiling

↓

Drinkable Water

```

Later:

- sand filters

- charcoal filters

- ceramic filters

- distillation

- advanced purification

**## 23.3 Water Cycle**

```text

Rain

↓

Soil

↓

Streams

↓

Rivers

↓

Lakes

↓

Evaporation

↓

Weather

↓

Rain

```

**## 23.4 Evaporation**

Depends on:

- sunlight

- temperature

- wind

- season

- biome

- water body size

**## 23.5 Soil Moisture**

```text

Soil Moisture

██████░░░░ 60%

```

Affected by:

- rain

- irrigation

- sunlight

- heat

- soil type

- plants

**## 23.6 Groundwater**

Regions can have groundwater levels.

```text

Groundwater Level

Y = 52

```

Enables:

- wells

- pumps

- springs

- aquifers

- regional water scarcity

**---**

**# 24. Seasons**

Functional seasons:

```text

Spring

Summer

Autumn

Winter

```

They affect:

- temperature

- rainfall

- snow

- water

- plants

- animals

- farming

- calorie demand

- fuel demand

- daylight length

**## Spring**

- high rainfall

- strong growth

- high river levels

- animal population growth

Risks:

- flooding

- mud

- cold nights

**## Summer**

- high temperatures

- high water demand

- strong evaporation

- good crop growth

Risks:

- drought

- overheating

- water shortages

- possible wildfires later

**## Autumn**

- harvest season

- falling temperatures

- preparation for winter

**## Winter**

- low temperatures

- frozen water

- weak plant growth

- high calorie demand

- high fuel consumption

Poor preparation should be dangerous.

**---**

**# 25. Adaptation and Conditioning**

Hardwrought should not use normal stat allocation.

The body adapts through actual repeated activity.

Possible categories:

```text

Conditioning

├─ Cardio

├─ Strength

├─ Work Endurance

├─ Load Capacity

├─ Swimming Efficiency

├─ Heat Adaptation

├─ Cold Adaptation

└─ Altitude Adaptation

```

Specific training:

```text

Running

→ Cardio

```

```text

Mining

→ Work Endurance

```

```text

Carrying

→ Load Capacity

```

```text

Swimming

→ Swimming Efficiency

```

Improvement depends on:

```text

Training

+

Food

+

Sleep

+

Recovery

=

Adaptation

```

Too much training:

```text

Fatigue ↑

Recovery ↓

Performance ↓

```

Unused adaptations slowly decay.

**---**

**# 26. Tolerance System**

Possible tolerance groups:

```text

Tolerances

├─ Poison

├─ Healing

├─ Regeneration

├─ Stimulants

├─ Sedatives

└─ Magic Potions

```

**## Healing Tolerance**

```text

Tolerance 0%

→ 100% healing

Tolerance 50%

→ 50% healing

Tolerance 100%

→ no effect

```

**## Regeneration Tolerance**

Repeated regeneration effects become weaker and may become nearly useless at extreme tolerance.

**## Poison Resistance**

```text

Poison Tolerance 0%

→ full effect

Poison Tolerance 50%

→ half effect

Poison Tolerance 100%

→ immunity

```

Intentional poisoning remains dangerous.

Possible consequences:

- organ damage

- weakness

- recovery penalties

- death

**## Cross-Tolerance**

Similar substances can share tolerance.

**## Tolerance Decay**

Tolerance slowly falls after long periods without exposure.

**---**

**# 27. Combat System**

Combat should be substantially expanded.

Goals:

- distinct weapon roles

- stamina-based combat

- meaningful armor

- blocking

- parrying

- reach

- attack types

- impact

- armor penetration

- player skill

**---**

**# 28. Damage Types**

Core physical types:

```text

Slash

Pierce

Blunt

```

Additional types:

```text

Fire

Cold

Electric

Explosion

Magic

Poison

```

**---**

**# 29. Weapon Properties**

Weapons may have:

```text

Damage

Attack Speed

Reach

Weight

Stamina Cost

Armor Penetration

Impact

Handling

Durability

```

No weapon should dominate every category.

**---**

**# 30. Core Weapon Classes**

Recommended first set:

1. Knife / Dagger

2. Sword

3. Axe

4. Mace

5. Spear

6. Two-Handed Weapon

7. Bow

8. Shield

Later:

- war hammer

- poleaxe

- halberd

- glaive

- crossbow

- throwing weapons

- magical foci

- engineer weapons

- primitive firearms

**## Knife / Dagger**

```text

Damage: Low

Reach: Very Low

Speed: Very High

Stamina Cost: Very Low

Armor Penetration: Low

```

Roles:

- backup

- stealth

- utility

- early survival

**## Sword**

```text

Slash: High

Pierce: Medium

Blunt: Very Low

Reach: Medium

Attack Speed: Medium

Stamina Cost: Medium

Handling: High

```

Role:

- flexible all-rounder

**## Axe**

- high impact

- high damage

- useful against shields

- works as tool and weapon

- higher stamina cost

**## Mace**

- high blunt damage

- strong against armored targets

- useful against skeletal enemies

**## War Hammer**

- very high impact

- strong armor penetration

- slow

- high stamina cost

**## Spear**

```text

Damage: Medium

Reach: Very High

Speed: Medium

Stamina: Low-Medium

Armor Penetration: Medium

```

Strengths:

- spacing

- animals

- large enemies

- formations

Weaknesses:

- confined spaces

- enemies inside minimum range

**## Polearms**

Examples:

- halberd

- glaive

- poleaxe

```text

Slash: High

Pierce: High

Blunt: High

Reach: Very High

Speed: Low

Stamina: High

Handling: Low

```

**## Two-Handed Weapons**

Examples:

- greatsword

- greataxe

- heavy hammer

Properties:

- high damage

- high reach

- high stamina cost

- slow attacks

- strong guard breaks

**## Bow**

Bow performance can depend on:

- draw weight

- arrow type

- material

- stamina

- player handling

- craftsmanship

**## Crossbow**

Later system:

- high penetration

- long reload

- less dependent on user strength at the moment of firing

**## Throwing Weapons**

Examples:

- throwing spear

- throwing knife

- stones

- throwing axe

**---**

**# 31. Shields**

**## Buckler**

```text

Parry: Excellent

Block: Low

Weight: Very Low

```

**## Round Shield**

```text

Parry: Good

Block: Good

Weight: Medium

```

**## Tower Shield**

```text

Parry: Poor

Block: Excellent

Weight: Very High

Stamina Drain: High

```

**---**

**# 32. Attack Types**

Sword:

```text

Slash

Thrust

Heavy Strike

```

Spear:

```text

Quick Thrust

Heavy Thrust

Brace

Sweep

```

**---**

**# 33. Brace Mechanic**

Spears and polearms can be braced against charging enemies.

```text

Enemy Charge

↓

Braced Weapon

↓

Damage scales with momentum

```

Useful against:

- animals

- large monsters

- mounts

- charges

**---**

**# 34. Blocking and Parrying**

Blocking:

- reduces damage

- costs stamina

- can fail under heavy impact

- can cause guard break

Parrying:

- timing window

- prevents most damage

- can stagger attacker

- rewards player timing

**---**

**# 35. Hit Zones**

Suggested coarse zones:

```text

Head

Torso

Arms

Legs

```

Possible consequences:

- leg hits affect mobility

- arm hits affect handling

- head hits are dangerous

Avoid excessive per-hit micromanagement.

**---**

**# 36. Armor**

Armor should strongly affect playstyle.

**## Cloth**

```text

Protection: Low

Weight: Very Low

Stamina Cost: Very Low

Heat: Low

Magic Interference: Very Low

```

**## Leather**

```text

Protection: Medium-Low

Weight: Low

Stamina Cost: Low

Insulation: Medium

```

**## Chain**

```text

Slash: High

Pierce: Medium

Blunt: Low

Weight: Medium

```

**## Plate**

```text

Slash: Very High

Pierce: High

Blunt: Medium

Weight: Very High

Stamina Cost: High

Heat: High

```

Plate gives strong protection but:

- lowers mobility

- raises stamina drain

- increases heat

- increases water consumption

- worsens swimming

**---**

**# 37. Tool System**

Basic tools can be made by everyone.

Specialists can make better versions.

Example:

```text

Steel Pickaxe

Poor Craftsmanship:

Mining Speed: 82%

Durability: 71%

Good Craftsmanship:

Mining Speed: 107%

Durability: 119%

```

Tool properties depend on:

- material

- craftsmanship

- geometry

- heat treatment

- maintenance

**---**

**# 38. Smithing**

Smithing should be interactive.

Possible process:

```text

Heat Billet

↓

Hammer

↓

Shape

↓

Quench

↓

Temper

↓

Grind

```

Possible evaluated properties:

- balance

- edge

- durability

- geometry

- material stress

- craftsmanship

Example:

```text

Steel Longsword

Balance:       93%

Edge:          97%

Durability:    88%

Material:      95%

Craftsmanship: 94%

```

Different players can create different weapons from the same material.

**---**

**# 39. Building Physics**

Building should use simplified structural physics.

Goals:

- prevent trivial skybases

- reward realistic supports

- retain creative freedom

Material properties:

```text

Compression Strength

Tension Strength

Support Distance

Weight

```

Unsupported structures accumulate stress.

```text

Stress

↓

Cracks

↓

Collapse

```

Material behavior:

- wood: flexible

- stone: strong in compression

- earth: weak

- steel: allows large spans

Massive bases should not remain stable on a single dirt block.

**---**

**# 40. Leaves**

Leaves should not support players.

Players and large mobs pass through them.

Possible effects:

- slight movement slowdown

- reduced visibility

**---**

**# 41. World Height and Depth**

Planned vertical range:

```text

Minimum Y: -256

Maximum Y: +1024

```

Total:

```text

1280 blocks

```

The extra height and depth must matter mechanically.

**---**

**# 42. Vertical Zones**

Possible structure:

```text

Y +1024

│ Extreme High Altitude

│

├ High Mountains

├ Alpine Zone

├ Normal Surface

├ Shallow Caves

├ Deep Caves

├ Lower Caverns

├ Abyss

│

Y -256

```

**---**

**# 43. High Altitude**

Increasing altitude causes:

- less effective oxygen

- worse stamina regeneration

- increased breathing effort

- worse sleep

- stronger wind

- lower temperatures

Example:

```text

Y 100 → Normal

Y 300 → Slightly Reduced Oxygen

Y 600 → Low Oxygen

Y 900 → Very Low Oxygen

```

**---**

**# 44. Altitude Adaptation**

Long-term exposure can increase:

```text

Altitude Adaptation

```

Possible benefits:

- improved stamina

- improved oxygen use

- better sleep

- reduced altitude stress

Adaptation slowly fades at lower altitude.

At extreme altitude, adaptation alone may not be enough.

Late-game equipment can include:

- oxygen systems

- advanced clothing

- environmental gear

**---**

**# 45. Wind**

Wind should become stronger with elevation.

Wind affects:

- heat loss

- projectile paths

- movement

- fire

- wind power

**---**

**# 46. Deep World**

Depth introduces new hazards.

Increasing depth may cause:

- higher geothermal temperature

- worse ventilation

- CO2 accumulation

- methane

- underground water

- rare resources

**---**

**# 47. Geothermal Heat**

Example:

```text

Surface → 15°C

Y -100  → 22°C

Y -200  → 35°C

Y -256  → 45°C+

```

Actual temperature depends on geology.

Volcanic regions can be much hotter.

**---**

**# 48. Deep Adaptation**

Possible adaptations:

- heat adaptation

- CO2 tolerance

- work endurance

Adaptation should never make extreme hazards harmless.

**---**

**# 49. Cave Layers**

**## Shallow Caves**

```text

Y +50 to -20

```

Relatively normal cave environments.

**## Deep Caves**

```text

Y -20 to -100

```

- larger caves

- less ventilation

- more valuable resources

**## Lower Caverns**

```text

Y -100 to -180

```

- giant caverns

- underground lakes

- methane

- geothermal regions

**## Abyss**

```text

Y -180 to -256

```

- extreme hazards

- rare materials

- little natural ventilation

- special mobs

- geological or magical anomalies

**---**

**# 50. World Generation Philosophy**

World generation should be geological rather than purely random.

Large-scale systems:

```text

WORLD GENERATION

│

├─ Climate

│  ├─ Temperature

│  ├─ Rainfall

│  └─ Seasons

│

├─ Geology

│  ├─ Rock Types

│  ├─ Ore Deposits

│  ├─ Oil / Gas

│  └─ Groundwater

│

├─ Surface

│  ├─ Soil

│  ├─ Vegetation

│  └─ Rivers

│

├─ Underground

│  ├─ Cave Systems

│  ├─ Gas Pockets

│  ├─ Aquifers

│  └─ Ore Bodies

│

└─ Special

   ├─ Ruins

   ├─ Magic Zones

   ├─ Volcanoes

   └─ Rare Structures

```

**---**

**# 51. Geological Regions**

Examples:

```text

Granite Region

├─ Tin

├─ Tungsten

└─ Quartz

```

```text

Volcanic Region

├─ Copper

├─ Sulfur

└─ Obsidian

```

```text

Sedimentary Basin

├─ Coal

├─ Oil

├─ Natural Gas

├─ Salt

└─ Limestone

```

World geology should influence economy and settlement.

**---**

**# 52. Ore Deposits**

Avoid tiny random veins everywhere.

Prefer:

- larger deposits

- regional geology

- surface indicators

- exploration

- prospecting

Strip mining should be less effective than informed exploration.

**---**

**# 53. Ore Grade**

Ore quality varies.

Example:

```text

Copper Ore

Grade: 3.2%

```

```text

Rich Copper Ore

Grade: 18.7%

```

Processing:

```text

Ore

↓

Crushing

↓

Screening

↓

Washing

↓

Concentration

↓

Smelting

```

Better technology recovers more material.

**---**

**# 54. Prospecting**

Early methods:

- visible ore traces

- rock color

- surface fragments

- river sediment

Later:

- prospecting hammer

- geological samples

- drill cores

- chemical tests

- magnetic methods

- seismic methods

Prospecting can become a natural multiplayer specialization.

**---**

**# 55. Metals**

Possible metals:

- copper

- tin

- zinc

- lead

- silver

- gold

- iron

- nickel

- chromium

- aluminum

- tungsten

- titanium

**---**

**# 56. Alloys**

Examples:

```text

Copper + Tin

→ Bronze

```

```text

Copper + Zinc

→ Brass

```

```text

Iron + Carbon

→ Steel

```

```text

Iron + Chromium + Nickel

→ Stainless Steel

```

**---**

**# 57. Industrial Minerals**

Possible resources:

- limestone

- gypsum

- salt

- sulfur

- graphite

- phosphates

- clay

- quartz

Uses include:

- construction

- metallurgy

- chemistry

- agriculture

- electronics

**---**

**# 58. Energy Resources**

Possible resources:

- peat

- lignite

- coal

- anthracite

- crude oil

- natural gas

- geothermal energy

**---**

**# 59. Coal Progression**

Possible fuel grades:

```text

Peat

↓

Lignite

↓

Coal

↓

Anthracite

```

Differences:

- heat output

- smoke

- burn time

- industrial suitability

**---**

**# 60. Oil**

Oil should exist in underground reservoirs.

Not:

```text

Oil Block

→ Bucket

```

Preferred:

```text

Exploration

↓

Drilling

↓

Well

↓

Crude Oil

```

Oil should be associated with geology.

**---**

**# 61. Natural Gas**

Natural gas may occur:

- above oil reservoirs

- in separate gas fields

- in sedimentary basins

Risks:

- fire

- explosion

- leaks

- suffocation

**---**

**# 62. Oil Refining**

Crude oil must be processed.

Simplified model:

```text

Crude Oil

↓

Distillation

├─ Light Fraction

├─ Fuel Fraction

├─ Heavy Oil

└─ Residue

```

Possible products:

- fuel

- lubricants

- solvents

- bitumen

- chemical feedstocks

**---**

**# 63. Chemistry**

Separate two major branches.

**## Industrial Chemistry**

Based on:

- minerals

- oil

- gas

- water

- industrial processing

**## Alchemy**

Based on:

- plants

- magical materials

- organic materials

- magical reactions

These systems can merge later.

**---**

**# 64. Chemical Resources**

Important raw chemical resources:

- sulfur

- salt

- limestone

- quartz

- graphite

- phosphates

- crude oil

- natural gas

Avoid simulating hundreds of chemicals.

Focus on strategically useful groups.

**---**

**# 65. Chemical Purity**

Materials can have purity.

```text

Sulfur

Purity: 73%

```

Later:

```text

Refined Sulfur

Purity: 99.2%

```

High-tech processes may require high purity.

**---**

**# 66. Unified Fluids**

Possible fluids:

```text

Fluids

├─ Water

├─ Crude Oil

├─ Fuel

├─ Lubricant

├─ Chemical Solutions

└─ Acids

```

Later systems can use:

- pipes

- tanks

- pumps

- valves

- pressure

**---**

**# 67. Unified Gases**

Possible gases:

```text

Gases

├─ O2

├─ CO2

├─ Methane

├─ Natural Gas

├─ Steam

└─ Industrial Gas

```

**---**

**# 68. Aquifers**

Underground water-bearing layers should exist.

Opening one may flood a mine.

```text

Aquifer Opened

↓

Water Flow

↓

Mine Flooding

```

Countermeasures:

- drainage

- pumps

- planned tunnels

**---**

**# 69. Regional Economy**

World regions should naturally encourage different roles.

Example:

```text

Mountain Region

↓

Granite

↓

Tin / Tungsten

↓

Cold Climate

↓

Poor Farming

↓

Mining Region

```

```text

Sedimentary Basin

↓

Coal / Oil / Gas

↓

Flat Land

↓

Good Farming

↓

Industrial Region

```

This naturally supports multiplayer trade and logistics.

**---**

**# 70. Technology Progression**

Core path:

```text

Primitive

↓

Hand Powered

↓

Mechanical

↓

Water / Wind

↓

Iron / Steel

↓

Steam

↓

Electricity

↓

Industry

↓

Advanced Technology

↓

Arcane Engineering

```

**---**

**# 71. Primitive Stage**

Examples:

- stones

- sticks

- fibers

- primitive tools

- fire

- primitive shelter

Trees should not be punchable by hand.

## 71.1 Tool-Gated Block Breaking

Hardwrought removes the Vanilla principle that almost every block can eventually be broken with bare hands.

The player must use a physically appropriate tool for solid materials.

The purpose is not to arbitrarily lock content behind tiers. The goal is to make the physical interaction with the world believable and to make primitive tools important from the first minutes of a world.

General rule:

```text
Correct Tool
→ normal breaking
→ normal drops

Weak / Improvised Tool
→ slow breaking
→ high stamina cost
→ tool damage
→ potentially reduced yield

Bare Hands on Solid Material
→ no meaningful breaking progress
→ no usable block drop
```

A player cannot simply spend more time punching a log, stone wall, ore block, machine or metal structure until it eventually disappears.

### 71.1.1 Blocks that can be gathered or manipulated by hand

Very soft or loose materials remain interactable without tools.

Examples:

- sticks
- loose stones
- berries
- mushrooms
- flowers
- fibers
- loose vegetation
- dropped branches
- small surface ore fragments
- snow layers
- other naturally loose resources

These resources form the basis of the primitive starting phase.

### 71.1.2 Soil and loose ground

Materials such as:

- dirt
- sand
- gravel
- soft mud
- loose snow

can be moved by hand in an emergency.

However:

- breaking is slow
- stamina consumption is high
- digging tools are far more efficient
- some materials may not produce a clean reusable block when dug by hand (like dird chunks wich can be craftet in to dirt slaps)

Example:

```text
Bare Hands + Dirt
→ Possible
→ Slow
→ High Stamina Cost

Shovel + Dirt
→ Fast
→ Low Stamina Cost
→ Reliable Yield
```

### 71.1.3 Wood

Solid wood cannot be harvested by punching it.

This includes:

- logs
- stripped logs
- large branches
- wooden beams
- structural timber
- planks where appropriate

Bare hands:

```text
Player + Log
→ No meaningful breaking progress
```

A player first gathers loose natural materials and constructs a primitive cutting tool.

Example progression:

```text
Loose Stone
+
Stick
+
Fiber
↓
Primitive Cutting Tool
↓
Branches / Bark / Wood
↓
Better Axe
↓
Logs and Construction Timber
```

Primitive stone tools may be capable of processing wood, but they should:

- work slowly
- consume considerable stamina
- lose durability quickly
- produce a lower yield than proper axes

A high-quality metal axe later turns wood processing into a much faster task.

### 71.1.4 Stone

Stone cannot be mined with bare hands.

Required methods may include:

- hammer stone for very soft early materials
- primitive stone pick
- pickaxe
- hammer and chisel
- later mechanical drilling

Different rock types can have different hardness.

Example:

```text
Limestone
→ relatively easy

Granite
→ harder

Very Hard Geological Material
→ advanced tool required
```

Using an insufficient tool does not allow the player to bypass the requirement simply by mining for several minutes.

### 71.1.5 Ores

Ore extraction requires an appropriate mining tool and tool tier.

The requirement should depend on:

- host rock hardness
- ore hardness
- tool material
- tool geometry
- tool condition

Example:

```text
Copper Deposit in Soft Rock
→ primitive / early mining possible

Tungsten-Bearing Deposit in Hard Rock
→ advanced tool required
```

This means ore progression is driven by physical capability rather than an arbitrary character level.

### 71.1.6 Constructed Blocks

Placed blocks should also respect their material.

Examples:

```text
Wooden Beam
→ Axe / Saw

Stone Wall
→ Pick / Hammer

Metal Plate
→ Metalworking Tool

Machine
→ Wrench / Appropriate Tool

Pipe
→ Wrench / Cutter
```

This prevents players from instantly dismantling complex structures with their fists.

### 71.1.7 Glass

Glass can physically be broken without a proper tool, but doing so is destructive.

Possible behavior:

```text
Bare Hand + Glass
→ Glass breaks
→ no reusable glass block
→ possible cut injury
```

A proper glass-working or dismantling tool can recover material safely.

### 71.1.8 Emergency Escape and Softlocks

Tool requirements must not create unavoidable softlocks.

The design should ensure that a player cannot permanently trap themselves because their last tool broke.

Possible safeguards:

- loose stones can always be found or created under reasonable conditions
- primitive emergency tools remain craftable from common materials
- selected weak construction materials can be destructively dismantled
- respawn situations must provide a path back into primitive progression

Hardwrought should punish poor preparation without making a world permanently unplayable.

### 71.1.9 Stamina and Tool Efficiency

Breaking blocks should interact with stamina.

A poor tool may technically work but be exhausting.

Example:

```text
Primitive Stone Axe
Wood Cutting Speed: Low
Stamina Cost: High

Steel Axe
Wood Cutting Speed: High
Stamina Cost: Low
```

This makes technological improvement immediately noticeable.

### 71.1.10 Breaking vs. Harvesting

Breaking a block and harvesting it should be separate concepts.

An object may be destructible without being recoverable.

Example:

```text
Wrong Tool
→ structure can eventually be destroyed
→ material heavily damaged or lost

Correct Tool
→ controlled dismantling
→ useful material recovered
```

This system should be used where it prevents softlocks or improves physical believability without allowing bare-hand mining to return through the back door.

Possible early chain:

```text

Loose Stone

+

Stick

+

Fiber

↓

Primitive Tool

```

**---**

**# 72. Hand-Powered Technology**

Examples:

- hand grinder

- mortar

- crank

- hand drill

- hand press

Human effort consumes stamina and food.

**---**

**# 73. Mechanical Technology**

Systems:

- shafts

- gears

- belts

- flywheels

- clutches

- gearboxes

Example:

```text

Crank

↓

Shaft

↓

Gearbox

↓

Mill

```

**---**

**# 74. Water and Wind Power**

Possible systems:

- water wheels

- windmills

Example:

```text

Water Wheel

↓

Gearbox

↓

Main Shaft

├─ Saw

├─ Mill

└─ Hammer

```

**---**

**# 75. Steam Technology**

Steam phase includes:

- boilers

- pressure

- pistons

- valves

- steam engines

Poor systems can fail.

Possible failures:

- leaks

- pressure loss

- rupture

- explosion

**---**

**# 76. Electricity**

Later systems:

- generators

- wires

- batteries

- transformers

- motors

- electric machines

Mechanical systems remain useful.

**---**

**# 77. Machines**

Machines should be assembled from components.

Not:

```text

8 Iron + Furnace

→ Crusher

```

Preferred:

```text

Crusher

├─ Steel Frame

├─ Bearings

├─ Shaft

├─ Gears

├─ Crushing Jaws

└─ Housing

```

**---**

**# 78. Machine Wear**

Machines may track:

- temperature

- lubrication

- friction

- bearing condition

- pressure

- durability

Example:

```text

Bearing Condition

██████░░░░ 61%

```

Neglect may cause:

- lower efficiency

- heat

- seizure

- breakage

**---**

**# 79. Research System**

Research should be central.

Research means knowledge, not direct skill.

**---**

**# 80. Knowledge Compendium**

Custom replacement for:

- JEI

- vanilla recipe book

- wiki

- tutorial

- research display

Possible categories:

```text

Knowledge

├─ Materials

├─ Crafting

├─ Metallurgy

├─ Engineering

├─ Agriculture

├─ Biology

├─ Medicine

├─ Chemistry

├─ Magic

└─ Electricity

```

**---**

**# 81. Unknown Materials**

Unknown objects provide limited information.

Example:

```text

IRON

Unknown Material

Properties:

???

???

???

Uses:

Unknown

```

**---**

**# 82. Learning**

Research may come from:

- discovering materials

- finding structures

- building machines

- experimenting

- reading books

- studying plants

- studying mobs

- boss drops

- ruins

- magical artifacts

**---**

**# 83. Personal and Shared Research**

Possible categories:

**## Personal Knowledge**

- material discoveries

- medicine

- magic

- biology

**## Shared Knowledge**

Through:

- library

- research table

- archive

Multiplayer groups can distribute specializations.

**---**

**# 84. Magic System**

Magic should be a construction system rather than a normal hotbar spell system.

Example:

```text

Form

+

Element

+

Direction

+

Power

+

Modifier

```

**---**

**# 85. Rune Drawing**

Magic can use:

- drawing

- symbols

- rune sequences

- circles

- written construction

Possible evaluated properties:

- shape

- order

- accuracy

- speed

- stability

Poor casting may:

- weaken the spell

- increase cost

- destabilize it

- fail

- backfire

**---**

**# 86. Scrolls**

Prepared magic:

```text

Parchment

+

Ink

+

Rune Construction

+

Binding

↓

Scroll

```

Example:

```text

Fire Bolt Scroll

Rune Accuracy: 94%

Stability: 91%

Power: 108%

Efficiency: 97%

```

Scrolls allow fast combat use after prior preparation.

**---**

**# 87. Combat Mage**

Possible focus:

- projectiles

- shields

- teleportation

- elements

- area effects

The player becomes good through practice.

**---**

**# 88. Enchanter**

Possible focus:

- buffs

- debuffs

- curses

- rune stones

- weapon enchantments

- armor enchantments

- protection circles

**---**

**# 89. Enchanting**

No simple level-30 enchant button.

Enchanting should involve runic construction.

Example:

```text

        FIRE

      /      \\

   POWER    EDGE

        SWORD

```

Rune placement and compatibility matter.

**---**

**# 90. Enchantment Capacity**

Item quality limits enchantment complexity.

Example:

```text

Poor Iron Sword

Enchant Capacity: 12

```

```text

Masterwork Steel Sword

Enchant Capacity: 41

```

This creates strong cooperation between smiths and enchanters.

**---**

**# 91. Arcane Metallurgy**

Possible magical materials:

- silver

- gold

- meteoric iron

- mithril

- void crystals

Possible properties:

- magical conductivity

- rune stability

- enchantment capacity

- low weight

- exotic interactions

**---**

**# 92. Engineering Specialization**

Engineers become effective by understanding systems rather than gaining simple stat bonuses.

Important concepts:

- torque

- RPM

- gearing

- pressure

- energy

- heat

- flow

- ventilation

**---**

**# 93. Engineer Combat**

Engineering-based combat may include:

- traps

- mines

- grenades

- smoke devices

- flash devices

- incendiary devices

- tripwire traps

- defensive mechanisms

These should remain abstract and game-focused.

**---**

**# 94. Alchemy**

Possible process:

```text

Ingredient

↓

Grinding

↓

Extraction

↓

Distillation

↓

Reaction

↓

Stabilization

```

Possible variables:

- quantity

- temperature

- purity

- order

- duration

Alchemy can bridge magic and chemistry.

**---**

**# 95. Natural Specializations**

No class selection.

Possible natural roles:

- smith

- mage

- enchanter

- engineer

- demolition specialist

- alchemist

- medic

- farmer

- cook

A player becomes known for a role because they actually perform it well.

**---**

**# 96. Multiplayer Philosophy**

Multiplayer should reward cooperation without trivializing difficulty.

**---**

**# 97. Boss Scaling**

Differentiate:

- online players

- nearby players

- active participants

Only active participants scale the encounter.

A player can count as active by:

- damaging the boss

- fighting adds

- applying debuffs

- healing participants

- applying buffs

- actively moving within the encounter

**---**

**# 98. Boss Health Scaling**

Example:

```text

HP Multiplier =

1 + 0.65 × (Players - 1)

```

Example values:

```text

1 Player → 100%

2 Players → 165%

3 Players → 230%

4 Players → 295%

5 Players → 360%

8 Players → 555%

```

Boss damage should scale much more slowly.

**---**

**# 99. Boss Mechanics Scaling**

More players should create more mechanics, not just more health.

Possible changes:

- additional adds

- multiple targets

- area denial

- extra phases

- target marking

- coordination mechanics

**---**

**# 100. Boss Join/Leave Protection**

Encounter scaling must resist exploitation.

New players should increase scaling gradually.

Leaving players should not instantly reduce scaling.

**---**

**# 101. Boss Reset**

If no active players remain:

```text

Short Delay

↓

Boss Reset

↓

Full Health

```

**---**

**# 102. Boss Loot**

Possible model:

```text

Shared Loot

+

Personal Progression Loot

```

Important progression resources should not force one kill per group member.

**---**

**# 103. Machines in Multiplayer**

Machine state is server-authoritative.

Server tracks:

- inventory

- energy

- temperature

- pressure

- wear

- processing

- research locks

**---**

**# 104. Chunk Behavior**

Early machines can stop when chunks unload.

Later technology may allow controlled persistent operation.

This should be expensive and deliberate.

**---**

**# 105. Ecology**

**## Animal Populations**

Animals should not infinitely respawn.

Regions track populations.

```text

HIGH

↓

MEDIUM

↓

LOW

↓

EXTINCT

```

Overhunting matters.

**## Wild Plants**

Wild plants can be depleted.

Players must:

- collect seeds

- cultivate plants

- harvest sustainably

**---**

**# 106. Farming**

Possible influences:

- soil type

- moisture

- season

- light

- temperature

Possible later additions:

- crop rotation

- fertilizer

- pests

- plant disease

- greenhouses

Avoid overcomplication too early.

**---**

**# 107. Food Spoilage**

Food decays over time.

Possible variables:

- temperature

- humidity

- storage

- season

Preservation:

- salting

- smoking

- drying

- fermentation

- cooling

- freezing

**---**

**# 108. Performance Rules**

Hardwrought contains many simulations.

Rules:

- avoid per-block per-tick simulation where possible

- prefer region-based systems

- use event-driven updates

- cache environmental values

- simulate distant systems at lower frequency

- fully simulate only loaded or relevant areas

Critical for:

- gas

- water

- temperature

- ecology

- large vertical worldgen

- multiplayer machines

**---**

**# 109. Suggested Module Layout**

```text

hardwrought/

├─ core/

├─ config/

├─ networking/

├─ survival/

├─ stamina/

├─ hydration/

├─ nutrition/

├─ sleep/

├─ adaptation/

├─ combat/

├─ armor/

├─ tools/

├─ smithing/

├─ research/

├─ knowledge/

├─ materials/

├─ geology/

├─ worldgen/

├─ water/

├─ climate/

├─ seasons/

├─ atmosphere/

├─ temperature/

├─ fire/

├─ light/

├─ ecology/

├─ agriculture/

├─ metallurgy/

├─ mechanical/

├─ steam/

├─ electricity/

├─ fluids/

├─ chemistry/

├─ alchemy/

├─ magic/

├─ enchanting/

├─ bosses/

├─ multiplayer/

├─ building_physics/

├─ industry/

└─ ui/

```

**---**

**# 110. Recommended Development Order**

** Milestone 0 – Foundation**

- project starts

- common/client split works

- config system

- networking

- save data

- debug tools

- data-driven registries

** Milestone 1 – Core Survival**

- stamina

- hydration

- nutrition

- carry weight

- sleep

- basic temperature

** Milestone 2 – Combat**

- weapon attributes

- slash/pierce/blunt

- stamina combat

- blocking

- parrying

- armor weight

- armor interaction

** Milestone 3 – Environment**

- O2

- CO2

- methane

- smoke

- fire

- temperature volumes

- dynamic light

** Milestone 4 – Water**

- finite water

- water levels

- flow

- groundwater

- water quality

- evaporation

** Milestone 5 – Vertical World Prototype**

- min Y -256

- max Y +1024

- extreme mountains

- deep caves

- altitude oxygen

- geothermal depth

** Milestone 6 – Geology**

- rock regions

- large ore deposits

- ore grade

- prospecting

- aquifers

** Milestone 7 – Early Progression**

- primitive tools and block breaking rules

- (new metals: tin, zinc, lead, nickel, chromium, aluminum, titanium, tungsten, cobalt, manganese, magnesium, platinum, mercury, uranium, thorium) and the ore generation of these ores.

- bronze (alloys)

- early metallurgy

** Milestone 8 – Knowledge System**

- custom recipe browser (JEI but not as easy and dont just use JEI as the mod)

- materials

- discoveries

- research

- unknown entries

** Milestone 9 – Smithing**

- forging minigame

- weapon quality

- tool quality

- heat treatment

# Milestone 10 – Mechanical Age**

- crank

- gears

- shafts

- belts

- water wheel

- windmill

# Milestone 11 – Oil & Chemistry**

- oil reservoirs

- natural gas

- wells

- refining

- chemical feedstocks

# Milestone 12 – Steam**

- boiler

- steam

- pressure

- steam engines

# Milestone 13 – Electricity**

- generator

- battery

- cable

- motor

# Milestone 14 – Seasons & Ecology**

- spring

- summer

- autumn

- winter

- animal populations

- farming

# Milestone 15 – Adaptation**

- cardio

- work endurance

- altitude adaptation

- heat/cold adaptation

- poison tolerance

- healing tolerance

# Milestone 16 – Magic**

- rune system

- basic spells

- scrolls

- combat magic

- enchanting

# Milestone 17 – Building Physics**

- support

- structural stress

- collapse

- leaf collision

# Milestone 18 – Multiplayer Balance

- boss scaling

- encounter participants

- shared research

- multiplayer sleep

- server performance

# Milestone 19 – Advanced Industry**

- high-end metallurgy

- industrial chemistry

- large production lines

- advanced fluids

# Milestone 20 – Arcane Engineering**

- magic + technology

- exotic materials

- hybrid machines

# Milestone 21 – Endgame**

Still open for detailed design.

Possible directions:

- world bosses

- extreme expeditions

- megaprojects

- high-end industry

- rare dimension materials

- server-scale projects

**---**

** 111. Final Design Principle**

Hardwrought should create this progression:

```text

Beginning:

The world controls the player.

Midgame:

The player understands the world.

Late game:

The player builds systems that control the world.

```

Power should come from:

- knowledge

- infrastructure

- real player skill

- specialization

- adaptation

- preparation

- cooperation

- mastery of the environment