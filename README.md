# Puzzle Hunt

A RuneLite plugin that lets players **build, run and share custom puzzle
hunts** to challenge themselves and friends — similar in spirit to OSRS's
Treasure Trails or Gielinor Games challenges.

## Modes

- **Treasure Trail** — sequential, hidden steps. The current step is
  expanded; completed steps collapse with a green tick; future steps are
  obscured (`??????`) until the previous one is completed.
- **Diary** — every step is visible from the start and can be completed
  in any order.

## Features

- **Sidebar UI** with Create / Import-from-JSON / Resume.
- **Hunt creator** with a step list (drag-style up/down reordering in
  treasure-trail mode), per-step clue text, and per-clue-type editors.
- **Eyedropper** pickers for items (from inventory), NPCs and monsters.
- **Tile painter** — toggle a "Paint tile" entry on any tile while in
  paint mode; selected tiles are highlighted on screen.
- **Sync start** countdown to the next whole minute (≥60s notice) with
  Cancel.
- **Live timer** on the active-hunt view, with manual Pause/Resume.
  Auto-pauses on logout / hop / disconnect / plugin shutdown and
  resumes on login. Persisted to `.runelite/puzzle-hunt/progress/`
  after every state change so nothing is lost on a crash.
- **End-of-hunt summary** with per-step splits, deltas and a
  Reset-progress button (handy while testing your own hunts).

## Supported clue types

| Type             | Subtype          | Detection                                            |
|------------------|------------------|------------------------------------------------------|
| Get item         | Any source       | Inventory gain of the configured item                |
| Get item         | Monster drop     | Item appears in the loot of one of the named NPCs    |
| Get item         | Ground spawn     | Player clicks "Take" on the configured ground item   |
| Location puzzle  | NPC              | RuneLite-only "Complete clue step" entry on the NPC  |
| Location puzzle  | Tiles            | Player stands on one of the painted tiles            |

The model is structured so additional clue types can be added without
breaking existing saved hunts.

## Storage

Hunts and progress are stored as JSON in
`~/.runelite/puzzle-hunt/{hunts,progress}/`. The `hunts/` directory is
the safe one to share — copy a file in there to import, or use the
sidebar's **Import from JSON…** action.
