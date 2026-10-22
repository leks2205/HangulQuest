# Changelog

All notable changes to Hangul Quest. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [semantic versioning](https://semver.org/). Minecraft versions are named
explicitly, because a mod for 26.3 is not a mod for anything else.

## [Unreleased]

### Changed

- **The grammar ladder is data and teaches.** Each Korean level now has one instruction ending, set in `data/ladder.json`: `-아/어요`, `-(으)세요`, `-아/어야 해요` (the old "must" ladder, the double negative and the resigned `-(으)ㄹ 수밖에 없어요` are gone as instructions). A player reads at most one level above the grammar they have met; a new construction shows a two-line rule card once; the journal has a grammar book; the review quiz has grammar cloze cards. What the player has met is derived from the review log, so saved progress is unchanged.

### Added and fixed (generator wave 6)

- **Limits from the first level-2 batches.** A trail may list the constructions it practises (`"grammar": [...]`, checked
  against `grammar.json`, dumped into `steps.json`; the 15 smithy trails carry theirs). The iron and gold chains have
  words (철 광석, 철 원석, 철 주괴, 금 ...); 철 stays the core word. A tool or armour goal that asks for one tier says it
  (철 곡괭이, 나무 도끼, 철 갑옷; 투구, 바지, 신발 are words too) and is exact. New goal `EQUIP` reads the four armour
  slots (투구를 써요, 갑옷을 입어요, 신발을 신어요, 벗어요). 밖 is a deciding form; 앞 and 뒤 take stairs, workstations,
  barrels, signs, banners, pumpkins and creatures (주민 앞에) as anchors. Compound pattern names are spelled as
  표준국어대사전 does (돌담, 돌기둥) and the shortener keeps a shared material (석재 벽돌 기둥 옆에 석재 벽돌 계단을). Signs,
  armour stands and single pillars are stood (세우다), hanging signs are fitted (달다). A plain -고 sequence is a level-2
  sentence. The request to eat is 드세요, to till is 괭이로 흙을 가세요, to buy is 주민에게서 사세요; a villager takes a
  dropped item it picks up, not a right-click. The German giveaway check looks at the step's own relation, as separate
  words, and leaves out what its conditions say (unter der Erde). A concept without a German gloss that a trail teaches
  now fails a test by name.

### Fixed (generator wave 5)

- **Limits from the seventh content batch.** 요리하다 names the food that goes in (감자를 요리해요), not the baked
  product; the held item of a HOLDING phrase uses the taught word in the clue as in the hint (배를 들고); stairs are
  piled up (계단을 쌓아요); a block placed under a roof is checked on the block (`PlacementFact.roofed`) and said as
  지붕 아래에 통을 놓아요; 표지판 (every sign and hanging sign) and 괭이 (every hoe) are words; `NEAR_BLOCK` is a
  proximity scan (twice a second, only while a step asks) instead of a stare; a wait shows the seconds left in the
  status line and in the hint. `tools/coverage.py` counts a taught word as an encounter only when the clue, the hint,
  the title or the finale line says it, and reads 팔아요 as 팔다.

### Fixed (generator wave 4)

- **Limits from the fifth and sixth content batches.** Lighting a candle is checked (the `lit` state flips; `USE_BLOCK`
  with `change` `LIT`, the default for candles); a baby only counts as bred when it stands within 4 blocks of two
  of the animals the player fed; the open sky says 밖에서 up to level 4 and 하늘이 보이는 곳에서 from level 5, with
  grammar ids that exist; a height or a place condition before a place of the goal's own no longer doubles the
  에서 (높이 90블록 위 침대에서); a word for a whole kind does not name two different things of one sentence (꽃
  옆에 민들레를); the clue says a second taught noun when it was met before; an authored clue may hold a verb in
  any conjugated form; the German giveaway check ignores "bei"; heights have a band in their task signature;
  used blocks get their own verb (제작대를 열어요, 주크박스를 틀어요, 리스폰 정박기를 충전해요, ...) instead of 쓰다;
  `pronunciation-exceptions.json` has a `crossWord` switch; `steps.json` and `tools/variety.py` have the share of
  form-dependent steps per level.

### Fixed

- **Generator limits from the second content batch.** An authored clue now wins on a step with conditions (the
  condition phrases it lacks are put in front); a word that covers a block (묘목: any sapling) is accepted by the
  validator; `BREAK_BLOCK` takes any breaking verb of `verbs.json` (파요, 꺾어요, ...) and a block tag
  (`#minecraft:flowers`); water and lava are anchors (물 옆에), and a tag anchor is spoken with its everyday word
  (꽃, 통나무, 묘목, 나뭇잎) in the clue and in the finale; snow blocks have their own word (눈 블록); an open sky
  is "밖에서" on levels 1 and 2; the note block is struck (쳐요) and the bell rung; 창살, 밀짚 and 숨결 are tensed
  and 철사 tensifies inside a phrase. A content metric that meets its roadmap target no longer fails the ratchet.
- **Generated Korean names things by what they are.** A gift is named by its own name (a cat shown by a cod no
  longer gets "고양이에게 고양이를"), an entity anchor is the animal (`chicken` is 닭, not the raw meat; `PLAYER_REL`
  takes an optional `kind`), a log anchor is 나무 when the step teaches 나무, relation sentences drop the material
  they repeat ("울타리 옆에 문을 달았어요"), finale lines use the taught word ("우유를 마셨어요"), and counted nouns
  take their counter from `counters.json` (종이 세 장, 꽃 두 송이). Building a pattern and placing on one now show
  a status line; a cave biome is entered (들어가요) and a climb reads "언덕 위로 올라가요" at level 1.
- **A step may teach any role of its goal**, and a word with several referents accepts any of them.
- **Progress can no longer be lost by an update or a crash.** `progress.json` is written to a unique temp file,
  forced to disk and renamed over the old file. Fields this version does not know are written back unchanged,
  and a file written by a newer version is opened read-only instead of being overwritten.
- A byte-order mark at the start of `progress.json` no longer makes it unreadable.

### Added

- **The everyday words the mod lacked, and the verbs a player can do.** The 150 common words of the curriculum
  list that had no concept (사람, 위, 안, 하나, 빨간색, 열쇠, 듣다, 눕다, ...) are concepts now, with tags and
  English and German glosses: place words, numbers, counters, directions and colours are lexicon entries (colours
  stand for their dye and wool), foods and vehicles cover the items of the game. New goals: `TAKE_OUT` (꺼내다),
  `TRANSIT` (들어가다, 나가다, 나오다: the sky above the player flips), `HEAR` (듣다: a playing jukebox or a
  struck note block), `LIE_DOWN` (눕다), `WAIT` (기다리다), `BREED` (키우다), `MOUNT` (앉다, 타다) and `SWIM`
  (수영하다); `USE_BLOCK` takes `change` (`OPEN`/`CLOSE`, the door state really changes), a descent reads
  "아래로 내려가요", a step that teaches 요리하다 cooks food in the furnace ("닭고기를 요리해요") and a sea with the
  water condition reads "바다에서 수영해요". The sensor sees door states, the vehicle, a playing jukebox, taken-out
  items and baby animals (`sense/README.md`). An authored clue that says its condition in its own words keeps its
  text. Progress files are unchanged.
- **Generator limits from the third to the fifth content batch.** A step that teaches a word of another kind on
  a biome says the biome (개구리 on a swamp: 늪으로 가요), biome words have covers (동굴: the lush caves) and a
  word speaks every id it covers (묘목 for any sapling) except where the item has a word of its own (눈 블록 is
  never 눈); a ring of blocks is a 담 and only fences make a 울타리 (울타리를 쳐요); a torch is placed (놓아요), a
  candle switched on (켜요), a campfire kindled (피워요); stone, glass and ores in the furnace name their input
  (화로에서 조약돌을 구워요, 철을 녹여요); a covered fish accepts the cooked form of every fish; `PLAYER_REL` takes
  a tag; water can be looked at; a verb may be the first taught word; the hint of a multi-word step names every
  noun; a new `TRADE` goal (사요, 팔아요). The finale fixture picks nouns the way the clue does.
- **Books tab: graded stories, chapters and culture notes.** The journal has a new Books tab (책장). Four short
  stories (집, 불과 빛, 밭, 동물; 80 to 200 syllables, level 1 to 2 grammar) open when their trails are done and at
  least 98% of their words are known to the player; a locked story lists the words to learn first. Hovering or
  clicking a word shows its gloss, and for a conjugated verb its stem, ending and grammar point. Reading a story
  logs an encounter (never a review) for its words, once per study day. The Quests tab has a second view, the
  trails by life situation (`data/chapters.json`, written by `tools/assign_chapters.py`) with progress per
  chapter. Eight culture notes (온돌, 한옥, 장독, 한복, 김치, 설날, 추석, 윷놀이) appear once as a HUD card when the
  matching item is first crafted or picked up, or, for 설날 and 추석, near their real date (a table for
  2026 to 2030, no network), and are listed in the Books tab.
- Action variety for quests: goals `CRAFT`, `SMELT`, `GIVE`, `PUT_IN`, `HOLD_WHILE`, `SEQUENCE` (-기 전에,
  -(으)ㄴ 후에, -고 나서) and `AVOID` (-지 마세요), conditions `HOLDING`, `SKY`, `IN_WATER` and `PLAYER_REL`, the
  verbs 넣다, 들다, 들어가다, 떨어뜨리다, 닫다, 녹이다, 굽다 and 걷다, and a level-3 demo trail "마을 심부름". Every generated
  sentence can now report the grammar points it teaches.
- Daily and weekly backups of `progress.json` in `config/hangulquest/backups/` (the last 7 days and 4 weeks).
- One progress fixture per released version, loaded and saved by `ProgressCompatibilityTest` to catch any
  change that would drop saved data.

### Changed

- `progress.json` is written as compact JSON; it is about a third smaller.
- Interface text now lives in lang keys (`hangulquest.ui.*` in `lang/en_us.json` and `lang/de_de.json`) and is
  resolved by the mod itself, so the interface language can differ from the game language. A key a language lacks
  falls back to English, never German. `util/Tr` stays one release as a deprecated shim.
- The language setting in `config.json` is now `AUTO` or a language code (`en_us`, `de_de`); old `EN`/`DE`
  values are migrated on load.
- Vanilla items and entities become vocabulary from the Korean name alone. `de_de` is no longer required, and
  the glosses come from English and the language file of the player's interface language.

## [2.1.0] — 2026-10-20

### Added

- **English, everywhere.** The interface, the journal, the settings page, the achievements, the quiz and
  every quest line now exist in English as well as German. English is the default; *Optionen → Sprache*
  switches to German, and an existing config that had German switched on moves over automatically.
- **English quest text for all 420 trails**: titles, 1,524 clue and hint lines, 420 finale lines, and the
  vocabulary gloss for all 1,132 concepts. Names come from Minecraft's own `en_us.json`, so an apple is
  an apple; the sentences are generated from the same goal data as the German ones.
- `QuestlineValidationTest` now also fails when a trail lacks an English title or a step lacks an English
  clue, or when an English clue contains Hangul.

### Changed

- **The translation line stays off by default**, in either language: quest text is Korean only until you
  switch *Übersetzungen in Quests* (or the journal's eye) on. The setting no longer means "German", only
  "show a translation", and it follows the interface language.
- **Package renamed** from `de.zirks.hangulquest` to `io.github.leks2205.hangulquest` (config, progress
  files and the mod id are unchanged), so the source tree carries no personal namespace.

[2.1.0]: https://github.com/leks2205/HangulQuest/releases/tag/v2.1.0

## [2.0.1] — 2026-10-20

### Fixed

- **German lines for re-authored steps.** The sweep dropped the hand-written German clue of every step
  it changed; they are now generated from the goal ("Sammle 2× Roten Farbstoff."), so the German toggle
  explains every step again.
- **Two impossible goals that predated the sweep.** `adv_end_respawn_dragon` asked to *place* an end
  crystal (an entity, not a block) and `w8_06` asked to place lava; they now place crying obsidian and
  basalt.
- Object pools now come from the game's own registries (1,657 items, 1,285 blocks, 160 entities, dumped
  from the dev client) instead of the language file, so no quest asks for a block that cannot be placed
  or a mob that does not exist.
- A dev hook (`-Dhangulquest.dumpregistries=true`) writes those registry lists to `run/` for content
  tooling.

## [2.0.0] — 2026-10-20

### Changed

- **Every action is used exactly once.** Only 406 of the 1,505 steps had a goal of their own; 1,109
  steps repeated an action another quest already asked for (68 trails placed a torch, 51 a campfire,
  41 required sleeping in a bed). All of them now have a unique `action + object` pair, chosen from the
  game's registries and matching the branch and level of the trail they sit in.
  `QuestlineValidationTest.everyActionIsUsedOnceAcrossTheContent` keeps it that way.
- **The vocabulary catalog grew to 1,132 entries.** The 293 hand-written concepts stay; every rarer
  object the quests now use got a generated entry with the game's Korean and German name.
- The 29 altitude-only steps were re-authored into object goals: "climb above Y 120" never names the
  word it teaches.

### Added

- **The colour chain**, six quests that build a thing in the order a crafter would: red, blue and yellow
  dye paths (beetroot, cornflower, sunflower), the purple and orange wool quests that require both their
  parent dyes, and a rainbow banner that needs both mixed colours.
- 420 questlines, 1,524 steps.

[2.0.1]: https://github.com/leks2205/HangulQuest/releases/tag/v2.0.1
[2.0.0]: https://github.com/leks2205/HangulQuest/releases/tag/v2.0.0

## [1.3.1] — 2026-10-20

### Fixed

- **`V` only reached two quests.** The focused quest was sorted to the top of the log, so the "next" step
  always landed on whichever quest had just moved down — with three or more quests the highlight bounced
  between the first two. The log now keeps a fixed order and only the gold title moves, so `V` visits
  every checked quest and wraps around.

[1.3.1]: https://github.com/leks2205/HangulQuest/releases/tag/v1.3.1

## [1.3.0] — 2026-10-20

### Changed

- **The quest log lost its panel.** Quests are drawn straight onto the world as shadowed text: two
  tight lines per quest, no background box, no header or footer — the log now takes roughly a third of
  the screen space it used to.
- **Counts are inline instead of a bar.** A counting goal shows its number right after the objective
  (`회색 돌을 세 개 캐요. 1/3`); condition messages follow after a dot (` · 밤 필요`) when they fit.
- The highlighted quest keeps a gold title; everything else is white/grey, so the focus is visible
  without a box.

[1.3.0]: https://github.com/leks2205/HangulQuest/releases/tag/v1.3.0

## [1.2.0] — 2026-10-20

### Added

- **Checkbox tracking in the journal.** The Quests tab's detail card has an **Im Log** checkbox (and a
  **Fokus** button for a quest that is already in the log). Checked quests are drawn on the HUD and are
  the only ones whose goals are checked against the world.
- The HUD hint panel now always opens below the quest log, whatever its height.

### Changed

- **Only visible quests progress.** A quest that is not checked into the log keeps its saved step,
  counter, hint state and sidequest, but it does not advance — so quests can no longer complete behind
  the player's back while another quest is in focus.
- Checking a seventh quest unchecks the oldest one instead of discarding it; its progress stays saved.
- Taking a quest out of the log (checkbox, or **Abbrechen**) keeps its progress for later.

[1.2.0]: https://github.com/leks2205/HangulQuest/releases/tag/v1.2.0

## [1.1.1] — 2026-10-20

### Fixed

- The journal marks every quest in the log as tracked ("▸ im Log") instead of only the highlighted one,
  and offers a **Fokus** button to highlight a quest that is already in the log.

[1.1.1]: https://github.com/leks2205/HangulQuest/releases/tag/v1.1.1

## [1.1.0] — 2026-10-20

### Added

- **Quest log instead of a single tracker.** The HUD now lists every tracked quest with its title,
  current Korean objective, live status line and a progress bar for counting goals — modelled on how
  World of Warcraft quest trackers show accepted quests, drawn in the mod's own panel style.
- **Several quests at once.** Up to six trails are tracked in parallel and all of them are checked
  against the same world observation, each with its own step, partial counter, hint state and sidequest.
  Starting a quest adds it to the log instead of replacing the previous one; the oldest background quest
  is dropped when the log is full.
- **Focus cycling.** `V` moves the highlight through the log. Hints, sidequests and the follow-up chain
  follow the highlighted quest, so `H` answers exactly the quest you are looking at.
- `questTrackerRows` in the config (1–6) caps how many log rows the HUD draws.
- A dev run helper for UI screenshots: `./gradlew runClient -Pdemo=questlogdemo -PquickPlay="World"`.

### Changed

- Finishing a background quest marks it done with a banner and does not steal the highlight; finishing
  the highlighted quest still starts or offers its successor.
- A quest that is already tracked is only focused when you start it again, never reset.
- The old "the active quest switches to whatever you just did" behaviour is gone: with a quest log, an
  action simply counts for every tracked quest that wants it.

[1.1.0]: https://github.com/leks2205/HangulQuest/releases/tag/v1.1.0

## [1.0.0] — 2026-10-20

First public release, for Minecraft 26.3.

### Added

- **414 questlines / 1,505 steps.** Authored JSON trails in a tree 22 levels deep, with 38 roots and
  merges of up to five parents. Korean difficulty climbs from polite `-아/어요` to literary
  `-지 않고서는 … 수 없어요` following the tree depth.
- **Quest engine.** Twelve goal types (break, place, acquire, consume, look at block or entity,
  reach biome, dimension or height, use block, interact, defeat) and seven condition types (time,
  weather, altitude, biome, dimension, nearby entity, nearby block), all verified on the client.
- **Hint → sidequest → return.** `H` restates the task in level-1 Korean and starts a two-question
  sidequest on the blocking word; the original step waits until it is finished. Known words skip the
  sidequest.
- **Tracker.** Current Korean sentence, live status line (`0/3`, `지금 낮 · 밤 필요`, `Y 62 / 140 위`)
  and non-modal step, sidequest and trail celebrations with sounds and particles.
- **Journal.** A hand-drawn notebook screen with info, dictionary, learning, achievements, trail tree
  and options, plus a full-text dictionary of curated, vanilla and imported words.
- **Vocabulary.** 293 curated concepts, generated vanilla names, and a read-only Lute CSV import
  (`config/hangulquest/vocab/lute-vocab.csv`, statuses 1–5 and 99).
- **Learning layer.** Leitner spaced repetition, daily review trail, streak and fifteen achievements.
- **Content pipeline.** JSON questlines hot-reloaded from `config/hangulquest/questlines/` every two
  seconds, a template, a scaffolding script, a JSON schema, an authoring guide, an attribute
  reference and a validation test that rejects dead ends, unspoken conditions and wrong levels.
- **Bundled Korean font**, a Noto Sans CJK KR subset, so Hangul renders identically on every machine.

### Known limitations

- Minecraft 26.3 only, Java 25, client-side only.
- Interface text is German; quest text is Korean with an optional German line.
- No pronunciation audio, no romanisation, no Korean keyboard input.
- Progress is stored per instance and is not synchronised between machines.

[1.0.0]: https://github.com/leks2205/HangulQuest/releases/tag/v1.0.0
