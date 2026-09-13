# Portable three-layer duel protocol over RuneLite Party

- Status: accepted
- Date: 2026-08-06
- Deciders: Andrew, Claude

## Context and Problem Statement

v2 adds head-to-head challenges: challenge a friend via private message,
both plugins coordinate a fair series (any difficulty; best score or best
average; 1/3/5/7 games), with cheating or disconnection forfeiting to the
opponent. Andrew additionally wants the challenge format portable to
future non-RuneLite apps. Two hard platform constraints shape everything:
plugins may READ private messages but must never SEND chat (input
automation - Jagex rules, hub rejection), and hub plugins cannot share
code (per-jar classloader isolation), so cross-app reuse means a spec,
not a library.

## Decision

Three layers, spec-first (docs/DUEL_PROTOCOL.md is the portable
artifact):

1. **Transport (L0)** - RuneLite's Party websocket (`PartyService.
   changeParty` with a DERIVED passphrase, one custom
   `PartyMemberMessage` carrying JSON envelopes, `UserJoin`/`UserPart`
   presence). Verified public API in 1.12.35.
2. **Session (L1)** - pure-Java, zero RuneLite/Trappy imports: handshake
   with rules echo, per-game commit-reveal shared seeds, report/verify
   loop, heartbeats, forfeit matrix, series aggregation with sudden-death
   ties. Fully unit-testable headless.
3. **Game adapter (L2)** - Trappy binding: FIXED 242x480 virtual
   viewport simulation (determinism across differently-sized panels),
   seeded runs, flap-tick input traces, headless re-simulation of the
   opponent's trace to verify claimed scores.

The invite alone rides the DM, composed by the plugin but SENT BY THE
PLAYER ("Trappy duel! Hard, best score of 3 [tc1:h-bs3-<nonce>]"); both
clients parse it (PRIVATECHAT / PRIVATECHATOUT) and derive the private
party key from it.

## Consequences

- Anti-cheat is structural: commit-reveal kills seed-grinding, mirror
  seeds kill layout luck, replay verification kills score-editing. The
  irreducible residual is a bot that genuinely plays well.
- The session core ports to Andrew's future apps by rebinding L0/L2;
  JSON envelopes keep it language-neutral.
- Duel simulation must diverge from solo rendering (fixed virtual
  viewport) - a letterbox/scale pass on GameCanvas.
- Depending on party.runelite.net adds a service dependency and likely
  the routine "sensitive API" manual-review label on the hub PR.
- v2 ships as one release after 1.0.0 merges (hub throttle favors
  fewer, larger PRs).

## Options Considered

- **Pure-DM protocol** - rejected: no programmatic sending; every
  protocol frame would be hand-typed, and a hand-typed channel cannot
  carry anti-cheat.
- **Independent random layouts per player** - rejected (Andrew):
  mirror-seed duels are the fairness model.
- **Custom relay server** - rejected: infrastructure to run, a harder
  hub review, and RuneLite already operates a sanctioned peer channel.
- **Score-only trust (no replay verification)** - rejected: trivially
  cheatable, and the headless deterministic FlappyGame makes
  verification nearly free.
