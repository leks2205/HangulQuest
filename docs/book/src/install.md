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

## Steps

1. **Install Fabric.** Run the [Fabric installer](https://fabricmc.net/use/installer/) for Minecraft
   26.3 and start the game once with the new profile so the folder exists.
2. **Download the mod.** Take `hangulquest-1.0.0.jar` from the
   [latest release](https://github.com/leks2205/HangulQuest/releases/latest).
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
# hangulquest-1.0.0.jar: OK
```

## First five minutes

- The quest log sits in the top-left corner and lists every quest you have open: one Korean sentence
  each, a progress bar where the goal counts something, and a status line that says what is still
  missing (`지금 낮 · 밤 필요` — "it is day now, night is needed"). `V` moves the highlight; `H` helps
  with the highlighted quest.
- Press `H` whenever a word is unknown. You get the same task in easier Korean, an explanation of the
  word, and a two-question sidequest. Finishing it brings the quest back.
- Press `K` for the journal. The **Quests** tab shows the whole tree: what you finished, what is open
  and what the locked branches are waiting for.
- If you want to see the German line for each quest, open **Optionen** in the journal and switch on
  *Deutsch in Quests*. The quest itself is always Korean, so the Korean is what you learn to read.

## Updating

Replace the jar in `mods` with the new one. Progress lives in `config/hangulquest/` and survives an
update; the mod backs the file up before it migrates a format. If a new version needs a newer
Minecraft or Fabric API, the release notes say so.

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
- **German interface.** The UI strings are German today, the quest text Korean. An English UI is on
  the [contribution list](../CONTRIBUTING.md).
- **Not in scope:** pronunciation audio, romanisation, Korean keyboard input (every answer is
  multiple choice), and anything that would need the server's cooperation.
