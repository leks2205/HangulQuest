# BATCH-030 plan: the last small poems (level 6)

Brief: `content/briefs/BATCH-030.md` on top of `L456-PLAN.md`. Eleven trails (w11_06 to w11_08, w12_01 to w12_08); ids, `order`, `requires`, `tier`, `topic` and titles are kept. Every trail is level 6 by tree depth, so none changes level. Clues and hints are generated; the eleven finale lines and one hint (w11_07 s2) are authored and listed in `korean-lines.tsv`. Ten trails have four steps, `w12_08` (the last trail of the course) has five. 45 steps, 27 form-dependent (60%). `concepts.json` is untouched. EN and DE texts of all steps are rewritten in the lang files (translations adopted as proofread).

| id | what it checks | finale claims |
|---|---|---|
| w11_06 | obsidian at night, a look at the dragon with a bow, no looking at an Enderman in thunder, a dragon head placed | the head placed; heads are on End-city ships |
| w11_07 | gold smelted in rain, a gold block at night, a soul lantern crafted, a totem at dawn | the totem at sunrise; evokers drop it |
| w11_08 | a note block at night, a song heard near a jukebox in rain, no breaking the note block in thunder, the jukebox played | the jukebox played |
| w12_01 | a compass walk north by day, a map bought, the cartography table at night, height 90 | the climb; it gets colder higher up |
| w12_02 | a carved pumpkin at night, waiting east until day, a bell heard, soul torches | torches placed; the soul flame is blue |
| w12_03 | a cracked-brick pyramid, a chiseled brick on top, a lectern in rain, a beacon at night | the beacon seen at night |
| w12_04 | a fire kindled with flint and steel, netherrack with a pickaxe, a ghast hit with a bow, magma blocks | the stack of magma |
| w12_05 | soul sand in thunder, a soul lantern in rain, an Enderman at night with a sword, an ender chest seen with an eye of ender | the look at the chest |
| w12_06 | seven pearls at dusk, a look at the dragon holding a pearl, a nether star, a dragon egg placed in the End | the egg placed in the End |
| w12_07 | iron blocks by day, an anvil crafted, the anvil used in thunder, flint | flint collected; it comes from gravel |
| w12_08 | grass by day, an iron golem at dawn, a diamond helmet, height 100 with a compass, a wait for day | the wait for day; the night passes and the sun rises again (-기 마련이다) |

Slack decisions
- Duplicate signatures and (verb, object) pairs: none added; BATCH-028 landed meanwhile, so the steps that collided with it were changed.
- `unknownOverOneShare`: none of the 45 clues has more than one unknown word; the share cannot fall further through these trails (all 88 over-one sentences are earlier in the path), so `w12_08` got a fifth step to lower it by the denominator.
- Recycled words: 듣다 (twice), 노래, 가위, 일곱, 불 (as the target of 불을 피우다, which also keeps 불 at three roles), 찾다-free.
- Metrics: `formDependentShare.6` 0.4875 to 0.6273, `bareShare.6` 0.2438 to 0.0745, `topTypeShare.6` 0.2063 to 0.1491, `objectOnlyRepeats` 17 to 12, `noveltyShare`, `boredomIndex`, `meanUnknown`, `hintFullyKnownShare`, `hintWordCoverage`, `seenShare` better. Baseline not rewritten.

Notes for the integrator
- A hint that equals the clue (-아야 해요) appears when the object name contains an adnominal such as 이끼 낀; the cracked bricks avoid it.
- GIVE a poppy to a golem would print the general word 꽃, so w12_08 uses INTERACT_ENTITY (만지다).
- The end portal frame is not obtainable, so w12_05 ends at the ender chest.
- `ConstructionGateTest` reads an authored -(으)면 text in `w11_07/s2`; it is kept as an authored hint.
- `ContentTextTest` needs one hint without German text; `w12_08` s4 keeps its English fallback.
