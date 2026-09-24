"""Item sprites for the weaving chain: pointed stick, wooden and iron sewing needle, woven cloth.

The stick keeps vanilla's own stick and loses its upper end to a point. The needles are a thin
diagonal with an eye near the top and a loop of cord through it. The cloth is a folded, frayed swatch
of dense leaf-fibre weave: still green, but dry and muted enough to read as fabric instead of leaves.

Run from the repository root:  python tools/weaving_textures.py
"""
import zipfile
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/hardwrought/textures/item"
JAR = Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar"


def vanilla_item(name):
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(BytesIO(jar.read(f"assets/minecraft/textures/item/{name}.png"))).convert("RGBA")


def leaf_greens():
    cord = Image.open(OUT / "leaf_string.png").convert("RGBA")
    colours = sorted({pixel[:3] for pixel in cord.getdata() if pixel[3] > 0}, key=sum)
    return colours[0], colours[len(colours) // 2], colours[-1]


def pointed_stick():
    stick = vanilla_item("stick")
    px = stick.load()
    # Vanilla's stick runs from lower left to upper right; the top three pixels of it become a
    # pale, freshly cut point.
    top = [(x, y) for y in range(16) for x in range(16) if px[x, y][3] > 0]
    top.sort(key=lambda p: p[1])
    for x, y in top[:2]:
        px[x, y] = (0, 0, 0, 0)
    for x, y in top[2:5]:
        r, g, b, a = px[x, y]
        px[x, y] = (min(255, r + 60), min(255, g + 50), min(255, b + 30), a)
    return stick


def needle(shaft, light, eye_colour, cord):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for i in range(3, 14):
        x, y = i, 16 - i
        px[x, y] = shaft
        if i < 12:
            px[x + 1, y] = light
    px[12, 3] = (0, 0, 0, 0)
    px[13, 3] = eye_colour
    px[12, 4] = eye_colour
    for x, y in [(14, 2), (14, 3), (13, 5), (12, 6), (11, 7), (12, 8), (13, 8)]:
        px[x, y] = cord
    return image


def woven():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    outline = (24, 43, 21, 255)
    dark = (39, 64, 27, 255)
    mid = (67, 91, 36, 255)
    light = (108, 124, 54, 255)
    straw = (151, 146, 72, 255)

    # A deliberately uneven folded swatch, wider than it is tall so it no longer reads as a net.
    rows = {
        3: (5, 9), 4: (3, 12), 5: (2, 13), 6: (2, 14), 7: (1, 14),
        8: (1, 14), 9: (1, 13), 10: (2, 13), 11: (3, 12), 12: (5, 11),
    }
    for y, (left, right) in rows.items():
        for x in range(left, right + 1):
            edge = x in (left, right) or y in (3, 12)
            if edge:
                colour = outline
            else:
                # One-pixel plain weave survives inventory scaling and reads as crossed fibres.
                colour = light if (x + y) % 2 == 0 else mid
                if y in (6, 10) and x % 2 == 0:
                    colour = dark
            px[x, y] = colour

    # A folded corner gives the flat material volume and a different weaving direction.
    fold_rows = {4: (9, 12), 5: (8, 13), 6: (8, 14), 7: (9, 14), 8: (10, 13)}
    for y, (left, right) in fold_rows.items():
        for x in range(left, right + 1):
            if x in (left, right) or y == 4:
                px[x, y] = outline
            else:
                px[x, y] = straw if (x + y) % 2 == 0 else light
    for x, y in ((9, 8), (10, 9), (11, 9), (12, 9)):
        px[x, y] = dark

    # Short, sparse loose fibres; long threads turn into visual noise in a 16-pixel slot.
    for x, y, colour in ((4, 3, light), (4, 2, dark), (2, 5, mid), (1, 5, dark),
                         (2, 11, mid), (1, 12, dark), (11, 12, light), (12, 13, mid)):
        px[x, y] = colour
    return image


def main():
    dark, mid, light = leaf_greens()
    pointed_stick().save(OUT / "pointed_stick.png")
    needle((126, 94, 52, 255), (176, 138, 84, 255), (70, 50, 28, 255), mid + (255,)).save(OUT / "sewing_needle.png")
    needle((96, 100, 106, 255), (206, 210, 214, 255), (50, 52, 56, 255), mid + (255,)).save(OUT / "iron_sewing_needle.png")
    cloth = woven()
    cloth.save(OUT / "woven.png")
    source = ROOT / "art_source/weaving"
    source.mkdir(parents=True, exist_ok=True)
    cloth.resize((256, 256), Image.Resampling.NEAREST).save(source / "woven_cloth_pixel_preview.png")
    print("done")


if __name__ == "__main__":
    main()
