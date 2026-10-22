# Levels 4–6 rebuild plan (99 trails, BATCH-024 to BATCH-030)

Level 3 ends with BATCH-023. Four Nether trails (brewing, all potions, the beacon base, the skull tower) were already
rebuilt at level 4 by BATCH-017. That leaves 99 trails: 32 at level 4, 27 at level 5 and 40 at level 6.

## What each level adds

The grammar ladder is in `grammar.json` and `ladder.json`; roadmap Part C has the rationale.

| Level | Clue ending | New constructions |
|---|---|---|
| 4 | -아/어야 해요 | -(으)면 (a condition: 밤이 되면 …), -지만, -아/어서 (cause or sequence), -(으)러 가요 (purpose: 나무를 베러 숲에 가세요), -(으)ㄹ게요 (in dialogue and finales) |
| 5 | -아/어야 해요 | adnominal -(으)ㄴ/는/(으)ㄹ (빨간 꽃, 자는 주민, 만들 도구), -는 동안 (HOLD_WHILE and WAIT), -아/어도, -는데, -(으)니까 (reasons), 합쇼체 in speech to elders and librarians |
| 6 | -아/어야 해요, plus -(으)ㄹ 수밖에 없어요 where the sentence states a forced conclusion | -(으)ㄹ수록, -듯이, -기 마련이다, 반말 with children and friends |

**Generator first.** Generator wave 11 adds these constructions to the generator and the validator before
BATCH-024 starts. They give level 4 its form-dependent steps:
- -(으)면 with a condition that decides when to act;
- -아/어서 / -고 나서 orders;
- -(으)러 for purpose.

**Form-dependent share.** At least 30% per level, as before. Finales claim only what the goal checks, in the past
tense. A flavour line must be a timeless fact (see the finale rule in COMMON).

## Reachability by level

| Level | Allowed |
|---|---|
| 4 | The End is allowed: entering it, the dragon fight as the level's last step, the end city. So are trial chambers, ocean monuments and woodland mansions. |
| 5 | The deep dark and ancient cities, and the full trial-chamber vaults. |
| 6 | Everything, including the elytra, the dragon egg, respawning the dragon and the full beacon. |

## Batches

| Batch | Level | Theme | Trails |
|---|---|---|---|
| 024 | 4 | Into the End and the far kingdoms | kronenlicht, ewiges_eis, pyramidenpfad, bibliothekpfad, leuchtturmpfad, drachenhort, tempelwaechter, sternenkarte, handelsflotte, adv_nether_all_effects, adv_nether_create_full_beacon, adv_story_enter_the_end, wolkenpfad, unendlichereise, reisekrone |
| 025 | 4 | Small poems (w7, w8) | w7_01–w7_09, w8_01–w8_06 |
| 026 | 4–5 | The crown's workshop | w8_07, w8_08 (level 4); kronenwacht, kronenpfad, kronenglanz, zeitportal, schattenpfad, handwerksmeister, seelenpfad, golemwerkstatt, kronenrat, richterpfad, runenpfad (level 5) |
| 027 | 5 | Small poems (w9, w10) | w9_01–w9_08, w10_01–w10_07 |
| 028 | 5–6 | The last journeys and the colour chain | w10_08 (level 5); kronenfeier, letzte_reise, weltenschoepfer, kronenklang, weltwaechter, ewige_krone, schicksalspfad, weltenrichter, unsterblichkeit (level 6); rotwerk, blauwerk, gelbwerk, purpurwerk, orangewerk (level 6, the 색깔 chain) |
| 029 | 6 | The End game | regenbogenfahne; adv_end_root, adv_end_kill_dragon, adv_end_dragon_breath, adv_end_dragon_egg, adv_end_elytra, adv_end_enter_end_gateway, adv_end_find_end_city, adv_end_levitate, adv_end_respawn_dragon; w11_01–w11_05 |
| 030 | 6 | Small poems (w11, w12), the last batch | w11_06–w11_08, w12_01–w12_08 |

**Level follows depth.** `koreanLevel` must equal `Topik.levelForDepth`. If a batch finds a trail whose depth says
another level, it rebuilds the trail at that level and reports it.

**Running and reviewing.** At most two batches run in parallel, each leaving slack. Every batch gets the blind dual
review (Claude, plus Gemini in a fresh agy project). The fixes land before the next rc.
