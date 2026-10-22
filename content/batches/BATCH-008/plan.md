# BATCH-008 plan: fire and iron, the smithy (first level-2 batch)

Brief: `content/briefs/BATCH-008.md` (on top of BATCH-001, BATCH-004, BATCH-005 and `L2-PLAN.md`). Fifteen level-2 trails; ids,
`order`, `requires`, `koreanLevel` and step ids unchanged (s1 to s3, s4 where a trail has a fourth step, and finale). Built on
phase0 after generator wave 5. Titles are kept except `schmelzpfad` (화로의 길, the old 용광로의 길 was about the blast furnace of
`schmelzofen`), `schmiedepfad` (English title now "The Smithy Path"). The four `adv_story_*` titles are kept.

## Baseline and result

See `manifest.json` ("metrics") for every number. Highlights: no duplicate signature added (12 left, none of them mine),
no pair above three, `unknownOverOneShare` 0.053881 to 0.053674, level-2 bare share 0.6446 to 0.5316, level-2 top goal
type share 0.3620 to 0.3224, 21 of the 64 steps are form-dependent (32.8%).

## Level-2 design

- Every trail is a small smithy project: coal and a furnace, a blast furnace, a stone hearth, the four vanilla iron
  advancements, the smithing table, coal, a quarry, bricks, the stonemason, tools, the deep veins and the forge fire.
- The deciding forms are relations (옆/앞/뒤/안/위/아래/사이 + 에), 와/과 + 사이에, and 에게 (iron to the golem, iron to the
  villager). Each trail declares its grammar in the manifest (the schema has no `grammar` field).
- 에서 against 에: `schmelzpfad` places the furnace "제작대 옆에" and bakes logs "화로 옆에서"; `tiefenpfad` says "땅속에서 화로
  옆에 제작대를 놓으세요".
- -고 (들고): `schmelzofen`, `adv_story_upgrade_tools`, `kohlenpfad`, `werkzeugpfad`, `tiefenpfad`, `adv_story_obtain_armor`.
- (으)로 for material: the build steps (돌로 담을 쌓으세요) and `adv_story_upgrade_tools` (조약돌로 돌 도끼를 만드세요).
- 십 초 (Sino-Korean seconds): `adv_story_smelt_iron` s3 (authored).
- Flavour adjectives sit in the finale lines (뜨겁다, 차갑다, 무겁다) and 입다 in `adv_story_obtain_armor`; an adjective sentence
  next to a new noun in a clue would make two unknown words.

## Constraints checked

- 64 steps, none bare; top goal type PLACE_REL with 18.8%; no SEQUENCE or AVOID (level 3 and up); overworld only, no Nether,
  End or structure (the golem and the villager need a village).
- No step repeats the signature of another step anywhere on the learner path; no (verb, object) pair above three; the level-2
  top verb 놓다 stays under its ratchet (three placements use 달다 and 깔다).
- Authored Korean: schmelzpfad finale text; steinherz s2; smelt_iron s1, s3 and the finale text; iron_tools s2 and the finale
  text; armor finale clue and finale text; upgrade_tools, schmiedepfad, kohlenpfad, werkzeugpfad, schmiedefeuer and schmelzofen
  finale texts; schmiedefeuer finale clue.
