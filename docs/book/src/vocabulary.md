# Vocabulary

Every Korean word the mod can teach comes from one of three places, and all three end up in the same
catalog the quest engine draws from.

## 1. Curated concepts

`src/main/resources/assets/hangulquest/data/concepts.json` holds **1,132 concept entries**: 293 hand-written ones — blocks, tools, food,
animals, biomes, verbs and a few abstract words. Each one looks like this:

```json
{
  "id": "stone",
  "ko": "돌",
  "category": "BLOCKS",
  "tier": 1,
  "items": ["minecraft:stone"],
  "sentenceKo": "돌을 캐요.",
  "descKo": "땅에서 나와요. 단단해요.",
  "aliases": ["돌"]
}
```

| Field | Meaning |
|---|---|
| `id` | stable id used by questlines (`"teach": ["stone"]`) |
| `ko` | the word in Korean. Its English and German gloss is not in this file but in `lang/content/en_us.json` and `de_de.json` (`hangulquest.word.<id>.gloss`); an `en` or `de` field here would still win, for a resource pack that replaces the file |
| `category` | `BLOCKS`, `MATERIALS`, `TOOLS`, `FOOD`, `ANIMALS`, `COMBAT`, `BUILDING`, `ENVIRONMENT`, `ACTIONS`, `MISC` — used to pick a sensible goal |
| `tier` | how early the word belongs in the tree |
| `items` | the game items the concept maps to, so the engine can check goals |
| `entity` / `biome` | present when the concept is a mob or a biome (`minecraft:cow`, `minecraft:plains`) |
| `sentenceKo` | a model sentence the mod can fall back on; its translation is the key `hangulquest.word.<id>.sentence` |
| `descKo` | one line in easy Korean used by hints; must be Hangul-only |
| `aliases` | other spellings that should resolve to this concept |

Curated concepts are the only words with a hand-written description, and they are what the early tree
is built from.

## 2. Vanilla names

The game already ships Korean and German names for every item, block, entity and biome. `names.json`
is generated from those two language files with `tools/extract_names.py` — names only, no assets. It
gives the mod thousands of words it can use in quests without a human writing them down.

## 3. Your Lute vocabulary

If you already learn Korean with [Lute](https://github.com/LuteOrg/lute-v3), export your terms and drop
the file at:

```
config/hangulquest/vocab/lute-vocab.csv
```

The format is Lute's Anki-ready export — five semicolon-separated fields, quoted fields allowed:

```
term;translation;pronunciation;status;tags
돌;Stein;dol;99;
나무;Baum;namu;4;nature
```

| Status | Meaning in Hangul Quest |
|---|---|
| `1`–`5` | learning — the word counts as due-soon and can trigger a hint sidequest |
| `99` | known — hints for this word never start a sidequest |
| `0` or empty | ignored |

Rules:

- The file is **read-only**. The mod never writes to it, never moves it and never talks to Lute.
- Status is re-read at start-up; if you change your status in Lute, export again and restart.
- Words that match a curated concept reuse it, so `돌` does not appear twice.
- Unmapped words get the `lute:` prefix in the progress file and appear in the dictionary as normal.
- The import is off if the file is missing or if `luteImport` is disabled in the config.

## How words become quests

A step declares the words it teaches: `"teach": ["stone", "torch"]`. The trail itself lists its
`concepts`, which is what the journal shows under it. A trail unlocks when you have **discovered**
`minWordsKnown` words — discovered, not mastered, deliberately: a vocabulary gate that requires mastery
would stall a player who is happy with reading.

The engine will not accept a step whose goal mentions an object that no taught concept covers. That is
what keeps the quest text and the check in sync.

## Adding words

1. Add the concept to `concepts.json` with a Hangul-only `descKo`.
2. Use it in a questline's `teach` and `concepts`.
3. Run `./gradlew test` — the content test checks that every taught concept exists and that every
   Korean word is Hangul-only.

Next: [Review, streak and achievements](review.md) explains what happens to a word after you have met it.
