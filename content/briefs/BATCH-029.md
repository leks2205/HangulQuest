# Content brief BATCH-029: the End game (level 6)

**Rules:**
- `content/briefs/L456-PLAN.md` and `BATCH-016.md` to `BATCH-018.md` apply.
- Read `content/batches/BATCH-026/` and `BATCH-027/` (the level-5 models) and their `ling-review.md` when present.
- The level follows depth.
- Finales claim only what the goal checks (see COMMON).

**Trails (ids kept):** `regenbogenfahne`; `adv_end_root`, `adv_end_kill_dragon`, `adv_end_dragon_breath`, `adv_end_dragon_egg`, `adv_end_elytra`, `adv_end_enter_end_gateway`, `adv_end_find_end_city`, `adv_end_levitate`, `adv_end_respawn_dragon`; `w11_01` to `w11_05` (15).

**Chapter feel.** The End game: the rainbow banner that closes the colour chain, the End advancements (official titles kept; each trail should really earn its advancement where level 6 allows it: the elytra, the dragon egg, respawning the dragon) and five small poems.

## Grammar (generator wave 11 provides it)

- **Level 5 trails:** as in BATCH-026 (colour adjectives, -는 동안, -아/어도; -(으)니까 and -는데 in hints and finales).
- **Level 6:** the clue ending stays -아/어야 해요.
  - Hints and finales may use -(으)ㄹ수록 (높이 올라갈수록 추워져요), -듯이 and -기 마련이다.
  - -(으)ㄹ 수밖에 없어요 is allowed only where the sentence states a forced conclusion with its reason in the same sentence. Never use it as an instruction.
  - 반말 and 합쇼체 are dialogue only.
- **Every form from levels 4–5** stays usable.
- **Target:** at least 30% form-dependent steps.

## Known generator limits (generator wave 12 is fixing them; work around them)

- A generic taught word drops the colour of a coloured variant. Teach the colour word, or keep the variant's own concept.
- 검은 counts as unknown, so avoid black as an adjective.
- Compound anchors are shortened (영혼 랜턴 becomes 랜턴).
- The verbs 숫돌을 열다 and 신호기를 열다: avoid USE on grindstones and beacons.
- An -(으)러 가다 step needs a second spoken condition.
- SHOOT needs a target.
- PILLAR and ROW allow at most 4 blocks.
- WALK under 40 blocks.
- Avoid MOUNT on boats.
- The GeneratorWaveFourTest USE_BLOCK floor (more than 40 plain USE_BLOCK steps) must still hold.

## Target words

Recycle the core words still below target (`build/reports/content/recycling-gaps.csv` after `tools/gates.sh`).
Favour the lowest encounter counts.

## Coordination

- BATCH-028 runs in parallel, and generator wave 12 may land meanwhile. Leave slack:
  - no new duplicate signatures;
  - no (verb, object) pair raised over 3;
  - `unknownOverOneShare` strictly lower than when you started.
- Merge phase0 before your final gates run.
- If `de_de-status.json` conflicts, keep phase0's and rerun `translate.py adopt --lang de_de --state proofread --refresh`.
- Do not rebaseline.
- Only insert entries into `concepts.json`.
- Run `tools/gates.sh` unpiped.

## Gates and handback

As in BATCH-016, with `content/batches/BATCH-029/`.
