"""Generates the joined forge: one big brick furnace drawn across 8 or 26 forge blocks.

Every block of a formed forge carries a ``part`` number (see ForgeMultiblock.partIndex). Its model
shows only the faces on the outside of the whole structure, and each outer face takes its share of
one big side texture: brick walls with stone corners, a stone plinth and coping, and a fire mouth
in the middle of the bottom row that glows while the forge burns. The top layer is one shared pit
of coals with a rim only along the outer edge.

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

SMALL_FIRST, LARGE_FIRST, MAX_PART = 1, 9, 35


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
    n = 16 * edge
    brick = tiled(vanilla("mud_bricks" if lined else "bricks"), n)
    stone = tiled(vanilla("stone_bricks"), n)
    cobble = tiled(vanilla("cobblestone"), n)
    img = brick.copy()
    px = img.load()
    sp, cp = stone.load(), cobble.load()
    for y in range(n):
        for x in range(n):
            if y >= n - 3:                      # plinth
                px[x, y] = cp[x, y]
            elif y < 2:                         # coping
                px[x, y] = shade(sp[x, y], 0.95 if y == 0 else 0.8)
            elif x < 2 or x >= n - 2:           # corner quoins
                px[x, y] = shade(sp[x, y], 0.9)

    # The fire mouth: an arch standing on the plinth, in the middle of the bottom row.
    width = 10 if edge == 2 else 12
    height = 10 if edge == 2 else 12
    left = n // 2 - width // 2
    bottom = n - 3                              # first plinth row
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

    for y in range(n):
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


def big_uv(face, edge, b, lo, hi):
    """The rectangle of the big side texture a face covers, in model uv units (0-16)."""
    n = 16 * edge
    bx, by, bz = b
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    v = (n - (by * 16 + y1), n - (by * 16 + y0))
    if face == "north":
        u = (n - (bx * 16 + x1), n - (bx * 16 + x0))
    elif face == "south":
        u = (bx * 16 + x0, bx * 16 + x1)
    elif face == "west":
        u = (bz * 16 + z0, bz * 16 + z1)
    else:
        u = (n - (bz * 16 + z1), n - (bz * 16 + z0))
    scale = 16 / n
    return [round(u[0] * scale, 4), round(v[0] * scale, 4), round(u[1] * scale, 4), round(v[1] * scale, 4)]


def outer_faces(edge, b, lo, hi, outer):
    faces = {}
    for face in outer:
        faces[face] = {"uv": big_uv(face, edge, b, lo, hi), "texture": "#side", "cullface": face}
    return faces


def part_model(edge, b):
    bx, by, bz = b
    last = edge - 1
    outer = [f for f, on in (("north", bz == 0), ("south", bz == last), ("west", bx == 0), ("east", bx == last)) if on]
    elements = []
    if by < last:
        faces = outer_faces(edge, b, (0, 0, 0), (16, 16, 16), outer)
        if by == 0:
            faces["down"] = {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}
        if faces:
            elements.append({"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces})
        return elements

    body = outer_faces(edge, b, (0, 0, 0), (16, 12, 16), outer)
    body["up"] = {"uv": [0, 0, 16, 16], "texture": "#bed"}
    elements.append({"from": [0, 0, 0], "to": [16, 12, 16], "faces": body})

    def rim(lo, hi, own, inner):
        faces = outer_faces(edge, b, lo, hi, own)
        x0, _, z0 = lo
        x1, _, z1 = hi
        faces["up"] = {"uv": [x0, z0, x1, z1], "texture": "#rim", "cullface": "up"}
        span = (x1 - x0) if inner in ("north", "south") else (z1 - z0)
        faces[inner] = {"uv": [0, 0, span, 4], "texture": "#rim"}
        elements.append({"from": list(lo), "to": list(hi), "faces": faces})

    if bz == 0:
        rim((0, 12, 0), (16, 16, 3), ["north"] + [f for f in ("west", "east") if f in outer], "south")
    if bz == last:
        rim((0, 12, 13), (16, 16, 16), ["south"] + [f for f in ("west", "east") if f in outer], "north")
    z_lo = 3 if bz == 0 else 0
    z_hi = 13 if bz == last else 16
    if bx == 0:
        rim((0, 12, z_lo), (3, 16, z_hi), ["west"], "east")
    if bx == last:
        rim((13, 12, z_lo), (16, 16, z_hi), ["east"], "west")
    return elements


def parts():
    for y in range(2):
        for z in range(2):
            for x in range(2):
                yield SMALL_FIRST + x + 2 * z + 4 * y, 2, (x, y, z)
    for y in range(3):
        for z in range(3):
            for x in range(3):
                if (x, y, z) == (1, 1, 1):
                    continue
                yield LARGE_FIRST + x + 3 * z + 9 * y, 3, (x, y, z)


def suffix(lined, lit):
    return ("_lined" if lined else "") + ("_lit" if lit else "")


def main():
    tex_dir = ASSETS / "textures/block/forge"
    model_dir = ASSETS / "models/block/forge"
    tex_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)
    for old in model_dir.glob("part_*.json"):
        old.unlink()

    for edge, name in ((2, "small"), (3, "large")):
        for lined in (False, True):
            for lit in (False, True):
                side_texture(edge, lined, lit).save(tex_dir / f"{name}_side{suffix(lined, lit)}.png")

    known = {}
    for part, edge, b in parts():
        known[part] = True
        name = "small" if edge == 2 else "large"
        (model_dir / f"part_{part}_shape.json").write_text(json.dumps({
            "parent": "minecraft:block/block",
            "__comment": f"Generated by tools/forge_multiblock.py: block {b} of the {name} forge.",
            "elements": part_model(edge, b),
        }, indent=2) + "\n", encoding="utf-8")
        for lined in (False, True):
            for lit in (False, True):
                brick = "minecraft:block/mud_bricks" if lined else "minecraft:block/bricks"
                (model_dir / f"part_{part}{suffix(lined, lit)}.json").write_text(json.dumps({
                    "parent": f"hardwrought:block/forge/part_{part}_shape",
                    "textures": {
                        "side": f"hardwrought:block/forge/{name}_side{suffix(lined, lit)}",
                        "rim": brick,
                        "bed": "minecraft:block/magma" if lit else "minecraft:block/coal_block",
                        "bottom": "minecraft:block/cobblestone",
                        "particle": brick,
                    },
                }, indent=2) + "\n", encoding="utf-8")

    variants = {}
    for lined in (False, True):
        for lit in (False, True):
            for part in range(MAX_PART + 1):
                key = f"lined={str(lined).lower()},lit={str(lit).lower()},part={part}"
                if part in known:
                    model = f"hardwrought:block/forge/part_{part}{suffix(lined, lit)}"
                else:
                    model = f"hardwrought:block/forge{suffix(lined, lit)}"
                variants[key] = {"model": model}
    (ASSETS / "blockstates/forge.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n",
                                                   encoding="utf-8")
    print(f"{len(known)} parts, {len(variants)} states")


if __name__ == "__main__":
    main()
