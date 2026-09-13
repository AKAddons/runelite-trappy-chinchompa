# Duel Protocol - portable async challenge spec (v4.1)

Two players, the same seeds, one relay that keeps the book. Nobody has
to be online at the same time. The relay knows the rules of no game:
seeds, deadlines, series maths and records are generic; the clients
simulate and verify.

History: v1 ran a live session over RuneLite's party websocket
(withdrawn: no party service); v2 hand-pasted chat lines (withdrawn:
chat cannot paste); v3 the live session over our relay (withdrawn: the
acceptor was locked into a countdown). v4 keeps v3's relay and makes the
duel a record both players work through at their own pace.

```
+----------------------------------------------------------+
| L2  Game adapter    deterministic sim on a fixed world,   |
|                     seeded per game, replay of a trace    |
+----------------------------------------------------------+
| L1  Client ledger   records -> to-dos (play / verify /    |
|                     wait), verdicts, outcomes             |
+----------------------------------------------------------+
| L0  Relay           presence, challenges, duel records,   |
|                     seeds, deadlines, settlement, boards  |
+----------------------------------------------------------+
```

## L0 - akaddons-relay

JSON over WebSocket at `/ws`. `hello{name, v, game, account, hidden}`
registers the display name, binds it to the account (a keyed hash of
RuneLite's account hash; a second account on the same name is refused,
close 4001; banned accounts close 4002) and answers `sync{duels,
history}`.

| Message | Relay does |
| --- | --- |
| `challenge{to, game, data:{rules}}` | forwards; `sent` or `offline` back |
| `accept{to, game, data:{rules}}` | forwards, then **creates the duel record** (ten seeds, five-minute idle deadline) and pushes `duel{record}` to both |
| `decline{to, game, data}` | forwards (also used to withdraw a challenge) |
| `start{duel, game}` | Play pressed: reveals that game's seed to me (records carry only my started games' seeds) and starts its clock |
| `report{duel, game, score, flaps[], ticks}` | refused if unstarted, out of order, over the tick cap, faster than real time, or above the physics' score cap; **replayed by the relay**: a mismatch forfeits the reporter (CHEAT); stored, deadline refreshed, both pushed their view |
| `touch{duel}` | "still playing": refreshes the deadline (sent once a minute mid-game) |
| `concede{duel}` | forfeit |
| `compete{game, rules}` / `uncompete` | queue at these rules; the next arrival with another name is paired into a `compete` record |
| `board` / `ranks` `{game, key}` | relay-verified compete scores / Elo ratings per difficulty (hidden players omitted) |
| `admin{key, op, name, ...}` | operator: forget, release, ban, unban, removeScore |
| `history{game}` | wins, losses, draws, per-opponent record, compete record, ratings, last 20 |
| `send{to, game, kind, data}`, `who{names}` | the share and presence primitives for other plugins |

Record: `{id, game, kind, rules, players[2], seeds[10], created,
deadline, reports{name:[{game, score, flaps, ticks}]}, verdicts{name:
[bool]}, stretchEnd, status, result{winner, reason}, games[], scoreLine}`.
Settlement: best round compares each side's highest score, average the
sums; a tie extends the stretch by one sudden-death game (ten seeds,
then DRAW). A `false` verdict forfeits the reporter (CHEAT). At the
deadline a side with its stretch complete wins by TIMEOUT, neither is
NO_CONTEST; a concede is FORFEIT. Only compete records move ratings
(Elo, K 48 for the first ten games then 32, damped to half, a quarter,
then nothing against the same account in a day, and capped at +100 a
day per account) and feed the board. The compete queue never pairs the
same account, the same IP, or two accounts paired within the hour. No
duel outlives 45 minutes. A sender may challenge one player three
times in five minutes.

## L1 - the client ledger

From a record and my name: my next game (my reports so far + 1, up to
the stretch end), whether its seed is revealed yet (`start` if not),
time left, the outcome as a You/Them table. A to-do per duel: PLAY,
WAIT, DONE. Nothing runs unless the player presses Play; a game in
progress touches the relay once a minute. The client verifies nothing;
the relay replays every run (`src/sim.js`, proven against
`test/golden.json` written by the Java game).

## L2 - Trappy Chinchompa (adapter `tc1`)

`FlappyGame` on a fixed 242x480 world (rendering letterboxes), seeded
from the record's seed for the game, difficulty presets from the rules,
trace = flap tick indices + total ticks, score = traps cleared,
ten-minute cap. Reference: `com.trappychinchompa.duel` (`DuelRecord`,
`DuelLedger`, `DuelController`, `RelayClient`, `TrappyAdapter`,
`DuelRun`, `DuelCommand`, `DuelChat`).

## Porting

Embed `RelayClient`, `DuelRecord`, `DuelLedger` and a replayer; pick a
game id; the relay needs nothing new. Shares ride `send`.
