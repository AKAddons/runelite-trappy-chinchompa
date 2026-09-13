# Async duels: relay records, idle deadlines, compete and a verified board

- Status: accepted (supersedes the live session of 0003; keeps its relay)
- Date: 2026-09-07
- Deciders: Andrew, Claude

## Context and Problem Statement

The live session over the relay worked, and the first duel showed the
shape was wrong: accepting threw the acceptor into a countdown and
locked them until the challenger finished. The owner asked for duels
you can come back to, a five-minute clock that refreshes with activity,
a duel record, and random matchmaking feeding a verified board.

## Decision

The relay keeps the book. Accept creates a record with ten server
seeds and a five-minute idle deadline; reports (score + trace) and
verdicts are stored and forwarded; every report, verdict or once-a-
minute "touch" pushes the deadline out; the relay settles (best/avg,
sudden death from the next seed, TIMEOUT for the side that finished,
NO_CONTEST for neither, FORFEIT, CHEAT on a false verdict, DRAW after
ten seeds) and keeps per-player records, a head-to-head per opponent,
a compete record with Elo per difficulty, and a per-difficulty board of
opponent-verified compete scores. Commit-reveal is withdrawn: we run
the neutral party. Clients replay and judge each other's runs whenever
they are online; the dock lists every duel with its next step.

## Consequences

- No countdown, no lock: Play is the player's own button.
- The relay stores player data (names, results, ratings): the README
  discloses it; a `forget` path is owed before the hub release.
- A duel can settle while a player is away; they see it on sync.
- Friendly-duel scores stay off the board (collusion); compete only.

## Options Considered

- Live session plus a lobby of pending duels: still needed both online
  for seeds and verification. Rejected.
- Relay-generated seeds and stored reports. Chosen.
- Server-side replay for a solo board: port the sim to JS. Not worth it.
