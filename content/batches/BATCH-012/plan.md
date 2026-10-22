# BATCH-012 plan: mountains, ice and night (level 2)

Brief: `content/briefs/BATCH-012.md` on top of BATCH-008. Fifteen level-2 trails; ids, `order`, `requires`, `koreanLevel` and the old step ids
(s1 to s3, s4 where there was one, and finale) unchanged. Titles are kept. Built on phase0 at the brief and merged with phase0 after BATCH-010.
The grammar list of every trail is in the trail file and in `manifest.json`.

## Result

| metric | start (56e9e87) | phase0 after BATCH-010 | final |
|---|---|---|---|
| unknownOverOneShare | 0.054293 | 0.05293 | 0.052206 |
| formDependentShare.2 | 0.146444 | 0.214137 | 0.264414 |
| bareShare.2 | 0.387029 | 0.301455 | 0.242545 |
| topTypeShare.2 | 0.240586 | 0.205821 | 0.184891 |
| distinctTypes.2 | 26 | 28 | 28 |
| noveltyShare | 0.557143 | 0.57619 | 0.597619 |
| objectOnlyRepeats | 116 | 109 | 105 |
| hintWordCoverage | 0.961371 | 0.962363 | 0.96432 |
| meanUnknown | 0.919192 | 0.89414 | 0.88005 |
| targetMetShare | 0.151659 | 0.161137 | 0.165877 |
| seenShare | 0.706161 | 0.71564 | 0.725118 |
| this batch: form-dependent steps | 0 of 55 old | | 30 of 77 (39.0%) |
| this batch: bare steps | | | 2 of 77 |
| duplicateSignatures, verbObjectMax, verbObjectPairsOver3 | 8, 6, 5 | 8, 6, 5 | 8, 6, 5 (none of mine) |

None of the new sentences has two unknown words: every new word is paired with words the learner has met three times.

## Trails

| id | project | grammar |
|---|---|---|
| wanderpfad | Go to a mountain on a clear day, climb and plant a spruce sapling, put a sign beside it, build a granite pillar and put a candle on top at night. | L2.seyo, L2.euro_direction, L2.go_sequence, L2.euro_material |
| schneesturm | On a snowy slope: a ring wall of birch planks, a furnace inside it with a rug in front, cook meat there and eat it by the fire. | L2.seyo, L2.euro_direction, L2.euro_material, L2.eun_neun_contrast |
| eiswand | Craft ladders, put one next to the ice, climb high with a ladder in hand, look at the blue ice, hang a banner beside the ladder. | L2.seyo, L2.go_held |
| gletscherpfad | Frozen peaks by day, two fences, a fence on a snow block with a sign beside it, and a polar bear to end with. | L2.seyo, L2.euro_direction |
| gipfelwaechter | A watchtower: climb at night, build a pillar of planks on the mountain, hang a banner beside it, put a copper torch on top, keep watch for ten seconds. | L2.seyo, L2.euro_material, L2.eseo_activity, L2.num_sino |
| alpental | A goat on the mountain, a fenced pen in the meadow with a cauldron as trough, melon seeds for a chicken, a cow by the cauldron. | L2.seyo, L2.eseo_activity, L2.ege |
| w4_06 | A meadow lake at the foot of the mountains: a fern and a banner beside the water, an evening swim, ten quiet seconds. | L2.seyo, L2.euro_direction, L2.eseo_activity |
| mondpfad | A night walk to a birch forest, a gravel path with a candle beside and a candle on it, a fox, and the last candle lit. | L2.seyo, L2.euro_direction |
| sternwarte | An observatory: a brick pillar with a glass roof, lilies of the valley by the bricks, a spyglass crafted at night, a night walk with it. | L2.seyo, L2.euro_material, L2.go_held |
| sternenhaus | A house under the stars: a ring wall, a door, a bed inside, glass on top, and a night in the bed. | L2.seyo, L2.euro_material, L2.eun_neun_contrast |
| w3_09 | A jack o'lantern in front of the door at night, a pumpkin pie for a neighbour, the door opened by the light. | L2.seyo, L2.ege |
| adv_adventure_sleep_in_bed | Craft an orange bed, put it by a chest, a rug in front of it, sleep in it at night. | L2.seyo, L2.eseo_activity |
| adv_adventure_fall_from_world_height | Go to the grove, get a bucket of powder snow and put it on stone as a soft landing, climb high, come back down with the bucket in hand. | L2.seyo, L2.euro_direction, L2.go_held, L2.num_sino |
| adv_adventure_spyglass_at_parrot | Sparse jungle by day, craft a spyglass, walk with it, plant a sapling by a log, wheat seeds for a parrot, look at the parrot through the spyglass. | L2.seyo, L2.euro_direction, L2.ege, L2.go_held |
| adv_adventure_honey_block_slide | Four bottles of honey make a honey block; a pillar of planks, the honey block beside it, a sign, and a look at the block. | L2.seyo, L2.euro_material, L2.counter_more |

## Reachability

All steps are overworld. No Nether, End, structure or monument. Rare in the first week: the spyglass (amethyst shard from a geode), bees and
honey, a snowy biome for powder snow, and a jungle for the parrot (see openIssues in the manifest). Three trails (gipfelwaechter, gletscherpfad,
sternenhaus) leave `reachabilityDebt`. No trail moved to level 3.
