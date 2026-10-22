# BATCH-028 linguist review (blind, Claude + Gemini)

Claude: 143 ok, 3 minor, 4 error. Gemini (fresh, sandboxed agy project): 150 ok.

| Finding | Reviewer | Resolution |
|---|---|---|
| `purpurwerk` s1, `orangewerk` s1 clue/hint: only one of the two dyes is named | Claude, error x4 | **Applied** after wave 12b, which keeps a dye's colour: the `with` lists hold both dyes (파란 염료와 빨간 염료로 보라색 염료를 만들어야 해요; 빨간 염료와 노란 염료로 주황색 …). EN/DE follow. |
| `weltenschoepfer` finale: 자수정 블록은 반짝여요 is not clearly true | Claude, minor | **Applied:** 자수정 블록은 건드리면 맑은 소리가 나요. |
| `weltwaechter` finale clue: 높이 백 블록 이상으로 | Claude, minor | **Kept** (the wave 12 height decision). |
| `weltwaechter` finale claims the shield | Claude, minor | **Rejected:** the step checks HOLDING minecraft:shield. |

Note: Gemini passed every row, including the incomplete dye recipes. Claude caught them, which is why both reviews run.
