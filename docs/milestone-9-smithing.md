# Milestone 9: Smithing

Specification milestone 9 — forging minigame, weapon quality, tool quality, heat treatment — and
sections 37 and 38. (`milestone-9.md` covers the packs and bench tiers that were built under the same
number before; this is the specification's milestone 9 itself.)

Metal is no longer crafted. It is heated, hammered and treated, and how well that was done stays in
the finished tool.

## The chain

```text
raw ore ──heat──▶ hot raw ore ──anvil──▶ ingot
ingots  ──heat──▶ hot ingots  ──anvil──▶ head or blade ──water or air──▶ (reheat gently: temper)
head + sticks ──grid──▶ tool, carrying the head's quality
```

Powder and bronze mixture are still **cast**: melted in a furnace into an ingot, no anvil. That is the
crusher's reward — a way to a bar that skips the hammering.

## Heat

`Heat` is a data component: a temperature and the game time it was set. The current temperature is
worked out on demand by Newton's law of cooling towards 20 °C, so a hot stack is not a changed stack
every tick. Iron out of the fire stays workable for a little under half a minute and is safe to touch
after about two minutes. A cooling pass every two seconds drops the component from cold stacks so
they stack again.

All smithing temperatures are fractions of a metal's melting point (from the material definitions):

| | fraction | iron (1538 °C) |
| --- | --- | --- |
| working heat | 0.55 – 0.85 | 846 – 1307 °C |
| hardens when quenched from above | 0.45 | 692 °C |
| tempers when warmed back to | 0.12 – 0.30 | 185 – 461 °C |

Only iron hardens. Bronze and gold come out of the water as soft as they went in.

Quenching is throwing the piece into water or into a water cauldron. The steam is water taken: one
layer (125 mB) from open water, one level from a cauldron.

Hot metal glows: each metal item has a pale copy of its texture that an item-model condition switches
to above 450 °C, tinted by temperature from dull red to yellow-white (`HeatLook`,
`tools/glow_textures.py`).

## Heating

**Furnaces heat instead of smelting.** A piece of forge metal with no smelting recipe is handed a
synthetic recipe whose result is the piece itself; vanilla then does fuel, cook time and the brick
furnace's slowness, and the complete inserted stack comes out together as hot as the furnace gets —
never past the top of its working range. A real smelting recipe still consumes only one item per
operation. Progress and quality on the piece survive the trip. A furnace too cold to reach the
bottom of the working range does not heat it at all and burns no fuel on it.

The raw-ore-to-ingot recipes are gone. Vanilla's recipe ids are kept (vanilla data refers to them):
the raw-ore ids now cast powder, the ore-block ids roast the block down to raw ore. The campfire casts
only the soft metals (tin, lead, zinc) and bronze.

**The forge** (`ForgeBlock`) is the hearth that goes further. Coal in, pieces laid in the coals by
hand, taken out by hand. Its fire warms and cools gradually and the pieces follow it — which is what
makes it the place to temper.

| forge | heads for |
| --- | --- |
| plain | 1300 °C |
| lined with 8 refractory bricks | 1600 °C |
| with a worked bellows beside it | 2000 °C |
| lined and blown | 3500 °C |

The bellows only blows while something turns it — a hand crank on it or a driven shaft into it.

## The anvil

Hammer in the main hand, the hot piece in the off hand, use an anvil. The **wooden anvil** is a
hardwood stump, end grain up — stone would split — and wears out after twelve finished pieces; work
on it is capped at 85 % craftsmanship. Vanilla's iron anvils are the better rung (100 %, 97 % chipped,
93 % damaged) and occasionally take a chip.

The minigame (`ForgingScreen`) shows the piece at eight times size, one square per pixel of the item
texture, with the shape it is to become faintly behind it. A click is a blow: every wrong square within
one of it is put right — metal standing proud is driven in, a gap is filled. A blow that finds nothing
wrong is wasted. Below working heat a blow does nothing at all and is not counted: cold iron does not
move, and the piece has to go back to the fire. Each blow takes 6 °C out of the piece,
costs a little stamina and now and then wears the hammer. Going back to the fire keeps the work.

Where a piece can become several things (an ingot into any head), the screen asks first and shows
what each costs. **Give up** returns everything that went in, as hot as it is now.

The server judges all of it. The shapes, the heat, every blow and its effect are written onto the item
(`ForgingState`); the client only says where it struck. The one thing the client supplies is the two
shapes at the start, because only the client has the textures.

Craftsmanship, from the blows: `0.20 + 0.55 × accuracy + 0.25 × economy`, capped by
the poorest anvil used. Accuracy is useful blows over all blows; economy is how close the blow count
came to a third of the squares that needed fixing.

| part | ingots |
| --- | --- |
| pickaxe head, axe head, halberd head | 3 |
| hoe head, sword blade | 2 |
| shovel head, dagger blade | 1 |
| greatsword blade | 4 |

Iron has all eight, gold the five vanilla tools, bronze the pick and hatchet heads.

## Quality

`ForgeQuality` — craftsmanship and treatment — rides on the part and is copied onto the tool by the
assembly recipes (`crafting_transmute`, part plus sticks). It is applied where it is felt rather than
written into the item, so the balance can change without rewriting every tool:

| | from craftsmanship c | quenched | tempered |
| --- | --- | --- | --- |
| mining speed | 0.80 + 0.30 c | × 1.10 | × 1.06 |
| durability | 0.65 + 0.60 c | × 0.75 | × 1.12 |
| damage | 0.85 + 0.25 c | × 1.08 | × 1.05 |

Poor work falls short of vanilla, good work and a proper treatment beat it — section 37's own example.
A tool nobody forged (loot, villagers, other mods) carries no quality and is exactly vanilla.

## Gloves

Hot metal in a bare hand burns, 2 damage a second. In the bag it is fine. **Smithing gloves** go in a
third square of the worn strap, below the lamp, and make hot metal safe to hold.

## The book

The anvil's recipes are registered with `WorldRecipes` straight from the recipe table, with the anvil as
the station, so a new metal or part appears without anyone touching the compendium. Three notes join
the Thoughts: *Iron wants hammering* (gloves, anvil and hammer, all three), a rewritten *Rust in the
rock* (heads and handles), and *Fire and water* (quenching, tempering, the forge and bellows).

## Tests

`SmithingGameTests`, ten tests: cooling and every treatment rule; a brick furnace heating raw iron
instead of smelting it; a full anvil session from hot raw iron to a bar and from bars to a head, with
the wooden anvil's wear and cap; wasted and cold blows costing craftsmanship; giving up returning all
three bars; a head's quality reaching the vanilla pickaxe it becomes and making it last longer and dig
faster; bare and gloved hands; the forge's four heats and a piece coming up to working heat in it; a hot
head quenched by being thrown into water.

`SmithingClientGameTest` photographs hot metal glowing in hand, the anvil, the choice of what to make,
and a pick head under way.

## Open

- The 15 forged parts have finished Vanilla-style sprites for iron, gold and bronze. The forge,
  bellows, gloves, fireclay and refractory brick are still placeholders; see `textureRequirements.md`.
  The wooden anvil stays as it is.
- Steel, and with it carbon, is the next metal the forge is for; nothing makes it yet.
- Armour is still crafted.
- The client supplies the two shapes. A modified client could send an easy pair; the server refuses
  anything under six squares of work, which stops the trivial case but not a determined one.
