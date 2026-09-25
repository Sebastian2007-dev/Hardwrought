# Werkbank-Stufen: Assets und Aussehen

Die Leiter aus [`BenchTier`](../src/main/java/de/ipnats/hardwrought/progression/BenchTier.java)
hat eine sichtbare Seite. Dieses Dokument sagt, was gezeichnet werden muss und wie es aussehen
soll — die Prompt-Blöcke sind auf Englisch, weil die Imagegen-Werkzeuge damit gefüttert werden.

## Die Erzählung, an der sich alles ausrichtet

Jede Stufe ist ein Satz über die Person, die daran arbeitet:

| Stufe | Block | Der Satz |
| --- | --- | --- |
| 0 | keiner, die Hände | „Ich habe nichts als das, was ich tragen kann." |
| 1 | Hewn Workbench | „Ich habe einen Baum gefunden und ihn flach gehackt." |
| 2 | *noch zu bauen* | „Ich habe Bretter gespalten und sie zusammengenagelt." |
| 3 | später, 4×4 | „Ich habe eine Werkstatt." |

Der visuelle Faden ist **Verbindungstechnik**, nicht Material. Stufe 1 ist gar nicht verbunden,
sie ist aus einem Stück gehackt. Stufe 2 ist zusammengefügt, aber sichtbar behelfsmäßig — schief,
gebunden, genagelt. Stufe 3 ist gezimmert. Wer das Bild sieht, soll die Stufe erraten können,
ohne den Namen zu lesen.

Wichtig für Stufe 2: sie soll **erkennbar an die Vanilla-Werkbank erinnern** — gleiche Silhouette,
gleiche Lesart „hier wird gearbeitet" — aber unsauberer. Kein Werkzeugbrett an der Seite, keine
sauber gefrästen Kanten. Es ist die Vanilla-Werkbank, gebaut von jemandem, der zum ersten Mal
etwas zusammennagelt.

---

## Stufe 2 — Nailed Workbench

### Dateien

| Pfad | Was |
| --- | --- |
| `textures/block/nailed_workbench_top_<wood>.png` | exakt die Arbeitsfläche der Hewn Workbench, 13 Varianten |
| `textures/block/nailed_workbench_side_<wood>.png` | tiefer behauene Stammseite, 13 Varianten |
| `textures/block/nailed_workbench_front_<wood>.png` | dieselbe Stammseite mit dezenten Nagelköpfen, 13 Varianten |
| `textures/block/nailed_workbench_bottom_<wood>.png` | originale Stirnfläche des jeweiligen Stamms, 13 Varianten |
| `models/block/nailed_workbench_<wood>.json` | 13 ausgerichtete Vollwürfelmodelle |
| `blockstates/nailed_workbench.json` | `wood` und `facing`, 52 Kombinationen |
| `items/nailed_workbench.json` | Itemmodell wählt anhand des `wood`-Blockstates |

Vier Flächen pro Holzart. Alles andere ist JSON und kostet keine Zeichenarbeit — siehe Abschnitt
„Modelle" unten.

Das Upgrade übernimmt die Holzart der Hewn Workbench. Oak, Spruce, Birch, Jungle, Acacia,
Dark Oak, Pale Oak, Poplar, Mangrove, Cherry, Bamboo, Crimson und Warped brauchen jeweils Top,
Seite, Front und Unterseite: 13 zusammengehörige Sätze beziehungsweise 52 Texturen. Als Quelle
dienen direkt die vorhandenen `hewn_workbench_top_*`- und `hewn_workbench_side_*`-Texturen. Dadurch
bleiben Farbe, Körnung und grobe Bearbeitung sichtbar dieselbe Werkbankfamilie. Die zweite Stufe ist
weiterhin **derselbe Stamm** und keine Kiste aus Brettern: Er wird tiefer und kontrollierter behauen,
und nur die kleinen Nagelköpfe zeigen die verbesserte Verbindungstechnik.

### Beschreibung

**Oben.** Pixelgleich zur Oberseite der Hewn Workbench. Das Upgrade verändert nicht die bereits
brauchbare Arbeitsfläche, sondern die Bearbeitung und Verbindung des Stamms darunter.

**Seite.** Eine durchgehende Stammseite wie bei der Hewn Workbench. Rinde bleibt in der unteren
Hälfte sichtbar; die unregelmäßige entrindete und abgeflachte Zone greift nun etwa sieben bis neun
Pixel tief statt nur zwei bis vier. Kurze dunkle Beilkerben mit einzelnen hellen Schnittkanten und
kleine Ausbrüche an der Übergangslinie zeigen die stärkere Bearbeitung. Keine Brettfugen, kein
Querriegel und kein Plankenmosaik.

**Front.** Derselbe Aufbau wie die Seite. Drei kleine, runde geschmiedete Nagelköpfe sitzen
unregelmäßig in der behauenen Zone. Ein einzelner kühler Lichtpunkt und eine dunkle Unterkante
lassen sie als gehämmertes Eisen statt als schwarze Löcher erkennen. Die asymmetrische Anordnung
bildet kein Gesicht und unterscheidet die genagelte Stufe, ohne den Stammcharakter zu überdecken.
Kein Eisenband und keine Zwinge.

**Unten.** Unveränderte Stirnfläche des ursprünglichen Logs beziehungsweise Stems. Sie zeigt klar,
aus welcher Holzart die Werkbank gefertigt wurde.

### Prompt

```text
Use case: precise-object-edit
Asset type: source artwork for thirteen matching sets of Minecraft-compatible 16x16 block textures
Input images: each timber's Hewn Workbench side and top plus the matching vanilla log, stripped-log
and log-top textures.
Primary request: Evolve the Hewn Workbench without replacing its single-log construction. Preserve
the Hewn top exactly. Preserve the source log's end grain as the bottom. On side and front, retain a
continuous bark-covered log but extend the irregular stripped and flattened zone from the top to
roughly half the face. Add readable axe and chisel marks. The front adds only three subtle dark iron
nail heads inside the worked zone.
Style/medium: crisp hand-placed pixel art compatible with modern vanilla Minecraft.
Composition/framing: exact orthographic square, full-bleed texture, no margin, no perspective.
Color palette: the exact vanilla palette of each source timber; nail heads in dark desaturated iron.
Constraints: four separate 16x16 faces per timber, 52 textures total, fully opaque, hard pixel edges;
no plank mosaic; no crafting-table grid; no brace; no metal band; no text; no symbols; no external
border or shadow; no watermark; no tools lying on the surface.
Avoid: generic oak replacing the source timber, fantasy wood, smooth painting, photorealism, blur,
anti-aliasing, saturated colour, glow, drawers, handles, sawblades, anvils or machinery on the block.
```

---

## Stufe 3 — Richtung, noch nicht festgelegt

Sobald das 4×4-Raster steht, braucht Stufe 3 dieselbe Behandlung. Der Satz dafür ist „ich habe
eine Werkstatt", und der visuelle Sprung ist **von behelfsmäßig zu absichtlich**: gehobelte
Bretter statt gespaltener, gezapfte statt genagelter Verbindungen, eine Schraubzwinge oder ein
Werkzeugbrett als Silhouettenbruch.

Das ist die Stelle, an der ein Vollwürfel vermutlich nicht mehr reicht — eine Bank mit Beinen
und einer überstehenden Platte liest sich sofort als größer. Dafür siehe unten.

---

## Modelle: das ist kein Blockbench-Problem

Die Hewn Workbench sieht aus wie ein von Hand modellierter Block, ist aber
[schlicht ein Würfel in JSON](../src/main/resources/assets/hardwrought/models/block/hewn_workbench_oak.json):

```json
"elements": [
  { "from": [0, 0, 0], "to": [16, 16, 16], "faces": { ... } }
]
```

Blockbench schreibt genau diese Datei — es ist eine Oberfläche über dem JSON, kein eigenes Format.
Für jeden würfelförmigen Block muss niemand Blockbench öffnen; die Datei ist zwölf Zeilen lang und
lässt sich direkt schreiben.

Für Stufe 2 heißt das: **null Modellierarbeit.** Vier Texturen, ein Würfel, fertig.

Für Formen, die kein Würfel sind, gilt dasselbe eine Ebene höher: jedes `element` ist ein Quader
mit `from`, `to` und sechs Flächen. Eine Bank mit vier Beinen und einer überstehenden Platte sind
fünf solche Quader. Das ist zwar mühsam von Hand zu erraten, aber es ist vollständig beschreibbar —
wer die Maße nennt, bekommt das JSON.

Der bestehende Weg im Repo ist ohnehin ein anderer und besser: die Texturen entstehen nicht per
Hand, sondern über ein Java-Programm in [`tools/`](../tools), das Vanilla-Sprites einliest und
gezielt verändert. `NailedWorkbenchTextureGenerator.java` übernimmt die Hewn-Oberseite pixelgleich,
nimmt die echte Log-Stirnfläche als Unterseite und erzeugt aus Rinde und entrindertem Holz die tiefer
behauenen Seiten samt Werkzeugspuren und dezenten Nagelköpfen. Deterministisch, wiederholbar und
nachjustierbar, ohne die 13 Holzarten einzeln neu zu zeichnen.
