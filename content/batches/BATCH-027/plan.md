# BATCH-027 plan: small poems IV (w9, w10), level 5

Brief: `content/briefs/BATCH-027.md` on top of `L456-PLAN.md`, BATCH-016 to BATCH-018 and BATCH-024/025. Fifteen trails; ids, `order`, `requires` and `tier` are kept. Every trail is level 5 by tree depth (13 to 15), so none changes level. Every clue is generated; only the fifteen finale lines are authored (all in `korean-lines.tsv`). Each poem is four steps (`s1`, `s2`, `s3`, `finale`), so the batch has 60 steps, eight more than the 52 it replaces. No `concepts.json` change was needed.

| id | poem |
|---|---|
| w9_01 | A crown crafted by day, a wait while the sun sets (-는 동안), a talk with a villager while holding a torch, a bell watched at night. |
| w9_02 | A map by day, a wait while it is night, a walk north with a compass at sunrise, a lantern placed facing north. |
| w9_03 | Emeralds by day, a wait while it rains, bread bought while holding an emerald, paper sold to a villager. |
| w9_04 | A stone-brick wall by day, two zombies defeated while holding a shield, no breaking of stone bricks even when night comes (-아/어도), an iron golem watched. |
| w9_05 | A clock watched in clear weather, a wait while the sun rises, no lying down in a bed even when the sun sets, a clock given to a villager by day. |
| w9_06 | A soul lantern on black wool at dusk (the colour adjective 검은), no torch even when the sun sets, a soul torch with soul sand at night, a soul lantern watched. |
| w9_07 | A grindstone in the rain, copper ore mined while holding an iron pickaxe, the grindstone used at night, shears crafted with an iron ingot. |
| w9_08 | A lectern in the rain, two bookshelves next to red wool at night (빨간), a talk with a villager while holding a book, the lectern used. |
| w10_01 (retitled 땅 아래의 빛) | Dirt dug in the plains (-(으)러 가다), gravel dug while holding a torch, glowstone on blue wool at night (파란), glowstone watched. |
| w10_02 | Thirty blocks west by day, raw gold into a chest in the rain, apples bought at dusk, raw gold smelted into ingots. |
| w10_03 (retitled 잊힌 사원) | Twenty-five blocks east at sunrise, chiseled sandstone in clear weather, no opening of a fence gate even when night comes, a stone pressure plate placed. |
| w10_04 | A skeleton defeated while holding a bow, bones at night, a bone block in the rain, two bone blocks stacked. |
| w10_05 | Cacti planted by day, no breaking of a cactus even when the sun sets, a wait while the weather is clear, a cactus watched. |
| w10_06 | Clay in the rain, bricks baked from clay at night (-아/어서), a decorated pot crafted with a brick in hand, the pot watched. |
| w10_07 | Thirty blocks south at dusk, a bed next to yellow wool (노란), a lie-down at night, a wait until day facing east. |

Constructions: -(으)면 on time and weather in 28 clues, -는 동안 on WAIT (5) and on HOLD_WHILE with an inner goal (7), -아/어도 on AVOID (5), colour adjectives (4: 검은, 파란, 노란, 빨간), -(으)러 가다 (1), -아/어서 (1), WALK (4). 47 of 60 steps are form-dependent (78.3%). Where a poem has a condition, the finale carries it in the past tense; no finale claims more than its goal checks.

Retitled because the old theme was out of reach or no longer fit: `w10_01` (the sand light became a light under the ground) and `w10_03` (`잊혀진` is a double passive, the standard form is `잊힌`). The other titles stay.

Slack decisions:
- Duplicate signatures: none added; `duplicateSignatures` falls from 2 to 1 (an old duplicate lived in a replaced trail). Every step whose bare signature exists elsewhere carries a held item or a second condition.
- Pairs: no (verb, object) pair passes three. Many pairs sit at exactly three already (BUY bread, CRAFT clock, CRAFT book, RING bell, GO desert, DIG sand, HIT target), so those were avoided: the clock is watched and given instead of crafted, the bell is watched, the desert is walked to instead of entered, the target is not shot.
- `unknownOverOneShare`: 76 of 1735 sentences before, 76 of 1743 now (0.043804 to 0.043603). All 60 sentences of the batch have at most one unknown word, so only the extra sentences lower the share.
- Metrics moved (all better): `formDependentShare.5` 0.1134 to 0.5333, `bareShare.5` 0.7423 to 0.3048, `topTypeShare.5` 0.5155 to 0.3143, `distinctTypes.5` 9 to 21, `noveltyShare` 0.8048 to 0.8262, `boredomIndex` 0.0138 to 0.0126, `objectOnlyRepeats` 33 to 31, `duplicateSignatures` 2 to 1, `meanUnknown` 0.7037 to 0.6885, recycling `seenShare` 0.8019 to 0.8066, `targetMetShare` 0.2123 to 0.2170, `hintFullyKnownShare` 0.9839 to 0.9845, `hintWordCoverage` 0.9942 to 0.9945.
- The baseline file is not rewritten here; the lead rebaselines after BATCH-026 and BATCH-027 are merged.

Notes for the integrator:
- AVOID cannot name a chest (`a minecraft:chest does not open or close by hand`); a door, trapdoor or fence gate works. Its EN and DE clues must hide the negation and the relation word `unter` (`A torch, and the sun sets — read the Korean.`).
- `GeneratorWaveFourTest` wants more than 40 plain USE_BLOCK steps; the replaced trails held three, so a grindstone and a lectern are used here.
- A colour adjective (빨간, 파란, 노란, 검은) appears only where the coloured object is an anchor or a held item of another concept, never where the step teaches that object (`teach: bed` still says 침대 for a blue bed). White wool and white beds are named 양털 and 침대, so they carry no colour.
- A carpet as an anchor trips lint rule KO011 (`양탄자 옆에 … 놓다`), so wool is used as the anchor instead.
- HOLD_WHILE with LOOK_AT_ENTITY reads `들고 있는 동안 … 일 초 동안`, a doubled 동안; the inner goals here are INTERACT_ENTITY, DEFEAT_ENTITY, BREAK_BLOCK and TRADE instead.
- An EUREO step needs a biome word that is already known, or the sentence has two unknown words (`eroded_badlands` plus a new sand); the plains are used.
