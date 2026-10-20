# Changelog

All notable changes to Hangul Quest. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [semantic versioning](https://semver.org/). Minecraft versions are named
explicitly, because a mod for 26.3 is not a mod for anything else.

## [1.2.0] — 2026-10-20

### Added

- **Checkbox tracking in the journal.** The Quests tab's detail card has an **Im Log** checkbox (and a
  **Fokus** button for a quest that is already in the log). Checked quests are drawn on the HUD and are
  the only ones whose goals are checked against the world.
- The HUD hint panel now always opens below the quest log, whatever its height.

### Changed

- **Only visible quests progress.** A quest that is not checked into the log keeps its saved step,
  counter, hint state and sidequest, but it does not advance — so quests can no longer complete behind
  the player's back while another quest is in focus.
- Checking a seventh quest unchecks the oldest one instead of discarding it; its progress stays saved.
- Taking a quest out of the log (checkbox, or **Abbrechen**) keeps its progress for later.

[1.2.0]: https://github.com/leks2205/HangulQuest/releases/tag/v1.2.0

## [1.1.1] — 2026-10-20

### Fixed

- The journal marks every quest in the log as tracked ("▸ im Log") instead of only the highlighted one,
  and offers a **Fokus** button to highlight a quest that is already in the log.

[1.1.1]: https://github.com/leks2205/HangulQuest/releases/tag/v1.1.1

## [1.1.0] — 2026-10-20

### Added

- **Quest log instead of a single tracker.** The HUD now lists every tracked quest with its title,
  current Korean objective, live status line and a progress bar for counting goals — modelled on how
  World of Warcraft quest trackers show accepted quests, drawn in the mod's own panel style.
- **Several quests at once.** Up to six trails are tracked in parallel and all of them are checked
  against the same world observation, each with its own step, partial counter, hint state and sidequest.
  Starting a quest adds it to the log instead of replacing the previous one; the oldest background quest
  is dropped when the log is full.
- **Focus cycling.** `V` moves the highlight through the log. Hints, sidequests and the follow-up chain
  follow the highlighted quest, so `H` answers exactly the quest you are looking at.
- `questTrackerRows` in the config (1–6) caps how many log rows the HUD draws.
- A dev run helper for UI screenshots: `./gradlew runClient -Pdemo=questlogdemo -PquickPlay="World"`.

### Changed

- Finishing a background quest marks it done with a banner and does not steal the highlight; finishing
  the highlighted quest still starts or offers its successor.
- A quest that is already tracked is only focused when you start it again, never reset.
- The old "the active quest switches to whatever you just did" behaviour is gone: with a quest log, an
  action simply counts for every tracked quest that wants it.

[1.1.0]: https://github.com/leks2205/HangulQuest/releases/tag/v1.1.0

## [1.0.0] — 2026-10-20

First public release, for Minecraft 26.3.

### Added

- **414 questlines / 1,505 steps.** Authored JSON trails in a tree 22 levels deep, with 38 roots and
  merges of up to five parents. Korean difficulty climbs from polite `-아/어요` to literary
  `-지 않고서는 … 수 없어요` following the tree depth.
- **Quest engine.** Twelve goal types (break, place, acquire, consume, look at block or entity,
  reach biome, dimension or height, use block, interact, defeat) and seven condition types (time,
  weather, altitude, biome, dimension, nearby entity, nearby block), all verified on the client.
- **Hint → sidequest → return.** `H` restates the task in level-1 Korean and starts a two-question
  sidequest on the blocking word; the original step waits until it is finished. Known words skip the
  sidequest.
- **Tracker.** Current Korean sentence, live status line (`0/3`, `지금 낮 · 밤 필요`, `Y 62 / 140 위`)
  and non-modal step, sidequest and trail celebrations with sounds and particles.
- **Journal.** A hand-drawn notebook screen with info, dictionary, learning, achievements, trail tree
  and options, plus a full-text dictionary of curated, vanilla and imported words.
- **Vocabulary.** 293 curated concepts, generated vanilla names, and a read-only Lute CSV import
  (`config/hangulquest/vocab/lute-vocab.csv`, statuses 1–5 and 99).
- **Learning layer.** Leitner spaced repetition, daily review trail, streak and fifteen achievements.
- **Content pipeline.** JSON questlines hot-reloaded from `config/hangulquest/questlines/` every two
  seconds, a template, a scaffolding script, a JSON schema, an authoring guide, an attribute
  reference and a validation test that rejects dead ends, unspoken conditions and wrong levels.
- **Bundled Korean font**, a Noto Sans CJK KR subset, so Hangul renders identically on every machine.

### Known limitations

- Minecraft 26.3 only, Java 25, client-side only.
- Interface text is German; quest text is Korean with an optional German line.
- No pronunciation audio, no romanisation, no Korean keyboard input.
- Progress is stored per instance and is not synchronised between machines.

[1.0.0]: https://github.com/leks2205/HangulQuest/releases/tag/v1.0.0
