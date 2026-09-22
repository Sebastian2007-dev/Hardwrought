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

### Die Oberfläche (noch ganz ohne Texturen)

`CompendiumScreen` zeichnet heute jede Fläche mit `fill` und `outline`: ein fast schwarzes Rechteck,
hellgraue Kästchen, ein blauer Balken für das ausgewählte Regal. Das ist lesbar und funktioniert,
aber es sieht aus wie ein Debug-Fenster und nicht wie das Buch, das der Spieler sich selbst gebunden
hat. Das ist der größte einzelne optische Gewinn, der im Mod noch zu holen ist.

Die Sprites gehören unter `src/main/resources/assets/hardwrought/textures/gui/sprites/compendium/`
und werden mit `graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h)` gezeichnet —
`ShadowItem` macht das bereits, der Weg ist also im Code schon vorhanden.

In Reihenfolge des Gewinns pro Aufwand:

| # | Datei | Größe | Was es ersetzt | Was es sein soll |
| --- | --- | --- | --- | --- |
| 1 | `page.png` | 32x32, Nine-Slice | die zwei `graphics.fill` in `extractBackground` | Die Doppelseite: dunkles, rauchgedunkeltes Rindenpapier mit sichtbarer Faser, links und rechts ein dickerer, abgegriffener Rand, in der Mitte eine Bindung aus Pflanzenschnur. Unregelmäßig, nirgends eine gerade Kante. |
| 2 | `slot.png` | 18x18 | den `fill` in `slot(...)` und `option(...)` | Ein einzelnes Fach: eine flach in das Papier gedrückte Vertiefung, innen eine Spur heller als die Seite. Die Helligkeit innen ist kein Geschmack, sondern Bedingung — siehe unten. |
| 3 | `half.png`, `half_hovered.png` | 32x32, Nine-Slice | `fill` und `outline` in `drawHalf` | Die zwei Felder der Startseite, 140x122 groß. Zwei aufgeschlagene Hälften desselben Buches; die überfahrene bekommt einen wärmeren Ton und eine deutlichere Kante, nicht einen Rahmen in einer anderen Farbe. |
| 4 | `tab.png`, `tab_selected.png` | 16x18, Nine-Slice | den `fill` in `drawTabs` | Die zehn Regalreiter, 82x18. Eingelegte Lederlaschen oder eingekerbte Papierzungen am linken Rand. Der ausgewählte steht nach rechts über und ist heller, der Rest liegt zurück. |
| 5 | `card.png` | 32x32, Nine-Slice | den `fill` in `drawCard` | Die Rezeptkarte, 240x58. Ein mit Kohle grob umrandetes Feld auf der Seite, keine gefüllte Box. |
| 6 | `emblem_knowledge.png` | 32x32 | `drawGridEmblem` | Das Zeichen der Wissenshälfte. Heute neun gezeichnete Kästchen; als Sprite ein mit der Hand gezogenes 3x3-Raster, wie jemand es auf einen Rand kritzelt. |
| 7 | `emblem_thoughts.png` | 32x32 | `drawWritingEmblem` | Das Zeichen der Gedankenhälfte. Heute fünf Balken; als Sprite fünf Zeilen unleserlicher Handschrift mit ungleichen Enden. |
| 8 | `followed.png` | 9x9 | nichts — kommt neben `gui.hardwrought.compendium.followed` | Ein nachträglich hingesetzter Haken in derselben Tinte wie der Text, leicht schief. |

Nine-Slice heißt: eine kleine Kachel, die der Renderer auf die gebrauchte Größe zieht, ohne die Ecken
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
- **Die Seite bleibt dunkel.** Das ist eine Entscheidung und keine Bequemlichkeit: ein hell
  leuchtendes Blatt Papier mitten in einer Nacht unter Tage blendet, der Rest der Oberfläche des Mods
  ist dunkel, und vor allem hängt der schwarze Schatten daran. Als Bereich: warmes, sehr dunkles
  Braungrau um `#1E1A17`, die Vertiefung eines Fachs eine Stufe heller um `#4E4E4E`, Text bleibt
  `#E0E0E0` und `#9A9A9A`.
- **Der schwarze Schatten muss lesbar bleiben.** Eine nie gehaltene Sache wird als ihre eigene
  Silhouette in reinem Schwarz gezeichnet. Wird der Boden eines Fachs dunkler als etwa `#3A3A3A`,
  verschwindet der Schatten darin und die halbe Idee von Milestone 8 ist weg. Das Fach ist damit die
  einzige Fläche im ganzen Buch, deren Helligkeit nicht verhandelbar ist.
- Kein Farbstich, der nach Fantasy aussieht: keine Blautöne, kein Gold, keine Leuchtkanten.
- Wenn die Seite doch einmal hell werden soll, sind `TEXT_COLOR`, `FADED_COLOR`, `SLOT_COLOR` und die
  schwarze Silhouette in `ShadowItem` alle mit zu ändern. Das ist eine Umstellung, kein Austausch
  einer Datei.

### Optional: eine Handschrift für die Gedanken

Die Gedankenhälfte ist der einzige Ort im Mod mit längerem Fließtext, und sie ist als etwas gemeint,
das der Spieler selbst geschrieben hat. Eine eigene Schriftart unter
`assets/hardwrought/font/journal.json` würde dafür mehr tun als jede Panel-Textur — dieselbe
Zeichenbreite wie Vanilla, aber mit ungleichmäßiger Grundlinie und leicht schiefen Buchstaben.

Das ist ein ganzer Zeichensatz Arbeit und braucht auch Code (`Style.withFont`), gehört also hinter
alles andere auf dieser Liste. Aufgeschrieben, weil es der Punkt ist, an dem die Hälfte aufhören
würde, wie ein Tooltip auszusehen.

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
