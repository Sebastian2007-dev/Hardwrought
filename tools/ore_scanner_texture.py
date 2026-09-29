"""Draws the creative ore scanner: a handheld box with a green screen showing a seam, and an aerial.

Run from the repository root:  python tools/ore_scanner_texture.py
"""
import json
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/hardwrought"

ART = [
    "............#...",
    "...........#o#..",
    "............#...",
    "............#...",
    "...##########...",
    "..#LLLLLLLLLL#..",
    "..#LSSSSSSSSM#..",
    "..#LSgggggGgM#..",
    "..#LSggGGgggM#..",
    "..#LSgGggggGM#..",
    "..#LSSSSSSSSM#..",
    "..#LMMrMMbMMM#..",
    "..#LMMMMMMMMM#..",
    "..#DDDDDDDDDD#..",
    "...##########...",
    "................",
]
COLOURS = {
    "#": (28, 28, 32), "L": (120, 124, 132), "M": (84, 88, 96), "D": (54, 56, 62),
    "S": (20, 34, 24), "g": (38, 92, 50), "G": (120, 230, 120), "o": (230, 60, 50),
    "r": (200, 60, 50), "b": (70, 120, 210),
}


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ART):
        for x, c in enumerate(row):
            if c in COLOURS:
                img.putpixel((x, y), COLOURS[c] + (255,))
    img.save(ASSETS / "textures/item/ore_scanner.png")
    (ASSETS / "models/item/ore_scanner.json").write_text(json.dumps(
        {"parent": "minecraft:item/handheld", "textures": {"layer0": "hardwrought:item/ore_scanner"}}, indent=2) + "\n")
    (ASSETS / "items/ore_scanner.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": "hardwrought:item/ore_scanner"}}, indent=2) + "\n")
    print("ore scanner written")


if __name__ == "__main__":
    main()
