# Puzzle Hunt

A RuneLite plugin that lets players **build, run and share custom puzzle
hunts** to challenge themselves and friends — similar in spirit to OSRS's
Treasure Trails or Gielinor Games challenges.

Two modes are planned:

- **Treasure Trail** — sequential, hidden steps. Future steps are obscured
  until the previous one is completed.
- **Diary** — every step is visible from the start and can be completed in
  any order.

## Status

This repository currently contains the **base plugin scaffolding** —
config, data model, JSON persistence in `.runelite/puzzle-hunt/` and a
sidebar entry-point with **Create**, **Import** and **Start** actions. The
full creation and active-hunt UX (clue editors, sync-start countdown, tile
painter, end-of-hunt splits, etc.) will be added in follow-up PRs.
