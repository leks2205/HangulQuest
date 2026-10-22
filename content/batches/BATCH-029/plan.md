# BATCH-029 plan: the End game (level 6)

Brief: `content/briefs/BATCH-029.md` on top of `L456-PLAN.md`. Fifteen trails; ids, `order`, `requires`, `tier`, `topic` and the official advancement titles are kept. Every trail is level 6 by tree depth (17 to 22), so none changes level. Every clue is generated; only the fifteen finale lines are authored (all in `korean-lines.tsv`). Each trail has four steps (`s1`, `s2`, `s3`, `finale`): 60 steps, 28 form-dependent (46.7%). No `concepts.json` change was needed.

| id | what it checks | earns the advancement? |
|---|---|---|
| regenbogenfahne | green dye by day, a white banner crafted holding wool, a pillar of three cherry planks, a green banner on top of the pillar | not an advancement; the finale claims only the banner on the pillar |
| adv_end_root | an enderman in the rain, pearls then an eye of ender, no looking at an enderman, the End reached holding an eye | yes (REACH_DIMENSION the_end) |
| adv_end_kill_dragon | bow then arrows, two endermen with a sword in hand, a bow hit on the dragon, the dragon defeated | yes (DEFEAT ender_dragon) |
| adv_end_dragon_breath | glass bottles by day, a look at the dragon with a bottle in hand, a wait near the dragon, dragon's breath obtained | yes (ACQUIRE dragon_breath) |
| adv_end_dragon_egg | a piston by day, a piston placed facing north, a look at the egg with a pickaxe, the egg obtained | yes (ACQUIRE dragon_egg) |
| adv_end_enter_end_gateway | pearls at night, thirty blocks east with a pearl, end stone seen, an end gateway entered in the End (ENTER_GATEWAY, seen as the throw from beside the gateway) | yes (the gateway throw) |
| adv_end_find_end_city | the End Highlands with a pearl, purpur broken with a pickaxe in hand, an end rod seen, a purpur pillar seen | partly: no goal sees a structure; blocks that only an End city has are the proxy |
| adv_end_elytra | a shulker with a shield in hand, a brewing stand in the highlands, rockets crafted with paper in hand, the elytra obtained | yes (ACQUIRE elytra) |
| adv_end_levitate | a shulker seen with a shield, two shells, a purpur pillar, the levitation effect at height 100 or more in the End (HAVE_EFFECT with a height condition) | nearly: the effect and the height are checked, the 50 blocks of rise are not |
| adv_end_respawn_dragon | a ghast defeated with a bow, tears, eyes of ender, four end crystals set down in the End (PLACE_ENTITY) | nearly: four crystals placed in the End are checked, not that they stand on the exit portal |
| w11_01 to w11_05 | five small poems: stars (sea lanterns, end rods), a moonlit voyage, an hourglass, a star map, a galaxy altar | n/a |

Constructions: -(으)면 on time and weather (15 clues), -는 동안 on HOLD_WHILE (5) and WAIT (1), -아/어도 on AVOID (1), -지 마세요 AVOID (2), -기 전에/-(으)ㄴ 후에 SEQUENCE (2), -아/어서 (via 녹여서, 1), WALK (3), FACING (several), HOLDING. No time or weather condition sits on a step that must be done in the End. No level-6 flavour needed -(으)ㄹ수록 other than in the levitate finale.

Slack decisions:
- Duplicate signatures: none added. Every step whose bare signature exists elsewhere carries a held item or a second condition.
- Pairs: no (verb, object) pair passes three.
- `unknownOverOneShare` is lower than at the start (every clue has at most one unknown word).
- Recycled core words that had 0 or few encounters: 초록색, 하얀색, 일곱, 찾다, 하루, 부르다, 자수정.
- Metrics moved (all better): `formDependentShare.6` 0.1528 to 0.3203, `bareShare.6` 0.6528 to 0.3856, `topTypeShare.6` 0.4861 to 0.3529, `distinctTypes.6` 10 to 24, `noveltyShare`, `boredomIndex`, `objectOnlyRepeats` 25 to 20, `seenShare`, `targetMetShare`, `hintFullyKnownShare`, `hintWordCoverage`, `meanUnknown`. The baseline file is not rewritten.

Notes for the integrator:
- SHOOT needs an entity concept (end crystal has an item concept only), so the bow shot hits the dragon.
- A pillar of four with a block placed on top does not fit the 7x7x7 snapshot; the flag pillar is three blocks.
- 캐다 is rejected for purpur (KO010), so the step says 부수다.
- A chest cannot be opened by hand (only doors, trapdoors, fence gates), so no chest step in the city.
- The flag finale reads 걸다 (the generator's verb for banners).
