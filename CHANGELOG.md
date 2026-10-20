# Changelog

All notable changes to Hangul Quest. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [semantic versioning](https://semver.org/). Minecraft versions are named
explicitly, because a mod for 26.3 is not a mod for anything else.

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
