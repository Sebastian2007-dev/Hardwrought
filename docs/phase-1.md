# Phase 1 – Technisches Fundament

Die gemeinsame Einstiegsklasse registriert Items, den Material-Loader, das
Netzwerkprotokoll, Server-Ereignisse und Debug-Befehle. Ein `CoreRuntime` wird
erst nach dem Laden der Welt angelegt und beim Serverende freigegeben.
Client-Code bleibt im separaten `src/client`-Quellensatz.

## Aufbau

| Plan | Implementierung |
| --- | --- |
| 1.1 Core | `registry`, `config`, `events`, `save`, `networking`, `simulation`, `utilities`; zusätzliche Diagnoseklassen in `debug` |
| 1.2 Server Authority | Ein Runtime pro logischem Server; Thread-Prüfungen auch für gehaltene Service-Referenzen; unveränderliche Materialtabellen; ausschließlich S2C-Diagnosepakete |
| 1.3 Daten statt Hardcode | Validierte Material-JSONs in Datapacks; Vanilla-Prioritäten und `/reload`; atomarer Austausch mit Fabric DataResourceStore |
| 1.4 Simulation | CRITICAL/FAST/MEDIUM/SLOW, fortlaufender gespeicherter Tick, Fehlerisolierung und Messung pro Aufgabe |
| 1.5 Debug | Operator-Befehle, Server-Snapshots zum HUD, sechs Diagnosekanäle, Profiling je Taktstufe und Aufgabe |

## Server, Speicherung und Netzwerk

`CoreLifecycle.require(server)` liefert den Core ausschließlich auf dessen Serverthread.
`CoreEvents.SERVER_READY` ist der Einstieg für weitere Module. Keine veränderlichen
Spielzustände in statischen Client-Feldern ablegen. Das HUD speichert lediglich die
zuletzt empfangenen Textzeilen und leert sie beim Verlassen einer Welt.

Der globale Simulationszähler wird über Minecraft `SavedData` im Weltordner unter
`data/hardwrought/core.dat` gespeichert, einschließlich Formatversion 1.
Vanilla übernimmt Autosave, `/save-all` und Speichern beim Beenden.
Das Codec lehnt negative Zeiten und unbekannte Formatversionen ab. Neue Formate
benötigen eine ausdrückliche Migration; die Datei nicht manuell bearbeiten.
Das ist die erste Anwendung der Speicherbasis. Individuelle Spielerdaten folgen
mit Ausdauer und Fortschritt; dafür existieren noch keine fertigen Datenmodelle.

`debug_snapshot_v1` überträgt maximal 32 Zeilen mit jeweils 240 Zeichen.
Der Server prüft die Spielleiter-Berechtigung beim Aktivieren und bei jeder
periodischen Aktualisierung. Ohne passenden Client-Empfänger sendet er nichts.
Der Client hat keinen Mod-Paketkanal zum Ändern von Welt- oder Spielerzuständen.
Neue Netzwerkanforderungen erhalten eigene typisierte Payloads mit Limits und
serverseitiger Validierung; Spielberechnungen gehören nicht in Paket-Callbacks des Clients.

## Simulation erweitern

| Stufe | Intervall in Spielticks | Vorgesehene Anwendungen |
| --- | --- | --- |
| CRITICAL | 1 | Kampf, Bewegung |
| FAST | 5 | Maschinen, Flüssigkeiten |
| MEDIUM | 20 | Temperatur, Atmosphäre; aktuell Debug-Synchronisierung |
| SLOW | 200 | Jahreszeiten, Boden, Ökologie |

Während der Initialisierung eines Moduls:

```java
CoreEvents.SERVER_READY.register(runtime -> {
    runtime.scheduler().register("hardwrought:my_system", SimulationTier.FAST, () -> {
        // Eine begrenzte Menge bereits aktiver Objekte auf dem Server bearbeiten.
    });
});
```

Aufgaben werden vor dem ersten Servertick registriert; doppelte IDs werden abgewiesen.
Der Scheduler folgt Spielticks und respektiert `/tick freeze`. Er holt beim Laden
keine Offline-Zeit nach. Persistierte Ticks erhalten die Intervallphase bei Neustarts.
Eine Aufgabe mit Laufzeitfehler wird bis zum Neustart deaktiviert und einmal geloggt;
andere Aufgaben laufen weiter. Gemessen werden Aufrufe, Fehler, Gesamtzeit, Mittelwert
und Maximum. `profile reset` setzt Messwerte zurück, reaktiviert aber keine Fehlerquellen.

Es gibt keine asynchrone Weltmutation und keine automatische Unterbrechung langer Jobs.
Jedes Modul muss seine Arbeit selbst begrenzen, etwa durch eine Warteschlange mit einer
festen Zahl von Objekten pro Aufruf. Keine Vollwelt-Scans oder Chunk-Generierung in Tickjobs.

## Material-Datapacks

Beispiele liegen in `src/main/resources/data/hardwrought/hardwrought/materials/`.
Die erste `hardwrought`-Komponente ist der Namespace, die zweite gehört zum eigenen
Datenverzeichnis `hardwrought/materials`. So wird `copper.json` zur ID `hardwrought:copper`.

```json
{
  "tier": 1,
  "density_kg_m3": 8960,
  "melting_point_c": 1084.62
}
```

Wertebereiche: Tier 0–100, Dichte 1–100000 kg/m³, Schmelzpunkt −273,15–10000 °C.
`tier` ist optionale Klassifikations-Metadaten (Standard 0), keine Spielerlevel-Sperre.
`melting_point_c` ist optional: Bei Materialien ohne definierten Schmelzpunkt wird
das Feld weggelassen. Bestehende JSONs bleiben gültig.

Für die detaillierte Mechanik-Spezifikation stehen zwei optionale Gruppen bereit:

| Gruppe | Pflichtfelder innerhalb der angegebenen Gruppe | Einheit / Werte |
| --- | --- | --- |
| `thermal` | `conductivity_w_m_k`, `specific_heat_j_kg_k` | W/(m·K), J/(kg·K); endlich und größer als 0 |
| `structure` | `compression_strength_mpa`, `tension_strength_mpa`, `support_distance_blocks` | MPa, MPa, Blöcke; endlich und mindestens 0 |

Eine fehlende Gruppe bedeutet „noch nicht definiert“, nicht 0 oder unendliche
Tragfähigkeit. Ungültige oder unvollständige angegebene Gruppen brechen den Reload ab.
Diese Materialkonstanten sind getrennt von individueller Reinheit, Schmiedequalität,
Verschleiß und aktuellem Wärmezustand zu behandeln. Gewicht braucht zusätzlich die
Materialmenge bzw. das Volumen des Gegenstands; Dichte allein ist keine Item-Masse.

Die Beispiele sind Ausgangswerte fürs Balancing, noch keine Metallurgie-Simulation.
Materialdefinitionen erzeugen auch nicht automatisch Items oder Rezepte.

Ein Welt-Datapack zum Überschreiben von Kupfer:

```text
<Welt>/datapacks/mein-balancing/
  pack.mcmeta
  data/hardwrought/hardwrought/materials/copper.json
```

`pack.mcmeta` für die festgelegte Minecraft-Version 26.3:

```json
{
  "pack": {
    "description": "Hardwrought balancing",
    "min_format": [121, 0],
    "max_format": [121, 0]
  }
}
```

Mit `/datapack list available` prüfen, gegebenenfalls
`/datapack enable "file/mein-balancing" last` ausführen, anschließend `/reload`.
`/hardwrought materials` zeigt die aktive Tabelle, begrenzt auf 50 Einträge.
Ein syntaktisch oder fachlich ungültiges Material bricht den Reload ab; die bisherige
Tabelle bleibt erhalten. Wird eine Definition entfernt, verschwindet sie beim nächsten
erfolgreichen Reload, sofern kein niedriger priorisiertes Pack sie bereitstellt.

Neue Definitionstypen wie Research oder Maschinen folgen demselben Muster aus Codec,
Reload-Listener und typisiertem DataResourceStore-Key. Deren Gameplay-Schemata werden
erst in den zugehörigen Entwicklungsphasen festgelegt.

## Debug-Werkzeuge

Alle Befehle benötigen `COMMANDS_GAMEMASTER` (Cheats/Operator-Spielbefehle).

| Befehl | Ergebnis |
| --- | --- |
| `/hardwrought debug on` | Aktiviert das HUD für den aufrufenden Spieler, Aktualisierung alle 20 Spielticks |
| `/hardwrought debug off` | Deaktiviert und leert das HUD |
| `/hardwrought status` | Einmalige Diagnose an der Position der Befehlsquelle, inklusive Aufgabenzeiten; auch für Serverkonsole |
| `/hardwrought materials` | Aktive Materialdefinitionen |
| `/hardwrought profile reset` | Messwerte seit letztem Start/Reset zurücksetzen |

ENVIRONMENT zeigt Biome, freien Himmel und Regen; TEMPERATURE den Vanilla-Biomwert
(ausdrücklich **nicht Celsius**); WATER Flüssigkeit, Füllstand 0–8 und Quellstatus
am Fußblock. Das ist noch kein Grundwasser- oder Gasströmungsmodell.
GAS, STRUCTURE und ORE melden ohne registrierten Anbieter `unavailable`.
Ihre Simulationen gehören zu den späteren Phasen; die Phase-1-Diagnose kann deren
Messwerte bereits ohne Änderungen an Netzwerk oder HUD darstellen.

Ein späteres Modul registriert seinen Anbieter beim Serverstart:

```java
CoreEvents.SERVER_READY.register(runtime -> {
    runtime.diagnostics().register("hardwrought:atmosphere", DiagnosticRegistry.Channel.GAS,
        (level, pos) -> atmosphere.describeAt(level, pos));
});
```

Maximal 20 Anbieter, eindeutige IDs, begrenzte Snapshots. Nur geladene Chunks werden
abgefragt. Anbieterfehler erscheinen als Diagnosefehler, nicht als erfundene Werte.
Keine Welt-Scans oder Änderungen innerhalb der Anbieter ausführen.

## Tests

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTest
.\gradlew.bat runClientGameTest
```

`build` führt durch die Loom-Testkonfiguration auch die Server-GameTests aus.
Der Clienttest wird getrennt gestartet und benötigt eine grafische Umgebung.
Testwelten liegen ausschließlich unter `build/run/`; vorhandene Spielwelten im
normalen `run`-Ordner werden nicht verwendet. Der Clienttest erzeugt eine Welt mit
Cheats, prüft HUD/Netzwerk, Datapack-Reload, Tick-Pause und erneutes Laden der Welt.
Der Test-Quellensatz wird nicht in die ausgelieferte Mod-JAR aufgenommen.

Die Server-Tests prüfen Taktfrequenzen, Wiederaufnahme, Profiling, Fehlerisolierung,
Speicher-Codecs, Materialvalidierung, Zugriffsrechte, Thread-Grenzen, Paketlimits und
die Erweiterbarkeit der Diagnosekanäle. Protokolle liegen unter
`build/run/gameTest/logs/` und `build/run/clientGameTest/logs/`; der Clienttest erstellt
einen Screenshot unter `build/run/clientGameTest/screenshots/`.

API-Grundlagen: [Fabric Networking](https://docs.fabricmc.net/develop/networking),
[HUD](https://docs.fabricmc.net/develop/rendering/hud),
[GameTests](https://docs.fabricmc.net/develop/automatic-testing).
Die konkrete Implementierung wurde zusätzlich gegen die lokal vorhandenen 26.3-APIs kompiliert.

## Verifikation am 17.09.2026

`gradlew.bat build runClientGameTest` erfolgreich: acht eigene Server-Tests plus
der Fabric-Kontrolltest bestanden; der Clienttest bestand einschließlich gültigem
Datapack-Override, abgelehntem ungültigem Reload, Tick-Pause, HUD-Schaltung und
Speichern/Öffnen derselben Welt. Der gespeicherte Tick 62 wurde beim Wiederöffnen
geladen. Das HUD wurde zusätzlich anhand des erzeugten Screenshots geprüft.
Die Release-JAR enthält Core und Client-HUD sowie beide Materialdefinitionen,
aber keine Testklassen. Gemeinsamer Code importiert keine Client-Klassen.
