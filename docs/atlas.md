# The atlas

A book that draws the land its bearer walks through, and keeps it. In the manner of the Antique
Atlas mod.

## Making and carrying it

A book and a compass, in any order, make an **atlas**. It does its work while it is anywhere in the
inventory; it does not have to be held.

## What it records

- Every second, the chunks within three of the bearer that are loaded and not yet in the atlas are
  recorded. The atlas never loads a chunk to look at it.
- A chunk is sixteen squares, each four blocks across: the map colour of what lies on top in the
  middle of the square, and whether the ground rises or falls towards the north, as vanilla's maps
  keep it. The surface is what is recorded; caves are not.
- What was recorded once stays as it was, whether the land changes afterwards or not.
- Each player has their own atlas for each dimension. It is kept with the world, not in the item: an
  atlas that is lost is a book to be made again, not a map to be walked again.

## How it is drawn

The book is opened as a book: a leather cover with stitching, the map on its page under a double
rule, a compass rose and a scale bar of a round number of blocks, and, where the screen is wide
enough, the list of marks down the side past the fold.

The map is drawn as a cartographer would (`AtlasPainter`), into a texture that is painted again only
when the view or the atlas changes:

- Each square's map colour is read back into a **kind of land**: water, forest, grass, sand, snow,
  rock, earth, built, lava, nether. Leaves and meadow plants share one colour, so plants count as
  forest only where most of the squares round them are plants too.
- Each kind is a **watercolour wash** on parchment, shaded by the slope the server kept, with the
  grain of the paper over it.
- A line of **ink along every coast**, and a lighter band of shallow water inside it.
- The edge of what is known is **ragged and burnt**: unknown land is bare paper.
- Little **drawings** stand on the land, at most one to a cell of nine pixels: trees and pines in
  the forests, peaks on rock and snow, waves on open water, tufts on grass, dots on sand and snow,
  houses where something is built.

The paper grain, the ragged edge and where every drawing stands are worked out from the world's
coordinates, so they stay in place while the page is dragged.

## Reading it

**M** opens it (the key can be changed), and so does using the book. North is up.

- Drag to move the page, turn the wheel to change the scale (1 to 8 pixels to a square), space to
  come back to where the bearer stands.
- The bearer is a white arrow the way they face.
- **Other bearers** of an atlas within 256 blocks in the same dimension are blue arrows, with their
  names; the server tells each bearer once a second. They are also listed under *Companions*.
- A right click on the map marks the place: a slip asks for a **name** and a **symbol** (home, camp,
  mine, cave, flag, danger, find, portal); Enter keeps it, Escape does not. A right click on a mark
  takes it out again. An atlas holds 64 marks for a dimension.
- Where the bearer of an atlas dies is marked with a **skull** by itself. When the atlas is full of
  marks, the oldest death gives way to a new one.
- The **list** down the side shows every mark with its symbol and how far it is; a click turns the
  page to it, a right click takes it out, the wheel scrolls it.
- The foot of the page shows the place under the pointer and how many chunks are drawn.

## Where it lives

- `atlas/Atlas`: the book, what is recorded, the marks and their symbols, the other bearers, and how it
  is saved (`Atlas.Data`, per dimension).
- `core/networking/AtlasPayloads`: chunks as they are drawn, the whole atlas on opening, marks, other
  bearers.
- `client/atlas/AtlasClient`: the client's copy. `AtlasScreen`: the open book. `AtlasPainter`: the map.
  `AtlasIcons`: the symbols and drawings, each written out as a grid of characters.

## Verification

- `AtlasGameTests`: carrying it; the chunks round the bearer are drawn, each once, the same way twice;
  marking with a symbol and unmarking; saved and read back unchanged; another bearer seen near and not far.
- `AtlasClientGameTest`: in a real world with a pond, a wood, a hill of rock and a beach built round the
  bearer, screenshots of the open atlas, of it zoomed in, and of the slip for a new mark.
