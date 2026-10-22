# BATCH-024 linguist review (blind, Claude + Gemini)

Claude: 139 ok, 12 minor, 5 error. Gemini (fresh, sandboxed agy project): 155 ok, 1 error.

| Finding | Reviewer | Resolution |
|---|---|---|
| `unendlichereise` finale says 팔십 블록; the goal is 35 | Claude + Gemini, error | **Applied:** 저녁에 서쪽으로 삼십오 블록 갔어요. |
| `handelsflotte` finale: banners are not made of 천 | Claude, minor | **Applied:** 현수막은 양털로 만들어요. (EN/DE too) |
| `tempelwaechter` finale: 바다 신전 is not the official name | Claude, minor | **Applied:** 바다 유적 (as in 바다 유적 지도). |
| `reisekrone` finale: 거북은 느려요 doesn't tie to the goal | Claude, minor | **Applied:** 거북 등딱지를 쓰면 물속에서 숨을 더 오래 쉴 수 있어요. |
| `sternenkarte` finale, clue, hint: the map is unchecked | Claude, error x3 | **Rejected:** the HOLDING minecraft:map condition is checked. 지도 is the general word covering 빈 지도, as with 묘목. |
| `kronenlicht` finale: 마지막 보스 is vague | Claude, minor | **Rejected:** it is natural and true. |
| `wolkenpfad` s1: 높이 150블록 위로 is ambiguous and uses digits | Claude, error | **Generator wave 12:** the 위/이상 wording, and digits above 100. |
| `handelsflotte` s1: 배에 앉다 | Claude, minor x2 | **Generator wave 12:** 배를 타다. |
| `adv_nether_create_full_beacon` finale: 신호기를 열다 | Claude, minor x3 | **Generator wave 12:** 사용하다 for beacons. |
| `pyramidenpfad` s3 and `reisekrone` s1: repeated nouns | Claude, minor x3 | **Generator wave 12** (the -아/어서 / -(으)러 phrasing). |
