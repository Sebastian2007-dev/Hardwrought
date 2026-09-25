"""Gives every piece of forge metal a glowing look for while it is hot.

For each item below it writes a pale, luminance-only copy of the item's texture, a model for it, and
rewrites the item definition to switch to that model - tinted by the hardwrought:heat tint source -
while the hardwrought:hot condition holds. The tint does the colour: the same pale copy reads dull red
at forging's cold end and yellow-white fresh out of the fire.

Safe to run again: an item that already switches is unwrapped first. Run from the project root after
adding a metal or a part:

    python tools/glow_textures.py
"""
import io
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets")
MOD = "hardwrought"
VANILLA_JAR_DIR = os.path.join(ROOT, ".gradle/loom-cache/minecraftMaven/net/minecraft")

VANILLA_ITEMS = ["raw_iron", "raw_copper", "raw_gold", "iron_ingot", "copper_ingot", "gold_ingot"]
MOD_METALS = ["tin", "zinc", "lead", "manganese", "magnesium", "aluminum", "nickel", "cobalt", "chromium",
              "mercury", "titanium", "tungsten", "uranium", "thorium", "platinum"]
PARTS = {
    "iron": ["pickaxe_head", "axe_head", "shovel_head", "hoe_head", "sword_blade", "dagger_blade",
             "greatsword_blade", "halberd_head", "hammer_head"],
    "gold": ["pickaxe_head", "axe_head", "shovel_head", "hoe_head", "sword_blade"],
    "copper": ["pickaxe_head", "axe_head", "shovel_head", "hoe_head", "sword_blade"],
    "bronze": ["pickaxe_head", "axe_head"],
}


def mod_items():
    items = ["bronze_ingot"]
    for metal in MOD_METALS:
        items += [f"raw_{metal}", f"{metal}_ingot"]
    for metal, parts in PARTS.items():
        items += [f"{metal}_{part}" for part in parts]
    return items


def vanilla_jar():
    for base, _, files in os.walk(VANILLA_JAR_DIR):
        for name in files:
            if name.startswith("minecraft-clientOnly") and name.endswith(".jar"):
                return zipfile.ZipFile(os.path.join(base, name))
    raise SystemExit("Minecraft client jar not found; run a Gradle build first")


def read_json(namespace, path, jar):
    local = os.path.join(ASSETS, namespace, path)
    if os.path.exists(local):
        with open(local, encoding="utf-8") as file:
            return json.load(file)
    return json.loads(jar.read(f"assets/{namespace}/{path}"))


def read_image(namespace, path, jar):
    local = os.path.join(ASSETS, namespace, path)
    if os.path.exists(local):
        return Image.open(local).convert("RGBA")
    return Image.open(io.BytesIO(jar.read(f"assets/{namespace}/{path}"))).convert("RGBA")


def split(identifier, default="minecraft"):
    return identifier.split(":", 1) if ":" in identifier else (default, identifier)


def glow(image):
    """Keeps the shape and the shading, drops the colour, and lifts it all towards white."""
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            if a == 0:
                continue
            luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            value = int(255 * (0.55 + 0.45 * luminance))
            out.putpixel((x, y), (value, value, value, a))
    return out


def process(namespace, name, jar):
    definition = read_json(namespace, f"items/{name}.json", jar)
    model = definition["model"]
    if model.get("type") == "minecraft:condition" and model.get("property") == f"{MOD}:hot":
        model = model["on_false"]
    model_ns, model_path = split(model["model"], namespace)
    model_json = read_json(model_ns, f"models/{model_path}.json", jar)
    texture_ns, texture_path = split(model_json["textures"]["layer0"], model_ns)
    image = read_image(texture_ns, f"textures/{texture_path}.png", jar)

    glow_name = f"{namespace}_{name}"
    os.makedirs(os.path.join(ASSETS, MOD, "textures/item/glow"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, MOD, "models/item/glow"), exist_ok=True)
    glow(image).save(os.path.join(ASSETS, MOD, f"textures/item/glow/{glow_name}.png"))
    with open(os.path.join(ASSETS, MOD, f"models/item/glow/{glow_name}.json"), "w", encoding="utf-8") as file:
        json.dump({"parent": model_json.get("parent", "minecraft:item/generated"),
                   "textures": {"layer0": f"{MOD}:item/glow/{glow_name}"}}, file, indent=2)

    definition["model"] = {
        "type": "minecraft:condition",
        "property": f"{MOD}:hot",
        "on_true": {"type": "minecraft:model", "model": f"{MOD}:item/glow/{glow_name}",
                    "tints": [{"type": f"{MOD}:heat"}]},
        "on_false": model,
    }
    target = os.path.join(ASSETS, namespace, "items")
    os.makedirs(target, exist_ok=True)
    with open(os.path.join(target, f"{name}.json"), "w", encoding="utf-8") as file:
        json.dump(definition, file, indent=2)


def main():
    jar = vanilla_jar()
    for name in VANILLA_ITEMS:
        process("minecraft", name, jar)
    for name in mod_items():
        process(MOD, name, jar)
    print(f"glow looks written for {len(VANILLA_ITEMS) + len(mod_items())} items")


if __name__ == "__main__":
    main()
