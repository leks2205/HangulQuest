# Author spec: villager conversations and requests (3.1 "Villagers")

This is the format every content packet of 3.1 is written in: the introductions, small talk, friendship
milestones and 반말 offers of the eight villager personalities, and the villager requests of the seven regions.
Write the files into a folder of your own (layout below) and run **one command** on it until it prints
`DRAFTS OK`:

```
python3 tools/validate_dialogue_drafts.py <folder>
```

It runs without the game. Every problem is printed as `<file>: <node>: <message>`; the exit status is 0 when the
folder is clean, 1 when something is wrong, 2 when the check could not run. It applies every rule of this page:
the checks the game makes when it loads the files, the checks of the content tests (glosses, coverage, Korean
form, grammar level) and the Korean linter (`tools/lint_korean.py`, rules in `tools/lint/rules.json`).

## Folder layout

```
<folder>/
  dialogue/<id>.json      conversations (intro, smalltalk, milestone, banmal_offer)
  requests/<id>.json      requests
  lang/en_us.json         English gloss of every line, flat {"key": "text"}
  lang/de_de.json         German gloss of every line, same keys
  _lexicon.json           optional: words the lines need that the catalog and the dialogue lexicon lack
```

Only `hangulquest.dialogue.*` and `hangulquest.request.*` keys belong in the two language files. Spanish and
French are made later by the i18n pipeline from the English and German text; do not write them.

## Personalities and regions

| id | gender | Korean label | speaks to the player | may offer 반말 | voice |
|---|---|---|---|---|---|
| LAZY | male | 느긋한 | 해요체, soft (음, -네요 only where the lexicon allows) | yes | v_lazy |
| JOCK | male | 활발한 | 해요체, loud (와!, 좋아요!) | yes | v_jock |
| CRANKY | male | 무뚝뚝한 | 해요체, terse, few exclamations (흠.) | no | v_cranky |
| SMUG | male | 점잖은 | 해요체, flowery compliments | no | v_smug |
| NORMAL | female | 상냥한 | 해요체, warm | no | v_normal |
| PEPPY | female | 발랄한 | 해요체, many exclamations (우와!, 진짜요?) | yes | v_peppy |
| SNOOTY | female | 새침한 | **합쇼체** (-습니다, -ㅂ니까, -십시오) | no | v_snooty |
| SISTERLY | female | 털털한 | 해요체, direct (어이!) | yes | v_sisterly |

Interests and region flavour: see the design tables of the 3.1 plan (food and naps for LAZY, fitness for JOCK,
tools and quiet for CRANKY, flowers and music for SMUG, cooking and books for NORMAL, dyes and parties for PEPPY,
gems and fine blocks for SNOOTY, bravery and caring for SISTERLY; PLAINS wheat and bells, DESERT cactus and
sandstone, SAVANNA acacia and horses, SNOW ice and stew, TAIGA spruce and berries, JUNGLE cocoa and bamboo, SWAMP
mud and frogs). Region ids: `PLAINS`, `DESERT`, `SAVANNA`, `SNOW`, `TAIGA`, `JUNGLE`, `SWAMP`.

Babies have a personality too but speak as children (반말, speaker `child`); they only get conversations written
for babies (`"baby": true`) and never requests or milestones.

## Ids (the naming scheme is checked)

| kind | id | example |
|---|---|---|
| intro for one region | `vt_<personality>_<region>_intro` | `vt_lazy_plains_intro` |
| intro for any region (fallback) | `vt_<personality>_gen_intro` | `vt_snooty_gen_intro` |
| small talk for one region | `vt_<personality>_<region>_<nn>` | `vt_peppy_desert_03` |
| small talk for any region | `vt_<personality>_gen_<nn>` | `vt_peppy_gen_01` |
| milestone at friendship 3, 6, 10 | `vt_<personality>_ms<3\|6\|10>` | `vt_cranky_ms3` |
| 반말 offer | `vt_<personality>_banmal` | `vt_jock_banmal` |
| request | `req_<region>_<nn>` (one of its regions) | `req_plains_01` |

Personality and region are lower case in ids. `<nn>` is two digits. The file name is the id plus `.json`. A drafted
villager conversation names **exactly one personality** (its lines are recorded in that personality's voice only)
and one region or none.

## Conversations

A conversation is a graph of nodes. Each node is one NPC line followed by exactly one of: `next` (the next node),
`choices` (what the player can say, each with its own `next`), `typed` (an answer the player types) or
`"end": true`. Every node must be reachable from `start`, and every node must be able to reach an end.

```json
{
	"schema": 1,
	"id": "vt_peppy_gen_01",
	"level": 1,
	"trigger": {"entity": "minecraft:villager", "baby": false, "kind": "smalltalk", "personalities": ["PEPPY"]},
	"start": "hello",
	"nodes": {
		"hello": {"speaker": "villager", "ko": "우와! 안녕하세요!", "next": "flower"},
		"flower": {
			"speaker": "villager",
			"ko": "저는 꽃이 정말 좋아요!",
			"effects": [{"type": "exposeWords", "words": ["concept:flower"]}],
			"next": "ask"
		},
		"ask": {
			"speaker": "villager",
			"ko": "꽃이 좋아요?",
			"choices": [
				{"ko": "네, 정말 좋아요!", "next": "friends"},
				{"ko": "아니요, 나무가 좋아요.", "next": "tree"}
			]
		},
		"friends": {"speaker": "villager", "ko": "우와, 진짜요? 우리 친구예요!", "ko_banmal": "우와, 진짜? 우리 친구야!", "end": true},
		"tree": {"speaker": "villager", "ko": "나무요? 나무도 좋아요!", "ko_banmal": "나무? 나무도 좋아!", "end": true}
	}
}
```

Trigger fields:

- `entity`: always `minecraft:villager`. `baby`: `false` for adults, `true` for a conversation of children.
- `kind`: `intro`, `smalltalk` (the default), `milestone` or `banmal_offer`. (`request` is reserved: requests are
  their own files.)
- `personalities`: exactly one personality id. `regions`: one region id, or leave it out for any region.
- `friendship`: a milestone's level (3, 6 or 10); a 반말 offer may leave it out (it needs 6 anyway).
- `profession` / `professions`: optional, a vanilla profession id (`minecraft:farmer`, …) when the talk only fits
  that job.
- `level`: the learner level 1 to 6 the conversation is written for; it is only offered to players at that level or
  above. Intros are level 1. Spread small talk: levels 1–2 40 %, 3–4 35 %, 5–6 25 %.

Node fields: `speaker` (`villager`; `child` in baby conversations), `ko` (the line), optional `ko_banmal` (the same
line in 반말, see below), optional `effects`, and one of `next` / `choices` / `typed` / `end`. A choice has `ko`, `next`
and optionally `speechLevel` (`haeyo` default, `banmal`, `hapsyo`), `effects`, and for graded speech-level tasks
`correct` plus the node's `grammar` (see the shipped `child.json` and `elder.json`). Effects: `exposeWords`
(`words`: catalog ids such as `concept:flower`), `exposeGrammar` (`point`: a `grammar.json` id), `startTrail`
(`trail`: a trail id) and, only in a 반말 offer, `acceptBanmal`.

### The four kinds

- **intro**: the first conversation with a villager. 3–5 NPC lines and one player choice. Exactly one node is the
  self-introduction: `{"speaker": "villager", "selfIntro": true, "next": "…"}` with **no `ko`**: the game says
  "저는 민준이에요." / "저는 서연입니다." (SNOOTY) / "나는 지우야." (baby) with the villager's own name. Its gloss
  contains `%s` for the name: `"I'm %s."`, `"Ich bin %s."`. Shipped example: `dialogue/vt_lazy_plains_intro.json`.
- **smalltalk**: 4–8 nodes. 14 per personality: one per region and seven general ones. Example:
  `dialogue/vt_peppy_gen_01.json`.
- **milestone**: once, when the friendship reaches `friendship` (3, 6, 10): the villager warms up. Example:
  `dialogue/vt_cranky_ms3.json`.
- **banmal_offer**: LAZY, JOCK, PEPPY and SISTERLY only, once at friendship 6 for a player at level 4 or above.
  "말 편하게 할까요?" with a yes choice that carries `{"type": "acceptBanmal"}` and a no choice. From then on the
  villager says the `ko_banmal` version of every line that has one. Example: `dialogue/vt_jock_banmal.json`.

### 반말 versions (`ko_banmal`)

After the switch, a LAZY, JOCK, PEPPY or SISTERLY villager says `ko_banmal` instead of `ko` where a node has it (and
`ko` where it does not). Write `ko_banmal` for the friendly lines of small talk and milestones of these four, never
for CRANKY, SMUG, NORMAL or SNOOTY (the check rejects it). It must be the same sentence in 반말 (-어/-아, -야, -자,
-지, -니), not the 해요체 line. The player's choices stay 해요체.

## Requests

```json
{
	"schema": 1,
	"id": "req_plains_01",
	"level": 1,
	"kind": "GIVE",
	"regions": ["PLAINS"],
	"goal": {"type": "GIVE", "item": "minecraft:bread", "entity": "minecraft:villager", "method": "DROP_NEAR", "count": 1},
	"words": ["concept:bread"],
	"answers": {"yes": "네, 좋아요!", "no": "아니요, 미안해요."},
	"lines": {
		"LAZY": {
			"offer": {"ko": "음, 빵이 필요해요.", "ko_banmal": "음, 나 빵이 필요해."},
			"accept": "고마워요. 빵을 기다려요.",
			"decline": "괜찮아요. 그럼 또 봐요.",
			"reminder": "음, 빵이 아직 없어요.",
			"thanks": {"ko": "빵이에요! 정말 고마워요.", "ko_banmal": "빵이야! 정말 고마워."}
		},
		"SNOOTY": {
			"offer": "안녕하십니까. 빵이 필요합니다.",
			"accept": "감사합니다.",
			"decline": "괜찮습니다.",
			"reminder": "빵은 아직 없습니까?",
			"thanks": "좋은 빵입니다. 감사합니다."
		}
	}
}
```

(The shipped `requests/req_plains_01.json` and `requests/req_desert_01.json` have all eight personalities.)

- `kind` and the honesty rules (the game is client-only, so a request may only claim what it can see):
  - `SHOW`: hold an item while talking to the villager again. `"show": {"item": "minecraft:cactus", "count": 1}`,
    no `goal`. Nothing is taken: no line may thank for a gift (줘서, 주셔서, 받았 … are rejected). The villager
    says "…을 보여 주세요".
  - `GIVE`: drop the item in front of the villager, who picks it up. The goal is `GIVE` to `minecraft:villager`
    with `"method": "DROP_NEAR"` (or no method), and the item must be one a villager picks up: `minecraft:bread`,
    `wheat`, `wheat_seeds`, `carrot`, `potato`, `beetroot`, `beetroot_seeds`, `torchflower_seeds`, `pitcher_pod`,
    `bone_meal`. Only a gift to the villager who asked counts.
  - `DO`: any other quest goal (`CRAFT`, `PLACE_BLOCK`, `REACH_BIOME`, `BREED`, `SMELT`, …, the same JSON and checks
    as a quest step, see `docs/questlines.md`), then report back by talking.
- `regions`: where villagers ask for it (at least one). `personalities` (optional): only these may offer it; lines
  are then needed for these only. Otherwise every one of the eight needs its five lines. `professions` (optional).
- `words`: the catalog words it practises (exposed on the offer, practised on the hand-in).
- `answers`: the player's yes and no, 해요체.
- `lines`: per personality `offer`, `accept`, `decline`, `reminder`, `thanks`, each a string or
  `{"ko": "…", "ko_banmal": "…"}` (반말 only for the four that switch). 8 requests per region.
- No item rewards: the reward is friendship +2 and the practised words.

## Glosses (English and German)

Every Korean line has an English and a German gloss with the same key in `lang/en_us.json` and `lang/de_de.json`:

| what | key |
|---|---|
| a line of a conversation | `hangulquest.dialogue.<id>.<node>` |
| choice N (1-based) of a node | `hangulquest.dialogue.<id>.<node>.c<N>` |
| the note after a graded choice | `hangulquest.dialogue.<id>.<node>.c<N>.note` |
| the instruction above a typed answer | `hangulquest.dialogue.<id>.<node>.prompt` |
| the self-introduction | `hangulquest.dialogue.<id>.<node>` with `%s` for the name |
| a request line | `hangulquest.dialogue.<req-id>.<personality>_<part>` (e.g. `…req_plains_01.lazy_offer`) |
| the player's answers | `hangulquest.dialogue.<req-id>.yes`, `….no` |
| the short task title (quest log, journal) | `hangulquest.request.<req-id>.title` |

A `ko_banmal` line shares the gloss of its node. Glosses translate the meaning plainly; mark the register where it
matters ("(casual)").

## Rules every line must pass

1. **Speech level per personality**: SNOOTY lines are 합쇼체, all other personalities 해요체, children 반말,
   `ko_banmal` 반말. A line must not mix levels. The player's choices are 해요체 unless the node is a graded
   speech-level task.
2. **No names**: no line says a villager name (the 80 names of `data/villager-names.json`) or the player's name;
   only the `selfIntro` node does, and the game builds it. Every line is recorded once per voice and must fit every
   villager.
3. **Plain Hangul**: only Hangul, spaces and `, . ! ?`, ending in `.`, `!` or `?`. No digits, Latin letters, `…` or
   quotes.
4. **Vocabulary coverage**: every word (eojeol) is a stem from the catalog (`data/concepts.json`, official Minecraft
   Korean names from `data/names.json`) or the dialogue lexicon (`dialogue/_lexicon.json`), followed by a particle or
   ending the coverage knows (`이 가 은 는 을 를 도 만 의 에 에서 에게 와 과 로 으로 예요 이에요 야 이야 입니다 요 어요 아요
   해요 해 세요 으세요 습니까 십시오 습니다 합니다 니다 자 …`). A line may have **at most one** word a beginner does
   not know (catalog words above tier 2 and lexicon words with `"known": false` count as new); new words are
   glossed on hover, so each needs an English and a German gloss. A word that is in neither list goes into your
   `_lexicon.json`: `{"words": [{"id": "nap", "forms": ["낮잠"], "known": false, "en": "nap", "de":
   "Mittagsschlaf"}]}` (`forms` are the stems as they appear before the ending; a verb needs the conjugated stem
   too: `["기다려", "기다리"]`).
5. **Official item names**: an item, block, animal or biome is called by its vanilla Korean name (`names.json`),
   e.g. 선인장, 빵, 주민, 평원.
6. **Grammar within the level**: constructions above the conversation's `level` (ladder in `data/ladder.json`,
   `data/grammar.json`) are rejected where the game can recognise them (-(으)면 and -지만 from level 4, -(으)ㄹ게요
   level 4, -(으)니까 and -는 동안 level 5, -(으)ㄹ수록, -듯이, -기 마련이다, -(으)ㄹ 수밖에 없다 level 6). Keep
   level 1–2 lines to present-tense 해요체, 을/를, 이/가, 에, 있어요/없어요, 안, numbers and counters, (으)세요.
7. **Korean linter**: particles fit the noun (을/를, 이/가, 은/는, 와/과, (으)로), no mixed speech levels in one
   line, counters spaced, no placeholders.
8. **Shape**: every node reachable, every node can end, ids of a–z, 0–9 and `_`, effects point at things that
   exist (catalog words, trail ids, grammar ids).
9. **Requests**: the honesty rules above, five lines per personality that may offer it, the goal passes the quest
   goal checks, `words` are catalog ids.

## After `DRAFTS OK`

The integrator copies `dialogue/*.json` to `src/main/resources/assets/hangulquest/dialogue/`, `requests/*.json` to
`src/main/resources/assets/hangulquest/requests/`, merges the glosses into
`src/main/resources/assets/hangulquest/lang/en_us.json` and `de_de.json` and the new words into
`dialogue/_lexicon.json`, then runs `tools/gates.sh` (it regenerates the review registry
`content/korean/lines.jsonl`) and commits. The voice tooling reads `data/voices.json` and
`data/villager-names.json` and records every line as `line.<id>.<node>.<voice>` (`<node>_banmal` for the 반말
version, `line.name.<name-id>.<voice>` for the self-introduction).
