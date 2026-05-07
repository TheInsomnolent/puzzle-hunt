# Puzzle Hunt

A RuneLite plugin that lets players **build, run and share custom
puzzle hunts** — community-made challenges in the spirit of Treasure
Trails, Gielinor Games or a tabletop scavenger hunt. Build a hunt
from a list of clue-style steps, race friends through it on a
synchronised countdown, and compare splits at the end.

![Create a new hunt](https://raw.githubusercontent.com/TheInsomnolent/puzzle-hunt/master/wiki-images/create-new.png)

## What it is

A puzzle hunt is an ordered (or unordered) list of **steps**. Each
step has some flavour text and a *clue type* — pick up an item, kill
a monster, walk to a tile, gain XP, etc. The plugin watches the game
state and automatically ticks each step off when its condition is
met. There is no manual "I did it" button — completion is detected
from real game events, so races stay honest.

Hunts are entirely **player-authored**. The plugin ships with no
content; you (or someone you trade hunt codes with) decide what the
challenge is.

## Getting started

1. Open the **Puzzle Hunt** sidebar.
2. Click **Create new hunt** to open the editor, or **Import from
   clipboard** to paste a hunt code shared by a friend.
3. Give the hunt a name, pick a mode (Treasure Trail / Diary), then
   add steps from the **+ Add step** button.
4. Optionally set a **starting tile**, **starting items** checklist
   and **briefing instructions** so players know how to prepare.
5. Back on the hunt's detail page, hit **Start now** (or **Sync
   start** to sync with friends — see below) and play through it.

## Modes

The plugin supports two top-level play styles. Hunts can also be
broken into **chapters**, and each chapter can independently choose
its own mode.

### Treasure Trail mode

Steps are revealed and completed **in order**, exactly like an OSRS
clue scroll. The current step is shown expanded, completed steps
collapse with a green tick, and future steps are obscured (`??????`)
until you reach them. Good for narrative hunts where each clue
points to the next.

### Diary mode

![Diary mode](https://raw.githubusercontent.com/TheInsomnolent/puzzle-hunt/master/wiki-images/diary-mode.png)

Every step is visible from the start and may be completed in **any
order**. Modelled after the Achievement Diaries — players see the
full to-do list and can route the most efficient path through it
themselves.

### Chapters (mixing both)

A hunt can be split into numbered chapters. Within a chapter the
mode determines whether its steps are sequential or freeform; you
must finish all of a chapter's steps before the next chapter
unlocks. This lets you build, e.g., a Diary-style "open" prologue
that gates a Treasure-Trail-style chase finale.

## Pre-hunt briefing

![Starting checklist](https://raw.githubusercontent.com/TheInsomnolent/puzzle-hunt/master/wiki-images/starting-checklist.png)

Each hunt's detail page shows a briefing built from optional
metadata you can set in the editor:

- **Instructions** — free-form text shown above the buttons.
- **Starting tile** — coordinates the player should begin at. When
  set, a green tile marker is drawn in-world while you're on the
  detail page so it's easy to find.
- **Starting items** — a checklist of items that should be in your
  inventory before you start. Rows are highlighted **green when you
  have the item** and **red when you don't**, Quest-Helper-style,
  and refresh whenever your inventory changes.

None of this is enforced — it's purely a pre-flight checklist so
everyone shows up on the same starting line.

## Synced play

![Synchronised countdown](https://raw.githubusercontent.com/TheInsomnolent/puzzle-hunt/master/wiki-images/sycnhronised-countdown.png)

Two start modes are available from the detail page:

- **Start now** — short 3-2-1-Go countdown, then the timer begins.
- **Sync start** — schedules the start to the next 15-second
  wall-clock boundary that's at least 15 seconds away. Everyone in
  your group hits **Sync start** within the lead-in window and the
  hunt begins on the same tick on every client. A **Cancel
  countdown** button is shown while it's pending.

Once running:

- A **live timer** ticks up on the active-hunt view, with manual
  **Pause / Resume** buttons.
- The hunt **auto-pauses** on logout, world hop, disconnect, or
  plugin shutdown, and **auto-resumes** on login. So a crash or a
  forced logout doesn't ruin a run.
- Progress is **persisted to disk** after every state change, so you
  can close RuneLite mid-run and resume from the sidebar.

## Tracking progress

![Progress trackers](https://raw.githubusercontent.com/TheInsomnolent/puzzle-hunt/master/wiki-images/progress-trackers.png)

Steps with a numeric goal (kill counts, GP gained, XP earned, etc.)
show a per-step progress bar so you can see how close you are to
finishing each one without leaving the active-hunt view.

## End-of-hunt summary

![Times and splits](https://raw.githubusercontent.com/TheInsomnolent/puzzle-hunt/master/wiki-images/times-and-splits.png)

When the last step ticks off, the plugin jumps to a **Summary**
view showing each step's split and delta from the previous one.
Compare with friends to see where time was won or lost. A
**Reset progress** button is available so you can re-run the same
hunt without rebuilding it.

## Sharing hunts

Hunts are stored as JSON, but the friendly way to share them is the
**hunt code** — a single base64 string you can paste into Discord,
forums, etc.

- **Export** — open the hunt's detail page and click
  **Export (copy code)**. The hunt code is copied to your clipboard.
- **Import** — open the sidebar, click **Import from clipboard**,
  and the plugin will decode whatever's on your clipboard. Both raw
  JSON and base64-encoded codes are accepted, so power users can
  share files directly if they prefer.

Imported hunts are saved under a fresh id, so you can safely import
a friend's copy of a hunt you already have without overwriting your
own progress.

Hunts and progress live on disk under
`~/.runelite/puzzle-hunt/{hunts,progress}/`. The `hunts/` directory
is the safe one to back up or move between machines.

## Clue types

The model is designed so new clue types can be added without
breaking saved hunts. Currently supported:

| Type                  | What it tracks                                                                  |
|-----------------------|---------------------------------------------------------------------------------|
| **Get item**          | Acquire a specific item. Sub-modes restrict *how* it can be obtained:           |
|  — Any source         | Any inventory gain of the item (shop, trade, drop, …).                          |
|  — Monster drop       | The item appears in the loot pile of one of the named NPCs.                     |
|  — Ground spawn       | Player picks the item up from a ground spawn (the **Take** menu option).        |
| **Location puzzle**   | Free-text clue completed by interacting with a configured target:               |
|  — NPC                | A RuneLite-only "Complete clue step" menu entry on the named NPC.               |
|  — Tiles              | Player stands on one of the painted tiles. Tiles are captured by walking the    |
|                       | area's perimeter while in **Walk loop** record mode — the convex hull is        |
|                       | filled in for you.                                                              |
| **Kill monster**      | Kill named monsters a configurable number of times.                             |
| **Die**               | Die. Useful for forfeit / speed-death hunts.                                    |
| **Gain GP**           | Accumulate a target amount of GP (positive coin-stack deltas in inventory).     |
| **Gain XP**           | Accumulate XP, optionally restricted to one or more specific skills.            |
| **Secret password**   | The player must type a configured password into the active-hunt panel.          |

Steps may have a custom title and clue body text shown to the
player, plus per-type fields (item ids, npc names, target counts,
tile lists, etc.) edited from the step editor.

## Authoring tips

- Use **Walk loop** for tile-based steps: enter record mode, walk
  the boundary of the valid area, then **Finish loop**. The plugin
  takes the convex hull, so you don't have to step on every tile.
- The **item picker** is backed by `ItemManager.search()` and shows
  real item icons, so you don't need to memorise item ids.
- The **duplicate step** button (`⎘`) is the fastest way to build
  a long similar-looking hunt — clone, tweak, repeat.
- Default hunt **author** is auto-filled from your RSN when you
  create a new hunt.

## Storage

| Path                                  | What's there                              |
|---------------------------------------|-------------------------------------------|
| `~/.runelite/puzzle-hunt/hunts/`      | One JSON file per hunt. Safe to share.    |
| `~/.runelite/puzzle-hunt/progress/`   | Per-hunt run state (timer, completions).  |

Deleting a hunt from the UI removes both files for that hunt.
