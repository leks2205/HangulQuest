# BATCH-008 linguist review (G2)

Claude (blind) found 0 errors and 12 minors in 158 lines. Gemini 3.8 Flash, in a fresh agy project, found 0 errors
and 2 minors.

| Finding | Who | Decision |
|---|---|---|
| werkzeugpfad s1: bare 도끼 for an iron axe | both | **Fixed in wave 6:** a goal on one tier prints the tier. Now 철 도끼 (clue and hint), the official name in `names.json`. |
| obtain_armor finale: says the armour was put on, the goal was crafting plus an iron gift to the golem | Claude | **Fixed:** the golem gift is now step s4, the finale is a real `EQUIP` goal (wear the iron chestplate), so 입었어요 is true. The title 차려입기 stays. EN and DE lines follow. |
| schmiedefeuer finale clue and hint: the goal needs two ingots, the text gives no count | Claude | **Fixed:** 주민에게 철을 두 개 주고 에메랄드를 받으세요 (clue, hint, finale). 철 stays the one unknown-word-free choice; 철 주괴 would add 주괴. |
| ziegelpfad s1/s2: 점토 where the goal is clay_ball (점토 덩이) | Claude | **No change.** The concept 점토 owns `clay_ball` as its item, 점토 is also the official name of the clay block, and the learner is taught 점토 for exactly this item. 점토 덩이 would add an unknown word for no gain. A second linguist pass agreed. |
| steinherz finale: 소고기 for cooked beef (official 스테이크) | Claude | **No change.** The concept 소고기 owns `cooked_beef` (the raw item is 익히지 않은 소고기, a different name), and 소고기를 먹다 is the everyday phrase for cooked beef. 스테이크 is the tooltip name. A second linguist pass agreed. |

Moved metrics (rebaselined together with the BATCH-009 fixes and the wave-6 follow-up, see the commit):
`unknownOverOneShare` 0.0553 to 0.0565, `formDependentShare.2` 0.0940 to 0.0938 (one more non-form step in a trail of
level 2), `distinctTypes.2` 22 to 23 (EQUIP).
