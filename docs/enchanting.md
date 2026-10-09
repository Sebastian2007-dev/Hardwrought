# Enchanting: runes written on the piece

Enchanting is done at the vanilla enchanting table, but not in the vanilla way. The runes of an
enchantment are written on the piece itself with **lapis lazuli powder**.

## The inks

The powders are ground in the **crusher**: one lapis lazuli gives one lapis powder; a bar or a piece of
raw ore of mithril or adamant gives one of its powder.

## A sitting

Hold the piece in the main hand and use the table. The table's own screen never opens.

- **Left**: the piece, drawn large, in its own outline (the shape the anvil works on, three times as fine).
- **Right**: the runes that lie ready. Every enchantment has its own rune, always the same shape.
- Pick a rune up with a click, turn it with a right click, the mouse wheel or R, and lay it on the piece.
- A laid rune shows the level it will give. A click picks it up again, a right click wipes it off.
- **Work in** writes them all.

## Coming back to a piece

A piece remembers its runes and where they lie, and it can be brought back to the table as often as
wanted: as a part, as the tool it became, as that tool worked up to netherite, mithril or adamant.

- The runes it carries are shown where they lie, in a quieter colour. They keep their room and count
  towards what the piece takes.
- New runes are written beside them while there is room and the piece takes more. A piece worked up a
  tier, or ground since, may take more than it did.
- A right click on an old rune marks it to be wiped off (it turns red; another right click keeps it
  after all). On working in, its enchantment is gone, its room is free, and the rune can be written
  again: larger on the piece, or in mithril or adamant for a higher level. Wiping costs nothing; the
  powder and levels the rune once took are not given back.
- Enchantments the piece has without a rune (found that way, or laid on from a book) take no room but
  count towards what the piece takes, and are not written a second time.

## What a rune gives

- The more of the rune lies on the piece, the higher the level. From 90 % on the piece it gives the
  enchantment's highest level, below that in proportion, and under one level's worth it does nothing.
  A rune may hang over the edge.
- **The material decides how large a rune is.** Adamant takes the finest strokes: a common rune is
  7 × 7 squares on it and a rare one 9 × 9. On mithril every rune is a square larger each way (8 and
  10), and on everything below — iron, steel, netherite, every part — two (9 and 11). The piece itself
  is the same size, so what lies comfortably on adamant has to be squeezed onto iron: fewer runes fit
  whole, and the rest hang over the edge at a lower level. A piece worked up a tier shows its old
  runes at the finer size, which frees room on it.
- Two runes may lie side by side, touching, but not over each other. Room on the piece is what limits
  how much can be written well. A common rune is a little over two of the piece's pixels across and a
  rare one three, so a small piece does not hold every rune it could take whole: which rune gets the
  room, and which is left hanging over the edge at a lower level, is the writer's choice.
- Vanilla's exclusions are not kept: protection lies beside fire, blast and projectile protection,
  sharpness beside smite, fortune beside silk touch. Only the same rune twice is refused.
- **Parts first**: on a part (a pickaxe head, a blade, a cuirass) a rune works itself in fully and
  goes with the part into the tool it becomes. On a finished tool or armor piece a rune reaches one
  level less than the highest (an enchantment with only one level still takes).

## Overcharging

Above the runes lies the row of inks: lapis, mithril and adamant powder. A click chooses what the
next rune is written in. A rune written in **mithril powder** can reach one level beyond its
enchantment's highest, in **adamant powder** two: efficiency VI and VII, protection V and VI. It
still has to lie on the piece almost whole to reach it, and on a finished tool it is still one level
short. An enchantment that only has one level (silk touch, mending) cannot be overcharged. Each rune
uses up the powder it is written in, at the same rate as lapis.

**A well made piece holds more runes**: one more from 75 % craftsmanship, two from 100 %, three from
125 %. That is the smith's share in the enchanting: 125 % is only reached by a piece forged well and
then ground, or worked up a tier with a true hand.

The finest ink on the piece also decides how many runes it holds: with a rune in mithril on it, one
more than its material takes; with a rune in adamant, three more.

## What it costs

- **Lapis powder**: one measure for every six squares of rune written onto the piece.
- **Experience**: 1 to 4 levels a rune, by how rare it is.
- **How many runes**: by how readily the material takes enchantment (`2 + enchantability / 5`, at
  most 7): gold 6, copper 5, iron, steel and tungsten steel 4, stone 3.

## Which runes lie ready

Fixed, not drawn by lot. A rune lies ready when the table has enough bookshelves round it and the
writer had enough levels **on sitting down**. Writing takes levels, but the runes that lay ready stay
ready until the sitting is over.

| Rune | Bookshelves | Level | Costs | Pattern |
|---|---|---|---|---|
| common (protection, sharpness, efficiency, power) | 0 | 1 | 1 level | 7 × 7 |
| uncommon (unbreaking, smite, knockback, feather falling, ...) | 4 | 8 | 2 levels | 7 × 7 |
| rare (fortune, looting, fire aspect, respiration, ...) | 9 | 16 | 3 levels | 9 × 9 |
| very rare (silk touch, infinity, thorns, ...) and every treasure enchantment (mending, frost walker, soul speed, swift sneak, ...) | 15 | 25 | 4 levels | 9 × 9 |

The class is the enchantment's own weight, so enchantments from datapacks fall into place by
themselves. What vanilla's table could give has a rune, and so has what vanilla kept for treasure and
for librarians, since there is no librarian to buy it from any more. Curses have none.

## What is left of the vanilla way

- Librarians sell no enchanted books (`data/minecraft/tags/villager_trade/librarian`).
- Three in four enchanted books that any loot would have given are not there.
- Enchanted tools and armor sold by villagers, and books laid on at an anvil, are unchanged.

## Where it lives

- `enchanting/RuneWork`: the rules as arithmetic: patterns, laying, levels, costs.
- `enchanting/RuneEnchanting`: the table, which runes lie ready, judging and writing.
- `client/enchanting/RuneScreen`: the sitting.
- `core/networking/RunePayloads`: the two messages of a sitting.

## Verification

- `EnchantingGameTests`: a rune is always the same figure; level by how much lies on the piece; what
  is written is judged (over each other, twice, too many, not ready, too little); a part takes its
  tool's runes and hands them on to the tool.
