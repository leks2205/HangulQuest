# Questlines — format and workflow

A questline is a small Schnitzeljagd: several clues that build on each other, then a ritual at the end.
All of it lives in one JSON file under `src/main/resources/assets/hangulquest/data/questlines/`.

Before you write one, read the [attribute reference](questline-attributes.md). It is mandatory: it
lists what every trail must have, and the tests enforce the mechanical half of it.

## Quick start

```bash
python3 tools/new_questline.py steinpfad2 --words stone,cobblestone --tier 1 \
  --title-ko "돌의 두 번째 길" --title-de "Der zweite Steinpfad"
./gradlew test
```

The script writes the file and moves its English and German text into the lang files (see below). The
mod finds questline files by listing the folder, so there is no index to maintain.

## Where the English and German text lives

A shipped questline file holds the Korean text and the structure, and nothing else. The English and
German text (titles, clues, hints, the finale line) is in two flat files, one per language, so that a
translator, Weblate or Crowdin only ever touches one file:

```
src/main/resources/assets/hangulquest/lang/content/en_us.json   English, the reference
src/main/resources/assets/hangulquest/lang/content/de_de.json   German
```

The keys are built from the ids in the questline file:

| Key | Text |
|---|---|
| `hangulquest.trail.<id>.title` | the trail's title |
| `hangulquest.trail.<id>.step.<stepId>.clue` | the translation of a step's clue |
| `hangulquest.trail.<id>.step.<stepId>.hint` | the translation of a step's easy-Korean hint |
| `hangulquest.trail.<id>.finale` | the line shown when the trail is finished |
| `hangulquest.word.<conceptId>.gloss` | a word's gloss (`concepts.json` has no English or German left) |
| `hangulquest.word.<conceptId>.sentence` | the translation of the word's example sentence |

The finale step is the step id `finale`: `hangulquest.trail.<id>.step.finale.clue`. A language file
may lack any key; the game then shows the English text, never another language. English is complete
(`ContentTextTest` fails otherwise), German is reported and may have gaps. Arguments, if a text ever
needs one, are `%s` and must match English. A new language is a new `lang/content/<code>.json` next
to the interface file `lang/<code>.json`.

**Adding or changing text.** Write English and German inline in the trail file while you work, then run

```bash
python3 tools/i18n/extract_content.py
```

It moves every inline `titleEn`/`titleDe`, `clue.en`/`de`, `hint.en`/`de`, `finale.textEn`/`textDe` and
every concept gloss into the lang files and removes the fields from the JSON (nothing else in the file
changes). `python3 tools/i18n/extract_content.py --check` fails while shipped files still carry inline
text; CI runs it. To change an existing line, edit the lang file directly.

**Files of your own.** Questline files in `config/hangulquest/questlines/` and in resource packs may
keep using the inline fields, and they should: they have no lang file. An inline field always wins over
a key. A field with inline text (say `clue.en`) hides the keys for that field in every language, so
a rewritten English clue is never shown next to the shipped German one; add `clue.de` as well to
translate it. Without any inline text for a title, clue or hint the key of the same trail and step id is
used, which is what a copy of a shipped file wants.

## The external folder (no restart)

Trails also live as individual JSON files in `config/hangulquest/questlines/`, copied out of the jar on
first start. The mod re-reads that folder every two seconds:

- a new id appears in the journal immediately,
- a changed file replaces the loaded trail,
- the same id as a shipped trail is **ignored** unless the file sets `"override": true`,
- a broken file is logged and skipped, and the shipped version stays active.

The template is `_template.json`.

## Conditions are spoken in the sentence

`conditions` always end up in the Korean text: `TIME` (밤에/낮에/새벽에/저녁에), `WEATHER`, `Y_RANGE`
(„땅 아래에서", „120블록보다 높은 곳에서"), `IN_BIOME` („해변에서"), `IN_DIMENSION` („지옥에서",
„엔더에서"), `NEAR_ENTITY`/`NEAR_BLOCK` („좀비 근처에서"), `SKY` („밖에서" on levels 1 and 2, „하늘이 보이는
곳에서" from level 3). A step without an authored clue uses the generated sentence, which speaks every
condition. An authored clue wins also on a step with conditions: it is shown as written, and a condition
phrase it does not contain is put in front of it, so no condition can be missing from the text. `REACH_HEIGHT`
below the ground uses „내려가다" (descend) instead of climbing.

## Anatomy of a file

```json
{
  "schema": 1,
  "id": "steinpfad",
  "titleKo": "돌의 길",
  "topic": "돌",
  "tier": 1,
  "order": 10,
  "koreanLevel": 1,
  "concepts": ["stone", "cobblestone", "coal", "torch"],
  "requires": { "minWordsKnown": 0, "dimension": "minecraft:overworld",
                "completed": ["holzpfad"] },
  "steps": [
    {
      "id": "s1",
      "teach": ["stone"],
      "goal": { "type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 3 },
      "conditions": [],
      "clue": { "ko": "단단하고 회색인 것을 세 개 캐요." },
      "hint": { "ko": "돌을 캐요. 세 개요." }
    }
  ],
  "finale": {
    "step": {
      "id": "finale",
      "teach": ["torch"],
      "goal": { "type": "PLACE_BLOCK", "block": "minecraft:torch", "count": 1 },
      "conditions": [{ "type": "TIME", "phase": "NIGHT" }]
    },
    "textKo": "어두운 곳에서 빛을 만들었어요."
  },
  "reward": { "badge": "steinpfad", "words": ["stone", "torch"] }
}
```

This is the shipped shape. The matching lang entries are `hangulquest.trail.steinpfad.title`,
`...step.s1.clue`, `...step.s1.hint` and `...finale`. A file of your own adds the translations inline:
`"titleEn"`, `"titleDe"`, `"clue": { "ko": …, "en": …, "de": … }`, the same for `hint`, and `"textEn"` and
`"textDe"` next to `textKo` in the `finale`.

- `clue` and `hint` are optional. Without them the Korean sentence is generated from the goal, with
  correct particles, counters and time prefixes. Authored sentences are for riddle flavour. Their
  English and German translations are lang keys (or inline fields in a file of your own).
- `teach` names the words the step introduces; they must exist in `concepts.json`.
- The `finale` is the last step and is always required.
- `requires.minWordsKnown` compares against **discovered** words, so the chain stays playable.
- `koreanLevel` (1–6) drives the generated grammar and must equal `Topik.levelForDepth(depth)`
  (`depth <= 3 → 1`, `<= 6 → 2`, `<= 9 → 3`, `<= 12 → 4`, `<= 15 → 5`, deeper `→ 6`). Hints always stay
  at level 1.
- `order` sorts the journal and decides which trail starts automatically at world start (lower first;
  continuations sit above their parents).
- `requires.completed` lists parent trail ids: the trail unlocks only when all of them are finished.
  Locked trails say what they are waiting for in the journal.

## Goal types

| Type | Fields | Checked by |
|---|---|---|
| `BREAK_BLOCK` | `block`, `count`, optional `verb` (any verb `verbs.json` uses for breaking: `MINE`, `DIG`, `CHOP`, `SNAP`, `HARVEST`, `PICK`, `PULL`, `SHATTER`, `BREAK`; the table picks one by default); `block` may also be a tag (`#minecraft:flowers`) | the player breaking that block |
| `PLACE_BLOCK` | `block`, `count` | block change after a right-click |
| `ACQUIRE_ITEM` | `item`, `count` | inventory gain since the step started |
| `CONSUME_ITEM` | `item`, `count` | inventory drop after use |
| `LOOK_AT_BLOCK` | `block`, `dwellMs` | looking at it for that long |
| `LOOK_AT_ENTITY` | `entity`, `dwellMs` | looking at it for that long |
| `DEFEAT_ENTITY` | `entity`, `count` | attack, then death within 5 s |
| `REACH_BIOME` | `biome` (id or `#tag`), `dwellMs` | the biome you are standing in |
| `REACH_DIMENSION` | `dimension` | dimension |
| `REACH_HEIGHT` | `minY`, `maxY` | player altitude |
| `USE_BLOCK` | `block` | right-click on that block |
| `INTERACT_ENTITY` | `entity` | interaction with the mob |
| `PLACE_REL` | `block`, `relation` (`ON_TOP_OF`/`UNDER`/`NEXT_TO`/`IN_FRONT_OF`/`BEHIND`/`INSIDE`/`OUTSIDE`/`BETWEEN`/`NEAR`), `anchor`, `count`, optional `anchor2` | a placement of `block` in that relation to `anchor` (a block, family or `#tag`, or `"@s2"`, see below); a right block in the wrong relation is a near miss, never progress |
| `BUILD_PATTERN` | `pattern` (`PILLAR`/`PYRAMID`/`RING`/`ROW`/`WALL`/`CROSS`/`STAIRS`), `material`, `size` | the placement that completes the shape out of `material` (sizes: pillar and row 2–4, pyramid and ring 3, wall 2–4 = 2x2, 3x2, 4x3, cross 3 = a plus of five blocks, stairs 3–4 steps) |
| `PLACE_ON_PATTERN` | `block`, `relation`, `pattern`, `material` | a placement of `block` in that relation to a structure built from `material` |
| `CRAFT` | `item`, `count`, optional `station`, optional `with` (ingredient ids, wording only) | taking the result out of a crafting screen |
| `SMELT` | optional `input` (wording, level 4+), `output`, `count` | taking the result out of a furnace, blast furnace or smoker |
| `GIVE` | `item`, `entity`, optional `method` (`FEED`/`USE_WITH`/`DROP_NEAR`/`TRADE`), `count` | the hand-over facts of the sensor |
| `PUT_IN` | `item`, `container`, `count` | items moved into that container block |
| `HOLD_WHILE` | `item`, `inner` (a goal, or a condition plus `seconds`) | `inner` only counts with the item in a hand; with a condition: walking with it |
| `SEQUENCE` | `parts` (2–3 goals), `ordered`, optional `connector` (`BEFORE`/`AFTER`/`THEN`) | the parts in order; an event before its turn does not count. Trail level 3 or higher |
| `AVOID` | `action` (a goal), `windowSeconds`, optional `during` (a condition) | the window runs out without the action; doing it restarts the window quietly. Trail level 3 or higher |

**Relations and anchors.** The Korean names the relation (`문 옆에`, `기둥 위에`) in every form of the
sentence, and the English and German clue must not give it away. A relation goal is graded: finishing the step
is a right outcome for the location noun (`L1.loc_yeop_e` for `NEXT_TO`, `L2.loc_sai_e` for `BETWEEN`, ...),
and a right block in the wrong relation is a wrong one (at most three per step). `BETWEEN` has one anchor
(`두 횃불 사이에`) or two different ones: `"anchor": "minecraft:chest", "anchor2": "minecraft:furnace"` says
`상자와 화로 사이에` (와/과 by the final sound; the pair is one task whichever way round it is written).
Fluids are anchors too (`minecraft:water` is 물, `minecraft:lava` is 용암: `물 옆에 횃불을 놓아요`). A tag
(`#minecraft:doors`, `logs`, `planks`, `wool`, `fences`, `beds`, `torches`, `chests`, `flowers`, `saplings`,
`leaves`) is spoken with its everyday word (문, 통나무, 판자, 양털, 울타리, 침대, 횃불, 상자, 꽃, 묘목, 나뭇잎): the
curated concept whose items and covers are the largest set inside the tag. The finale line uses the same word
as the clue, and a word the step teaches (나무 for the logs) wins in both.

**The block of an earlier step.** `"anchor": "@s2"` points at the block that finished step `s2` of the same
trail, which must be a `PLACE_BLOCK`, `PLACE_REL` or `PLACE_ON_PATTERN` step before this one. The sentence says
`아까 놓은 횃불 옆에` (the verb of that step as a past -(으)ㄴ, so the trail needs `koreanLevel` 5 or higher).
Only `ON_TOP_OF`, `UNDER`, `NEXT_TO` and `NEAR` fit a single spot. The engine remembers where the block stood
and saves it with the trail (`placed` on the saved quest); if the memory is missing, any block of the kind that
step placed will do.

**Level 3 connectives.** Where a task has two natural parts, a trail of level 3 says both with -고: a `CRAFT`
with `with` ingredients reads `막대기와 석탄을 모으고 횃불을 만드세요`, and a `PLACE_ON_PATTERN` whose structure no
earlier step of the trail built reads `돌로 기둥을 쌓고 그 위에 횃불을 놓으세요`. Other levels and the hint keep the
single clause.

`SEQUENCE` and `AVOID` teach -기 전에 / -(으)ㄴ 후에 / -고 나서 and -지 마세요, so the English and German
clue must not give the order or the "not" away („Do the two things in the order the Korean says").
`SEQUENCE` parts, the `HOLD_WHILE` inner goal and the `AVOID` action are simple goals (no nesting).
Finishing a `GIVE` (에게), an ordered `SEQUENCE` (its connective) or an `AVOID` (-지 마세요) rightly is a right
outcome for that construction; doing an ordered sequence out of order is a wrong one.

## Conditions (gates)

`TIME` (`DAY`/`NIGHT`/`DAWN`/`DUSK`), `WEATHER` (`CLEAR`/`RAIN`/`THUNDER`), `Y_RANGE`, `NEAR_ENTITY`,
`NEAR_BLOCK`, `IN_DIMENSION`, `IN_BIOME`, `HOLDING` („횃불을 들고"), `SKY` (`OPEN`/`ROOFED`),
`IN_WATER` („물속에서"), `PLAYER_REL` (`relation`, `anchor`, optional `kind` = `block`/`item`/`entity`:
„나무 아래에서"; without a `kind` an entity name wins, so `minecraft:chicken` is the animal). A goal only counts
while every condition holds.

A step may teach the word for any role of its goal or conditions (the object, an anchor, the recipient, the
container, the held item, a material). When the first taught word has several referents (물고기 = cod, salmon,
tropical fish) and covers an id of the goal, any of them completes the step; a word with one referent never
widens the goal. The covers of a concept (`concepts.json`) count as much as its items: a step that teaches 묘목
accepts any sapling as its goal, not only the oak one.

## Rules

- Korean fields (`clue.ko`, `hint.ko`, `textKo`, `titleKo`, `topic`) are **Hangul only** — digits and
  punctuation are fine. The test fails otherwise.
- English and German text is a translation, not the quest: the player sees Korean first. Shipped trails
  need an English text for the title, every clue and hint and the finale (`ContentTextTest`); German
  is optional.
- Exactly one goal per step, the finale included.
- Step ids are unique within a trail.
- `concepts` is the list of words shown under the trail in the journal.
- Never require a structure, a chest or a specific coordinate. The ritual finale is the guarantee that
  a trail can always be finished.
- Do not teach a word twice in the same step list unless the repetition is the point.

## Validating

```bash
./gradlew test                      # content validation plus engine tests
python3 tools/validate_schema.py    # shape only, against docs/questline.schema.json
python3 tools/i18n/extract_content.py --check   # no English/German text left inline in shipped files
```

`QuestlineValidationTest` loads every file, checks references, ids, Hangul and the finale, and fails on
dead ends, missing parents, wrong Korean levels, unspoken conditions and clues that do not name the
action. At runtime the loader skips a broken step with a log warning instead of crashing the game.

The full attribute list — including what the journal must show for each state — is in the
[attribute reference](questline-attributes.md).
