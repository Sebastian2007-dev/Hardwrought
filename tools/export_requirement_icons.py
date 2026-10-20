"""Export the approved image sources as native Minecraft item/effect sprites.

Run from the repository root: python tools/export_requirement_icons.py
Requires Pillow. Source artwork stays in art_source for later revisions.
"""

from pathlib import Path
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/hardwrought/textures"
SOURCES = {
    "fireclay": ("smithing/fireclay_source.png", "item/fireclay.png", 16),
    "refractory_brick": ("smithing/refractory_brick_source.png", "item/refractory_brick.png", 16),
    "stator": ("motors/stator_source.png", "item/stator.png", 16),
    "rotor": ("motors/rotor_source.png", "item/rotor.png", 16),
    "commutator": ("motors/commutator_source.png", "item/commutator.png", 16),
    "graphite_brush": ("motors/graphite_brush_source.png", "item/graphite_brush.png", 16),
    "motor_housing": ("motors/motor_housing_source.png", "item/motor_housing.png", 16),
    "motor_controller": ("motors/motor_controller_source.png", "item/motor_controller.png", 16),
    "gripper_arm": ("electricity/gripper_arm_source.png", "item/gripper_arm.png", 16),
    "spark_gap": ("electricity/spark_gap_source.png", "item/spark_gap.png", 16),
    "tungsten_filament": ("electricity/tungsten_filament_source.png", "item/tungsten_filament.png", 16),
    "nichrome_wire": ("electricity/nichrome_wire_source.png", "item/nichrome_wire.png", 16),
    "glass_bulb": ("electricity/glass_bulb_source.png", "item/glass_bulb.png", 16),
    "glass_tube": ("electricity/glass_tube_source.png", "item/glass_tube.png", 16),
    "lamp_socket": ("electricity/lamp_socket_source.png", "item/lamp_socket.png", 16),
    "light_bulb": ("electricity/light_bulb_source.png", "item/light_bulb.png", 16),
    "reflector": ("electricity/reflector_source.png", "item/reflector.png", 16),
    "ballast": ("electricity/ballast_source.png", "item/ballast.png", 16),
    "arc_electrode": ("electricity/arc_electrode_source.png", "item/arc_electrode.png", 16),
    "motion_sensor": ("electricity/motion_sensor_source.png", "item/motion_sensor.png", 16),
    "relay": ("electricity/relay_source.png", "item/relay.png", 16),
    "heating_element": ("electricity/heating_element_source.png", "item/heating_element.png", 16),
    "bimetal_strip": ("electricity/bimetal_strip_source.png", "item/bimetal_strip.png", 16),
    "thermostat": ("electricity/thermostat_source.png", "item/thermostat.png", 16),
    "fan_blades": ("electricity/fan_blades_source.png", "item/fan_blades.png", 16),
    "compressor": ("electricity/compressor_source.png", "item/compressor.png", 16),
    "refrigerant": ("electricity/refrigerant_source.png", "item/refrigerant.png", 16),
    "cooling_coil": ("electricity/cooling_coil_source.png", "item/cooling_coil.png", 16),
    "insulated_panel": ("electricity/insulated_panel_source.png", "item/insulated_panel.png", 16),
    "pressure_vessel": ("electricity/pressure_vessel_source.png", "item/pressure_vessel.png", 16),
    "pressure_gauge": ("electricity/pressure_gauge_source.png", "item/pressure_gauge.png", 16),
    "lead_acid_pack": ("electricity/lead_acid_pack_source.png", "item/lead_acid_pack.png", 16),
    "nickel_iron_cell": ("electricity/nickel_iron_cell_source.png", "item/nickel_iron_cell.png", 16),
    "silver_zinc_cell": ("electricity/silver_zinc_cell_source.png", "item/silver_zinc_cell.png", 16),
    "heated_lining": ("electricity/heated_lining_source.png", "item/heated_lining.png", 16),
    "cooled_lining": ("electricity/cooled_lining_source.png", "item/cooled_lining.png", 16),
    "steel_drill_bit": ("electricity/steel_drill_bit_source.png", "item/steel_drill_bit.png", 16),
    "tungsten_steel_drill_bit": ("electricity/tungsten_steel_drill_bit_source.png", "item/tungsten_steel_drill_bit.png", 16),
    "saw_chain": ("electricity/saw_chain_source.png", "item/saw_chain.png", 16),
    "battery_acid": ("electricity/battery_acid_source.png", "item/battery_acid.png", 16),
    "potash": ("electricity/potash_source.png", "item/potash.png", 16),
    "cell_case": ("electricity/cell_case_source.png", "item/cell_case.png", 16),
    "toggle_switch": ("electricity/toggle_switch_source.png", "item/toggle_switch.png", 16),
    "device_housing": ("electricity/device_housing_source.png", "item/device_housing.png", 16),
    "reduction_gear": ("electricity/reduction_gear_source.png", "item/reduction_gear.png", 16),
    "chuck": ("electricity/chuck_source.png", "item/chuck.png", 16),
    "saw_bar": ("electricity/saw_bar_source.png", "item/saw_bar.png", 16),
    "strap": ("electricity/strap_source.png", "item/strap.png", 16),
    "nichrome_mesh": ("electricity/nichrome_mesh_source.png", "item/nichrome_mesh.png", 16),
    "thermoelectric_pad": ("electricity/thermoelectric_pad_source.png", "item/thermoelectric_pad.png", 16),
    "regulator": ("electricity/regulator_source.png", "item/regulator.png", 16),
    "image_tube": ("electricity/image_tube_source.png", "item/image_tube.png", 16),
    "pulse_coil": ("electricity/pulse_coil_source.png", "item/pulse_coil.png", 16),
    "exo_frame": ("electricity/exo_frame_source.png", "item/exo_frame.png", 16),
    "joint_actuator": ("electricity/joint_actuator_source.png", "item/joint_actuator.png", 16),
    "ashcork_bark": ("ashcork/ashcork_bark_source.png", "item/ashcork_bark.png", 16),
    "nichrome_ingot": ("electricity/nichrome_ingot_source.png", "item/nichrome_ingot.png", 16),
    "nichrome_rod": ("electricity/nichrome_rod_source.png", "item/nichrome_rod.png", 16),
    "nichrome_plate": ("electricity/nichrome_plate_source.png", "item/nichrome_plate.png", 16),
    "blackstone_piece": ("ashcork/blackstone_piece_source.png", "item/blackstone_piece.png", 16),
}

def export(source: Path, destination: Path, size: int) -> None:
    original = Image.open(source).convert("RGBA")
    alpha = original.getchannel("A")
    bounds = alpha.point(lambda value: 255 if value >= 96 else 0).getbbox()
    if bounds is None:
        raise ValueError(f"No visible pixels in {source}")
    cropped = original.crop(bounds)
    # Leave a one-pixel clear margin, including for artwork that touched its
    # source canvas edge. BOX sampling keeps the large source's forms legible.
    extent = size - 2
    width, height = cropped.size
    scale = min(extent / width, extent / height)
    reduced = cropped.resize(
        (max(1, round(width * scale)), max(1, round(height * scale))),
        Image.Resampling.BOX,
    )
    visible = reduced.getchannel("A").point(lambda value: 255 if value >= 128 else 0)
    reduced = reduced.quantize(colors=24, method=Image.Quantize.FASTOCTREE).convert("RGBA")
    reduced.putalpha(visible)
    result = Image.new("RGBA", (size, size))
    result.alpha_composite(reduced, ((size - reduced.width) // 2, (size - reduced.height) // 2))
    destination.parent.mkdir(parents=True, exist_ok=True)
    result.save(destination)
    print(f"{destination.relative_to(ROOT)}: {size}x{size}")


if __name__ == "__main__":
    for source_name, destination_name, size in SOURCES.values():
        export(ROOT / "art_source" / source_name, ASSETS / destination_name, size)
