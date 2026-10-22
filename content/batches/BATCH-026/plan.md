# BATCH-026 plan: the crown's workshop (levels 4 and 5)

Brief: `content/briefs/BATCH-026.md` on top of `L456-PLAN.md`, BATCH-016 to BATCH-018, BATCH-024 and BATCH-025. Thirteen trails; ids, `order`, `tier`, `requires` and titles are kept, and so is the topic. Every trail has the level its tree depth gives: `w8_07` and `w8_08` are level 4 (depth 12), the other eleven are level 5 (depth 13 to 15). No level changes. Every clue is generated; only the thirteen finale lines are authored (a generated ceremony line plus one timeless fact, all in `korean-lines.tsv`).

| id | level | project |
|---|---|---|
| w8_07 | 4 | Shears by day, a carved pumpkin, a jack o'lantern with a torch, an enderman watched at night. |
| w8_08 | 4 | Glowstone carried above height 150 at dusk, glowstone placed at night, a door hung facing east, twelve blocks east at dawn. |
| kronenwacht | 5 | A barrel beside a blue bed at night, a skeleton watched while holding a shield (-는 동안), no villager defeated even at night (-아/어도), a witch defeated in the rain. |
| kronenpfad | 5 | Gold ore mined in the plains (-(으)러 가다), an emerald block in the rain, a pillar of two gold blocks, the emerald block on top by day. |
| kronenglanz | 5 | Copper blocks, a copper pillar, glowstone on top, copper bulbs at night, waiting near the glowstone while it is night (-는 동안). |
| zeitportal | 5 | A daylight detector, a clock held while walking east (-는 동안), the detector beside an iron door by day, waiting near it while the sun is up. |
| schattenpfad | 5 | A bat watched, no bat defeated even at night, a soul torch beside a blue bed, the deep dark, a sculk sensor. |
| handwerksmeister | 5 | A stonecutter in the rain, a grindstone, an anvil beside the grindstone, the stonecutter beside the anvil. |
| seelenpfad | 5 | A soul lantern at night, soul sand watched while holding it, the soul sand valley, a soul campfire with the lantern in hand. |
| golemwerkstatt | 5 | Copper ore mined then a copper block crafted (-아/어서), a copper chest, green glass beside the block, a snow golem watched by day. |
| kronenrat | 5 | A lectern, a bookshelf, and brown, gray and purple banners, each beside the lectern. |
| richterpfad | 5 | A trial key, a crossbow shot at a breeze (SHOOT), no lying in bed even at night (-아/어도), the crossbow held while watching the breeze (-는 동안), the breeze defeated at height 0 or below. |
| runenpfad | 5 | Deepslate lapis ore below height 0, an enchanting table, a chiseled bookshelf beside it, lapis lazuli held while watching the table (-는 동안). |

Constructions: -(으)면 on time and weather, -는 동안 (HOLD_WHILE with an inner goal x5, WAIT x2), -아/어도 on AVOID (x3), -(으)러 가다 x1, -아/어서 x1, a colour adjective (파란 침대) x2, SHOOT x1, WALK x1, FACING x1, relations (옆에, 위에) in 12 steps. 36 of 56 steps are form-dependent (64.3%); level 4 5 of 8, level 5 31 of 48.

Rules this batch settled:
- **A sentence may hold one word the learner has met fewer than three times.** The simulated learner needs three encounters; the first idea of each trail had several two-new-word sentences. Rare names are therefore introduced one per sentence (a pillar step first, the placement later) and common anchors are used for the rest (a door, a bed, a lectern).
- **Colour adjectives** (L5) come from anchors named by the registry, not from the taught word: 파란 침대 옆에 통을 놓아야 해요. A step that teaches the coloured item keeps the noun (갈색 현수막), as the generator intends. 검은 is not matched by the coverage lexicon (it reads as 검), so black stays out. 놓다 next to a banner or carpet anchor trips KO011, so the anchors are beds.
- **Hint words.** A hint that prints a coloured vanilla name the learner has not met three times lowers `hintFullyKnownShare`; the bed trails teach `bed`, so the hint says 침대.
- **Pairs and signatures.** No pair is raised over three and no duplicate signature is added. BATCH-027 had already used `WALK east dawn`, so `w8_08` carries glowstone in hand.

Slack: `unknownOverOneShare` falls (every sentence of the batch has at most one unknown word), `hintFullyKnownShare` rises, no new duplicate signature, no pair over three.

Depth: every trail is already at the level its depth gives; none is rebuilt at another level. `kronenwacht` has only four steps (the old trail had three).

Titles of the two level-4 trails and of the eleven level-5 trails are unchanged.
