<div align="center">

<img src=".github/banner.png" alt="Hangul Quest" width="820" />

# 한글 탐험 · Hangul Quest

**Learn Korean by playing Minecraft.** Korean instructions, easier-Korean hints that start a sidequest, and 420 authored quest trails in a tree that climbs from "break three stone" to literary Korean.

Client-only Fabric mod. No server component, no packets, no chat, no telemetry.

[**Download**](../../releases/latest) · [**Guide**](https://leks2205.github.io/HangulQuest/) · [Features](#features) · [Install](#install) · [Writing questlines](#writing-your-own-questlines) · [Report a bug](../../issues/new/choose)

![Minecraft](https://img.shields.io/badge/Minecraft-26.3-3c8527)
![Fabric](https://img.shields.io/badge/Fabric%20Loader-0.19.5%2B-dbbd8b)
![Java](https://img.shields.io/badge/Java-25-b7410e?logo=openjdk&logoColor=white)
![Questlines](https://img.shields.io/badge/questlines-420-16a34a)
![License](https://img.shields.io/badge/license-MIT-blue)
![Telemetry](https://img.shields.io/badge/telemetry-none-16a34a)
[![CI](https://github.com/leks2205/HangulQuest/actions/workflows/ci.yml/badge.svg)](https://github.com/leks2205/HangulQuest/actions/workflows/ci.yml)

</div>

<p align="center">
  <img src="docs/screenshots/korean-level5.webp" alt="A late questline asking for a torch, in literary Korean" width="900" />
</p>

---

## Contents

- [About](#about)
- [Features](#features)
- [Screenshots](#screenshots)
- [Install](#install)
- [Keys](#keys)
- [How a quest works](#how-a-quest-works)
- [The journal](#the-journal)
- [Writing your own questlines](#writing-your-own-questlines)
- [Project layout](#project-layout)
- [Building from source](#building-from-source)
- [Compatibility and limits](#compatibility-and-limits)
- [Privacy](#privacy)
- [Contributing](#contributing)
- [Credits](#credits)
- [Legal notes](#legal-notes)
- [License](#license)

## About

Most language-learning mods put vocabulary lists in a book and ask you to read them. Hangul Quest does the opposite: it hides the vocabulary inside the world. A quest is a Korean sentence in the corner of your screen —

```
돌을 세 개 캐요.
```

*"Mine three stone."* — and the world is where you prove you understood it. Finish the step and the next sentence appears; finish the trail and a ritual finale (a torch on a mountain at night, a bell rung at dawn) closes it with a gold card, fireworks and a badge.

When a word blocks you, `H` restates the same task in **simpler Korean**, explains the unknown word in easy Korean, and hands you a two-question **sidequest**. The original quest waits until you have answered both. Nothing is ever translated into a language you can read at a glance — an English or German line only appears if you switch it on.

Everything happens on your own client. The mod never talks to a server, so it works unchanged in single-player and on any server, with or without other players.

## Features

**Quest trails**
- **420 questlines**, one JSON file each: a tree **22 levels deep**, 1,524 steps in total, 38 roots, branches that merge again before they continue.
- **Every action is used once.** No two steps in the whole mod ask for the same thing: one quest collects wool, another shears a sheep, another dyes it purple — the sweep replaced 1,109 duplicated goals with unique ones.
- **Chains that build a thing.** The colour branch is a real path: red, blue and yellow dye quests unlock the purple and orange wool quests, and those unlock the rainbow banner.
- Each trail is a small Schnitzeljagd — three to five clues that build on each other, then a **ritual finale** that is always completable. No structure, chest, coordinate or rare biome is ever required.
- **A quest log, not a single tracker.** Tick quests in the journal and they appear top-left as a short, background-free list: title, current Korean objective, the count right after it (`1/3`) and a status line when a condition is missing. Up to six, one of them highlighted; `V` cycles the highlight.
- **Only checked quests advance.** An unticked quest keeps its saved progress but is paused, so nothing completes behind your back while you are reading something else. The highlighted quest is the one the hint key and the sidequest work on.
- Finishing a trail chains onward automatically: the only unlocked child starts, or a HUD card offers up to five follow-ups (`1`–`5`, the first one after 60 s). Leaving a trail pauses it mid-step; coming back resumes that exact step, counter and sidequest state.

**Hints and sidequests**
- `H` restates the goal in **level-1 Korean** (always simpler than the quest) plus a one-line description of the blocking word, also in easy Korean.
- An unknown word starts a **two-question sidequest** (Korean → meaning, meaning → Korean) and freezes the original step. A wrong answer shows the word card, drops to two options and lets you retry; nothing is penalised.
- Words you already know — a mastered word in the mod, or Lute status 99 — get the hint without a sidequest.
- Quiz cards hide the item icon behind a `?` until you answer, so the picture never gives the answer away.

**Vocabulary**
- 1,132 concepts: 293 hand-written ones (blocks, tools, food, animals, biomes, verbs) with Korean, German, an easy-Korean description and aliases, plus one entry per rarer object, generated from the game's name files.
- Every vanilla item, block, entity and biome name that has a Korean, English and German name, generated from the game's own language files.
- **Lute import**: drop your Lute export at `config/hangulquest/vocab/lute-vocab.csv` and your own words and learning states become quest vocabulary — read-only, the mod never writes to your file.

**Learning layer**
- Leitner-style spaced repetition with due words, a streak and fifteen achievements; review cards appear in-game only when you are standing still, so nothing interrupts a fight.
- A daily **adaptive review trail** built from the words due today, seeded by world and date so it is stable for the day.
- Progress that survives restarts: hints used, sidequest state, partial counters and badges are all stored per step.

**Journal**
- A journal that is a worn notebook: leather cover, stitched spine, ribbon, cream paper and flat ink text instead of the shadowed Minecraft font.
- Six tabs: info, dictionary, learning, achievements, the trail tree and options.
- Translations are hidden by default; the eye in the corner reveals them.

**Content as data**
- Questlines are plain JSON under `config/hangulquest/questlines/`, re-read every two seconds: a new file appears in the journal **without restarting the game**, a broken file is logged and ignored, and the shipped version stays active.
- A JSON schema, a template, a scaffolding script (`tools/new_questline.py`), an authoring guide and a validation test guard every change.
- Korean difficulty follows the tree: polite `-아/어요` at the top, then obligation, emphatic, double negative and finally literary `-지 않고서는 … 수 없어요`. Hints always stay at the easiest level.

**Presentation**
- Three celebration tiers — step, sidequest, trail — with cards, sounds and client-side particles that the server never sees.
- **English or German interface** (English by default; *Optionen* → *Sprache* switches to Deutsch), and a translation line in quest text, banners and celebrations that stays **off** until you enable it (*Optionen* → *Übersetzungen in Quests*).
- A bundled Korean font (a Noto Sans CJK KR subset), so Hangul looks the same everywhere instead of falling back to the 6-pixel unifont.

## Screenshots

<table>
  <tr>
    <td><img src="docs/screenshots/hint.webp" alt="Hint with easier Korean and a word description" /></td>
    <td><img src="docs/screenshots/sidequest.webp" alt="Sidequest about the blocking word" /></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/journal.webp" alt="Journal as a worn notebook" /></td>
    <td><img src="docs/screenshots/journal-translations.webp" alt="Journal with translations switched on" /></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/quest-log.webp" alt="Background-free quest log with three quests" /></td>
    <td><img src="docs/screenshots/celebration-trail.webp" alt="Trail completed celebration" /></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/wordcard.webp" alt="Word card while looking at a block" /></td>
    <td><img src="docs/screenshots/journal-quest-log.webp" alt="Quest tab with the Im Log checkbox" /></td>
  </tr>
</table>

## Install

Hangul Quest needs **Minecraft 26.3** (Java Edition), **Fabric Loader 0.19.5 or newer**, **Fabric API 0.162.0+26.3 or newer** and **Java 25**. It is client-side: install it in your own `mods` folder, not on the server.

1. Install [Fabric](https://fabricmc.net/use/installer/) for Minecraft 26.3.
2. Download `hangulquest-<version>.jar` from the [latest release](../../releases/latest) and drop it into `.minecraft/mods/`.
3. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3 and put it in the same folder.
4. Start the game and enter any world — the first questline starts by itself.

Every release ships a `SHA256SUMS` file; on Linux you can verify the download with `sha256sum -c SHA256SUMS`.

**You need to be able to read Hangul (the Korean alphabet) first.** Hangul Quest teaches words and grammar, not the alphabet, and most people learn Hangul in a few hours. The first time you join a world, a short welcome screen asks whether you can read it and, if not, points to free lessons (open it again any time from the journal's Info tab: *About reading Hangul*):

- [How to Study Korean, Unit 0](https://www.howtostudykorean.com/unit0/) (English)
- [KoreanClass101: Learn the Korean Alphabet](https://www.koreanclass101.com/korean-alphabet) (English)
- [Ryan Estrada: Learn to Read Korean in 15 Minutes](https://www.ryanestrada.com/learntoreadkoreanin15minutes/) (English, comic)
- [Koreanischkurs: Einführung in Hangeul](https://koreanischkurs.com/sprache/grundlagen/koreanisches-alphabet-lernen/) (Deutsch)

## Keys

All keys can be changed in **Options → Controls → Key Binds → 한글 탐험**.

| Key | Action |
|---|---|
| `K` | Open the journal |
| `H` | Show the hint (and start the sidequest if the word is new) |
| `J` | Review due words now |
| `F8` | Toggle the HUD |
| `V` | Cycle the highlighted quest in the log |
| `1`–`3`, `1`–`5` | Answer in a quiz or sidequest, pick the next trail |

## How a quest works

1. **Clue.** The quest log shows the objective of every quest you have open — one Korean sentence each. Every condition is spoken inside the sentence — `해변에서 모닥불을 놓아요.`, "place a campfire *on the beach*" — so text and check can never disagree.
2. **Do it.** The mod verifies the goal locally: blocks broken or placed, items gained or eaten, a block or mob looked at long enough, a biome, a dimension, an altitude, a right-click, an interaction. Twelve goal types, seven condition types, nothing that needs the server's trust.
3. **Stuck?** `H`. Simpler Korean, the word explained, a sidequest so it sticks.
4. **Done.** A step card names the next clue. The finale ends with a gold card, a fanfare, fireworks and a badge; the next trail starts.
5. **Later.** Due words come back as flashcards, and the generated review trail turns them into a quest again.

The full model — every goal type, every condition, how the quest log words its status lines, what the tests enforce — is in the **[guide](https://leks2205.github.io/HangulQuest/)**.

## The journal

`K` opens the notebook. It is drawn by the mod, not a vanilla screen: leather cover, sewn spine, cream paper and a flat ink font, so Korean stays readable at small GUI scales.

| Tab | What is in it |
|---|---|
| **Info** | Words discovered and mastered, due today, streak, level, word of the day |
| **Words / Wörter** | Your dictionary: Korean, the translation, status, and where the word came from (curated, vanilla, Lute) |
| **Lernen** | Flashcards for due words, with the review trail as a shortcut |
| **Erfolge** | Fifteen achievements with progress |
| **Quests** | The trail tree and a detail card with the **Im Log** checkbox: ticked quests appear on the HUD and advance, unticked ones wait |
| **Settings / Optionen** | Language, HUD, translations in quests, quiz size, hints, sound and the quest folder |

## Writing your own questlines

A questline is one JSON file. No Java, no rebuild:

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
  "requires": { "minWordsKnown": 0, "dimension": "minecraft:overworld" },
  "steps": [
    {
      "id": "s1",
      "teach": ["stone"],
      "goal": { "type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 3 },
      "clue": { "ko": "회색 돌을 세 개 캐요.", "de": "Baue drei harte, graue Dinge ab." },
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

- Drop the file into `config/hangulquest/questlines/`. The folder is created on first start and already holds every shipped trail plus `_template.json`. A new file appears in the journal within two seconds; add `"override": true` with an existing `id` to replace a shipped trail.
- `clue` and `hint` are optional — without them the Korean sentence is generated from the goal, with the right particles, counters and time prefixes.
- Korean fields must be Hangul-only. The validator rejects Latin letters, romanisation and emoji.
- Run `./gradlew test` after editing shipped content: it loads every file, checks the tree for dead ends, verifies that `koreanLevel` matches the depth band, and that every condition is spoken in the sentence.

```bash
python3 tools/new_questline.py meinpfad --words stone,dirt --tier 1 \
  --title-ko "나의 길" --title-de "Mein Pfad"
```

References: [`docs/questline.schema.json`](docs/questline.schema.json), the [authoring guide](docs/questlines.md), the [attribute reference](docs/questline-attributes.md) and the [guide's chapter on writing](https://leks2205.github.io/HangulQuest/questlines.html).

## Project layout

| Path | What's in it |
|---|---|
| `src/main/java/…/quest` | Engine, loader, validator, sentence builder, Korean grammar, TOPIK bands, adaptive trail |
| `src/main/java/…/learn` | Progress store, spaced repetition, streak, achievements |
| `src/main/java/…/sense` | World sensors (blocks, items, look, biome, dimension, height) and the safety gate |
| `src/main/java/…/ui` | Quest log HUD, journal screen, celebrations, Korean text drawing |
| `src/main/java/…/data` | Concept catalog, vanilla names, Lute CSV import |
| `src/main/resources/assets/hangulquest/data` | `concepts.json`, `names.json` and 420 questline files |
| `docs/` | Authoring guide, attribute reference, JSON schema, screenshots, the mdBook guide |
| `tools/` | Questline scaffolding, font subsetting, name extraction, quest tree report |
| `src/test/java` | 15 test classes: engine, grammar, SRS, store, content validation and content QA |

## Building from source

You need JDK 25; the wrapper pins Gradle 9.7.1 and Loom 1.18.

```bash
git clone https://github.com/leks2205/HangulQuest.git
cd HangulQuest
./gradlew test          # engine tests plus validation of all 420 questlines
./gradlew build         # jar in build/libs/
./gradlew runClient     # development client
```

To look at the UI without playing, start the dev client with `-Dhangulquest.selftest=true`: it opens a world, shows a hint and the journal, and writes `[selftest]` lines to the log.

Building the guide locally:

```bash
cargo install mdbook --version 0.5.4      # or download the release binary
python3 tools/quest_tree.py               # regenerate the tree report
mdbook build docs/book                    # output in docs/book/book
```

## Compatibility and limits

- Built for exactly **Minecraft 26.3**. Other versions are untested; the mod uses named 26.3 classes and will not load elsewhere without changes.
- **Client-only.** It works in single-player and on servers that do not have it. Installing it on a server does nothing.
- Quest text is always Korean. The interface and the optional translation line are English by default, with German one switch away; per-quest line authoring in more languages is the obvious next step; see [CONTRIBUTING.md](CONTRIBUTING.md).
- No pronunciation audio, no romanisation and no Korean keyboard input — every answer is multiple choice.
- Goals are limited to what a client can see. There is no "find this structure" or "open this chest" requirement, which is what keeps every trail finishable.
- Progress lives in `config/hangulquest/progress.json`. It is backed up before a migration and a corrupt file is renamed rather than overwritten, but it is not synced between machines.

## Privacy

- No telemetry, no analytics, no account, no network requests. The mod talks to nothing.
- What it stores stays in your instance: `config/hangulquest/config.json`, `progress.json` and `vocab/lute-vocab.csv`.
- The mod's log lines contain quest ids and file paths, never chat, player names or server addresses.
- Screenshots in this repository were taken in a clean test world.

## Contributing

Bug reports, questlines and code are welcome. Questline contributions need no Java at all — see [CONTRIBUTING.md](CONTRIBUTING.md) and the [authoring guide](docs/questlines.md). Run `./gradlew test` before opening a pull request; content failures show up there, not in the game.

## Credits

- **[Fabric](https://fabricmc.net/)** and **Fabric API** — the loader and the client hooks the quest engine builds on.
- **Noto Sans CJK KR** (SIL Open Font License 1.1) — the bundled Korean font subset. Details in [NOTICE.md](NOTICE.md).
- **[Lute](https://github.com/LuteOrg/lute-v3)** — the vocabulary import reads Lute's CSV export format; Lute itself is not bundled.
- Vanilla Korean, English and German item, block, entity and biome names come from Minecraft's own language files; the quest text built on them is original.

## Legal notes

- **Unofficial.** Hangul Quest is an independent project. It is not affiliated with, endorsed or sponsored by Mojang Studios or Microsoft.
- **Minecraft** is a trademark of Mojang Synergies AB. The mod contains no Minecraft code or assets; it requires a legitimate copy of the game.
- Some questlines are themed around vanilla **advancements**. Only their names and completion conditions are referenced; no advancement data or assets are redistributed.
- The bundled font is a subset of Noto Sans CJK KR and remains under the SIL Open Font License; the reserved font name is not used.

## License

MIT — see [LICENSE](LICENSE). You may use, change and redistribute the mod and its questlines, including in modpacks.

The name "Hangul Quest", the Korean title 한글 탐험 and the banner are not covered by the licence. If you publish a modified version, please give it a different name.
