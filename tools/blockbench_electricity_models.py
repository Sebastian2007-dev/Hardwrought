"""Build editable Blockbench projects for the electricity assets.

The Minecraft model JSON remains the source of truth.  This exporter mirrors the
geometry and embeds a copy of every 16x16 texture so the resulting projects can
be opened away from the development checkout.
"""

from __future__ import annotations

import argparse
import base64
import json
import shutil
import struct
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/hardwrought"
BLOCK_MODELS = ASSETS / "models/block"
BLOCK_TEXTURES = ASSETS / "textures/block/hardwrought"
ITEM_TEXTURES = ASSETS / "textures/item"

BLOCK_PROJECTS = (
    "dynamo",
    "insulator",
    "electric_lamp_off",
    "electric_lamp_dim",
    "electric_lamp_on",
    "electric_lamp_broken",
    "basic_crusher",
    "electric_furnace",
    "sawmill",
    "plate_press",
    "battery_0",
)
ITEM_PROJECTS = ("copper_wire", "copper_cable", "voltmeter")


def stable_uuid(key: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"hardwrought:blockbench:electricity:{key}"))


def png_size(path: Path) -> tuple[int, int]:
    raw = path.read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Not a PNG: {path}")
    return struct.unpack(">II", raw[16:24])


def texture_record(path: Path, texture_id: int, relative_path: str) -> dict:
    width, height = png_size(path)
    encoded = base64.b64encode(path.read_bytes()).decode("ascii")
    return {
        "name": path.name,
        "relative_path": relative_path.replace("\\", "/"),
        "folder": "",
        "namespace": "",
        "id": str(texture_id),
        "group": "",
        "scope": 0,
        "width": width,
        "height": height,
        "uv_width": width,
        "uv_height": height,
        "particle": False,
        "use_as_default": texture_id == 0,
        "layers_enabled": False,
        "sync_to_project": "",
        "file_format": "png",
        "render_mode": "default",
        "render_sides": "auto",
        "wrap_mode": "limited",
        "pbr_channel": "color",
        "fps": 7,
        "frame_time": 1,
        "frame_order_type": "loop",
        "frame_order": "",
        "frame_interpolate": False,
        "visible": True,
        "internal": True,
        "saved": True,
        "uuid": stable_uuid(f"texture:{path.name}"),
        "source": f"data:image/png;base64,{encoded}",
    }


def face_uv(face: str, start: list[float], end: list[float]) -> list[float]:
    x, y, z = (end[i] - start[i] for i in range(3))
    if face in ("north", "south"):
        return [0, 0, x, y]
    if face in ("east", "west"):
        return [0, 0, z, y]
    return [0, 0, x, z]


def common_project(name: str, model_format: str) -> dict:
    project = {
        "meta": {"format_version": "5.0", "model_format": model_format, "box_uv": False},
        "name": name,
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "multi_file_ruleset": "",
        "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "ai_used": True,
        "ai_agents": "codex",
        "resolution": {"width": 16, "height": 16},
        "elements": [],
        "groups": [],
        "outliner": [],
        "textures": [],
    }
    if model_format == "java_block":
        project.update({
            "parent": "",
            "java_block_version": "26.3",
            "ambientocclusion": True,
            "front_gui_light": False,
        })
    return project


def material_name(reference: str) -> str:
    return reference.lstrip("#").replace("_", " ").title()


def build_block_project(name: str, output: Path) -> None:
    source = json.loads((BLOCK_MODELS / f"{name}.json").read_text(encoding="utf-8"))
    texture_keys = [key for key in source["textures"] if key != "particle"]
    texture_indices = {key: index for index, key in enumerate(texture_keys)}
    project = common_project(name, "java_block")

    texture_dir = output / "textures" / "electricity"
    texture_dir.mkdir(parents=True, exist_ok=True)
    for index, key in enumerate(texture_keys):
        asset = source["textures"][key].split("/")[-1]
        src = BLOCK_TEXTURES / f"{asset}.png"
        dst = texture_dir / src.name
        shutil.copy2(src, dst)
        project["textures"].append(
            texture_record(dst, index, f"textures/electricity/{dst.name}")
        )

    groups: dict[str, dict] = {}
    for element_index, raw in enumerate(source["elements"]):
        start, end = raw["from"], raw["to"]
        first_reference = next(iter(raw["faces"].values()))["texture"]
        material = first_reference.lstrip("#")
        group = groups.setdefault(material, {
            "name": material_name(material),
            "uuid": stable_uuid(f"{name}:group:{material}"),
            "export": True,
            "locked": False,
            "scope": 0,
            "selected": False,
            "visibility": True,
            "_static": {"properties": {}, "temp_data": {}},
            "origin": [8, 8, 8],
            "rotation": [0, 0, 0],
            "color": len(groups) % 8,
            "children": [],
            "reset": False,
            "shade": True,
            "mirror_uv": False,
            "autouv": 1,
            "isOpen": True,
            "primary_selected": False,
        })
        element_uuid = stable_uuid(f"{name}:element:{element_index}")
        faces = {}
        for face_name, face in raw["faces"].items():
            texture_key = face["texture"].lstrip("#")
            converted = {
                "uv": face.get("uv", face_uv(face_name, start, end)),
                "texture": texture_indices[texture_key],
            }
            if "rotation" in face:
                converted["rotation"] = face["rotation"]
            if "tintindex" in face:
                converted["tint"] = face["tintindex"]
            faces[face_name] = converted
        element = {
            "name": f"{material_name(material)} {element_index + 1}",
            "box_uv": False,
            "render_order": "default",
            "rescale": False,
            "locked": False,
            "shade_direction_override": "",
            "light_emission": 0,
            "export": True,
            "scope": 0,
            "allow_mirror_modeling": True,
            "from": start,
            "to": end,
            "autouv": 1,
            "color": len(groups) % 8,
            "origin": [(start[i] + end[i]) / 2 for i in range(3)],
            "faces": faces,
            "type": "cube",
            "uuid": element_uuid,
        }
        if "rotation" in raw:
            rotation = raw["rotation"]
            axis = rotation["axis"]
            element["rotation"] = [rotation["angle"] if axis == a else 0 for a in ("x", "y", "z")]
            element["origin"] = rotation["origin"]
            element["rescale"] = rotation.get("rescale", False)
        project["elements"].append(element)
        group["children"].append(element_uuid)

    project["groups"] = list(groups.values())
    project["outliner"] = [
        {"uuid": group["uuid"], "isOpen": True, "children": group["children"]}
        for group in project["groups"]
    ]
    (output / f"{name}.bbmodel").write_text(
        json.dumps(project, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )


def build_item_project(name: str, output: Path) -> None:
    src = ITEM_TEXTURES / f"{name}.png"
    texture_dir = output / "textures" / "electricity"
    texture_dir.mkdir(parents=True, exist_ok=True)
    dst = texture_dir / src.name
    shutil.copy2(src, dst)
    project = common_project(name, "image")
    project["textures"] = [texture_record(dst, 0, f"textures/electricity/{dst.name}")]
    (output / f"{name}.bbmodel").write_text(
        json.dumps(project, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path, nargs="?", default=ROOT / "build/blockbench-electricity")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for name in BLOCK_PROJECTS:
        build_block_project(name, args.output)
    for name in ITEM_PROJECTS:
        build_item_project(name, args.output)
    print(f"Wrote {len(BLOCK_PROJECTS) + len(ITEM_PROJECTS)} projects to {args.output.resolve()}")


if __name__ == "__main__":
    main()
