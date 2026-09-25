"""Writes the finished ore drill: one machine drawn across the head and the seventeen frame blocks round it.

The drill is laid out once as boxes in the space of the whole structure, three blocks wide and deep and
two high (48 x 32 x 48 pixels, the head at the middle of the bottom layer). Each box is then cut along the
block borders, and every block gets the pieces that fall into it. Faces made by a cut are left off, so
the machine shows no seams where its blocks meet.

A frame block draws its pieces in plate of its own metal, so a drill of mixed frames still shows which
block is the weak one. Loose frames keep the lattice look from tools/drill_assets.py.

Run from the repository root:  python tools/ore_drill_formed.py
(tools/drill_assets.py runs it too, since both write the drill's blockstates.)
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/hardwrought"
TIERS = ("bronze", "iron", "nickel", "chromium", "titanium")
FACES = ("north", "south", "west", "east", "up", "down")

# The whole machine, in structure pixels: (from, to, texture, faces left open or None for all).
MACHINE = [
    # The casing the head sits in, a band of bracing round its top edge.
    ((0, 0, 0), (48, 11, 48), "#plate"),
    ((1, 11, 1), (47, 13, 47), "#brace"),
    # The drive housing over the head.
    ((14, 13, 14), (34, 23, 34), "#gearbox"),
    ((12, 23, 12), (36, 25, 36), "#brace"),
    # The mast the bit is fed down, up to the crown.
    ((20, 25, 20), (28, 28, 28), "#steel"),
    # Corner posts of the derrick.
    ((0, 13, 0), (4, 32, 4), "#plate"),
    ((44, 13, 0), (48, 32, 4), "#plate"),
    ((0, 13, 44), (4, 32, 48), "#plate"),
    ((44, 13, 44), (48, 32, 48), "#plate"),
    # Middle rails between the posts.
    ((4, 20, 1), (44, 22, 3), "#plate"),
    ((4, 20, 45), (44, 22, 47), "#plate"),
    ((1, 20, 4), (3, 22, 44), "#plate"),
    ((45, 20, 4), (47, 22, 44), "#plate"),
    # The crown: a ring of beams on the posts, and a crossbeam over the mast.
    ((4, 28, 0), (44, 32, 4), "#plate"),
    ((4, 28, 44), (44, 32, 48), "#plate"),
    ((0, 28, 4), (4, 32, 44), "#plate"),
    ((44, 28, 4), (48, 32, 44), "#plate"),
    ((4, 28, 20), (44, 32, 28), "#brace"),
]


def uv(face, lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return {
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
    }[face]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def cell_offsets():
    """The frame blocks in the order OreDrillBlockEntity.framePositions lists them: part 1 is the first."""
    cells = []
    for dy in (0, 1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx, dy, dz) != (0, 0, 0):
                    cells.append((dx, dy, dz))
    return cells


def pieces(cell):
    """The parts of the machine inside one block, as elements in that block's own pixels."""
    dx, dy, dz = cell
    origin = ((dx + 1) * 16, dy * 16, (dz + 1) * 16)
    elements = []
    for lo, hi, texture in MACHINE:
        a = [max(lo[i], origin[i]) for i in range(3)]
        b = [min(hi[i], origin[i] + 16) for i in range(3)]
        if any(a[i] >= b[i] for i in range(3)):
            continue
        # Only faces the box really has; the faces of a cut are inside the machine.
        real = {
            "west": a[0] == lo[0], "east": b[0] == hi[0],
            "down": a[1] == lo[1], "up": b[1] == hi[1],
            "north": a[2] == lo[2], "south": b[2] == hi[2],
        }
        local_lo = [a[i] - origin[i] for i in range(3)]
        local_hi = [b[i] - origin[i] for i in range(3)]
        faces = {}
        for face in FACES:
            if not real[face]:
                continue
            entry = {"uv": uv(face, local_lo, local_hi), "texture": texture}
            # A face on the border of the block is hidden by whatever solid block stands there.
            on_border = {"west": local_lo[0] == 0, "east": local_hi[0] == 16, "down": local_lo[1] == 0,
                         "up": local_hi[1] == 16, "north": local_lo[2] == 0, "south": local_hi[2] == 16}[face]
            if on_border:
                entry["cullface"] = face
            faces[face] = entry
        if faces:
            elements.append({"from": local_lo, "to": local_hi, "faces": faces})
    return elements


def plate(tier):
    """Riveted plate in the three shades of the loose frame of the same metal."""
    lattice = Image.open(ASSETS / f"textures/block/drill_frame_{tier}.png").convert("RGBA")
    dark = lattice.getpixel((0, 0))[:3]
    mid = lattice.getpixel((2, 14))[:3]
    light = lattice.getpixel((1, 5))[:3]
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            colour = mid
            if x in (0, 15) or y in (0, 15):
                colour = dark
            elif x == 1 or y == 1:
                colour = light
            elif (x * 7 + y * 3) % 11 == 0:
                colour = light
            img.putpixel((x, y), colour + (255,))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), dark + (255,))
        img.putpixel((x - 1, y - 1), light + (255,))
    return img


def textures(plate_texture):
    return {"plate": plate_texture, "particle": plate_texture,
            "brace": "hardwrought:block/hardwrought/iron_brace",
            "steel": "hardwrought:block/hardwrought/drill_steel",
            "gearbox": "hardwrought:block/hardwrought/gearbox_side"}


def main():
    formed = ASSETS / "models/block/ore_drill_formed"
    comment = "Generated by tools/ore_drill_formed.py."
    cells = cell_offsets()
    for part, cell in enumerate(cells, start=1):
        write(formed / f"part_{part}.json", {"parent": "minecraft:block/block", "__comment": comment,
                                             "elements": pieces(cell)})
    for tier in TIERS:
        plate(tier).save(ASSETS / f"textures/block/drill_plate_{tier}.png")
        variants = {"part=0": {"model": f"hardwrought:block/drill_frame_{tier}"}}
        for part in range(1, len(cells) + 1):
            write(formed / tier / f"part_{part}.json", {
                "parent": f"hardwrought:block/ore_drill_formed/part_{part}", "__comment": comment,
                "textures": textures(f"hardwrought:block/drill_plate_{tier}")})
            variants[f"part={part}"] = {"model": f"hardwrought:block/ore_drill_formed/{tier}/part_{part}"}
        write(ASSETS / f"blockstates/drill_frame_{tier}.json", {"variants": variants})

    write(formed / "head.json", {"parent": "minecraft:block/block", "__comment": comment,
                                 "textures": textures("hardwrought:block/hardwrought/drill_steel"),
                                 "elements": pieces((0, 0, 0))})
    variants = {}
    for running in ("false", "true"):
        variants[f"formed=false,running={running}"] = {"model": "hardwrought:block/ore_drill"}
        variants[f"formed=true,running={running}"] = {"model": "hardwrought:block/ore_drill_formed/head"}
    write(ASSETS / "blockstates/ore_drill.json", {"variants": variants})
    print("formed ore drill written")


if __name__ == "__main__":
    main()
