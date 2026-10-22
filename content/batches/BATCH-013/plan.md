# BATCH-013 plan: animals and pets (level 2)

Brief: `content/briefs/BATCH-013.md` on top of BATCH-008. Fifteen level-2 trails; ids, `order`, `requires`, `koreanLevel` and the old step ids
(s1 to s3 and finale, s1, s2 and finale for the advancements) unchanged, new steps added after them. Titles are kept except for five trails whose
advancement is out of reach at level 2. Built on phase0 at the brief (28af90a) and merged with phase0 (526df82) before the final gates.
The grammar list of every trail is in the trail file and in `manifest.json`.

## Result

| metric | start (28af90a) | final |
|---|---|---|
| unknownOverOneShare | 0.052206 | 0.051534 |
| formDependentShare.2 | 0.272366 | 0.328244 |
| bareShare.2 | 0.242545 | 0.175573 |
| topTypeShare.2 | 0.184891 | 0.162214 |
| distinctTypes.2 | 28 | 30 |
| noveltyShare | 0.597619 | 0.611905 |
| objectOnlyRepeats | 105 | 101 |
| duplicateSignatures | 8 | 6 (none of mine) |
| verbObjectMax, verbObjectPairsOver3 | 6, 5 | 6, 5 (none of mine) |
| hintWordCoverage | 0.96432 | 0.967018 |
| meanUnknown | 0.88005 | 0.861963 |
| seenShare | 0.726415 | 0.740566 |
| this batch: form-dependent steps | 0 of 49 old | 35 of 70 (50.0%) |
| this batch: bare steps | | 0 of 70 |

No new sentence has two unknown words. Every trail teaches at most four new words, at most one per semantic set.
The old trails taught Nether and rare items by name (a crimson shelf, a warped nylium); gestuet leaves the reachability debt.

## Trails

| id | project | grammar |
|---|---|---|
| pferderennen | Fence a paddock near two horses, hang a fence gate and a banner in front of a horse, give it sugar by day, then feed it an apple and ride it. | L2.seyo, L2.ege, L2.go_sequence, L2.eun_neun_topic |
| reitpfad | A donkey's riding trail: look at a donkey, give it hay, put a sign in front of it and a fence gate beside it, open the gate and ride out. | L2.seyo, L2.ege, L2.go_sequence, L2.eun_neun_contrast |
| pferdehof | A horse farm: a plank ring wall near three horses, a door, a cauldron as trough beside a horse, and an apple for a horse at the cauldron. | L2.seyo, L2.ege, L2.euro_material |
| gestuet | A stable: a plank ring wall with a rug inside, a sign in front of a horse, and the horse's name written on the sign. | L2.seyo, L2.euro_material, L2.eseo_activity, L2.eun_neun_topic |
| adv_husbandry_bred_all_animals | A pen near three cows with a gate, a sheep raised with wheat, a sign in front of the sheep, a chicken and a cow raised. | L2.seyo, L2.go_held, L2.eun_neun_topic |
| adv_husbandry_complete_catalogue | Befriend a village cat: pet it near a villager, put a bed beside it and a rug in front of it, give it a fish and pet it. | L2.seyo, L2.ege, L2.go_sequence, L2.eun_neun_topic |
| adv_husbandry_whole_pack | A wolf pack: look at a wolf in the forest, a bone by day, a rug beside the wolf, raise a wolf with beef, wait ten seconds near three wolves at night. | L2.seyo, L2.ege, L2.go_held, L2.num_sino, L2.eun_neun_topic |
| adv_husbandry_remove_wolf_armor | Craft shears, give a wolf a bone at night, put wolf armor on it, then pet it with the shears in hand (armor off). | L2.seyo, L2.ege, L2.go_held, L2.eun_neun_topic |
| adv_husbandry_repair_wolf_armor | Craft wolf armor, put it on a wolf, repair it with an armadillo scute, pet the wolf. | L2.seyo, L2.ege, L2.eseo_activity |
| adv_husbandry_leash_all_frog_variants | At the swamp: craft leads, a fence post by the water, a slimeball for a frog, wait ten seconds near three frogs at night. | L2.seyo, L2.ege, L2.eseo_activity, L2.num_sino |
| adv_husbandry_froglights (A Night at the Frog Pond) | A night at a mangrove-swamp pond: a lime candle beside the water, a small plank dock with a rug on top, and a frog fed near the candle. | L2.seyo, L2.ege |
| adv_husbandry_kill_axolotl_target (Shearing Day) | Wool with shears in hand, a white banner crafted from it and hung in front of a sheep, a sheep fed near the banner. | L2.seyo, L2.ege, L2.go_held, L2.eseo_activity |
| adv_husbandry_allay_deliver_cake_to_note_block (A Song for the Animals) | Craft a note block near a cow, put it beside a cow, listen, a rug in front of a sheep, listen again near two sheep. | L2.seyo, L2.eseo_activity |
| adv_husbandry_feed_snifflet (The Pig Pen) | A plank ring wall near three pigs, a door, mud beside a pig, and a pig raised with a carrot. | L2.seyo, L2.euro_material, L2.go_held, L2.eun_neun_topic |
| adv_husbandry_plant_any_sniffer_seed (The Rabbit Patch) | Feed a rabbit in a meadow, a fence pen with a tulip and carrots inside, raise a rabbit, wait ten seconds near two rabbits. | L2.seyo, L2.ege, L2.go_held, L2.eseo_activity, L2.num_sino |

## Wave 8 features

- `NEAR_ENTITY` with a count: 소 세 마리, 말 두 마리 and 세 마리, 돼지 세 마리, 늑대 세 마리, 개구리 세 마리, 양 두 마리, 토끼 두 마리 (eight steps).
- Creature anchors: 말, 당나귀, 고양이, 늑대, 소, 양, 돼지 (앞, 옆; thirteen of the fifteen `PLACE_REL` steps, the other two are 물 옆에).
- `BREED` (sheep, cow, chicken, pig, wolf, rabbit), `MOUNT` (horse and donkey, both inside an ordered `SEQUENCE`), `GIVE` (the particle 에게 decides who gets it).
- Wolf armour is not `EQUIP` (that is the player's armour): it is `GIVE` with `USE_WITH`.

## Reachability

All steps are overworld. No Nether, End, structure or monument. The five advancements that needed things beyond level 2 (sniffer eggs,
a magma cube, an axolotl fight, an allay) are rebuilt as day-week animal trails with new titles; none is proposed for level 3.
Scarce in the first week: armadillo scutes for the two wolf-armour trails (savanna or badlands, a brush), a slimeball for the frog trail
(swamp slimes at night) and a mangrove swamp for the frog pond (see openIssues in the manifest).
