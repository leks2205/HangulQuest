# Changelog

All notable changes to Hangul Quest. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [semantic versioning](https://semver.org/). Minecraft versions are named
explicitly, because a mod for 26.3 is not a mod for anything else.

## [Unreleased]

### Added

- **Sound forge (소리 대장간)**, a listening mini-game in the journal's Study tab for the sounds that sound alike to an
  English or German ear: ㄱ ㄲ ㅋ, ㄷ ㄸ ㅌ, ㅂ ㅃ ㅍ, ㅈ ㅉ ㅊ, ㅅ ㅆ and ㅓ ㅗ (never ㅐ ㅔ). A voice says a syllable or a word, you
  pick which of two or three it was (keys 1 2 3, R to hear it again, or click), and you hear both sounds again with the
  answer. A run is 60 trials in a random voice each time. A 30-trial listening test before and after (8 to 10 runs over at
  least 3 weeks) uses voices that never appear in training, and the start page shows before, lately and after per contrast.
  Answers go to the review log as the new mode `HVPT` (kind `HVPT`; it never touches a schedule) and the progress file gets
  a small `hvpt` record (finished runs and a tally per contrast) without a schema bump. The sounds come from the optional
  Hangul Quest Voices pack (synthetic for now, marked `source: tts` in its manifest); without it the screen says so.
  `tools/audio/hvpt_texts.py` writes the texts, `build_pack.py --hvpt` adds them to the pack. `learn/audio/Pitch` (YIN F0)
  is the base for a later pitch view.

## [3.0.0] — unreleased (release candidate 3.0.0-rc.7)

**New in rc.7: new kinds of quests, ready for level 4.**
- **Shooting and throwing:** hit a target block or a creature with a bow, crossbow, trident, snowball or egg
  (활로 과녁을 맞히세요), some from far away or with one arrow through two mobs.
- **Compass directions:** walk north, south, east or west (북쪽으로 이십 블록 가세요), or face a direction while doing
  something.
- **"Don't" works on creatures too:** 주민을 물리치지 마세요.
- **More natural numbers:** heights and distances use Sino-Korean numbers (높이 백 블록), and counts up to 99 use native
  numbers (스물네 개).
- Sequences no longer repeat the same object twice, and swimming in the ocean reads 바다에서 수영하세요.
- Six flavour lines from the last two batches now state plain facts.

**New in rc.6: level 3 is complete.**
- All 108 level-3 trails are rebuilt, and nearly half of their steps now depend on the Korean form.
- The last two batches are small mood trails: miners and smiths, caves and gems, forest herbs, foxes and bees. There
  are also three watchtowers.
- Finale lines were tightened after review: no plans, and no claims the quest doesn't check.

- **Generator wave 9** (what the mountain, ice and night and the animal batches found). A fall is a goal (`FALL`: a jump
  onto a soft block, or a fall that costs no health; 높은 곳에서 가루눈 위로 뛰어내리세요). The tool in the hand decides
  the verb on a creature (가위로 양털을 깎아요, 끈으로 소를 묶어요, 솔로 아르마딜로를 닦아요, 가위로 늑대의 갑옷을 벗겨요), armour is
  put on a creature (늑대에게 갑옷을 입히세요) and dye colours a sheep (파란색 염료로 양을 염색하세요). A name that ends in its
  own counter is counted with 개 (꿀이 든 병을 네 개), a row has the size that was built, conditions are spoken in a fixed
  order (time, weather, place, what is held), seconds of a look are spoken as 삼 초, a sequence says a shared object once
  and teaches its word in every part, a colour stands in front of the dye or the wool, and the material of a fence ring
  or a plank pillar is the kind word (울타리, 판자). Goals and conditions refuse unknown or missing fields. Rabbits,
  frogs, goats, turtles, bees, mules, parrots and axolotls can anchor a placement.
**New in rc.5:**
- **Explorers:** a spyglass on a mountain, music in the meadow, leather boots on powder snow, a cloud city, an
  observatory, the far north.
- **Wonders of the world:** the world market, a giant tree, a pyramid, a harbour, the pirate's chest, desert and
  jungle temples, a desert town.
- **Level 3 meets its grammar goal:** more than a third of its steps now depend on the Korean form (before/after,
  "don't").
- **French covers every rebuilt trail**, proofread.

**New in rc.4:** two more level-3 batches.
- The village heroes: curing a zombie villager, the iron golem, the raid patrol, enchanting, the ender eye, diamond
  gear, and the trade, post and fishing guilds.
- The hunter: bow, crossbow, spear and shield, night hunts, the wolf.

Finale lines across levels 3 and 4 now claim only what the quest checks. Pouring water reads 붓다, and rails laid in
a row read 깔다.

**New in rc.3:** the second level-3 batch: the first diamond, the jewellers' guild, amethyst and the crystal smithy,
the mine with rails and a minecart, the respawn anchor, wither skeletons and the bastion. The first four level-4
trails arrive with it (brewing, a stronger potion, a beacon base, the skull tower), using the level-4 ending
-아/어야 해요.

**New in rc.2:** the first level-3 batch. The way into the Nether is rebuilt: obsidian, the portal, piglins and
gold, the fortress and blaze rods, the bastion, striders, crying obsidian and ancient debris. Level 3 brings
"before" and "after" (-기 전에, -(으)ㄴ 후에), "don't" (-지 마세요) and "can" (-(으)ㄹ 수 있어요). The Nether
branch now starts after the lava-bucket trail. Several BATCH-015 finale lines were corrected so they claim only
what the quest checks.

Hangul Quest 3.0 rebuilds the curriculum around what a player does in the world, replaces the old review ladder
with a real spaced-repetition scheduler, and opens the Korean text to review by native speakers. It needs
Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API 0.162.0+26.3 and Java 25, like 2.1.0.

### Before you update (read this)

- **Your progress is kept.** Words, review state, streak, achievements, finished and started trails, the quest log
  and the options are all converted when you first start 3.0. Nothing is reset.
- **Saves are converted and backed up.** Progress is now stored per Minecraft account in
  `config/hangulquest/profiles/<account-id>/progress.json` (schema 4). The first time a profile opens, your old
  `config/hangulquest/progress.json` is **copied** into it; the old file is never moved, changed or deleted. Before
  an old-format file is converted the mod also writes `progress.v<N>-backup-<time>.json` next to it, and daily and
  weekly copies are kept in `backups/`. A word's old level stays in the file, and its new review card starts from
  it (a word you had marked known starts at 30 days), so nothing comes due all at once. `config.json` is upgraded
  in place (version 5).
- **Trails that were rebuilt keep their state.** The curriculum was rebuilt trail by trail. Finished trails stay
  finished; a renamed trail, step or word is mapped to its new id, and retired content is flagged, never deleted.
- **How to roll back.** Close the game, put the 2.1.0 jar back into `mods/`, and delete
  `config/hangulquest/config.json` (it is rebuilt with defaults). 2.1.0 reads the untouched
  `config/hangulquest/progress.json`, so you are back where you were before updating. What you learned in 3.0 stays
  in the `profiles/` folder and is not carried back. To go back to a state inside 3.0, copy one of the backup files
  over the profile's `progress.json`. A copy of the whole `config/hangulquest/` folder made before updating is the
  safest rollback of all.
- **Release candidate.** This is a pre-release. Please report anything that looks wrong.

### For players

**What changes in play**

- **Windows stay until you close them.** The hint, rule cards, culture notes, word cards, banners, toasts, the
  quiz prompt, the next-quest popup and the completion cards no longer time out. One press of the hint key (`H`)
  closes exactly one window, the top one; waiting windows show a "+N" badge; a repeat is not queued twice. Closing a
  window is never a hint and never changes grading. Each window shows its key (`[H] 닫기 · close`) and none covers
  the crosshair or the hotbar. *Settings → Auto-close windows* brings back the old timeouts (off by default).
- **The game fills your quest log.** Three regular quests that fit the start of the game, and a fourth slot that
  follows where you are (a snow trail in the snow, a cave trail underground). When you finish one, the next one
  follows. Quests you tick yourself are never replaced, and a quest you take out stays out for 30 minutes of play.
  *Settings → Pick quests automatically* turns this off.
- **A readable quest tree.** The tree shows what matters now: trails you can start, the ones in your log and,
  greyed out, those waiting directly behind them. Finished trails fold into one "완료 · Completed" button, and
  *Show all trails* shows the whole map. A locked trail says what it is waiting for. There is also a second view by
  life situation (house, fire and light, field, animals, ...).
- **A welcome screen and the Hangul prerequisite.** The first time you join a world, a short screen asks whether
  you can read Hangul and, if not, points to free lessons. The mod teaches words and grammar, not the alphabet.
  Skip it, ask to be reminded once, or switch it off; it never blocks a quest. It stays in the journal's Info tab.
- **The curriculum was rebuilt.** The 420 trails of 2.x were mostly generated and repeated themselves. Over 200
  trails of levels 1 and 2 were rewritten in batches of 15 around one small idea each (the first steps, the village,
  the kitchen and the feast, the smithy, the sea and harbour, animals and pets, colours and the garden, mountains,
  ice and night, and more), with the words recycled on purpose. The rebuild goes on in the 3.x releases. The tree has
  422 trails and 1,661 steps in a quest tree 22 trails deep; the word catalog has about 1,300 concepts.
- **A grammar ladder that teaches.** Each Korean level has one instruction ending: level 1 `-아/어요`, levels 2 and 3
  `-(으)세요`, levels 4 to 6 `-아/어야 해요`. You read at most one level above the grammar you have met; a new
  construction shows a two-line rule card once, the journal has a grammar book, and the review quiz has grammar cloze
  cards. Hints always stay at level 1.
- **New things to do.** Trails now ask you to craft, smelt, give, put into a chest, hold while doing something, do
  things in a sequence ("before", "after", "and then"), avoid something, place things next to or between other things
  or in a wall, cross or stair pattern, light a candle, breed animals, trade (the direction of 에게서 / 에게 is
  checked), write on a sign, hear a bell or a jukebox, wait, swim, lie down, mount, take out, go in and out, put
  armour on, shear, dye and tie animals, and jump from a height onto snow. Every sentence still says every condition.
- **A real review scheduler.** Reviews use FSRS-6 instead of the seven-step ladder, with a retention setting and 5, 10
  or 15 new words a day. Cards are multiple choice with four options, self-graded recall, syllable tiles and, for
  stable words, **typing** with a 두벌식 keyboard (also on QWERTZ). The streak is gentle: a weekly goal of four days
  and two automatic freezes a month. Mastery needs recall on three study days.
- **Reading and people.** A Books tab with graded stories (a word must be known to 98% before a story opens) and
  eight culture notes; villagers you sneak-click talk Korean, with speech-level choices (반말 to a child, 합쇼체 to a
  librarian). Verbs get their forms from a morphology engine; the word list shows Sino-Korean families.
- **Pronunciation in brackets.** Where the spelling hides the sound, the card shows the sound in Hangul
  (횃불 [홰뿔]). There is no romanisation.
- **Four interface languages.** English, German, Spanish and French (see below).
- **Privacy is unchanged.** No network code, no telemetry. The optional research export is manual only, hashes ids
  and shows a consent text.

**Languages shipped**

| Language | Interface | Quest text | State |
|---|---|---|---|
| English | complete | complete | the source language |
| Deutsch | complete | about 88% | proofread (Gemini draft, Claude second pass) |
| Español | complete | about half | proofread (Gemini draft, Claude second pass) |
| Français | complete | about a third | proofread (Gemini draft, Claude second pass); the rest of the quest text **falls back to English** |

A text a language lacks is shown in English, never in German. The quest itself is always Korean. Coverage is measured
against all 6,068 rows of the current content and moves with every new content batch. No translation has been
approved by a native speaker yet.

**The optional voices pack**

"Hangul Quest Voices" is a separate resource pack with machine-made clips for words. It is **not part of the mod jar**
and has its own download and project page. Without it the mod is silent, as before. Drop it into `resourcepacks/` and
switch it on; a listen key plays a word when the pack has a clip for it. The clips are made offline with an open
text-to-speech model and checked by a speech-recognition round trip. They are **not** native recordings. `audio` in
`config.json` switches playback off.

### Korean text and its review

- Every Korean line, authored, generated or from the word catalog (6,536 lines in this build), passes the project's
  Korean linter, and each line has an id, its context and a status.
- **Two independent AI reviewers** (Claude and Gemini, blind to each other) have checked **365 lines, about 6%**, and
  both said "fine" for all of them. The rest are lint-checked only. New content batches get a dual review; the registry
  is updated as the reports are imported.
- **No line is native-approved yet.** Nobody who speaks Korean natively has signed off on any line, and the review
  site says so ("AI checked", not "native approved").
- **The GitHub review path.** A static review site lists every Korean line with its context. Anyone with a GitHub
  account presses **Looks right** or **Suggest a correction**, which opens a prefilled issue form. A line becomes
  native-approved with two approvals from accounts at least 30 days old, or one from a trusted reviewer; nobody can
  approve their own line. The review bot turns accepted corrections into pull requests that the maintainer merges.
  A change in the Korean text sends the line back to "unreviewed". Reviewers are credited on the site.
- Translations have the same loop: a **Translation correction** form and a page per language.

### For contributors and translators

- **A content pipeline with gates.** Content comes in batches of at most 15 trails from a written brief, with the
  batch folder (manifest, lint, coverage delta, both reviews). CI runs the tests, the Korean linter, the schema
  check, a metrics ratchet (task variety and word recycling may only improve), a mass-change guard and the review
  registry check.
- **Goals and conditions are registries.** A new goal is a record in the sealed `Goal`, one type class and one line
  in `GoalTypes.ALL`; conditions work the same way. The loader is strict: an unknown or missing field makes a trail
  unloadable. The generator learned from every batch (nine waves): authored clues with conditions, general words that
  cover several items, official Minecraft names for exact tiers, counters, a fixed order of conditions, seconds in
  words, tool-dependent verbs on creatures and more. `tools/variety.py` and `tools/coverage.py` report the numbers.
- **Translation pipeline in the repository** (`tools/i18n/translate.py`, no external platform). English is the source
  and Korean is never translated. `export` writes packets, `import` checks every row (placeholders, Hangul,
  untranslated English, length, giveaway words), `proofread-export` / `-import` take a second pass, and
  `content/translations/<code>-status.json` tracks each key as `machine`, `proofread`, `community-approved` or
  `stale` (the English changed). New languages are listed with `translate.py ship`. `LangCompletenessTest` and
  `LangPickerTest` guard coverage and the settings button.
- **Progress safety.** Migrations are idempotent and run on the raw JSON; unknown fields survive; a file from a newer
  version opens read-only; there is one fixture per release and per schema change (`ProgressCompatibilityTest`),
  and `tools/new_fixture.sh <tag>` writes the one a release needs.
- **Releases.** `release.yml` checks that the tag equals `mod_version` and that the progress fixture for the tag
  exists, builds the jar, writes checksums, uses this section as the release text and marks versions with a hyphen
  (`3.0.0-rc.1`) as pre-releases. `docs/release-checklist.md` is the manual part. The voices pack is released
  separately.
- **Tests.** An end-to-end client game test (new world, welcome screen, trail, quiz, save, reload), a playtest that
  drives the goal types, and content QA in the unit tests.
- A research harness (held-out probes, per-word experiments, an analysis script) runs locally; the data leaves the machine only if the player exports it by hand.

### Fixed

- Progress can no longer be lost by an update or a crash: `progress.json` is written to a temp file, forced to disk
  and renamed; fields this version does not know are written back; a byte-order mark no longer makes it unreadable.
- Typing now works in the real game (Minecraft 26.3 reports SDL scancodes, not GLFW codes); the daily review trail
  and the streak turn over at 04:00; opening the journal no longer starts a streak day; the quiz no longer gives
  credit without recall.
- German text no longer leaks into the English interface.

[3.0.0]: https://github.com/leks2205/HangulQuest/releases/tag/v3.0.0

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
