# BATCH-005 plan: first tools, the market, night and the small poems (level 1)

Brief: `content/briefs/BATCH-005.md` (on top of BATCH-001 and BATCH-004). Fifteen level-1 trails; ids, `order`, `requires`
and `koreanLevel` unchanged, step ids kept (s1 to s3 and finale; marktpfad has an s4). Built on phase0 at generator wave 2
(wave 3 had not merged when the batch was written).

## Baseline and result

| metric | before | after |
|---|---|---|
| unknownOverOneShare | 0.061528 | 0.056958 |
| meanUnknown | 0.996114 | 0.973463 |
| hintFullyKnownShare | 0.907383 | 0.911327 |
| targetMetShare | 0.212121 | 0.257576 |
| bare share, level 1 | 0.2644 | 0.1749 |
| top goal-type share, level 1 | 0.2173 | 0.1697 |
| duplicate signatures | 15 | 13 |
| novelty share | 0.4571 | 0.4738 |
| boredom index | 0.0817 | 0.0810 |
| object-only repeats | 152 | 146 |
| most repeated (verb, object) pair | 3 | 3 (three pairs now stand at 3) |

The old fifteen trails asked for one random block per step ("break a cracked nether brick, place a pale oak fence
gate"). Three of their sentences had two unknown words; none of the new 61 has. No step of the batch repeats the
signature of another step (the 13 duplicates left belong to other trails). The pairs that end at 3 are the ones the
brief asked for: 우유를 마셔요 (DRINK milk), 초를 놓아요 (PLACE candle) and 모닥불을 피워요 (KINDLE campfire). Every other
pair of the batch ends at 2 or fewer.

## Trails

| id | project | steps (goal) | mechanics |
|---|---|---|---|
| adv_story_root 마인크래프트 | wood to a wooden pickaxe | CHOP logs, ACQUIRE planks (8), CRAFT table, CRAFT wooden pickaxe | 통나무, 판자, 제작대, 나무 곡괭이 |
| adv_story_mine_stone 석기 시대 | stone, stone pickaxe, first furnace | MINE stone, CRAFT stone pickaxe (+HOLDING cobblestone), COLLECT cobblestone (8), SMELT stone | 돌, 조약돌, 화로 |
| marktpfad 시장의 길 | milk, bread and emeralds | GIVE wheat to the cow, DRINK milk (+PLAYER_REL cow), CRAFT bread, GIVE bread to a villager friend (authored), COLLECT emeralds | 소, 우유, 마시다, 빵, 친구, 이야기하다 |
| nachtwache 별빛의 길 | a night watch | STACK column by day, PLACE_ON_PATTERN candle on top (authored), USE bell (+HOLDING clock, night), WATCH zombie (night) | 기둥 위, 시계, 종, 달 |
| firstpfad 봉우리의 길 | the peak | SHATTER ice (+IN_BIOME), COLLECT snowballs, THROW snowball (+IN_BIOME), WATCH snow (+IN_BIOME) | 얼음, 눈덩이, 산 |
| gipfelblick 정상의 전망 | the summit | CLIMB (+HOLDING torch), STAND sign (+IN_BIOME), PLACE_REL bed next to the sign, SLEEP (authored) | 올라가다, 옆, 산 위 |
| w1_01 첫 촛불 | a candle on a table | STACK planks, CRAFT candle, PLACE_REL candle on planks (night), light it (authored) | 위, 초, 불 |
| w1_02 유리 속 햇살 | glass in the sun | DIG sand (+IN_BIOME), SMELT glass, CRAFT yellow pane, PLACE pane | 모래, 유리, 화로 |
| w1_03 노란 꽃밭 | a yellow flower bed | SNAP dandelions, PLANT sunflower (authored 해가 떠요), PLANT dandelion next to it (authored), WATCH sunflower | 옆, 해, 해바라기 |
| w1_04 달빛 산책 | a walk by moonlight | CRAFT torches, GO forest (authored 달이 떠요), WATCH log (+HOLDING torch), WATCH sheep | 달, 숲, 횃불 |
| w1_05 물가의 불빛 | light by the water | GO beach, PLACE_REL lily pad on water, CRAFT campfire, KINDLE campfire (night) | 물 위, 해변, 모닥불 |
| w1_06 겨울 창가 | winter at the window | BREAK snow blocks, CRAFT glass panes, PLACE_REL bed (authored 창문), WATCH snow (+SKY roofed) | 옆, 창문, 지붕 아래 |
| w1_07 나무 아래의 점심 | a picnic | SPREAD carpet by a log, COLLECT sweet berries, EAT them (+PLAYER_REL log), WATCH pig | 옆, 통나무, 양탄자 |
| w1_08 작은 집의 아침 | a little house | RING walls (authored), door beside the walls, bed inside (authored), OPEN door at dawn | 집, 문, 안 |
| w1_09 작은 발견 | small finds on a wall | COLLECT eggs, OBTAIN feather, CRAFT item frame, HANG frame next to a chest | 상자 옆, 달걀, 깃털 |

## Constraints checked

- 61 steps, 9 bare (14.8 %); top goal type CRAFT with 16 %; no SEQUENCE, AVOID, DEFEAT_ENTITY; overworld only; no Nether,
  End, structure or rare biome (the village trails need a village, as in BATCH-004).
- No iron tool is asked for; the only items outside the first minutes are the clock (gold, redstone), the candle
  (honeycomb), the bucket of milk and the village bell. TIME DUSK (50 s) and DAWN (100 s) are too short for the first
  steps of a trail, so the batch uses DAY and NIGHT; the one DAWN step is the finale of w1_08 (a night in the new bed ends
  at dawn).
- Sentences with more than one unknown word in the batch: none (before: 3). Downstream, four sentences of other trails
  that were over-one are fine now and none got worse.
- Authored Korean: nachtwache s2, w1_01 finale, marktpfad s4, gipfelblick finale, w1_03 s2 and s3, w1_04 s2, w1_06 s3,
  w1_08 s1 and s3, and the finales of w1_01 and gipfelblick (see the manifest and `korean-lines.tsv`).

## Known limits

- 종이, 책, 하늘, 높이 and 창문 do not occur in a sentence the learner has to act on: CRAFT paper and CRAFT book already
  stand at 2, and the sky and the height have no goal of their own. 창문 is in no lexicon list; it is spoken once, in the
  clue of w1_06 s3, with 유리판 in the hint.
- 열다/닫다, 꺼내다, 사다/팔다 and 요리하다 of generator wave 3 were not available; 열다 is used once (w1_08 finale).
