"""Rebuild the older Parts and Circuits icons in a restrained Vanilla style.

The original 16x16 sprites live in art_source/vanilla_style_items/base. The
familiar Minecraft silhouettes remain the source of truth; only conspicuously
flat placeholders are redrawn here. Run from the repository root with Pillow.
"""

from pathlib import Path
import colorsys

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "art_source/vanilla_style_items/base"
OUT = ROOT / "src/main/resources/assets/hardwrought/textures/item"
NAMES = """redstone_coil electric_motor spark_plug glow_plug engine_piston crankshaft fuel_injector bearing
rubber_gasket silicone_gasket circuit_board resistor capacitor transistor microchip quartz_oscillator
basic_circuit advanced_circuit elite_circuit electrical_steel electrical_steel_plate transformer_core
enamelled_wire winding porcelain_blank porcelain fuse_cartridge insulated_wire""".split()

# These silhouettes are already direct Vanilla relatives: leave their original
# material and shading intact instead of forcing an unrelated palette on them.
VANILLA_RELATIVES = set()


def blank():
    return Image.new("RGBA", (16, 16))


def normalize(image):
    """Mute bright procedural colours and shade existing edges in four pixel tones."""
    old = image.convert("RGBA")
    result = blank()
    for y in range(16):
        for x in range(16):
            r, g, b, a = old.getpixel((x, y))
            if a < 128:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            s *= 0.83
            luminance = (r + g + b) / 3
            top = (x == 0 or old.getpixel((x - 1, y))[3] < 128 or
                   y == 0 or old.getpixel((x, y - 1))[3] < 128)
            bottom = (x == 15 or old.getpixel((x + 1, y))[3] < 128 or
                      y == 15 or old.getpixel((x, y + 1))[3] < 128)
            if luminance > 55:
                v = min(1, v * (1.09 if top else 0.86 if bottom else 1.0))
            rgb = colorsys.hsv_to_rgb(h, s, v)
            result.putpixel((x, y), tuple(round(c * 255) for c in rgb) + (255,))
    return result


def redstone_coil():
    image = blank()
    d = ImageDraw.Draw(image)
    d.ellipse((3, 2, 12, 13), fill="#303135", outline="#181a1c")
    d.rectangle((4, 4, 11, 11), fill="#813e2d")
    for y in (4, 6, 8, 10):
        d.line((4, y, 10, y), fill="#c16d3e")
        d.point((5, y), fill="#dc8953")
    d.line((3, 3, 3, 12), fill="#616368")
    d.line((12, 4, 12, 11), fill="#242326")
    d.rectangle((6, 1, 8, 2), fill="#ad3e39")
    d.rectangle((6, 13, 8, 14), fill="#633039")
    return image


def gasket(color):
    image = blank()
    d = ImageDraw.Draw(image)
    dark, mid, light = color
    d.ellipse((1, 1, 14, 14), fill=dark)
    d.ellipse((2, 2, 13, 13), fill=mid)
    d.arc((2, 2, 13, 13), 195, 320, fill=light, width=1)
    d.ellipse((5, 5, 10, 10), fill=(0, 0, 0, 0))
    # ImageDraw transparent fill cuts the centre out for Minecraft's item layer.
    return image


def circuit(tier):
    image = blank()
    d = ImageDraw.Draw(image)
    dark, mid, bright = ("#263529", "#426042", "#779261")
    d.polygon([(1, 6), (10, 2), (14, 5), (5, 10)], fill=dark)
    d.polygon([(2, 6), (10, 3), (13, 5), (5, 9)], fill=mid)
    d.line([(1, 6), (5, 10), (14, 5)], fill="#222922")
    d.line([(4, 6), (7, 5), (9, 6)], fill="#a8864e")
    d.point((6, 7), fill=bright)
    if tier >= 1:
        d.rectangle((8, 4, 10, 6), fill="#252a2b")
        d.point((8, 4), fill="#7b8481")
        d.line([(3, 8), (5, 7), (6, 8)], fill="#b9995f")
    if tier >= 2:
        d.rectangle((4, 5, 6, 7), fill="#293437")
        d.point((4, 5), fill="#768a8b")
        d.line([(8, 7), (10, 8), (12, 6)], fill="#c6a15b")
    if tier == 3:
        d.line([(2, 5), (10, 2)], fill="#8ba979")
        d.point((11, 4), fill="#d2b66c")
    return image


def transformer_core():
    image = blank()
    d = ImageDraw.Draw(image)
    d.polygon([(2, 3), (12, 3), (14, 5), (14, 13), (12, 14), (2, 14), (1, 12), (1, 5)], fill="#374147")
    d.rectangle((3, 3, 12, 12), fill="#687c86")
    d.line((3, 3, 12, 3), fill="#a7b3b6")
    d.line((3, 12, 12, 12), fill="#2c3439")
    d.rectangle((6, 6, 9, 9), fill=(0, 0, 0, 0))
    for x in (3, 4, 11, 12):
        d.point((x, 5), fill="#4b5b64")
        d.point((x, 10), fill="#4b5b64")
    return image


def winding():
    image = blank()
    d = ImageDraw.Draw(image)
    d.rectangle((3, 3, 12, 12), fill="#313b41")
    d.rectangle((4, 4, 11, 11), fill="#8c482f")
    for y in (4, 6, 8, 10):
        d.line((4, y, 11, y), fill="#c57748")
        d.line((4, y + 1, 11, y + 1), fill="#643627")
    d.line((3, 3, 12, 3), fill="#879098")
    d.line((3, 12, 12, 12), fill="#202529")
    return image


def porcelain(fired):
    image = blank()
    d = ImageDraw.Draw(image)
    dark = "#77746d" if not fired else "#a6a9a5"
    mid = "#b5ad99" if not fired else "#e0dfd2"
    light = "#d7cdb8" if not fired else "#f8f5e8"
    d.rectangle((6, 2, 9, 13), fill=dark)
    for y, width in ((4, 3), (8, 4), (12, 3)):
        d.ellipse((8 - width, y - 1, 7 + width, y + 2), fill=dark)
        d.line((8 - width, y - 1, 6 + width, y - 1), fill=light)
        d.line((8 - width, y, 7 + width, y), fill=mid)
    d.point((7, 3), fill=light)
    return image


def bitumen():
    image = blank()
    d = ImageDraw.Draw(image)
    d.polygon([(2, 10), (4, 5), (8, 3), (12, 5), (14, 10), (11, 13), (4, 13)], fill="#171819")
    d.polygon([(4, 6), (8, 4), (11, 6), (9, 8), (5, 9)], fill="#353031")
    d.line((5, 6, 8, 5), fill="#62504a")
    d.line((4, 12, 10, 12), fill="#100f10")
    return image


def crude_oil_bucket():
    # Keep the Vanilla bucket silhouette, but expose the contents at its lip.
    image = Image.open(BASE / "crude_oil_bucket.png").convert("RGBA")
    d = ImageDraw.Draw(image)
    d.ellipse((4, 3, 11, 6), fill="#27211d", outline="#151413")
    d.line((5, 4, 8, 4), fill="#57443a")
    return image


def plastic_pellets():
    image = blank()
    d = ImageDraw.Draw(image)
    for x, y in ((3, 9), (6, 6), (10, 7), (8, 11), (12, 11), (5, 12)):
        d.ellipse((x, y, x + 2, y + 2), fill="#c8c7bb")
        d.point((x, y), fill="#f0eee1")
        d.point((x + 2, y + 2), fill="#8e938e")
    return image


def plastic_sheet():
    image = blank()
    d = ImageDraw.Draw(image)
    d.polygon([(2, 7), (11, 3), (14, 5), (5, 11)], fill="#848e90")
    d.polygon([(2, 6), (11, 2), (14, 4), (5, 10)], fill="#d6d9d4")
    d.line([(2, 6), (11, 2), (14, 4)], fill="#f4f1e5")
    d.line((5, 10, 14, 4), fill="#a6afb0")
    return image


def rubber():
    image = blank()
    d = ImageDraw.Draw(image)
    d.polygon([(2, 6), (10, 3), (14, 5), (14, 10), (6, 13), (2, 11)], fill="#17191a")
    d.polygon([(2, 6), (10, 3), (14, 5), (6, 9)], fill="#34383a")
    d.line((3, 6, 10, 4), fill="#55595a")
    d.line((6, 10, 13, 7), fill="#202427")
    return image


def chemical_pile(caustic):
    image = blank()
    d = ImageDraw.Draw(image)
    dark = "#aeb4b8" if caustic else "#868c8d"
    mid = "#d5d8d6" if caustic else "#acaeaa"
    light = "#f4f3eb" if caustic else "#d5d2c8"
    d.polygon([(2, 12), (4, 9), (6, 9), (7, 5), (9, 7), (11, 8), (14, 12)], fill=dark)
    d.polygon([(3, 11), (6, 8), (8, 8), (10, 10), (13, 11)], fill=mid)
    for x, y in ((6, 8), (7, 6), (10, 9), (4, 10)):
        d.point((x, y), fill=light)
    return image


OVERRIDES = {
    "redstone_coil": redstone_coil,
    "rubber_gasket": lambda: gasket(("#171a1b", "#373c3b", "#5a605d")),
    "silicone_gasket": lambda: gasket(("#656d6d", "#b9bfba", "#e2e2d7")),
    "circuit_board": lambda: circuit(0),
    "basic_circuit": lambda: circuit(1),
    "advanced_circuit": lambda: circuit(2),
    "elite_circuit": lambda: circuit(3),
    "transformer_core": transformer_core,
    "winding": winding,
    "porcelain_blank": lambda: porcelain(False),
    "porcelain": lambda: porcelain(True),
}


def main():
    for name in NAMES:
        image = Image.open(BASE / f"{name}.png").convert("RGBA")
        result = (OVERRIDES[name]() if name in OVERRIDES else
                  image if name in VANILLA_RELATIVES else normalize(image))
        result.save(OUT / f"{name}.png")
    sheet = Image.new("RGB", (9 * 80, ((len(NAMES) + 8) // 9) * 88), (123, 123, 123))
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(NAMES):
        image = Image.open(OUT / f"{name}.png").convert("RGBA")
        x, y = (index % 9) * 80, (index // 9) * 88
        tile = image.resize((64, 64), Image.Resampling.NEAREST)
        sheet.paste(tile, (x + 8, y), tile)
        draw.text((x + 2, y + 65), name[:12], fill=(255, 255, 255))
    sheet.save(ROOT / "art_source/vanilla_style_items/preview.png")
    print(f"Processed {len(NAMES)} Vanilla-style item sprites ({len(OVERRIDES)} redrawn)")


if __name__ == "__main__":
    main()
