"""Generates the joined forge: one flat brick hearth drawn across 2x2 or 3x3 forge blocks.

Every block of a formed forge carries a ``part`` number (see ForgeMultiblock.partIndex). Its model
shows only the faces on the outside of the whole hearth, and each outer face takes its tile of one
long side strip: brick with stone corners, a stone plinth and coping, and a fire mouth in the middle
that glows while the forge burns. The top is one shared pit with a rim only along the outer edge.

The pit is drawn empty - a floor of ash - and the bed of coal is a second model laid into it by the
blockstate, at one of four heights, so the hearth visibly fills up as fuel goes in. A forge on its
own is part 0 and is drawn the same way.

Run from the repository root:  python tools/forge_multiblock.py
"""
import json
import os
import random
import zipfile
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/hardwrought"
JAR = Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar"

SMALL_FIRST, LARGE_FIRST, MAX_PART = 1, 5, 35
# How deep the pit is: its floor, and the top of the bed at each fill level of ForgeBlock.FUEL.
FLOOR = 6
BED_TOPS = {1: 8, 2: 10, 3: 12, 4: 14}


def vanilla(name):
    with zipfile.ZipFile(JAR) as jar:
        image = Image.open(BytesIO(jar.read(f"assets/minecraft/textures/block/{name}.png"))).convert("RGBA")
    return image.crop((0, 0, 16, 16))


def tiled(tile, size):
    out = Image.new("RGBA", (size, size))
    for x in range(0, size, 16):
        for y in range(0, size, 16):
            out.paste(tile, (x, y))
    return out


def shade(color, factor):
    r, g, b, a = color
    return (min(255, int(r * factor)), min(255, int(g * factor)), min(255, int(b * factor)), a)


def side_texture(edge, lined, lit):
    """The whole side of the hearth: 16 * edge wide, one block high."""
    n = 16 * edge
    h = 16
    brick = tiled(vanilla("mud_bricks" if lined else "bricks"), n).crop((0, 0, n, h))
    stone = tiled(vanilla("stone_bricks"), n).crop((0, 0, n, h))
    cobble = tiled(vanilla("cobblestone"), n).crop((0, 0, n, h))
    img = brick.copy()
    px = img.load()
    sp, cp = stone.load(), cobble.load()
    for y in range(h):
        for x in range(n):
            if y >= h - 3:                      # plinth
                px[x, y] = cp[x, y]
            elif y < 2:                         # coping
                px[x, y] = shade(sp[x, y], 0.95 if y == 0 else 0.8)
            elif x < 2 or x >= n - 2:           # corner quoins
                px[x, y] = shade(sp[x, y], 0.9)

    # The fire mouth: a low arch standing on the plinth, in the middle of the side.
    width = 8 if edge <= 2 else 10
    height = 8
    left = n // 2 - width // 2
    bottom = h - 3                              # first plinth row
    top = bottom - height
    radius = width / 2
    cx = n / 2 - 0.5
    rng = random.Random(edge * 7 + lined * 3 + lit)
    def inside(x, y, grow=0.0):
        if x < left - grow or x > left + width - 1 + grow or y >= bottom or y < top - grow:
            return False
        arch_y = top + radius
        if y >= arch_y:
            return True
        return ((x - cx) ** 2 + (y - arch_y) ** 2) <= (radius - 0.5 + grow) ** 2

    for y in range(h):
        for x in range(n):
            if inside(x, y):
                depth = (bottom - y) / height   # 0 at the floor, 1 at the crown
                if lit:
                    hot = (255, 214, 96, 255)
                    deep = (176, 58, 20, 255)
                    t = min(1.0, depth * 1.2)
                    color = tuple(int(hot[i] * (1 - t) + deep[i] * t) for i in range(3)) + (255,)
                    if y >= bottom - 2:
                        color = (255, 150, 40, 255) if rng.random() < 0.55 else (120, 30, 14, 255)
                    elif rng.random() < 0.08:
                        color = (255, 244, 180, 255)
                else:
                    base = 24 + int(18 * (1 - depth))
                    color = (base + 6, base, base - 4, 255)
                    if y >= bottom - 2 and rng.random() < 0.5:
                        color = (46, 44, 44, 255)
                px[x, y] = color
            elif inside(x, y, grow=1.2):
                px[x, y] = shade(sp[x, y], 0.75)   # stone voussoirs around the mouth
    return img


def hearth_floor():
    """Cold ash and cinders at the bottom of an empty pit."""
    base = vanilla("cobblestone")
    rng = random.Random(38)
    out = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r, g, b, a = base.getpixel((x, y))
            v = int((r + g + b) / 3 * 0.42)
            color = (v + 4, v + 2, v, 255)
            roll = rng.random()
            if roll < 0.10:
                color = (150, 146, 140, 255)          # pale ash
            elif roll < 0.14:
                color = (18, 16, 16, 255)             # a cinder
            out.putpixel((x, y), color)
    return out


def tiles(strip, edge):
    return [strip.crop((16 * i, 0, 16 * i + 16, 16)) for i in range(edge)]


def tile_uv(face, edge, b, lo, hi):
    """Which tile of the side strip a face shows, and the rectangle of it, in uv units (0-16)."""
    bx, bz = b
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    v = (16 - y1, 16 - y0)
    if face == "north":
        tile, u = edge - 1 - bx, (16 - x1, 16 - x0)
    elif face == "south":
        tile, u = bx, (x0, x1)
    elif face == "west":
        tile, u = bz, (z0, z1)
    else:
        tile, u = edge - 1 - bz, (16 - z1, 16 - z0)
    return tile, [u[0], v[0], u[1], v[1]]


def outer_faces(edge, b, lo, hi, outer):
    faces = {}
    for face in outer:
        tile, uv = tile_uv(face, edge, b, lo, hi)
        faces[face] = {"uv": uv, "texture": f"#side{tile}", "cullface": face}
    return faces


def part_model(edge, b):
    bx, bz = b
    last = edge - 1
    outer = [f for f, on in (("north", bz == 0), ("south", bz == last), ("west", bx == 0), ("east", bx == last)) if on]
    elements = []
    body = outer_faces(edge, b, (0, 0, 0), (16, FLOOR, 16), outer)
    body["up"] = {"uv": [0, 0, 16, 16], "texture": "#floor"}
    body["down"] = {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}
    elements.append({"from": [0, 0, 0], "to": [16, FLOOR, 16], "faces": body})

    def rim(lo, hi, own, inner):
        faces = outer_faces(edge, b, lo, hi, own)
        x0, _, z0 = lo
        x1, _, z1 = hi
        faces["up"] = {"uv": [x0, z0, x1, z1], "texture": "#rim", "cullface": "up"}
        span = (x1 - x0) if inner in ("north", "south") else (z1 - z0)
        faces[inner] = {"uv": [0, 0, span, 16 - FLOOR], "texture": "#rim"}
        elements.append({"from": list(lo), "to": list(hi), "faces": faces})

    if bz == 0:
        rim((0, FLOOR, 0), (16, 16, 3), ["north"] + [f for f in ("west", "east") if f in outer], "south")
    if bz == last:
        rim((0, FLOOR, 13), (16, 16, 16), ["south"] + [f for f in ("west", "east") if f in outer], "north")
    z_lo = 3 if bz == 0 else 0
    z_hi = 13 if bz == last else 16
    if bx == 0:
        rim((0, FLOOR, z_lo), (3, 16, z_hi), ["west"], "east")
    if bx == last:
        rim((13, FLOOR, z_lo), (16, 16, z_hi), ["east"], "west")
    return elements


def bed_model(top):
    """The coal lying in the pit, up to this height. Only its top shows; the rim hides the rest."""
    return {"from": [0, FLOOR, 0], "to": [16, top, 16],
            "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#bed"}}}


def parts():
    yield 0, 1, (0, 0)
    for z in range(2):
        for x in range(2):
            yield SMALL_FIRST + x + 2 * z, 2, (x, z)
    for z in range(3):
        for x in range(3):
            yield LARGE_FIRST + x + 3 * z, 3, (x, z)


def suffix(lined, lit):
    return ("_lined" if lined else "") + ("_lit" if lit else "")


def main():
    tex_dir = ASSETS / "textures/block/forge"
    model_dir = ASSETS / "models/block/forge"
    tex_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)
    for old in model_dir.glob("part_*.json"):
        old.unlink()
    for old in tex_dir.glob("*_side*.png"):
        old.unlink()
    for old in model_dir.glob("bed_*.json"):
        old.unlink()
    hearth_floor().save(tex_dir / "hearth_floor.png")

    for edge, name in ((1, "single"), (2, "small"), (3, "large")):
        for lined in (False, True):
            for lit in (False, True):
                for i, tile in enumerate(tiles(side_texture(edge, lined, lit), edge)):
                    tile.save(tex_dir / f"{name}_side_{i}{suffix(lined, lit)}.png")

    known = {}
    for part, edge, b in parts():
        known[part] = True
        name = {1: "single", 2: "small", 3: "large"}[edge]
        (model_dir / f"part_{part}_shape.json").write_text(json.dumps({
            "parent": "minecraft:block/block",
            "__comment": f"Generated by tools/forge_multiblock.py: block {b} of the {name} forge.",
            "elements": part_model(edge, b),
        }, indent=2) + "\n", encoding="utf-8")
        for lined in (False, True):
            for lit in (False, True):
                brick = "minecraft:block/mud_bricks" if lined else "minecraft:block/bricks"
                textures = {f"side{i}": f"hardwrought:block/forge/{name}_side_{i}{suffix(lined, lit)}"
                            for i in range(edge)}
                textures.update({
                    "rim": brick,
                    "floor": "hardwrought:block/forge/hearth_floor",
                    "bottom": "minecraft:block/cobblestone",
                    "particle": brick,
                })
                # The forge on its own keeps its old model names; its item is drawn from them.
                target = (ASSETS / "models/block" / f"forge{suffix(lined, lit)}.json" if part == 0
                          else model_dir / f"part_{part}{suffix(lined, lit)}.json")
                target.write_text(json.dumps({
                    "parent": f"hardwrought:block/forge/part_{part}_shape",
                    "textures": textures,
                }, indent=2) + "\n", encoding="utf-8")

    for level, top in BED_TOPS.items():
        for lit in (False, True):
            (model_dir / f"bed_{level}{suffix(False, lit)}.json").write_text(json.dumps({
                "parent": "minecraft:block/block",
                "__comment": "Generated by tools/forge_multiblock.py: the coal in the pit.",
                "textures": {
                    "bed": "hardwrought:block/hardwrought/coal_bed_hot" if lit else "hardwrought:block/hardwrought/coal_bed",
                    "particle": "hardwrought:block/hardwrought/coal_bed",
                },
                "elements": [bed_model(top)],
            }, indent=2) + "\n", encoding="utf-8")

    # Multipart: the hearth by its part, and the bed laid into it by how much fuel there is.
    single = "|".join(str(part) for part in range(MAX_PART + 1) if part == 0 or part not in known)
    multipart = []
    for lined in (False, True):
        for lit in (False, True):
            flags = {"lined": str(lined).lower(), "lit": str(lit).lower()}
            multipart.append({"when": {**flags, "part": single},
                              "apply": {"model": f"hardwrought:block/forge{suffix(lined, lit)}"}})
            for part in sorted(known):
                if part == 0:
                    continue
                multipart.append({"when": {**flags, "part": str(part)},
                                  "apply": {"model": f"hardwrought:block/forge/part_{part}{suffix(lined, lit)}"}})
    for level in BED_TOPS:
        for lit in (False, True):
            multipart.append({"when": {"fuel": str(level), "lit": str(lit).lower()},
                              "apply": {"model": f"hardwrought:block/forge/bed_{level}{suffix(False, lit)}"}})
    (ASSETS / "blockstates/forge.json").write_text(json.dumps({"multipart": multipart}, indent=2) + "\n",
                                                   encoding="utf-8")
    print(f"{len(known)} parts, {len(multipart)} multipart cases")


if __name__ == "__main__":
    main()
