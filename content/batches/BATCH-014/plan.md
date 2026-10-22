# BATCH-014 plan: colours, garden and treasure (level 2)

Brief: `content/briefs/BATCH-014.md` on top of BATCH-008. Fifteen level-2 trails; ids, `order`, `requires`, `koreanLevel` and the old
step ids (s1 to s3, and finale) unchanged. Titles are kept, except the garden-tool trail that used to need netherite
(`adv_husbandry_obtain_netherite_hoe`, now 괭이의 길). Built on phase0 at the brief (28af90a); phase0 moved twice meanwhile (UI pass and the
Spanish language, then BATCH-013) and is merged. The grammar list of every trail is in the trail file and in `manifest.json`.

## Result

| metric | start (28af90a) | phase0 after BATCH-013 | final (merged) |
|---|---|---|---|
| unknownOverOneShare | 0.052206 | 0.051534 | 0.050273 |
| formDependentShare.2 | 0.272366 | 0.328244 | 0.379817 |
| bareShare.2 | 0.242545 | 0.175573 | 0.102752 |
| topTypeShare.2 | 0.184891 | 0.162214 | 0.188991 |
| distinctTypes.2 | 28 | 30 | 30 |
| noveltyShare | 0.597619 | 0.611905 | 0.614286 |
| objectOnlyRepeats | 105 | 101 | 95 |
| medianEncounters | 3.0 | 3.5 | 4.0 |
| hintWordCoverage | 0.96432 | 0.967018 | 0.971304 |
| meanUnknown | 0.88005 | 0.861963 | 0.844337 |
| targetMetShare | 0.165094 | 0.169811 | 0.169811 |
| seenShare | 0.726415 | 0.740566 | 0.745283 |
| duplicateSignatures, verbObjectMax, verbObjectPairsOver3 | 8, 6, 5 | 6, 6, 5 | 5, 6, 5 (none of mine) |
| this batch: form-dependent steps | 0 of 55 old | | 35 of 76 (46.1%) |
| this batch: bare steps | 39 of 55 old | | 3 of 76 |

No sentence of mine has two unknown words, and no step repeats a signature of any other trail. Each new word is paired with words the
learner has met three times; a colour compound (파란색 양털, 주황색 염료 ...) comes back in three steps before it sits next to a second new word.
topTypeShare.2 is higher than after BATCH-013 alone (PLACE_REL and PLACE_ON_PATTERN are the point of the batch) but far inside the 0.25 cap.

## Trails

| id | project | grammar |
|---|---|---|
| farbenpfad | Dye a sheep blue, craft blue wool, build a pillar of it, plant a flower beside it, top the pillar with a red candle; the finale contrasts the two colours. | L2.seyo, L2.ege, L2.euro_material, L2.eun_neun_contrast |
| gartenzaun | A ring of spruce fence, a flower inside it, a gate beside the fence that opens, a carpet outside; the finale contrasts inside and outside. | L2.seyo, L2.eun_neun_contrast |
| abenteuerpfad | Craft a compass, walk to a mountain with it in hand, build a camp ring of planks with a bed inside and a sign on top, lie down in the bed at night. | L2.seyo, L2.euro_direction, L2.go_held, L2.euro_material |
| glueckspfad | Mine gold ore, smelt an ingot, store it in a chest, put a sign beside the chest and write on it. | L2.seyo, L2.eseo_activity |
| kartenpfad | A map room: a cartography table in front of a chest, open it, store a map in the chest, hang an item frame beside the table. | L2.seyo |
| festungpfad | A small brick fortress: ring wall, door beside it, bed inside, yellow candle on top, red banner outside, door closed. | L2.seyo, L2.euro_material |
| schatzkammer | Underground: go down, craft a gold block, put it between two torches, a sign beside it, look at it. | L2.seyo, L2.loc_sai_e, L2.num_sino |
| gartenkunst | A dirt cross with a red tulip, a dirt pyramid with an orange tulip, a gravel path between the flowers and the fence. | L2.seyo, L2.euro_material, L2.hago_wa_gwa, L2.loc_sai_e |
| faerberei | Orange dye at the crafting table (에서) and a cauldron beside the furnace (에), orange wool, a dyed sheep, the wool laid on a fence to dry. | L2.seyo, L2.ege, L2.eseo_activity |
| gerberei | Collect leather, craft a leather cap, store it in a chest, an armor stand in front of the chest, fill a cauldron. | L2.seyo |
| adv_adventure_crafters_crafting_crafters | A crafter in front of a chest, a sign on top of it and a lever beside it, the lever pulled. | L2.seyo |
| adv_adventure_read_power_of_chiseled_bookshelf | A chiseled bookshelf and six books, the shelf beside a chest, a look at it, filled with the books, a lectern on top. | L2.seyo |
| adv_husbandry_obtain_netherite_hoe | A garden-tool trail: a stone hoe, till the soil and plant wheat, fence the field, a carved pumpkin inside as a scarecrow. | L2.seyo, L2.go_sequence |
| adv_story_lava_bucket | Look at lava, dirt beside it as a barrier, a lava bucket and a climb up with it (-고), then fuel a furnace with it. | L2.seyo, L2.go_sequence |
| gartenpfad | A brick row, a flower beside it, fill a composter, a beehive between two flowers, watch a bee. | L2.seyo, L2.loc_sai_e |

## Reachability

All steps are overworld. No Nether, End, structure or monument. Not trivial in the first week: nine gold ingots for the gold block, honeycomb for the
coloured candles, gold ore (deep caves or badlands), a cave at height 40 and lava, and iron plus redstone for the compass and the crafter (see
openIssues in the manifest). `adv_husbandry_obtain_netherite_hoe` leaves `reachabilityDebt`. No trail moved to level 3.
