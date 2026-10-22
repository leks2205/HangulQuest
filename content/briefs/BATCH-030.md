# Content brief BATCH-030: small poems (w11, w12), level 6 — the last batch

**Rules:**
- `content/briefs/L456-PLAN.md` and `BATCH-016.md` to `BATCH-018.md` apply.
- Read `content/batches/BATCH-029/` (the level-6 model) and `BATCH-026/`, `BATCH-027/` and their `ling-review.md` when present.
- The level follows depth.
- Finales claim only what the goal checks (see COMMON).

**Trails (ids kept):** `w11_06`, `w11_07`, `w11_08`, `w12_01` to `w12_08` (11).

**Chapter feel.** The last small poems: a mood in three or four steps at level 6. The level 4–5 forms carry the steps
(-(으)면, -아/어서, -는 동안, -아/어도, colour adjectives). The level-6 constructions live in hints and finales, where
they are true (높이 올라갈수록 추워져요; 해가 지듯이 …; 밤이 오기 마련이에요). `w12_08` is the very last trail of the
course: give it a quiet, honest ending that looks back at the journey, without claiming anything the goal does not check.

## Grammar (generator wave 11 provides it)

- **Level 5 trails:** as in BATCH-026 (colour adjectives, -는 동안, -아/어도; -(으)니까 and -는데 in hints and finales).
- **Level 6:** the clue ending stays -아/어야 해요.
  - Hints and finales may use -(으)ㄹ수록 (높이 올라갈수록 추워져요), -듯이 and -기 마련이다.
  - -(으)ㄹ 수밖에 없어요 is allowed only where the sentence states a forced conclusion with its reason in the same sentence. Never use it as an instruction.
  - 반말 and 합쇼체 are dialogue only.
- **Every form from levels 4–5** stays usable.
- **Target:** at least 30% form-dependent steps.

## Known generator limits (work around them; report any new ones)

- Colours (wave 12b): from level 3 a named colour is checked exactly, so use exact coloured ids and count a colour as
  form-dependent only when the goal names the coloured id.
- An -(으)러 가다 step needs a second spoken condition.
- PILLAR and ROW allow up to 7 blocks; a pillar with something on top fits the playtest at 3.

## Target words

Recycle the core words still below target (`build/reports/content/recycling-gaps.csv` after `tools/gates.sh`).
Favour the lowest encounter counts.

## Coordination

- BATCH-028 runs in parallel, and generator wave 12b (colours) may land meanwhile. Leave slack:
  - no new duplicate signatures;
  - no (verb, object) pair raised over 3;
  - `unknownOverOneShare` strictly lower than when you started.
- Merge phase0 before your final gates run.
- If `de_de-status.json` conflicts, keep phase0's and rerun `translate.py adopt --lang de_de --state proofread --refresh`.
- Do not rebaseline.
- Only insert entries into `concepts.json`.
- Run `tools/gates.sh` unpiped.

## Gates and handback

As in BATCH-016, with `content/batches/BATCH-030/`.
