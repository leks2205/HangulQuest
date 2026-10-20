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

The script writes the file and refreshes `index.json`. The build regenerates the index during
`processResources` anyway, so `index.json` is never maintained by hand.

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
„엔더에서"), `NEAR_ENTITY`/`NEAR_BLOCK` („좀비 근처에서"). A step with conditions uses the generated
sentence instead of its authored clue, so no condition can be missing from the text. `REACH_HEIGHT`
below the ground uses „내려가다" (descend) instead of climbing.

## Anatomy of a file

```json
{
  "schema": 1,
  "id": "steinpfad",
  "titleKo": "돌의 길",
  "titleDe": "Der Steinpfad",
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
      "clue": { "ko": "단단하고 회색인 것을 세 개 캐요.", "de": "Baue drei harte, graue Dinge ab." },
      "hint": { "ko": "돌을 캐요. 세 개요.", "de": "Baue Stein ab, drei Stück." }
    }
  ],
  "finale": {
    "step": {
      "id": "finale",
      "teach": ["torch"],
      "goal": { "type": "PLACE_BLOCK", "block": "minecraft:torch", "count": 1 },
      "conditions": [{ "type": "TIME", "phase": "NIGHT" }]
    },
    "textKo": "어두운 곳에서 빛을 만들었어요.",
    "textDe": "Du hast im Dunkeln Licht gemacht."
  },
  "reward": { "badge": "steinpfad", "words": ["stone", "torch"] }
}
```

- `clue` and `hint` are optional. Without them the Korean sentence is generated from the goal, with
  correct particles, counters and time prefixes. Authored sentences are for riddle flavour.
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
| `BREAK_BLOCK` | `block`, `count`, optional `verb` (`MINE`/`CHOP`/`HARVEST`) | the player breaking that block |
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

## Conditions (gates)

`TIME` (`DAY`/`NIGHT`/`DAWN`/`DUSK`), `WEATHER` (`CLEAR`/`RAIN`/`THUNDER`), `Y_RANGE`, `NEAR_ENTITY`,
`NEAR_BLOCK`, `IN_DIMENSION`, `IN_BIOME`. A goal only counts while every condition holds.

## Rules

- Korean fields (`clue.ko`, `hint.ko`, `textKo`, `titleKo`, `topic`) are **Hangul only** — digits and
  punctuation are fine. The test fails otherwise.
- German text is a translation, not the quest: the player sees Korean first.
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
```

`QuestlineValidationTest` loads every file, checks references, ids, Hangul and the finale, and fails on
dead ends, missing parents, wrong Korean levels, unspoken conditions and clues that do not name the
action. At runtime the loader skips a broken step with a log warning instead of crashing the game.

The full attribute list — including what the journal must show for each state — is in the
[attribute reference](questline-attributes.md).
