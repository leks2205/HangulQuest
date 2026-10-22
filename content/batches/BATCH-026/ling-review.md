# BATCH-026 linguist review (blind, Claude + Gemini)

Claude: 115 ok, 20 minor, 3 error. Gemini (fresh, sandboxed agy project): 137 ok, 1 minor.

| Finding | Reviewer | Resolution |
|---|---|---|
| `kronenwacht` finale: witches don't brew potions | Claude, error | **Applied:** 마녀는 물약을 던져요. (EN/DE too) |
| `kronenglanz` title 왕관의 빛남 is an awkward noun form | Claude + Gemini, minor | **Applied:** 빛나는 왕관. |
| `kronenrat` finale: banners also stand on the ground | Claude, minor | **Applied:** 현수막에는 무늬를 넣을 수 있어요. |
| `schattenpfad` finale: 소리를 느껴요 | Claude, minor | **Applied:** 스컬크 감지체는 진동을 감지해요. |
| `zeitportal` finale: 햇빛을 느껴요 | Claude, minor | **Applied:** 햇빛을 감지해요. |
| `seelenpfad` finale: 영혼 불꽃 is not the official name | Claude, minor | **Applied:** 영혼 불, also in `w7_05` and `w9_06`. |
| `kronenglanz` s4: 구리 전구를 달다 | Claude, error x2 | **Generator wave 12:** lamps take 놓다. |
| Doubled 동안 in HOLD_WHILE + LOOK (4 clues) | Claude, minor | **Generator wave 12.** |
| `kronenwacht` s3: 주민을 물리치지 마세요 | Claude, minor x2 | **Generator wave 12:** 해치다 for peaceful creatures. |
| 높이 0 이하 and 150블록 위로 (`richterpfad`, `runenpfad`, `w8_08`) | Claude, minor | **Generator wave 12** (height wording). |
| `w8_07` s3: 횃불을 들고 … 만들어야 | Claude, minor | **Kept:** the step checks HOLDING a torch. |
