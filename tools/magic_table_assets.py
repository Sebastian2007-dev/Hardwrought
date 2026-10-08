import base64
import json
import sys
from pathlib import Path


WOODS = {
    "oak": "oak_log",
    "spruce": "spruce_log",
    "birch": "birch_log",
    "jungle": "jungle_log",
    "acacia": "acacia_log",
    "dark_oak": "dark_oak_log",
    "pale_oak": "pale_oak_log",
    "poplar": "poplar_log",
    "mangrove": "mangrove_log",
    "cherry": "cherry_log",
    "bamboo": "bamboo_block",
    "crimson": "crimson_stem",
    "warped": "warped_stem",
}

TEXTURE_KEYS = {
    "mage_table_arcane_glow.png": "rune",
    "minecraft_dark_oak_planks.png": "wood",
    "minecraft_oak_planks.png": "planks",
    "minecraft_iron_block.png": "metal",
    "minecraft_gold_block.png": "gold",
    "minecraft_obsidian.png": "obsidian",
    "minecraft_amethyst_block.png": "crystal",
    "minecraft_paper.png": "pages",
    "minecraft_feather.png": "pages",
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def clean_number(value):
    rounded = round(value, 4)
    return int(rounded) if rounded == int(rounded) else rounded


def transform_point(point, x_offset):
    return [clean_number(point[0] - x_offset), clean_number(max(0, min(16, point[1]))),
            clean_number(point[2] + 8)]


def split_element(element, low, high, texture_names):
    source_from = list(element["from"])
    source_to = list(element["to"])
    if element["name"] == "Drawer_Front":
        source_to[2] = -7.1
    if element["name"].startswith("Foot_"):
        source_from[1], source_to[1] = 0, 1
    if source_to[0] <= low or source_from[0] >= high:
        return None
    clipped_from = [max(low, source_from[0]), source_from[1], source_from[2]]
    clipped_to = [min(high, source_to[0]), source_to[1], source_to[2]]
    result = {
        "name": element["name"],
        "from": transform_point(clipped_from, low),
        "to": transform_point(clipped_to, low),
        "faces": {},
    }
    for direction, face in element.get("faces", {}).items():
        texture_index = face.get("texture")
        if not isinstance(texture_index, int) or texture_index >= len(texture_names):
            continue
        key = TEXTURE_KEYS.get(texture_names[texture_index], "wood")
        result["faces"][direction] = {"uv": [0, 0, 16, 16], "texture": "#" + key}
    rotation = element.get("rotation")
    if rotation and any(abs(value) > 0.001 for value in rotation):
        axis_index = next((i for i, value in enumerate(rotation) if abs(value) > 0.001), None)
        if axis_index is not None:
            result["rotation"] = {
                "origin": transform_point(element.get("origin", [0, 8, 0]), low),
                "axis": "xyz"[axis_index],
                "angle": clean_number(rotation[axis_index]),
                "rescale": False,
            }
    return result


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: magic_table_assets.py <bbmodel> <resources>")
    source_path = Path(sys.argv[1])
    resources = Path(sys.argv[2])
    project = json.loads(source_path.read_text(encoding="utf-8"))
    texture_names = [texture["name"] for texture in project["textures"]]

    defaults = {
        "particle": "#planks",
        "rune": "hardwrought:block/magic_table_arcane_glow",
        "wood": "minecraft:block/oak_log",
        "planks": "minecraft:block/oak_planks",
        "metal": "minecraft:block/iron_block",
        "gold": "minecraft:block/gold_block",
        "obsidian": "minecraft:block/obsidian",
        "crystal": "minecraft:block/amethyst_block",
        "pages": "minecraft:block/sandstone_top",
    }
    model_dir = resources / "assets/hardwrought/models/block"
    for part, low, high in (("left", -16, 0), ("right", 0, 16)):
        elements = []
        for element in project["elements"]:
            converted = split_element(element, low, high, texture_names)
            if converted is not None:
                elements.append(converted)
        write_json(model_dir / f"magic_table_{part}_base.json", {
            "parent": "minecraft:block/block",
            "ambientocclusion": True,
            "textures": defaults,
            "elements": elements,
        })

    for wood, log in WOODS.items():
        for part in ("left", "right"):
            write_json(model_dir / f"magic_table_{part}_{wood}.json", {
                "parent": f"hardwrought:block/magic_table_{part}_base",
                "textures": {
                    "wood": f"minecraft:block/{log}",
                    "planks": f"minecraft:block/{wood}_planks",
                    "particle": f"minecraft:block/{wood}_planks",
                },
            })

    variants = {}
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    for wood in WOODS:
        for part in ("left", "right"):
            for facing, rotation in rotations.items():
                entry = {"model": f"hardwrought:block/magic_table_{part}_{wood}"}
                if rotation:
                    entry["y"] = rotation
                variants[f"facing={facing},part={part},wood={wood}"] = entry
    write_json(resources / "assets/hardwrought/blockstates/magic_table.json", {"variants": variants})

    glow = next(texture for texture in project["textures"] if texture["name"] == "mage_table_arcane_glow.png")
    encoded = glow["source"].split(",", 1)[1]
    texture_path = resources / "assets/hardwrought/textures/block/magic_table_arcane_glow.png"
    texture_path.parent.mkdir(parents=True, exist_ok=True)
    texture_path.write_bytes(base64.b64decode(encoded))


if __name__ == "__main__":
    main()
