# BATCH-001 plan: the first steps, rebuilt

Brief: `content/briefs/BATCH-001.md`. Fifteen level-1 trails, ids, `order`, `requires` and `koreanLevel` unchanged.
Learner-path order: steinpfad, holzpfad, essenspfad, feuerpfad, tierfreund, brotpfad, tierpfad, haustierpfad,
hoehlenlicht, kraeuterpfad, wollpfad, waldlicht, feuerstelle, nachtpfad, (wasserpfad), flusslicht.

## Baseline (before)

| metric | before |
|---|---|
| bare share, level 1 | 0.6105 |
| top goal-type share, level 1 | 0.3785 |
| novelty share | 0.3762 |
| boredom index | 0.0952 |
| duplicate signatures / object-only repeats | 18 / 178 |
| median encounters of the 29 core target words (whole path) | 3 |
| core words with 4+ encounters in 2+ roles (66 core words) | 31 |
| target words never met | 나무, 해, 옆, 밖 (and 쌓다, 들다 are not measured) |

Before the batch the old trails taught one random block per step ("break stone, place cobblestone, place a black
banner"). 나무 was invisible to the coverage tool (the concept `log` carried the alias 나무, so every 나무 was read as
통나무; the alias is removed, see manifest notes).

## Rules this plan is checked against

- 3-4 steps plus a finale per trail, every step another goal type or relation inside the trail.
- At most 4 new words per trail and one per `semSet` (new = first teach on the learner path; 문 as door and gate is one
  Korean word, so the gate of tierpfad is not new).
- At least one non-bare step per trail; bare steps (plain break/place/acquire) at most 30% of the batch.
- No goal type above 25% of the batch; no SEQUENCE, no AVOID; overworld only.
- No (verb, object) pair more than twice on the whole path; every step signature new (duplicates must not grow).
- Every trail adds a mechanic that none of the previous five trails of the path had.
- Generated Korean wherever the generator can say it; authored clues only for a reason sentence ("달이 떠요.") or the
  words the generator cannot say (나무 for a log). Only two authored finale lines (the test
  `everyGeneratedFinaleMatchesTheCeremonyGenerator` wants more than 300 generated ones).

## Trails

| # | id | project | steps (goal) | new words (semSet) | mechanic that is new |
|---|---|---|---|---|---|
| 0 | steinpfad 돌과 불의 길 | coal, cobblestone pillar, torch on top at night | BREAK_BLOCK, BUILD_PATTERN PILLAR, CRAFT, PLACE_ON_PATTERN ON_TOP_OF (+TIME) | 석탄 (ore), 조약돌 (stone), 횃불 (light) | the first trail |
| 1 | holzpfad 나무 집과 문의 길 | plant a tree, craft a door, torch on the trunk, door beside it | PLACE_BLOCK (+TIME), CRAFT, PLACE_REL ON_TOP_OF, PLACE_REL NEXT_TO | 나무 (wood), 문 (building) | PLACE_REL, 옆 |
| 2 | essenspfad 사과와 달걀과 소의 길 | farm picnic | ACQUIRE, LOOK_AT_ENTITY (+TIME), GIVE, CONSUME (+PLAYER_REL) | 사과 (food), 닭 (animal) | GIVE, PLAYER_REL |
| 3 | feuerpfad 물과 불의 길 | chop, campfire, water from the river, fire with water in hand | BREAK_BLOCK, CRAFT, ACQUIRE (+IN_BIOME), HOLD_WHILE{PLACE_REL}, LOOK_AT_BLOCK (+TIME) | 통나무 (wood), 모닥불 (light), 물 (element) | HOLD_WHILE, IN_BIOME |
| 4 | tierfreund 소와 우유의 길 | bucket, milk next to the cow, drink, feed | LOOK_AT_ENTITY, CRAFT, ACQUIRE (+PLAYER_REL), CONSUME, INTERACT_ENTITY | 소 (animal), 양동이 (tool), 우유 (food) | INTERACT_ENTITY |
| 5 | brotpfad 밀과 빵의 길 | wheat, bread, chest, supper beside it | BREAK_BLOCK (+TIME), CRAFT, PUT_IN, CONSUME (+PLAYER_REL) | 밀 (crop), 빵 (food), 상자 (container) | PUT_IN, 안 |
| 6 | tierpfad 닭과 돼지와 달걀의 길 | pig, egg, fence row, gate | INTERACT_ENTITY (+HOLDING), CONSUME, BUILD_PATTERN ROW, PLACE_REL, USE_BLOCK | 돼지 (animal), 달걀 (food), 울타리 (building) | HOLDING, ROW, USE_BLOCK |
| 7 | haustierpfad 고양이의 집 | cat house | LOOK_AT_ENTITY (+TIME), GIVE, CRAFT, PLACE_REL INSIDE, PLACE_REL OUTSIDE | 고양이 (animal), 판자 (wood), 침대 (furniture), 민들레 (flower) | INSIDE, OUTSIDE (안, 밖) |
| 8 | hoehlenlicht 돌과 횃불의 길 | light the cave | BREAK_BLOCK (+Y_RANGE), BUILD_PATTERN PYRAMID (+HOLDING), PLACE_BLOCK (+Y_RANGE), HOLD_WHILE | 돌 (stone) | Y_RANGE, PYRAMID |
| 9 | kraeuterpfad 꽃과 감자의 길 | garden | PLACE_REL NEXT_TO, ACQUIRE (+TIME), SMELT, CONSUME (+PLAYER_REL) | 꽃 (flower), 감자 (crop), 구운 감자 (food) | SMELT |
| 10 | wollpfad 양과 양털의 길 | sheep to bed | LOOK_AT_ENTITY, ACQUIRE, CRAFT, PLACE_REL IN_FRONT_OF (a carpet) | 양 (animal), 양털 (material), 양탄자 (decor) | IN_FRONT_OF (앞), 깔다 |
| 11 | waldlicht 유리와 초의 길 | cabin window and candle | SMELT, PLACE_REL NEXT_TO, PLACE_REL ON_TOP_OF, USE_BLOCK (+SKY) | 유리 (building), 초 (light) | SKY, 위 |
| 12 | feuerstelle 불과 빵의 길 | fire in front of the door | PLACE_REL IN_FRONT_OF, PLACE_REL BEHIND, ACQUIRE (+TIME), CONSUME (+TIME +PLAYER_REL) | none (all recycled) | BEHIND (뒤) |
| 13 | nachtpfad 해와 달의 길 | clock, night walk, book in the chest, sleep | CRAFT, HOLD_WHILE{TIME}, PUT_IN, USE_BLOCK (+TIME) | 시계 (tool), 밤 (time), 책 (material) | HOLD_WHILE with a condition |
| 15 | flusslicht 종이와 책의 길 | dock, paper, book at the river | REACH_BIOME (+TIME), BUILD_PATTERN ROW (+IN_BIOME), ACQUIRE (+IN_BIOME), CRAFT, HOLD_WHILE{IN_BIOME} | 강 (place), 종이 (material) | REACH_BIOME + ROW at the river |

Where the core words come back (role in brackets): 나무 (object, anchor, title, finale), 물 (object, held item),
돌 (object), 집 (title, finale, mention), 문 (object, anchor, finale), 빵 (object, material, anchor-near), 사과, 감자
(object, held), 달걀, 우유, 상자 (object, container, anchor), 침대 (object, anchor), 초, 소 (object, anchor, recipient),
닭 (object, recipient), 고양이, 강 (condition, object), 해, 달 (reason sentences), 유리, 시계, 종이, 책 (material, held).
Exact counts, before and after: `manifest.json`, key `recycledWords`.

## Checked before writing the JSON

- 65 steps, 4 bare (6%), most used goal type PLACE_REL 17% (rule: 30% and 25%); the verb 놓다 stays under its level-1 ratchet (19.2% of the level, limit 19.4%).
- Every trail is new against its five predecessors on the path (the mechanic column).
- Pair and signature budget: no (verb, object) pair above two, 17 duplicate signatures (was 18).
- Unknown words: 92 of 1537 sentences have more than one unknown word (was 95 of 1524).

## Known limits

- 올라가다 and 들어가다 have no level-1 sentence in the generator (REACH_HEIGHT reads "높이 N블록 위로"; the verb
  ENTER is not wired to any goal). 쌓다, 들다 and 걷다 are enacted but the coverage lexicon does not know them.
- 감자, 초, 달걀 and 시계 need a village chest, honeycomb, a chicken and gold: possible on day one, not trivial.
- The finale line of the generator names the registry block (참나무 판자), not the taught word; only holzpfad and
  nachtpfad are authored because of the 300-finale test.
