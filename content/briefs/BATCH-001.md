# Content brief BATCH-001: the first steps, rebuilt

**Chapter:** 첫걸음 — home, fire and light, food (level 1)
**Trails (ids are kept, so saved progress stays valid):** `steinpfad`, `holzpfad`, `essenspfad`, `feuerpfad`,
`tierfreund`, `brotpfad`, `tierpfad`, `haustierpfad`, `hoehlenlicht`, `kraeuterpfad`, `wollpfad`, `flusslicht`,
`waldlicht`, `feuerstelle`, `nachtpfad` (15 trails, the content-guard limit).

## Pedagogical intent

These are the first trails a new player meets. They decide whether the mod feels like a game or a word list.
The trails today ask for one action on one object, three times ("break stone, place stone, break stone"), and
the most basic words (나무, 물, 집, 불, 해, 위, 옆, 안) barely appear. Rebuild them so that:

- **the same core words come back again and again, in different roles and tasks** (the owner's rule: repeat
  the word, never the task). 나무 is cut, then planked, then a torch goes 나무 옆에, then a house of 나무 gets a door;
  물 is reached, carried in a bucket, poured, and a fire is lit 물 옆에.
- every trail is a tiny project with a reason (build a shelter before night, light the way into a cave, feed the
  animals, bake the first bread), and the finale closes that project;
- the grammar is level 1 only, and it is *in* the task: location nouns + 에 decide where things go, native numbers
  + counters decide how many (한 개, 두 마리), 을/를 and 이/가 are everywhere.

## Target words

Use and recycle these, first-come in this batch (concept ids in `concepts.json`; add a concept only if a word is
missing and has an official Minecraft anchor in `names.json`):

- **Core, now 0–3 encounters:** 나무, 해, 물, 불, 돌, 집, 문, 길, 빵, 사과, 감자, 달걀, 우유, 상자, 침대, 초, 소, 닭, 고양이,
  강, 달, 유리, 시계, 종이, 책.
- **Location nouns (new, level 1):** 위, 아래, 옆, 안, 밖 — through `PLACE_REL` / `PLACE_ON_PATTERN` and the
  `PLAYER_REL` condition.
- **Verbs by doing them (enactment first):** 캐다, 베다, 놓다, 쌓다, 만들다, 먹다, 마시다, 주다, 넣다, 들다, 걷다,
  올라가다, 들어가다.
- **At most 4 new words per trail, at most 1 new word per semantic set per trail** (`semSet` in
  `lexicon-tags.json`): do not teach 사과, 감자 and 달걀 in the same trail.

Every core word above should reach **at least 4 encounters in at least 2 different roles inside this batch**
(object, anchor, material, recipient, place of activity, held item). `tools/coverage.py` measures it.

## Mechanics (novelty budget)

- Every trail uses **at least one non-bare step**: a relation (`PLACE_REL`, `PLACE_ON_PATTERN`), a small build
  (`BUILD_PATTERN`: PILLAR, ROW, RING, PYRAMID), `CRAFT`, `GIVE`, `PUT_IN`, `HOLD_WHILE`, or a condition
  (`TIME`, `IN_BIOME`, `PLAYER_REL`, `HOLDING`, `SKY`, `IN_WATER`).
- **Bare steps (plain break/place/acquire without relation or condition) at most 30% of the batch.**
- No goal type above 25% of the batch's steps; within a trail every step has a different goal type or relation.
- `SEQUENCE` and `AVOID` are level 3+ (the validator enforces it) — do not use them here.
- Every step is reachable in a fresh overworld on day one (no Nether, End, deep dark, structures or rare biomes).
- 3–4 steps plus a finale per trail. Keep the tree shape (`requires.completed`) unless a prerequisite no longer
  makes sense; never create cycles; keep `order` values.

## Text rules

- Prefer **generated Korean** (no `clue.ko`/`hint.ko`): the sentence builder gets particles, counters, verbs and
  relations right and the review covers its rules. Write `clue.ko` only where the generated sentence cannot say
  the project's reason, and then keep it level 1 and lint-clean.
- Finale text (`finale.textKo`) is a short past-tense line in level-1 grammar that closes the project, e.g.
  나무 집에 문을 달았어요. (Not -기 전에 / -고 나서: those are level 3.)
- English and German clue/hint/title/finale text go into `assets/hangulquest/lang/content/en_us.json` and
  `de_de.json` under the existing keys (`hangulquest.trail.<id>.…`). For relation and pattern steps the English
  must not give away the location word ("Place a torch — read the Korean to see where.").
- Every taught word needs a real `descKo` in easy Korean (words at or below its level). Fix the concept if it lacks one.
- Use official Korean Minecraft names (`names.json`).

## Gates (all must pass before the batch is handed back)

`./gradlew test --offline`, the Korean linter, the three review-registry steps, `tools/i18n/extract_content.py
--check`, `tools/validate_schema.py`, `tools/quest_tree.py`, `tools/variety.py --check` and `tools/coverage.py
--check` (no metric may get worse; when they improve, run `python3 tools/variety.py --update-baseline` and commit
the new baseline). `PlaytestTest` must drive every trail to the end. Write
`content/batches/BATCH-001/manifest.json` (trails, new words, recycled words with encounter counts before → after,
mechanics used) and `content/batches/BATCH-001/korean-lines.tsv` (every Korean line of the batch, generated and
authored, `id<TAB>ko<TAB>context`) for the blind linguist review that follows.
