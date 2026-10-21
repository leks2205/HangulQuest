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
