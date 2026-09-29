# Steel and beyond

Above iron there are four metals now, each with tools, weapons and armor: **steel**, **stainless
steel**, **titanium** and **tungsten steel** (mechanics specification section 56). Tools and weapons are
forged like iron's — a head or blade on the anvil, then handles in the grid; armor is riveted from forged
plates.

## Making steel

Steel and its alloys are made **only in the smeltery** (see [smeltery.md](smeltery.md)). There, iron and
coal powder melt together into molten steel, and a faucet pours that steel into an ingot cast. The old
cementation and crucible routes are gone.

| In the tank | Alloys at | Gives |
|---|---|---|
| 4 iron + 1 carbon (2 coal powder = 1 ingot's worth) | 1540 °C | 4 steel |
| 3 steel + 1 chromium + 1 nickel | 1550 °C | 5 stainless steel |
| 3 steel + 1 tungsten | 3422 °C | 4 tungsten steel |

**Titanium** needs no alloying: its ore is already in the world, deep in granite, and forged like iron.

## The tiers

| | durability | mining speed | damage | cuts like | armor (helm / chest / legs / boots) | toughness | armor weight vs. iron |
|---|---|---|---|---|---|---|---|
| iron | 250 | 6 | 2 | iron | 2 / 6 / 5 / 2 | 0 | 100 % |
| steel | 900 | 7.5 | 3 | diamond | 2 / 5 / 7 / 2 | 1 | 100 % |
| stainless steel | 1300 | 7.5 | 3 | diamond | 2 / 6 / 7 / 2 | 1.5 | 102 % |
| titanium | 1700 | 8.5 | 3.5 | netherite | 3 / 6 / 8 / 3 | 2.5 | 57 % |
| tungsten steel | 2600 | 10 | 4.5 | netherite | 3 / 7 / 9 / 3 | 3.5 | 110 % |

Tungsten steel armor also resists knockback by 10 %. Forging quality and heat treatment apply on top,
as for iron; every steel hardens when quenched, titanium does not. Weapons use the same class profiles
as iron's (sword, knife, two-handed, polearm, axe), armor the plate profile.

## Rust

Plain steel rusts. Held or worn in rain or water, a steel tool, weapon or armor piece takes on rust —
fully rusted after about four minutes of being wet. Rust makes a tool dig slower (60 % at full rust), a
weapon hit softer (70 %), and every use may wear it a second time, as likely as it is rusted. The
tooltip shows how rusty it is. Using a rusty item on a **grindstone** takes the rust off, and 2 % of its
durability with it. Stainless steel, titanium and tungsten steel never rust.

## Where it lives

- `metallurgy/Alloys` — the three alloy ingots.
- `smeltery/` — where they are made (see [smeltery.md](smeltery.md)).
- `smithing/AlloyEquipment` — the four tiers, their tool and armor materials and items.
- `smithing/Rust` — rust, its effects and the grindstone.
- `tools/alloy_assets.py` — textures (iron's, recoloured per metal), models, recipes, tags, materials,
  profiles, weights and names. Run `tools/glow_textures.py` after it.

## Verification

`SmelteryGameTests` covers how steel is made: iron and coal become steel in the tank, and the faucet
casts it into a bar. The tier stats and rust are covered by the equipment and smithing tests.
