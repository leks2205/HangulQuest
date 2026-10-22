# Progress fixtures

One `progress.json` per released version, in the shape that version wrote, with synthetic data.
`ProgressCompatibilityTest` loads each one, saves it with the current code and checks that nothing was lost.

Rules:

- Never edit or delete a released fixture. It stands for files that exist on players' disks.
- Every release adds `<tag>.json`. Every change to `ProgressData`, `WordProgress` or the file layout adds a
  fixture for the new shape plus a migration test.
- `future-v99.json` checks that a file from a newer version is never overwritten, `unknown-fields.json` that
  fields this build does not know survive a save, `corrupt.json` that a broken file is moved aside, and
  `bom.json` that a byte-order mark does not break loading.
- `v3-sample.json` is the schema v3 shape (`meta.schemaHistory`, `meta.installSalt`, the persisted
  sidequest and quest-step counters). Older fixtures are migrated to v3 on load; `ProgressMigrationsTest`
  checks that every migration step is idempotent.
- `v4-sample.json` is the schema v4 shape: every word has an `fsrs` card (stability, difficulty, state, step,
  reps, lapses, lastReview, due) and some have a `ledger` (encounters, retrievals, contexts, times,
  recallDays). Older fixtures are migrated to v4 on load: the card is seeded from the old level
  (stability 0.1, 0.2, 0.5, 1, 2, 4, 8, 16, 35 days for levels 0 to 8, 30 days for a word marked known,
  difficulty 5, due = max(old dueAt, now)) and `level` is kept. `ProgressMigrationsTest` covers every level.
- `v4-placed.json` is `v4-sample.json` plus `quests.active.placed` and a `placed` map on a saved quest: the
  block that finished an earlier step, by step id, as `[x, y, z]` (the memory behind a step's `"@s1"` anchor,
  "아까 놓은 횃불 옆에"). The field is optional and was added without a version bump: a file without it means
  "nothing remembered", and a build that does not know it keeps it (`unknown-fields.json`).
- `v4-autotrack.json` is `v4-placed.json` plus the quest picker's fields in `quests`: `auto` (the tracked trails the game
  picked; the rest of `tracked` the player chose), `situational` (the trail in the situational slot, also in `auto`),
  `dismissed` (trails the player took out of the log, by `playMs` at that moment) and `playMs`. All four are optional and
  were added without a version bump: a file without them means "the player chose every tracked trail" and "nothing
  dismissed", and a build that does not know them keeps them (`unknown-fields.json`). `AutoTrackFixtureTest` covers it.
