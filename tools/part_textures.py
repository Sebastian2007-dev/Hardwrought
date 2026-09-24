"""Export forged parts from the real Vanilla tool silhouettes.

The five Vanilla tool parts keep their original orientation, shading and metal pixels. Only the
wooden handle is omitted. The three Hardwrought-only weapons use matching hand-authored masks.

Run from the project root: python tools/part_textures.py
"""
from pathlib import Path
import json

from PIL import Image


ASSETS = Path("src/main/resources/assets/hardwrought")
VANILLA = Path("art_source/smithing/vanilla_reference/assets/minecraft/textures/item")

PARTS = {
    "iron": ["pickaxe_head", "axe_head", "shovel_head", "hoe_head", "sword_blade",
             "dagger_blade", "greatsword_blade", "halberd_head", "hammer_head"],
    "gold": ["pickaxe_head", "axe_head", "shovel_head", "hoe_head", "sword_blade"],
    "bronze": ["pickaxe_head", "axe_head"],
}

VANILLA_TOOL = {
    "pickaxe_head": "pickaxe",
    "axe_head": "axe",
    "shovel_head": "shovel",
    "hoe_head": "hoe",
    "sword_blade": "sword",
}

# Exact selections in the current Vanilla 16x16 tools. These retain the original diagonal pose and
# asymmetrical profile instead of redrawing a pickaxe head as a centred chevron.
VANILLA_MASKS = {
    "pickaxe_head": [
        "................", "................", "......#####.....", ".....#######....",
        "......######....", "...........###..", "............###.", "............###.",
        "............###.", "............###.", "............###.", ".............#..",
        "................", "................", "................", "................",
    ],
    "axe_head": [
        "................", ".........##.....", "........####....", ".......#####....",
        "......#####.....", "......######....", ".......##.####..", "...........###..",
        "...........##...", "................", "................", "................",
        "................", "................", "................", "................",
    ],
    "shovel_head": [
        "................", "................", "...........###..", "..........#####.",
        ".........######.", "........#######.", "..........####..", "...........##...",
        "...........#....", "................", "................", "................",
        "................", "................", "................", "................",
    ],
    "hoe_head": [
        "................", ".......###......", "......#####.....", ".......#####....",
        ".........###.#..", "...........###..", "...........##...", "................",
        "................", "................", "................", "................",
        "................", "................", "................", "................",
    ],
    "sword_blade": [
        ".............###", "............####", "...........#####", "..........#####.",
        ".........#####..", "........#####...", ".......#####....", "......#####.....",
        ".....#####......", "....#####.......", "...#####........", "...###..........",
        "..##............", "................", "................", "................",
    ],
}

CUSTOM_MASKS = {
    # The head of the stone hammer, without its handle.
    "hammer_head": [
        "..........##....", ".........####...", "........######..", ".......########.",
        "........########", ".........#######", "...........#####", "...........####.",
        "............##..", "................", "................", "................",
        "................", "................", "................", "................",
    ],
    "dagger_blade": [
        "................", "................", "................", ".............##.",
        "............###.", "...........####.", "..........####..", ".........####...",
        "........####....", ".......####.....", "......###.......", ".....##.........",
        "................", "................", "................", "................",
    ],
    "greatsword_blade": [
        ".............###", "............####", "...........#####", "..........#####.",
        ".........#####..", "........#####...", ".......#####....", "......#####.....",
        ".....#####......", "....#####.......", "...#####........", "..#####.........",
        ".####...........", ".###............", "..#.............", "................",
    ],
    "halberd_head": [
        "............#...", "...........###..", "..........####..", ".........####...",
        ".....########...", "....#########...", "...########.....", "....#####.......",
        "......####......", ".......###......", ".......##.......", "................",
        "................", "................", "................", "................",
    ],
}

PALETTES = {
    "iron": [(54, 58, 63), (139, 145, 150), (191, 197, 200), (238, 241, 241)],
    "gold": [(112, 72, 13), (211, 151, 26), (249, 211, 58), (255, 245, 157)],
    "bronze": [(84, 43, 26), (145, 78, 43), (202, 124, 69), (238, 171, 102)],
}


def selected(mask, x, y):
    return mask[y][x] == "#"


def recolour(pixel, metal):
    """Map Vanilla iron shading to another material without flattening its highlights."""
    red, green, blue, alpha = pixel
    if not alpha:
        return pixel
    brightness = (red + green + blue) / 3
    index = 0 if brightness < 80 else 1 if brightness < 175 else 2 if brightness < 230 else 3
    return PALETTES[metal][index] + (alpha,)


def vanilla_part(metal, part):
    source_metal = "golden" if metal == "gold" else "iron"
    source = Image.open(VANILLA / f"{source_metal}_{VANILLA_TOOL[part]}.png").convert("RGBA")
    output = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if selected(VANILLA_MASKS[part], x, y):
                pixel = source.getpixel((x, y))
                # The short tang occupies the first two former grip pixels and must remain metal.
                # Every other Vanilla part can retain its source pixel verbatim.
                if metal == "bronze" or (part == "sword_blade" and y >= 11):
                    pixel = recolour(pixel, metal)
                output.putpixel((x, y), pixel)
    return output


def custom_part(part):
    mask = CUSTOM_MASKS[part]
    palette = PALETTES["iron"]
    output = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if not selected(mask, x, y):
                continue
            # Bright upper-left edge, mid-tone body, dark lower-right outline.
            neighbours = ((x - 1, y), (x, y - 1))
            edge = any(nx < 0 or ny < 0 or not selected(mask, nx, ny) for nx, ny in neighbours)
            lower_edge = x == 15 or y == 15 or not selected(mask, min(x + 1, 15), y) or not selected(mask, x, min(y + 1, 15))
            colour = palette[2] if edge else palette[0] if lower_edge else palette[1]
            if edge and (x + y) % 3 == 0:
                colour = palette[3]
            output.putpixel((x, y), colour + (255,))
    return output


def centre_in_slot(image):
    """Move the visible part into the middle of its 16x16 inventory canvas."""
    bounds = image.getbbox()
    if bounds is None:
        return image
    left, top, right, bottom = bounds
    cropped = image.crop(bounds)
    target_x = (16 - (right - left)) // 2
    target_y = (16 - (bottom - top)) // 2
    centred = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    centred.paste(cropped, (target_x, target_y), cropped)
    return centred


def write_definition(name):
    model_path = ASSETS / "models/item" / f"{name}.json"
    item_path = ASSETS / "items" / f"{name}.json"
    model_path.write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"hardwrought:item/{name}"},
    }, indent=2), encoding="utf-8")
    item_path.write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": f"hardwrought:item/{name}"},
    }, indent=2), encoding="utf-8")


def main():
    for metal, parts in PARTS.items():
        for part in parts:
            name = f"{metal}_{part}"
            image = vanilla_part(metal, part) if part in VANILLA_TOOL else custom_part(part)
            image = centre_in_slot(image)
            image.save(ASSETS / "textures/item" / f"{name}.png")
            write_definition(name)


if __name__ == "__main__":
    main()
