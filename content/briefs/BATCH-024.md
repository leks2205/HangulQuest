# Content brief BATCH-024: into the End and the far kingdoms (level 4)

**Rules:**
- `content/briefs/L456-PLAN.md` and `BATCH-016.md` to `BATCH-018.md` apply.
- Read `content/batches/BATCH-023/` (the newest model, with honest finales and timeless flavour lines) and the
  BATCH-017 level-4 trails.
- The level follows depth.
- Finales claim only what the goal checks (see COMMON).

**Trails (ids kept):** `kronenlicht`, `ewiges_eis`, `pyramidenpfad`, `bibliothekpfad`, `leuchtturmpfad`,
`drachenhort`, `tempelwaechter`, `sternenkarte`, `handelsflotte`, `adv_nether_all_effects`,
`adv_nether_create_full_beacon`, `adv_story_enter_the_end`, `wolkenpfad`, `unendlichereise`, `reisekrone` (15).

**Chapter feel.** These are the far kingdoms: the End, ocean monuments, woodland mansions, trial chambers, libraries,
lighthouses and fleets. Each trail is one coherent project.
- The dragon fight comes only as the level's last step.
- Advancement trails keep their official advancement title.
- If depth says a trail is level 5 or 6 (for example `adv_nether_create_full_beacon` or `adv_nether_all_effects`),
  rebuild it at that level and report it.

## Grammar (generator wave 11 provides it)

- **Clue ending:** -아/어야 해요.
- **Form-dependent constructions:**
  - -(으)면 on time and weather conditions (밤이 되면 …);
  - SEQUENCE connectors `EOSEO` (돌을 캐서 화로를 만들어야 해요);
  - `EUREO` (…을 베러 숲으로 가야 해요), which needs REACH_BIOME first and an IN_BIOME condition.
- **Use the new goals where they fit:** SHOOT (활로 과녁을 맞히세요), WALK with a direction, FACING, AVOID on creatures.
- **Hints and finales** may use -지만.
- **-(으)ㄹ게요** is dialogue only and not allowed in finales.
- **Target:** at least 30% form-dependent steps across the batch.

## Target words

- Recycle the core words still below target, listed in `build/reports/content/recycling-gaps.csv` after
  `tools/gates.sh` runs. Favour the lowest encounter counts.
- Teach the direction words 북쪽/남쪽/동쪽/서쪽, where a WALK or FACING step really uses them.

## Coordination

- BATCH-025 runs in parallel. Leave slack:
  - no new duplicate signatures;
  - no (verb, object) pair raised over the baseline;
  - `unknownOverOneShare` strictly lower than when you started.
- Only insert entries into `concepts.json`.
- Run `tools/gates.sh` unpiped.

## Gates and handback

As in BATCH-016, with `content/batches/BATCH-024/`.
