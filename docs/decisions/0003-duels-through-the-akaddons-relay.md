# Duels through the AKAddons relay on Cloudflare

- Status: accepted (supersedes 0002; restores the session of 0001 over a new transport)
- Date: 2026-09-07
- Deciders: Andrew, Claude

## Context and Problem Statement

0002's hand-pasted lines were field-tested with two accounts: the chat
rewrite worked, but the game's chat cannot take a paste and every step
needed a typed code. The owner asked for an endpoint of our own so a
duel is one typed command, and for the mechanism to serve every
AKAddons plugin's duel and share features.

## Decision

`akaddons-relay`, a Cloudflare Worker with one Durable Object on the
free tier: presence by display name, challenge forwarding, rooms with
broadcast, a generic `send`, `who`. It knows no game. The v1 session
(commit-reveal seeds, replay verification, heartbeats, forfeits, sudden
death) runs over a `RelayTransport`. The command is `::duel <player>
<game> [games] [style] [difficulty]` via `CommandExecuted`; right-click
on a chat name offers Duel / Accept / Decline. Duels are opt-in in the
settings; the README discloses the endpoint.

## Consequences

- One shared endpoint for all AKAddons plugins; each embeds the small
  client and speaks the spec (hub jars cannot share code).
- Identity is the reported display name; a poser can only send a
  challenge the real person must accept.
- Hub review: external endpoint, disclosure line, likely the
  sensitive-API label. Opt-in keeps consent explicit.
- Losing our own relay link after LOCK is our forfeit, so both clients
  agree on who vanished.

## Options Considered

- **Cloudflare Workers + Durable Objects.** Chosen.
- **Fly.io VM.** Costs a little, more upkeep.
- **pi5 over Tailscale Funnel.** Residential endpoint; reviewers would ask.
- **Stay with hand-pasted lines (0002).** Rejected by the field test.
