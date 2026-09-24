# Milestone 8: The Knowledge System

Milestone 8 implements specification sections 79 to 83. It replaces two things at once: the recipe
book, and the wiki tab a player would otherwise keep open on a second monitor. Everything the game
can tell a player is in one browser, and what that browser will say depends on what the player has
actually done.

The rule is one sentence: **a player may see the shape of everything and the name of what they have
found out.**

## Two ways of coming to know a thing

| Level | How it is reached | What the compendium then shows |
| --- | --- | --- |
| **Unknown** | never held, never worked with | a black shadow of the item, named `???` |
| **Discovered** | held — it was in the inventory | the real icon and the real name |
| **Studied** | worked with, examined in the hand, or carried around | the same, and the entry counts as understood |

Discovery is found rather than announced. A slow simulation job walks the inventory of each online
player and marks what it finds. That costs one pass over forty-one slots per player per slow tick,
it needs no hook into vanilla pickup at all, and it cannot miss an item that arrived some other way
— a trade, a dispenser, a command.

The same pass is also the slowest way to study something, and the two levels fall out of the two
passes. A thing seen for the first time is discovered; a thing still in the bag one pass later has
been carried around for ten seconds and more, and that is study. Nothing extra is remembered to make
that work — the discovered set already *is* the record of having seen it before.

That pass stays silent. Picking up a stack of gravel is not a moment, and a player walking out of a
cave with thirty new things should not be handed thirty toasts about it. The acts below still
announce themselves, because each of those was a decision.

Studying has to be an act, so it is hooked where the act already happens:

| Act | Where it is caught | What is learned |
| --- | --- | --- |
| Breaking a block | `PlayerBlockBreakEvents.AFTER` | the block **and** the tool that did it |
| Crafting | `ItemStack#onCraftedBy` (mixin) | what was made, in any grid, anywhere |
| Eating | the existing `ItemStackMixin` | the food |
| Examining | a medium simulation job on the main hand | what is being held |

Examining is the one act that had to be added rather than found. Section 82 counts experimenting
among the ways to learn, and an ingot is why that matters: there is nothing a player can break, cook
or craft **with** a bar of iron, so without a way of simply turning it over it would stay a page of
question marks for ever. Keeping the same item in the main hand across three medium passes — a shade
over three seconds — studies it. Switching slots, emptying the hand or swapping for something else
starts the count again, so this rewards holding one thing rather than owning many, and the entry is
marked afterwards so a player who never puts the item down does not re-study it every second.

One mixin on `ItemStack#onCraftedBy` covers the inventory square, a vanilla workbench, a hewn
workbench and any station a later milestone adds. Hooking every menu with a result slot would have
been many times the code for the same answer.

## What is written to disk

Knowledge is the whole point of the milestone, so it outlives the session it was gathered in. It is
part of `CoreSaveData`: a map of player to two identifier sets, written as a sixth codec field.

Two identifier sets rather than a level per entry. Everything held is in the first, everything worked
with in the second, anything in neither is unknown. The save stays small, the common question — do I
know this? — is a set lookup, and the two sets cannot drift into a state the compendium would have to
guess about.

The field is optional, so a world saved before this milestone loads as a player who knows nothing
rather than refusing to load.

Two tests hold this down, because "it saves" is the kind of claim that is easy to believe and easy to
be wrong about:

- a codec round trip through NBT, which proves a discovered entry comes back discovered, a studied
  one comes back studied, and an absent field comes back as an empty book;
- the client test genuinely **closes the world and reopens it from disk**, then checks that a studied
  entry is still studied, a discovered one has not been promoted, and nothing was invented.

## Saying so

Learning something is a moment, and a moment that leaves no mark may as well not have happened. The
first time an item is discovered or studied, the same toast vanilla shows for an unlocked recipe
appears in the corner: the item, and whether it was **Discovered** or **Studied**.

The whole toast lasts as long as any other vanilla one, however much it has to say; the lines take
turns inside that single span rather than each claiming a span of its own.

Learnings are gathered rather than sent one by one. `KnowledgeSystem` queues them and a fast-tier job
flushes the queue, so breaking a new rock with a new pick — two things learned in one swing — is one
toast with two lines taking turns, not two toasts racing each other. Discovering a thing and then
studying it before the queue is flushed replaces the earlier line instead of adding a second; a
player learns a thing once. A queue is capped at eight lines, because someone who empties a shulker
of unfamiliar things has still learned all of it — every entry is in the compendium — but a toast
that cycled for two minutes would be a nuisance rather than a reward.

## Two halves of one book

`B` opens the compendium on its front page, and so does the compendium item. There are two halves to
choose between, and they answer different questions:

| Half | Answers | Drawn from |
| --- | --- | --- |
| **Knowledge** | how is this made, what is it used in, where is it found | the recipe and loot tables |
| **Thoughts** | what is in my way, and roughly where the way around it lies | a written chain of six notes |

The front page is the one thing in the browser the server is never asked about. It says the same to
every player, and a round trip before the book so much as appears would be felt on a poor connection.

## The browser

`R` over any item asks how it is made. `U` asks what it is used in. Both skip the front page and go
straight to the answer. The keys work over a slot in any
container screen, over the item in hand when no screen is open, and inside the browser over anything
it has drawn. Left-click walks to how a thing is made, right-click to what it is used in, and
backspace walks back.

Ten shelves, from section 80: materials, crafting, metallurgy, engineering, agriculture, biology,
medicine, chemistry, magic, electricity. Which shelf an entry stands on is read off the item itself —
its components, its tags, the block behind it — rather than written down per item, so a mod that adds
a thousand items does not need a thousand lines of configuration.

## Where a thing comes from when nobody makes it

A recipe browser that only knows recipes shrugs at a feather. Most of the early game is not crafted
at all, so `R` answers with two things: the recipes, and the places the thing is found.

| Kind | Read from | Shown as |
| --- | --- | --- |
| **Mob** | the entity default loot table | the spawn egg, *Dropped by Chicken* |
| **Block** | the block loot table | the block, *Broken out of Gravel* |
| **Container** | every other loot table | a chest, *Found in Shipwreck Map* |

A block that drops itself is not listed — dirt does not come from dirt. A block that the player has
never held is masked the same way an ingredient is, because it is an item like any other; a mob and a
chest are places in the world rather than items, so those are named outright.

Loot tables keep their contents private, but they can serialise themselves. The index walks the JSON
a table produces through its own codec rather than reaching into its fields with a mixin: one pass
over every table, done once and only when somebody first asks, and it keeps working when the
internals move.

The list grows to fill the room it has. A feather nobody crafts shows every mob and chest it comes
from; an item that is both found and made keeps enough space below for a recipe card.

## The written half

The browser cannot help with a thing the player has never heard of. Nobody looks up the hewn workbench
before they know a standing trunk can be hewn at all, and a mod whose first hour is unlike vanilla's
either explains itself or is played with a wiki open on a second monitor — which is the exact thing
section 79 exists to abolish.

So there is a second half: six notes, in order, written as though the player had written them.

| Note | Points at | Opens once the player has held |
| --- | --- | --- |
| Something with an edge | flint tools | — it is there from the first page |
| A bench out of the trunk | the hewn workbench | a flint hatchet, or a flint shard |
| Firmer than flint | stone tools | cobblestone, or a flint pickaxe |
| Heat that stays put | the brick furnace | clay, or a cobblestone piece |
| Rust in the rock | iron tools | the brick furnace, or raw iron |
| Two soft metals | bronze | a copper ingot, or raw tin |

Every note names the material, the place or the act, and stops there. None of them names a grid. The
note about the bench says to crouch against a standing trunk and keep working the same spot, because
no player would ever guess that; it does not say how many strokes. Finding the pattern is still the
player's work, and the knowledge half is where they go once they hold the first piece.

A note the player is not ready for is drawn as unreadable scrawl rather than hidden — the same bargain
the black shadows strike. So is the item beside it: the thing a note points at is a silhouette until
the player has held it, and its own icon afterwards, which is what makes a followed note look
different from an open one at a glance.

`Journal` holds the chain and `Journal.state` resolves it against one player's `PlayerKnowledge`. A
note counts as followed the moment the player has held **any** of the things it points at, so making a
flint sword closes the same note a flint hatchet would; the icon then shows the one they actually
hold rather than the first in the list.

## Why the server answers

The client never reads the recipe table for itself. It asks, and the server sends back a page in
which **every stack already carries the knowledge level of the player who asked**. A browser that
looked recipes up locally would know the whole tech tree from the first tick, and section 81 would
have nothing left to hide — the mask would be a client-side drawing decision that any resource pack
or packet sniffer could take off.

So the page is the authority. `CompendiumPagePayload` carries either a shelf, in which each entry is
an identifier and a level, or a list of resolved recipes, in which each slot is a list of stacks with
their levels. An ingredient written as a tag keeps its options and the browser cycles them once a
second, the way a recipe book does.

Both directions are indexed once from `RecipeManager` and rebuilt when a datapack reload changes how
many recipes there are. A keypress then costs a map lookup and the resolution of at most 64 displays,
never a sweep over every recipe in the game. The usage direction reads `PlacementInfo#ingredients`,
which every recipe type fills in, so it needs no case per recipe type; only the shape for drawing
does.

Pages are bounded on purpose: 1024 entries, 64 recipes, 16 slots, 16 options per slot, 16 places of
origin. A browser should not be a way to make a server send an arbitrarily large packet.

## The shadow

An undiscovered entry is drawn as the item model particle texture tinted to black. For a flat item
model that is the item icon itself, so the shadow has the real outline; for a block it is a block
face, so the shadow is a solid square. Where a model has no such texture the shadow falls back to a
plain black square.

The outline is deliberate. A shadow shaped like an ingot tells a player to go looking for an ingot.
A blank square tells them nothing, and an absent row tells them the recipe does not exist.

## What is not gated

The recipe. A player may look up anything, including a recipe made entirely of things they have never
seen — they will see the shape of it and nothing else. The one thing knowledge does gate outright is
**search**: a name cannot be typed before it is real, or the search field would be a way to ask the
server whether a given item exists.

## Tests

`KnowledgeGameTests`, twelve tests:

- holding discovers, working studies, and studying implies having discovered
- an item kept in the main hand long enough is studied, a glance at it is not, and a half-examined
  item put down stays half-examined
- an item carried across two inventory passes is studied, one carried across a single pass and put
  down stays discovered, and neither says anything out loud
- learning something queues exactly one toast line for it, at the level it reached, and the queue is
  capped and cleared when it is sent
- two players do not share one book
- knowledge survives being written out and read back, and an older save loads as an empty book
- a shelf shows unknown entries, and search refuses to match them until they are known
- every registered item stands on exactly one shelf
- the browser answers both directions for a real recipe
- a feather has no recipe and still has an origin: the chicken that drops it, the gravel flint is
  broken out of, and never the block that drops itself
- a recipe travels with the level of every stack in it, and changes when the player studies one
- pages stay inside the packet budget, and a request for something that does not exist answers
  nothing rather than failing
- every identifier in the written chain resolves to a registered item, so a rename cannot silently
  turn a note into a blank page
- the chain opens one note at a time: the first is readable at once, an unread note does not name its
  subject, and acting on a note marks it followed and opens the next one

The client test additionally closes and reopens the world to prove the save survives a restart.

## Open

- The compendium has no artwork of its own yet: the item is still the vanilla book, and every surface
  of the screen is drawn out of coloured rectangles. `textureRequirements.md` lists the eight GUI
  sprites that would fix it and what each one replaces.
- The toast reuses the vanilla recipe-toast background. A background of its own would suit a book
  made of bark better.
- Section 81 asks for measured properties — weight, hardness, melting point — in a studied entry.
  The data is already loaded by the material and item-weight datapack listeners; the detail pane that
  shows it is not written.
