# BATCH-018 linguist review (G2)

Claude (blind) reviewed the village and guild trails: 18 findings (7 errors, 11 minors). 14 are applied and
4 rejected. A finale may claim only what its goal checks, in the past tense, with the verb of its clue. Each fix lives
in the questline JSON (`finale.textKo`), in `korean-lines.tsv`, and in the EN and DE lang lines. Where a tail sentence
carried a target word, it was replaced by a timeless fact that is true in the game.

| Finding | Who | Decision |
|---|---|---|
| hero_of_the_village finale: "이제 ~ 들을 수 있어요" unbacked | Claude, error | **Applied:** 종을 울렸어요. 주민은 종소리를 들어요. (keeps 듣다 as a fact) |
| summon_iron_golem finale: golem not checked | Claude, error | **Applied:** the pumpkin sentence + 철 골렘은 마을을 지켜요. |
| totem_of_undying finale: 얻었어요 vs clue 구하세요; 쓸 수 있어요 | Claude, minor | **Applied:** 불사의 토템을 구했어요. |
| under_lock_and_key finale: door not checked | Claude, error | **Applied:** 돌 버튼을 눌렀어요. 철 문은 버튼으로 열려요. (a general fact, not an outcome) |
| whos_the_pillager_now finale: future plan, 쇠뇌로 | Claude, minor | **Applied:** 쇠뇌를 들고 약탈자를 한 마리 물리쳤어요. |
| cure_zombie_villager finale: curing not checked | Claude, error | **Applied:** 좀비 주민에게 황금 사과를 줬어요. |
| deflect_arrow finale: blocking not checked | Claude, minor | **Applied:** ... 십 초 동안 기다렸어요. 방패는 화살을 막아요. (the clue's ten seconds; a general fact) |
| enchant_item finale: false "이제 ~ 수 있어요" | Claude, error | **Applied:** the second sentence is dropped. |
| follow_ender_eye finale: future plan | Claude, minor | **Applied:** 엔더의 눈을 들고 아래로 내려갔어요. 엔더의 눈은 요새 쪽으로 날아가요. |
| follow_ender_eye clue: "높이 40 이하로" without a unit | Claude, minor | **Applied in the generator:** see below. |
| fischergilde finale: false "이제 ~ 수 있어요" | Claude, error | **Applied:** 물 옆에 훈연기를 놓았어요. 훈연기는 음식을 구워요. |
| handelsgilde finale: selling not checked | Claude, error | **Applied:** the tail is 에메랄드 블록은 에메랄드 아홉 개로 만들어요. |
| karawane finale: future plan | Claude, minor | **Applied:** 주민에게 호박을 팔았어요. 주민은 에메랄드를 줘요. |
| postpfad finale: giving to a friend later | Claude, minor | **Applied:** the plan is replaced by 통은 물건을 보관해요. |
| karawane s2 clue and hint: 묶다, but the tag says RIDE | Claude, minor x2 | **Rejected,** see below. |
| village_errands_demo s2 hint, postpfad s3 hint: 놓다 for a Give goal | Claude, error x2 | **Rejected.** The hint "주민 앞에 X를 놓아요" is deliberate: villagers accept only dropped items, so the hint says how to give. The clue keeps 주세요. |

## karawane s2

The step is `INTERACT_ENTITY horse` under the condition `HOLDING lead`. A click on a horse with a lead in the hand
leashes it, so 끈으로 말을 묶으세요 is exactly what the goal checks. The generator did this on purpose: `Tools.interaction`
(the tool in the hand decides the verb) picks LEASH. "RIDE" in the review packet is only the table's base verb for a
click on a horse; the packet context prints that tag before the tool replaces it. So the clue is right and the packet
context is misleading. Weakness (packet, not the game): the context column should print the final verb. Not a generator bug.

## The height unit

The unit was dropped for every descent, not only inside HOLD_WHILE: `ReachHeightType.down` printed "높이 40 이하로"
(the climb already printed 높이 N블록 위로). It now prints "높이 40블록 이하로". Five generated clues change (tiefenpfad,
schatzkammer, ancient debris, fall_from_world_height, follow_ender_eye), in BATCH-008/012/014/016/018 (`korean-lines.tsv`
and the `clueKo` of the manifests). Gold rows: `height.down.l2` updated, `height.down.hold.l2` added for HOLD_WHILE.
Weakness 7 of this batch is closed.

## Metrics moved

`coverage.recycling.seenShare` 0.7547 to 0.7500 (내일 now has no sentence: the tails "내일 요새를 찾을 거예요" and
"내일 마을 밖으로 갈 거예요" were unchecked plans); `targetMetShare` 0.1745 to 0.1698; `meanUnknown` 0.7835 to 0.7841.
Rebaselined on purpose; the cost of honest finales.
