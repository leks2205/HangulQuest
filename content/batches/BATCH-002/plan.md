# BATCH-002 plan: village, music and friends

Brief: `content/briefs/BATCH-002.md` (builds on BATCH-001). Fifteen level-1 trails; ids, `order`, `requires`,
`tier` and `koreanLevel` unchanged. Learner-path positions: bergpfad 16, dorfpfad 17, wurzelpfad 18, musikpfad 19,
bienenpfad 20, freundschaftspfad 21, briefpfad 22, redstonepfad 23, schneeballpfad 24, steintiefe 25, waldhaus 26,
bogenpfad 27, blumenpfad 28, kerzenpfad 29, wetterpfad 51.

## Baseline (before)

| metric | before |
|---|---|
| bare share, level 1 | 0.5387 |
| top goal-type share, level 1 | 0.3227 |
| novelty share | 0.4024 |
| boredom index | 0.0905 |
| duplicate signatures / object-only repeats | 17 / 170 |
| unknown-over-one share | 0.0618 |
| hint word coverage | 0.9220 |
| median encounters of the 31 target words (whole path) | 5 |
| 올라가다 / 들어가다 / 이야기하다 / 친구 (encounters) | 1 / 1 / 2 / 1 |

Before the batch the trails asked for one random block per step (pink concrete slab, sticky piston, azalea leaves).

## Rules this plan is checked against

- 3 steps plus a finale, every step another goal type or relation inside its trail.
- At most 4 new words per trail (new = first teach on the learner path), one per `semSet`. One exception:
  musikpfad teaches redstone and note_block, both `redstone`.
- At least one non-bare step per trail; bare steps 4 of 60 (7%), no goal type above 20% (CRAFT 12 of 60).
- No SEQUENCE, no AVOID, overworld only. 올라가다 (REACH_HEIGHT) and 들어가다 (REACH_BIOME into a cave) are used.
- No (verb, object) pair above 2 on the whole path (the pairs at 2 are avoided on purpose: other objects), and no
  new duplicate signature (one remains, INTERACT villager TALK, shared with handelspfad; an authored clue needs a step
  without conditions).
- Every trail adds a mechanic none of the previous five trails of the path had (audit: 15 of 15).
- Sentences are generated; authored clues only where the generator cannot say it (집을 지어요, 친구, 동굴 flavour,
  a morning or night reason sentence). Two authored finale lines (waldhaus, schneeballpfad).

## Trails

| # | id | project | steps (goal) | new words | new mechanic |
|---|---|---|---|---|---|
| 16 | bergpfad 산과 눈의 길 | climb a mountain, snow, campfire at night, torch walk | REACH_HEIGHT, LOOK_AT_BLOCK (+TIME), PLACE_REL ON_TOP_OF (+TIME), HOLD_WHILE{IN_BIOME} | 산, 올라가다, 눈 | REACH_HEIGHT, LOOK_AT_BLOCK |
| 17 | dorfpfad 마을의 길 | a day in the village | LOOK_AT_ENTITY (+TIME), USE_BLOCK, ACQUIRE (+TIME), GIVE | 주민, 마을, 종, 당근 | GIVE to a villager |
| 18 | wurzelpfad 나무와 묘목의 길 | sapling to crafting table | ACQUIRE, BREAK_BLOCK (+TIME), CRAFT, PLACE_REL NEXT_TO | 묘목, 막대기, 제작대 | NEXT_TO |
| 19 | musikpfad 소리 블록의 길 | redstone, note block, button | BREAK_BLOCK (+Y_RANGE), CRAFT, USE_BLOCK (+TIME), PLACE_REL NEXT_TO | 레드스톤, 소리 블록 | Y_RANGE |
| 20 | bienenpfad 꿀벌과 꽃의 길 | bees, flower, hive | LOOK_AT_ENTITY (+TIME), PLACE_REL NEXT_TO (+TIME), CRAFT, PLACE_REL UNDER | 꿀벌, 벌통 | UNDER (아래) |
| 21 | freundschaftspfad 친구의 길 | horse, apple, carrot beside a villager, talk | LOOK_AT_ENTITY, GIVE, CONSUME (+PLAYER_REL), INTERACT_ENTITY | 말, 친구 | INTERACT_ENTITY (이야기하다), PLAYER_REL |
| 22 | briefpfad 종이와 편지의 길 | paper, chest, gift to a friend, mailbox | ACQUIRE, CRAFT, GIVE, PLACE_BLOCK (+TIME) | none | PLACE_BLOCK |
| 23 | redstonepfad 레드스톤의 길 | torch, lever, lamp | CRAFT, PLACE_REL NEXT_TO, PLACE_BLOCK (+SKY), USE_BLOCK (+TIME) | 레드스톤 횃불, 레버 | SKY |
| 24 | schneeballpfad 눈싸움의 길 | snowball fight, snow pillar | ACQUIRE, CONSUME, CRAFT, BUILD_PATTERN PILLAR | 눈덩이 | PILLAR |
| 25 | steintiefe 돌과 철의 길 | cave, iron, furnace | REACH_BIOME, BREAK_BLOCK (+Y_RANGE), CRAFT, SMELT | 동굴, 철, 화로 | 들어가다, SMELT |
| 26 | waldhaus 숲의 집 | forest house | BUILD_PATTERN RING, PLACE_REL NEXT_TO, CRAFT, PLACE_ON_PATTERN INSIDE | 유리판 | RING, INSIDE (안) |
| 27 | bogenpfad 활의 길 | bow and arrows | ACQUIRE (+TIME), CRAFT, PUT_IN, HOLD_WHILE{TIME} | 실, 활 | HOLD_WHILE, PUT_IN |
| 28 | blumenpfad 꽃과 토끼의 길 | rabbit and flowers | LOOK_AT_ENTITY (+TIME), GIVE, BREAK_BLOCK, CRAFT | 토끼, 염료 | GIVE, LOOK |
| 29 | kerzenpfad 초와 밤의 길 | candles for the night | CRAFT, PLACE_REL NEXT_TO, PUT_IN (+SKY), HOLD_WHILE{TIME} | none | SKY |
| 51 | wetterpfad 비와 물고기의 길 | a rainy fishing day | CRAFT, ACQUIRE (+IN_BIOME), SMELT, CONSUME (+WEATHER) | 낚싯대, 물고기, 익힌 대구, 비 | WEATHER |

## Known limits

- Candles and note blocks cannot be placed by a goal (their pairs are used up), so candle and note-block steps craft,
  use, store or look at them.
- Honeycomb (beehive, candle), string (bow), redstone and iron take a pickaxe, shears or a night: possible by day three.
- The finale of wetterpfad waits for rain.
