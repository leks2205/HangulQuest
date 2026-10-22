# BATCH-016 linguist review (G2)

Claude (blind) found 7 errors and 9 minors in 152 lines. All 16 findings are applied; where the reviewer's
suggestion could be improved, the wording differs from the suggestion. A finale may claim only what its goal checks.
Each fix lives in the questline JSON (`finale.textKo`), in `korean-lines.tsv`, and in the EN and DE lang lines.

| Finding | Who | Decision |
|---|---|---|
| distract_piglin finale: "the piglin did not get angry" | Claude, error | **Fixed:** the second sentence is dropped; the goal checks only Give, then open the chest. |
| explore_nether finale: "하지만 멋졌어요" mixes tenses | Claude, minor | **Fixed:** 네더는 뜨거워요. 하지만 멋져요. (present, a general statement). |
| fast_travel finale: "한 걸음이 여덟 걸음" lacks its reference | Claude, minor | **Fixed:** 네더에서 한 걸음은 오버월드의 여덟 걸음과 같아요. (the second linguist found "이에요" imprecise) |
| find_bastion finale: "모두 조용했어요" unchecked | Claude, minor | **Fixed:** the sentence is dropped. Also dropped "네더의": the goal checks a piglin nearby, not the dimension. The finale is now the clue's sentence in the past. |
| find_fortress finale: claims 네더 요새 | Claude, error | **Fixed:** 네더에서 블레이즈를 바라봤어요. 블레이즈는 불덩이를 쏴요. 바라보다 instead of the reviewer's 보다, to match the clue's verb. |
| obtain_ancient_debris finale: 굽다 for ore | Claude, error | **Fixed:** 녹였어요 (clue's verb). The tail is now 나중에 네더라이트 주괴를 만들 수 있어요 (scrap and gold make an ingot), not a promise of the player's next move. |
| obtain_blaze_rod s3 clue and hint: 물을 놓다 | Claude, minor x2 | **Fixed in the generator:** water and lava are poured (POUR, 붓다) in PLACE_BLOCK, PLACE_REL and PLACE_ON_PATTERN. Prints 네더에서 물을 붓지 마세요. |
| form_obsidian s2 clue and hint: 물을 놓다 | Claude, minor x2 | **Fixed by the same rule:** 용암 옆에 물을 부은 후에 ... |
| obtain_crying_obsidian finale: "다시 시작할 수 있어요" overclaims | Claude, minor | **Fixed:** 나중에 충전할 수 있어요 (the anchor still needs charging); 충전 is the verb of the next trail's clue. |
| return_to_sender finale: fireball claim | Claude, error | **Fixed:** 네더에서 가스트를 물리쳤어요. Kept a true teaching sentence in place of the claim: 가스트는 화염구를 쏴요. |
| ride_strider finale: riding over lava | Claude, error | **Fixed:** 오버월드에서 스트라이더를 탔어요. 스트라이더는 용암 위를 걸어요. The reviewer's "용암 속에서도 편해요" was loose; striders walk on lava. |
| enter_the_nether finale: fire and portal | Claude, error | **Fixed:** 흑요석을 바라본 후에 네더로 갔어요. The tail 이제 네더를 걸을 수 있어요 is true once the player is in the Nether, so it stays. |
| form_obsidian finale: names the diamond pickaxe | Claude, minor | **Fixed, tail changed:** 흑요석을 캤어요. 나중에 흑요석으로 네더 차원문을 만들 수 있어요. The reviewer's "이제 네더에 갈 수 있어요" is not true after one block (a portal needs ten). |
| vulkanpfad finale: "산 위에서" unchecked | Claude, error | **Fixed:** 밤에 용암을 바라봤어요. 용암은 밤에도 밝아요 (lava glows all the time; "더 밝아요" was a perception claim). |

## Second linguist

A fresh agent confirmed 붓다 (all forms), 깔다/까세요 and 충전하다 for the BATCH-016 lines. It preferred 제련하다 over 녹이다
for ore (kept, see BATCH-017/ling-review.md) and 구했어요 over 캤어요 only in BATCH-017. It found "이제 네더를 걸을 수 있어요"
slightly odd; kept as true and simple.

## Generator verbs (from this review and BATCH-017)

New verb `POUR` (붓다, ㅅ-irregular: 부어요, 부으세요, 부은, 부을, 부었어요). `verbs.json` has a `fluids` family
(water, lava) in front of the default for PLACE_BLOCK, PLACE_REL and PLACE_ON_PATTERN. A row (BUILD_PATTERN ROW) now
asks the table for the verb of a single block of its material, so rails and carpets in a row print 깔다. No generated
finale changes (none of them places a fluid or lays a row). The conjugation gold already had 붓다; the frozen verb table
and the action gold have a POUR row.

## Metrics

`unknownOverOneShare` goes from 0.0466 to 0.0472 (one sentence): the replaced 놓다 was a known word, and the pouring and
spreading verbs are not in the lexicon, so a later sentence meets one word less often. Rebaselined on purpose.
