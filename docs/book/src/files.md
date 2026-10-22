# Files and backups

Everything Hangul Quest knows lives in one folder in your instance:

```
config/hangulquest/
├── config.json              settings (mirrors the journal's options)
├── progress.json            the file of 2.x, kept untouched as a copy and a way back
├── profiles/
│   └── <account-id>/        one folder per Minecraft account
│       ├── progress.json    words, review cards, streak, achievements, trail state
│       ├── reviews.ndjson   the review log
│       └── backups/         a daily and a weekly copy of progress.json
├── questlines/              one JSON per trail, hot-reloaded
│   ├── _template.json       starting point for your own trail
│   └── … 422 shipped trails
└── vocab/
    └── lute-vocab.csv       your Lute export (optional, read-only)
```

On a default installation that is `.minecraft/config/hangulquest/`; with the Flatpak build it is
`~/.var/app/com.mojang.Minecraft/.minecraft/config/hangulquest/`.

## progress.json

Each Minecraft account has its own `profiles/<account-id>/progress.json` (the schema is version 4). The first time
a profile opens, the `progress.json` of an older version is **copied** into it and the old file is left alone, so
the 2.x file stays a way back. The file holds, in plain JSON:

- every word you have met, its review card (stability, difficulty, due time) and its old level,
- which day keys count for the streak, and your best streak,
- unlocked achievements with their dates,
- the focused trail, its step, partial counters, sidequest state and hints used,
- the quest log (`quests.tracked`) and every other trail you started (`quests.saved`), all with their
  progress, plus completed trails with their badges,
- the last Lute import summary.

It is written atomically (a temporary file is renamed over the old one) and debounced, so a crash mid-write
cannot leave a half file.

**Two safety nets:**

- A **format migration** copies the old file first: `progress.v<N>-backup-<stamp>.json`, where N is the version
  it was written in. Daily and weekly copies are kept in `backups/`.
- An **unreadable file** is renamed to `progress.json.broken-<stamp>` instead of being overwritten, and
  the mod starts fresh. The broken file is still there if you want to salvage it by hand.

The **Reset progress** button in the journal's settings writes
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
| `autoCloseWindows` | `false` | `true`: HUD windows close by themselves after the old timeouts; `false`: they stay until the hint key closes them |
| `cardDurationMs` | `4500` | how long an ambient word card stays when `autoCloseWindows` is on |
| `lookAtCards`, `mobCards`, `biomeCards`, `timeCards` | `true` | which ambient cards may appear |
| `sound` | `true` | celebration sounds |
| `hudCorner` | `BOTTOM_RIGHT` | corner for ambient cards |
| `quests`, `questTracker` | `true` | quest system and tracker |
| `questTrackerCorner` | `TOP_LEFT` | corner for the quest log |
| `questTrackerRows` | `4` | how many quests the log draws at once (1–6) |
| `language` | `"AUTO"` | interface language: `AUTO` (follows the game) or a code: `en_us`, `de_de`, `es_es`, `fr_fr` |
| `autoPickQuests` | `true` | the game fills the quest log |
| `retention`, `newWordsPerDay` | `0.88`, `10` | review settings |
| `typedCards` | `true` | typed answers on stable words |
| `audio` | `true` | play voice-pack clips when the pack is installed |
| `questTranslations` | `false` | translation line in quests (in the interface language) |
| `journalTranslations` | `false` | translations in the journal |
| `luteImport` | `true` | read the Lute CSV |

Edit it with the game closed; the journal's **Settings** tab writes the same file.

## The questline folder

`questlines/` is created on first start and filled with every shipped trail, so you can copy one, change
it and reload without restarting. See the [authoring guide](questlines.md) for the format and the
hot-reload rules.

## What is *not* written

No log of chat, no player names, no server addresses, no timestamps beyond the day keys that the streak
needs. Nothing leaves the machine.

Next: [Troubleshooting](troubleshooting.md).
