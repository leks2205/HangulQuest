# BATCH-007 linguist review (G2)

Claude (blind) found 6 errors and 12 minors in 140 lines. Gemini 3.8 Flash, in a fresh agy project, found 15
errors and 7 minors.

| Finding | Who | Decision |
|---|---|---|
| tadpole finale: 양동이 for a water-bucket goal | both | **Fixed:** 늪에서 물 양동이를 구해요 (authored, teaches 양동이 and 물) |
| root finale: 고기 when the goal is cooked chicken | both | No change. 고기 covers every raw and cooked meat, so any meat counts. |
| fishy finale: 생선 for cooked salmon | Gemini | No change. 생선 covers every fish, raw and cooked. |
| boat trail: 배 instead of 참나무 보트 | Gemini | No change. 배 covers every boat, and it is the core word. |
| 꿀 for 꿀이 든 병, 뼈 for 뼈다귀 | both, minor | Kept. These are the taught core words, and the official name is in the item tooltip. Queued for native review. |
| 키우다 for breeding | Claude, minor | Kept for now. Queued for native review; 번식시키다 is the precise verb, but it is above level 1. |

The fix adds one sentence with two unknown words: `unknownOverOneShare` goes from 0.0537 to 0.0543.
