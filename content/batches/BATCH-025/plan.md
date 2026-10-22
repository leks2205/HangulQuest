# BATCH-025 plan: small poems III (w7, w8), level 4

Brief: `content/briefs/BATCH-025.md` on top of `L456-PLAN.md`, BATCH-016 to BATCH-018, BATCH-022 and BATCH-023. Fifteen trails; ids, `order`, `requires` and `tier` are kept. Every trail is level 4 by tree depth (11 or 12), so none changes level. Every clue is generated; only the fifteen finale lines are authored (all in `korean-lines.tsv`). Each poem is four steps (`s1`, `s2`, `s3`, `finale`), so the batch has 60 steps, two more than the 58 it replaces.

| id | poem |
|---|---|
| w7_01 | A flint and steel at night, an iron door facing north, a candle lit, ten blocks north (WALK). |
| w7_02 | A crossbow (retitled 금빛 과녁): a target hit in clear weather (SHOOT), a target watched facing east (FACING), gold ore mined then smelted at night (-아/어서). |
| w7_03 | Iron: raw iron smelted by day, an iron block, a helmet in the rain, an anvil. |
| w7_04 | Potatoes harvested in the meadow with a hoe (-(으)러 가다), a smoker crafted in the rain, a smoker watched at dusk, a potato put in and a baked one taken out (-아/어서). |
| w7_05 | Soul sand dug in the soul sand valley with a pickaxe in hand (-(으)러 가다), a soul torch, a soul campfire, the campfire lit at night. |
| w7_06 | A row of netherrack, magma on the row, a lava bucket, twenty blocks south (WALK). |
| w7_07 | Roses in the east (retitled): twenty blocks east by day (WALK), a sapling facing east (FACING), rose bushes, pink wool next to a flower. |
| w7_08 | Red bricks: bricks collected in the rain, brick walls at night, bricks watched at dawn, bricks crafted then brick stairs placed (-아/어서). |
| w7_09 | The song of thunder (retitled): raw copper, a lightning rod crafted in thunder, a climb to Y 90 in clear weather, the rod watched in thunder. |
| w8_01 | Obsidian broken by day with a diamond pickaxe, four obsidian placed facing east, ten blocks east at night (WALK), obsidian watched. |
| w8_02 | An eye of ender with a pearl in hand, thirty blocks west at night (WALK), cracked stone bricks broken by day, stone bricks watched. |
| w8_03 | A picture in wool (retitled): gray, green, brown and purple wool, each in its own place (the colour words). |
| w8_04 | The little island (retitled): mushrooms picked in the mushroom fields at night (-(으)러 가다), a boat, a swim in the rain, a seat in the boat. |
| w8_05 | The song of the egg (retitled): a chicken watched at dusk, an egg thrown at a target from ten blocks (SHOOT), sugar, a cake eaten in the rain. |
| w8_06 | The end of the sky (retitled): a pillar of planks, a sign on top, a climb at sunrise, a jump into water in clear weather. |

Constructions: -(으)면 on time and weather in 27 clues, WALK in 5, FACING in 4, SHOOT in 2, -아/어서 in 3 finales, -(으)러 가다 in 3 steps. 37 of 60 steps are form-dependent (61.7%). Finales use -지만 twice (w7_03, w7_05) and a timeless flavour sentence each.

Retitled because the old theme was not reachable or did not fit the new steps: `w7_02` (no sword any more), `w7_07` (the Wither skull is level 6), `w7_09` (the ghast tear), `w8_03`, `w8_04` (the dragon island), `w8_05` (the dragon egg is level 6) and `w8_06` (the void). The other eight titles stay.

Slack decisions:
- Duplicate signatures: none added. A step whose bare signature already exists carries a held item or a condition (`철 주괴를 들고 …`, `낮이 되면 …`) so the signature is new.
- Pairs: no (verb, object) pair passes three, and `verbObjectMax` and `verbObjectPairsOver3` stay at 7 and 13. Eight pairs of this batch end at exactly three (`SWITCH_ON candle`, `CRAFT gold_ingot`, `MELT iron_ingot`, `CRAFT lightning_rod`, `PLACE obsidian`, `WATCH obsidian`, `WATCH chicken`, `CRAFT sugar`), each from two before; objects with free pairs were chosen everywhere else (`BREAK` instead of `MINE` for obsidian, an iron helmet instead of boots, bricks collected instead of placed).
- `unknownOverOneShare`: 76 of 1725 sentences before, 76 of 1727 now (0.044058 to 0.044007). The 76 are all in the first 60 trails of the path, so only more sentences can lower the share: the batch keeps all 60 of its sentences at one unknown word at most, and has two steps more than before.
- Metrics moved (all better): `formDependentShare.4` 0.1168 to 0.3813, `bareShare.4` 0.7007 to 0.3094, `topTypeShare.4` 0.4599 to 0.2878, `distinctTypes.4` 17 to 24, `objectOnlyRepeats` 46 to 38, `noveltyShare` 0.7619 to 0.7810, `boredomIndex` 0.0209 to 0.0168, `meanUnknown` 0.7206 to 0.7087, recycling `seenShare` 0.7689 to 0.8019, `medianEncounters` 4.5 to 5, `targetMetShare` 0.2028 to 0.2123, hint coverage 0.9786 to 0.9821 (fully known) and 0.9923 to 0.9936 (words).
- The baseline file is not rewritten here, so BATCH-024 can land without a merge conflict in it; the moved metrics are listed in the commit message.

Notes for the integrator:
- EUREO steps need IN_BIOME and the existing test `everyConditionAppearsInTheKoreanClue` compares the clue with and without conditions, so each EUREO step here also carries a second condition that is spoken (a held tool or the time): `철 괭이를 들고 …`, `밤이 되면 …`.
- SHOOT without a target does not validate (`bad item null`), so both SHOOT trails shoot at a target block.
- A PILLAR or ROW is at most four blocks; the PILLAR fixture does not build a log pillar, so the sign pillar is made of planks.
