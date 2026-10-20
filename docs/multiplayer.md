# Multiplayer – parties, boss fights in company, server utilities

Milestone 18 of the plan ("Multiplayer Balance": boss scaling, encounter participants, shared research,
multiplayer sleep). Server-authoritative throughout; the client only draws the party list.

## Parties

[PartySystem](../src/main/java/de/ipnats/hardwrought/party/PartySystem.java), saved per world in
[PartySaveData](../src/main/java/de/ipnats/hardwrought/party/PartySaveData.java). Invitations are not saved
and run out after a minute.

| Command | |
|---|---|
| `/party` | members, health, away marks, friendly fire |
| `/party invite <player>` | starts a party if you have none; leader only; clickable accept/decline |
| `/party accept\|decline [player]` | newest invitation without a name |
| `/party leave` | the longest-standing member takes over a leaderless party; the last one out ends it |
| `/party kick\|promote <member>` | leader only; works on offline members |
| `/party friendlyfire <true\|false>` | leader only; off by default |
| `/party chat <text>`, `/pc <text>` | party channel |
| `/party ping` | tells the party where you are |

What being in a party does:

- **Shared knowledge.** Everything one member discovers or studies, every member learns, online or not
  ([KnowledgeSystem#share](../src/main/java/de/ipnats/hardwrought/knowledge/KnowledgeSystem.java)). Acts
  (crafting, breaking, examining…) show the others a toast too; things merely carried stay silent for
  everyone. Joining pools everyone's knowledge (`KnowledgeSystem#pool`) and says in one line how much was
  gained. Leaving keeps what was learned. Bookmarks are not shared.
- **Friends.** No damage between members unless friendly fire is on (arrows and tamed animals count as
  their owner). Spells meant for friends (heal ring, shroud, …) reach party members
  ([Allies](../src/main/java/de/ipnats/hardwrought/magic/Allies.java)).
- **Word of each other.** A list at the left edge of the screen with each other member's health and
  distance ([PartyHud](../src/client/java/de/ipnats/hardwrought/client/party/PartyHud.java), synced once a
  second), online/offline notices, and where a member died.

## Boss fights in company

[Encounter](../src/main/java/de/ipnats/hardwrought/encounter/Encounter.java) per boss, kept by
[Encounters](../src/main/java/de/ipnats/hardwrought/encounter/Encounters.java). Specification §§ 97–101.

**Who counts.** A player takes part by striking the boss, being struck by it or its spells, fighting
monsters in its arena, or moving about in it. One act keeps them counted for 30 s. Creative, spectators,
the dead and players away from the keyboard never count.

**The count follows slowly.** One more fighter is felt only after 20 s; fewer fighters are ignored for
60 s, then the count drops by one every 30 s. Stepping out as the boss turns does not make it easier.

**Never more health.** The count changes what the boss does:

| Boss | More of it | Group mechanic |
|---|---|---|
| Wither | +2 skeletons per extra fighter (max 9), a first wave at ¾ health, each sent after a different fighter | **Soul burden** ([SharedBurden](../src/main/java/de/ipnats/hardwrought/encounter/SharedBurden.java)): a marked player; when it falls, the damage is split among everyone within 4 blocks – gather round |
| Witch King | more dead raised, more souls; fans and rings aimed at others; a second lance from 3 fighters; rifts open behind whoever hangs back furthest | **Brand** ([Brand](../src/main/java/de/ipnats/hardwrought/encounter/Brand.java)): bursts out of the bearer and tears everyone within 5 blocks – the bearer walks away. **Crystal chain**: once a crystal breaks, the next must fall within 20 s (15 s from 3 fighters) or a broken one rises again – split over the hall. His boss bar names the fighter count |
| Dragon | switches prey more often, every other bolt at someone else, more endermen, more skyfall targets | **Void tether** ([Tether](../src/main/java/de/ipnats/hardwrought/encounter/Tether.java)): two players bound; pull 18 blocks apart within 8 s or both are torn and thrown down |

Group mechanics need at least two active fighters. Ultra adds one to the pressure
(`Encounter#pressure`), so a group mechanic already comes with two fighters' worth of weight.

**Reset.** Nobody fighting for 3 min (Wither) or 5 min (dragon) and the boss is whole again. The Witch
King keeps his own behaviour: mending while idle, back on his throne after 30 s alone.

## Server utilities

- **Away from the keyboard** ([Afk](../src/main/java/de/ipnats/hardwrought/server/Afk.java)): after
  `afkMinutes` without acting (vanilla's last-action time), or `/afk` until the next act. Announced in
  chat, greyed and marked `[AFK]` in the player list. Away players do not hold back sleep (the sleeper
  share counts only present players) and do not count in boss fights.
- **Death coordinates**: the dying player, and their party, are told where it happened.
- **`/hardwrought server`** (operators): TPS, tick time, players online/away/asleep, parties, per world
  loaded chunks, entities and players, boss fights. `… server parties` lists every party,
  `… server bosses` every fight with its fighters, scale, health and place.
- **Settings** in `config/hardwrought-server.properties`, read at server start
  ([ServerConfig](../src/main/java/de/ipnats/hardwrought/server/ServerConfig.java)): `partyMaxSize` (8),
  `shareKnowledge` (true), `friendlyFire` default for new parties (false), `afkMinutes` (5, 0 = off),
  `deathCoordinates` (true).

Tests: [PartyGameTests](../src/gametest/java/de/ipnats/hardwrought/test/PartyGameTests.java),
[EncounterGameTests](../src/gametest/java/de/ipnats/hardwrought/test/EncounterGameTests.java).
