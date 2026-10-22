# BATCH-006 plan: weather and seasons, the w2 poems (level 1)

Brief: `content/briefs/BATCH-006.md` (on top of BATCH-001, BATCH-004 and BATCH-005). Eight level-1 trails; ids, `order`,
`requires` and `koreanLevel` unchanged, step ids kept (s1 to s3 and finale). Built on phase0 after generator wave 3
(bb55642). `w2_04` is retitled; the other seven keep their titles.

## Baseline and result

| metric | before | after |
|---|---|---|
| unknownOverOneShare | 0.056958 | 0.052427 |
| meanUnknown | 0.9748 | 0.9586 |
| bare share, level 1 | 0.1749 | 0.1305 |
| top goal-type share, level 1 | 0.1671 | 0.1619 |
| duplicate signatures | 13 | 13 |
| novelty share | 0.4738 | 0.4833 |
| object-only repeats | 146 | 144 |
| most repeated (verb, object) pair | 3 | 3 |

The old eight trails asked for one random block per step ("place a nether gold ore", "place a pale oak pressure plate").
The new ones are small scenes in which the weather, the time of day or a height is part of the sentence. No step of the
batch repeats the signature of another step, and no (verb, object) pair goes above 3.

## Trails

| id | project | steps (goal) | mechanics |
|---|---|---|---|
| w2_01 비 그친 하늘 | rain, a window, the clear sky | CRAFT glass pane (roofed), WATCH it in the rain (roofed), CLIMB a hill when it is clear (authored 하늘이 파래요), PLANT a flower when it is clear | WEATHER, SKY, REACH_HEIGHT |
| w2_02 첫눈 소리 | the first snow | GO to the snowy plains by day, COLLECT snowballs there, CRAFT a snow block, WALK there holding a snowball | IN_BIOME, HOLD_WHILE |
| w2_03 해질녘 언덕 | sunset on a hill | CLIMB with bread (HOLDING), SPREAD a carpet at height 80, EAT on the carpet (PLAYER_REL), WATCH a sheep at dusk (authored 해가 져요) | HOLDING, Y_RANGE, PLAYER_REL, DUSK |
| w2_04 빗속의 불빛 | a torch and a baked potato in the rain | STACK a plank pillar, CRAFT a furnace (roofed), PLACE a torch on the pillar in the rain, BAKE a potato in the rain (roofed) | PILLAR, PLACE_ON_PATTERN, WEATHER |
| w2_05 계절의 문 | spring, summer and winter at a door | HANG a door, PLANT a daisy beside it (봄), a sunflower in front (여름), snow on top (겨울) | PLACE_REL NEXT_TO, IN_FRONT_OF, ON_TOP_OF |
| w2_06 별이 뜨는 밤 | a night of stars | WAIT for the night, CLIMB a hill (authored 별이 떠요), LIGHT three torches (authored 달이 높이 떠요), WATCH a torch | WAIT, TIME |
| w2_07 겨울 아침 | a quiet winter morning | PLACE a flower pot by the window (authored 창문), OBTAIN milk, CRAFT a book (roofed), DRINK the milk at dawn | PLACE_REL, SKY, DAWN |
| w2_08 구름 위 탑 | a tower above the clouds | CRAFT stairs of cobblestone, CLIMB holding a ladder (authored 하늘로), PLACE ladders in clear weather, SLEEP at height 90 at night | STAIRS, HOLDING, Y_RANGE |

## Constraints checked

- 32 steps, 1 bare (3 %); top goal type PLACE_BLOCK with 16 %; no SEQUENCE, AVOID, DEFEAT_ENTITY; overworld only; no
  Nether, End or structure. The snowy plains (w2_02) are a cold biome, not a rare one; the rain, the clear sky, dusk
  and dawn are waited for. TIME DUSK (50 s) and DAWN (100 s) are used only in finales.
- Sentences with more than one unknown word in the batch: none.
- Two of the three target verbs of generator wave 3 fit: 기다리다 (w2_06 s1). 열다/닫다 and 나가다/들어가다 were left out:
  the moods are outdoors, and the door of w2_05 is a plant bed, not a threshold.
- Authored Korean: w2_01 s3; w2_03 finale; w2_05 s2, s3 and finale; w2_06 s2 and s3; w2_07 s1; w2_08 s2.

## Known limits

- 시계, 종이, 얼음, 초 and 집 get no new encounter: a clock needs gold and redstone, ice and a candle need a cold biome
  and honeycomb, and candles are at the pair limit (PLACE 초 x3, KINDLE/PLACE 모닥불 x3).
- 저녁 and 아침 lose a few encounters because the old w2 trails said "저녁에 … 놓았어요" three times; the new ones use 저녁 once.
- 창문 is spoken once (w2_07 s1) and is in no lexicon list.
