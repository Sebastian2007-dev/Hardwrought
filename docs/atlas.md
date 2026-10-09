# The atlas

A book that draws the land its bearer walks through, and keeps it. In the manner of the Antique
Atlas mod.

## Making and carrying it

A book and a compass, in any order, make an **atlas**. It does its work while it is anywhere in the
inventory; it does not have to be held.

## What it draws

- Every second, the chunks within three of the bearer that are loaded and not yet in the atlas are
  drawn in. The atlas never loads a chunk to look at it.
- A chunk is sixteen squares, each four blocks across: the map colour of what lies on top in the
  middle of the square, lighter where the ground rises towards the north and darker where it falls,
  as vanilla's maps shade it. The surface is what is drawn; caves are not.
- What was drawn once stays as it was drawn, whether the land changes afterwards or not.
- Each player has their own atlas for each dimension. It is kept with the world, not in the item: an
  atlas that is lost is a book to be made again, not a map to be walked again.

## Reading it

**M** opens it (the key can be changed), and so does using the book. North is up.

- Drag to move the page, turn the wheel to change the scale (2 to 8 pixels to a square), space to
  come back to where the bearer stands.
- The bearer is the white point, with a stroke the way they face.
- A right click on the page marks the place: a name is asked for, Enter keeps it, Escape does not.
  A right click on a mark takes it out again. An atlas holds 64 marks for a dimension.
- Where the bearer of an atlas dies is marked in red by itself. When the atlas is full of marks, the
  oldest death gives way to a new one.
- The foot of the page shows the place under the pointer and how many chunks are drawn.

## Where it lives

- `atlas/Atlas`: the book, what is drawn, the marks, and how it is saved (`Atlas.Data`, per dimension).
- `core/networking/AtlasPayloads`: chunks as they are drawn, the whole atlas on opening, marks.
- `client/atlas/AtlasClient`, `AtlasScreen`: the client's copy and the open book.

## Verification

- `AtlasGameTests`: carrying it; the chunks round the bearer are drawn, each once, the same way twice;
  marking and unmarking; saved and read back unchanged.
- `AtlasClientGameTest`: a screenshot of the open atlas in a real world, with the bearer and two marks.
