# The world pushes back

Two rules in the manner of Better Than Wolves and MITE. Each is hard, and none is a dice roll the
player cannot see coming: what it costs is known before the price is paid. They are in
`survival/Hardship`, one number each.

## Wounds

A hurt body does less.

| Health | Walking | Digging | Jump | Running |
|---|---|---|---|---|
| 60 % and more | as ever | as ever | as ever | yes |
| 45 % | −6 % | −10 % | −5 % | yes |
| 30 % | −12 % | −20 % | −10 % | yes |
| under 30 % | up to −25 % | up to −40 % | up to −20 % | **no** |

Being hit is therefore not something to shrug off until the last heart: every fight leaves the next
one harder until the wounds are healed. This comes on top of what exhaustion, load and diet already
take.

## The arcane burden

Runes are power, and power is noticed.

- **Noticed from farther off**: everything hostile sees a player from 4 % farther for every rune they
  wear or hold (armor, both hands), and 6 % more for every level a rune is overcharged, up to twice as
  far. Light and sprinting count as before, on top.
- **Wears faster**: for every point of wear, a piece has a 4 % chance per rune to lose one more, and
  10 % more per overcharged level, up to 75 %. Unbreaking still works, and is itself a rune.

A piece's tooltip shows both figures for that piece. The best armor there is makes its wearer the
easiest thing in the dark to find, and has to be mended the most. A full set with six runes a piece
is noticed from twice as far.

## Verification

- `CoreGameTests.theWorldPushesBack`: the wound figures; the runes and overcharge on a piece, its wear
  over a thousand uses and how far its wearer is noticed from.
