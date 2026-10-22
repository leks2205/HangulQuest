# BATCH-024 plan: into the End and the far kingdoms (level 4)

Brief: `content/briefs/BATCH-024.md` on top of `L456-PLAN.md`, BATCH-016 to BATCH-018 and BATCH-023. Fifteen trails; ids, `order`, `tier`, `requires` and the step ids (s1, s2, ..., finale) are kept. Every trail is level 4 by tree depth (10 to 12), so no trail changes its level. Every clue is generated; only the fifteen finale lines are authored (a generated ceremony line plus one timeless fact). Titles stay except `drachenhort`, which now tells the end city.

| id | project |
|---|---|
| ewiges_eis | Frozen peaks, thirty blocks east, a fall onto powder snow in clear weather, a snowball on a zombie by day, a polar bear in the rain. |
| pyramidenpfad | The desert, chiseled sandstone by day, sandstone mined then cut sandstone crafted (-아/어서), a pyramid of cut sandstone. |
| bibliothekpfad | The dark forest by day, a wall of bookshelves in the rain, an enchanting table near bookshelves at night, an enchanted book. |
| leuchtturmpfad | A pillar of white concrete, a lantern on it at night, thirty blocks north at dusk, wait for night by the lantern in clear weather. |
| drachenhort (retitled 엔드 도시의 보물) | End highlands, a shulker, purpur mined, two shulkers defeated, a shulker box crafted. |
| tempelwaechter | The deep ocean in the rain, a guardian underwater, prismarine mined, a prismarine wall, a guardian defeated underwater. |
| sternenkarte | A cartography table crafted in clear weather, thirty blocks north with a compass, a spyglass faced north for ten seconds at night, the table opened with a map in hand. |
| handelsflotte | A boat on the ocean in clear weather, fish sold to a villager, fish in a barrel, a gray banner by day. |
| adv_nether_all_effects | Potions and a golden apple gathered, no milk, then the potion drunk before the apple (ordered). |
| adv_nether_create_full_beacon | Ten iron blocks by day, a pyramid of iron blocks, a beacon placed on an iron block at night, the beacon opened outside. |
| adv_story_enter_the_end | Twelve eyes of ender at night, a silverfish and stone bricks underground, the End. |
| wolkenpfad | Above height 150 in clear weather, a fall onto a slime block, a bow on a target from afar by day, stairs of quartz blocks. |
| unendlichereise | Thirty blocks south by day, flowers picked in a meadow (-(으)러 가다), swimming in the ocean in the rain, thirty-five blocks west at dusk. |
| reisekrone | Sunflowers picked in sunflower plains (-(으)러 가다), turtle scutes, a turtle shell crafted by day and put on. |
| kronenlicht | A bow in hand into the End, no eye contact with endermen, a bow shot at the dragon, the dragon defeated. The dragon fight is the level's last step. |

Rules this batch settled:
- **-(으)러 가다 needs a second spoken condition.** The step must hold `IN_BIOME` for the trip, and the sentence already says that place, so `QuestlineValidationTest.everyConditionAppearsInTheKoreanClue` would find nothing more to speak. Both -(으)러 steps carry a weather or time condition as well (`날씨가 맑으면`, `낮이 되면`).
- **Walks stay under forty blocks.** The playtest harness moves one block a tick for forty ticks. A pillar under a top placement stays at three blocks, since the matcher sees a 7x7x7 cube.
- **No concept for spyglass, milk bucket or the end portal frame.** `IconReferentTest` keeps spyglass and milk bucket free for their vanilla names, and the frame is not obtainable. The frame therefore does not appear; the End trail goes through the stronghold's silverfish and stone bricks.
- **Entity concepts.** An item concept cannot name an entity: the end crystal cannot be shot or defeated without a concept that carries the entity, so the dragon fight shoots and defeats the dragon.

Slack decisions:
- `unknownOverOneShare` falls from 0.0441 to 0.0439: every sentence of the fifteen trails has at most one unknown word (the End words are spread over single-word sentences, and 보다 is met in a FACING sentence only after 북쪽 has been met three times).
- No new duplicate signature (the signatures that equalled another trail's got a condition). Duplicate signatures fall from 3 to 2.
- The only pairs at three are `GO desert`, `GO deep_ocean` and `STACK iron_block`; no pair is over three. Torches, candles, campfires, book crafting, bells and cobblestone are not touched.
- The four new concepts (cut sandstone, slime block, turtle scute, gray banner) are inserted in the middle of `concepts.json` and `lexicon-tags.json`, not at the end, so that BATCH-025 appends cleanly.

Titles of `adv_nether_all_effects` (어쩌다 이 지경까지) and `adv_nether_create_full_beacon` (신호자) are the official advancement titles and are kept as the brief asks, although neither trail can earn its advancement at level 4: all thirteen effects at once and a four-tier beacon belong to level 6 (a pyramid builds at most three wide). Both are rebuilt as level-4 steps toward it, and their finales claim only what the goals check.

Depth: the two trails the brief named as possible level 5 or 6 are level 4 by depth (12), so neither is rebuilt at another level. No trail is proposed for another level.
