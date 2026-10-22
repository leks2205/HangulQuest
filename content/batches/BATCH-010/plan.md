# BATCH-010 plan: village, home and market (level 2)

Brief: `content/briefs/BATCH-010.md` on top of BATCH-008 (and through it BATCH-001, BATCH-004, BATCH-005 and `L2-PLAN.md`).
Fifteen level-2 trails; ids, `order`, `requires`, `koreanLevel` and step ids unchanged (s1 to s3, s4 where a trail has a
fourth step, and finale). Built on phase0 after generator wave 6 and wave 7, merged again with BATCH-011 before the final
gates. Every trail carries a `grammar` array. The old trails were runs of bare "get a block, place a block" steps with
whatever the lexicon sampler gave them; they are rebuilt as small village projects. Titles are kept (they fit).

## Result

See `manifest.json` ("metricsNote", "metricsAfter") for every number. Highlights: no duplicate signature added, no
(verb, object) pair pushed from three to four, `unknownOverOneShare` 0.054293 to 0.0529 (the start was 0.055273), no
sentence of this batch with two unknown words, level-2 `formDependentShare` 0.1464 to 0.2141 (0.0940 at the first start),
`bareShare.2` 0.3870 to 0.3015, `topTypeShare.2` 0.2406 to 0.2058, 33 of the 62 steps of this batch are form-dependent
(53%) and one is bare.

## Trails (grammar)

| id | title | steps | grammar |
|---|---|---|---|
| glockenpfad | 종의 길 | ring the bell by day, flower in front of it, ring and talk (-고), wait ten seconds near it | -세요, 앞에, -고, 와/과, 에서, 십 초 |
| uhrmacher | 시계공의 길 | craft a clock, pillar, candle on top, hold the clock and wait thirty seconds, give it to a villager | 에서, 위에, -고 (들고), 삼십 초, 에게 |
| zuhausepfad | 우리 집의 길 | bed, carpet between door and bed, bookshelf next to the bed, close the door and place a torch (-고), sleep | 사이에, 와/과, 옆에, -고, 에서, 은/는 (finale) |
| adv_adventure_trade | 훌륭한 거래군요! | fifteen coal by day, flower by the door, melon to a villager, one real sale | 옆에, 에게 |
| familienpfad | 가족의 길 | into the house at night, fish to the cat under a roof, carpet by the bed, lie down | 에게, 옆에, 안으로 |
| karawanepfad | 상단의 길 | sign by the road, hay bale in front of it, wheat into a chest, take it out and give it (-고) | 옆에, 앞에, -고, 에게 |
| handelsstrasse | 무역로 | composter by hay, sign in front of it, buy bread, emerald into a barrel | 옆에, 앞에, 에게서 |
| druckerei | 인쇄소의 길 | three sheets of paper (장), book and notebook (-고), lectern, book to a villager | 장, -고, 옆에, 에게 |
| w3_01 | 장터의 길 | sign by the barrel, apples in, flower in front, apple out | 옆에, 앞에, 에서 |
| w3_02 | 이웃의 문 | open the door, sign by it, close it, flower in front of it | 옆에, 앞에 |
| w3_03 | 나그네의 주머니 | pumpkin by the road, baked potato outside, walk with bread, egg to a villager | 옆에, 에서, -고 (들고), 에게 |
| w3_04 | 마을 잔치 | pumpkin pie by day, candle on a plank, baked potato to a villager, eat by the candle | 위에, 에게, 에서 |
| w3_05 | 마을의 밤 | home at dusk, candle by the door, close the door and place a candle (-고), wait for night under a roof | 옆에, -고, 에서 |
| w3_06 | 이웃의 밭 | harvest carrots, fence ring, carrots inside it, carrot to a pig | 안에, 에게 |
| w3_07 | 말과 장터 | sugar to a horse, gate by hay, open it beside the horse, talk beside the horse | 에게, 옆에, 에서 |

Authored Korean: the finale lines of glockenpfad, uhrmacher, zuhausepfad and familienpfad; the clues of glockenpfad
(finale), uhrmacher (s4), karawanepfad (s1) and w3_03 (s1), each with the matching level-1 hint. Everything else is generated.

Reachability: all steps are overworld day-one to week-one (village, gold and redstone for the clock). No trail moved to
level 3; familienpfad, handelsstrasse, uhrmacher and w3_02 left `reachabilityDebt`.
