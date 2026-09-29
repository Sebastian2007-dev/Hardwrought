"""Writes the structural anchor item texture: an iron tie rod with a square anchor plate.

The plate sits top left, the threaded rod runs diagonally down to the right, shaded like vanilla iron.
"""
from PIL import Image

OUT = "src/main/resources/assets/hardwrought/textures/item/structural_anchor.png"
OUTLINE = (54, 54, 58, 255)
DARK = (114, 114, 120, 255)
MID = (168, 168, 172, 255)
LIGHT = (216, 216, 216, 255)
SHINE = (248, 248, 248, 255)

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()

# Rod: two pixels wide along the diagonal, lit on its upper edge, threads as dark notches.
for i in range(5, 15):
    px[i, i] = LIGHT
    px[i, i + 1] = MID if i % 2 else DARK
    px[i + 1, i] = OUTLINE
    if i + 2 < 16:
        px[i, i + 2] = OUTLINE
px[14, 14] = MID
px[15, 15] = OUTLINE

# Plate: a square washer with a bevel, drawn over the rod's head.
for x in range(1, 8):
    for y in range(1, 8):
        if x in (1, 7) or y in (1, 7):
            px[x, y] = OUTLINE
        elif x == 2 or y == 2:
            px[x, y] = LIGHT
        elif x == 6 or y == 6:
            px[x, y] = DARK
        else:
            px[x, y] = MID
# The nut on the plate.
for x, y in ((3, 3), (4, 3), (3, 4)):
    px[x, y] = SHINE
px[4, 4] = DARK
px[5, 5] = LIGHT

img.save(OUT)
print("wrote", OUT)
