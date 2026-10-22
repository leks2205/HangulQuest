# BATCH-028 plan: the last journeys and the colour chain (levels 5 and 6)

Brief: `content/briefs/BATCH-028.md` on top of `L456-PLAN.md`, BATCH-016 to BATCH-018 and BATCH-024 to BATCH-027. Fifteen trails; ids, `order`, `tier`, `requires`, titles and topics are kept. Every trail has the level its tree depth gives: `w10_08` is level 5 (depth 15), the other fourteen are level 6 (depth 16 to 19). No level changes. Every clue is generated; only the fifteen finale lines are authored (a generated ceremony line plus one timeless fact, all in `korean-lines.tsv`). Each trail has four steps, 60 in all.

| id | level | project |
|---|---|---|
| w10_08 | 5 | A lantern by day, gravel dug while holding it (-는 동안), a pillar of cut sandstone, a jack o'lantern on top at night. |
| kronenfeier | 6 | Cookies by day, cookies in a barrel in the rain, berries at dusk, a cake at night. |
| letzte_reise | 6 | An eye of ender at night, the End with it in hand, an end crystal shot with a bow, the dragon egg placed. |
| weltenschoepfer | 6 | Dirt dug in the windswept hills (-(으)러 가다), a quartz pyramid in clear weather, no breaking it in the rain (-아/어도), a sea lantern on top. |
| kronenklang | 6 | The jagged peaks by day, a jukebox at night, a parrot in clear weather, a bell heard at sunrise. |
| weltwaechter | 6 | A shield at night, two witches defeated with an iron sword (-는 동안), an iron golem at night, height 100 with the shield (flavour: -(으)ㄹ수록). |
| ewige_krone | 6 | Gold smelted at dusk, a piglin watched with a gold ingot, a gold block in clear weather, the golden helmet put on at night. |
| schicksalspfad | 6 | A map bought by day, thirty blocks west with the map in clear weather, the cherry grove in the rain, a bed at night. |
| weltenrichter | 6 | A copper block by day, waiting in thunder (-는 동안), no breaking of a lightning rod in the rain (-아/어도), the rod watched at night. |
| unsterblichkeit | 6 | Golden apples by day, one eaten in the rain, an evoker defeated with an iron sword (-는 동안), a totem of undying at night. |
| rotwerk | 6 | Red dye, bricks beside red wool, a red banner, a red carpet at dusk. |
| blauwerk | 6 | Blue dye, ice beside blue wool (the adjective 파란), a blue banner, a blue bed at night. |
| gelbwerk | 6 | Yellow dye with a dandelion, honey beside yellow wool, a yellow banner, yellow glass in clear weather. |
| purpurwerk | 6 | Purple dye with blue dye (파란 염료), a purpur block beside purple wool, a purple banner hung at dusk. |
| orangewerk | 6 | Orange dye with yellow dye (노란 염료), copper beside orange wool, an orange banner hung at dawn. |

Constructions: -(으)면 on time and weather in 40 clues, -는 동안 (HOLD_WHILE x3, WAIT x1), -아/어도 on AVOID (x2), -(으)러 가다 x1, relations (옆에, 위에) in 8 steps, colour adjectives (파란, 노란) and the colour noun in every colour trail. Level 6 flavour: 높이 올라갈수록 추워져요 in the `weltwaechter` finale. 49 of 60 steps are form-dependent (81.7%); level 5: 3 of 4, level 6: 46 of 56.

Colour chain: dye, wool, banner and glass, bed or carpet in one colour per trail, in the order rot, blau, gelb, purpur, orange (the links to `regenbogenfahne` are unchanged). The mixing is told, not checked: purple and orange dye are crafted with blue and yellow dye, and the finale says the colour comes from mixing.

Rules this batch settled:
- **Pairs are nearly used up.** Most (verb, object) pairs of craft, place and eat sit at three already. The trails therefore use fresh objects: cookies, a barrel, a jukebox, a parrot, a sea lantern on quartz, a piglin, ice and honey beside wool. An ACQUIRE of a craftable item speaks 만들다 and counts as the CRAFT pair, so it is no way round.
- **Hints and colour compounds.** A hint prints the exact coloured name. A compound like 빨간색 양털 has to be met three times in clues, or stand in the clue of the same step. The step therefore teaches the wool first (`gen_red_wool`, `gen_yellow_wool`), which keeps the noun in clue and hint; blue wool is known already and keeps the adjective.
- **`red_dye` is named 염료** in a held or `with` position (the generic dye concept owns the item). Purple and orange dye are crafted with the other dye only (`with`), which names blue and yellow correctly.
- New concepts (insert only): `gen_red_dye`, `gen_yellow_stained_glass` (with lexicon tags and EN/DE glosses).
- `letzte_reise` ends on placing the egg only; the finale does not claim the End.

Slack: `unknownOverOneShare` 0.050286 to 0.050085 (every sentence of the batch has at most one unknown word), `hintFullyKnownShare` rises (no hint of the batch has a missing word), no new duplicate signature, no pair over three. Many pairs sit at exactly three now (CRAFT copper block, PLACE sea lantern, WATCH iron golem, WEAR_HEAD golden helmet, EAT cake).

Depth: every trail is already at the level its depth gives; none is rebuilt at another level.

Notes for the integrator:
- `gen_red_dye` could not name the dye in a held position (the `dye` concept lists red dye as its item); a generator fix would let the mixing step say 빨간 염료 as well.
- A coloured anchor of the same colour as the placed block drops both colours in the clue (blue glass beside blue wool read 양털 옆에 색유리); the trails avoid that pairing.
- The pronunciation review file gained two rows for the new concepts.
