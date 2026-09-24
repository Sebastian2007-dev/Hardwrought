# Milestone 9: What You Carry It In

Two rules that turn out to be the same rule. A player carries an inventory only because something is
carrying it for them, and a workbench makes only what a workbench of its kind could make. Both replace
a thing the game had always simply granted.

## The pack is the inventory

Vanilla hands every player twenty-seven slots and a belt, from the first second, for nothing.
Hardwrought hands them the belt.

| Worn | Main grid | Pack page | Carried |
| --- | --- | --- | --- |
| nothing | **closed** | — | 30 kg |
| **Woven pack** — four leaf fibres, 2×2 | 3 × 9 | — | 85 kg |
| **Leather pack** — leather, fibre and the woven pack | 3 × 9 | 1 × 9 | 110 kg |

The woven pack is deliberately reachable from nothing at all: leaves stripped by hand give fibre, and
four of them in the two-by-two grid a player always has give the pack. That is section 71.1.8 again —
the first link of the chain has to be free — and it is why the recipe does not need a bench.

85 kg is not a new number. It is the allowance everything else in the mod was balanced against, and
the woven pack is what provides it. The tier below it, having nothing, is new.

## It cannot be lost

A pack that lived in the inventory would be dropped on death with everything else — and a player who
has just lost their pack has also lost every slot it was unlocking, all at once, with nothing they
could do about it. So it does not live there. `PlayerEquipment` is world data keyed by player, saved
beside vitals and knowledge, and death never touches it.

That is a construction rather than a rule: nothing has to remember to put the pack back, because
nothing ever takes it. On top of it, three guarantees:

- **On respawn**, `AFTER_RESPAWN` makes sure a pack is worn. A player who somehow lost theirs gets the
  plainest one, never an upgrade.
- **On a first join**, a pack and the compendium — except on **hardcore**, where it is the compendium
  alone and weaving the first pack is the opening move.
- **On every other join**, the same check, which also quietly fits out every player of a world saved
  before packs existed.

## Two grids, one set of coordinates

The pack's contents are not extra room in the main grid. They are a second grid the same size that the
inventory turns to, and a better pack fills it from the top down — so every new tier is the same
gesture, one more row, rather than a differently shaped screen each time.

Both grids are built in `AbstractContainerMenu`, not in the inventory menu alone, so the pack is
reachable from a chest and a workbench too. A pack you can only open by closing the chest you are
packing from is a pack nobody would use.

The mechanism is `CarriedSlot`: twenty-seven player slots and twenty-seven pack slots share the same
coordinates, and which set is open is decided per frame. Swapping a pack or turning a page therefore
never changes the menu's shape, never moves a slot index, and never needs a resync. Two synchronised
values carry it — which page is open, and how many slots the worn pack has, with `-1` meaning no pack
and therefore a closed main grid.

A square that is shut has to *look* shut. The panel behind the grid is vanilla's texture and goes on
drawing all twenty-seven wells whatever the menu thinks, so a player with no pack saw a full inventory
that silently refused everything, and a one-row pack looked like it had twenty-seven places to put
things. The screen shades over every position where nothing is open — per position rather than per
slot, since the two grids share their coordinates.

### Shift-clicking into it

A second grid nobody can shift-click into is a second grid nobody uses, and that is what it was.
Every vanilla menu decides where a quick-moved stack goes by hard-coded slot ranges — nine to
forty-five in the inventory, thirty-six to forty-five back the other way — written when the player's
own grid was the only grid there was. The pack's page is appended after the belt and lies outside
every one of those ranges, so while it was open a shift-click had nowhere to put anything and simply
did nothing.

Rewriting a range per menu would mean knowing every menu, including the ones other mods add. Instead
`AbstractContainerMenuMixin` redirects the `quickMoveStack` call inside `doClick` and lets the menu
answer first: only where vanilla came back empty-handed — it could place nothing anywhere — is the
stack offered the pack. A shift-click inside a chest therefore still means the chest and one inside
the crafting grid still means the grid, and the pack catches exactly the case where the grid vanilla
was aiming at is the one the player has turned away from.

It has to be the redirect and not an inject, because `doClick` calls `quickMoveStack` twice: once to
start and once per turn of the loop that empties a slot stackful by stackful. Catching only the first
would move one slotful into the pack and stop. The fallback also follows vanilla's return contract to
the letter — the stack as it was on success, empty for "not an inch" — because a stack returned after
moving nothing spins that loop for ever.

Going the other way needs nothing: a pack index falls into the catch-all branch of vanilla's own
quick move, which walks the player's grid and belt, so shift-clicking out of the pack already worked.

`isActive` alone would not have been enough. It stops a slot being drawn and clicked, but vanilla's
own shift-click walks the slot list asking only `mayPlace`, so a closed slot refuses both — which is
also what keeps the fallback honest on the player's own page: the pack is shut, so nothing reaches
through it. And closing the grid in the menu is only half the rule: picking something up never looks at a menu, so
`InventoryMixin` stops `getFreeSlot` and `getSlotWithRemainingSpace` reaching past the belt. The belt
fills, and then the ground keeps the rest — which is exactly what carrying nothing should feel like.

## The worn strap

The pack and the safety lamp hang off the left edge of the player's own inventory, on a narrow strap
that folds out and shuts again from one small button — the shape Curios-style mods use, and the one
the armour slots already taught players to read.

They belong beside the character panel rather than behind a button of their own. What a player is
wearing is part of the picture of the player — armour, shield and pack are one glance, not two — and a
screen that has to be opened to see whether you are wearing a pack is a screen nobody opens. An
earlier draft did exactly that and was the wrong shape.

Folding is the one piece of this that lives on the client alone: it is something the player does to
their own view, and the server is never told. So the squares answer the question twice — the server
always says open, which keeps every click it validates legitimate, and the client answers with the
fold, which is what stops a folded-away square being drawn or hit.

The strap gives way to the recipe book. Vanilla opens that book into exactly this space and pushes the
whole panel right to make room, so the two cannot both be there; a strap drawn over the top of the
book would read as a bug rather than a choice. With the book open the strap folds itself away and its
button says why, and closing the book puts the strap back exactly as the player left it.

The pack's page keeps its own button under every container panel, where there is always room in every
menu including ones from other mods, and where it never covers a slot.

Taking the pack out closes the main grid the moment the menu next syncs, which is the honest thing for
it to do. Swapping a pack for a smaller one spills the rows it has stopped having at the player's
feet, where they can be seen and picked up, rather than swallowing them.

## Benches have tiers

The bench hewn out of a standing log is the first one a player can have, and it now reads as exactly
that: a flat surface on a stump. It joins what the early game is made of — boards, handles, a chest,
the first metal tools, the leather pack — and anything more wants a bench that was itself joined.

`BenchTier` reads a datapack tag of **results**, and it is a list of what the hewn bench *can* make
rather than of what it cannot. That is the strict reading on purpose: something the tag has not heard
of is refused, so a recipe from a later milestone or another mod does not quietly become available at
the first bench in the game. The cost is real and worth stating — every new early recipe has to be
added to the tag of the rung it belongs to, and forgetting one shows up as a bench that will not make
it.

One entry in that tag matters more than the rest: **the crafting table**. Without it the first bench
is also the last one, and the chain stops dead.

The rule is applied where the result is chosen, so a refused recipe simply produces nothing rather
than being hidden. A hewn bench and a joined crafting table open the very same menu class, so the only
thing that tells them apart is the block the menu is anchored to — which is what `CraftingMenuAccessor`
is for.

## Tests

`EquipmentGameTests`, eight tests:

- a worn pack is nowhere in the inventory, and is still worn after the inventory is emptied
- a player without one is given the plainest pack back, and one wearing a better one is left alone
- each tier carries more than the one below and opens one more row, and no pack has more rows than the
  grid it is drawn in
- what is in a pack rides on the pack through a copy, a woven pack holds nothing, and something that
  is not a pack is not mistaken for one
- only a pack goes on the back and only a lamp on the belt, and emptying a worn slot is always allowed
- **shift-clicking reaches the pack page.** A stack quick-moved while the page is open lands in the
  pack whole, a quick-move from the pack comes back out, and the same click on the player's own page
  puts nothing into a pack the player cannot see
- the hewn bench does early work and refuses what wants a joined bench, a joined bench is not limited
  at all, and the hewn bench can make the bench that replaces it
- **every square of the carried grid still points at its own slot.** A slot carries two different
  numbers — the container slot it shows, and its place in the menu — and the public `index` field is
  the second one, overwritten by `addSlot` moments after the slot is built. Reading it while rebuilding
  the gated grid handed all twenty-seven squares container slot 0, so whatever sat in the first belt
  slot appeared twenty-seven times over and could be taken twenty-seven times. Nothing else caught it:
  it compiles, it syncs, and every other test passed.

`CoreClientGameTest` additionally opens the real inventory and photographs it — the strap folded out
and shut, and the pack's page — then asserts that the page opens exactly the pack's own row and closes
the grid behind it. Slot coordinates and a drawn panel are the kind of thing that passes every server
test and is still visibly wrong on screen.

## Open

- The pack items use the vanilla leather chestplate sprite; see `textureRequirements.md`.
- Only the pack and the lamp are worn so far. The screen has room for more.
- The tier list is seeded for the early game as it stands today. Every recipe a later milestone adds
  below the joined bench has to be added to the tag with it.
