# Texture requirements

What Hardwrought still needs drawn, and what it has to be.

The resource audit of 24 September 2026 found **122 registered item IDs**. Every one has an item
definition, every referenced Hardwrought model exists, and every Hardwrought texture reference
resolves to a PNG. There are no technically missing item textures.

The remaining work is visual polish. Four machinery and driveline block items still render entirely
from vanilla block textures, while the thirst effect still uses a functional generated placeholder.
The dirt slab also uses a vanilla texture, deliberately: it is made from ordinary dirt.

| | Was fehlt | Aufwand |
| --- | --- | --- |
| 1 | Eigene Oberflächen für Welle, Kurbelkasten, Handkurbel und Starter Crusher | mehrere Block-Sprites; Modelle bestehen bereits |
| 2 | `mob_effect/thirst.png` — vorhanden, aber noch ein generierter Platzhalter | ein 18x18-Sprite |
| 3 | Das Kompendium samt Oberfläche | umgesetzt |
| 4 | Die Werkbank der zweiten Stufe | umgesetzt: 52 Block-Sprites für 13 Holzarten |
| 5 | Schmieden: Esse, Blasebalg, Handschuhe, Schamotte, feuerfester Ziegel | Werkzeugteile umgesetzt; übrige Platzhalter siehe Abschnitt „Schmieden“ |

Die Werkbank-Stufen haben ein eigenes Dokument, weil dort die Erzählung über die Stufen hinweg
zusammenhängen muss: [`art_source/bench_tiers_style_prompt.md`](art_source/bench_tiers_style_prompt.md).
Dort stehen die Dateipfade, die Beschreibung jeder Fläche, der fertige Imagegen-Prompt und warum
für einen würfelförmigen Block kein Blockbench nötig ist.

## How a placeholder is replaced

1. Put the finished PNG under `src/main/resources/assets/hardwrought/textures/item/` or
   `textures/block/`, depending on the model.
2. For a flat item sprite, point `models/item/<item>.json` at `hardwrought:item/<item>`.
3. For a block item, replace the relevant vanilla entries in the block model's `textures` object
   with `hardwrought:block/<texture>`. Its item definition can keep rendering the block model.
4. Nothing else changes. No registry, recipe or data-file change is needed.

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

## Milestone 8: das Kompendium (umgesetzt)

### Verbindliche Neuausrichtung der Startstufe

Das erste Kompendium ist noch kein ordentlich gebundenes Buch. Es besteht aus überlappenden
Blättern, Rindenstücken, Zweigen, Pflanzenfasern und zwei hellen Fetzen als ruhiger Schreibfläche.
Erst ein späteres Upgrade darf wie das saubere, mehrlagige Buchkonzept aussehen.

- Unbekannte Gegenstände bleiben ihre echten, schwarz eingefärbten Silhouetten — auch Blöcke.
  Es gibt kein allgemeines Fragezeichen- oder Würfel-Icon.
- Methodenreiter rendern echte Minecraft-ItemStacks: Werkbank, Truhe, Lagerfeuer, Schmiedetisch,
  Ofen und Holzblock. Sichtbar sind nur Reiter, hinter denen Rezepte oder Fundorte liegen.
- Text wird ohne Minecraft-Dropshadow auf heller, kontrastreicher Fläche gezeichnet.
- Titelbanner und alle außerhalb des Buches schwebenden Bedienelemente entfallen.
- Das polierte Buchkonzept ist die Vorlage für ein späteres Compendium-Upgrade, nicht für Stufe 1.

Das Kompendium (`hardwrought:compendium`) ist das Buch, in dem der Spieler nachschlägt, was er
herausgefunden hat. Es hat seit Milestone 8 zwei Hälften, zwischen denen die Startseite wählt:

- **Wissen** — der Rezeptbrowser mit den zehn Regalen. Was der Spieler noch nie in der Hand hatte,
  wird als sein eigener schwarzer Schatten gezeichnet und `???` genannt.
- **Gedanken** — eine kurze Kette handgeschriebener Notizen, die sagen, wo das nächste Problem liegt,
  ohne die Lösung zu verraten. Ein Eintrag, der noch nicht an der Reihe ist, ist unleserliches
  Gekritzel statt einer fehlenden Seite.

### Das Buch als Gegenstand (umgesetzt)

| Datei | Was es sein soll |
| --- | --- |
| `textures/item/compendium.png` | Ein abgegriffenes, selbstgebundenes Notizbuch: Blätter aus Rinde oder Lumpenpapier, mit Pflanzenschnur gebunden, deren Knoten am Rücken sichtbar ist. Gedeckte Braun- und Grautöne, kein Leder, kein Gold, keine Verzierung. Es soll aussehen, als hätte der Spieler es selbst aus dem gemacht, was er gefunden hat, und nicht, als hätte er es gekauft. |

Die 16x16-Textur liegt am angegebenen Pfad. Das Itemmodell verweist bereits auf
`hardwrought:item/compendium`; der frühere Vanilla-Buch-Platzhalter ist entfernt.

### Die Oberfläche

Umgesetzt. Alle unten aufgeführten Sprites sind vorhanden und werden von `CompendiumScreen`
verwendet. Die Tabelle bleibt als verbindliche Stil- und Regenerationsreferenz erhalten.

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
- **Der schwarze Schatten muss lesbar bleiben.** Eine nie gehaltene Sache wird als ihre echte
  Silhouette gezeichnet, ausdrücklich auch bei Blöcken. Das Fach bleibt hell genug dafür.
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

## Milestone 10: Antrieb und Starter Crusher (umgesetzt)

Die Blockmodelle und Itemdarstellungen verwenden nun eigene, Vanilla-nahe 16x16-Oberflaechen.
Erzeugt werden sie reproduzierbar von `tools/vanilla_style_block_textures.py`; die Stilquelle und
Vorschau liegen unter `art_source/machinery/`. Die Items rendern weiterhin ihre Blockmodelle und
brauchen deshalb keine separaten Item-Sprites.

| Modell | Derzeit verwendete Vanilla-Texturen | Was es sein soll |
| --- | --- | --- |
| `models/block/shaft.json` und `shaft_bar.json` | entrindeter Eichenstamm | Eine grob zugerichtete hoelzerne Welle: laengs laufende Werkzeugspuren, dunklere Stirnflaeche und klar erkennbare Lagerflaechen. Sie soll mechanisch bearbeitet aussehen, nicht wie ein duenner Baumstamm. |
| `models/block/crank_box.json` und `crank_box_turning.json` | Eichenbretter und entrindete Eiche | Ein schwerer hoelzerner Getriebekasten mit gezapften Brettern, dunklen Fugen und einer deutlich anderen Oberseite im laufenden Zustand. Keine moderne Metallverkleidung. |
| `models/block/hand_crank.json` und `hand_crank_handle.json` | entrindete Eiche, Eichenbretter und Fichte | Ein handgebautes Lager mit sichtbarer Holzachse, kantigem Kurbelarm und dunklem, abgegriffenem Griff. Lager, Arm und Griff muessen auf einen Blick getrennt lesbar sein. |
| `models/block/starter_crusher.json` | Eiche, Bruchstein und Schleifstein | Ein primitiver Backenbrecher aus schwerem Holzrahmen und zwei rauen Steinbacken. Der Einfuelltrichter braucht eine eigene dunkle Innenflaeche; die Backen sollen wie zwei gegeneinander arbeitende Teile und nicht wie eingesetzte Schleifsteine aussehen.|
| `models/block/cogwheel_gear.json` und `large_cogwheel_gear.json` | Fichtenbretter, entrindete Eiche | Hoelzerne Zahnraeder: Radscheibe mit sichtbarer Maserung quer zu den Zaehnen, eingesetzte Zaehne mit dunkleren Stirnflaechen, Nabe mit Keil. Das grosse Rad braucht zusaetzlich Speichen, damit es nicht wie eine volle Scheibe wirkt. Die Modelle werden aus `tools/machinery_assets.py` erzeugt. |
| `models/block/gearbox.json` | Fass-Seite und Fassboden | Ein geschlossener Holzkasten mit eisenbeschlagenen Kanten und runden Lagerbuchsen auf allen sechs Seiten. |
| `models/block/water_wheel_rim.json` | Eichen- und Fichtenbretter, entrindete Eiche | Ein Wasserrad drei Bloecke breit: nasse, dunkle Schaufelbretter, hellere Speichen, eisenbeschlagene Nabe. |
| `models/block/windmill_sails.json` und `windmill.json` | weisse Wolle, dunkler Eichenstamm und -bretter | Segeltuch mit sichtbaren Naehten und Reffleinen, dunkle Holzruten, ein Lagergehaeuse mit Dach. |
| `models/block/belt_strip.json`, `textures/item/belt.png` | braune Wolle; generiertes Icon | Ein Lederriemen: dunkles Leder mit hellerer Naht an den Kanten; das Item als zusammengerollter Riemen. |

Gemeinsame Regeln:

- Native 16x16-Blocktexturen, harte Pixelkanten und dieselbe gedeckte Palette wie Werkbaenke und
  Ziegelofen.
- Holz darf zur Eiche passen, muss aber durch Werkzeugspuren, Fugen und Lagerstellen klar als
  bearbeitetes Maschinenteil erkennbar sein.
- Bewegte und ruhende Zustaende unterscheiden sich durch die Stellung oder Oberflaeche des Bauteils,
  nicht durch Leuchten oder eine unpassende Signalfarbe.
- Quellen unter `art_source/machinery/` ablegen und einen reproduzierbaren Exporter unter `tools/`
  behalten.

## Spezifikation Milestone 9: Schmieden (Platzhalter)

Alles hier funktioniert im Spiel, aber die Grafiken sind generierte Platzhalter. Die Tabellen nennen
die Datei, das heutige Aussehen und was daraus werden soll.

### Werkzeugteile — 15 Item-Sprites (fertig)

Erzeugt von `tools/part_textures.py`: Vanilla-nahe, pixelgenaue Köpfe und Klingen ohne Holzstiele,
mit eigener Materialpalette für Eisen, Gold und Bronze. Der Stilentwurf und seine Vorgaben liegen
unter `art_source/smithing/`.

| Datei (`textures/item/`) | Was es sein soll |
| --- | --- |
| `iron_pickaxe_head.png` | Der gebogene Kopf der Eisenspitzhacke, mit Öhr in der Mitte, wo der Stiel hineinkommt. Schmiedespuren und eine etwas hellere, geschliffene Spitze an beiden Enden. |
| `iron_axe_head.png` | Axtkopf mit breiter Schneide und Öhr. Die Schneide heller angeschliffen als der Rücken. |
| `iron_shovel_head.png` | Das Schaufelblatt mit Tülle oben. |
| `iron_hoe_head.png` | Hackenblatt, rechtwinklig zur Tülle. |
| `iron_sword_blade.png` | Schwertklinge mit Angel (der Dorn, der ins Heft geht), noch ohne Parierstange und Griff. |
| `iron_dagger_blade.png` | Kurze Dolchklinge mit Angel. |
| `iron_greatsword_blade.png` | Lange, breite Klinge mit langer Angel. |
| `iron_halberd_head.png` | Hellebardenkopf: Beilblatt, Spitze und Haken, mit Tülle. |
| `gold_pickaxe_head.png` … `gold_sword_blade.png` (5) | Dieselben Formen in Gold; weicher und glänzender, weniger Schmiedespuren. |
| `bronze_pickaxe_head.png`, `bronze_axe_head.png` | In Bronze, passend zur vorhandenen Bronzespitzhacke und zum Bronzebeil. |

Wichtig für das Minispiel: Der Amboss liest die **Form** des Werkstücks aus genau diesen Texturen
(jeder nicht durchsichtige Pixel ist Metall). Ein Kopf sollte deshalb deutlich anders geformt sein als
ein Barren, sonst gibt es nichts zu schmieden — der Server lehnt Arbeit unter 6 abweichenden Pixeln ab.

### Glühen — nichts zu zeichnen

Heißes Metall wird über eine helle Graustufen-Kopie jeder Metalltextur dargestellt, die je nach
Temperatur rot, orange oder gelbweiß eingefärbt wird. Diese Kopien unter `textures/item/glow/`
werden von `tools/glow_textures.py` erzeugt. **Nach jeder neuen oder geänderten Metalltextur
(Roherz, Barren, Werkzeugteil) das Skript erneut ausführen**, sonst glüht die alte Form.

### Blöcke

| Modell | Heute | Was es sein soll |
| --- | --- | --- |
| `models/block/wooden_anvil.json` | Eichenstamm und entrindete Stirnseite | **Bleibt so.** Ein Hartholzklotz, Hirnholz oben. |
| `models/block/forge*.json` (4 Zustände: an/aus, ausgekleidet/nicht) | Vanilla-Ziegel, Schlammziegel, Magma und Kohleblock | Eine gemauerte Schmiedeesse mit eingelassenem Glutbett. Eigene Ziegelflächen für außen, eine eigene Glut-Oberfläche (aus: schwarze Kohle mit etwas Asche; an: glühende Kohle), und für den ausgekleideten Zustand ein helleres, feuerfestes Mauerwerk an der Innenkante. |
| `models/block/bellows.json` | Eichenbretter, braune Wolle, Eisenblock | Ein Blasebalg: zwei Holzplatten mit gefaltetem Leder dazwischen und einer Düse vorn. Das Leder soll wie Leder aussehen, nicht wie Wolle. |

### Items und Symbole

| Datei | Heute | Was es sein soll |
| --- | --- | --- |
| `textures/item/smithing_gloves.png` | generierte braune Faust | Dicke, genähte Lederhandschuhe mit Stulpe; angesengte Fingerspitzen wären ein schönes Detail. |
| `textures/gui/sprites/equipment/slot_gloves.png` | graue Silhouette derselben Form | Leeres-Slot-Symbol im Stil von `slot_backpack` und `slot_lamp`. |
| `textures/item/fireclay.png` | generierter grauer Klumpen | Ein Klumpen hellgrauer, körniger Ton mit sichtbarem Sand und Kies darin. |
| `textures/item/refractory_brick.png` | generierter gelblicher Ziegel | Ein feuerfester Schamotteziegel: blass gelbbraun, feinporig, heller als ein gewöhnlicher Ziegel. |

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
- **Die Erzpulver**, 25 Pulverformen fuer Vanilla-Erzmaterialien und Hardwrought-Metalle — erzeugt
  von `tools/PowderTextureExporter.java`, Quelle und Vorschau unter `art_source/powder_*`.
- **Bronzegemenge, Bronzenaegel und Hammer** besitzen eigene 16x16-Item-Sprites. Das Bronzegemenge
  verwendet dieselbe Pulversprache wie die Erzpulver.
- **Das Kompendium**, seine Buchoberflaeche und alle Bedienelemente — Quellen unter
  `art_source/compendium/`, Export ueber `tools/CompendiumTextureExporter.java`.
- **Die zweite Werkbank**, 52 Blocktexturen fuer 13 Holzarten — erzeugt von
  `tools/NailedWorkbenchTextureGenerator.java`, Quellen unter `art_source/bench_tiers/`.
- **Rucksaecke und getragene Leiste**, einschliesslich der leeren Rucksack- und Lampenslots.

## Technische Aufraeumarbeit, keine fehlende Textur

- `cobblestone_piece` verweist derzeit korrekt, aber mit historisch falsch geschriebenem Dateinamen
  auf `textures/item/cobblestone_pice.png`. Eine Umbenennung muss Datei und Modell gemeinsam aendern.
- Einige aeltere Werkzeugtexturen verwenden verkuerzte Namen wie `flint_h.png`, `iron_pick.png` und
  `stone_h.png`. Die Referenzen sind gueltig; eine Umbenennung waere nur Konsistenzpflege.
- `dirt_slab` verwendet absichtlich `minecraft:block/dirt` und braucht keine eigene Kopie derselben
  Textur.

## Not yet required

These are named in the specification but have no item in the code yet, so they need no artwork until
the milestone that adds them:

- War hammer, poleaxe, glaive, throwing weapons (specification section 30, listed there as later).
- Buckler and tower shield (section 31); only the vanilla shield has a profile so far.
- Cloth armor (section 36); there is no cloth armor item.
- Lantern, oil lamp, gas lamp and electric lamp of the lighting progression (section 20).
- Ventilation shafts, mechanical fans and gas detectors (sections 18.3 and 19).
