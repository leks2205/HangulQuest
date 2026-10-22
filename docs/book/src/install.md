# Installing

## What you need

| | |
|---|---|
| **Minecraft** | Java Edition **26.3** |
| **Mod loader** | [Fabric Loader](https://fabricmc.net/use/installer/) **0.19.5** or newer |
| **Java** | **25** (the version 26.3 already runs on) |
| **Fabric API** | **0.162.0+26.3** or newer, for the same game version |

Hangul Quest is a **client** mod. Install it in your own `mods` folder, never on a server; it never
sends a packet, so a server without it behaves exactly the same.

## Before you start: reading Hangul

Hangul Quest assumes you can already **read Hangul**, the Korean alphabet. It teaches words and
grammar, not the alphabet. Most people learn Hangul in a few hours. The first time you join a world a
short welcome screen asks whether you can read `고양이가 나무 위에 있어요.`; if you cannot yet, it
lists free lessons. You can skip it, ask to be reminded once, or switch it off for good, and it never
blocks a quest. Open it again any time from the journal's Info tab (*About reading Hangul*).

Free ways to learn Hangul:

- [How to Study Korean, Unit 0: Learn How to Read Hangul](https://www.howtostudykorean.com/unit0/) (English)
- [KoreanClass101: Learn the Korean Alphabet (Hangul)](https://www.koreanclass101.com/korean-alphabet)
  (English; the lessons are readable without an account, the PDF chart needs a free one)
- [Ryan Estrada: Learn to Read Korean in 15 Minutes](https://www.ryanestrada.com/learntoreadkoreanin15minutes/)
  (English, a short comic)
- [Koreanischkurs: Einführung in Hangeul](https://koreanischkurs.com/sprache/grundlagen/koreanisches-alphabet-lernen/)
  (Deutsch)

## Steps

1. **Install Fabric.** Run the [Fabric installer](https://fabricmc.net/use/installer/) for Minecraft
   26.3 and start the game once with the new profile so the folder exists.
2. **Download the mod.** Take `hangulquest-<version>.jar` from the
   [releases page](https://github.com/leks2205/HangulQuest/releases). Release candidates (`3.0.0-rc.N`) are
   marked as pre-releases and do not show up under "latest".
3. **Download Fabric API** for 26.3 from [Modrinth](https://modrinth.com/mod/fabric-api) or CurseForge.
4. **Copy both jars** into your `mods` folder:
   - Windows: `%appdata%\.minecraft\mods`
   - Linux: `~/.minecraft/mods` (or `~/.var/app/com.mojang.Minecraft/.minecraft/mods` for the Flatpak)
   - macOS: `~/Library/Application Support/minecraft/mods`
5. **Start Minecraft** with the Fabric profile and enter any world. The first questline starts by
   itself; `K` opens the journal, `H` shows a hint.

## Checking the download

Each release page carries a `SHA256SUMS` file with the hash of every asset:

```bash
sha256sum -c SHA256SUMS
# hangulquest-<version>.jar: OK
```

## First five minutes

- The quest log sits in the top-left corner. The game picks up to four quests for you (three regular ones and one
  that follows where you are); each shows one Korean sentence, a progress bar where the goal counts something, and a status line that says what is still
  missing (`지금 낮 · 밤 필요` — "it is day now, night is needed"). `V` moves the highlight; `H` helps
  with the highlighted quest.
- Press `H` whenever a word is unknown. You get the same task in easier Korean, an explanation of the
  word, and a two-question sidequest. Finishing it brings the quest back. When a window is open (a hint, a rule
  card, a banner), `H` closes it first; windows stay until you do.
- Press `K` for the journal. The **Quests** tab shows the whole tree: what you finished, what is open
  and what the locked branches are waiting for.
- The interface follows your Minecraft language (English, German, Spanish or French; anything else gets English);
  **Settings** → *Language* switches it explicitly.
- If you want a translation line under each quest, switch on *Translations in quests* in the same tab. It is off by default: the quest itself is always Korean, so the Korean is
  what you learn to read.

## Updating

Replace the jar in `mods` with the new one. Progress lives in `config/hangulquest/` and survives an
update; the mod backs the file up before it migrates a format. If a new version needs a newer
Minecraft or Fabric API, the release notes say so.

**From 2.x to 3.0:** progress is converted when you first start the game. It now lives per Minecraft account in
`config/hangulquest/profiles/<account>/`; your old `progress.json` is copied there and never changed or deleted. To
roll back, put the 2.1.0 jar back and delete `config/hangulquest/config.json`; the CHANGELOG has the full notes. Copy
the `config/hangulquest/` folder before updating if you want to be sure.

## Uninstalling

Delete the jar. Your progress, settings and imported vocabulary stay in `config/hangulquest/` — delete
that folder too if you want a clean slate. Nothing was ever written outside it.

## Compatibility

- **Minecraft 26.3 only.** The mod is built against named 26.3 classes; it will not load on 1.21.x or
  on a future 26.4 without changes.
- **Client-side.** It works on servers that do not have the mod, in single-player and in LAN worlds.
  Other players see nothing.
- **Other client mods.** It draws its own HUD and journal and does not replace vanilla screens, so it
  coexists with HUD mods; if two trackers overlap, move the quest tracker to another corner in the
  journal's options.
- **Languages.** English, German, Spanish and French interfaces; a text a language lacks is shown in English. Spanish
  and French cover only part of the quest text so far.
- **Voices.** Pronunciation audio is an optional separate resource pack ("Hangul Quest Voices"); without it the
  mod is silent.
- **Not in scope:** romanisation (Hangul is a prerequisite), speech recognition, and anything that would need
  the server's cooperation.
