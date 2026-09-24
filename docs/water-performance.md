# Wassersimulation: Befunde zur Performance bei großen Seen

Stand: 22.09.2026. Gemessen gegen den Solver in
[`WaterFlow`](../src/main/java/de/ipnats/hardwrought/water/WaterFlow.java) und
[`WaterStorage`](../src/main/java/de/ipnats/hardwrought/water/WaterStorage.java),
Minecraft 26.3.

Anlass war die Frage, wie sich die Simulation bei großen Seen besser verteilen lässt.
Die Antwort fiel anders aus als erwartet: der größte Hebel ist kein Engpass im heißen
Pfad, sondern eine Modelleigenschaft, die aus einem ruhenden See einen dauerhaft
arbeitenden macht.

## Messaufbau

Zwei Wegwerf-Gametests gegen den echten Solver, nicht gegen eine Nachbildung. Beide
sind nach der Messung wieder entfernt worden; sie lassen sich aus den Zahlen unten
jederzeit rekonstruieren.

**Sonde 1 — geschlossener Schacht, acht Blöcke tief, komplett mit vollen
Wasserblöcken gefüllt.** Danach 400 Durchläufe `step` über alle acht Zellen.

**Sonde 2 — Steinbecken 15 × 15 × 3, flach und randvoll gefüllt (675 Wasserzellen),
in Ruhe.** Danach *ein* Eimer aus *einer* Zelle in der Mitte entnommen und 60 volle
Durchläufe über alle Zellen.

## Befund 1: Ein ruhender See bleibt nicht ruhend

Sonde 1:

```
vorher:  1000 1000 1000 1000 1000 1000 1000 1000   Tabelle: 0 Einträge
nachher: 1293 1244 1195 1146 1097 1048  977  LUFT
```

Sonde 2:

```
vorher:  675 Zellen, 0 Tabelleneinträge,   0 aktiv
nachher: 675 Zellen, 675 Tabelleneinträge, 675 aktiv
         nach 60 vollen Durchläufen immer noch nicht ruhig
```

### Ursache

`WaterAmounts.stableState` hat einen Fixpunkt bei rund 1052 mB. Für zwei gestapelte
Zellen gilt im Gleichgewicht `unten = 1000 + oben / 20`, und zwei volle Zellen wollen
deshalb nicht 1000/1000 werden, sondern 1047/953. Das pflanzt sich durch die ganze
Säule fort.

### Drei Folgen, alle gemessen

1. **Jede Zelle bekommt einen Tabelleneintrag.** `WaterStorage` ist ausdrücklich
   darauf gebaut, nur die *bewegte Kante* zu speichern — „keeps the stored table to
   the moving edge of the water rather than to every block of it". Nach einem
   einzigen Eimer speichert es den ganzen See. Hochgerechnet auf 100 × 100 × 10 sind
   das rund 100.000 persistierte Einträge statt einiger hundert.
2. **Der ganze See landet in der Queue.** `MAX_ACTIVE` ist 32.768. Ein großer See
   passt nicht hinein, ein Teil wird nach FIFO verworfen, der Rest arbeitet weiter.
3. **Die Oberfläche sinkt.** In der Achtersäule verschwindet der oberste Block
   vollständig. Das Volumen bleibt erhalten, aber Blöcke haben feste Größe, also geht
   sichtbare Höhe verloren.

### Das ist eine Entscheidung, kein Versehen

[`WaterFlowGameTests.pressureRisesWithWhatStandsOnTop`](../src/gametest/java/de/ipnats/hardwrought/test/WaterFlowGameTests.java)
fordert `stableState(2 * BLOCK) > BLOCK` ausdrücklich, und die Dokumentation begründet
es: ohne Überdruck kommunizieren kommunizierende Röhren nicht. Der Preis dafür zeigt
sich erst bei Seegröße und war so vermutlich nicht beabsichtigt.

### Ausweg A — Kompression erst oberhalb zweier voller Zellen (empfohlen)

Ein Paar mit einer Gesamtmenge bis 2000 mB ruht bei 1000/1000. Erst darüber wirkt die
Kompression: ein Paar mit 2200 teilt sich etwa 1150/1050. Der Druck wandert weiterhin
nach oben, aber ein offener See hat keinen Überschuss und bleibt exakt voll — Tabelle
leer, Queue leer.

Wichtig für die Bewertung: offene Röhren brauchen die Kompression gar nicht.
`level()` und `fall()` gleichen zwei verbundene Schächte auch ohne sie aus. Gebraucht
wird sie nur für den geschlossenen Tank, der von unten gespeist wird, und dort gibt es
echten Überschuss.

Die Formel muss bei 2000 mB stetig sein, sonst zieht ein einzelner Millibucket
Überschuss sofort 25 mB nach unten. Ein Ansatz, der das erfüllt:

```
unten = BLOCK + ueberschuss / 2 + min(ueberschuss, COMPRESSION) / 2
```

mit `ueberschuss = gesamt - 2 * BLOCK`. Die untere Zelle trägt den Überschuss bis
`COMPRESSION` allein und teilt danach — was der vorhandenen Beschreibung („this small
excess is the weight of the water above it") entspricht.

**Nicht verifiziert.** Abnahmekriterium ist `waterRisesInCommunicatingVessels`;
`pressureRisesWithWhatStandsOnTop` muss neu formuliert werden, weil es genau die
Eigenschaft festschreibt, die hier fällt.

### Ausweg B — Druck als eigener Skalar

Der Modellfehler ist, dass Druck als Volumen geführt wird. Sauber wäre ein eigener
Skalar pro Zelle — „bis auf welche Höhe will dieses Wasser" —, der sich als Maximum
durch den verbundenen Körper ausbreitet und kein Volumen kostet. `rise` hebt Wasser,
solange dieser Wert über der eigenen Höhe liegt; Zellen halten nur noch 0 bis `BLOCK`.
Kein Sink, kein Tabellenwachstum, kein Höhenverlust. Dafür ein Umbau von `rise` und
ein zusätzliches Feld pro Zelle.

## Befund 2: Rund 2 µs pro Zelle, fast nur Chunk-Zugriffe

```
arbeitender step: ~2076 ns
Frühausstieg:      ~115 ns
```

Ein `step` auf einer Innenzelle löst etwa **28 chunk-auflösende Zugriffe** aus,
obwohl höchstens ein bis vier verschiedene Chunks beteiligt sind:

- `canHold(p)` macht `hasChunkAt` und `getBlockState`
- `amount(p)` macht erneut `getBlockState` und zusätzlich `getChunkAt` für die
  Attachment-Tabelle
- und das für die Zelle selbst, oben, unten und vier Seiten

Die Differenz zwischen Frühausstieg und echtem Schritt besteht im Wesentlichen aus
diesen Zugriffen.

**Vorschlag:** ein wiederverwendeter `StepContext` pro Zelle, der `LevelChunk` und
`WaterChunkData` zwischenspeichert (Schlüssel `chunkX, chunkZ`, vier Slots reichen
für jede Nachbarschaft), und ein gemeinsames `read(pos) → (state, amount)` statt
getrenntem `canHold` und `amount`. Das entfernt rund 20 der 28 Zugriffe.

Geschätzt **3- bis 5-fach** auf dem heißen Pfad, ohne jede Verhaltensänderung. Die
risikoärmste Maßnahme im ganzen Dokument.

## Befund 3: Aufweckdichte

```
Signale gesamt:        115.667
davon mit Weltzugriff:  29.916   (74 % durch die Queue dedupliziert)
```

Die Deduplizierung trägt, ist aber teurer als nötig:

- `transfer` weckt elf Nachbarn, und ein `step` macht bis zu sechs Transfers — also
  bis zu 66 `activate`-Aufrufe für eine Zelle, viele davon dieselbe Position.
- `activate` prüft gegen **drei** getrennte Sets (`near`, `mid`, `far`), also drei
  Hash-Proben pro Aufruf — und das zweimal, einmal in `WaterFlow.activate` und einmal
  in `ActiveQueue.activate`.

Zwei billige Verbesserungen:

- **Weckliste pro `step` sammeln und einmal am Ende leeren.** Entfernt Duplikate
  innerhalb einer Zelle, bevor sie überhaupt eine Hash-Probe kosten.
- **Eine `Long2ByteOpenHashMap` für die Mitgliedschaft** (Wert = Bucket) statt drei
  Sets. Eine Probe statt drei, und die Vorprüfung in `WaterFlow.activate` entfällt.

## Befund 4: Das Speicherformat

`WaterStorage.TABLE_CODEC` ist `Codec.unboundedMap(Codec.STRING, Codec.INT)` mit
`Long.toString` als Schlüssel. Sobald eine Chunk-Tabelle tausende Einträge hat — was
wegen Befund 1 der Normalfall ist —, sind das tausende String-Allokationen und
`Long.parseLong`-Aufrufe pro Chunk-Speichern und -Laden.

Zwei gepackte Arrays (`long[]` Positionen, `int[]` Mengen) wären dasselbe in einem
Bruchteil, brauchen aber eine Migration des alten Formats. Dasselbe gilt für
`WaterQualityStorage`, das denselben Aufbau verwendet.

Mit Befund 1 behoben entschärft sich das von selbst, weil die Tabellen wieder klein
sind. Deshalb steht es hier hinten.

## Kleinkram

- **`SIDES` rotieren.** Die Reihenfolge Nord/Ost/Süd/West ist fest, was eine
  Richtungsschlagseite beim Ausbreiten erzeugt. Für „verteilt sich gut" relevant,
  kostet nichts.
- **`activate(from)` einmal statt pro Transfer.** `from` ist die gerade bearbeitete
  Zelle und wird bis zu sechsmal neu eingereiht.

## Empfohlene Reihenfolge

1. **Befund 2** — Kontext-Caching. Risikolos, messbar, kein Verhalten ändert sich.
2. **Befund 1** — Kompressions-Fixpunkt. Größter Effekt bei großen Seen, aber eine
   bewusst getroffene Design-Entscheidung; vorher die Vessel-Tests prüfen.
3. **Befund 3**, danach **Befund 4**.
