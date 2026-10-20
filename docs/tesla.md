# The Tesla Tower

Stage E6 of the [electricity roadmap](electricity-roadmap.md), its capstone: power without wire around the
home, and a guard that never sleeps.

## Building it

A tower is five blocks stacked and one beside them:

| Part | Where | Made |
|---|---|---|
| **Tesla tower** (the foot) | on the ground | 2 elite circuits, a spark gap, 2 transformers, a winding, 3 steel plates |
| **Resonant coil** ×3 | on the foot | 4 porcelain, 4 enamelled wire, a winding |
| **Top load** | on the coils | 8 copper plates round a copper block |
| **Capacitor bank** | against the foot | 4 steel plates, 4 capacitors, a bottle of heavy oil |
| Spark gap | in the foot's recipe | 2 tungsten rods, porcelain |

The foot is wired like any device and wants **960 V**: from a 60 V line, one transformer up to 240 V and
a second up to 960 V. It takes 900 W while its bank fills and nothing when it is full. Its foot stands in
porcelain, so the rain does not flash it over. Not built whole, it does nothing, and the meter says what
is missing.

## What it does

Everything it does it spends from its **capacitor bank** (20 kJ), and the foot refills the bank from the
line.

- **Wireless charge.** Every cell a player carries within 24 blocks — loose, in a device, in a lined
  chestplate — takes 20 W. The exoskeleton, the drill and the headlamp no longer need the charging station
  near home.
- **Wireless lamps.** An electric light within 24 blocks with no line to it is lit from the tower, at its
  25 W.
- **Defence.** A monster within 16 blocks is struck by an arc for four hearts, up to three a second, at
  1.5 kJ a strike. A siege runs the bank down and the foot draws its full power to refill it; the lights on
  the same line dim, and the player sees what the defence costs.
- **Danger.** A player within reach of the top load in metal armour draws arcs too. In a thunderstorm the
  tower calls the lightning down on itself.

The spark gap is the part of the foot that goes: overdriven, it is mended with a new one.

Arcane Engineering (milestone 20) can join here later: a void-crystal resonator in place of the spark gap
is the obvious hook.

## Tests

`TeslaGameTests`: a tower built whole charges a cell carried within its reach and not one outside it,
lights an electric light in reach with no line to it and not one beyond, spending from its bank; it strikes
a zombie in reach at the bank's cost; a tower without its top load is not whole, says so and lights
nothing.

The assets are written by `tools/TeslaAssets.java`.
