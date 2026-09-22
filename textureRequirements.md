# Texture requirements

What Hardwrought still needs drawn, and what it has to be.

The item and block art is essentially finished: of eighty-seven item models exactly one still points at
a vanilla sprite. What is missing now is not another icon — it is the **surface of the compendium**,
which is currently drawn entirely out of coloured rectangles and does not yet look like a book.

| | Was fehlt | Aufwand |
| --- | --- | --- |
| 1 | `item/compendium.png` — der einzige verbliebene Platzhalter im ganzen Mod | ein Sprite |
| 2 | Die Oberfläche des Kompendiums | acht GUI-Sprites, Code-Änderungen dabei beschrieben |
| 3 | `mob_effect/thirst.png` — ein generierter Platzhalter, spielbar aber nicht gezeichnet | ein Sprite |

## How a placeholder is replaced

1. Put the finished PNG at the path in the table, for example
   `src/main/resources/assets/hardwrought/textures/item/compendium.png`.
2. In `src/main/resources/assets/hardwrought/models/item/<item>.json` change the single line
   `"layer0"` from the vanilla path to `hardwrought:item/<item>`.
3. Nothing else changes. No Java, no recipe, no data file.

Keep the generated source image next to the others in `art_source/` so a later re-export does not
have to start from nothing.

## Shared rules for every sprite

- Native **16x16** square canvas so the inventory renderer does not discard detail while scaling;
  crisp hard pixel edges, no anti-aliased blur, no resampling softness.
- Transparent background, object centred, no text, no border, no drop shadow outside the sprite.
- Minecraft inventory item style: a strong, immediately readable silhouette that still works at
  16x16 in a hotbar.
- Muted, earthy palette. Hardwrought is a hardcore survival conversion, not a bright fantasy set.
  Avoid saturated primary colours and glow effects unless the item actually emits light.
- Diagonal items point from the lower left to the upper right, matching vanilla tools.

GUI sprites are a different job and follow their own rules; those are in the compendium section.

## Milestone 8: das Kompendium

Das Kompendium (`hardwrought:compendium`) ist das Buch, in dem der Spieler nachschlägt, was er
herausgefunden hat. Es hat seit Milestone 8 zwei Hälften, zwischen denen die Startseite wählt:

- **Wissen** — der Rezeptbrowser mit den zehn Regalen. Was der Spieler noch nie in der Hand hatte,
  wird als sein eigener schwarzer Schatten gezeichnet und `???` genannt.
- **Gedanken** — eine kurze Kette handgeschriebener Notizen, die sagen, wo das nächste Problem liegt,
  ohne die Lösung zu verraten. Ein Eintrag, der noch nicht an der Reihe ist, ist unleserliches
  Gekritzel statt einer fehlenden Seite.

### Das Buch als Gegenstand (Platzhalter)

| Datei | Was es sein soll |
| --- | --- |
| `textures/item/compendium.png` | Ein abgegriffenes, selbstgebundenes Notizbuch: Blätter aus Rinde oder Lumpenpapier, mit Pflanzenschnur gebunden, deren Knoten am Rücken sichtbar ist. Gedeckte Braun- und Grautöne, kein Leder, kein Gold, keine Verzierung. Es soll aussehen, als hätte der Spieler es selbst aus dem gemacht, was er gefunden hat, und nicht, als hätte er es gekauft. |

Wenn die Textur fertig ist, in `src/main/resources/assets/hardwrought/models/item/compendium.json`
die Zeile `"layer0"` von `minecraft:item/book` auf `hardwrought:item/compendium` ändern.

### Die Oberfläche

Die Oberfläche ist ein selbstgebundenes, vollständig aufgeschlagenes Buch. Die komplette Grundfläche
wird in ihrer tatsächlichen GUI-Größe gezeichnet; sie darf ausdrücklich nicht aus einer winzigen,
wiederholten Papierkachel entstehen. So bleiben Papierkanten, Seitenschatten und Bindung bewusst
gesetzt und Text liegt immer auf einer ruhigen Fläche.

Die Sprites gehören unter `src/main/resources/assets/hardwrought/textures/gui/sprites/compendium/`
und werden mit `graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h)` gezeichnet —
`ShadowItem` macht das bereits, der Weg ist also im Code schon vorhanden.

In Reihenfolge des Gewinns pro Aufwand:

| # | Datei | Größe | Was es ersetzt | Was es sein soll |
| --- | --- | --- | --- | --- |
| 1 | `book.png` | 344x204, feste Größe | der vollständige Hintergrund in `extractBackground` | Die gesamte Doppelseite samt Rindendeckel, ruhigem Lumpenpapier, Seitenschatten und Bindung aus Pflanzenschnur. Keine Skalierung und kein wiederholtes Muster. |
| 2 | `slot.png` | 18x18 | den `fill` in `slot(...)` und `option(...)` | Ein einzelnes Fach: eine flach in das Papier gedrückte Vertiefung, innen eine Spur heller als die Seite. Die Helligkeit innen ist kein Geschmack, sondern Bedingung — siehe unten. |
| 2a | `unknown_block.png` | 16x16 | massive schwarze Blockflächen | Ein neutraler, dunkler Würfel mit Fragezeichen. Block-Partikel füllen sonst den gesamten Slot und ergeben keine erkennbare Silhouette. |
| 3 | `half.png`, `half_hovered.png` | 32x32, Nine-Slice | `fill` und `outline` in `drawHalf` | Die zwei Felder der Startseite, 140x122 groß. Zwei aufgeschlagene Hälften desselben Buches; die überfahrene bekommt einen wärmeren Ton und eine deutlichere Kante, nicht einen Rahmen in einer anderen Farbe. |
| 4 | `tab.png`, `tab_selected.png` | 16x18, Nine-Slice | den `fill` in `drawTabs` | Die zehn Regalreiter, 82x18. Eingelegte Lederlaschen oder eingekerbte Papierzungen am linken Rand. Der ausgewählte steht nach rechts über und ist heller, der Rest liegt zurück. |
| 5 | `card.png` | 32x32, Nine-Slice | den `fill` in `drawCard` | Die Rezeptkarte, 240x58. Ein mit Kohle grob umrandetes Feld auf der Seite, keine gefüllte Box. |
| 6 | `emblem_knowledge.png` | 32x32 | `drawGridEmblem` | Das Zeichen der Wissenshälfte. Heute neun gezeichnete Kästchen; als Sprite ein mit der Hand gezogenes 3x3-Raster, wie jemand es auf einen Rand kritzelt. |
| 7 | `emblem_thoughts.png` | 32x32 | `drawWritingEmblem` | Das Zeichen der Gedankenhälfte. Heute fünf Balken; als Sprite fünf Zeilen unleserlicher Handschrift mit ungleichen Enden. |
| 8 | `followed.png` | 9x9 | nichts — kommt neben `gui.hardwrought.compendium.followed` | Ein nachträglich hingesetzter Haken in derselben Tinte wie der Text, leicht schief. |

Die vollständige Grundfläche `book.png` ist ausdrücklich **kein** Nine-Slice. Die kleineren
Bedienelemente dürfen Nine-Slices bleiben. Nine-Slice heißt: eine kleine Kachel, die der Renderer auf die gebrauchte Größe zieht, ohne die Ecken
zu verzerren. Dazu gehört neben jedem solchen PNG eine `.mcmeta` mit demselben Namen, so wie Vanilla
es macht:

```json
{
  "gui": {
    "scaling": {
      "type": "nine_slice",
      "width": 32,
      "height": 32,
      "border": 6
    }
  }
}
```

`width` und `height` sind die Maße des PNG, `border` die Breite des Randes, der nicht gestreckt wird.
`slot.png` und `followed.png` brauchen keine `.mcmeta`: sie werden immer in ihrer eigenen Größe
gezeichnet.

### Regeln für die GUI-Sprites

Sie weichen von den Inventarregeln oben ab, weil sie etwas anderes tun:

- **Vollflächig deckend**, keine freistehende Silhouette. Die Seite ist eine Fläche, kein Gegenstand.
- **Lesbarkeit geht vor Körnung.** Das Papier ist ein mittleres, warmes Ocker um `#B89D69`, Text
  nahezu schwarz. Fasern sind sparsame Einzelpixel und dürfen weder Buchstaben noch Itemkonturen
  überlagern. Bedienelemente müssen auch ohne Hover klar voneinander zu unterscheiden sein.
- **Der schwarze Schatten muss lesbar bleiben.** Eine nie gehaltene Sache wird als ihre eigene
  Silhouette gezeichnet. Bei Blöcken wäre das nur ein volles Quadrat; sie verwenden deshalb den
  neutralen unbekannten Würfel. Das Fach bleibt hell genug für beide Darstellungen.
- Kein Farbstich, der nach Fantasy aussieht: keine Blautöne, kein Gold, keine Leuchtkanten.
- `TEXT_COLOR`, `FADED_COLOR`, Slot und Papier werden immer gemeinsam abgestimmt. Dunkle Schrift
  braucht eine helle, ruhige Schreibfläche; der schwarze unbekannte Gegenstand braucht zusätzlich
  das noch hellere Slot-Inlay.

### Optional: eine Handschrift für die Gedanken

Die Gedankenhälfte ist der einzige Ort im Mod mit längerem Fließtext, und sie ist als etwas gemeint,
das der Spieler selbst geschrieben hat. Eine eigene Schriftart unter
`assets/hardwrought/font/journal.json` würde dafür mehr tun als jede Panel-Textur — dieselbe
Zeichenbreite wie Vanilla, aber mit ungleichmäßiger Grundlinie und leicht schiefen Buchstaben.

Das ist ein ganzer Zeichensatz Arbeit und braucht auch Code (`Style.withFont`), gehört also hinter
alles andere auf dieser Liste. Aufgeschrieben, weil es der Punkt ist, an dem die Hälfte aufhören
würde, wie ein Tooltip auszusehen.

## Milestone 9: die Rucksaecke (umgesetzt)

Beide Rucksaecke sind fertig implementiert und verwenden eigene 16x16-Sprites. Sie sind der
wichtigste Gegenstand im fruehen Spiel, weil ohne sie das Inventar zu bleibt.

| Datei | Was es sein soll |
| --- | --- |
| `textures/item/starter_backpack.png` | Ein aus Laubfasern geflochtener Tragebeutel: grobes, ungleichmaessiges Geflecht in Graugruen und Strohbraun, zwei Schlaufen als Traeger, kein Leder, keine Schnalle. Er soll aussehen, als haette ihn jemand am ersten Abend aus dem gemacht, was am Boden lag. |
| `textures/item/basic_backpack.png` | Derselbe Beutel, aber gefasst: dunkelbraunes, vernaehtes Leder ueber dem Geflecht, eine erkennbare Klappe mit einem Riemen darueber, seitlich eine aufgesetzte Tasche. Deutlich praller als der geflochtene. Gedeckte Erdtoene, kein Metallbeschlag, kein Gold. |

Beide folgen den Regeln fuer Inventarsprites oben. Wichtig ist der Unterschied auf einen Blick: die
zwei stehen in derselben Leiste nebeneinander und muessen auch bei 16x16 auseinanderzuhalten sein —
das Geflecht hell und offen, das Leder dunkel und geschlossen.

Die Modelle unter `models/item/starter_backpack.json` und `basic_backpack.json` verweisen auf die
jeweiligen Hardwrought-Texturen.

## Milestone 9: die getragene Leiste (umgesetzt)

Die getragene Ausruestung haengt als schmale Leiste an der linken Kante des Inventars und klappt ueber
einen kleinen Knopf auf und zu — dieselbe Form, die Curios-Mods benutzen. Lederriemen, Vertiefungen,
Knopfzustände und leere Slot-Symbole werden durch eigene Sprites gezeichnet.

Die Sprites gehoeren unter `src/main/resources/assets/hardwrought/textures/gui/sprites/equipment/`
und werden mit `graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h)` gezeichnet.

Die Masse stehen in `WornStrap` und sind die Wahrheit, gegen die gezeichnet wird: die Leiste ist
**30 px breit**, das Brett beginnt 32 px links neben dem Inventar, jedes Fach ist **18x18** mit 18 px
Abstand, der Knopf ist **14x14** und sitzt ueber dem Brett. Bei zwei Faechern ist das Brett 50 px hoch;
es waechst mit jedem weiteren Fach um 18 px, muss also in der Hoehe dehnbar sein.

| # | Datei | Groesse | Was es ersetzt | Was es sein soll |
| --- | --- | --- | --- | --- |
| 1 | `strap.png` | 30x32, Nine-Slice (Rand 6) | die zwei `fill` in `hardwrought$drawStrap` | Das Brett der Leiste: ein schmaler, senkrechter Lederriemen mit vernaehtem Rand, oben und unten abgerundet, in der Mitte dehnbar. Dunkles, abgegriffenes Braun, damit es an den Inventarrahmen anschliesst statt davor zu schweben. |
| 2 | `worn_slot.png` | 18x18 | den dritten `fill` in `hardwrought$drawStrap` | Ein Fach in der Leiste: eine in das Leder eingelassene Vertiefung mit genaehter Kante. Innen eine Spur heller als der Riemen. |
| 3 | `fold_button.png`, `fold_button_hovered.png` | 14x14 | die Vanilla-Knopftextur des Klappknopfs | Eine kleine Lederlasche mit einer eingepraegten Pfeilspitze. Der ueberfahrene Zustand ist eine Spur heller, kein anderer Farbton. |
| 4 | `fold_button_blocked.png` | 14x14 | denselben Knopf, wenn das Rezeptbuch den Platz belegt | Dieselbe Lasche, matt und ohne Pfeil — sie soll aussehen, als ginge sie gerade nicht, nicht als sei sie kaputt. |
| 5 | `slot_backpack.png` | 16x16 | nichts — neu, als Hinweis im leeren Fach | Die Umrisszeichnung eines Rucksacks, so wie Vanilla leere Ruestungsslots mit einem Helm- oder Stiefelumriss markiert. Ein Grauton, halbdurchsichtig, keine Fuellung. |
| 6 | `slot_lamp.png` | 16x16 | nichts — neu, als Hinweis im leeren Fach | Dasselbe fuer die Grubenlampe: Umriss einer Laterne mit Buegel, gleicher Grauton und gleiche Strichstaerke wie der Rucksackumriss. |

Die zwei leeren Umrisse (5 und 6) sind der groesste praktische Gewinn der Liste. Ein leeres Fach sagt
heute nichts darueber, was hineingehoert — und das Rucksackfach ist das einzige Feld im ganzen Spiel,
das darueber entscheidet, ob der Spieler ueberhaupt ein Inventar hat. Sie werden ueber
`Slot#getNoItemIcon` gesetzt, genau wie Vanilla es fuer Helm, Brustplatte und Stiefel macht, und
brauchen dafuer auch je einen Eintrag als Vanilla-Leerslot-Sprite.

Es gelten die **Regeln fuer GUI-Sprites** aus dem Kompendium-Abschnitt oben, mit einer Ergaenzung: die
Leiste haengt direkt am Vanilla-Inventarrahmen und muss zu ihm passen, nicht zum Kompendium. Also die
gedeckten Grau- und Brauntoene des Vanilla-Inventars aufgreifen statt des dunklen Buchpapiers.

## Milestone 4: das Durst-Symbol (Platzhalter)

Der Effekt `hardwrought:thirst` hat ein generiertes Platzhalter-Symbol: ein leerer Tropfen mit einem
Rest Wasser unten, aus vier Farben. Es ist spielbar und sichtbar, aber es ist kein gezeichnetes Icon.

Effektsymbole weichen von den Regeln für Inventarsprites oben ab:

| Datei | Was es sein soll |
| --- | --- |
| `textures/mob_effect/thirst.png` | **18x18**, nicht 16x16. Ein Symbol im Stil der Vanilla-Effektsymbole: klar lesbar in der winzigen Leiste neben der Erfahrungsanzeige, mit demselben leichten Volumen und derselben dunklen Unterkante wie Hunger oder Übelkeit. Motiv: ein fast leerer Wassertropfen oder eine aufgesprungene Lippe. Gedeckte Blaugrautöne passend zur Effektfarbe `#3A6B7C`, transparenter Hintergrund. |

## Fertig, nichts mehr zu tun

Diese Texturen liegen im Spiel und sind hier nur noch als Fundstelle verzeichnet, falls sie einmal
neu erzeugt werden müssen:

- **Die behauene Werkbank**, 26 Blocktexturen, zwei je Holzart — erzeugt von
  `tools/HewnWorkbenchTextureGenerator.java`, Quellen unter `art_source/hewn_workbench/`.
- **Der Ziegelofen**, vier Blocktexturen samt leuchtender Front — erzeugt von
  `tools/BrickFurnaceTextureExporter.java`, Quellen unter `art_source/brick_furnace/`.
- **Die Metalle**, Erz, Rohbrocken und Barren je Metall — erzeugt von
  `tools/MetalTextureGenerator.java`, Quellen unter `art_source/metals_*`.

## Not yet required

These are named in the specification but have no item in the code yet, so they need no artwork until
the milestone that adds them:

- War hammer, poleaxe, glaive, throwing weapons (specification section 30, listed there as later).
- Buckler and tower shield (section 31); only the vanilla shield has a profile so far.
- Cloth armor (section 36); there is no cloth armor item.
- Lantern, oil lamp, gas lamp and electric lamp of the lighting progression (section 20).
- Ventilation shafts, mechanical fans and gas detectors (sections 18.3 and 19).
