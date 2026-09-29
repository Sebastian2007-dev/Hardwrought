"""Generates the item pipes of the five metals from vanilla textures: models, blockstates, item models, loot
tables and recipes.

An item pipe is a core with an arm toward every side it is joined on, like the gas pipe but wider.
Each tier uses a different vanilla material. Dark coupling rings distinguish item pipes from the
smaller copper gas pipe. The item is a straight length, open at both ends.

Run from the repository root:  python tools/item_pipe_assets.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
ASSETS = ROOT / "assets/hardwrought"
DATA = ROOT / "data/hardwrought"
TIERS = ("bronze", "iron", "nickel", "chromium", "titanium")
VANILLA_TEXTURES = {
    "bronze": "minecraft:block/cut_copper",
    "iron": "minecraft:block/iron_block",
    "nickel": "minecraft:block/smooth_stone",
    "chromium": "minecraft:block/quartz_block_top",
    "titanium": "minecraft:block/light_gray_concrete",
}
FACES = ("north", "south", "west", "east", "up", "down")
LO, HI = 4, 12
ARMS = {
    "north": ((LO, LO, 0), (HI, HI, LO)), "south": ((LO, LO, HI), (HI, HI, 16)),
    "west": ((0, LO, LO), (LO, HI, HI)), "east": ((HI, LO, LO), (16, HI, HI)),
    "down": ((LO, 0, LO), (HI, LO, HI)), "up": ((LO, HI, LO), (HI, 16, HI)),
}


def uv(face, lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return {
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
    }[face]


def box(lo, hi, faces=FACES, textures=None):
    textures = textures or {}
    return {"from": list(lo), "to": list(hi),
            "faces": {f: {"uv": uv(f, lo, hi), "texture": textures.get(f, "#pipe")} for f in faces}}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def template_models():
    folder = ASSETS / "models/block/item_pipe"
    write(folder / "core.json", {"parent": "minecraft:block/block",
                                  "elements": [box((3.5, 3.5, 3.5), (12.5, 12.5, 12.5), textures={f: "#trim" for f in FACES})]})
    for name, (lo, hi) in ARMS.items():
        # The arm's open end touches the next block: no face there, and none where it meets the core.
        faces = [f for f in FACES if f not in (name, opposite(name))]
        collar_lo = [3, 3, 3]
        collar_hi = [13, 13, 13]
        axis = {"west": 0, "east": 0, "down": 1, "up": 1, "north": 2, "south": 2}[name]
        if name in ("north", "west", "down"):
            collar_lo[axis], collar_hi[axis] = 0.75, 2.25
        else:
            collar_lo[axis], collar_hi[axis] = 13.75, 15.25
        collar_faces = [f for f in FACES if f not in (name, opposite(name))]
        write(folder / f"arm_{name}.json", {"parent": "minecraft:block/block", "elements": [
            box(lo, hi, faces),
            box(tuple(collar_lo), tuple(collar_hi), collar_faces,
                textures={f: "#trim" for f in collar_faces})]})
    write(folder / "item.json", {
        "parent": "minecraft:block/block",
        "display": {"gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}},
        "elements": [
            box((LO, LO, 0), (HI, HI, 16), textures={"north": "#end", "south": "#end"}),
            box((3, 3, 0.75), (13, 13, 2.25), ("west", "east", "up", "down"),
                textures={f: "#trim" for f in ("west", "east", "up", "down")}),
            box((3, 3, 13.75), (13, 13, 15.25), ("west", "east", "up", "down"),
                textures={f: "#trim" for f in ("west", "east", "up", "down")})]})


def opposite(face):
    return {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}[face]


def tier_assets(metal):
    name = f"item_pipe_{metal}"
    texture = VANILLA_TEXTURES[metal]
    parts = ["core", *(f"arm_{face}" for face in ARMS), "item"]
    for part in parts:
        write(ASSETS / f"models/block/{name}/{part}.json", {
            "parent": f"hardwrought:block/item_pipe/{part}",
            "textures": {"pipe": texture, "trim": "minecraft:block/polished_blackstone",
                         "end": "minecraft:block/black_concrete", "particle": texture}})
    multipart = [{"apply": {"model": f"hardwrought:block/{name}/core"}}]
    for face in ARMS:
        multipart.append({"when": {face: "true"}, "apply": {"model": f"hardwrought:block/{name}/arm_{face}"}})
    write(ASSETS / f"blockstates/{name}.json", {"multipart": multipart})
    write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"hardwrought:block/{name}/item"}})
    write(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"hardwrought:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write(DATA / f"recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"P": f"hardwrought:{metal}_plate"},
        "pattern": ["PPP"], "result": {"id": f"hardwrought:{name}", "count": 8}})


if __name__ == "__main__":
    template_models()
    for metal in TIERS:
        tier_assets(metal)
