# BATCH-014 plan: colours, garden and treasure (level 2)

Brief: `content/briefs/BATCH-014.md` on top of BATCH-008. Fifteen level-2 trails; ids, `order`, `requires`, `koreanLevel` and the old
step ids (s1 to s3, and finale) unchanged. Titles are kept, except the garden-tool trail that used to need netherite
(`adv_husbandry_obtain_netherite_hoe`, now 괭이의 길). Built on phase0 at the brief and merged with phase0 again before the final gates.
The grammar list of every trail is in the trail file and in `manifest.json`.

## Result

| metric | start (phase0 at the brief) | final |
|---|---|---|
| unknownOverOneShare | 0.052206 | 0.051597 |
| formDependentShare.2 | 0.272366 | 0.329502 |
| bareShare.2 | 0.242545 | 0.164751 |
| topTypeShare.2 | 0.184891 | 0.168582 |
| distinctTypes.2 | 28 | 28 |
| noveltyShare | 0.597619 | 0.604762 |
| objectOnlyRepeats | 105 | 99 |
| hintWordCoverage | 0.96432 | 0.968528 |
| meanUnknown | 0.88005 | 0.863022 |
| targetMetShare | 0.165094 | 0.165094 |
| seenShare | 0.726415 | 0.731132 |
| duplicateSignatures, verbObjectMax, verbObjectPairsOver3 | 8, 6, 5 | 7, 6, 5 (none of mine) |
| this batch: form-dependent steps | 0 of 55 old | 35 of 74 (47.3%) |
| this batch: bare steps | 39 of 55 old | 3 of 74 |

No sentence of mine has two unknown words, and no step repeats a signature of any other trail. Each new word is paired with words the
learner has met three times; the colour compounds (파란색 양털, 주황색 염료 ...) come back in three steps before they sit next to a second new word.

## Trails

| id | project | grammar |
|---|---|---|
| farbenpfad | Dye a sheep blue, craft blue wool, build a pillar of it, plant a flower beside it, top the pillar with a red candle; the finale contrasts the two colours. | L2.seyo, L2.ege, L2.euro_material, L2.eun_neun_contrast |
| gartenzaun | A ring of spruce fence, a flower inside it, a gate beside the fence that opens, a carpet outside; the finale contrasts inside and outside. | L2.seyo, L2.eun_neun_contrast |
| abenteuerpfad | Craft a compass, walk to a mountain with it in hand, build a camp ring of planks with a bed inside and a sign on top. | L2.seyo, L2.euro_direction, L2.go_held, L2.euro_material |
| glueckspfad | Mine gold ore, smelt an ingot, store it in a chest, put a sign beside the chest and write on it. | L2.seyo, L2.eseo_activity |
| kartenpfad | A map room: a cartography table in front of a chest, open it, store a map in the chest, hang an item frame beside the table. | L2.seyo |
| festungpfad | A small brick fortress: ring wall, door beside it, bed inside, yellow candle on top, red banner outside, door closed. | L2.seyo, L2.euro_material |
| schatzkammer | Underground: go down, craft a gold block, put it between two torches, a sign beside it, look at it. | L2.seyo, L2.loc_sai_e, L2.num_sino |
| gartenkunst | A dirt cross with a pink tulip, a dirt pyramid with an orange tulip, a gravel path between the flowers and the fence. | L2.seyo, L2.euro_material, L2.hago_wa_gwa, L2.loc_sai_e |
| faerberei | Orange dye at the crafting table (에서) and a cauldron beside the furnace (에), orange wool, a dyed sheep, the wool laid on a fence to dry. | L2.seyo, L2.ege, L2.eseo_activity |
| gerberei | Collect leather, craft a leather cap, store it in a chest, a cauldron in front of the chest, fill it. | L2.seyo |
| adv_adventure_crafters_crafting_crafters | A crafter in front of a chest, a sign on top of it and a lever beside it, the lever pulled. | L2.seyo |
| adv_adventure_read_power_of_chiseled_bookshelf | A chiseled bookshelf and six books, the shelf beside a chest, filled with the books, a lectern on top. | L2.seyo |
| adv_husbandry_obtain_netherite_hoe | A garden-tool trail: a stone hoe, till the soil and plant wheat, fence the field, a carved pumpkin inside as a scarecrow. | L2.seyo, L2.go_sequence |
| adv_story_lava_bucket | Look at lava, dirt beside it as a barrier, a lava bucket and a climb up with it (-고), then fuel a furnace with it. | L2.seyo, L2.go_sequence |
| gartenpfad | A brick row, a flower beside it, fill a composter, a beehive between two flowers, watch a bee. | L2.seyo, L2.loc_sai_e |

## Reachability

All steps are overworld. No Nether, End, structure or monument. Not trivial in the first week: nine gold ingots for the gold block, honeycomb for the
coloured candles, gold ore (deep caves or badlands), a cave at height 40 and lava, and iron plus redstone for the compass and the crafter (see
openIssues in the manifest). `adv_husbandry_obtain_netherite_hoe` leaves `reachabilityDebt`. No trail moved to level 3.
