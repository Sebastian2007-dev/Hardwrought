"""Generates the water a part-filled bucket shows in its mouth.

A Hardwrought bucket holds a measured amount rather than one block or nothing, so it has to be able
to look part full. The bucket is opaque below its rim, which means the only place water can be seen
at all is the three rows inside the mouth — so there are three visible levels and no more. The
exact figure is on the tooltip and the bar under the item; this is the glance.

Only the water is drawn here, on transparent ground. The bucket itself stays vanilla's own sprite,
referenced as a second layer by the models rather than copied into ours.

Run from the repository root:  python tools/bucket_water_levels.py
"""
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/hardwrought"

# The rows inside the bucket's mouth, deepest first, and how wide the opening is at each.
# Taken from the shape of the vanilla bucket sprite: the mouth is an ellipse, wider at the top.
ROWS = [
    (5, 5, 10),
    (4, 3, 12),
    (3, 3, 12),
]

# A surface that catches the light and a body that does not, the way still water reads.
SURFACE = (96, 136, 247)
BODY = (52, 95, 218)
DEEP = (35, 79, 204)


def level(filled_rows):
    """The water in a bucket filled to this many of the visible rows."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()
    shown = ROWS[:filled_rows]
    top = shown[-1][0]
    for row, start, end in shown:
        for x in range(start, end + 1):
            # The top row is the surface; below it the water darkens with depth.
            if row == top:
                colour = SURFACE if (x + row) % 2 == 0 else BODY
            else:
                colour = DEEP if row == ROWS[0][0] else BODY
            pixels[x, row] = (*colour, 255)
    return image


def main():
    target = ASSETS / "textures/item"
    target.mkdir(parents=True, exist_ok=True)
    for filled in (1, 2):
        path = target / f"bucket_water_{filled}.png"
        level(filled).save(path)
        print(f"wrote {path.relative_to(Path(__file__).resolve().parent.parent)}")


if __name__ == "__main__":
    main()
