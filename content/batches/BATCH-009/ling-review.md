# BATCH-009 linguist review (G2)

Claude (blind) found 0 errors and 5 minors in 160 lines. Gemini 3.8 Flash, in a fresh agy project, found 0 errors
and 0 minors.

| Finding | Who | Decision |
|---|---|---|
| gartenfest s3: 에게 with 사다 reads as buying for the villager | Claude | **Fixed in wave 6:** buying takes the source marker, 주민에게서 사과를 사세요 (clue and hint). |
| wax_off s2: the clue names only 구리 블록 as material, the recipe also needs honeycomb | Claude | **Fixed (authored):** the `with` list of the CRAFT goal now holds `minecraft:honeycomb`, so the generator says 구리 블록과 벌집 조각으로 밀랍칠한 구리 블록을 만드세요. `with` is wording only (not checked), so the generator needed no change. EN and DE lines follow. |
| saatfeld title: 씨앗밭 is not a standard word | Claude | **Fixed:** 씨 뿌리는 밭의 길. A second linguist pass found it correct and slightly stiff (씨 뿌리는 밭길 is shorter); kept as the reviewer suggested. |

Moved metrics: see BATCH-008. The honeycomb adds one sentence with two unknown words (벌집 조각), part of the
`unknownOverOneShare` move.
