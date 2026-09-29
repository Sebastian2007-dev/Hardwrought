"""Writes crude oil as a liquid: its still and flowing textures, animated, and its blockstate.

Oil is near black with a brown depth to it. A slow, glossy sheen drifts over the still surface, with
now and then the thin rainbow a film of oil throws; the flowing texture runs the same streaks down
its length, the way vanilla's flow textures scroll.

Run from the repository root:  python tools/oil_fluid_assets.py
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/hardwrought"
TEXTURES = ASSETS / "textures/block"

DEEP = (14, 10, 8)
BODY = (28, 21, 15)
GLOSS = (74, 62, 50)
# The colours of a film of oil in the light, faint.
FILM = [(70, 48, 92), (40, 78, 88), (96, 84, 40)]


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def wave(x, y, phase, size):
    """Smooth, tiling ripples: sums of sines whose periods divide the tile."""
    tau = 2 * math.pi
    return (math.sin(tau * (x / size + phase)) * 0.5
            + math.sin(tau * ((x + y) / size - phase)) * 0.3
            + math.sin(tau * (2 * y / size + 2 * phase)) * 0.35
            + math.sin(tau * ((2 * x - y) / size + 3 * phase)) * 0.25)


def pixel(x, y, phase, size):
    v = wave(x, y, phase, size)
    colour = mix(DEEP, BODY, 0.5 + v * 0.6)
    if v > 0.75:
        colour = mix(colour, GLOSS, (v - 0.75) * 2.2)
    # A thin sheen band where the ripples cross, tinted by where on the tile it falls.
    film = wave(y, x, -phase * 2, size)
    if 0.95 < film < 1.05:
        colour = mix(colour, FILM[int(x + y) // 5 % len(FILM)], 0.35)
    return colour + (255,)


def strip(size, frames, flowing):
    img = Image.new("RGBA", (size, size * frames))
    for f in range(frames):
        phase = f / frames
        for y in range(size):
            for x in range(size):
                # The flowing texture moves its pattern down the tile, one full tile per loop.
                sy = (y - phase * size) % size if flowing else y
                img.putpixel((x, f * size + y), pixel(x, sy, 0 if flowing else phase, size))
    return img


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    strip(16, 32, False).save(TEXTURES / "crude_oil_still.png")
    write(TEXTURES / "crude_oil_still.png.mcmeta", {"animation": {"frametime": 4, "interpolate": True}})
    strip(32, 32, True).save(TEXTURES / "crude_oil_flow.png")
    write(TEXTURES / "crude_oil_flow.png.mcmeta", {"animation": {"frametime": 3}})
    write(ASSETS / "models/block/crude_oil.json",
          {"textures": {"particle": "hardwrought:block/crude_oil_still"}})
    write(ASSETS / "blockstates/crude_oil.json", {"variants": {"": {"model": "hardwrought:block/crude_oil"}}})
    print("crude oil written")


if __name__ == "__main__":
    main()
