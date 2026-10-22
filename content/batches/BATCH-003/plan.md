# BATCH-003 plan: garden, kitchen and water (level 1)

Brief: `content/briefs/BATCH-003.md` (on top of BATCH-001 and the "What changed" section of BATCH-002). Fifteen level-1
trails; ids, `order`, `requires` and `koreanLevel` unchanged. Learner-path order of this batch: kuerbispfad, baeckerei,
glasfenster, schneemannpfad, pilzpfad, teppichpfad, bambuspfad, apfelpfad, froschteich, farmpfad, sonnenblume,
regenbogenpfad, fuchsbau, and (behind their parents) windpfad, inselpfad.

## Baseline (before, `content/metrics-baseline.json` at phase0)

| metric | before | after |
|---|---|---|
| bare share, level 1 | 0.5387 | 0.4324 |
| top goal-type share, level 1 | 0.3227 | 0.2679 |
| distinct goal types, level 1 | 18 | 19 |
| novelty share | 0.4024 | 0.4286 |
| boredom index | 0.0905 | 0.0878 |
| object-only repeats | 170 | 162 |
| duplicate signatures | 17 | 17 |
| most repeated (verb, object) pair | 2 | 2 |
| sentences with more than one unknown word | 0.061809 | 0.061728 |
| mean unknown words | 1.0332 | 1.0227 |
| median encounters of core words | 5.5 | 6 |

The old fifteen trails taught one random block per step ("break a dead tube coral wall fan, place a purple pillar").

## The constraint that shapes the batch

`coverage.sentences.unknownOverOneShare` is a ratchet, and a word is only "known" after three earlier steps. So every
sentence of the batch has at most one word that the learner has not met three times yet (checked with a text-only
what-if of `tools/coverage.py` before any JSON was written). Consequences: one new word per sentence; ingredient lists
(`with`) are mostly left out; the first new word of a trail is alone in its sentence; known words (닭, 상자, 문, 강,
낮, 밤) fill the rest of the sentence. Exactly one sentence has two unknown words (inselpfad s3, the swim line).

## Rules this plan is checked against

- 3-4 steps plus a finale per trail, a different goal type or relation per step inside the trail (checked by script).
- At most 4 new words per trail, at most one per `semSet` (checked against `lexicon-tags.json`).
- At least one non-bare step per trail (one bare step in the whole batch, 1.6 %); no goal type above 25 % (ACQUIRE_ITEM
  is the top with 19 %); no SEQUENCE, AVOID, DEFEAT_ENTITY; overworld only.
- No (verb, object) pair more than twice on the whole path, no new duplicate signature, every trail adds a mechanic
  none of the previous five trails of the path had.
- Generated Korean wherever the generator can say it. Authored clue lines are only where the generator has no verb
  (요리하다, 수영하다), a flavour line (비가 내려요, 해가 떠요) or a hint. Authored finales: none (the finale test wants
  more than 300 generated ones).

## Trails

| id | project | steps (goal) | new words | new mechanic against the five before |
|---|---|---|---|---|
| kuerbispfad 호박 파이의 길 | pumpkin pie | BREAK_BLOCK (+TIME), ACQUIRE_ITEM sugar (+TIME), CRAFT pie, CONSUME (+PLAYER_REL chicken) | 호박, 설탕, 호박 파이 | CRAFT, CONSUME, PLAYER_REL |
| baeckerei 빵집과 케이크의 길 | cake in the bakery | ACQUIRE wheat (+TIME), CRAFT cake, PUT_IN chest, PLACE_BLOCK candle (+SKY) | 케이크 | PUT_IN, SKY |
| glasfenster 유리창의 길 | a window | ACQUIRE sand (+IN_BIOME river), BUILD ROW glass, CRAFT panes, LOOK_AT_BLOCK (+TIME) | 유리판 | BUILD_PATTERN, ROW |
| schneemannpfad 눈사람의 길 | a snowman | ACQUIRE snowballs, CRAFT snow blocks, LOOK_AT_BLOCK ice, BUILD PILLAR, PLACE_ON_PATTERN pumpkin | 얼음 | PLACE_ON_PATTERN, PILLAR |
| pilzpfad 버섯 스튜의 길 | mushroom stew | BREAK mushroom (+TIME), ACQUIRE bowl (+PLAYER_REL chest), CRAFT stew, CONSUME (+PLAYER_REL campfire), HOLD_WHILE | 버섯, 그릇, 버섯 스튜 | HOLD_WHILE, PLACE_REL |
| teppichpfad 양탄자의 길 | picnic carpet | ACQUIRE wool (+NEAR_ENTITY sheep), CRAFT carpets, PLACE_REL carpet, CONSUME carrot (+PLAYER_REL) | 당근 | NEAR_ENTITY |
| bambuspfad 대나무와 수박의 길 | jungle trip | REACH_BIOME jungle, BREAK bamboo, ACQUIRE melon, PLACE_BLOCK bamboo (+TIME) | 정글, 대나무, 수박 | REACH_BIOME |
| apfelpfad 사과나무의 길 | apple tree | PLACE_REL sapling, ACQUIRE apple, PUT_IN chest, LOOK_AT_BLOCK leaves | 묘목, 나뭇잎 | PUT_IN |
| froschteich 개구리 연못 | frog pond | PLACE_BLOCK orchid (+IN_BIOME swamp), LOOK_AT_ENTITY frog, ACQUIRE lily pad, PLACE_BLOCK lily pad | 개구리, 수련잎 | IN_BIOME, LOOK_AT_ENTITY |
| farmpfad 농장의 길 | farm lunch | BUILD RING, INTERACT_ENTITY (+HOLDING), SMELT (authored 요리해요), CONSUME (+TIME) | 요리하다, 닭고기 | HOLDING, INTERACT_ENTITY, SMELT, RING |
| sonnenblume 해바라기의 길 | sunflower garden | ACQUIRE (+TIME), PLACE_REL potatoes, GIVE pig, CONSUME (+PLAYER_REL) | 해바라기 | GIVE |
| regenbogenpfad 비와 해의 길 | rain and sun | PLACE_REL flower (+flavour), CRAFT dye, BUILD ROW wool, PLACE_ON_PATTERN pane (+flavour) | 염료 | CRAFT, ROW |
| fuchsbau 토끼의 집 | rabbit hutch | ACQUIRE dandelion, LOOK_AT_ENTITY rabbit, GIVE, PLACE_REL INSIDE | 토끼 | INSIDE |
| windpfad 바람의 길 | wool flag on a mountain | REACH_HEIGHT (올라가요), ACQUIRE wool (+HOLDING shears), BUILD PILLAR, PLACE_ON_PATTERN | (올라가다, 산, 가위) | REACH_HEIGHT |
| inselpfad 섬의 길 | island beach fire | LOOK_AT_BLOCK sand (+IN_BIOME), BUILD RING, REACH_BIOME ocean (authored 수영해요), PLACE_ON_PATTERN INSIDE | 바다, 수영하다 | RING, INSIDE |

## Checked before writing the JSON (and again after)

- 62 steps, 1 bare; 29 mechanics; most used goal type ACQUIRE_ITEM 19 %.
- Every trail adds a mechanic against its five predecessors on the path (script `nov.py`).
- (verb, object) pairs: none over two, duplicates 17 (= baseline, one is the swim line REACH_BIOME ocean).
- Text-only what-if and the real dumps agree: 1 sentence of the batch has more than one unknown word.

## Known limits

- `요리하다`, `수영하다`: no verb in the generator; the two lines are authored (see manifest, `generatorWeaknesses`).
- Day-one reachability: jungle (bamboo, melon), swamp (frog), snowy plains (snowballs), mountain (minY 100) and
  sunflowers need a walk; none needs a structure, the Nether or a rare biome.
- Target words 소고기, 돼지고기, 물 (as a noun) and 우유 do not occur in a sentence of this batch: new foods cost an
  unknown-word slot each, and fluids cannot be anchors.
