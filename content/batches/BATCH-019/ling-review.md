# BATCH-019 linguist review (G2)

Claude (blind) reviewed the hunter trails: 18 findings (5 errors, 13 minors). 10 are applied and
8 are rejected. Each fix lives in the questline JSON, in `korean-lines.tsv`, and in the EN and DE lang lines.

| Finding | Who | Decision |
|---|---|---|
| arbalistic, bullseye, overoverkill, shoot_arrow finales: false "이제 ~ 사냥/쏠 수 있어요" | Claude, error x4 | **Applied:** only the goal, in the past tense with the clue's verb and tool (쇠뇌를 들고 좀비를 한 마리 물리쳤어요; 활을 들고 과녁을 일 초 동안 바라봤어요; 철 도끼를 들고 ...; 밤에 활을 들고 ... + 스켈레톤은 활을 써요.). |
| spear_many_mobs finale: campfire cooking unchecked | Claude, minor | **Applied:** 철 창을 들고 양을 세 마리 물리쳤어요. 고기는 모닥불에서 구워요. (a general fact, not "can") |
| throw_trident finale: drop and throw unchecked | Claude, minor | **Applied:** the defeat only + two general facts (드라운드에게서 가끔 삼지창이 나와요. 삼지창은 던질 수 있어요.). The reviewer would drop both; they are true in the game and keep 나오다. |
| very_very_frightening finale: 옆 vs clue 근처; lightning unchecked | Claude, minor | **Applied:** 피뢰침 근처에서 밤까지 기다렸어요. 피뢰침은 번개를 받아요. |
| kill_mob_near_sculk_catalyst title: 밤사냥 | Claude, minor | **Applied:** 숲의 밤 사냥 (title JSON, tsv, quest-tree doc). |
| revaulting finale: unchecked eating; the name | Claude, minor | **Applied for the tail:** 밤에 상자에서 소고기를 꺼냈어요. 모닥불은 고기를 구워요. The name is rejected (below). |
| wolfpfad finale: running is irrelevant | Claude, minor | **Applied:** 늑대에게 돼지고기를 줬어요. 늑대는 고기를 좋아해요. The name is rejected (below). |
| revaulting s2 clue/hint, finale clue/hint: 소고기 should be 스테이크 | Claude, minor x4 | **Rejected.** 소고기 is the taught core word; the dish is queued for native review. EN keeps "steak". |
| wolfpfad s2 clue/hint, finale clue/hint: 스테이크, 익힌 돼지고기 | Claude, minor x4 | **Rejected,** same reason: 돼지고기 is the taught core word. Queued for native review. |


## Metrics moved

Shared with BATCH-018 (one gate run): `seenShare` 0.7547 to 0.7500, `targetMetShare` 0.1745 to 0.1698, `meanUnknown` 0.7835
to 0.7841. Rebaselined on purpose.
