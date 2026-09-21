# Texture requirements

Every Hardwrought item and block that still needs its own artwork, and what that artwork has to be.

Items marked **placeholder** are fully implemented and playable right now: their model points at an
existing vanilla sprite so nothing is invisible or missing in game. They are only visually wrong.

## How a placeholder is replaced

1. Put the finished PNG at the path in the table, for example
   `src/main/resources/assets/hardwrought/textures/item/iron_dagger.png`.
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

## Milestone 7: die behauene Werkbank (fertig, je Holzart)

Die aus einem Stamm gehauene Werkbank (`hardwrought:hewn_workbench`) ist ein Block, kein Item: dreizehn
Varianten, eine je Holzart. Das Modell bleibt ein normaler voller Stammblock; die bearbeitete Kante
ist Teil einer durchgehenden Seitentextur und keine aufgesetzte Modellschicht.

| Schicht | Y | Textur | Status |
| --- | --- | --- | --- |
| Stammseite | 0–16 | `hardwrought:block/hewn_workbench_side_<holz>` | Vanilla-Rinde mit unregelmaessig behauener Oberkante |
| Arbeitsflaeche (oben) | — | `hardwrought:block/hewn_workbench_top_<holz>` | eigene Vanilla-nahe Textur |

Umgesetzt sind zwei Blocktexturen je Holzart, insgesamt 26:

| Datei | Was es sein soll |
| --- | --- |
| `textures/block/hewn_workbench_top_<holz>.png` | Das klar erkennbare Rahmen- und 3x3-Rastermotiv der Vanilla-Werkbank, auf die Palette der jeweiligen Holzart umgefaerbt und am aeussersten Rand leicht mit deren Stirnholz verbunden. |
| `textures/block/hewn_workbench_side_<holz>.png` | Eine durchgehende Vanilla-Stammseite: fast vollstaendig Rinde, die nur an den obersten zwei bis vier Pixeln unregelmaessig in entrindetes Holz uebergeht. |

Drei Dinge weichen von den Regeln fuer Inventarsprites oben ab, weil es Blocktexturen sind:

- **Vollflaechig deckend**, keine Transparenz, keine freistehende Silhouette.
- Die komplette 16x16-Seite wird auf einer einzigen Modellflaeche dargestellt. Dadurch gibt es
  keine harte geometrische Naht oder schnurgerade helle Linie zwischen Rinde und Arbeitskante.
- Die Farben und die Hauptmaserung bleiben direkt an der jeweiligen Vanilla-Holzart orientiert.

Die dreizehn Modelle verweisen bei `"worked"` und `"top"` jeweils auf die Texturen ihrer eigenen
Holzart. Dadurch sieht eine Birkenbank weiterhin nach Birke und eine Fichtenbank nach Fichte aus.

## Not yet required

These are named in the specification but have no item in the code yet, so they need no artwork until
the milestone that adds them:

- War hammer, poleaxe, glaive, throwing weapons (specification section 30, listed there as later).
- Buckler and tower shield (section 31); only the vanilla shield has a profile so far.
- Cloth armor (section 36); there is no cloth armor item.
- Lantern, oil lamp, gas lamp and electric lamp of the lighting progression (section 20).
- Ventilation shafts, mechanical fans and gas detectors (sections 18.3 and 19).
