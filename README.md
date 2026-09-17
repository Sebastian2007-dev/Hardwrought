# Hardwrought

Fabric-Grundgeruest fuer die Hardcore-Total-Conversion aus
[dem Entwicklungsplan](Hardcore_Minecraft_Total_Conversion_Development_Plan.md).

## Entwicklungsumgebung

Vorhandene Versionen: Minecraft **26.3**, Fabric Loader **0.19.5**,
Fabric API **0.160.7+26.3**, Gradle **9.5.1**. Loom verwendet weiterhin
**1.17-SNAPSHOT**; vor einer Veroeffentlichung eine stabile Version festlegen.
Benötigt wird ein **JDK ab Version 25**, Zielbytecode ist Java 25.

In IntelliJ diesen Ordner als Gradle-Projekt oeffnen. Unter
Settings > Build, Execution, Deployment > Build Tools > Gradle die Gradle JVM
auf JDK 25 oder neuer setzen und das Gradle-Projekt neu laden.
Auf diesem Rechner ist `C:\Users\ErnstGuenther\.jdks\openjdk-26` vorhanden.
Die Java-Toolchain in `build.gradle` verwendet bei einer aelteren Gradle-JVM
automatisch JDK 26 fuer Compiler und Spielstart. Laeuft Gradle bereits unter
Java 25 oder neuer, wird dessen Java-Hauptversion verwendet.
Nach Aenderungen an `build.gradle` in IntelliJ das Gradle-Projekt neu laden.

## Bauen und starten (Windows)

Im Projektordner:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 build
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 client
```

Das Skript sucht ein geeignetes JDK in JAVA_HOME, `%USERPROFILE%\.jdks` und
im PATH. JAVA_HOME wird nur fuer diesen Aufruf gesetzt. Die ExecutionPolicy
gilt nur fuer den gestarteten PowerShell-Prozess.
Der erste Build braucht Internet fuer Gradle und die Abhaengigkeiten.
Die fertige Mod liegt in `build/libs/hardwrought-1.0.0.jar`.
Die `-sources.jar` ist nur fuer Entwickler.

Weitere Aufgaben: `server` startet den Entwicklungsserver, `sources` bereitet
Minecraft-Quellen fuer die IDE vor, `datagen` startet die Daten-Generierung.
Beim ersten Serverstart muss die Minecraft-EULA selbst gelesen und in
`run/eula.txt` akzeptiert werden.
Alternativ bei korrekt gesetztem JAVA_HOME: `./gradlew.bat build` bzw.
`./gradlew.bat runClient` (Linux/macOS: `./gradlew`).

## Erster Test im Spiel

Eine Testwelt mit aktivierten Cheats oeffnen und eingeben:

```mcfunction
/give @s hardwrought:flint_shard
```

Der Feuersteinsplitter hat deutsche/englische Namen und verwendet vorlaeufig
die Vanilla-Feuersteintextur. Er ist eine Vorlage fuer weitere Items und hat
noch kein Rezept und keine eigene Spielmechanik.

Milestone 1 ist spielbar: Gruene Ausdauertropfen stehen ueber den Herzen, blaue
Wassertropfen ueber der Hungerleiste; unter Wasser verschwinden sie zugunsten der
Vanilla-Atemblasen. `H` blendet oben links die duenne
Muedigkeitsleiste und die Temperaturanzeige ein oder aus. Mit `V` schlaeft der Spieler auf
einer sicheren, trockenen Flaeche oder wacht wieder auf. Der Wasserschlauch ist
herstellbar oder direkt testbar:

```mcfunction
/give @s hardwrought:filled_waterskin
```

Er enthaelt acht Portionen. Zum Auffuellen an einer Wasserquelle schleichen und
den Wasserschlauch benutzen. Getrunkene Wasserflaschen erhoehen ebenfalls den Wasserwert.

Beim ersten Start entsteht `run/config/hardwrought.properties`.
`debugLogging=true` aktiviert zusaetzliche Registrierungsausgaben nach einem Neustart.
In normalen Installationen liegt die Datei im jeweiligen `config`-Ordner.

## Wo programmieren?

Alle gemeinsamen Java-Pfade beginnen mit `src/main/java/de/ipnats/hardwrought/`.

| Bereich | Einstieg |
| --- | --- |
| Mod-Start | `Hardwrought.java` |
| Neue Items | `core/registry/ModItems.java` |
| Konfiguration | `core/config/HardwroughtConfig.java` |
| Rendering und HUD | `src/client/java/de/ipnats/hardwrought/client/HardwroughtClient.java` |
| Datagen-Provider | `src/client/java/de/ipnats/hardwrought/client/HardwroughtDataGenerator.java` |
| Sprache und Modelle | `src/main/resources/assets/hardwrought/` |

Neue Items in `ModItems` registrieren und Itemdefinition, Modell und
Sprachschluessel nach dem Beispiel `flint_shard` anlegen.
Siehe auch [Fabric: erste Items](https://docs.fabricmc.net/develop/items/first-item).

Gemeinsamer Code darf keine Klassen aus `net.minecraft.client` importieren.
Spielentscheidungen wie Ausdauer, Inventare und Fortschritt gehoeren auf den
logischen Server. Der Client stellt die Ergebnisse dar. Konfiguration wird
bislang lokal geladen und noch nicht zwischen Server und Client synchronisiert.

## Phase 1: Technisches Fundament

Der Core besitzt jetzt serverseitige Lebenszyklen, Welt-Speicherung, einen
Scheduler mit vier Taktstufen, Datapack-Materialien und ein synchronisiertes Debug-HUD.
Details, Erweiterungsbeispiele und Testbefehle stehen in [docs/phase-1.md](docs/phase-1.md).

In einer Welt mit Cheats bzw. als Operator:

```mcfunction
/hardwrought debug on
/hardwrought status
/hardwrought materials
/hardwrought profile reset
/hardwrought debug off
```

Das HUD zeigt Biome, Vanilla-Temperatur, Fluessigkeitsstand und Simulation-Profiling.
Gas-, Statik- und Erzregionen-Diagnosen besitzen Erweiterungspunkte. Solange die
zugehoerigen spaeteren Spielsysteme fehlen, melden sie ausdruecklich `unavailable`.
Es werden keine fiktiven Messwerte oder noch nicht vorhandenen Simulationen angezeigt.

## Milestone 1: Ueberleben

Ausdauer, Hydration, Ernaehrung, Traglast, Schlaf und Basistemperatur sind als
serverseitige, gespeicherte Systeme umgesetzt. Formeln, Bedienung und
Erweiterungspunkte stehen in [docs/milestone-1.md](docs/milestone-1.md).

## Naechste Schritte

Die detaillierte Arbeitsgrundlage ist jetzt die
[vollstaendige Mechanik-Spezifikation](Hardwrought_Full_Mechanics_Specification.md).
Der [Abgleich mit dem Fundament](docs/specification-review.md) dokumentiert die
Anpassungen und Vorgaben fuer kommende Module.

1. Milestone 2: Kampf, Blocken, Parieren und Ruestungsinteraktion.
2. Milestone 3–6: Umwelt, endliches Wasser, Vertikalwelt und Geologie.
3. Weitere Schritte gemaess Abschnitt 110 der Mechanik-Spezifikation.

Der vorhandene GitHub-Workflow baut und startet die Server-GameTests bei Pushes und Pull Requests.
Im aktuellen Ordner ist noch kein Git-Repository initialisiert.

## Lizenz

Die vorhandene Projektlizenz ist CC0-1.0; siehe [LICENSE](LICENSE).
