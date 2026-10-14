# Water quality: purified water, colours, fields and the bowl

How water is told apart, and the ladder of cleaning it: the bowl, the sand filter, the ceramic filter and the
electric purifier.

## The kinds (`water/WaterQuality`)

In order of usefulness; mixing two waters makes the worse one win once it is a quarter of the mixture.

| Kind | Hydration | Illness risk | Colour (items) | World tint |
|---|---|---|---|---|
| **Purified** | 110 % | 0 | light blue `#6FD6F7` | 70 % towards `#5CCBF2` |
| Fresh (spring, rain) | 100 % | 4 % | vanilla water | none |
| River | 90 % | 12 % | `#3D8BC0` | 20 % |
| Swamp | 65 % | 45 % | murky `#6B6A33` | 60 % |
| Salt | −60 % | 5 % | grey-green `#4F8C8A` | 35 % |

Only purified water is wholly safe. Boiling purifies anything but seawater (`boiled()`), which it only
makes saltier.

## Shown in the world (`WaterQualityStorage.SHOWN`, client `WaterTint`)

Every cell whose quality differs from its surroundings, and every cauldron that holds anything but plain
water, is sent to the client per chunk — rebuilt at most every ten ticks, so marked water flowing through a
lake does not cost a packet a step. The client tints water by it in `BiomeColorsMixin`, after the season.
Water nobody has marked is judged by its biome: the sea is shown as seawater, everything else keeps its
biome's colour. (The server's finer fallback — a small standing pond reads as swamp water — is not
repeated on the client, so such a pond looks like its biome until somebody pours something into it.)

## Shown on items

- **Buckets:** the water in the bucket is tinted by its kind (`assets/minecraft/items/water_bucket.json`,
  a `select` on `hardwrought:water_quality` over the three fill steps; textures
  `item/bucket_water_tint_*`). The bar under a bucket is in the water's colour, and a full bucket of
  anything but plain water shows it at full width (`ItemStackBucketBarMixin`).
- **Bottles** of anything but plain water carry the colour as the potion's custom colour
  (`CauldronWater.bottle`).
- **Waterskin:** its bar (the drinks left) is in the water's colour.
- Every container names its water on the tooltip (`BucketTooltip`).

## Rare purified springs

Meadow, cherry grove and grove water is purified (`water_quality/alpine_spring.json`), and so is the
groundwater under them (`aquifer/alpine_spring.json`): high meadows are where a clean spring is found.

## Seawater on a field (`FarmlandWaterMixin`)

Farmland is wet where there is water within four blocks, as in vanilla — but seawater does not count.
Rain still waters a field by the sea.

## Cauldrons (`CauldronWater`)

A cauldron remembers what was poured into it (`WaterQualityStorage.CAULDRON_QUALITY`): a bucket or a
bottle in, a bucket or a bottle out, mixing as water does. Before, seawater poured into a cauldron came back
out clean.

## Boiling and drinking take time

- The **bowl** goes on a campfire like food (`recipe/water_bowl_from_campfire`, 20 seconds) and comes off
  purified and hot. The campfire does not take seawater, water already purified or a bowl still hot
  (`CampfireWaterBowlMixin`).
- The **waterskin** is held over a lit fire for five seconds to boil it (`Boiling`); looking away stops it.
- A drink from either takes as long as a potion.

## The bowl (`WaterBowlItem`)

1. An empty wooden bowl dipped into water takes 250 mB of it, of whatever kind it is.
2. Boiled on a campfire it is purified — and hot. It cools in thirty seconds while carried; its bar is the
   heat going out of it, and the bowl steams until it is cool.
3. It can be drunk hot. Water hotter than a third of boiling burns at once (1 to 4 health, by its heat)
   and leaves the drinker **scalded** for a minute.
4. Drunk, the empty bowl is left.

## Scalds and inflammation (`survival/Injuries`)

- **Scalded** (a minute; stronger for water drunk nearly boiling): every two seconds a 2.5 % chance per
  level to turn into an inflammation, about a third over the whole minute.
- **Inflammation** (four minutes): one health every twenty seconds, never below three hearts, and a weaker
  arm (−1 attack damage).

## Filters and the purifier (`WaterTreatment`, `WaterTreatmentBlocks`)

**Water poured on top soaks in, and comes out cleaned underneath** — into a tank standing there, or out onto
the ground as water of its new kind (50 mB a tick each way at most). A filter can simply stand under a
spring. Buckets and pipes fill them and draw from them too, and an empty bowl takes a bowlful. An empty hand
reads what is in each side.

| Stage | Recipe | Pace | Makes | Bed |
|---|---|---|---|---|
| **Sand filter** | planks around sand, charcoal, gravel | 50 mB/s | river and swamp water → fresh; salt not at all | charcoal, 4 buckets each, up to 16 |
| **Ceramic filter** | terracotta around charcoal and a sand filter | 150 mB/s | anything but seawater → purified | charcoal, 16 buckets each, up to 64 |
| **Water purifier** | iron plates, circuit board, ceramic filter, electric pump | 1000 mB/s at rated voltage | anything → purified; seawater at 250 mB/s | current, through a coil like the other machines (30 Ω) |

## Tests

`WaterGameTests`: the order of the kinds and what boiling does; a cauldron of seawater giving seawater
back; a field drying beside seawater and staying wet beside clean water; a bowl of swamp water boiled to
purified (only after the boil's time), scalding when drunk hot and cooling when carried, and one of seawater
that is not set to boil; each stage of cleaning at its pace, with and without charcoal or current, and
none of the filters taking salt out; a purified cell, and poured swamp water, sent to the client.
