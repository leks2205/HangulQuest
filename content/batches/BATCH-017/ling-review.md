# BATCH-017 linguist review (G2)

Claude (blind) found 4 errors and 16 minors in 148 lines. 14 findings are applied (several in better words than
the suggestion), 6 are rejected. A finale may claim only what its goal checks. Each fix lives in the questline JSON
(`finale.textKo`), in `korean-lines.tsv`, and in the EN and DE lang lines. A second, independent linguist (a fresh
agent) re-read the new verbs and finales; its remarks are under each decision.

| Finding | Who | Decision |
|---|---|---|
| all_potions finale: "더 강한 물약" unchecked | Claude, error | **Fixed:** 양조기에서 물약을 꺼낸 후에 물약을 마셨어요 (the clue's two verbs). Tail 나중에 다른 물약도 만들 수 있어요. |
| all_potions title: official 뿅가는 폭탄주 | Claude, minor | **Rejected.** The trail no longer awards the vanilla advancement, so the official name would be wrong. Title kept. |
| brew_potion title: official 물약 양조장 | Claude, minor | **Applied.** The trail still earns the advancement (the EN title stays "Local Brewery"), so the official name is back. |
| brew_potion finale: 만들었어요 but the goal takes the potion out | Claude, minor | **Fixed:** 양조기에서 물약을 꺼냈어요 (꺼내다, the clue's verb). |
| charge_respawn_anchor finale: 채웠어요 vs clue 충전 | Claude, minor | **Fixed:** 충전했어요. The tail is 나중에 ..., not 이제. |
| create_beacon finale: present tense, a beacon cannot go here | Claude, error | **Fixed:** 영혼 랜턴 근처에서 밤까지 기다렸어요 (the clue's wording). A second linguist would prefer "밤이 될 때까지"; kept, so that it equals the clue. |
| create_beacon title: 신호기 받침 misleading | Claude, minor | **Applied, own wording:** 철 블록과 영혼 랜턴 (what the trail does). EN "Iron Blocks and a Soul Lantern", DE "Eisenblöcke und Seelenlaterne". The reviewer's official 신호기 꾸리기 is not used, because the trail does not build a beacon. |
| get_wither_skull s2 clue and hint: 뼈 vs 뼈다귀 | Claude, minor x2 | **Rejected** (as instructed). 뼈 is the taught core word; on the native-review list. |
| get_wither_skull finale: "이제 만들 수 있어요" is false after one skull | Claude, minor | **Fixed:** 나중에 위더를 만들 수 있어요. |
| loot_bastion finale: 보루 and "금이 있어요" unchecked | Claude, error | **Fixed:** 네더에서 상자를 열었어요. 피글린은 돼지처럼 생겼어요. |
| netherite_armor, summon_wither, uneasy_alliance titles: official names | Claude, minor x3 | **Rejected,** as for all_potions: the rebuilt trails do not award the advancement. |
| summon_wither finale: "해골은 하나면 충분해요" is false | Claude, error | **Fixed:** 위더 스켈레톤 해골을 더 놓지 않았어요. 위더는 아직 없어요. "더" because a skull was placed earlier in the trail. The second linguist rejected "아직 일러요" for an entity, so the tail says the wither does not exist yet. |
| mine_diamond finale: not mining; one diamond is not a pickaxe | Claude, minor | **Fixed:** 땅속에서 다이아몬드를 구했어요 (구하다, the clue's verb) + 나중에 ... 만들 수 있어요. The second linguist would write 캤어요; kept 구했어요 because the goal is AcquireItem and the clue says 구하세요. |
| bergwerk s2 clue and hint: rails are laid with 깔다 | Claude, minor x2 | **Fixed in the generator** (see BATCH-016: a row asks the table for its material's verb). Prints 레일 네 개를 나란히 까세요 / 깔아요. |
| kristallhoehle finale: tinted glass blocks light | Claude, minor | **Fixed:** 차광 유리는 빛을 막아요. |
| kristallschmiede finale: "대장간이 완성됐어요" unchecked | Claude, minor | **Fixed:** the second sentence is dropped. |

## Second linguist on the verbs

- 붓다 for water (and lava), 깔다 for rails, 충전하다, 꺼내다: **agree.** Forms 부어요, 부었어요, 부으세요, 부은, 부을, 붓지 마세요,
  부어서 and 까세요, 깔아요 are correct. 용암을 붓다 is acceptable; one verb for both fluids keeps the table simple.
- 녹이다 for smelting ore: **disagrees** (prefers 제련하다 or 굽다). Kept: 녹이다 is the project's verb for ingots and ore in
  the table (MELT), the reviewer of BATCH-016 asked for exactly this verb, and the clue uses it. Queued for native review.
- Applied from the second linguist: fast_travel tail "...여덟 걸음과 같아요", find_fortress "불덩이를 쏴요", summon_wither tail.
