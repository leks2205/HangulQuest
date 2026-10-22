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
(„땅 아래에서", „120블록보다 높은 곳에서"; before a place of the goal's own it modifies it: „높이 90블록 위 침대에서", from level 3 „…위의 침대에서"), `IN_BIOME` („해변에서"), `IN_DIMENSION` („지옥에서",
„엔더에서"), `NEAR_ENTITY`/`NEAR_BLOCK` („좀비 근처에서"; `NEAR_ENTITY` takes an optional `count`, „주민 두 명 근처에서", „소 세 마리 근처에서" with 명 for people and 마리 for animals; `NEAR_BLOCK` is a proximity check, a block of that kind within the
radius, not the block in the crosshair), `SKY` („밖에서" on levels 1 to 4, „하늘이 보이는 곳에서" from level 5;
`ROOFED` says „지붕 아래에서" of the player, but with a `PLACE_BLOCK` goal it checks the roof over the placed block
and says „지붕 아래에 통을 놓아요"). A step without an authored clue uses the generated sentence, which speaks every
condition. An authored clue wins also on a step with conditions: it is shown as written, and a condition
phrase it does not contain is put in front of it, so no condition can be missing from the text. A clue that
says the condition in its own words does not get the phrase again: 밤에 / 한밤에 / 밤이 for `TIME` night, 비가 for
rain, 물속 for `IN_WATER` (a forms list per condition, kept to words that cannot mean anything else: 밤 alone is
also a chestnut). `FACING` (`direction`) says „북쪽을 보고" in front of the action; it is true while the yaw of the player is within 45 degrees of the direction. Heights and distances in blocks are spelled in Sino-Korean from level 2 („높이 백 블록 위로", „물에서 이십 블록 수영하세요"; digits only above 100 unless round hundreds); counts with a native counter go up to 99 („스물네 개"). `AVOID` may forbid an action on a creature („주민을 물리치지 마세요"). `REACH_HEIGHT` below the ground uses „내려가다" (descend) instead of climbing.

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
- `grammar` is optional: the constructions of `grammar.json` (or their ladder aliases) the trail is meant to practise,
  `["L2.seyo", "L2.eseo_activity"]`. The loader checks every id and drops the unknown ones with a problem; the list
  is dumped into `steps.json`.
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
- Goals and conditions are strict about their fields: an unknown field (`{"type":"WEATHER","phase":"CLEAR"}`) or a
  missing required one makes the file unreadable, and the log names the trail and the step
  (`probe/s1 condition WEATHER: unknown field 'phase' ...`). The fields of each type are in the schema.

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
| `REACH_HEIGHT` | `minY`, `maxY` (a ceiling below sea level, `maxY` under 63, asks for a descent: 아래로 내려가요; from level 2 `높이 40블록 이하로 내려가세요`) | player altitude |
| `USE_BLOCK` | `block`, optional `change` (`OPEN`/`CLOSE`: a wooden door, trapdoor or fence gate that really changes state, 열어요 / 닫아요; `LIT`: a candle or campfire that really catches fire, 켜요 / 피워요; a candle without `change` means `LIT`; `WRITE`: a sign whose edit screen is closed with text on it, 표지판에 글을 써요, or 표지판에 이름을 써요 when the step teaches 이름; a sign without `change` means `WRITE`) | right-click on that block; with `change` the sensor compares the block's `open` (or `lit`) state before and after the click; for `WRITE` it watches the sign edit screen and the client copy of the sign |
| `INTERACT_ENTITY` | `entity` | interaction with the mob. The item in the hand decides the verb when a `HOLDING` condition names a tool that does something to that creature: shears on a sheep 가위로 양털을 깎아요, a lead 끈으로 소를 묶어요, a brush on an armadillo 솔로 아르마딜로를 닦아요, shears on a wolf in armour 가위로 늑대의 갑옷을 벗겨요. The tool phrase replaces 가위를 들고; any other item keeps feeding or petting |
| `PLACE_REL` | `block`, `relation` (`ON_TOP_OF`/`UNDER`/`NEXT_TO`/`IN_FRONT_OF`/`BEHIND`/`INSIDE`/`OUTSIDE`/`BETWEEN`/`NEAR`), `anchor`, `count`, optional `anchor2` | a placement of `block` in that relation to `anchor` (a block, family or `#tag`, or `"@s2"`, see below); a right block in the wrong relation is a near miss, never progress |
| `BUILD_PATTERN` | `pattern` (`PILLAR`/`PYRAMID`/`RING`/`ROW`/`WALL`/`CROSS`/`STAIRS`; a ring is a 담, and only fence blocks make a 울타리: `울타리를 쳐요`), `material`, `size` | the placement that completes the shape out of `material` (sizes: pillar and row 2–4, pyramid and ring 3, wall 2–4 = 2x2, 3x2, 4x3, cross 3 = a plus of five blocks, stairs 3–4 steps) |
| `PLACE_ON_PATTERN` | `block`, `relation`, `pattern`, `material`, optional `size` (the size of the structure; a row of four reads 자갈 네 개 옆에) | a placement of `block` in that relation to a structure built from `material` |
| `CRAFT` | `item`, `count`, optional `station`, optional `with` (ingredient ids, wording only) | taking the result out of a crafting screen |
| `SMELT` | optional `input` (wording, level 4+), `output`, `count` | taking the result out of a furnace, blast furnace or smoker |
| `GIVE` | `item`, `entity`, optional `method` (`FEED`/`USE_WITH`/`DROP_NEAR`/`TRADE`), `count` | the hand-over facts of the sensor. With `USE_WITH`, wolf armour on a wolf and horse armour on a horse are put on (늑대에게 갑옷을 입히세요) and dye on a sheep colours it (파란색 염료로 양을 염색하세요). A villager takes no item by right-click: without a `method` only a drop it picks up (the item entity is gone, nobody took it back, the villager is still near) or a `TRADE` counts, and the hint says `주민 앞에 빵을 놓아요`; a wandering trader only takes a `TRADE`. The validator refuses `FEED`/`USE_WITH` for both |
| `PUT_IN` | `item`, `container`, `count` | items moved into that container block |
| `TAKE_OUT` | `item`, `container`, `count` | items moved out of that container block (the container lost them and the player gained them; an item put in a moment before does not count) |
| `EQUIP` | `item` (a helmet, chestplate, leggings or boots, or the tag of a slot: `#minecraft:chest_armor`), optional `change` (`ON` the default, `OFF`) | the four armour slots of the player: on counts when the slot was seen without the piece and then has it, off the other way round (a piece already worn when the step begins has to come off first). 투구를 써요, 갑옷을 입어요, 바지를 입어요, 신발을 신어요; 벗어요 for `OFF` |
| `HOLD_WHILE` | `item`, `inner` (a goal, or a condition plus `seconds`) | `inner` only counts with the item in a hand; with a condition: walking with it |
| `SEQUENCE` | `parts` (2–3 goals), `ordered`, optional `connector` (`BEFORE`/`AFTER`/`THEN`/`AND`) | the parts in order; an event before its turn does not count. Trail level 3 or higher; at level 2 only the plain -고: parts in any order, or `"ordered": true` with `"connector": "AND"` (문을 닫고 횃불을 놓으세요), where the order is checked and the step counts as form-dependent |
| `AVOID` | `action` (a goal), `windowSeconds`, optional `during` (a condition) | the window runs out without the action; doing it restarts the window quietly. Trail level 3 or higher |
| `TRADE` | `item`, optional `entity` (a villager), `direction` (`BUY`/`SELL`), `count` | the result slot of the merchant screen: a buy counts what arrived and is said with the source marker (주민에게서 빵을 사요), a sell what was paid (주민에게 밀을 팔아요). The particle decides the direction, so the step is form-dependent (L2.ege) and a trade the wrong way round (selling what was to be bought) is a slip with a hint in the status line |
| `TRANSIT` | `direction` (`IN`/`OUT`) | the sky above the player flips and stays flipped for a second: 집 안으로 들어가요 / 집 밖으로 나가요. The player has to be on the other side first. The place noun is spoken only for 집, 방 and 동굴 |
| `HEAR` | `source` (`minecraft:jukebox`, `minecraft:note_block` or `minecraft:bell`), `radius` (2–16), `dwellMs` | a jukebox with a disc in it within `radius` blocks for `dwellMs` (the sensor scans the block entities around the player twice a second, only while such a step is tracked), a struck note block, or a bell ringing within `radius` (by anyone; 종소리를 들어요) |
| `LIE_DOWN` | `block` (a bed), `dwellMs` | the sleeping pose in that bed for `dwellMs`; the night does not have to pass (a night condition is excused once the server skipped it, as for 자요) |
| `WAIT` | `seconds`, optional `until` (`NIGHT`/`DAY`) | staying within three blocks of the spot where the wait began; walking away starts over. With `until` also that part of the day. A place to wait near is a condition (`NEAR_BLOCK`) |
| `BREED` | `entity`, `count` | a baby of that kind (the synced young flag) that was not near before, within two minutes of the player feeding or using an item on an animal of the kind, and within 4 blocks of two of the animals fed |
| `MOUNT` | `entity`, `dwellMs` | the vehicle state of the player: a boat or minecart is sat in (앉아요), an animal is ridden (타요); every boat is one boat |
| `FALL` | `blocks` (4–250), optional `landing` (a soft block: powder snow, hay, slime, honey, water) | the distance fallen since the feet left the ground (the longest value before the game resets it on the landing). With `landing` the player has to land on or in that block; without it the health must not drop (read 0.3 s after the landing, when the damage has arrived). 높은 곳에서 가루눈 위로 뛰어내리세요; 십 블록 높이에서 안 다치고 뛰어내리세요 |
| `SHOOT` | `weapon` (bow, crossbow, trident, snowball, egg), optional `entity` or `block` (only `minecraft:target`), `count`, `minDistance`, `pierce` | every projectile of the player is followed to its end. A creature is struck when it shows the hurt animation after the projectile passed, or (snowball, egg, arrow) when the projectile vanishes beside it; a target block when it shows power. `pierce` 2 or more: one projectile must strike that many. Without a target the shot itself counts. 활로 과녁을 맞히세요; 쇠뇌로 좀비를 맞히세요; 삼지창을 던지세요; 멀리서 활로 과녁을 맞히세요 (이십 블록 이상 떨어진 곳에서 from level 2) |
| `WALK` | `direction` (NORTH, SOUTH, EAST, WEST), `blocks` | blocks moved along the direction (north is -Z) from the change of position between ticks; a step back takes from it, a jump of more than eight blocks is no walk. 북쪽으로 이십 블록 가세요 |
| `SWIM` | `blocks` | blocks covered while the player is in water (a jump of more than eight blocks between two ticks is not swimming) |

**Tiers.** One word stands for a tool or an armour piece in every material (곡괭이, 도끼, 삽, 괭이, 검; 투구, 갑옷, 바지,
신발), and a goal that asks for one material says it: with 곡괭이 taught, `iron_pickaxe` reads `철 곡괭이를 만드세요`
(`quest/goal/Tiers`). The goal is exact then: a wooden pickaxe does not finish a step that says 철 곡괭이. The name
printed is always the official one (`names.json`): `iron_chestplate` reads 철 흉갑 (not "철 갑옷"), the leggings 철 레깅스,
the boots 철 부츠, and the leather set 가죽 모자, 가죽 조끼, 가죽 바지, 가죽 장화. The general word keeps the sentence
when the goal is any piece of the slot: `EQUIP` on `#minecraft:chest_armor` (also `head_armor`, `leg_armor`,
`foot_armor`) says 갑옷을 입으세요 and takes every material.

**Relations and anchors.** The Korean names the relation (`문 옆에`, `기둥 위에`) in every form of the
sentence, and the English and German clue must not give it away. A relation goal is graded: finishing the step
is a right outcome for the location noun (`L1.loc_yeop_e` for `NEXT_TO`, `L2.loc_sai_e` for `BETWEEN`, ...),
and a right block in the wrong relation is a wrong one (at most three per step). `BETWEEN` has one anchor
(`두 횃불 사이에`) or two different ones: `"anchor": "minecraft:chest", "anchor2": "minecraft:furnace"` says
`상자와 화로 사이에` (와/과 by the final sound; the pair is one task whichever way round it is written). With water or lava as one of the two (`모래와 물 사이에 사탕수수를 심어요`) a plant counts that stands on the other block with the fluid next to it, on the shore.
Blocks with a front can be the anchor of 앞 and 뒤: doors, chests, furnaces, beds, stairs, barrels, workstations, signs, banners, pumpkins, bells; a pillar, a wall or a fence cannot. So can a creature that faces a way (`minecraft:villager`, an iron golem, a cow, a wolf ...: 주민 앞에 횃불을 놓아요; 앞, 뒤, 옆 and 근처 only). 밖 is a deciding form (a block put outside when 안 was asked is a slip), 근처 is not. Fluids are anchors too (`minecraft:water` is 물, `minecraft:lava` is 용암: `물 옆에 횃불을 놓아요`). A tag
(`#minecraft:doors`, `logs`, `planks`, `wool`, `fences`, `beds`, `torches`, `chests`, `flowers`, `signs`, `candles`, `wool_carpets`, `windows`, `saplings`,
`leaves`; for `EQUIP` also `head_armor`, `chest_armor`, `leg_armor`, `foot_armor`) is spoken with its everyday word (문, 통나무, 판자, 양털, 울타리, 침대, 횃불, 상자, 꽃, 표지판, 초, 양탄자, 창문, 묘목, 나뭇잎): the
curated concept whose items and covers are the largest set inside the tag; a tag without such a word is a validator error, never "그것". The finale line uses the same word
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

**Verbs a step can teach.** A step may teach a verb next to its nouns, or as its first word: the clue then
contains a conjugated form of it (`올라가요` for 올라가다). When the goal can say that verb it uses it instead of its
default: 요리하다 for a food in the furnace (`닭고기를 요리해요`, the default is `구워요`), 타다 for a boat that is
sat in (`배를 타요`), 나오다 for leaving (`집에서 나와요`). A water biome with the `IN_WATER` condition reads
`바다에서 수영해요`, not `물속에서 바다로 가요`: the sentence already says the water, so the condition's phrase is left out.

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
accepts any sapling as its goal, not only the oak one, and the sentence says 묘목 for it too. A cover is spoken
with the word unless the word only describes (`"grammar": "DESCRIPTOR"`, the colours: 빨간색 양털 stays 빨간색
양털) or the item has a hand-made word of its own and the word only resembles it (눈 does not name the snow block;
`"accepts"` lists ids that a goal takes but the word never names). A word for one flower names that flower
only: with 해바라기 taught, "민들레 옆에 해바라기를 심어요". The covers of a biome concept are biomes: 동굴
covers the lush caves, so a `REACH_BIOME` on any of them counts and reads 동굴로 들어가요. A step that teaches a
word of the biome's kind still says the biome: 개구리 on a swamp reads 늪으로 가요. The hint of a step with
several nouns names every noun the goal has a place for, so it needs no authored hint.

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
