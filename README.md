# Trappy Chinchompa

![Mid-run](docs/screenshot-run.png) ![The kaboom scorecard](docs/screenshot-kaboom.png) ![Start screen](docs/screenshot-start.png)

Level Old School RuneScape's newest skill: Anti-Hunter! As a chinchompa
escaping all those traps players keep laying, gain xp for every trap
you avoid - and enjoy the grind.

A flappy-style arcade game in your RuneLite sidebar. Touch a trap and
KABOOM.

## Features

- **One button** - click or Space to flap.
- **Anti-hunter xp** - the skill of not getting caught. Every trap pays more
  than the last (+5, +15, +25, ...), with optional in-game-style xp drops.
  Your lifetime total runs the real OSRS curve to an Anti-hunter level.
  99 is 13,034,431 xp. It can be done. It should not be done.
- **88 achievements** - score and level milestones, consistency streaks,
  one-offs (dying at exactly zero counts), and at least one secret.
- **Unlockables** - critters unlock by high score (grey 10 / red 20 / black
  40, then seven sealed ??????s); zones by level, Feldip Marsh at 1 to
  Prifddinas at 99. Unlocks announce the moment they land, mid-run included.
- **Real game sprites** - traps and most critters are item sprites loaded
  from your own client at runtime.
- **Three difficulties, honestly priced** - Easy pays x0.1 xp and never sets
  records; Hard pays x2.5. Difficulty locks when a run launches.
- **Polite by design** - the game only runs while its panel is the open
  sidebar tab; closed, it costs nothing.

## Playing

Click the chinchompa icon in the sidebar, then click (or press Space) to
flap. The start screen holds the **Difficulty** toggle and the **Critter**,
**Zone**, **Stats**, and **Achievements** buttons - pickers show every
option's actual look, with locked rows greyed until earned.

## Configuration

| Option | What it does |
| --- | --- |
| Difficulty | Easy (x0.1 xp, no records) / Normal / Hard (x2.5 xp). |
| XP drops | Float +xp above the chin on every dodge. |

**Reset progress** (bottom of the Stats card) wipes progress only; the config
panel's standard **Reset** is the full factory reset.

## Development

- `./gradlew run` - dev client with the plugin sideloaded. In
  `--developer-mode` only: 5x-tap **Stats** toggles all unlocks, 5x-tap
  **Zone** toggles trap invincibility - both watermarked DEBUG MODE,
  sandboxed, and branded on anything earned.
- `./gradlew test` - headless game-model tests.
- `./gradlew preSubmit` - tests + Plugin Hub token and glyph gates.
- `python3 scripts/generate_icons.py` - regenerates the original icon art.
  No Jagex assets are bundled; sprites load at runtime via ItemManager.
