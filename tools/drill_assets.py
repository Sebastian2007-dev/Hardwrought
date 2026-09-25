"""Writes the look of the ore drill: the drill head, and the five drill frames.

A frame is a lattice of struts with open gaps between them, so a finished drill reads as a truss round
its head and not as a box. Each frame takes its three shades from the ingot it is made of, so the tier
can be told from across a quarry.

Run from the repository root:  python tools/drill_assets.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/hardwrought"
VANILLA = Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar"
FRAMES = {"bronze": "hardwrought:bronze_ingot", "iron": "minecraft:iron_ingot", "nickel": "hardwrought:nickel_ingot",
          "chromium": "hardwrought:chromium_ingot", "titanium": "hardwrought:titanium_ingot"}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def cuboid(lo, hi, texture):
    return {"from": lo, "to": hi,
            "faces": {face: {"texture": texture}
                      for face in ("north", "south", "east", "west", "up", "down")}}


def ingot(identifier):
    namespace, name = identifier.split(":")
    if namespace == "hardwrought":
        return Image.open(ASSETS / f"textures/item/{name}.png").convert("RGBA")
    import io
    import zipfile
    with zipfile.ZipFile(VANILLA) as jar:
        return Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/item/{name}.png"))).convert("RGBA")


def shades(image):
    """Dark, middle and light of an ingot: the quartiles of its opaque pixels by brightness."""
    pixels = sorted((p for p in image.getdata() if p[3] > 0), key=lambda p: p[0] + p[1] + p[2])
    pick = lambda share: pixels[min(len(pixels) - 1, int(len(pixels) * share))][:3]
    return pick(0.15), pick(0.5), pick(0.9)


def frame(dark, mid, light):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            inner = x in (1, 14) or y in (1, 14)
            diagonal = abs(x - y) <= 1 or abs(x - (15 - y)) <= 1
            if edge:
                colour = dark
            elif inner:
                colour = light if x == 1 or y == 1 else mid
            elif diagonal:
                colour = light if x < y else mid
            else:
                continue
            img.putpixel((x, y), colour + (255,))
    # Rivets where the struts cross the rim.
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 7), (8, 8)):
        img.putpixel((x, y), dark + (255,))
    return img


def main():
    for name, source in FRAMES.items():
        frame(*shades(ingot(source))).save(ASSETS / f"textures/block/drill_frame_{name}.png")
        block = f"drill_frame_{name}"
        write(ASSETS / f"models/block/{block}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"hardwrought:block/{block}"}})
        write(ASSETS / f"blockstates/{block}.json", {"variants": {"": {"model": f"hardwrought:block/{block}"}}})
        write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"hardwrought:block/{block}"}})

    write(ASSETS / "models/block/ore_drill.json", {
        "parent": "minecraft:block/block",
        "textures": {"brace": "hardwrought:block/hardwrought/iron_brace",
                     "steel": "hardwrought:block/hardwrought/drill_steel",
                     "particle": "hardwrought:block/hardwrought/drill_steel"},
        "elements": [cuboid([1, 13, 1], [15, 16, 15], "#brace"),
                     cuboid([3, 7, 3], [13, 13, 13], "#steel"),
                     cuboid([4, 5, 4], [12, 7, 12], "#brace"),
                     cuboid([6, 0, 6], [10, 5, 10], "#steel")],
    })
    write(ASSETS / "blockstates/ore_drill.json", {"variants": {
        "running=false": {"model": "hardwrought:block/ore_drill"},
        "running=true": {"model": "hardwrought:block/ore_drill"}}})
    write(ASSETS / "items/ore_drill.json", {"model": {"type": "minecraft:model", "model": "hardwrought:block/ore_drill"}})
    print("ore drill assets written")
    # The blockstates for the finished drill are written over the plain ones above.
    import ore_drill_formed
    ore_drill_formed.main()


if __name__ == "__main__":
    main()
