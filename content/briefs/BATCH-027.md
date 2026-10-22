# Content brief BATCH-027: small poems (w9, w10), level 5

**Rules:**
- `content/briefs/L456-PLAN.md` and `BATCH-016.md` to `BATCH-018.md` apply.
- Read `content/batches/BATCH-024/` and `BATCH-025/` (the level-4 models).
- The level follows depth.
- Finales claim only what the goal checks (see COMMON).

**Trails (ids kept):** `w9_01` to `w9_08`, `w10_01` to `w10_07` (15).

**Chapter feel.** Small poems: a mood in three or four steps, now at level 5. A while-clause (해가 지는 동안), a concession (밤이 되어도 … 마세요) or a colour adjective (빨간 꽃) carries the step. Keep a title when its theme is reachable; otherwise retitle.

## Grammar (generator wave 11 provides it)

- **Level 4 trails:** as in BATCH-024 (-(으)면, -아/어서, -(으)러 가다; -지만 in hints and finales).
- **Level 5 form-dependent constructions:**
  - colour adjectives as modifiers (빨간 침대);
  - -는 동안 on WAIT with a condition and on HOLD_WHILE with an inner goal;
  - -아/어도 on AVOID windows (밤이 되어도 자지 마세요).
- **Level 5 hints and finales** may use -(으)니까, -는데 and -아/어도.
- **-(으)ㄹ게요 and 합쇼체** are dialogue only, never in clues or finales.
- **Also usable:** the level-4 forms and the goals SHOOT, WALK, FACING and AVOID on creatures.
- **Target:** at least 30% form-dependent steps.

## Known generator limits (work around them; report any new ones)

- An -(으)러 가다 step needs a second spoken condition.
- SHOOT needs a target.
- PILLAR and ROW allow at most 4 blocks.
- WALK under 40 blocks.
- REACH_HEIGHT reads 높이 N 블록 위로 (a wording fix is pending; it is fine to use).
- Boats print 배에 앉다 (a fix is pending; avoid MOUNT on boats).
- A SEQUENCE PUT_IN then TAKE_FROM repeats the anchor (avoid it).

## Target words

Recycle the core words still below target (`build/reports/content/recycling-gaps.csv` after `tools/gates.sh`).
Favour the lowest encounter counts.

## Coordination

- BATCH-026 runs in parallel. Leave slack:
  - no new duplicate signatures;
  - no (verb, object) pair raised over 3;
  - `unknownOverOneShare` strictly lower than when you started.
- Do not rebaseline; the lead does that after both batches merge.
- Only insert entries into `concepts.json`.
- Run `tools/gates.sh` unpiped.

## Gates and handback

As in BATCH-016, with `content/batches/BATCH-027/`.
