# Rods and plates

Every solid metal with an ingot has a rod and a plate: iron, copper, gold, bronze, and every ore metal
of `Metal` except mercury, which is liquid. They are forged on the anvil like tool parts. The target
shape is the item's own silhouette:

| Result | From | Gives |
| --- | --- | --- |
| Rod | 1 ingot, drawn out | 4 rods |
| Plate | 1 ingot, beaten flat | 1 plate |

Both melt as their metal (`Smelting.materialOf`), take heat and glow while hot. Registered in
`smithing/MetalStock`, forged through `Smithing.Recipe#makes`. Textures come from
`tools/metal_stock_textures.py`, which takes each metal's shades from its ingot. Run
`tools/glow_textures.py` after it.

Drill frames are made of rods (see [ore-drill.md](ore-drill.md)).

Test: `SmithingGameTests.everySolidMetalIsForgedIntoRodsAndPlates`.
