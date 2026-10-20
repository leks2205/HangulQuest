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

- **414 questlines** and 1,505 steps, arranged in a tree 22 levels deep. The Korean gets harder as you
  go deeper: polite sentences at the top, obligation, emphatic forms, double negatives and finally
  literary Korean near the crown.
- **A quest log** instead of a single tracker: every open quest with its Korean objective, its
  conditions and a live progress bar, up to six at a time.
- **A hint that teaches.** `H` restates the task in level-1 Korean and explains the blocking word,
  then starts a two-question sidequest on it. The original quest waits until you have answered both.
- **A journal** (`K`) drawn as a worn notebook: your dictionary, due words, achievements, the trail
  tree and the options.
- **Vocabulary from three places**: 293 curated concepts, the game's own Korean/German names, and your
  own words imported from [Lute](https://github.com/LuteOrg/lute-v3).
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

Version 1.0.0, for Minecraft 26.3 with Fabric Loader 0.19.5+ and Fabric API 0.162.0+26.3 on Java 25.
The interface is German; the quest text is Korean, with an optional German line. See
[Compatibility](install.md#compatibility) for what is deliberately out of scope.
