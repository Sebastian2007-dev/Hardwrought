"""Rebuild simple 16px device faces and non-component item placeholders.

These E2/E4-E6 resources were untracked when the painted texture pass began, so
their earlier bytes are unavailable. This keeps the original plain generator
look and the model references usable without the painted block source images.
"""

from pathlib import Path
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "src/main/resources/assets/hardwrought/textures"
BLOCK = ASSET / "block/hardwrought"
ITEM = ASSET / "item"

FACES = {
    "drive_housing": ((84, 105, 119), "ribs"),
    "drive_coil": ((105, 65, 48), "coil"),
    "motor_controller_front": ((65, 73, 79), "dial"),
    "motor_controller_side": ((68, 77, 83), "panel"),
    "assembly_bench_top": ((112, 108, 96), "bench"),
    "assembly_bench_side": ((88, 92, 89), "panel"),
    "charging_station_top": ((80, 85, 78), "slots"),
    "charging_station_front": ((73, 82, 76), "dial"),
    "charging_station_front_on": ((73, 82, 76), "lit_dial"),
    "conveyor_belt": ((48, 48, 49), "belt"),
    "conveyor_side": ((84, 91, 94), "panel"),
    "assembler_front": ((89, 94, 94), "panel"),
    "assembler_front_on": ((89, 94, 94), "lit_panel"),
    "resonant_coil": ((109, 67, 46), "coil"),
    "resonant_coil_end": ((86, 91, 92), "ring"),
    "top_load": ((175, 121, 69), "ring"),
    "capacitor_bank": ((79, 85, 83), "slots"),
    "tesla_tower_front": ((73, 76, 78), "coil"),
    "tesla_tower_front_on": ((73, 76, 78), "lit_coil"),
}


def face(name, colour, motif):
    rng = random.Random(name)
    image = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rng.choice((-9, -5, -2, 0, 2, 5, 8))
            image.putpixel((x, y), tuple(max(0, min(255, channel + n)) for channel in colour))
    draw = ImageDraw.Draw(image)
    dark, light, copper, glow = "#343a3c", "#c2c1b7", "#ae6c3d", "#e4c36c"
    draw.rectangle((0, 0, 15, 15), outline=dark)
    if motif in ("panel", "lit_panel", "bench"):
        draw.rectangle((2, 2, 13, 13), outline="#555f60")
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            draw.point((x, y), fill=light)
        if motif == "bench":
            draw.rectangle((4, 4, 10, 8), fill="#737874", outline=dark)
            draw.line((3, 11, 12, 11), fill=light)
        if motif == "lit_panel":
            draw.rectangle((5, 5, 10, 8), fill="#83ba73", outline=dark)
    elif motif in ("coil", "lit_coil"):
        for x in (3, 6, 9, 12):
            draw.line((x, 2, x, 13), fill=copper)
            draw.line((x + 1, 2, x + 1, 13), fill=dark)
        if motif == "lit_coil":
            draw.line((3, 7, 12, 7), fill=glow)
    elif motif == "ribs":
        for y in (3, 6, 9, 12):
            draw.line((2, y, 13, y), fill=dark)
            draw.line((2, y + 1, 13, y + 1), fill=light)
    elif motif in ("dial", "lit_dial"):
        draw.rectangle((3, 3, 12, 12), fill="#5c6666", outline=dark)
        draw.ellipse((5, 4, 10, 9), fill="#c9c9b5", outline=dark)
        draw.line((8, 7, 10, 5), fill=dark)
        draw.rectangle((5, 11, 10, 12), fill=glow if motif == "lit_dial" else copper)
    elif motif == "slots":
        for x in (3, 7, 11):
            draw.rectangle((x, 3, x + 1, 12), fill=dark)
            draw.point((x, 2), fill=light)
    elif motif == "belt":
        for y in (3, 7, 11):
            draw.line((2, y, 13, y), fill="#6e7070")
            draw.point((4, y), fill=dark)
            draw.point((11, y), fill=dark)
    elif motif == "ring":
        draw.ellipse((2, 2, 13, 13), fill=copper, outline=light)
        draw.ellipse((5, 5, 10, 10), fill=dark)
    image.save(BLOCK / f"{name}.png")


def item(name):
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    if name == "machine_oil":
        draw.polygon([(3, 11), (5, 5), (9, 4), (11, 6), (10, 12)], fill="#856033", outline="#3b352d")
        draw.line((8, 4, 12, 3, 13, 4), fill="#959a91", width=1)
    elif name == "service_kit":
        draw.rectangle((2, 6, 13, 12), fill="#8c4b36", outline="#3c3835")
        draw.rectangle((5, 3, 10, 6), outline="#9d9d91")
        draw.rectangle((6, 7, 9, 8), fill="#c2ae71")
    elif name == "drawing":
        draw.polygon([(3, 3), (12, 2), (13, 12), (4, 13)], fill="#dad7c7", outline="#87877f")
        draw.line((5, 6, 10, 5, 10, 10, 6, 10), fill="#5a6670")
    elif name == "sterile_dressing":
        draw.rectangle((3, 4, 12, 11), fill="#e5e5da", outline="#8e9a9e")
        draw.line((5, 6, 10, 6), fill="#a9b3b5")
        draw.line((5, 9, 10, 9), fill="#a9b3b5")
    image.save(ITEM / f"{name}.png")


if __name__ == "__main__":
    for name, (colour, motif) in FACES.items():
        face(name, colour, motif)
    for name in ("machine_oil", "service_kit", "drawing", "sterile_dressing"):
        item(name)
    print(f"Restored {len(FACES)} plain device faces and 4 item placeholders.")
