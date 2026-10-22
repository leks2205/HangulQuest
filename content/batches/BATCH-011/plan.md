# BATCH-011 plan: sea and harbour (level 2)

Brief: `content/briefs/BATCH-011.md` on top of BATCH-008. Fifteen level-2 trails; ids, `order`, `requires`, `koreanLevel` and
the old step ids unchanged (s1 to s3, plus s4 where a trail has four clue steps, and finale). Titles are kept. Built on phase0
after generator waves 6 and 7; the grammar list of every trail is in the trail file and in `manifest.json`.

## Result

| metric | start (phase0) | final |
|---|---|---|
| unknownOverOneShare | 0.056508 | 0.054293 (none of the 67 new sentences has two unknown words) |
| formDependentShare.2 | 0.093817 | 0.146444 |
| this batch: form-dependent steps | 0 of 58 old | 26 of 67 (38.8%) |
| bareShare.2 | 0.447761 | 0.387029 |
| topTypeShare.2 | 0.279318 | 0.240586 |
| distinctTypes.2 | 23 | 26 |
| duplicateSignatures | 10 | 8 (none of mine) |
| verbObjectMax / PairsOver3 | 6 / 5 | 6 / 5 |

## Trails

| id | project | grammar |
|---|---|---|
| nachthafen | pier of planks, pumpkin lantern on it, sapling beside it, boat ride at night | -세요, 에서/에 |
| adv_husbandry_tactical_fishing | iron bucket, watch a cod, cod bucket | (으)로 material, 에서 |
| adv_husbandry_axolotl_in_a_bucket | lush cave, copper torch by the pool, axolotl, bucket | 에서/에 |
| riffpfad | warm sea, swim, kelp on sand, dolphin fed | (으)로 direction, 에서/에, 에게 |
| kuestenwacht | plank ring, bed inside, banner beside, night in the bed | (으)로 material, 에서/에 |
| meerestiefe | boat, deep sea, rod in hand, island beach (-고), sapling | (으)로 direction, 들고, -고 |
| ozeanfahrt | chest boat, ride, lukewarm sea, carpet on the beach | (으)로 direction, 에서/에 |
| w3_08 | salmon with the rod, carpet on planks, cat watched and fed | 들고, 에서/에, 에게 |
| w4_01 | turtle, seagrass and kelp on the sand, swim | 에서/에 |
| w4_02 | stone-brick pillar, banner, copper lantern on top, ten seconds of watch | (으)로 material, 에서/에, 십 초 |
| w4_03 | rod, lily pad, salmon on the planks, roast, salmon for the cat | 에서/에, 에게 |
| w4_04 | boat, banner by the water, ladder, swim by the ladder | 에서/에 |
| w4_05 | fence ring with lantern and banner, night swim, squid | 에서/에 |
| w4_07 | barrel, banner, fish in it, a sale, cooked cod for a villager | 에서/에, 에게 |
| w4_08 | sand, cactus, sugar cane by the water, fence ring, flower inside | 에서/에 |

Reachability: all steps are overworld. The warm, lukewarm and deep sea need a boat; the lush cave is a real biome step; no
ocean monument, no Nether. No trail moved to level 3.
