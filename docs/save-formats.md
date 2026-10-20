# What Hardwrought writes into a world

Once 1.0.0 is out, everything below is a promise: a later version has to keep loading worlds that hold
it. The rules that keep that promise:

- Add new codec fields only as `optionalFieldOf(..., default)`; never rename or remove a saved key.
- Never rename a registry id (block, item, entity, fluid, effect, enchantment, dimension, data component,
  attachment, block entity type). Knowledge, inventories and chunks store ids.
- Never rename or remove an enum value that is saved by name, and only ever append to an enum saved by
  ordinal.
- Never narrow a ranged codec (`intRange`, `floatRange`): an item already holding a value outside the new
  range will not load.
- Where a format has to change, version it and convert on load, as the forge, the atlas and the smeltery
  already do (see "Upgrades already in place").

## Most fragile: saved by ordinal or by constant name

| Where | Key | Stored as |
|---|---|---|
| `magic/RuneKnowledge` (player attachment `known_runes`) | bitmask | bit = `Glyph.ordinal()` — append-only |
| `oil/DrillingRigBlockEntity` | `stage` | `Stage.ordinal()` |
| `smeltery/SmelteryControllerBlockEntity` | `fuel` | `Fuel.ordinal()` |
| `magic/SigilBlockEntity` | `shape` | `SigilDesign.Shape.ordinal()` |
| `ritual/RitualAltar` | `theme` | `Temple.Theme.ordinal()` |
| `magic/Scrolls` (component `scroll`) | `judged[].outcome` | `CastQuality.Outcome.ordinal()` |
| `magic/SigilBlockEntity` | `program.target`, `program.conditions` | enum `name()`; a renamed constant throws on load |
| `health/Disease` (in the `health` attachment) | disease names | `valueOf(name)`; a renamed or removed disease throws |

Plain ints whose meaning lives in code: `WitchKingThrone.state`, `WitchKing.phase`, `GasolineEngineBlock.plug`,
`CastingTableBlockEntity.setting`, `ClimateBlockEntity.setting`, `RitualPedestal.index`, `Atlas.Marker.kind/icon`.
Keys built from list positions: `ProcessorBlock output_<i>`, `RefineryStageBlock cut_<i>`,
`DryingRackBlockEntity progress_<slot>`; and every `ContainerHelper` inventory depends on its slot order.

## Saved data

- `core/save/CoreSaveData` — id `hardwrought:core`: `schema_version` (validated as exactly 1), `simulation_ticks`,
  `player_vitals`, `environment_cells`, `aquifers`, `knowledge`, `equipment`, `reservoirs`. Bumping
  `schema_version` makes an older mod version refuse the file.
- `atlas/Atlas.Data` — id `hardwrought:atlas`, per dimension: per player `chunks`, `tiles` (raw bytes, 16 map
  colours per chunk), `markers`, `drawing`.

## Block entities

About sixty `saveAdditional` implementations. The richest: the forge (`layout_version` 2, `burn`, `temperature`,
`multiblock_controller`, `multiblock_size`, items), sigils (`element`, `program`, `links`, `power`, `owner`, …),
every electric node (`wires[{to, gauge, hot}]`, `volts`, `amps`), the smeltery controller (`fluids[{material,
amount}]`, `temperature`, `fuel`, `tank_min`, `tank_max`), shared cells (`host`, `insert`: full block states),
gas stores, process tanks and water treatment. A renamed key resets that value to its default.

## Entities and attachments

- Entities: `witch_king` (`phase`, `home`, `crystals`, `enraged`, `throne`), `grave_walker` and
  `grave_skeleton` (`rising`, `rising_floor`), `seat_mount`; vanilla End crystals get `hardwrought_ward`.
- Player attachments: `health`, `known_runes`, `sleep_debt`, `stuffed`, `adrenaline`, `home_spawn`,
  `left_behind`, `soulbound`, `underworld_descent`, `underworld_return`.
- Chunk attachments (keyed by packed block positions): `built_blocks`, `anchored_blocks`, `structural_stress`,
  `partial_water`, `carrier_levels`, `frozen_water`, `cell_water_quality`, `cauldron_water_quality`,
  `river_current`, `burning_lights`. Entity: `creaking_wood`.

## Item data components

`water_quality`, `water_amount` (ranged), `heat`, `forge_quality`, `runes`, `forging_state`, `belt_start`,
`purity` and `rust` (ranged), `charge`, `cell_wear`, `cell`, `lining`, `working_part`, `switched_on`,
`rots_at`, `drawing`, `soul_charge`, `soul_charged`, `throne_link`, `sigil_link`, `wire_start`,
`fluid_ingredient`, `scroll`.

## Player data

Vitals (stamina, hydration, nutrition, fatigue, body temperature, wetness, stress, height and depth habit),
knowledge (`discovered`, `studied`, `bookmarks` — item ids) and equipment (`backpack`, `lamp`, `gloves`) live
in `CoreSaveData`; the rest in the player attachments above.

## Registry ids and block states

Roughly 280 blocks and 750 items, 4 saved entity types, 8 fluids, 22 effects, the enchantments `soulbound`
and `temperance`, and the dimension `hardwrought:underworld` (a level folder of its own). Enum block-state
values saved by name: `SaltPanBlock.contents`, `BurntLanternBlock.variant`, `FluidDisplayBlock.fluid`, the
six sides of `ConnectedPipeBlock`, `RuneStoneBlock.glyph`, `MagicTableBlock.wood/part`, the `wood` of the
hewn and nailed workbenches, `TreeHeartBlock.wood`. Renaming a value resets those blocks to their default.

Game rule: `hardwrought:ultra`.

## Upgrades already in place

- `ForgeBlockEntity.layout_version`: 0→1 moves the item slots; below 2 drops the old three-high link.
- `Atlas.Page.drawing`: pages of roofed dimensions drawn before `CAVE_DRAWING` are drawn again.
- `SmelteryControllerBlockEntity`: falls back from `fuel` to the old boolean `hot_fuel`.
- `CoreSaveData`: `equipment` and `reservoirs` are optional; players of older worlds get a starter pack
  (outside Ultra).
- `PlayerVitals`: `stress`, `height_habit`, `depth_habit` optional; `nutrition` defaults for calorie-era saves.
- `PlayerEquipment.gloves`, `RuneWork.turns/ink`, `Scrolls.Line.ms`, `Judged.worst` optional.
