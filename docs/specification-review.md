# Abgleich mit der vollständigen Mechanik-Spezifikation

Stand: 17.09.2026. Grundlage ist
[Hardwrought_Full_Mechanics_Specification_v3.md](../Hardwrought_Full_Mechanics_Specification_v3.md).
Für die weitere Arbeit dient sie als detaillierte Mechanik- und Meilensteinbeschreibung;
der ursprüngliche Entwicklungsplan bleibt die übergeordnete Designreferenz.
Beispiele und als „possible/suggested“ bezeichnete Optionen sind keine endgültigen
Balancingwerte. Beide Originaldokumente bleiben erhalten.

## Ergebnis für das vorhandene Fundament

Phase 1 des alten Plans entspricht **Milestone 0 – Foundation** in Abschnitt 110.
Fabric, Minecraft 26.3, gemeinsame/clientseitige Quellensätze, Konfiguration,
Speicherung, Netzwerk, Debug-Werkzeuge und datengetriebene Materialien passen dazu.
Die vier Simulationstakte widersprechen der neuen Spezifikation nicht.

Das bisherige Materialformat war für die genannten Nichtmetalle und Baustoffe zu
eng: Ein Schmelzpunkt war immer erforderlich. Er ist jetzt optional. Die optionalen
Gruppen `thermal` und `structure` bilden Wärmeleitfähigkeit, Wärmekapazität,
Druckfestigkeit, Zugfestigkeit und Stützweite ab (Abschnitte 17 und 39).
Vorhandene Material-JSONs bleiben gültig. Fehlende Werte werden nicht erfunden.
Die tatsächlichen Wärme- und Statiksimulationen sind damit noch nicht implementiert.

## Verbindliche Leitplanken für die nächsten Module

| Spezifikationsabschnitte | Konsequenz für die Implementierung |
| --- | --- |
| 27–36 | Kampf schichtet auf Vanilla auf: Grundschaden, Rüstungspunkte und Frontprüfung bleiben bei Vanilla, Hardwrought besitzt Schadensart, Materialantwort, Durchschlag, Block, Parade und Deckungsbruch. Trefferzonen (35) sind keine Slot-Abdeckung und brauchen ein eigenes System. Waffen ohne Profil bleiben nutzbar. |
| 3–6, 25–26 | Survival und Hardcore teilen das Balancing. Wissen, reale Spielerfertigkeit und körperliche Anpassung bleiben getrennt. Keine gemeinsame XP-/Level-Sperre; Material-`tier` ist nur Metadaten. |
| 7–10, 14–16, 43 | Ausdauer bekommt serverseitige Einflüsse für Flüssigkeit, Ernährung, Schlaf, Verletzung, Körpertemperatur, Sauerstoff, Kondition und Last. Fehlende Systeme liefern zunächst klar bezeichnete neutrale Eingänge, keine erfundenen Simulationen. |
| 11 | Schlaf beschleunigt reale Spiel-/Simulationstakte. Ein bloßes Setzen der Tageszeit lässt Pflanzen, Feuer, Maschinen und Verderb nicht korrekt fortschreiten. Die Kopplung an Vanilla-Weltticks muss beim Schlafsystem umgesetzt und getestet werden. |
| 13, 17–19, 66–67 | Ein gemeinsames Umweltmodell für Atmosphäre, Temperatur, Feuchte und Luftbewegung; abgegrenzte Zellen/Räume/Regionen und Caches. Diagnose-Strings sind keine Gameplay-Datenschnittstelle. Später typisierte, serverseitige Messwerte verwenden. |
| 23, 68 | Wasser erhält endliches Volumen, Qualität und Grundwasser; nicht einfach das aktuelle Vanilla-Fluidlevel als vollständige Wassersimulation weiterverwenden. |
| 37–39, 53, 65, 78, 90 | Materialkonstanten von individueller Qualität, Erzgehalt, Reinheit, Temperatur, Verschleiß und Verzauberungskapazität trennen. Diese Zustände gehören später an Item, Maschine oder Weltzelle. |
| 41–49 | Keine festen Vanilla-Höhengrenzen oder ungeprüften Y-Indizes in neuen Systemen. Ziel −256 bis +1024 gehört zum Vertikalwelt-Prototyp. 1280 Blöcke bedeutet obere exklusive Grenze 1024, also höchster Block Y=1023; diese technische Interpretation vor der Worldgen-Umsetzung berücksichtigen. |
| 79–83 | Materialdefinitionen sind Serverwissen, kein automatisch freigeschaltetes Spielerwissen. Normale Clients erhalten später nur erlaubte Erkenntnisse; das jetzige Operator-Debug-HUD ist kein Kompendium. |
| 97–104 | Begegnungen zählen aktive Teilnehmer, nicht alle Online-Spieler. Maschinenzustände gehören auf den Server und frühe Maschinen pausieren in ungeladenen Chunks. Der globale Core-Takt allein ist keine Erlaubnis, entfernte Maschinen weiterzurechnen. |
| 105–108 | Begrenzte aktive Arbeitsmengen, Ereignisse, regionale Daten und niedrigere Frequenzen für entfernte relevante Systeme. Keine Vollwelt-/Luftblock-Scans; keine Chunks aus Tickjobs nachladen. Der Scheduler unterbricht überlange Jobs nicht automatisch. |
| 109 | Das Modullayout ist ein Vorschlag. Bestehende `core/config` und `core/networking` erfüllen die Rollen bereits. Fachmodule werden bei ihrer Umsetzung ergänzt; es braucht keine leeren Ordner für alle späteren Systeme. |

## Reihenfolge ab jetzt

Die detaillierte Reihenfolge aus Abschnitt 110 gilt als Arbeitsgrundlage:

1. **Milestone 1:** Ausdauer, Hydration, Ernährung, Traglast, Schlaf, Basistemperatur. Umgesetzt.
2. **Milestone 2:** Kampf mit Schadensarten, Blocken, Parieren und Rüstungsinteraktion. Umgesetzt.
3. **Milestone 3:** Umwelt mit Gasen, Feuer, Raumtemperatur und getragenem Licht. Umgesetzt.
4. **Milestone 4:** endliches Wasser, Qualität, Grundwasser und Verdunstung. Umgesetzt.
5. **Milestone 5–6:** Vertikalwelt, Geologie.
6. **Milestone 7–9:** Frühe Progression, Wissen, Schmieden.
7. **Milestone 10–15:** Mechanik, Öl/Chemie, Dampf, Strom, Jahreszeiten/Ökologie, Anpassung.
8. **Milestone 16–20:** Magie, Bauphysik, Multiplayer-Balance, Industrie, Arcane Engineering.
9. **Milestone 21:** Endgame, dessen genaue Ausgestaltung ausdrücklich noch offen ist.

Milestone 1 ist inzwischen als spielbarer Grundstand umgesetzt. Die Überlebenswerte werden
getrennt pro Spieler gespeichert und ausschließlich auf dem Server verändert. Gewicht und
Nährwerte besitzen Datapack-Definitionen; fehlende spätere Systeme wie Sauerstoff, Verletzungen,
Kondition und Wind bleiben ausdrücklich neutrale Eingänge. Das Schlafsystem beschleunigt echte
Weltticks proportional zum Anteil schlafender Spieler und setzt keine Tageszeit direkt.

Milestone 2 ist als spielbarer Grundstand umgesetzt und in [milestone-2.md](milestone-2.md)
beschrieben. Schadensarten, Waffenklassen, Rüstungsmaterialien und Schilde sind
Datapack-Definitionen; die Auflösung eines Treffers passiert ausschließlich auf dem Server.
Nicht enthaltene Teile der Abschnitte 30 bis 37 – Messer, Zweihänder, Stangenwaffen, Angriffsarten,
Brace, Trefferzonen und Fertigungsqualität – sind in der Milestone-Dokumentation benannt statt
durch erfundene Werte vorweggenommen.

Milestone 3 ist umgesetzt und in [milestone-3.md](milestone-3.md) beschrieben. Das Umweltmodell
arbeitet mit abgegrenzten Zellen, begrenzten Flutfüllungen und zwischengespeicherten periodischen
Aktualisierungen; es gibt keinen Vollwelt- oder Luftblock-Scan und kein Tickjob lädt Chunks nach.
Die Gas- und Temperaturkanäle der Diagnose liefern jetzt echte Messwerte, während Statik und
Erzregionen weiterhin ausdrücklich `unavailable` melden. Andere Systeme lesen den typisierten
`EnvironmentReading`, nicht die Diagnose-Strings. Feuchte, Luftbewegung als eigene Felder, Rauch
zwischen Räumen sowie echte Dunkelheit, Mondphasen und Dunkeladaption bleiben offen und sind in der
Milestone-Dokumentation benannt.

Milestone 4 ist umgesetzt und in [milestone-4.md](milestone-4.md) beschrieben. Die Leitplanke zu den
Abschnitten 23 und 68 verlangte, das Vanilla-Fluidlevel nicht als vollständige Wassersimulation
weiterzuverwenden. Genau das passiert auch nicht: die Quellenverdopplung ist entfernt, Wasserkörper
werden über begrenzte Flutfüllungen gemessen, Qualität ist eine eigene, datengetriebene Größe am
Behälter, und der Grundwasserspiegel ist regional aus dem Weltseed abgeleitet statt gespeichert.
Das Vanilla-Ausbreiten ist vollständig ersetzt: Wasser ist eine Menge in Millibucket, die fällt und
sich ausgleicht, und keine Zelle erzeugt neue. Wasserdurchtränkte Blöcke, der geschlossene
Wasserkreislauf, Bodenfeuchte, Filter und Pumpen bleiben offen.

## Prüfung der Anpassung

`gradlew.bat runGameTest` und `gradlew.bat runClientGameTest` sind erfolgreich. Alle 74
Server-GameTests bestanden: 16 für den Kampf einschließlich Parade, Block und Deckungsbruch an einem
real erhobenen Schild, 21 für die Umwelt einschließlich Raumerkennung an tatsächlich gebauten
Gehäusen: dichte Wände, durchlässige Öffnungen, echte Löcher, große geschlossene Räume, die
Kalibrierung der Atmung gegen Raumgröße und Lüftung sowie die Höhenschichtung der Gase nach
ihrem Gewicht. Dreizehn weitere prüfen Wasserqualität, Wasserkörper, Grundwasser, Behälterzustand und vor allem
die Mengenerhaltung der Wassersimulation. Zusätzlich zu den Fundamenttests werden Überlebens-Persistenz,
ungültige Speicherwerte, Nährwert- und Gewichtsdefinitionen sowie das HUD-Protokoll geprüft.
Der Clienttest mauert den Spieler zusätzlich in einen echten dichten Steinraum ein und prüft die
gesamte Umweltkette bis zur getragenen Fackel. Er hebt außerdem ein echtes Schild und prüft Deckungszustand und Paradefenster
über das reale Netzwerk. Er sendet die reale Schlafanfrage, bestätigt Schlafzustand und 100 TPS im
Einzelspieler, weckt den Spieler, bestätigt die Rückkehr auf 20 TPS und prüft weiterhin
Datapack-Reload, HUD, Tick-Pause sowie Speichern/Neuladen der Testwelt.
