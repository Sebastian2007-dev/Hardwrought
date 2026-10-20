# Automation

Stage E5 of the [electricity roadmap](electricity-roadmap.md): parts make themselves. Everything made by
hand at the benches can be made by a line the player designed — and the line goes on consuming parts.

## The assembler

A frame, two gripper arms and a controller around an assembly bench (60 V, 200 W). It makes whatever the
**drawing** in it shows, from the parts put into it, eight seconds a piece at full power. Its three rows
hold the drawing, the parts and what it has made, opened as a chest is; hoppers feed it and draw from it.
With nothing to make it from it takes no current.

**Drawings.** A blank drawing (four paper and black dye make four) used on an assembly bench copies the
work laid out on the bench — the recipe the bench's grid makes now — but only of a thing its maker has
studied (see the knowledge system): a drawing is drawn from knowing how a thing goes together. One drawing,
one thing.

## Ordering at the storage desk

An assembler joins a storage network as a tube does: the tubes run to it. Everything an assembler on the
network makes and the store room has none of is listed at the desk at nought. A click on it orders one,
a shift-click a stack. Working an order the assembler takes from the store room the parts it lacks — one
of anything that fits each part of the drawing — and puts what it makes back into the store room, where
the desk shows it. Short of a part nowhere to be had, it waits, and the meter says for what.

## The workshop bench at the desk

The third workbench (`WorkshopBenchBlock`): a nailed bench bound in steel — three steel plates over it with
a tube section in the middle, steel rods for its arms and legs (`PTP / RNR / R R`). On its own it is a
crafting table that makes what the nailed bench makes and keeps its grid, as that one does.

Set against a storage desk (any side, above or below), it becomes the desk's bench. The desk then opens
with a panel to the right of its list: the bench's own grid — the same one that lies on the bench — and
its result. What is made there is made as at the bench (its tier, its recipes; the first matching recipe
by name). After every craft, each square the craft emptied is filled again out of the store room with
one of what lay there, so the grid stands ready to make the same again; a shift-click on the result
therefore makes as many as the store room has the makings of. A shift-click on a square puts what lies
there away, and the arrow button clears the whole grid into the store room. Without current nothing is
fetched or put away, but the grid still crafts. Closed, the grid stays on the bench; one player at a time
works it, at the bench or at the desk.

## The conveyor

A belt over rollers (four from three rubber, two iron plates and a shaft). Its rollers turn on an axle
across the belt — a shaft, a gear or an electric drive at either side — and one length of belt drives the
next it runs into, so a whole line turns from one drive. It carries what lies on it, items and creatures
alike, at about two and a half blocks a second at 30 turns a minute, drawing it to the middle of the belt.
At the end of the line an item goes into whatever holds things in front of it; with nothing there it
falls off. Sneaking, a player is not carried. Each length takes half a stress unit per turn a minute.

## The second marks

The coil winder, wire drawer and glass kiln each have a **Mk II**: the first mark with an advanced
circuit, two silicone gaskets and an electric motor, twice as fast for more current.

## Machines wear

Section 78 of the specification, for every electric machine — the crushers, furnaces, sawmills, presses,
lathe, tube mill and the stations:

- Its bearings and gaskets wear while it works: ten hours of work to wear through, oiled.
- Its oil lasts two hours of work. Dry, it wears **six times** as fast. A bottle of **machine oil**
  (heavy oil from the still or refinery) fills it again and gives back the bottle. A new machine comes
  oiled.
- Worn, it works slower — at worst half as fast — and smokes when nearly worn through.
- Worn through, it **seizes**: it takes no current and does nothing, and the meter says so. A **service
  kit** (two bearings, two rubber gaskets and machine oil) puts new bearings and gaskets in.

So an automated line keeps consuming parts: bearings, gaskets and oil, and the electric drives their
brushes.

## Tests

`AutomationGameTests`: an assembler makes the two straps its drawing shows from two leather and a nugget,
and with nothing left to make them from takes no current; a desk asked for straps the store room has none
of orders them from the assembler on its tubes, which fetches its parts from the chest and puts the straps
in it; a belt turned by one drive carries three ingots into the chest at its end; a machine run dry wears
six times as fast, works slower worn, seizes worn through, and is set going again by a service kit and oil.

The assets are written by `tools/AutomationAssets.java`.
