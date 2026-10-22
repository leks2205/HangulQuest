# BATCH-015 plan: the adventurer (level 2, last level-2 batch)

Brief: `content/briefs/BATCH-015.md` on top of BATCH-008. Twelve level-2 trails; ids, `order`, `requires`, `koreanLevel` and the step ids (s1.. and finale)
unchanged. Titles are kept for the six trails whose advancement a week-one player can reach (모험, 몬스터 사냥꾼, 부러진 화살, 이게 인갑인갑다, 번개 멈춰!, 집으로 이끌려가네,
자진 유배); the five that need a structure or the deep dark got a new Korean title and a week-one project. Built on phase0 at the brief (3c50d27) and merged with
phase0 again (generator wave 9). The grammar list of every trail is in the trail file and in `manifest.json`. No koreanLevel was changed; the five rebuilt
trails are proposed for level 3 in the manifest.

## Trails

| id | project | grammar |
|---|---|---|
| adv_adventure_root | Shield, a night walk with it, plank wall with a bed beside it, two spiders at night. | L2.seyo, L2.go_held |
| adv_adventure_avoid_vibration (소리 경보) | Sandstone wall, note block beside it, pressure plate in front of the door, hear the note block at night. | L2.seyo, L1.loc_yeop_e, L1.loc_ap_e |
| adv_adventure_brush_armadillo | Brush, savanna, look at an armadillo, feed it a spider eye, get the scute. | L2.seyo, L2.ege |
| adv_adventure_heart_transplanter (망루) | Log pillar, staircase, candle on top, sign beside it, look at the candle at night. | L2.seyo, L1.loc_yeop_e, L1.loc_wi_e |
| adv_adventure_kill_a_mob | Iron sword, armor stand beside the door, two creepers and two zombies at night. | L2.seyo, L1.loc_yeop_e |
| adv_adventure_lightning_rod_with_villager_no_fire | Rod on a birch wall, carpet beside it, an emerald for the villager, look at the rod in the rain. | L2.seyo, L2.ege, L1.loc_wi_e, L1.loc_yeop_e |
| adv_adventure_minecraft_trials_edition (훈련장) | Hay cross as a dummy with a pumpkin head, target block beside it, look at it with a bow. | L2.seyo, L1.loc_wi_e, L2.go_held |
| adv_adventure_ol_betsy | Crossbow, arrow, target between hay and chest, night walk with the crossbow, two skeletons at night. | L2.seyo, L2.go_held, L2.loc_sai_e, L2.hago_wa_gwa |
| adv_adventure_salvage_sherd (여행 준비) | Sell a potato, buy a pumpkin pie, store it, leave the house with it in hand. | L2.seyo, L2.ege, L2.ege_source, L2.go_held |
| adv_adventure_trim_with_any_armor_pattern (멋쟁이) | Iron boots, smithing table between furnace and chest, carpet in front, boots into the chest. | L2.seyo, L2.loc_sai_e, L2.hago_wa_gwa, L1.loc_ap_e |
| adv_adventure_use_lodestone | Compass, chiseled bricks, lodestone on a chest, use it with the compass, walk home. | L2.seyo, L2.go_held, L1.loc_wi_e |
| adv_adventure_voluntary_exile | Leave the house with a shield, watch a pillager, granite wall with a bell, two pillagers by day with a crossbow. | L2.seyo, L2.go_held, L1.loc_yeop_e |

## Rules kept

- Every sentence is generated; only the finale lines are authored (all in `korean-lines.tsv`).
- No duplicate signature of mine: plain DEFEAT of the four mobs has a night or day condition, walls, a cross and stairs replace the saturated rings and pillars,
  and the saturated (verb, object) pairs are avoided by using other ids.
- At most one unknown word per sentence except one (brush_armadillo/s4, 아르마딜로에게 거미 눈을 주세요); each new item name has its own step first.
- Reachability: overworld only, no structure; the lightning rod finale needs rain, the trade steps need a farmer (see openIssues in the manifest).
