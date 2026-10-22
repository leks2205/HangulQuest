# Level 3 rebuild plan (108 trails, BATCH-016 to BATCH-023)

Level 3 has 99 trails. The nine level-2 Nether advancement trails move up to it, because `ReachabilityGateTest`
keeps the Nether out of levels 1–2. That makes 108 trails in eight batches.

## What level 3 adds

From the grammar ladder (`grammar.json`, `ladder.json`) and roadmap Part C:

- **Clue ending.** Clues keep -(으)세요, and the ladder turns on **connectives** at level 3.
- **Time order.** -기 전에 and -(으)ㄴ 후에 / -고 나서 use SEQUENCE with an order connector: "문을 닫기 전에 횃불을
  놓으세요". These are the main form-dependent tool at this level. An order the player gets wrong is a form error.
- **-(으)ㄹ 수 있어요** in hints and finales: "이제 네더에 갈 수 있어요."
- **-지 마세요** with AVOID: "밤에 자지 마세요", "용암에 들어가지 마세요".
- **-(으)ㄹ 거예요** for a plan in an authored clue or finale: "내일은 산에 갈 거예요."
- **-았/었어요** for finale narration. This already exists; at level 3 a finale may carry two past-tense sentences.
- **(으)로** for instrument and direction: 활로, 곡괭이로, 북쪽으로.
- **Adnominal forms belong to level 5.** "-(으)ㄴ/는 + noun" is not used before then. The generator already keeps
  "하늘이 보이는 곳에서" for level 5.

**Form-dependent share.** At least 30% per level from level 2 on (D12). `variety.formDependentShare.3` starts low.

## Reachability at level 3

- The Nether is allowed: fortress, bastion, strider, blaze rods, ghasts.
- The End, ancient cities and the deep dark stay out until level 4 or above. `ReachabilityGateTest` decides; check it.
- Trial chambers, ocean monuments and woodland mansions: propose them, with the trail id and the reason, for a
  higher level in the manifest.
- Advancements that need the end game (the Wither, netherite armour, all potions, a full beacon): propose them for
  level 4–6 in the manifest. Do not change `koreanLevel` yourself. The lead session moves trails up when it merges.

## Batches

| Batch | Theme | Trails |
|---|---|---|
| 016 | The Nether I, crossing over | adv_nether_root, adv_nether_distract_piglin, adv_nether_fast_travel, adv_nether_find_bastion, adv_nether_find_fortress, adv_nether_obtain_ancient_debris, adv_nether_obtain_crying_obsidian, adv_nether_return_to_sender, adv_nether_ride_strider, adv_story_enter_the_nether, adv_story_form_obsidian, adv_nether_explore_nether, adv_nether_obtain_blaze_rod, adv_nether_ride_strider_in_overworld_lava, vulkanpfad |
| 017 | The Nether II, brewing, beacons and treasure | adv_nether_all_potions, adv_nether_brew_potion, adv_nether_charge_respawn_anchor, adv_nether_create_beacon, adv_nether_get_wither_skull, adv_nether_loot_bastion, adv_nether_netherite_armor, adv_nether_summon_wither, adv_nether_uneasy_alliance, adv_story_mine_diamond, kristallhoehle, kristallschmiede, schmuckpfad, schmuckgilde, bergwerk |
| 018 | Heroes of the village: story, guilds and trade | adv_story_cure_zombie_villager, adv_story_deflect_arrow, adv_story_enchant_item, adv_story_follow_ender_eye, adv_story_shiny_gear, adv_adventure_hero_of_the_village, adv_adventure_whos_the_pillager_now, adv_adventure_summon_iron_golem, adv_adventure_under_lock_and_key, adv_adventure_totem_of_undying, handelsgilde, postpfad, fischergilde, karawane, village_errands_demo |
| 019 | Bow, crossbow and trident: the hunter | adv_adventure_arbalistic, adv_adventure_blowback, adv_adventure_bullseye, adv_adventure_kill_all_mobs, adv_adventure_kill_mob_near_sculk_catalyst, adv_adventure_overoverkill, adv_adventure_shoot_arrow, adv_adventure_sniper_duel, adv_adventure_spear_many_mobs, adv_adventure_two_birds_one_arrow, adv_adventure_very_very_frightening, adv_adventure_throw_trident, adv_adventure_revaulting, adv_adventure_lighten_up, wolfpfad |
| 020 | Explorers: far views and high places | adv_adventure_adventuring_time, adv_adventure_spyglass_at_dragon, adv_adventure_spyglass_at_ghast, adv_adventure_play_jukebox_in_meadows, adv_adventure_trade_at_world_height, adv_adventure_trim_with_all_exclusive_armor_patterns, adv_adventure_walk_on_powder_snow_with_leather_boots, adv_adventure_who_needs_rockets, adv_adventure_craft_decorated_pot_using_only_sherds, weltreise, observatorium, himmelswarte, wolkenstadt, polarlicht, eiskrone |
| 021 | Wonders of the world: far lands and great feasts | weltmarkt, weltbaum, weltwunder, riesenfest, festtafel, tiefseehafen, grossesmeer, hafenwacht, koenigspfad, piratenpfad, tempelpfad, dschungelpfad, wuestenstadt |
| 022 | Small poems I (w5) and the watchtowers | w5_01–w5_09, frostwacht, gartenwacht, vogelpfad |
| 023 | Small poems II (w6) | w6_01–w6_08 |

## How the batches run

- **Moving the Nether trails.** BATCH-016 moves the nine level-2 Nether trails to `koreanLevel` 3 as part of its
  rebuild. This is the one level change a batch may make itself, because the plan decides it here.
- **Briefs.** Each batch brief starts from `BATCH-008.md` and replaces its level-2 rules with the level-3 rules above.
- **Reviews.** Every batch gets a blind dual review: Claude, and Gemini in a fresh agy project. Fixes land before the
  next batch on the same theme.
- **Two batches in parallel at most,** each leaving slack: no new duplicate signatures, and `unknownOverOneShare`
  strictly lower than before.
