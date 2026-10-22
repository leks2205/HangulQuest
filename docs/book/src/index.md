# Hangul Quest

**Learn Korean by playing Minecraft.**

Hangul Quest is a client-side Fabric mod for Minecraft 26.3. It turns the world into a language
course: a Korean sentence appears in the corner of your screen, you work out what it asks, and the
game verifies that you did it. When a word blocks you, the mod explains it in easier Korean and
makes you practise it before the quest continues.

There is no chat, no pop-up quiz during a fight, no server component and no network traffic. It
works in single-player and on any server, with or without other players.

<img src="screenshots/korean-level5.webp" alt="A late questline asking for a torch, in literary Korean" />

## What is in it

- **422 questlines** and 1,661 steps, arranged in a tree 22 levels deep, with 31 kinds of goal. Over 200 trails were
  rebuilt around small themes (the village, the kitchen, the smithy, the harbour, animals, colours) and recycle words
  on purpose. The Korean gets harder as you go deeper through six levels, each with one instruction ending:
  `-아/어요`, then `-(으)세요`, then `-아/어야 해요`. A new construction shows a rule card once.
- **A quest log** instead of a single tracker: the HUD lists up to four quests with their Korean objective, their
  conditions and a live count (three regular slots and one for the place you are in). The game fills the slots for
  you and picks the next quest when you finish one; tick quests in the journal to choose yourself, or switch "Pick
  quests automatically" off. Only tracked quests advance.
- **Windows that wait for you.** Hints, rule cards and banners stay until you press the hint key (`H`), one window per
  press, and closing one is never a hint.
- **A hint that teaches.** `H` restates the task in level-1 Korean and explains the blocking word,
  then starts a two-question sidequest on it. The original quest waits until you have answered both.
- **A journal** (`K`) drawn as a worn notebook: your dictionary, due words, achievements, a quest tree that shows
  what matters now, a grammar book, stories and the settings.
- **Real spaced repetition** (FSRS-6), with four-option cards, recall, syllable tiles and typed answers on a
  built-in Korean keyboard for words you know well.
- **Vocabulary from three places**: about 1,300 concepts (hand-written ones and one generated entry per rarer object),
  the game's own Korean names, and your own words imported from [Lute](https://github.com/LuteOrg/lute-v3).
- **Four interface languages**: English, German, Spanish and French. The quest itself is always Korean.
- **Checked Korean.** Every Korean line passes a linter, and a part of it has been read by two AI reviewers. No
  line has been approved by a native speaker yet; [you can help](help-review.md).
- **Content as data.** Questlines are JSON files, re-read every two seconds, so you can write your own
  without touching Java or restarting the game.

## How to read this guide

| If you want to … | Read |
|---|---|
| install the mod and start playing today | [Installing](install.md), [Playing](playing.md) |
| understand hints, the sidequest and the review system | [Hints and sidequests](hints.md), [Review, streak and achievements](review.md) |
| write your own questlines | [Authoring guide](questlines.md), [Attribute reference](questline-reference.md) |
| see what content ships | [The quest tree](quest-tree.md) |
| fix something that broke | [Troubleshooting](troubleshooting.md) |
| know what the mod stores | [Files and backups](files.md), [Privacy](privacy.md) |

## A quest, in one sentence

<div lang="ko" style="font-size:1.3rem; margin:1rem 0;">해변에서 모닥불을 놓아요.</div>

*"Place a campfire on the beach."* — the place is part of the sentence, the mod checks the block and
the biome, and the tracker answers "why not yet?" with the live status underneath. That is the whole
design: **the Korean is the interface**.

## Status

Version 3.0.0 (release candidates until it is final), for Minecraft 26.3 with Fabric Loader 0.19.5+ and Fabric API
0.162.0+26.3 on Java 25. The interface follows your game language (English, German, Spanish, French); the quest text is
Korean, with an optional line in your language. See
[Compatibility](install.md#compatibility) for what is deliberately out of scope.
