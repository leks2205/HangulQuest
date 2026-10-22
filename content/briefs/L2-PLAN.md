# Level 2 rebuild plan (126 trails, BATCH-008 to BATCH-016)

Level 1 is finished once BATCH-007 merges; 98 trails were rebuilt in seven batches. Level 2 has 126 trails, which
is twice the roadmap's target of about 60 (D13).

## What level 2 adds

The grammar ladder (`grammar.json`, `ladder.json`) and roadmap Part C give level 2 these points:

- **-(으)세요.** The polite request is the default clue form from L2 on; the generator already switches.
- **에서 vs 에.** These are an activity place (빵 옆에서 먹어요) and a target (상자 위에 놓아요). Steps where only
  the particle tells the player where to act are form-dependent.
- **-고.** "A and then B" uses SEQUENCE with two parts, or HOLD_WHILE: 횃불을 들고 들어가세요.
- **하고/와/과.** Two objects or a companion: 주민하고 이야기하세요, 문과 벽 사이에.
- **은/는 for contrast.** This appears in authored hints and finales only, when two things are compared.
- **All location nouns:** 위, 아래, 옆, 앞, 뒤, 안, 밖, 사이, 근처.

**Form-dependent share.** At least 30% of the steps in a level-2 trail must be form-dependent: the Korean form
decides the action. Generator wave 4 adds the `variety.formDependentShare.2` metric, and every level-2 batch
raises it.

## What happens to each trail

- **Rebuilt** at level 2: the overworld trails that a day-3 to day-10 player can reach.
- **Moved up** to level 3 or 4 inside their batch, with `koreanLevel` changed and noted in the manifest.
  `ReachabilityGateTest` forbids the Nether at levels 1–2, so this covers the `adv_nether_*` trails and the ones
  that need a structure: strongholds, trial chambers, ancient cities, ocean monuments.
- **Retired:** a trail that repeats another trail's theme too closely gets `retired: true`, its steps kept and
  its id never deleted. Retiring is an ARCH decision; propose it in the manifest and do not do it in the batch.

## Batches (15 trails each)

| Batch | Theme | Trails |
|---|---|---|
| 008 | Fire and iron: the smithy | schmelzofen, schmelzpfad, steinherz, kohlenpfad, steinbruch, tiefenpfad, schmiedepfad, schmiedefeuer, werkzeugpfad, steinmetz, ziegelpfad, adv_story_upgrade_tools, adv_story_smelt_iron, adv_story_iron_tools, adv_story_obtain_armor |
| 009 | The kitchen and the feast | saatfeld, festmahl, schmauspfad, kochkunst, milchpfad, brauerei, bienenzucht, obstgarten, alchemiepfad, adv_husbandry_balanced_diet, adv_husbandry_wax_on, adv_husbandry_wax_off, waldfest, kuestenfest, gartenfest |
| 010 | Village, home and market | zuhausepfad, familienpfad, glockenpfad, uhrmacher, druckerei, karawanepfad, handelsstrasse, adv_adventure_trade, w3_01, w3_02, w3_03, w3_04, w3_05, w3_06, w3_07 |
| 011 | Sea and harbour | nachthafen, riffpfad, kuestenwacht, meerestiefe, ozeanfahrt, w3_08, w4_01, w4_02, w4_03, w4_04, w4_05, w4_07, w4_08, adv_husbandry_axolotl_in_a_bucket, adv_husbandry_tactical_fishing |
| 012 | Mountains, ice and night | wanderpfad, schneesturm, eiswand, gletscherpfad, gipfelwaechter, alpental, w4_06, mondpfad, sternwarte, sternenhaus, w3_09, adv_adventure_sleep_in_bed, adv_adventure_fall_from_world_height, adv_adventure_spyglass_at_parrot, adv_adventure_honey_block_slide |
| 013 | Animals and pets | pferderennen, reitpfad, pferdehof, gestuet, adv_husbandry_bred_all_animals, adv_husbandry_complete_catalogue, adv_husbandry_whole_pack, adv_husbandry_remove_wolf_armor, adv_husbandry_repair_wolf_armor, adv_husbandry_leash_all_frog_variants, adv_husbandry_froglights, adv_husbandry_kill_axolotl_target, adv_husbandry_allay_deliver_cake_to_note_block, adv_husbandry_feed_snifflet, adv_husbandry_plant_any_sniffer_seed |
| 014 | Colours, garden and treasure | farbenpfad, faerberei, gerberei, gartenzaun, gartenkunst, gartenpfad, kartenpfad, festungpfad, schatzkammer, glueckspfad, abenteuerpfad, adv_husbandry_obtain_netherite_hoe, adv_story_lava_bucket, adv_adventure_read_power_of_chiseled_bookshelf, adv_adventure_crafters_crafting_crafters |
| 015 | The adventurer | adv_adventure_root, adv_adventure_kill_a_mob, adv_adventure_ol_betsy, adv_adventure_avoid_vibration, adv_adventure_brush_armadillo, adv_adventure_heart_transplanter, adv_adventure_lightning_rod_with_villager_no_fire, adv_adventure_minecraft_trials_edition, adv_adventure_salvage_sherd, adv_adventure_trim_with_any_armor_pattern, adv_adventure_use_lodestone, adv_adventure_voluntary_exile |
| 016 | The Nether (moves to level 3) | adv_nether_root, adv_nether_distract_piglin, adv_nether_fast_travel, adv_nether_find_bastion, adv_nether_find_fortress, adv_nether_obtain_ancient_debris, adv_nether_obtain_crying_obsidian, adv_nether_return_to_sender, adv_nether_ride_strider |

## Order and parallelism

- **Batch order.** Batches go out in the order of the table. Two can run in parallel when their trails don't share a
  `requires` chain; then each must leave the slack the brief demands.
- **Waves vs batches.** A generator wave never runs alongside the batch that first depends on its new feature.
- **Reviews.** Every batch gets a blind dual review: Claude, plus Gemini in a fresh agy project (`--new-project
  --sandbox`). The fixes are applied before the next batch of the same theme starts.
