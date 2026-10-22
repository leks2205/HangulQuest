# BATCH-016 plan: the Nether I, crossing over (first level-3 batch)

Brief: `content/briefs/BATCH-016.md` on top of BATCH-008 and `content/briefs/L3-PLAN.md`. Fifteen trails; ids, `order`, `requires`, titles and the old step ids
(s1 to s3, finale) are kept, and each trail gets one or two more steps. Built on phase0 at 4cfaff0 and merged with phase0 three times (i18n checker fixes,
generator wave 9, BATCH-015). The grammar list of every trail is in the trail file and in `manifest.json`.

## Result

| metric | start (4cfaff0) | phase0 at the last merge | final |
|---|---|---|---|
| unknownOverOneShare | 0.050273 | 0.049014 | 0.047450 |
| formDependentShare.3 | 0 | 0 | 0.056848 |
| bareShare.3 | 0.694524 | 0.694524 | 0.583979 |
| topTypeShare.3 | 0.391931 | 0.391931 | 0.333333 |
| distinctTypes.3 | 11 | 11 | 23 |
| duplicateSignatures | 5 | 3 | 3 (none of mine) |
| medianEncounters | 4.0 | 4.0 | 4.0 |
| this batch: form-dependent steps | | | 22 of 61 (36.1%) |
| this batch: bare steps | | | 2 of 61 |

formDependentShare.3 is a share of all level-3 steps (99 old trails); my 22 form-dependent steps are what moves it. No sentence of mine has two unknown
words, no step repeats a signature, and no (verb, object) pair of mine goes past three.

## The learner path

The nine Nether trails sit late on the learner path once they are level 3 (index 249 to 290), after the level-2 trails. The words that matter are still
new there: 흑요석, 부싯돌과 부시 and 네더 start in `adv_nether_root`, 금 주괴 and 피글린 in `adv_nether_distract_piglin`, and so on down the chain.
Each new word has a sentence to itself for its first three encounters (the trail title and the finale line count as encounters). 물리치다, 구하다 and
바라보다 are not lexicon words, so they cost nothing.

## The level move and the depth rule

The brief moves the nine level-2 Nether trails to koreanLevel 3. `QuestlineValidationTest` requires `koreanLevel == Topik.levelForDepth(depth)`, and the
nine sit at depth 5 and 6 (level 2). The trails below them are BATCH-017 trails at level 3 up to depth 9, so adding depth to the nine moves
`adv_nether_all_potions` and `adv_nether_create_beacon` to depth 10. See the first generator weakness in `manifest.json`: one assertion fails, everything
else passes. The commit waits for the lead's decision.

## Trails

| id | project | grammar |
|---|---|---|
| adv_nether_root | Obsidian pillar, flint and steel, a top block, then a gold block into a chest and across. | L3.gi_jeone, L2.euro_material, L3.past |
| adv_nether_distract_piglin | Craft gold, pay a piglin, leave the gold blocks alone near it, pay and open the chest. | L2.ege, L3.jima_seyo, L3.eun_hue, L3.past |
| adv_nether_fast_travel | A pillar in the Nether, a compass walk, keep off the bed, Overworld then Nether. | L3.jima_seyo, L3.eun_hue, L3.past |
| adv_nether_find_bastion | Look at and mine polished blackstone bricks, piglin before gold, chest shut near piglins. | L3.gi_jeone, L3.jima_seyo, L3.past |
| adv_nether_find_fortress | Nether bricks, cobblestone beside them, a blaze. | L1.loc_yeop_e, L3.past |
| adv_nether_obtain_ancient_debris | Down to Y 20, stone beside the lava, diamond pickaxe, debris, netherite scrap. | L1.loc_yeop_e, L3.future_geoyeyo |
| adv_nether_obtain_crying_obsidian | Look, mine, place beside obsidian, mine then craft a respawn anchor. | L3.eun_hue, L1.loc_yeop_e, L3.su_itda |
| adv_nether_return_to_sender | Blaze powder, fire charge, look at a ghast, defeat it. | L2.seyo, L3.past |
| adv_nether_ride_strider | Saddle from a chest, look at a strider, warped fungus on a stick, ride. | L2.eseo_activity, L3.su_itda |
| adv_story_enter_the_nether | Obsidian wall, flint and steel in hand, a layer on top, look, cross. | L3.eun_hue, L3.su_itda, L2.go_held |
| adv_story_form_obsidian | Lava with a water bucket, pour then look at the obsidian, stone by water, mine with a diamond pickaxe. | L3.eun_hue, L3.euro_instrument, L3.su_itda |
| adv_nether_explore_nether | Netherrack, dirt beside it, soul sand, gravel on top. | L1.loc_yeop_e, L1.loc_wi_e, L3.past |
| adv_nether_obtain_blaze_rod | Arrows with a bow in hand, defeat a blaze, leave water alone, two blaze rods. | L3.jima_seyo, L3.su_itda, L3.euro_instrument |
| adv_nether_ride_strider_in_overworld_lava | Stone over lava, look at a strider, ride it in the Overworld. | L1.loc_wi_e, L3.past |
| vulkanpfad | Climb to 85, magma, bricks beside it, a torch walk at night, lava at night. | L2.euro_direction, L2.go_held, L3.past |

## Reachability

The Nether is allowed at level 3. `adv_nether_obtain_ancient_debris` leaves `reachabilityDebt`. Nothing goes to level 4 or above.
