"""Writes the shape of the overworld: mountains that use the height of the world, and a deep that is
worth going down into.

Everything here is vanilla's own terrain with a few things changed, read out of the game's data and
patched, so that an update of the game's terrain is taken over by running this again:

- **The world may be as high as it is.** Vanilla lets the land thin out to nothing between 240 and
  256 and measures depth only up to 320. Both are moved to the top of this world (1024).
- **Mountain ranges.** One more, very slow noise says where the great ranges stand: massifs some
  kilometres across, days apart. There the land is told to be inland and unworn, so that vanilla
  builds its own mountains with its own peaks and biomes, and an uplift of up to some 700 blocks is
  added under them. Because the noise is slow, a range rises over kilometres and not as a wall.
- **The deep has caves of every kind.** Vanilla makes everything below -64 solid and leaves only its
  tunnel carvers to cut through it. The solid floor is moved down to the bottom of this world, so the
  cheese, spaghetti and noodle caves and their pillars reach all the way; and two layers of great
  caverns are added, one in the lower caverns and a wider one in the abyss.

The lava that used to fill every cave below -54 is a number in the game's code, not in its data; it
is moved to the bottom of the world in mixin/NoiseBasedChunkGeneratorLavaMixin (see worldgen/DeepWorld).

Safe to run again:

    python tools/worldgen_assets.py
"""
import json
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src/main/resources/data")
JAR_DIR = os.path.join(ROOT, ".gradle/loom-cache/minecraftMaven/net/minecraft")
MOD = "hardwrought"

# Kept in step with the dimension type, noise settings and worldgen/DeepWorld.
BOTTOM, TOP = -256, 1024
# The land thins out to nothing over the last blocks under the top, and is solid over the first above the bottom.
TOP_FADE = (TOP - 32, TOP)
FLOOR_FADE = (BOTTOM + 8, BOTTOM + 32)
# Vanilla's measure of depth falls by 3 over 384 blocks; carried on at that rate to the top.
DEPTH_PER_BLOCK = 3.0 / 384.0
# The most the great ranges add to the height of the land, in vanilla's units of 128 blocks.
UPLIFT = 5.8
# The slow noise from which a range begins, and how quickly it reaches its full height beyond that.
RANGE_FROM, RANGE_GAIN = 0.10, 1.67


def jar():
    for base, _, files in os.walk(JAR_DIR):
        for name in files:
            if name.startswith("minecraft-common") and name.endswith(".jar") and not name.endswith("-sources.jar"):
                return zipfile.ZipFile(os.path.join(base, name))
    raise SystemExit("Minecraft jar not found; run a Gradle build first")


JAR = jar()


def vanilla(name):
    return json.loads(JAR.read(f"data/minecraft/worldgen/density_function/{name}.json"))


def write(namespace, kind, name, data):
    path = os.path.join(DATA, namespace, "worldgen", kind, name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as file:
        json.dump(data, file, indent=2)
        file.write("\n")


def walk(node, visit):
    """Calls visit on every object in a density function, parents before children."""
    if isinstance(node, dict):
        visit(node)
        for value in node.values():
            walk(value, visit)
    elif isinstance(node, list):
        for value in node:
            walk(value, visit)


def gradient(from_y, from_value, to_y, to_value):
    return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": from_y, "from_value": from_value,
            "to_coordinate": to_y, "to_value": to_value}


def add(left, right):
    return {"type": "minecraft:add", "left": left, "right": right}


def mul(left, right):
    return {"type": "minecraft:mul", "left": left, "right": right}


def clamp(value, low, high):
    return {"type": "minecraft:clamp", "input": value, "min": low, "max": high}


def noise(name, xz, y):
    return {"type": "minecraft:noise", "noise": name, "xz_scale": xz, "y_scale": y}


def is_gradient(node, from_y, to_y):
    return node.get("type") == "minecraft:gradient" and node.get("from_coordinate") == from_y and node.get("to_coordinate") == to_y


def retarget(function, counts):
    """Moves vanilla's three fixed heights - the solid floor, the open top, the measure of depth - to this world's."""
    def visit(node):
        if is_gradient(node, -64, -40):
            node["from_coordinate"], node["to_coordinate"] = FLOOR_FADE
            counts["floor"] = counts.get("floor", 0) + 1
        elif is_gradient(node, 240, 256):
            node["from_coordinate"], node["to_coordinate"] = TOP_FADE
            counts["top"] = counts.get("top", 0) + 1
        elif is_gradient(node, -64, 320) and node.get("from_value") == 1.5:
            node["to_coordinate"] = TOP
            node["to_value"] = 1.5 - (TOP + 64) * DEPTH_PER_BLOCK
            counts["depth"] = counts.get("depth", 0) + 1
    walk(function, visit)
    return function


def band(centre, half):
    """One in the middle of a layer of the deep, falling to nought at its edges and nought outside it."""
    return add(1.0, mul(-1.0, {"type": "minecraft:abs", "input": gradient(centre - half, -1.0, centre + half, 1.0)}))


def main():
    counts = {}

    # ---- the height of the land
    write("minecraft", "density_function", "overworld/depth", retarget(vanilla("overworld/depth"), counts))

    offset = vanilla("overworld/offset")
    patched = []

    def lift(node):
        # The land's own height, before it is blended into old chunks: the uplift is added to that.
        if node.get("type") == "minecraft:lerp" and isinstance(node.get("alpha"), dict) \
                and node["alpha"].get("type") == "minecraft:blend_alpha":
            node["second"] = add(node["second"], f"{MOD}:overworld/mountain_uplift")
            patched.append(node)
    walk(offset, lift)
    if len(patched) != 1:
        raise SystemExit(f"vanilla's offset has changed shape: {len(patched)} places to add the uplift")
    write("minecraft", "density_function", "overworld/offset", offset)

    # A range is its own, very slow map: one in the heart of a great range, nought where there is none.
    # Nothing quick may go into it. Vanilla's own maps of the land change far too fast for this - the
    # one that says how far inland a place is can go from coast to deep inland in four hundred blocks -
    # and a height hung on them stands up as a wall. This one rises over kilometres.
    write(MOD, "noise", "mountain_range", {"base_octave": -12, "octave_count": 3, "base_amplitude": 1.0,
                                             "amplitude_modifiers": [1.0, 0.6, 0.3]})
    write(MOD, "density_function", "overworld/mountain_range", {"type": "minecraft:cache", "input":
          clamp(mul(add(noise(f"{MOD}:mountain_range", 1.0, 0.0), -RANGE_FROM), RANGE_GAIN), 0.0, 1.0)})
    rng = f"{MOD}:overworld/mountain_range"
    write(MOD, "density_function", "overworld/mountain_uplift", {"type": "minecraft:cache", "input":
          mul(UPLIFT, mul(rng, rng))})

    # Where a range stands, the land is told to be inland and unworn, so that vanilla builds its own
    # mountains there, with its own peaks and its own mountain biomes, and the uplift raises those.
    # The foothills of a range that stands in the sea come up out of it as an island.
    foot = clamp(mul(rng, 2.5), 0.0, 1.0)
    continents = vanilla("overworld/continents")
    erosion = vanilla("overworld/erosion")
    if continents.get("type") != "minecraft:cache" or erosion.get("type") != "minecraft:cache":
        raise SystemExit("vanilla's continents or erosion have changed shape")
    continents["input"] = {"type": "minecraft:max", "left": continents["input"], "right": add(-1.2, mul(1.75, foot))}
    erosion["input"] = {"type": "minecraft:min", "left": erosion["input"], "right": add(1.0, mul(-1.55, foot))}
    write("minecraft", "density_function", "overworld/continents", continents)
    write("minecraft", "density_function", "overworld/erosion", erosion)

    surface = retarget(vanilla("overworld/preliminary_surface_level"), counts)
    raised = []

    def bounds(node):
        # Where the search for the surface may begin: no longer capped at vanilla's highest land.
        if node.get("type") == "minecraft:clamp" and node.get("max") == 320.0 and node.get("min") == -40.0:
            node["max"] = float(TOP - 32)
            raised.append(node)
    walk(surface, bounds)
    if len(raised) != 1:
        raise SystemExit(f"vanilla's surface estimate has changed shape: {len(raised)} upper bounds")
    write("minecraft", "density_function", "overworld/preliminary_surface_level", surface)

    # ---- the deep
    final = retarget(vanilla("overworld/final_density"), counts)
    caves = []

    def deepen(node):
        # Inside the rock, where vanilla chooses between its kinds of cave: the caverns of the deep are one more.
        if node.get("type") == "minecraft:range_choice" and node.get("input") == "minecraft:overworld/sloped_cheese":
            inner = node["when_out_of_range"]
            if inner.get("type") != "minecraft:max":
                raise SystemExit("vanilla's caves have changed shape")
            inner["left"] = {"type": "minecraft:min", "left": inner["left"], "right": f"{MOD}:overworld/caves/deep_caverns"}
            caves.append(node)
    walk(final, deepen)
    if len(caves) != 1:
        raise SystemExit(f"vanilla's final density has changed shape: {len(caves)} places for caves")
    write("minecraft", "density_function", "overworld/final_density", final)

    # The lower caverns: halls some tens of blocks across, about a quarter of the rock at the middle of the layer.
    lower = add(add(noise(f"{MOD}:deep_cavern", 1.0, 1.6), 0.95), mul(-0.75, band(-140, 55)))
    # The abyss: one wide, low, broken hall over the lava at the bottom of the world, nearly half open.
    abyss = add(add(noise(f"{MOD}:abyss", 0.6, 1.2), 1.0), mul(-0.95, band(-215, 28)))
    write(MOD, "density_function", "overworld/caves/deep_caverns", {"type": "minecraft:min", "left": lower, "right": abyss})
    write(MOD, "noise", "deep_cavern", {"base_octave": -7, "octave_count": 3, "base_amplitude": 1.0,
                                          "amplitude_modifiers": [1.0, 1.0, 0.5]})
    write(MOD, "noise", "abyss", {"base_octave": -8, "octave_count": 3, "base_amplitude": 1.0,
                                    "amplitude_modifiers": [1.0, 1.0, 0.5]})

    # The thin winding tunnels stopped at -60; they go on to just above the floor.
    noodle = vanilla("overworld/caves/noodle")
    lowered = []

    def reach(node):
        if node.get("type") == "minecraft:range_choice" and node.get("input") == "minecraft:y" and node.get("min_inclusive") == -60.0:
            node["min_inclusive"] = float(BOTTOM + 12)
            node["max_exclusive"] = float(TOP)
            lowered.append(node)
    walk(noodle, reach)
    if not lowered:
        raise SystemExit("vanilla's noodle caves have changed shape")
    write("minecraft", "density_function", "overworld/caves/noodle", noodle)

    expected = {"floor": 2, "top": 2, "depth": 2}
    if counts != expected:
        raise SystemExit(f"vanilla's fixed heights are not where they were: {counts}, expected {expected}")
    print("worldgen written: land to", TOP, "| uplift", UPLIFT, "| floor fade", FLOOR_FADE, "|", len(lowered), "noodle ranges")


if __name__ == "__main__":
    main()
