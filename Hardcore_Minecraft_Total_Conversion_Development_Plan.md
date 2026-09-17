# Hardcore Minecraft Total Conversion
## Development Plan & Design Specification

---

# 0. Projektziel

Das Projekt ist eine vollständige Hardcore-Total-Conversion für Minecraft Java Edition.

Es soll sich nicht wie ein normaler Mod anfühlen, sondern wie eine eigene Minecraft-Variante mit:

- harter Survival-Simulation
- eigener Progression
- eigener Metallurgie
- eigener Technikentwicklung
- Magie
- Research
- Player Mastery
- körperlicher Anpassung
- Multiplayer-Skalierung
- eigener Worldgen
- realistischerer Umwelt
- eigenem Wissens-/Recipe-System
- kontrollierter Mod-Umgebung

Grundprinzip:

> Der Charakter lernt, was möglich ist.  
> Der Spieler lernt, wie man es gut macht.  
> Die Welt zwingt beide dazu, sich anzupassen.

---

# 1. Technische Zielbasis

## Minecraft

- neueste stabile Minecraft-Java-Version
- keine Snapshots
- feste Projektversion statt ständig jedem Minecraft-Update hinterherzulaufen

## Modloader

Aktuell vorgesehen:

- Fabric

## Installation

Langfristiges Ziel:

- kontrollierte Standalone-Installation
- keine beliebigen Fremdmods
- keine Mods, die Progression oder Schwierigkeit umgehen
- eigene Recipe-/Knowledge-Anzeige
- optional eigener Launcher
- eigene Dedicated-Server-Distribution

Im Multiplayer ist der Server autoritativ.

Der Server entscheidet unter anderem über:

- Inventare
- Research
- Maschinenzustände
- Bosszustände
- Itemqualität
- Progression
- Energie
- Umweltzustände

---

# 2. Entwicklungsphilosophie

## Schwierigkeit soll entstehen durch

- Wissen
- Vorbereitung
- Risiko
- Planung
- mechanisches Können
- Ressourcenmanagement
- echte Spielererfahrung
- Zusammenarbeit
- Umweltbedingungen

## Möglichst vermeiden

- reine HP-Schwämme
- extrem teure Rezepte ohne Gameplay
- künstlichen Grind
- klassische Skilltrees
- feste Klassenwahl
- reine XP-Boni
- "Du darfst das erst ab Level X"

---

# 3. Kernprinzip: Baseline + Spezialisierung

Grundlegende Dinge müssen immer für jeden Spieler zugänglich bleiben.

Jeder Spieler darf:

- einfache Waffen herstellen
- grundlegende Werkzeuge herstellen
- einfache Rüstung herstellen
- Basiszauber verwenden
- einfache Maschinen bauen
- einfache Heilmittel herstellen

Spezialisierung verbessert dagegen:

- Qualität
- Effizienz
- Kontrolle
- Komplexität
- Präzision
- besondere fortgeschrittene Möglichkeiten

Beispiel:

```text
JEDER SPIELER
├─ einfache Waffen
├─ einfache Werkzeuge
├─ einfache Rüstung
├─ Basiszauber
├─ einfache Heilmittel
└─ einfache Maschinen

SPEZIALIST
├─ höhere Qualität
├─ komplexere Konstruktionen
├─ bessere Effizienz
├─ bessere Kontrolle
└─ besondere High-End-Möglichkeiten
```

Ein Schmied kann also einfache Magie verwenden.

Ein Magier kann normale Werkzeuge herstellen.

Ein Solo-Spieler darf niemals durch Spezialisierungen komplett blockiert werden.

---

# 4. Entwicklungsreihenfolge

Die Entwicklung sollte in klaren Phasen erfolgen.

---

# PHASE 1 – Technisches Fundament

## Ziel

Bevor Survival, Magie oder Industrie gebaut werden, muss eine stabile technische Basis existieren.

## Systeme

### 1.1 Core

```text
core/
├─ registry/
├─ config/
├─ events/
├─ save/
├─ networking/
├─ simulation/
└─ utilities/
```

### 1.2 Server Authority

Wichtige Systeme dürfen nicht clientseitig entschieden werden.

### 1.3 Datengetriebene Definitionen

Wo möglich sollen Daten statt Hardcode verwendet werden.

Beispiele:

- Materialien
- Waffenwerte
- Rüstungswerte
- Bosse
- Rezepte
- Research
- Maschinen
- Erzlagerstätten

### 1.4 Simulations-Taktung

Nicht jedes System darf jeden Tick vollständig berechnet werden.

Vorgesehen:

```text
Tick Critical
→ Combat / Movement

Fast Simulation
→ Machines / Fluids

Medium Simulation
→ Temperature / Atmosphere

Slow Simulation
→ Seasons / Soil / Ecology
```

### 1.5 Debug Tools

Früh notwendig:

- Umweltanzeige
- Gaswerte
- Temperatur
- Wasserstände
- Statik
- Ore-Regionen
- Simulation Profiling

---

# PHASE 2 – Player Core Survival

Diese Phase muss früh spielbar werden.

---

# 5. Ausdauer

Ausdauer beeinflusst:

- Sprinten
- Springen
- Schwimmen
- Kämpfen
- Blocken
- Parieren
- schwere Werkzeuge
- Schmieden
- Klettern
- Lasten tragen
- Handmaschinen

Beispiel:

```text
Stamina
██████████ 100 %

Sprinten        -8/s
Schwimmen       -6/s
Heavy Attack    -15
Jump            -7
```

Regeneration hängt ab von:

- Hunger
- Durst
- Temperatur
- Schlaf
- Verletzungen
- Training
- Tragegewicht
- Sauerstoffversorgung

---

# 6. Hydration

Der Spieler benötigt Wasser.

```text
Hydration
███████░░░ 72 %
```

Wasserverbrauch steigt durch:

- Hitze
- Sprinten
- körperliche Arbeit
- schwere Rüstung
- Krankheit
- hohe Temperaturen

---

# 7. Nahrung und Energie

Nahrung besteht nicht nur aus Hungerpunkten.

Mögliche Kategorien:

- Kalorien
- Protein
- Kohlenhydrate
- Fett
- Mikronährstoffe

Aktivität erhöht den Energieverbrauch.

```text
Grundbedarf
+ Arbeit
+ Kälte
+ Kampf
+ Regeneration
```

---

# 8. Gewicht und Belastung

Gegenstände besitzen Gewicht.

```text
Carry Weight:
34 / 45 kg
```

Überlastung verursacht:

- mehr Stamina-Verbrauch
- langsamere Bewegung
- schlechtere Sprünge
- langsamere Regeneration

Spätere Transportprogression:

```text
Backpack
↓
Cart
↓
Horse
↓
Mechanical Transport
↓
Advanced Transport
```

---

# 9. Schlaf

Schlafen ist überall möglich.

Keybind:

```text
Sleep
```

Der Spieler kann schlafen:

- auf Gras
- Stein
- Holz
- im Bett
- draußen
- im Haus
- in Höhlen

Die Qualität hängt ab von:

```text
Sleep Quality =
Surface
+ Temperature
+ Shelter
+ Safety
+ Noise
+ Wetness
+ Bedding
```

Schlafen überspringt die Zeit nicht.

Stattdessen:

```text
Sleep
→ World Simulation Accelerates
```

Währenddessen:

- Pflanzen wachsen
- Nahrung verdirbt
- Maschinen arbeiten
- Tiere bewegen sich
- Wetter verändert sich
- Wasser fließt
- Feuer verbraucht Brennstoff

---

# PHASE 3 – Combat, Tools & Armor

Dieses System sollte vor komplexer Magie kommen.

---

# 10. Kampfsystem

Ziele:

- echte Waffenunterschiede
- Ausdauer
- Blocken
- Parieren
- Reichweite
- Rüstungsinteraktion
- Schadensarten
- Spieler-Skill

---

# 11. Schadensarten

Physisch:

```text
Slash
Pierce
Blunt
```

Weitere:

```text
Fire
Cold
Electric
Explosion
Magic
Poison
```

---

# 12. Waffenwerte

```text
Damage
Attack Speed
Reach
Weight
Stamina Cost
Armor Penetration
Impact
Handling
Durability
```

---

# 13. Waffenklassen

Erste Kernklassen:

```text
1. Dagger / Knife
2. Sword
3. Axe
4. Mace
5. Spear
6. Two-Handed Weapon
7. Bow
8. Shield
```

Später:

- War Hammer
- Poleaxe
- Halberd
- Glaive
- Crossbow
- Throwing Weapons
- Magical Foci
- Engineer Weapons
- primitive Firearms

---

# 14. Dagger / Knife

```text
Damage: Low
Reach: Very Low
Speed: Very High
Stamina: Very Low
Armor Penetration: Low
```

Gut für:

- Backup
- frühes Spiel
- Utility
- Stealth

---

# 15. Sword

```text
Slash: High
Pierce: Medium
Blunt: Very Low

Reach: Medium
Speed: Medium
Stamina: Medium
Handling: High
```

Allround-Waffe.

---

# 16. Axe

- hoher Impact
- hoher Schaden
- gut gegen Schilde
- Werkzeug und Waffe
- höhere Ausdauerkosten als Schwert

---

# 17. Mace / War Hammer

Gut gegen:

- schwere Rüstung
- Skelette
- harte Ziele

Nachteil:

- langsamer
- hoher Ausdauerverbrauch

---

# 18. Spear

```text
Damage: Medium
Reach: Very High
Speed: Medium
Stamina: Low-Medium
```

Stärken:

- Distanz
- Tiere
- große Gegner
- Formationen

Schwächen:

- enge Räume
- Gegner direkt am Spieler

---

# 19. Polearms

Beispiele:

- Halberd
- Poleaxe
- Glaive

```text
Slash: High
Pierce: High
Blunt: High
Reach: Very High
Speed: Low
Stamina: High
Handling: Low
```

---

# 20. Angriffsarten

Beispiel Schwert:

```text
Slash
Thrust
Heavy Strike
```

Beispiel Speer:

```text
Quick Thrust
Heavy Thrust
Brace
Sweep
```

Brace:

```text
Enemy Charge
↓
Braced Spear
↓
Damage scales with enemy momentum
```

---

# 21. Blocken

Blocken:

- zuverlässig
- reduziert Schaden
- kostet Stamina
- schwere Treffer können Guard Break verursachen

---

# 22. Parieren

Parieren:

- kleines Timing-Fenster
- geringer Schaden bei Erfolg
- Gegner kann gestaggert werden
- stark abhängig von echter Spielerreaktion

---

# 23. Rüstung

Rüstung verändert den gesamten Spielstil.

## Cloth

```text
Protection: Low
Weight: Very Low
Stamina Cost: Very Low
Magic Interference: Very Low
```

## Leather

```text
Protection: Medium-Low
Weight: Low
Insulation: Medium
```

## Chain

```text
Slash: High
Pierce: Medium
Blunt: Low
Weight: Medium
```

## Plate

```text
Slash: Very High
Pierce: High
Blunt: Medium
Weight: Very High
Stamina Cost: High
Heat: High
```

Schwere Rüstung:

- schützt besser
- kostet mehr Ausdauer
- erhöht Hitze
- erhöht Wasserbedarf
- verschlechtert Schwimmen
- reduziert Mobilität

---

# 24. Trefferzonen

Grob:

```text
Head
Torso
Arms
Legs
```

Keine extrem kleinteilige Simulation.

---

# 25. Tools

Jeder darf grundlegende Tools herstellen.

Qualität wird später durch Handwerk beeinflusst.

Beispiel:

```text
Steel Pickaxe

Poor Craftsmanship:
Mining Speed: 82 %
Durability: 71 %

Good Craftsmanship:
Mining Speed: 107 %
Durability: 119 %
```

---

# PHASE 4 – Environment Simulation

---

# 26. Environmental Volume System

Gas, Temperatur, Luftfeuchtigkeit und Luftstrom werden möglichst in einem gemeinsamen System verwaltet.

```text
ENVIRONMENT
│
├─ Atmosphere
│  ├─ O2
│  ├─ CO2
│  ├─ Methane
│  └─ Smoke
│
├─ Temperature
├─ Humidity
└─ Airflow
```

Nicht jeder Luftblock muss jeden Tick vollständig berechnet werden.

Bevorzugt:

- Regionen
- Räume
- Chunk-Zellen
- periodische Simulation

---

# 27. Temperatur

Einfluss:

```text
Body Temperature =
Environment
+ Clothing
+ Activity
+ Sun
+ Wind
+ Wetness
+ Fire
```

Zustände:

```text
Hypothermic
← Cold
← Normal
→ Hot
→ Overheated
```

---

# 28. Nässe

Nässe entsteht durch:

- Regen
- Schwimmen
- Schnee
- feuchte Kleidung

Nässe erhöht Wärmeverlust.

---

# 29. Wärmeausbreitung

Wärme breitet sich räumlich aus.

```text
Fire
↓
Room Temperature rises
```

Materialien isolieren unterschiedlich.

Beispiel:

```text
Wool   → Excellent
Wood   → Good
Earth  → Good
Stone  → Medium
Glass  → Poor
Metal  → Very Poor
```

---

# 30. Gase

Wichtige Gase:

```text
O2
CO2
Methane
Smoke
Steam
Toxic Gas
```

---

# 31. Höhlengase

Beispiel:

```text
Cave Atmosphere

O2: 15 %
CO2: 7 %
Methane: 3 %
```

CO2 kann unauffällig verursachen:

- Müdigkeit
- Atemprobleme
- Bewusstlosigkeit
- Tod

Methan kann mit Feuer explodieren.

```text
Lit Torch
+
Methane
=
Explosion
```

---

# 32. Feuer

Feuer:

- erzeugt Wärme
- verbraucht Sauerstoff
- erzeugt CO2
- erzeugt Rauch

Dadurch wird Belüftung relevant.

Technische Progression:

```text
Campfire
↓
Chimney
↓
Ventilation
↓
Mechanical Fan
↓
Gas Detector
↓
Electric Ventilation
↓
Climate Control
```

---

# 33. Licht

Dynamische Lichtquellen:

- Fackel
- Kerze
- Laterne
- Öllampe
- Gaslampe
- elektrische Lampe

Getragene Fackeln sollen tatsächlich Licht erzeugen.

---

# 34. Mondlicht

```text
Full Moon
→ gute Sicht

Half Moon
→ schwache Sicht

New Moon
→ nahezu vollständige Dunkelheit
```

---

# 35. Dunkeladaption

Augen passen sich an Dunkelheit an.

```text
0 s  → fast nichts sichtbar
10 s → etwas sichtbar
30 s → bessere Nachtsicht
```

Helles Licht reduziert die Anpassung wieder.

---

# 36. Krankheiten

Bewusst einfach halten.

Mögliche Krankheiten:

- Erkältung
- Grippe
- Lebensmittelvergiftung
- Wundinfektion
- Lungenentzündung

Keine übertriebene medizinische Simulation.

---

# PHASE 5 – Wasser, Wetter & Jahreszeiten

---

# 37. Kein unendliches Wasser

Vanilla-Unendlichwasser wird entfernt.

Wasser besitzt echte Menge.

```text
Water Level 8
...
Water Level 1
Water Level 0
```

Wasser:

- fließt
- verteilt sich
- kann abgepumpt werden
- kann verdunsten
- kann gefrieren

---

# 38. Wasserqualität

Mögliche Kategorien:

```text
Fresh Water
River Water
Swamp Water
Salt Water
```

Aufbereitung:

```text
Water
↓
Filter
↓
Boil
↓
Drinkable Water
```

Später:

- Sandfilter
- Kohlefilter
- Keramikfilter
- Destillation
- moderne Aufbereitung

---

# 39. Wasserkreislauf

```text
Rain
↓
Soil
↓
Streams / Rivers
↓
Lakes
↓
Evaporation
↓
Weather
↓
Rain
```

---

# 40. Grundwasser

Regionen besitzen Grundwasserstände.

Dadurch:

- Brunnen
- Pumpen
- Quellen
- Aquifere

---

# 41. Jahreszeiten

```text
Spring
Summer
Autumn
Winter
```

Sie beeinflussen:

- Temperatur
- Regen
- Schnee
- Wasser
- Pflanzen
- Tiere
- Landwirtschaft
- Kalorienverbrauch
- Brennstoffverbrauch

---

# 42. Spring

- viel Regen
- gute Wachstumsbedingungen
- hohe Flusspegel

Risiken:

- Überschwemmungen
- Schlamm
- kalte Nächte

---

# 43. Summer

- hohe Temperaturen
- hoher Wasserbedarf
- starke Verdunstung
- gute Landwirtschaft

Risiken:

- Dürre
- Überhitzung
- Wasserknappheit

---

# 44. Autumn

- Erntezeit
- sinkende Temperaturen
- Vorbereitung auf Winter

---

# 45. Winter

- niedrige Temperaturen
- gefrorene Gewässer
- kaum Pflanzenwachstum
- hoher Kalorienbedarf
- hoher Brennstoffverbrauch

---

# PHASE 6 – Worldgen & Vertical World

Diese Phase sollte erst kommen, wenn die Umweltlogik grundsätzlich funktioniert.

---

# 46. Neue Welthöhe

Geplanter Bereich:

```text
Min Y: -256
Max Y: +1024
```

Gesamthöhe:

```text
1280 Blöcke
```

Die zusätzliche Höhe und Tiefe sollen gameplayrelevant sein.

---

# 47. Vertikale Zonen

```text
Y +1024
│ Extreme High Altitude
│
├ High Mountains
├ Alpine Zone
├ Normal Surface
├ Deep Caves
├ Lower Caverns
├ Abyss
│
Y -256
```

---

# 48. Höhe und Sauerstoff

Je höher:

- weniger effektiver Sauerstoff
- schlechtere Ausdauerregeneration
- schnellerer Energieverbrauch
- schlechtere Schlafqualität
- höhere Kältebelastung
- stärkerer Wind

Beispiel:

```text
Y 100  → Normal
Y 300  → Slightly Reduced
Y 600  → Low
Y 900  → Very Low
```

---

# 49. Höhenanpassung

Langer Aufenthalt in großer Höhe steigert:

```text
Altitude Adaptation
```

Mögliche Effekte:

- bessere Sauerstoffnutzung
- bessere Belastbarkeit
- bessere Schlafqualität
- geringere Höhenstress-Symptome

Diese Anpassung kann wieder langsam zurückgehen.

---

# 50. Wind

Mit zunehmender Höhe steigt tendenziell der Wind.

Einfluss:

- Temperatur
- Projektilflug
- Feuer
- Windmühlen
- Bewegung

---

# 51. Geothermische Tiefe

Mit zunehmender Tiefe kann Temperatur steigen.

Beispiel:

```text
Surface  → 15 °C
Y -100   → 22 °C
Y -200   → 35 °C
Y -256   → 45 °C+
```

Vulkanische Regionen können deutlich heißer sein.

---

# 52. Tiefenanpassung

Mögliche Anpassungen:

- Heat Adaptation
- CO2 Tolerance
- Work Endurance

Aber nie unbegrenzt.

Technik bleibt langfristig notwendig.

---

# 53. Cave Layers

## Shallow Caves

```text
Y +50 to -20
```

## Deep Caves

```text
Y -20 to -100
```

## Lower Caverns

```text
Y -100 to -180
```

## Abyss

```text
Y -180 to -256
```

Abyss:

- extreme Hitze
- Gase
- seltene Rohstoffe
- geringe natürliche Belüftung
- spezielle Kreaturen
- geologische oder magische Anomalien

---

# 54. Aquifere

Unterirdische wasserführende Schichten.

Ein angeschnittener Aquifer kann eine Mine fluten.

```text
Aquifer opened
↓
Water flow
↓
Mine flooding
```

Später notwendig:

- Pumpen
- Drainage
- Stollenplanung

---

# 55. Geologie

Worldgen basiert auf geologischen Regionen.

Beispiele:

```text
Granite Region
├─ Tin
├─ Tungsten
└─ Quartz
```

```text
Volcanic Region
├─ Copper
├─ Sulfur
└─ Obsidian
```

```text
Sedimentary Basin
├─ Coal
├─ Oil
├─ Natural Gas
├─ Salt
└─ Limestone
```

---

# 56. Erz-Lagerstätten

Erze werden nicht nur als kleine zufällige Adern verteilt.

Stattdessen:

- größere Lagerstätten
- geologische Bedingungen
- Oberflächenhinweise
- regionale Rohstoffverteilung

Exploration soll wichtiger als Strip Mining sein.

---

# 57. Ore Grade

Erze können unterschiedliche Qualität besitzen.

```text
Copper Ore
Grade: 3.2 %
```

oder:

```text
Rich Copper Ore
Grade: 18.7 %
```

Verarbeitung:

```text
Ore
↓
Crushing
↓
Screening
↓
Washing
↓
Concentration
↓
Smelting
```

---

# 58. Prospektion

Früh:

- sichtbare Erzspuren
- Gesteinsfarbe
- Oberflächenstücke
- Flusssedimente

Später:

- Prospektorhammer
- Proben
- Bohrkerne
- chemische Analyse
- geophysikalische Methoden

---

# 59. Rohstoffklassen

## Metalle

- Kupfer
- Zinn
- Zink
- Blei
- Silber
- Gold
- Eisen
- Nickel
- Chrom
- Aluminium
- Wolfram
- Titan

## Industrieminerale

- Kalkstein
- Gips
- Salz
- Schwefel
- Graphit
- Phosphate
- Ton
- Quarz

## Energierohstoffe

- Torf
- Braunkohle
- Steinkohle
- Anthrazit
- Öl
- Erdgas

## Magische Rohstoffe

- Kristalle
- Meteoritenmaterial
- magisch aktive Minerale

---

# 60. Öl

Öl kommt als unterirdisches Reservoir vor.

Nicht:

```text
Oil Block
→ Bucket
```

Sondern:

```text
Exploration
↓
Drilling
↓
Well
↓
Crude Oil
```

---

# 61. Erdgas

Kann vorkommen:

- mit Öl
- in eigenen Gasfeldern
- in Sedimentbecken

Risiken:

- Explosion
- Feuer
- Erstickung
- Leckagen

---

# 62. Raffinerie

Rohöl wird verarbeitet.

```text
Crude Oil
↓
Distillation
├─ Light Fraction
├─ Fuel Fraction
├─ Heavy Oil
└─ Residue
```

Mögliche Produkte:

- Kraftstoffe
- Schmiermittel
- Lösungsmittel
- Bitumen
- chemische Ausgangsstoffe

---

# 63. Chemische Rohstoffe

Wichtige Stoffe:

- Schwefel
- Salz
- Kalkstein
- Quarz
- Graphit
- Phosphate
- Öl
- Erdgas

Nicht hunderte Chemikalien simulieren.

Stattdessen wenige strategisch wichtige Stoffgruppen.

---

# 64. Chemische Reinheit

Beispiel:

```text
Sulfur
Purity: 73 %
```

Später:

```text
Refined Sulfur
Purity: 99.2 %
```

High-End-Prozesse können hohe Reinheit verlangen.

---

# PHASE 7 – Adaptation & Conditioning

Erst sinnvoll, wenn die Basissysteme existieren.

---

# 65. Player Conditioning

```text
Conditioning
├─ Cardio
├─ Strength
├─ Work Endurance
├─ Load Capacity
├─ Swimming Efficiency
├─ Heat Adaptation
├─ Cold Adaptation
└─ Altitude Adaptation
```

Keine frei verteilbaren Skillpunkte.

---

# 66. Spezifisches Training

```text
Running
→ Cardio

Mining
→ Work Endurance

Carrying
→ Load Capacity

Swimming
→ Swimming Efficiency
```

---

# 67. Rückbildung

Nicht verwendete Anpassungen können langsam zurückgehen.

---

# 68. Übertraining

```text
Training
+ Food
+ Sleep
+ Recovery
=
Improvement
```

Zu viel Belastung:

```text
Fatigue ↑
Recovery ↓
Performance ↓
```

---

# 69. Toleranzen

```text
Tolerances
├─ Poison
├─ Healing
├─ Regeneration
├─ Stimulants
├─ Sedatives
└─ Magic Potions
```

---

# 70. Heiltrank-Toleranz

```text
Tolerance 0 %
→ 100 % Effect

Tolerance 50 %
→ 50 % Effect

Tolerance 100 %
→ No Effect
```

---

# 71. Giftresistenz

```text
Poison Tolerance 0 %
→ Full Effect

Poison Tolerance 50 %
→ Half Effect

Poison Tolerance 100 %
→ Immunity
```

Bewusste Vergiftung bleibt riskant.

---

# 72. Toleranzabbau

Toleranz sinkt bei längerer Nichtnutzung wieder.

---

# PHASE 8 – Research & Knowledge

---

# 73. Research bedeutet Wissen

Research gibt keine simplen Stat-Boni.

Es beantwortet:

> Was weiß der Charakter?

---

# 74. Knowledge Compendium

Eigener Ersatz für:

- JEI
- Vanilla Recipe Book
- Wiki
- Research Tree
- Tutorial

```text
Knowledge
├─ Materials
├─ Crafting
├─ Metallurgy
├─ Engineering
├─ Agriculture
├─ Biology
├─ Medicine
├─ Chemistry
├─ Magic
└─ Electricity
```

---

# 75. Verdecktes Wissen

Nicht erforschte Materialien zeigen nur wenig.

```text
IRON

Unknown Material

Properties:
???
???
???

Uses:
Unknown
```

---

# 76. Research erwerben

Durch:

- Materialien entdecken
- Biome erforschen
- Experimente
- Maschinen bauen
- Bücher
- Ruinen
- Pflanzen untersuchen
- Gegner untersuchen
- Bosse
- Artefakte

---

# PHASE 9 – Handwerk & Metallurgie

---

# 77. Early Game

Nicht:

```text
Punch Tree
→ Wooden Pickaxe
→ Stone
→ Iron
```

Sondern:

```text
Äste + Steine
→ primitive Tools
→ Fasern
→ Schnur
→ primitive Axt
→ Holz
→ Feuer
→ Arbeitsfläche
→ Steinbearbeitung
→ Metallurgie
```

---

# 78. Metallurgie

Verarbeitung statt reiner Rezeptkosten.

```text
Ore
↓
Crushing
↓
Washing
↓
Smelting
↓
Casting
↓
Forging
```

---

# 79. Legierungen

```text
Copper + Tin
→ Bronze

Copper + Zinc
→ Brass

Iron + Carbon
→ Steel

Iron + Chromium + Nickel
→ Stainless Steel
```

---

# PHASE 10 – Smithing / Player Mastery

---

# 80. Schmieden

Ablauf:

```text
Heat
↓
Hammer
↓
Shape
↓
Quench
↓
Temper
↓
Grind
```

Spielerleistung beeinflusst:

- Balance
- Edge
- Durability
- Geometry
- Material Stress
- Craftsmanship

---

# 81. Individuelle Waffen

Beispiel:

```text
Heavy Steel Sword

Damage: +14 %
Attack Speed: -9 %
Durability: +21 %
```

oder:

```text
Balanced Steel Sword

Damage: +5 %
Attack Speed: +7 %
Critical Chance: +4 %
```

---

# PHASE 11 – Mechanical Technology

---

# 82. Handbetrieb

Beispiele:

- Handmühle
- Mörser
- Kurbel
- Handbohrer
- Handpresse

---

# 83. Mechanik

```text
Shafts
Gears
Belts
Flywheels
Clutches
```

---

# 84. Naturkraft

```text
Water Wheel
Windmill
```

Beispiel:

```text
Water Wheel
↓
Gearbox
↓
Main Shaft
├─ Saw
├─ Mill
└─ Hammer
```

---

# PHASE 12 – Steam

---

# 85. Dampftechnik

- Boiler
- Druck
- Kolben
- Ventile
- Dampfmaschinen

Fehler können verursachen:

- Lecks
- Ausfall
- Explosionen

---

# PHASE 13 – Electricity

---

# 86. Elektrizität

- Generatoren
- Kabel
- Batterien
- Motoren
- Transformatoren
- elektrische Maschinen

Mechanik bleibt weiterhin relevant.

---

# PHASE 14 – Industrie & Chemie

---

# 87. Industrie

- Crusher
- Walzwerk
- Hochofen
- Pumpen
- Tanks
- Rohrsysteme
- Produktionslinien
- industrielle Chemie

---

# 88. Einheitliches Fluid-System

```text
Fluids
├─ Water
├─ Crude Oil
├─ Fuel
├─ Lubricant
├─ Chemical Solutions
└─ Acids
```

---

# 89. Einheitliches Gas-System

```text
Gases
├─ O2
├─ CO2
├─ Methane
├─ Natural Gas
├─ Steam
└─ Industrial Gas
```

---

# PHASE 15 – Magic

Magie kommt erst, wenn das physische Basisspiel solide ist.

---

# 90. Magie als Konstruktion

Kein simples:

```text
Mana
→ Button
→ Fireball
```

Sondern:

```text
Form
+
Element
+
Direction
+
Power
+
Modifier
```

---

# 91. Runen

Bewertet werden können:

- Form
- Reihenfolge
- Genauigkeit
- Geschwindigkeit
- Stabilität

Schlechte Runen können:

- ineffizient sein
- schwach sein
- instabil sein
- fehlschlagen

---

# 92. Schriftrollen

```text
Parchment
+
Ink
+
Rune Construction
+
Binding
↓
Scroll
```

Schriftrollen erlauben vorbereitete Magie.

---

# 93. Combat Mage

Fokus:

- Projektilmagie
- Schilde
- Teleportation
- Elementarmagie
- Flächenzauber

---

# 94. Enchanter

Fokus:

- Buffs
- Debuffs
- Flüche
- Schutzkreise
- Waffenverzauberung
- Rüstungsverzauberung

---

# 95. Enchanting + Smithing

Beispiel:

```text
Poor Iron Sword
Enchant Capacity: 12

Masterwork Steel Sword
Enchant Capacity: 41
```

Gute magische Waffen benötigen:

- guten Schmied
- guten Enchanter

---

# PHASE 16 – Alchemy & Medicine

---

# 96. Alchemie

```text
Ingredient
↓
Grinding
↓
Extraction
↓
Distillation
↓
Reaction
↓
Stabilization
```

Einfluss:

- Menge
- Temperatur
- Reinheit
- Reihenfolge
- Dauer

---

# 97. Medizin

Bewusst begrenzt.

Fokus:

- Verletzungen
- Infektionen
- einfache Krankheiten
- Trank-/Medikamententoleranz
- Behandlung

---

# PHASE 17 – Building Physics

---

# 98. Statik

Materialien besitzen:

```text
Compression Strength
Tension Strength
Support Distance
Weight
```

---

# 99. Tragende Strukturen

Keine unrealistischen Skybases auf einem einzigen Block.

Zu hohe Belastung:

```text
Stress
↓
Cracks
↓
Collapse
```

---

# 100. Materialien

Beispiele:

- Holz: flexibel
- Stein: stark auf Druck
- Stahl: große Spannweiten
- Erde: schwach

---

# 101. Blätter

Spieler und große Kreaturen fallen durch Leaf Blocks.

Blätter:

- bremsen Bewegung leicht
- behindern Sicht
- tragen keine Spieler

---

# PHASE 18 – Agriculture & Ecology

---

# 102. Boden

Einfluss:

- Feuchtigkeit
- Bodentyp
- Nährstoffe
- Temperatur

---

# 103. Landwirtschaft

Später denkbar:

- Fruchtfolge
- Düngung
- Saatgut
- Schädlinge
- Gewächshäuser

Nicht zu früh überkomplizieren.

---

# 104. Tierpopulation

Regionale Populationen.

Überjagung kann Populationen reduzieren.

```text
HIGH
↓
MEDIUM
↓
LOW
↓
EXTINCT
```

---

# 105. Wildpflanzen

Nachhaltige Ernte wird relevant.

---

# PHASE 19 – Multiplayer

Multiplayer wird zwar technisch von Anfang an berücksichtigt, das volle Gameplay wird später balanciert.

---

# 106. Boss Scaling

Nur aktive Teilnehmer skalieren Bosse.

Unterscheidung:

- Online Players
- Nearby Players
- Active Participants

---

# 107. Boss HP Scaling

Beispiel:

```text
HP Multiplier =
1 + 0.65 × (Players - 1)
```

Nicht linear.

---

# 108. Zusätzliche Mechaniken

Mehr Spieler:

- mehr Adds
- mehr Ziele
- zusätzliche Phasen
- Gebietskontrolle
- koordinationsbasierte Mechaniken

Nicht nur mehr HP.

---

# 109. Shared Research

Möglich:

- Bibliothek
- Research Table
- Archiv

Spieler können unterschiedliche Spezialisierungen entwickeln.

---

# 110. Natürliche Rollen

Beispiel:

```text
Player A
Smithing
Mechanical Engineering

Player B
Combat Magic
Runes

Player C
Enchanting
Alchemy

Player D
Explosives
Electrical Engineering
```

Keine feste Klassenwahl.

---

# PHASE 20 – Advanced Technology

---

# 111. High-End Materialien

Mögliche Stoffe:

- Titan
- Wolfram
- Edelstahl
- Speziallegierungen
- seltene Kristalle

---

# 112. Geothermie

Vulkanische Regionen können geothermische Energie liefern.

---

# 113. Kerntechnik

Optional für sehr spätes Endgame:

- Uran
- Thorium

Nur falls Kerntechnik tatsächlich ins Gesamtkonzept passt.

---

# PHASE 21 – Arcane Engineering

Späte Verbindung von Magie und Technik.

---

# 114. Arcane Materials

Beispiele:

- Silver → gute magische Leitfähigkeit
- Gold → gute Runenstabilität
- Meteoric Iron → hohe Verzauberbarkeit
- Mithril → leicht + magisch leitfähig
- Void Crystal → High-End-Katalysator

---

# 115. Arcane Technology

Mögliche Systeme:

- magische Maschinen
- Runenmotoren
- verzauberte Bauteile
- hybride Energie
- automatisierte Zaubersysteme

---

# PHASE 22 – Endgame

Noch nicht endgültig definiert.

Mögliche Richtungen:

- Weltbosse
- extreme Expeditionen
- Megaprojekte
- seltene Dimensionsressourcen
- High-End-Industrie
- Arcane Engineering
- serverweite Projekte

Das Endgame soll nicht einfach nur aus besseren Waffen bestehen.

---

# 116. Progressionsübersicht

```text
SURVIVAL
↓
ENVIRONMENT
↓
COMBAT
↓
WORLD KNOWLEDGE
↓
STONE / PRIMITIVE
↓
COPPER
↓
BRONZE
↓
MECHANICAL
↓
IRON
↓
STEEL
↓
STEAM
↓
ELECTRICITY
↓
INDUSTRY
↓
ADVANCED CHEMISTRY
↓
MAGIC SPECIALIZATION
↓
ARCANE ENGINEERING
↓
ENDGAME
```

---

# 117. Charakterentwicklung

Drei getrennte Systeme:

```text
             CHARACTER DEVELOPMENT
                      │
        ┌─────────────┼─────────────┐
        │             │             │
    KNOWLEDGE      MASTERY      ADAPTATION
        │             │             │
    Research      Player Skill     Body
    Discovery     Practice       Tolerance
    Theory        Experience     Conditioning
```

---

# 118. Empfohlene tatsächliche Entwicklungs-Meilensteine

## Milestone 0 – Prototype

- Mod startet
- Server/Client-Struktur
- Datensystem
- Debug-Menü
- einfache Configs

## Milestone 1 – Hardcore Core

- Stamina
- Hydration
- Hunger
- Gewicht
- Sleep
- grundlegende Temperatur

## Milestone 2 – Combat

- neue Waffenwerte
- Slash/Pierce/Blunt
- Blocken
- Parieren
- Armor Weight
- Stamina im Kampf

## Milestone 3 – Environment

- O2
- CO2
- Temperatur
- Rauch
- Feuer
- dynamisches Licht

## Milestone 4 – Water

- begrenztes Wasser
- Flow
- Grundwasser
- Verdunstung
- Wasserqualität

## Milestone 5 – Worldgen Prototype

- Y -256 bis +1024
- erste neue Berge
- erste neue Höhlen
- Höhen-Sauerstoff
- geothermische Tiefe

## Milestone 6 – Geology

- Rock Regions
- Ore Deposits
- Ore Grade
- Prospektion
- Aquifers

## Milestone 7 – Early Progression

- Primitive Tools
- Stone
- Copper
- Bronze
- erste Metallurgie

## Milestone 8 – Knowledge System

- eigenes Recipe UI
- Materials
- Research
- Discovery

## Milestone 9 – Smithing

- Forging Minigame
- Weapon Quality
- Tool Quality
- Heat Treatment

## Milestone 10 – Mechanical Age

- Kurbeln
- Zahnräder
- Wellen
- Wind
- Wasser

## Milestone 11 – Oil & Chemistry

- Ölreservoirs
- Erdgas
- Bohrung
- Raffination
- chemische Grundstoffe

## Milestone 12 – Steam

- Boiler
- Pressure
- Steam Engines

## Milestone 13 – Electricity

- Generator
- Battery
- Motor
- Cable

## Milestone 14 – Seasons & Ecology

- Jahreszeiten
- Pflanzen
- Tiere
- Landwirtschaft

## Milestone 15 – Adaptation

- Conditioning
- Tolerances
- Altitude Adaptation
- Heat/Cold Adaptation

## Milestone 16 – Magic

- Runensystem
- Scrolls
- Combat Magic
- Enchanting

## Milestone 17 – Building Physics

- Statik
- Support
- Collapse
- Leaf Collision

## Milestone 18 – Multiplayer Balance

- Boss Scaling
- Shared Research
- Encounter Logic
- Server Performance

## Milestone 19 – Advanced Industry

- große Produktionslinien
- High-End Chemistry
- Advanced Metals

## Milestone 20 – Arcane Engineering

- Magic + Tech
- Exotic Materials
- Hybrid Systems

## Milestone 21 – Endgame

- Bosses
- Expeditions
- Megaprojects
- Final Progression

---

# 119. Was bewusst später entschieden werden sollte

Noch nicht zu früh festlegen:

- genaue Anzahl aller Erze
- genaue Anzahl aller Zauber
- komplette Krankheitstabelle
- endgültige Bossliste
- endgültige Kerntechnik
- exakte Endgame-Struktur
- PvP-Balance
- genaue Economy
- alle NPC-Systeme

Diese Bereiche hängen stark davon ab, wie sich die Kernsysteme tatsächlich spielen.

---

# 120. Wichtigster Leitgedanke

> Der Spieler soll nicht einfach stärker werden.

Er soll lernen:

- wie die Welt funktioniert
- wie man Ressourcen findet
- wie man mit Umweltgefahren umgeht
- wie man kämpft
- wie man Werkzeuge verbessert
- wie man Maschinen baut
- wie man Magie konstruiert
- wie man sich körperlich anpasst
- wie man mit anderen Spielern zusammenarbeitet

Das langfristige Gefühl soll sein:

```text
Anfang:
Die Welt kontrolliert den Spieler.

Mittelspiel:
Der Spieler versteht die Welt.

Endgame:
Der Spieler baut Systeme, mit denen er die Welt kontrolliert.
```
