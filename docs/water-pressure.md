# Wasserdruck ohne Kompression, und Abfluss als Blatt

Stand: 12.10.2026. Ersetzt den Kompressions-Fixpunkt aus
[`water-performance.md`](water-performance.md), Befund 1, und setzt Befund 2 um. Der zweite Teil
beschreibt, was nötig war, damit ein Meer durch ein Loch im Grund abfließen kann.

## Was vorher falsch lief

Druck war als Volumen modelliert: Die untere von zwei gestapelten Zellen wollte rund 1047 mB
halten, die obere 953. Zwei volle Zellen waren damit nie im Gleichgewicht. Jede geweckte Zelle
eines Sees schichtete um, weckte elf Nachbarn, und die taten dasselbe.

Dieselbe Sonde gegen altes und neues Modell: Becken 49 × 49 × 8 (19 208 volle Zellen, gesetzt wie
von der Weltgenerierung), danach zwei Eimer entnommen.

| | alt | neu |
|---|---|---|
| Alle Zellen einmal angesehen, ohne Eingriff | 19 208 gespeichert, 19 208 in der Queue | 0 gespeichert, 0 Weckrufe |
| Kosten einer ruhenden Zelle im offenen Wasser | 1 235 ns (arbeitet) | rund 280 ns |
| Nach zwei Eimern: gespeicherte Zellen | 16 807 | rund 145 |
| Nach zwei Eimern: Ticks bis zur Ruhe | rund 250 bei vollem 5-ms-Budget | 20 bis 60 |
| Weckrufe insgesamt | 9,5 Millionen | 25 000 bis 57 000 |
| Zellen unter der Oberfläche verändert | alle | keine |
| Oberfläche | oberste Schicht vollständig verschwunden | 962 bis 1000 mB, als voll gezeichnet |

Die kommunizierenden Röhren, für die die Kompression gedacht war, glich das alte Modell auch nicht
aus. Fünf Blöcke in einem Schacht endeten bei 2173 mB gegen 1781 mB. Der vorhandene Test bemerkte
das nicht, weil er mit vier Blöcken einen ganzen Block Unterschied erlaubt.

## Druck

`WaterAmounts.stableState`: Bis zwei volle Blöcke wird nichts gepresst. Die untere Zelle füllt
sich, der Rest steht darauf. Nur was über zwei Blöcke hinausgeht, wird gequetscht, also Wasser,
das wirklich von unten in einen zu kleinen Raum gedrückt wurde.

`WaterFlow.press` ist die letzte Regel. Sie läuft nur, wenn eine Zelle in diesem Schritt nichts
abgeben konnte und nicht auf allen vier Seiten Wasser hat. Sie speichert nichts und sucht durch
zusammenhängendes volles Wasser:

- **Schieben.** Die Zelle ist die Oberseite einer Säule und steht auf vollem Wasser. Gibt es
  unterhalb ihrer Höhe eine offene Stelle, oder auf gleicher Höhe eine mit deutlich weniger
  Wasser, dann gehört ihr Wasser dorthin.
- **Ziehen.** Die Zelle ist voll und hat Platz über sich. Steht irgendwo im selben Wasserkörper
  Wasser höher als dieser Platz, wird es von der Oberseite jener Säule geholt.

Beide Richtungen sind nötig, weil immer nur ein Ende wach ist: Ein Eimer weckt die Säule, in die
er geschüttet wird, ein gezogener Stopfen das Wasser darunter.

Eine Suche, die nichts findet, wird für diese Zelle nicht wiederholt, bis sich etwas um sie herum
geändert hat (`ActiveQueue.pressed`, alle 100 Durchläufe vergessen). Ohne diesen Merker suchte
jede Uferzelle eines abfließenden Meers bei jedem Leerlaufschritt neu, und das war mit 11,7 von
19 Sekunden der größte Posten des ganzen Abflusses.

## Abfluss als Blatt

Der Zellsolver bewegte Wasser nur zwischen Nachbarn. Ein Loch im Meeresgrund leerte so eine Säule
nach der anderen, jede hinterließ eine Delle in der Oberfläche, und die Dellen mussten sich über
das ganze Meer hinweg paarweise ausgleichen. Gemessen: ein Meer von 41 × 41 × 4 Blöcken (6 724
Blöcke) durch ein 2 × 2 Loch in eine Höhle darunter brauchte **21 Millionen Zellschritte und 100
Sekunden Rechenzeit**, und 6 Prozent blieben als Film liegen.

Zwei neue Regeln arbeiten auf der Oberfläche als Ganzem (`WaterFlow.walkSheet`):

- **Ziehen aus der Oberfläche** (`drawFromSurface`). Platz unter vollem Wasser gibt es in ruhendem
  Wasser nie. Wo er entsteht, klettert der Solver die Säule hinauf, läuft die Oberfläche ab und
  nimmt den Fehlbetrag gleichmäßig aus den höchsten Oberflächenzellen. Eine volle Zelle unter
  vollem Wasser fällt nicht mehr selbst nach; sie lässt den Platz unter sich von oben füllen.
- **Verteilen über die Oberfläche** (`spillOverSheet`). Eine Oberflächenzelle, neben der die
  Oberfläche tiefer steht, gibt den Unterschied als Blatt ab: zuerst in die Zellen eine Stufe
  tiefer, dann wird die eigene Höhe auf ein Niveau gebracht. Das ist auch der Weg, auf dem ein
  Haufen in einer Höhle sich als Schicht ausbreitet statt als Kegel.

Was beide gemeinsam haben:

- **Schlucke statt Bissen.** Kein Schreibvorgang nimmt einer Zelle mehr als 8 mB (`SIP`), unter
  der lokalen Ausgleichsschwelle. Ein Fenster von mindestens 128 Zellen rotiert über das Blatt,
  und das Blatt bleibt innerhalb eines Schlucks eben. Ein Biss von 250 mB aus einer Zelle löste
  vorher rund hundert Ausgleichsschritte der Nachbarn aus.
- **Stille Schreibvorgänge.** Die Zellen eines Blatts werden nicht geweckt; sie sind nach
  Konstruktion eben. Geweckt wird, was jenseits des Rands des Laufs steht, sobald das Blatt um
  eine Schwelle gesunken ist, und die handelnde Zelle selbst.
- **Zwei Caches.** Der letzte Lauf eines Zugs und der letzte Lauf eines Spills werden je 16-mal
  wiederverwendet, solange der Anlass innerhalb des Laufs liegt. Vor jedem Schreiben werden die
  betroffenen Zellen neu gelesen. Ein Zug darf einen Lauf nur verwenden, wenn seine Säule
  tatsächlich bis zur Höhe des Blatts Wasser führt; ein Tank unter einem See liegt in Spalten,
  die der See gelaufen ist, und wurde sonst durch Fels gefüllt.
- **Vergeblichkeit.** Ein Lauf, der nichts bewegt, wird für diese Zelle gemerkt und nicht
  wiederholt, bis der Merker verfällt oder der Rand eines anderen Laufs die Zelle weckt.
- **Lippen und Brücken.** Offene Luft mit einem Abgrund darunter ist eine Lippe: Ein Blatt gießt
  sich als Ganzes darüber. Ein Film (unter 125 mB) läuft außerdem über trockenen Boden gleicher
  Höhe hinweg, ohne ihn zu benetzen; sonst zerfiel das Blatt beim gleichmäßigen Abfließen in
  Inseln, die wenige Blöcke vor der Lippe strandeten.
- **Spuren.** Ein Film unter 31 mB auf festem Boden, den nichts speist und der nirgends
  hinkann, versickert nach zwei Durchgängen von je 200 Durchläufen ins Grundwasser seiner Region
  (`Groundwater.soak`) oder trocknet, wo der Boden gesättigt ist. `/hardwrought water` zeigt
  beides. Das ist die einzige Stelle, an der Oberflächenwasser nicht erhalten bleibt.

Gemessen mit `WaterFlowGameTests.aSeaDrainsThroughAHoleInItsBed` (33 × 33 × 3 Blöcke, 2 × 2 Loch,
Höhle darunter, echte Queue mit Budget je Durchlauf):

| | Zellsolver | Blatt |
|---|---|---|
| Zellschritte für 6 724 Blöcke | 21 Millionen | rund 1,5 Millionen |
| Rechenzeit für 3 267 Blöcke | | 3,2 bis 3,5 s, etwa 1 ms je Block |
| Größte Queue dabei | 3 959 | unter 1 000 |
| Rest im Meer | 6 Prozent | 0, nachdem die Spuren versickert sind |

Die Zeit je Block steckt zur Hälfte im Füllen der Höhle darunter; ein Meer, das in Lava oder ins
Leere läuft, kostet die Hälfte. Das Loch selbst begrenzt den Durchsatz: eine Lochzelle lässt je
Schritt einen Block fallen.

## Tempo und Void

Stand nach der Rückmeldung „Wasser soll nicht instant fließen".

- **Raten gelten je Tick.** Ein Tick bearbeitet nur die Zellen, die zu seinem Beginn warteten
  (`ActiveQueue.beginPass`). Vorher weckte sich eine Zelle selbst, kam im selben Tick wieder dran
  und gab weiter ab, bis das Zeitbudget leer war. Auf einem ruhigen Server durchquerte ein Eimer
  so in einem Tick den Raum, und das Tempo hing von der Serverlast ab.
- **Eine Rate für alles Schnelle:** 250 mB je Zelle und Tick, auch für Blätter. Ein Blatt gießt je
  Zelle höchstens so viel über eine Lippe und gleicht sich je Zelle höchstens so schnell aus.
- **Die Front** benetzt trockenen Boden weiter mit 60 mB je Tick und braucht eine sichtbare Stufe,
  um weiterzulaufen: rund sieben Blöcke je Sekunde, Vanilla schafft vier.
- **Void.** Wasser in der untersten Schicht der Welt, unter dem nichts mehr ist, fällt mit
  derselben Rate aus der Welt (`WaterFlow.fallIntoVoid`). Es ist danach weg und wird gezählt;
  `/hardwrought water` zeigt die Summe.

Gemessen mit demselben Test, jetzt 21 × 21 × 3 Blöcke (1 323 Blöcke) durch ein 2 × 2 Loch:
900 bis 1 050 Ticks, also 45 bis 53 Sekunden Spielzeit, bei 1,6 bis 1,8 s Rechenzeit insgesamt
(unter 2 ms je Tick). Wie lange ein Meer braucht, bestimmt damit die Größe des Lochs und nicht
die Simulation: rund 26 Blöcke je Sekunde durch vier Lochzellen.

## Zeitbudget und Queue

Das Zeitbudget je Tick ist ein Viertel dessen, was der Rest des Servers von 35 ms übrig lässt,
zwischen 0,1 und 8 ms (`WaterFlow.timeBudget`). Die eigene Zeit des letzten Ticks wird vorher
herausgerechnet, sonst würde ein beschäftigter Abfluss als beschäftigter Server gelesen. Die
Queue merkt sich bis zu 131 072 gestörte Zellen, bevor sie die ältesten verwirft; mit Blättern
bleibt sie bei einem Teich unter tausend.

## Schwellen

- `LEVELLING_THRESHOLD = 10 mB` zwischen Nachbarn, von denen einer ein Film oder eine Front ist.
- `PRESSURE_THRESHOLD = 62 mB` zwischen zwei Körperzellen (beide ab 125 mB): Feineres gleicht das
  Blatt aus, paarweise war es eine Kaskade über den ganzen Körper für jeden Schluck.
- `SPREAD_THRESHOLD = 125 mB`: Erst eine sichtbare Stufe benetzt trockenen Boden, mit 60 mB je
  Durchlauf. Eine Front, die steht, obwohl ihr Blatt mehr hat, bittet das Blatt um Nachschub.
- Fallen, Steigen, Ausgleich zwischen Wasserzellen und Ziehen aus der Oberfläche bewegen je
  Zelle höchstens 250 mB je Tick, also fünf Blöcke Wasser je Sekunde durch eine Zelle.

## Was sonst geändert wurde

- **`WaterStorage.probe`** beantwortet „kann hier Wasser stehen und wie viel" mit einem
  Chunk-Zugriff statt vier. Der Solver benutzt es für alle Nachbarn.
- **`displayLevel`** rundet auch auf die achte Achtelstufe. Eine Zelle ab 938 mB wird voll
  gezeichnet. Vorher sah ein See nach jedem Eimer hundert Blöcke weit eingedellt aus.
- **`WaterFlow.disturb`** weckt die sechs Nachbarn, wenn die Zelle selbst geleert wurde. Vorher
  kam der Weckruf fünf Ticks später von Vanilla, und nur in tickenden Chunks.
- **`WaterFlow.runPass`** ist der Tick des Solvers mit Zeit- und Zellbudget als Parameter, damit
  ein Test einen langen Abfluss in einem Tick mit der echten Queue laufen lassen kann.

Alte Welten heilen von selbst: Eine komprimierte Zelle gibt ihren Überschuss nach oben ab, sobald
sie das nächste Mal geweckt wird.

## Grenzen und Offenes

- **Reichweite.** Ein Lauf sieht 1 024 Spalten, eine Scheibe von etwa 18 Blöcken Radius. Ein
  Meer sinkt in dieser Scheibe als Ganzes; was darüber hinaus steht, fließt am Rand nach, und der
  Krater wächst, sobald seine Wand nachgibt. Die letzte Schicht jenseits der Reichweite der
  Lochnachbarn bleibt als Film liegen, bis sie dünn genug ist zum Versickern. Bei einem echten
  Ozean kann so ein Achtelfilm weit vom Loch liegen bleiben; die Verdunstung nimmt ihn nur für
  kleine Gewässer.
- **Nicht tickende Chunks.** Die Queue lebt im Speicher. Was beim Entladen eines Chunks noch in
  Bewegung war, steht nach dem Laden still, bis es jemand stört. Ein Abfluss läuft also nur, wo
  Spieler sind.
- **Regen auf vollem Wasser** legt weiterhin eine neue Schicht auf die Oberfläche
  (`Rainfall.target`, so von `RainfallGameTests` verlangt).
- **Befund 3 und 4** aus `water-performance.md` (Weckliste, Speicherformat) sind unangetastet.
- **`EnvironmentGameTests.carbonDioxideSinksAndGathersIntoFullBlocks`** ist gelegentlich
  fehlgeschlagen, wenn der Abflusstest im selben Tick lief und den Tick auf Sekunden dehnte; seit
  der Test unter vier Sekunden liegt, ist das nicht mehr aufgetreten.
- Im Spiel getestet ist nichts davon, nur in Gametests.
