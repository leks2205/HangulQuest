# Files and backups

Everything Hangul Quest knows lives in one folder in your instance:

```
config/hangulquest/
├── config.json              settings (mirrors the journal's options)
├── progress.json            words, SRS levels, streak, achievements, trail state
├── questlines/              one JSON per trail, hot-reloaded
│   ├── _template.json       starting point for your own trail
│   ├── index.json           generated file list, rewritten by the build
│   └── … 414 shipped trails
└── vocab/
    └── lute-vocab.csv       your Lute export (optional, read-only)
```

On a default installation that is `.minecraft/config/hangulquest/`; with the Flatpak build it is
`~/.var/app/com.mojang.Minecraft/.minecraft/config/hangulquest/`.

## progress.json

Holds, in plain JSON:

- every word you have met, its SRS level and when it is due,
- which day keys count for the streak, and your best streak,
- unlocked achievements with their dates,
- the active trail, its step, partial counters, sidequest state and hints used,
- paused trails (`quests.saved`) and completed trails with their badges,
- the last Lute import summary.

It is written atomically (a temporary file is renamed over the old one) and debounced, so a crash mid-write
cannot leave a half file.

**Two safety nets:**

- A **format migration** copies the old file first: `progress.v1-backup-<stamp>.json`.
- An **unreadable file** is renamed to `progress.json.broken-<stamp>` instead of being overwritten, and
  the mod starts fresh. The broken file is still there if you want to salvage it by hand.

The **Fortschritt zurücksetzen** button in the journal writes
`progress.json.backup-pre-reset-<stamp>` before it wipes anything.

## Making your own copy

Progress is not synced between machines; copy the folder yourself if you want to move a journey.

```bash
# Linux / macOS — back up before a mod update
cp -r ~/.minecraft/config/hangulquest ~/hangulquest-backup
```

On Windows the folder is `%appdata%\.minecraft\config\hangulquest`.

## config.json

The full set of settings, with the defaults that matter:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | master switch |
| `hudCards` | `true` | step, sidequest and trail cards |
| `tooltips` | `true` | word tooltips on items |
| `autoQuiz` | `true` | ambient quiz while standing still |
| `quizIntervalMinutes` | `7` | how often that quiz may appear |
| `cardDurationMs` | `4500` | how long a card stays |
| `lookAtCards`, `mobCards`, `biomeCards`, `timeCards` | `true` | which ambient cards may appear |
| `sound` | `true` | celebration sounds |
| `hudCorner` | `BOTTOM_RIGHT` | corner for ambient cards |
| `quests`, `questTracker` | `true` | quest system and tracker |
| `questTrackerCorner` | `TOP_LEFT` | corner for the quest log |
| `questTrackerRows` | `4` | how many quests the log draws at once (1–6) |
| `questGerman` | `false` | German line in quests |
| `journalTranslations` | `false` | translations in the journal |
| `luteImport` | `true` | read the Lute CSV |

Edit it with the game closed; the journal's **Optionen** tab writes the same file.

## The questline folder

`questlines/` is created on first start and filled with every shipped trail, so you can copy one, change
it and reload without restarting. See the [authoring guide](questlines.md) for the format and the
hot-reload rules.

## What is *not* written

No log of chat, no player names, no server addresses, no timestamps beyond the day keys that the streak
needs. Nothing leaves the machine.

Next: [Troubleshooting](troubleshooting.md).
