# Nuclear engineering

Development plan section 113. Uranium and thorium were already in the ground as metals; this adds what
is done with them: a fuel chain from ore to fuel assembly, a centrifuge cascade that enriches uranium,
a 5×5×5 reactor whose core is worked out by point kinetics, a steam turbine that turns its steam into
current, and radiation, which a body takes up and a shield keeps out. The code is in
`de.ipnats.hardwrought.nuclear`; the assets are written by `tools/NuclearAssets.java`.

```text
uranium ore ─ crusher ─ uranium powder ─┐
                       battery acid ────┴─ chemical reactor ─ yellowcake ─┐
fluorite ore ─ fluorite ─ + battery acid ─ chemical reactor ─ HF ─────────┴─ chemical reactor ─ UF₆ (gas)
UF₆ ─ centrifuge cascade ─ enriched UF₆ (gas) + depleted uranium
enriched UF₆ + water ─ chemical reactor ─ UO₂ ─ plate press ─ green pellet ─ glass kiln ─ fuel pellet
thorium powder + caustic soda ─ chemical reactor ─ ThO₂ + zircon
zircon + coke + chlorine ─ chemical reactor ─ zirconium sponge + hafnium
zirconium sponge + tin ─ large arc furnace ─ zircaloy ─ plate ─ tube mill ─ cladding tube
cladding tube + 4 pellets + spring + 2 plugs ─ fuel rod;  6 rods + grid + nozzles ─ fuel assembly
```

## The fuel chain

| Step | Where | In | Out |
|---|---|---|---|
| Leaching | chemical reactor | 4 uranium powder, battery acid | 2 yellowcake |
| Hydrofluoric acid | chemical reactor | 2 fluorite, battery acid | hydrofluoric acid |
| Conversion | chemical reactor | yellowcake, 2 hydrofluoric acid | 2 glass bottles; 10 units UF₆ out at the top |
| Enrichment | centrifuge cascade | 10 units UF₆ | 1 unit enriched UF₆, depleted uranium |
| Deconversion | chemical reactor | 1 unit enriched UF₆, 250 mB water | 2 uranium dioxide |
| Pressing | plate press | uranium dioxide | 4 green pellets |
| Sintering | glass kiln | green pellet | fuel pellet |
| Monazite | chemical reactor | 2 thorium powder, caustic soda | 2 thorium dioxide, zircon |
| Kroll process | chemical reactor | 4 zircon, coke; 8 units chlorine | 3 zirconium sponge, hafnium |
| Zircaloy | large arc furnace | 4 zirconium sponge, tin ingot | 4 zircaloy ingots |
| Cladding | tube mill | zircaloy plate | 2 cladding tubes |
| Thorium pellets | plate press | thorium dioxide | 4 thorium pellets |
| Thorium reprocessing | chemical reactor | irradiated thorium rod, 2 hydrofluoric acid | radioactive debris; 4 units enriched UF₆ |

Fluorite is a new ore in granite: veins between y −160 and 40, and ore bodies in the granite rock
profile, as uranium and thorium have. Zircaloy is a stock metal (ingot, rod, plate); it melts at
1850 °C, too hot for any furnace, so it is only ever made in the large arc furnace.

The chemical reactor now also takes water by pipe and enriched hexafluoride by gas line.

## The parts

| Part | Recipe |
|---|---|
| Plenum spring ×4 | 2 stainless steel rods |
| End plug ×8 | 2 zircaloy rods |
| Spacer grid ×2 | 4 zircaloy plates, nichrome wire |
| Top nozzle | 4 stainless steel plates, plenum spring |
| Bottom nozzle | 4 stainless steel plates, nichrome mesh |
| Fuel rod | cladding tube, 4 fuel pellets, plenum spring, 2 end plugs |
| Thorium breeder rod | cladding tube, 4 thorium pellets, plenum spring, 2 end plugs |
| Fuel assembly | 6 fuel rods, top nozzle, spacer grid, bottom nozzle |
| Control rod | 6 stainless steel plates and 3 hafnium, or 3 silver ingots |
| Turbine blade ×4 | 3 stainless steel plates |
| Impeller | 4 stainless steel plates, stainless steel rod |
| Fission chamber | glass tube, 2 uranium dioxide, capacitor, copper wire |
| Geiger–Müller tube | glass tube, tungsten filament, copper wire |
| Centrifuge rotor | 2 bearings, 2 stainless steel rods, tube section |
| Geiger counter | Geiger–Müller tube, 2 capacitors, device housing, basic circuit |

## The reactor

A 5×5×5 vessel of reactor vessel wall. Inside it, graphite electrodes as reflector above and below, and
nine fuel channels in the middle layer. The middle 3×3 of the head takes control rod drives or wall.
The reactor control desk sits in the middle of one side. Ports may take the place of any wall block but
the head's middle:

- a **fluid port** for feedwater, which must be purified (`FeedwaterTank`);
- a **gas port** the steam leaves by, at up to 30 bar, so the line must be chromium or titanium;
- an **item port** that loads fresh fuel and breeder rods and takes out spent fuel and irradiated
  thorium, but only while the core is shut down;
- a **power port** for the main coolant pumps and the rod drives: 15 Ω, working from 30 V, full flow
  at 60 V.

The rods hang on their drives by electromagnets. Without current they drop, so a reactor needs power
from somewhere else before it can make its own.

### The physics (`ReactorCore`)

- **Neutrons.** One group of delayed neutrons in the prompt-jump approximation: `P = C·β/(β−ρ)`,
  `dC/dt = λ(P − C)`, with β = 0.0065 and λ = 0.08/s. The period is (β − ρ)/(λρ). A fall is never faster
  than an 80 s period. At ρ ≥ β the core is prompt critical and the power runs away with a generation
  time of 1 ms. A startup source keeps a loaded core at 10⁻⁵.
- **Reactivity.** k = (0.12 + 0.12 per fresh assembly − 0.025 per breeder rod) × moderation. A burnt
  assembly is worth a fifth of a fresh one. Moderation is 0.6 without water, so a water-moderated core
  stops on losing its water. ρ = (k−1)/k − 0.05 per drive × rod insertion − 0.09 × xenon −
  0.0002/K × (T − 20 °C). Nine fresh assemblies give k = 1.2 cold, eight 1.08, seven 0.96. At least
  four drives are needed to shut a fresh core down cold.
- **Rod control.** By hand, the rods move at 2 %/s towards where they are set. With a power setpoint
  the desk drives them itself: out while the power is low and ρ is below 0.0015 (0.003 while starting
  up from the source), in when the power is above the setpoint or ρ is above that. Between critical and
  prompt critical there are only a couple of percent of rod travel; by hand it is easily overshot.
- **Heat.** Fission power and decay heat heat a core of 4 kJ/K. Decay heat is followed in three groups
  (3.5 % over a minute, 2 % over twenty minutes, 1 % over ten hours). Full power is 120 kW thermal. The
  coolant carries 600 W/K above 100 °C at full flow; without the pumps natural circulation gives 15 %.
  Losses through the insulation are 0.5 W/K. Steady at full power the core stands at about 310 °C.
- **Xenon.** Iodine follows the power over 400 s and decays into xenon over 600 s, which the flux burns
  away. After a shutdown from power the xenon peaks for a while before it decays.
- **Protection.** The rods drop by themselves on a redstone signal at the desk, above 350 °C, above
  115 % power, when the drives lose current, when the feedwater runs out at power, or when the flow is
  under half at more than 20 % power. A trip is cleared with the desk's reset once its cause has passed.
- **Burnup.** A fresh assembly lasts two hours at full power; fewer assemblies each burn faster. A burnt
  one becomes a spent fuel assembly in its channel. A breeder rod breeds at half that rate and becomes an
  irradiated thorium rod.
- **Steam.** Every 4 kJ carried off is one unit of steam and takes 2 mB of feedwater. When no line takes
  the steam, the relief valve on the head lifts and it is lost.

### When it goes wrong (`Meltdown`)

Above 1200 °C the cladding fails: every five seconds a lump of radioactive debris is thrown up round the
vessel. At 2200 °C the core melts. The controller is lost; the core layer and the reflector below it
become corium; a steam and hydrogen explosion of power 7 bursts the vessel; forty lumps of debris are
scattered up to fourteen blocks away. The protection makes this hard to reach. It takes a long run and
then a loss of all feedwater, so that decay heat alone heats the dry core, or too few rod drives.

## The steam turbine

A 3×3×3 hull of turbine casing, with a turbine rotor in the middle, a condenser under it, and the
controller in one side. Steam comes in through a gas port. The generator drives 240 V behind 0.3 Ω
through a power port with a copper coil on it. A third of the steam's heat becomes current. The voltage
sags with less than a fifth of the steam chest full. 95 % of the water comes back as purified condensate
through a fluid port, ready to be piped back to the reactor.

At full power the reactor's 120 kW of heat give about 40 kW of current.

## The grid

A turbine gives up to about 170 A at 240 V. The old conductors and transformer are made for a few
amperes, so a power station has its own:

| Conductor | Ω per block | Rated | Span | Bare | Recipe |
|---|---|---|---|---|---|
| Heavy power cable | 0.002 | 80 A | 16 | no | 4 copper cables, 2 rubber |
| Overhead conductor ×2 | 0.006 | 40 A | 48 | yes | 8 aluminium rods round a steel rod |

The **power transformer** steps up or down four times, as the small one does, but carries 60 A on its
high side through 0.05 Ω. The turbine's 240 V become 960 V, so 40 kW go over a long line at about
42 A on overhead conductor. It is made of 2 bushings, a steel plate, 2 windings, 2 transformer cores
and 2 cooling coils.

The **battery bank** (`BatteryBankController`) is a 5×5×5 hull of battery bank casing. Up to 27
battery cell stacks go inside, and fewer work too. The controller sits in the middle of one side, and a
power port with a copper coil may be anywhere in the casing.

- Voltage: 216 V empty to 252 V full. Like the small battery, it charges from a line above that and
  gives below it, with nothing to switch.
- Each stack holds 2 MJ and carries 10 A. With its share of the charge controller it stands behind
  13.5 Ω, and the stacks stand in parallel: 27 make 0.5 Ω, so charging an empty bank from a turbine
  draws under 50 A, which a heavy cable carries. 27 stacks hold
  15 kWh, which carries a turbine's 40 kW for about twenty minutes.
- 85 % of what goes in comes out again.
- More current than the stacks are made for boils the acid. Kept up, the middle stack bursts.
- A turbine at 240 V charges it to about two thirds. A 60 V line stepped up four times charges it
  fully.
- A broken hull gives nothing but keeps the charge.

Recipes:

- A cell stack is 4 lead plates, 2 battery acid and a cell case.
- 4 casings are 4 steel plates and 4 plastic sheets.
- The controller is 2 voltmeters, an advanced circuit, 2 batteries, a relay and 3 casings.

## The centrifuge cascade

A 3×3×3 hull of centrifuge hall casing, with five gas centrifuges in its middle layer (centre and
corners), and the controller in one side. A gas port in a wall or the floor takes the natural UF₆; a gas
port in the roof gives the enriched product; an item port gives the depleted uranium. It works one batch
in ten seconds at 60 V on 8 Ω.

## Radiation (`Radiation`, `Radioactivity`)

Once a second every player in survival is given the dose of where they stand. That is the sum of:

- what they carry (each item its own rate, in Sv/h);
- reactors, which give 1000 Sv/h at a metre per unit of power and 3000 Sv/h per unit of decay heat;
- dropped items within six blocks;
- corium (200 Sv/h) and debris (0.05 Sv/h) within six blocks.

Everything outside the player falls off as the square of the distance, at least one metre, and passes
the blocks on the straight line between.

| Block | Lets through |
|---|---|
| lead, uranium or tungsten block | 2 % |
| reactor vessel wall | 5 % |
| concrete | 15 % |
| other full blocks of hardness 3 or more (deepslate, metal blocks, obsidian) | 25 % |
| water | 30 % |
| other full blocks of hardness 1.5 to 3 (stone, planks, logs) | 30 % |
| softer full blocks (dirt, sand, gravel) | 60 % |
| glass | 80 % |
| anything not a full block | 90 % |

| Carried | Dose rate |
|---|---|
| raw uranium, ingot, powder | 20 µSv/h each |
| fresh fuel assembly | 0.5 mSv/h, plus 4 Sv/h × burnup |
| irradiated thorium rod | 5 Sv/h |
| spent fuel assembly | 12 Sv/h |

The dose adds up in sieverts and halves over 2400 seconds as the body mends it. It is not kept through
death.

| Dose | Effect |
|---|---|
| 0.5 Sv | nausea now and then |
| 1 Sv | radiation sickness: weakness, hunger |
| 3 Sv | one heart every 30 s |
| 6 Sv | one heart every 4 s |

The Geiger counter clicks in either hand, as fast as the radiation comes, and shows the dose rate above
the hotbar. Used, it tells the dose taken.

## Compendium

A new shelf, Nuclear Engineering, holds everything above. The journal has a chapter, *The Atom*, with
nine thoughts and four deeper looks: the fuel chain, running a reactor, radiation, and when it goes
wrong. The three machines have blueprints. The reactor's burnup, its steam, the cascade's product and
what a meltdown leaves are shown as recipes at the machines that do them. The turbine's condensate is
not shown, because water made of steam made of water would go round in a circle.

## Changes to shared code

- `ControllerBlock.Entity` takes a `reach()` (1 for 3×3×3, 2 for 5×5×5), `portAllowed()`, and an
  `idle()` tick while the hull is not whole.
- `PortHost` is what a controller offers through its ports. `PortBlock` and `BigMachines` ask it rather
  than knowing each machine. There is a new port kind, `GAS`.
- `GasKind` has steam, uranium hexafluoride and enriched hexafluoride. Only natural gas and LPG burn.

## Tests

`NuclearGameTests`:

- A fresh core with its rods in stays shut down; seven assemblies never go critical.
- The automatic control brings a fresh core to half power and holds it, below prompt critical.
- A scram drops the power within seconds and leaves the decay heat, which the pumps carry off.
- Losing the feedwater trips the core and stops the reaction; drives without current drop their rods.
- All rods pulled by hand are caught by the protection before the cladding fails.
- A fresh assembly lasts two hours at full power.
- The reactor forms and takes fuel only while shut down, and only purified feedwater.
- A missing fuel channel keeps it from forming. Without current its rods cannot be drawn.
- The turbine takes only steam and gives its voltage. The cascade's wall port takes feed, its roof port
  gives product.
- Two blocks of lead stop almost everything. Spent fuel is far hotter than fresh. A large dose makes a
  player sick.
- The fuel chain's steps are all in their machines.
- The heavy cable, the overhead conductor and the power transformer carry what they are made for; a
  turbine charges a battery bank over heavy cable.

## Still open

- Reprocessing of spent fuel (PUREX) and plutonium are left out.
- A spent fuel pool is only water as a shield; it does not cool anything.
- Contamination does not spread: debris and corium stay where they fall.
- Mobs take no dose.
