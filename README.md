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
Die Java-Toolchain in `build.gradle` verwendet bei einer aelteren Gradle-JVM
automatisch JDK 26 fuer Compiler und Spielstart; ein installiertes JDK 26 wird
ueber die Windows-Registry bzw. `~/.jdks` gefunden. Laeuft Gradle bereits unter
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
einer sicheren, trockenen Flaeche oder wacht wieder auf; das geht zu jeder Tageszeit, auch im Bett.
Niemand wird geweckt: wer nach dem Ausruhen liegen bleibt, sammelt Unruhe an. Der Wasserschlauch ist
herstellbar oder direkt testbar:

```mcfunction
/give @s hardwrought:filled_waterskin
```

Er enthaelt acht Portionen. Zum Auffuellen an einer Wasserquelle schleichen und
den Wasserschlauch benutzen. Getrunkene Wasserflaschen erhoehen ebenfalls den Wasserwert.

Beim ersten Start entsteht `run/config/hardwrought.properties`.
`debugLogging=true` aktiviert zusaetzliche Registrierungsausgaben nach einem Neustart.
`dynamicLight=false` schaltet das getragene Licht ab; es wird ausschliesslich in der
clienteigenen Weltkopie erzeugt und nie gespeichert.
In normalen Installationen liegt die Datei im jeweiligen `config`-Ordner.

## Wo programmieren?

Alle gemeinsamen Java-Pfade beginnen mit `src/main/java/de/ipnats/hardwrought/`.

| Bereich | Einstieg |
| --- | --- |
| Mod-Start | `Hardwrought.java` |
| Neue Items | `core/registry/ModItems.java` |
| Kampf, Waffen- und Ruestungswerte | `combat/CombatSystem.java` |
| Luft, Gase, Raumtemperatur | `environment/EnvironmentSystem.java` |
| Wasser, Qualitaet, Grundwasser | `water/WaterSystem.java` |
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

## Milestone 2: Kampf

Schadensarten (Schnitt/Stich/Wucht), Waffenklassen, Ruestungsmaterial, Blocken,
Parieren und Ausdauer im Kampf sind umgesetzt. Formeln, Werte und Grenzen stehen
in [docs/milestone-2.md](docs/milestone-2.md).

Zum Ausprobieren in einer Welt mit Cheats:

```mcfunction
/give @s minecraft:iron_sword
/give @s minecraft:mace
/give @s minecraft:shield
/give @s minecraft:iron_chestplate
/hardwrought combat
```

Gegen Plattenruestung richtet die Keule deutlich mehr aus als das Schwert, weil
Platte einen Schnitt weit besser beantwortet als einen Wuchtschlag. Mit erhobenem
Schild zeigt eine Leiste unter dem Fadenkreuz das Paradefenster: trifft der Gegner
in den ersten sechs aktiven Ticks, wird pariert und der Angreifer taumelt. Spaeter
ist es ein normaler Block, der Ausdauer kostet. Eine Axt bricht die Deckung.

## Milestone 3: Umwelt

Sauerstoff, Kohlendioxid, Methan, Rauch, sauerstoffabhaengiges Feuer, Raumtemperatur
und getragenes Licht sind umgesetzt. Formeln, Grenzwerte und Grenzen des Modells
stehen in [docs/milestone-3.md](docs/milestone-3.md).

Zum Ausprobieren in einer Welt mit Cheats: sich in einen dichten Raum einmauern.

```mcfunction
/give @s hardwrought:safety_lamp
/give @s minecraft:torch
/hardwrought debug on
/hardwrought status
/hardwrought air
/hardwrought air set oxygen 0.05
```

Ohne Wetterlampe gibt es nur Symptome: das Bild wird enger, Ausdauer und Muedigkeit
verhalten sich ohne sichtbaren Grund schlecht. Mit Wetterlampe im Inventar zeigt die
Anzeige (Taste `H`) Sauerstoff, Kohlendioxid und Methan als Zahlen. Eine Fackel in der
Hand leuchtet jetzt wirklich; sie verbraucht dabei Luft. Unter Y 8 an freiliegender
Kohle sammelt sich Methan und entzuendet sich an offener Flamme.

Neu hinzugekommen sind ausserdem die drei Waffenklassen aus Meilenstein 2, fuer die
Vanilla kein passendes Item hat:

```mcfunction
/give @s hardwrought:flint_dagger
/give @s hardwrought:iron_greatsword
/give @s hardwrought:iron_halberd
```

Diese Items sind spielbar, verwenden aber noch Platzhaltertexturen. Was an Grafik
fehlt, steht in [textureRequirements.md](textureRequirements.md).

## Milestone 4: Wasser

Wasserquellen gibt es nicht mehr. Wasser ist eine Menge in Millibucket, die faellt und sich
ausgleicht, und nichts erzeugt neues Wasser: ein voller Block sind 1000 mB, ein Eimer 1000 mB, eine
Glasflasche 100 mB, ein Wasserschlauch 800 mB. Dazu kommen Wasserqualitaet, ein regionaler
Grundwasserspiegel und Verdunstung. Einzelheiten stehen in
[docs/milestone-4.md](docs/milestone-4.md).

```mcfunction
/give @s hardwrought:filled_waterskin
/hardwrought debug on
/hardwrought status
```

Schleichen und den Wasserschlauch an einer Quelle benutzen fuellt ihn und merkt sich, **was** darin
ist. Meerwasser entzieht dem Koerper mehr Wasser als es bringt, Sumpfwasser macht krank. Den
Wasserschlauch auf ein brennendes Lagerfeuer anwenden kocht das Wasser ab und macht es trinkbar;
bei Salzwasser hilft das nicht.

Sauberes Wasser kommt sonst aus dem Boden: unterhalb des regionalen Grundwasserspiegels sickert
Wasser in ausgehobene Raeume mit natuerlichen Waenden. Das ist ein Brunnen, wenn man ihn wollte, und
ein absaufender Stollen, wenn nicht. Waende aus Brettern oder Ziegeln bleiben dicht, und je tiefer
unter dem Spiegel gegraben wird, desto schneller drueckt das Wasser nach.

Jede Region hat dabei einen **endlichen Vorrat**. Was Quellen und absaufende Stollen entnehmen, fehlt
der Region: der Grundwasserspiegel sinkt um bis zu zwoelf Bloecke, Brunnen fallen trocken, und der
Stollen hoert irgendwann von selbst auf vollzulaufen. Regen, Schneeschmelze und die Jahreszeit
fuellen den Vorrat wieder auf. Wie ergiebig der Boden ist, wie tief sein Spiegel liegt und was darin
geloest ist, steht pro Biom in `data/hardwrought/hardwrought/aquifer/*.json` — ein Brunnen am Strand
foerdert Salzwasser, die Wueste haelt wenig Wasser und haelt es tief.

Wasser traegt ausserdem mit, was darin ist: Meerwasser bleibt Meerwasser, wenn es weggetragen,
ausgegossen oder weitergeflossen ist, und ein Eimer merkt sich jetzt ebenfalls, wo er gefuellt wurde.
`/hardwrought water` zeigt Spiegel, Vorrat und Qualitaet der Region an.

**Regen bringt Wasser.** Er fuellt nicht nur den Grundwasservorrat, sondern faellt als echtes Wasser
auf den Boden: in Senken bilden sich Pfuetzen, ein verdunsteter Bewaesserungsgraben fuellt sich
wieder, und bei einem Gewitter faellt ein Mehrfaches davon — dann steigt das Wasser in Flusslaeufen
und tief liegendes Gelaende laeuft voll. Ein Dach oder ein Vordach haelt den Regen ab, Laub nicht;
in kalten Biomen faellt Schnee statt Regen, und Schnee taut im Modell noch nicht. Regenwasser ist
sauberes Wasser, mischt sich aber mit dem, worein es faellt.

## Milestone 5: Vertikalwelt

Die Welt reicht jetzt von **Y −256 bis Y +1023** (1280 Bloecke). Das Gelaende ist ganz normales
Vanilla-Gelaende mit normalen Gebirgen — neu ist nur der Raum darueber und darunter. Einzelheiten
stehen in [docs/milestone-5.md](docs/milestone-5.md).

> **Bestehende Welten lassen sich nicht umstellen.** Wer vor diesem Milestone eine Welt angefangen
> hat, muss eine neue erzeugen — die Hoehe einer Dimension zu aendern aendert jeden Chunk darin.

Die Hoehe ist kein reiner Koordinatenwert, sondern Spielmechanik:

- **Duenne Luft.** Der Luftdruck halbiert sich alle 960 Bloecke ueber dem Meeresspiegel, und der
  Sauerstoff draussen sinkt mit ihm: Y 300 leicht, Y 600 deutlich (unter der Schwelle, ab der es
  einen beeintraechtigt), Y 900 stark. Eine Huette, die oben gebaut wird, enthaelt von Anfang an
  duenne Luft, und Lueften bringt nur das, was draussen auch da ist. Ueber etwa **Y 720 haelt kein
  offenes Feuer mehr**.
- **Geothermie.** Nach unten wird es heiss: +7 °C bei Y −100, +20 °C bei Y −200, +30 °C am Grund.
  Zusammen mit schlechter Belueftung und CO2 ist die Tiefe damit von sich aus gefaehrlich.
- **Wind.** Ab Y 320 kommt Hoehenwind dazu, bei Y 720 ist es ein voller Sturm — unabhaengig vom
  Wetter.
- **Tiefe Hoehlen.** Die Vanilla-Carver setzen ihre Untergrenze relativ zum Weltboden an, karsten
  also jetzt bis Y −248. Unter Y −64 gibt es dafuer noch keine Erze; das ist Milestone 6.

```mcfunction
/hardwrought air
/hardwrought status
```

## Milestone 6: Geologie

Erz liegt nicht mehr ueberall. Die Vanilla-Adern sind aus allen Oberwelt-Biomen entfernt und durch
**wenige grosse Lagerstaetten** ersetzt. Einzelheiten stehen in [docs/milestone-6.md](docs/milestone-6.md).

> Bereits erzeugte Chunks bleiben, wie sie sind — Lagerstaetten entstehen nur in neuem Gelaende.

- **Gesteinsregionen.** Jede Region von 256 Bloecken besteht aus einem Gestein (Granit, Vulkan- oder
  Sedimentgestein), abgeleitet aus dem Weltseed und unabhaengig vom Biom. Was ein Gestein fuehrt,
  steht in `data/hardwrought/hardwrought/rock/*.json`: Kohle nur im Sediment, Diamant im Vulkangestein,
  Smaragd im Granit.
- **Lagerstaetten.** Ein bis drei pro Region, 20–48 Bloecke breit und flach wie ein Floez. Nichts
  davon wird gespeichert; alles ergibt sich aus Seed und Koordinate.
- **Erzgehalt.** In der Mitte am reichsten, am Rand noch etwa ein Drittel davon. Der Gehalt zahlt
  sich direkt aus: bis zu vier zusaetzliche Drops pro Block im reichen Kern, im tauben Randbereich
  keiner.
- **Prospektion.** Schleichen und eine Spitzhacke auf natuerliches Gestein benutzen. Jede Spitzhacke
  nennt das Gestein und Spuren im Umkreis von 48 Bloecken; ab Eisen kommen Richtung, Entfernung und
  Tiefe dazu (160 Bloecke). Wer drinsteht, bekommt den Gehalt genau an dieser Stelle.

```mcfunction
/hardwrought debug on
/hardwrought status
```

Der ORE-Kanal sagt, wo die naechste Lagerstaette liegt — der schnellste Weg, die Erzeugung im Spiel
selbst nachzupruefen.

## Naechste Schritte

Die detaillierte Arbeitsgrundlage ist jetzt die
[vollstaendige Mechanik-Spezifikation](Hardwrought_Full_Mechanics_Specification_v3.md).
Der [Abgleich mit dem Fundament](docs/specification-review.md) dokumentiert die
Anpassungen und Vorgaben fuer kommende Module.

1. Milestone 7: Fruehe Progression — primitive Werkzeuge und Abbauregeln, Stein, Kupfer, Bronze,
   fruehe Metallurgie.
2. Offen aus Milestone 6: Aufbereitung (Brechen, Sieben, Waschen), die spaeteren
   Prospektionsmethoden und Erz unterhalb von Y -64.
3. Weitere Schritte gemaess Abschnitt 118 der Mechanik-Spezifikation.

Der vorhandene GitHub-Workflow baut und startet die Server-GameTests bei Pushes und Pull Requests.
Im aktuellen Ordner ist noch kein Git-Repository initialisiert.

## Lizenz

Die vorhandene Projektlizenz ist CC0-1.0; siehe [LICENSE](LICENSE).
