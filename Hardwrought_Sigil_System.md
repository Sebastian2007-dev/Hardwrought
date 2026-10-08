# Hardwrought
## Sigil System Specification

---

# 1. Core Concept

The Sigil System is a separate magical system from normal free-drawn spell casting.

Normal spell casting is dynamic, fast and performed directly with a wand.

Sigils are prepared magical constructions. They are slower to create, but can describe much more complex, persistent and precise magical behavior.

The basic distinction is:

```text
SPELL CASTING
→ free drawing
→ fast
→ direct
→ combat-oriented
→ activated by the caster

SIGILS
→ prepared construction
→ geometric structure
→ persistent or conditional
→ highly configurable
→ can function without continuous caster input
```

A sigil therefore behaves less like a single spell and more like a small magical program.

---

# 2. Mandatory Sigil Structure

Every sigil consists of two concentric circles.

```text
OUTER CIRCLE
│
│   Auxiliary Runes
│
│   INNER CIRCLE
│   │
│   │ Stabilization Geometry
│   │
│   │ Main Element
│   │
│   └
│
└
```

The two circles have different purposes.

```text
INNER CIRCLE
→ What magic exists?

OUTER CIRCLE
→ What should the magic do?
```

---

# 3. Inner Circle

The inner circle contains two important components:

1. Main Element
2. Stabilization Geometry

The inner circle defines the fundamental magical effect and provides enough stability to process the instructions in the outer circle.

---

# 4. Main Element

The center of the sigil contains the main elemental rune.

The same six fundamental elements used by normal spell casting are used here:

```text
Fire
Water
Wind
Earth
Light
Dark
```

The element determines the fundamental magical substance or force.

Example interpretations:

```text
Fire
→ flame
→ heat
→ combustion

Water
→ water
→ cooling
→ liquid movement

Wind
→ airflow
→ pressure
→ movement

Earth
→ stone
→ physical matter
→ structural force

Light
→ illumination
→ projection
→ radiant effects

Dark
→ darkness
→ suppression
→ absorption
```

More complex multi-element sigils may exist later, but a basic sigil normally begins with one primary element.

---

# 5. Stabilization Geometry

A geometric figure surrounds the main element.

This figure is the stabilizer.

Possible examples:

```text
Triangle
Square
Pentagon
Hexagon
Heptagon
Octagon
...
```

The number of corners determines how much magical complexity the sigil can safely describe.

It does not directly determine raw spell power.

The central rule is:

> More corners allow more complex instructions.

Example conceptual progression:

| Stabilizer | Complexity |
|---|---|
| Triangle | Low |
| Square | Basic |
| Pentagon | Moderate |
| Hexagon | High |
| Heptagon | Very High |
| Octagon | Extreme |

---

# 6. Stabilization Capacity

Auxiliary runes consume stabilization capacity.

A basic sigil may contain:

```text
Fire
+
Direction
+
Duration
```

and require only a relatively simple stabilizer.

A more complicated sigil may contain:

```text
Fire
+
Target Detection
+
Shape
+
Direction
+
Movement
+
Speed
+
Duration
+
Repeat
+
Signal Input
```

and therefore require a higher-order polygon.

The system should prevent players from describing extremely complicated magical behavior using a simple triangle.

---

# 7. Why Not Always Use the Highest Stabilizer?

Higher-order stabilizers should have disadvantages.

Possible costs:

- harder to draw accurately
- more space required
- greater material requirements
- higher sensitivity to malformed geometry
- greater setup time
- greater risk when damaged
- potentially higher wand or tool requirements

Therefore:

```text
More Corners
≠ Automatically Better
```

The player should use the simplest stabilizer capable of safely describing the intended effect.

---

# 8. Outer Circle

The outer circle contains Auxiliary Runes.

Auxiliary Runes do not normally create magical energy by themselves.

They describe how the main element behaves.

Example:

```text
Fire
```

defines only the element.

Adding Auxiliary Runes can describe:

```text
Where?
How?
How strong?
How large?
How long?
Which direction?
Which target?
When?
How often?
```

The outer ring is therefore the instruction layer of the sigil.

---

# 9. Auxiliary Rune Philosophy

Auxiliary Runes function like a visual magical language.

They should be:

- simple enough to recognize
- combinable
- reusable across different elements
- affected by orientation where useful
- increasingly complex at higher magical levels

The same Auxiliary Rune should work with many elements.

Example:

```text
Direction Up
+
Fire
→ fire moves upward
```

```text
Direction Up
+
Water
→ water moves upward
```

```text
Direction Up
+
Earth
→ earth effect propagates upward
```

The rune describes behavior rather than a specific spell.

---

# 10. Direction Runes

Direction is one of the simplest Auxiliary Rune families.

Arrow-like shapes can define direction.

Examples:

```text
↑
↓
←
→
↖
↗
↙
↘
```

Direction can also be defined relative to the center.

```text
Arrow pointing outward
→ Away from center

Arrow pointing inward
→ Toward center
```

Orientation is therefore meaningful.

---

# 11. Symmetrical Direction

If all direction runes are symmetrical, the spell has no preferred horizontal direction.

Example:

```text
←   ↑   →
    FIRE
←   ↓   →
```

The resulting fire may remain centered or spread symmetrically depending on the other Auxiliary Runes.

This is the basic concept used by the example flame sigil.

---

# 12. Position Runes

Position determines where an effect is created relative to the sigil.

Possible concepts:

```text
Center
Above
Below
Surface
Outer Edge
Target Position
```

Example:

```text
Fire
+
Above
→ flame appears above the sigil
```

Position and direction should remain separate concepts.

```text
Position
→ where the effect begins

Direction
→ where the effect moves
```

---

# 13. Strength Runes

Strength determines the intensity of the magical effect.

A simple visual system could use repeated marks.

Example:

```text
|
→ low power

||
→ moderate power

|||
→ high power
```

Alternatively, a single shape can change in length or complexity.

The exact symbol design remains open.

Strength can influence:

- damage
- heat
- force
- brightness
- amount of material
- shield resistance
- movement force

---

# 14. Size Runes

Size determines the physical scale of the effect.

Possible concepts:

```text
Small
Medium
Large
Massive
```

A visual grammar may use nested shapes.

Example:

```text
○
→ small

◎
→ medium

◉
→ large
```

Exact rune forms can be designed later.

---

# 15. Range Runes

Range determines how far an effect reaches.

Possible conceptual levels:

```text
Near
Medium
Far
Extreme
```

One possible visual concept is repeated arcs:

```text
)
→ short range

))
→ medium range

)))
→ long range
```

---

# 16. Shape Runes

Shape determines the geometry of the generated effect.

Possible forms:

```text
Point
Line
Cone
Sphere
Wall
Ring
Field
Beam
Arc
```

Examples:

```text
Fire + Wall
→ Fire Wall
```

```text
Water + Sphere
→ Water Sphere
```

```text
Light + Line
→ Light Beam
```

This allows the same element to produce many different effects.

---

# 17. Movement Runes

Movement determines how the generated effect behaves after creation.

Possible movement modes:

```text
Stationary
Linear
Follow
Orbit
Return
Expand
Contract
Spiral
```

Example:

```text
Light
+
Sphere
+
Orbit
+
Caster
→ glowing sphere orbits caster
```

Another example:

```text
Fire
+
Sphere
+
Follow
+
Target
→ fire sphere follows target
```

---

# 18. Speed Runes

Speed is separate from direction.

Direction tells the effect where to move.

Speed tells it how quickly.

Possible levels:

```text
Slow
Normal
Fast
Extreme
```

A repeated slash system could represent speed:

```text
/
→ slow

//
→ fast

///
→ very fast
```

Exact graphical representation remains open.

---

# 19. Duration Runes

Duration determines how long the effect remains active.

Possible values:

```text
Instant
Short
Temporary
Sustained
Persistent
```

Examples:

```text
Fire
+
Instant
→ burst
```

```text
Fire
+
Sustained
→ persistent flame
```

Persistent effects should generally require more stabilization capacity.

---

# 20. Target Runes

Target runes define what the sigil affects.

Possible concepts:

```text
Caster
Single Entity
Living Entity
Hostile Entity
Friendly Entity
Object
Block
Area
Nearest Target
```

The system should preferably use combinations of simple target concepts instead of a completely unique rune for every possible target.

Example conceptual primitives:

```text
•
→ single target

○
→ area

△
→ living being

□
→ object / block
```

These symbols are conceptual placeholders rather than final rune designs.

---

# 21. Trigger Runes

A sigil does not have to activate immediately.

Trigger runes determine when activation occurs.

Possible triggers:

```text
Immediate
Contact
Presence
Damage
Signal
Time
Light Change
Environmental Condition
```

Examples:

```text
Contact
+
Fire
→ fire trap
```

```text
Presence
+
Hostile
+
Fire
→ activates when hostile entity approaches
```

---

# 22. Delay Rune

Delay allows time between trigger and execution.

Example:

```text
Presence Trigger
↓
Delay
↓
Fire Explosion
```

Delay can be useful for:

- traps
- timed mechanisms
- staged effects
- synchronized sigils

A broken or interrupted line could visually represent delay.

---

# 23. Repeat Rune

Repeat causes an instruction or effect to occur multiple times.

Examples:

```text
Fire Projectile
+
Repeat
→ multiple fire projectiles
```

or:

```text
Pulse
+
Repeat
→ periodic activation
```

Repeated use of the Repeat Rune could increase repetition count rather than using normal written numbers.

---

# 24. Pulse Rune

Pulse is related to repetition but represents regular periodic activation.

Example:

```text
Light
+
Pulse
→ flashing light
```

```text
Wind
+
Pulse
→ repeating gusts
```

This is especially useful for machinery and traps.

---

# 25. Bind Rune

Bind attaches an effect to something.

Possible bindings:

```text
Bind + Caster
Bind + Entity
Bind + Block
Bind + Location
```

Example:

```text
Light
+
Sphere
+
Bind
+
Caster
→ light follows the caster
```

---

# 26. Store Rune

Store prevents immediate release of the magical effect.

The sigil holds the prepared effect until another instruction releases it.

Example:

```text
Fire
+
Store
+
Contact Trigger
→ stored fire trap
```

Storage makes preloaded sigils possible.

---

# 27. Release / Emit Rune

Emit releases an element or effect outward.

Example:

```text
Fire
+
Emit
+
Direction Outward
→ fire emitted away from sigil
```

This can be combined with:

- shape
- strength
- speed
- range
- target

---

# 28. Absorb Rune

Absorb performs the opposite function.

It draws an element or property inward.

Examples:

```text
Fire
+
Absorb
→ draws heat or fire inward
```

```text
Light
+
Absorb
→ removes light from an area
```

This creates useful effects without requiring every behavior to be tied directly to the Dark element.

---

# 29. Expand Rune

Expand causes an effect to increase in size.

Example:

```text
Fire
+
Sphere
+
Expand
→ growing fire sphere
```

---

# 30. Contract Rune

Contract reduces an effect toward a point or center.

Example:

```text
Wind
+
Contract
→ air pulled toward center
```

This may be useful for suction or compression effects.

---

# 31. Invert Rune

Invert reverses another instruction.

Examples:

```text
Push
+
Invert
→ Pull
```

```text
Expand
+
Invert
→ Contract
```

Invert should be an advanced Auxiliary Rune because it can dramatically change complex sigils.

It should require significant stabilization capacity.

---

# 32. Rotation Rune

Rotation causes the effect to rotate around an axis or point.

Possible uses:

```text
Wind
+
Rotation
→ vortex
```

```text
Fire
+
Ring
+
Rotation
→ rotating fire ring
```

Direction can determine clockwise or counterclockwise rotation.

---

# 33. Axis Rune

Axis defines the orientation used by rotation or movement.

Possible concepts:

```text
Horizontal
Vertical
Facing Direction
Surface Normal
Custom Direction
```

This becomes useful for advanced three-dimensional sigils.

---

# 34. Acceleration Rune

Acceleration changes velocity over time.

Example:

```text
Fire Projectile
+
Forward
+
Acceleration
→ projectile becomes faster
```

The opposite modifier could cause deceleration.

---

# 35. Spread Rune

Spread causes an effect to divide or widen.

Examples:

```text
Fire
+
Projectile
+
Spread
→ multiple diverging flames
```

```text
Wind
+
Cone
+
Spread
→ broad gust
```

---

# 36. Focus Rune

Focus concentrates an effect into a smaller region.

Example:

```text
Light
+
Focus
→ intense beam
```

```text
Fire
+
Focus
→ concentrated high-temperature flame
```

Spread and Focus are natural opposites.

---

# 37. Boundary Rune

Boundary restricts an effect to a defined region.

Example:

```text
Fire
+
Field
+
Boundary
→ fire exists only inside marked region
```

This can help prevent prepared magical effects from spreading uncontrollably.

---

# 38. Exclusion Rune

Exclusion defines something the sigil must ignore.

Example:

```text
Area Effect
+
Exclude Caster
```

or:

```text
Fire Field
+
Exclude Friendly
```

This should be more advanced than simple target selection because exclusions add extra logical conditions.

---

# 39. Condition Runes

Advanced sigils can contain simple conditional logic.

Examples:

```text
IF Hostile Entity Present
→ Activate
```

```text
IF Light Level Low
→ Activate Light
```

```text
IF Temperature High
→ Activate Water Effect
```

Conditional sigils require significantly more stabilization capacity.

---

# 40. Input Rune

Input allows a sigil to receive a magical signal from another construction.

```text
Input
→ receive activation or information
```

---

# 41. Output Rune

Output sends a magical signal.

```text
Output
→ send signal
```

Together:

```text
Sigil A
↓
Output
↓
Signal
↓
Input
↓
Sigil B
```

This creates the foundation for Arcane Engineering.

---

# 42. Linked Sigils

Multiple sigils can form networks.

Example:

```text
Detection Sigil
↓
Hostile detected
↓
Output signal
↓
Attack Sigil
↓
Fire projectile
```

This acts like magical automation.

It can eventually provide a magical equivalent to parts of Redstone without simply copying Redstone mechanics.

---

# 43. Priority and Rune Order

Auxiliary Rune order can matter.

Example:

```text
Fire
→ Sphere
→ Expand
```

means:

> Create a fire sphere, then expand it.

Whereas:

```text
Fire
→ Expand
→ Sphere
```

may mean:

> Expand the fire effect, then constrain it into a sphere.

This makes advanced sigils dependent on understanding the magical grammar rather than only knowing individual symbols.

---

# 44. Auxiliary Rune Categories

A useful internal classification is:

| Category | Example Functions |
|---|---|
| Direction | Up, Down, Left, Right, Toward, Away |
| Position | Above, Below, Center, Surface |
| Strength | Weak, Normal, Strong, Extreme |
| Size | Small, Medium, Large |
| Range | Near, Medium, Far |
| Shape | Point, Line, Cone, Sphere, Wall, Ring, Field |
| Movement | Stationary, Linear, Follow, Orbit, Return |
| Speed | Slow, Normal, Fast |
| Duration | Instant, Temporary, Sustained |
| Target | Caster, Entity, Area, Object |
| Trigger | Immediate, Contact, Presence, Signal |
| Delay | Delayed activation |
| Repeat | Repeated execution |
| Pulse | Periodic execution |
| Bind | Attach effect |
| Store | Hold effect |
| Emit | Release outward |
| Absorb | Draw inward |
| Expand | Increase size |
| Contract | Decrease size |
| Rotate | Rotational motion |
| Axis | Orientation |
| Accelerate | Increase speed |
| Spread | Divide / widen |
| Focus | Concentrate |
| Boundary | Limit area |
| Exclude | Ignore selected target |
| Condition | Conditional behavior |
| Input | Receive signal |
| Output | Send signal |
| Invert | Reverse another operation |

---

# 45. Complexity Example

A simple sigil:

```text
Fire
+
Above
+
Sustained
```

Possible result:

> A stationary flame burns above the sigil.

A more complicated sigil:

```text
Fire
+
Sphere
+
Above
+
Hostile Target
+
Follow
+
Fast
+
Repeat
```

Possible result:

> The sigil creates repeated fire spheres above itself which track hostile targets.

Such a construction would require significantly more stabilization than the simple flame.

---

# 46. Example Sigil from Current Design

The current example sigil contains:

- two concentric circles
- Fire as its central main element
- a square stabilizer
- symmetrical directional instructions around the outer section

Conceptual interpretation:

```text
Main Element:
Fire

Stabilization:
Square

Position:
Above Sigil

Directional Bias:
Symmetrical

Result:
A stable flame appears above the sigil.
```

Because the directional instructions are symmetrical, the flame has no preferred horizontal travel direction.

It therefore remains associated with the sigil instead of launching to one side.

---

# 47. Relationship to Normal Spell Casting

Sigils and normal spells share the same magical foundations but have different purposes.

```text
NORMAL SPELL
→ free hand
→ fast
→ flexible
→ player actively casts
→ ideal for combat

SIGIL
→ geometric
→ prepared
→ precise
→ can persist
→ can contain triggers and logic
→ ideal for traps, defense, automation and complex magic
```

A powerful mage may use both systems.

---

# 48. Relationship to Enchanting

Sigils should also remain distinct from enchanting.

```text
Spell Casting
→ temporary direct magic

Sigils
→ programmed magical structures

Enchanting
→ persistent magic bound into items or objects
```

All three systems can share magical theory without becoming identical gameplay systems.

---

# 49. Failure and Damage

A malformed sigil can fail.

Possible causes:

- incorrect element rune
- malformed stabilizer
- broken outer circle
- incompatible Auxiliary Runes
- too many instructions for stabilizer capacity
- incorrect orientation
- damage to the physical sigil

Possible outcomes:

```text
Minor Error
→ reduced effect

Moderate Error
→ wrong behavior

Major Error
→ unstable activation

Severe Error
→ destructive magical failure
```

Complex sigils should therefore be dangerous to experiment with.

---

# 50. Damaged Sigils

Because sigils are physical prepared constructions, damage can alter them.

Example:

```text
One direction rune damaged
↓
symmetry lost
↓
stationary flame gains directional movement
```

This creates interesting gameplay where partially damaged magical machinery behaves unpredictably instead of simply switching off.

---

# 51. Arcane Engineering

The Sigil System is a major foundation for Arcane Engineering.

Possible future systems:

```text
Detection Sigils
Control Sigils
Signal Networks
Magical Doors
Defensive Wards
Automated Spell Turrets
Teleport Networks
Environmental Control
Magical Pumps
Magical Sensors
```

These should use the same runic grammar rather than becoming unrelated magical machines.

---

# 52. Design Principle

A sigil should answer three questions:

```text
WHAT?
→ Main Element

HOW COMPLEX?
→ Stabilization Geometry

WHAT SHOULD IT DO?
→ Auxiliary Runes
```

The intended experience is:

```text
Learn the element
↓
Learn auxiliary instructions
↓
Understand combinations
↓
Construct a sigil
↓
Test it
↓
Refine it
↓
Build complex magical systems
```

The player should eventually be able to look at a sigil and understand it almost like reading a diagram or program.

That is the core identity of Hardwrought's Sigil System.
