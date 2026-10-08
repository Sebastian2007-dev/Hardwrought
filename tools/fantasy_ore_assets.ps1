param([string]$Root = (Get-Location).Path)

$assets = Join-Path $Root "src/main/resources/assets/hardwrought"
$data = Join-Path $Root "src/main/resources/data"

function Write-Utf8([string]$Path, [string]$Text) {
    New-Item -ItemType Directory -Force -Path (Split-Path $Path) | Out-Null
    [System.IO.File]::WriteAllText($Path, $Text.Replace("`r`n", "`n") + "`n",
        [System.Text.UTF8Encoding]::new($false))
}

function Copy-MetalAsset([string]$Source, [string]$Target, [string]$Metal) {
    $text = [System.IO.File]::ReadAllText($Source).Replace("platinum", $Metal)
    Write-Utf8 $Target $text.TrimEnd()
}

$metals = @(
    @{ id="silver"; min=-80; max=32; count=3; size=5; density=10490; melt=962 },
    @{ id="mithril"; min=-180; max=-64; count=1; size=3; density=8200; melt=1900 },
    @{ id="adamantium"; min=-240; max=-128; count=1; size=2; density=12000; melt=2600 }
)

foreach ($metal in $metals) {
    $id = $metal.id
    foreach ($pair in @(
        @("blockstates/platinum_ore.json", "blockstates/${id}_ore.json"),
        @("blockstates/deepslate_platinum_ore.json", "blockstates/deepslate_${id}_ore.json"),
        @("models/block/platinum_ore.json", "models/block/${id}_ore.json"),
        @("models/block/deepslate_platinum_ore.json", "models/block/deepslate_${id}_ore.json"),
        @("models/item/platinum_ore.json", "models/item/${id}_ore.json"),
        @("models/item/deepslate_platinum_ore.json", "models/item/deepslate_${id}_ore.json"),
        @("models/item/raw_platinum.json", "models/item/raw_${id}.json"),
        @("models/item/platinum_ingot.json", "models/item/${id}_ingot.json"),
        @("models/item/platinum_powder.json", "models/item/${id}_powder.json"),
        @("items/platinum_ore.json", "items/${id}_ore.json"),
        @("items/deepslate_platinum_ore.json", "items/deepslate_${id}_ore.json"),
        @("items/raw_platinum.json", "items/raw_${id}.json"),
        @("items/platinum_ingot.json", "items/${id}_ingot.json"),
        @("items/platinum_powder.json", "items/${id}_powder.json")
    )) { Copy-MetalAsset (Join-Path $assets $pair[0]) (Join-Path $assets $pair[1]) $id }

    foreach ($pair in @(
        @("hardwrought/loot_table/blocks/platinum_ore.json", "hardwrought/loot_table/blocks/${id}_ore.json"),
        @("hardwrought/loot_table/blocks/deepslate_platinum_ore.json", "hardwrought/loot_table/blocks/deepslate_${id}_ore.json"),
        @("hardwrought/recipe/platinum_ingot_from_smelting.json", "hardwrought/recipe/${id}_ingot_from_smelting.json"),
        @("hardwrought/recipe/platinum_ingot_from_blasting.json", "hardwrought/recipe/${id}_ingot_from_blasting.json")
    )) { Copy-MetalAsset (Join-Path $data $pair[0]) (Join-Path $data $pair[1]) $id }

    Write-Utf8 (Join-Path $data "hardwrought/worldgen/feature/ore_${id}.json") @"
{
  "type": "minecraft:ore",
  "discard_chance_on_air_exposure": 0.0,
  "size": $($metal.size),
  "targets": [
    { "state": "hardwrought:${id}_ore", "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables" } },
    { "state": "hardwrought:deepslate_${id}_ore", "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables" } }
  ]
}
"@
    Write-Utf8 (Join-Path $data "hardwrought/worldgen/placed_feature/ore_${id}.json") @"
{
  "feature": "hardwrought:ore_${id}",
  "placement": [
    { "type": "minecraft:count", "count": $($metal.count) },
    { "type": "minecraft:in_square" },
    { "type": "minecraft:height_range", "height": { "type": "minecraft:trapezoid", "min_inclusive": { "absolute": $($metal.min) }, "max_inclusive": { "absolute": $($metal.max) } } },
    { "type": "minecraft:biome" }
  ]
}
"@
    Write-Utf8 (Join-Path $data "hardwrought/hardwrought/materials/${id}.json") @"
{
  "tier": 1,
  "density_kg_m3": $($metal.density),
  "melting_point_c": $($metal.melt)
}
"@
    Write-Utf8 (Join-Path $data "c/tags/item/powders/${id}.json") @"
{
  "values": ["hardwrought:${id}_powder"]
}
"@
    Write-Utf8 (Join-Path $assets "models/block/molten/${id}.json") @"
{
  "parent": "minecraft:block/cube_all",
  "textures": { "all": "hardwrought:block/molten/${id}" }
}
"@
}

foreach ($name in @("void_crystal_block", "budding_void_crystal")) {
    Write-Utf8 (Join-Path $assets "blockstates/${name}.json") "{`n  `"variants`": { `"`": { `"model`": `"hardwrought:block/${name}`" } }`n}"
    Write-Utf8 (Join-Path $assets "models/block/${name}.json") "{`n  `"parent`": `"minecraft:block/cube_all`",`n  `"textures`": { `"all`": `"hardwrought:block/${name}`" }`n}"
    Write-Utf8 (Join-Path $assets "models/item/${name}.json") "{`n  `"parent`": `"hardwrought:block/${name}`"`n}"
    Write-Utf8 (Join-Path $assets "items/${name}.json") "{`n  `"model`": { `"type`": `"minecraft:model`", `"model`": `"hardwrought:block/${name}`" }`n}"
}

$directions = @{
    down='{ "model": "hardwrought:block/NAME", "x": 180 }'; east='{ "model": "hardwrought:block/NAME", "x": 90, "y": 90 }'
    north='{ "model": "hardwrought:block/NAME", "x": 90 }'; south='{ "model": "hardwrought:block/NAME", "x": 90, "y": 180 }'
    up='{ "model": "hardwrought:block/NAME" }'; west='{ "model": "hardwrought:block/NAME", "x": 90, "y": 270 }'
}
foreach ($name in @("small_void_crystal_bud", "medium_void_crystal_bud", "large_void_crystal_bud", "void_crystal_cluster")) {
    $variants = @(); foreach ($direction in @("down", "east", "north", "south", "up", "west")) {
        $variants += "    `"facing=${direction}`": " + $directions[$direction].Replace("NAME", $name)
    }
    Write-Utf8 (Join-Path $assets "blockstates/${name}.json") ("{`n  `"variants`": {`n" + ($variants -join ",`n") + "`n  }`n}")
    Write-Utf8 (Join-Path $assets "models/block/${name}.json") "{`n  `"parent`": `"minecraft:block/cross`",`n  `"textures`": { `"cross`": `"hardwrought:block/${name}`" }`n}"
    Write-Utf8 (Join-Path $assets "models/item/${name}.json") "{`n  `"parent`": `"hardwrought:block/${name}`"`n}"
    Write-Utf8 (Join-Path $assets "items/${name}.json") "{`n  `"model`": { `"type`": `"minecraft:model`", `"model`": `"hardwrought:block/${name}`" }`n}"
}

Write-Utf8 (Join-Path $assets "models/item/void_crystal_shard.json") "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": { `"layer0`": `"hardwrought:item/void_crystal_shard`" }`n}"
Write-Utf8 (Join-Path $assets "items/void_crystal_shard.json") "{`n  `"model`": { `"type`": `"minecraft:model`", `"model`": `"hardwrought:item/void_crystal_shard`" }`n}"

Write-Utf8 (Join-Path $data "hardwrought/worldgen/feature/void_crystal_geode.json") @'
{
  "type": "minecraft:geode",
  "blocks": {
    "alternate_inner_layer_provider": { "id": "hardwrought:budding_void_crystal" },
    "cannot_replace": "#minecraft:features_cannot_replace",
    "filling_provider": { "id": "minecraft:air" },
    "inner_layer_provider": { "id": "hardwrought:void_crystal_block" },
    "inner_placements": ["hardwrought:small_void_crystal_bud", "hardwrought:medium_void_crystal_bud", "hardwrought:large_void_crystal_bud", "hardwrought:void_crystal_cluster"],
    "invalid_blocks": "#minecraft:geode_invalid_blocks",
    "middle_layer_provider": { "id": "minecraft:calcite" },
    "outer_layer_provider": { "id": "minecraft:smooth_basalt" }
  },
  "crack": { "generate_crack_chance": 0.8 },
  "invalid_blocks_threshold": 1,
  "layers": {},
  "outer_wall_distance": { "type": "minecraft:uniform", "max_inclusive": 6, "min_inclusive": 4 },
  "use_alternate_layer0_chance": 0.083
}
'@
Write-Utf8 (Join-Path $data "hardwrought/worldgen/placed_feature/void_crystal_geode.json") @'
{
  "feature": "hardwrought:void_crystal_geode",
  "placement": [
    { "type": "minecraft:rarity_filter", "chance": 64 },
    { "type": "minecraft:in_square" },
    { "type": "minecraft:height_range", "height": { "type": "minecraft:uniform", "min_inclusive": { "above_bottom": 12 }, "max_inclusive": { "absolute": -80 } } },
    { "type": "minecraft:biome" }
  ]
}
'@

Write-Utf8 (Join-Path $data "hardwrought/loot_table/blocks/void_crystal_block.json") "{`n  `"type`": `"minecraft:block`",`n  `"pools`": [{ `"rolls`": 1, `"entries`": [{ `"type`": `"minecraft:item`", `"name`": `"hardwrought:void_crystal_block`" }] }]`n}"
foreach ($name in @("small_void_crystal_bud", "medium_void_crystal_bud", "large_void_crystal_bud")) {
    Write-Utf8 (Join-Path $data "hardwrought/loot_table/blocks/${name}.json") "{`n  `"type`": `"minecraft:block`",`n  `"pools`": [{ `"condition`": `"minecraft:tool/can_silk_touch`", `"rolls`": 1, `"entries`": [{ `"type`": `"minecraft:item`", `"name`": `"hardwrought:${name}`" }] }]`n}"
}
Write-Utf8 (Join-Path $data "hardwrought/loot_table/blocks/budding_void_crystal.json") "{`n  `"type`": `"minecraft:block`"`n}"
Write-Utf8 (Join-Path $data "hardwrought/loot_table/blocks/void_crystal_cluster.json") @'
{
  "type": "minecraft:block",
  "pools": [{
    "rolls": 1,
    "entries": [{
      "type": "minecraft:alternatives",
      "children": [
        { "type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": "hardwrought:void_crystal_cluster" },
        { "type": "minecraft:alternatives", "children": [
          { "type": "minecraft:item", "condition": { "type": "minecraft:match_tool", "predicate": { "items": "#minecraft:cluster_max_harvestables" } }, "modifier": [{ "type": "minecraft:set_count", "count": 4 }, { "type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops" }], "name": "hardwrought:void_crystal_shard" },
          { "type": "minecraft:item", "modifier": [{ "type": "minecraft:set_count", "count": 2 }, { "type": "minecraft:explosion_decay" }], "name": "hardwrought:void_crystal_shard" }
        ] }
      ]
    }]
  }]
}
'@
Write-Utf8 (Join-Path $data "hardwrought/recipe/void_crystal_block.json") @'
{
  "type": "minecraft:crafting_shaped",
  "category": "building",
  "pattern": ["##", "##"],
  "key": { "#": "hardwrought:void_crystal_shard" },
  "result": { "id": "hardwrought:void_crystal_block" }
}
'@
Write-Utf8 (Join-Path $data "c/tags/item/gems/void_crystal.json") "{`n  `"values`": [`"hardwrought:void_crystal_shard`"]`n}"

Write-Output "Generated fantasy ore and void-crystal data/assets."
