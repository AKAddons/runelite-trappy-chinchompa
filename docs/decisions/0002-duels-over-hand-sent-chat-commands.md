# Duels over hand-sent chat commands, not the party service

- Status: accepted (supersedes the transport and session halves of 0001)
- Date: 2026-09-07
- Deciders: Andrew, Claude

## Context and Problem Statement

0001 chose RuneLite's party websocket as the two-peer channel and built
a live session on it (heartbeats, commit-reveal seeds, replay
verification). It was implemented and then withdrawn on the owner's
call: two players who are not in a party together must be able to
challenge and compete, and the party service should not be involved at
all. Plugins still cannot send chat, and hub plugins cannot share code.
Chat-command plugins (`!kc`, Rune Profile's `!log`) show the available
primitive: a line the player sends is rewritten on screen for everyone
with the plugin.

## Decision

Every duel message is a `!duel` line the player pastes as a private
message: challenge, accept, decline, results, concede. The plugin
rewrites the lines on screen (`ChatCommandManager` +
`MessageNode.setRuneLiteFormatMessage`), adds "Accept duel" /
"Decline duel" to the challenger's name in the chatbox
(`MenuEntryAdded`), derives every game's seed from the rules, both
nonces and both names, and settles when both results lines have been
seen. Scores are on honour with a tamper-evident check code. No live
link, no timeouts, no online-together requirement; the one duel in
flight persists per config profile. The party code was deleted.

## Consequences

- Cheating by score is possible and documented. Seed shopping by the
  acceptor is theoretically possible with four hex digits and not worth
  defending once scores are on honour.
- The portable spec shrinks to a grammar, a seed derivation and series
  rules, which port to any app with a text channel.
- Pre-filling the private-message box so a click sends without a paste
  is technically available (`Client.setVarcStrValue`) and parked: hub
  reviewers treat chat automation harshly.

## Options Considered

- **Party service (0001).** Withdrawn by the owner.
- **Own relay server.** Infrastructure and a harder review; not wanted.
- **Hand-sent commands with honour scores.** Chosen.
