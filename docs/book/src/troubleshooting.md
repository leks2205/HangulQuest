# Troubleshooting

## The mod does not load

- Is the jar in the right `mods` folder for the profile you started? Flatpak uses
  `~/.var/app/com.mojang.Minecraft/.minecraft/mods`.
- Does the Fabric profile really run 26.3? Check the version in the launcher's installation list.
- Is Fabric API present and for 26.3? Without it the entrypoint fails at start-up.
- JDK 25 or newer? The launcher's log says which Java it used.
- Look at `logs/latest.log` for `hangulquest` lines. A missing dependency is reported there first.

## No quests appear

- Press `K` → **Optionen** → is **Quests** on?
- The first trail starts when you enter a world; if you abandoned everything, the **Quests** tab lists
  open roots. Pick one.
- If you are on a server with a very restrictive client mod policy, no quest can finish, because the
  mod needs the client-side block events. It never asks the server for permission; it just cannot see
  what the server hides.

## A quest step does not complete

The quest log's status line is the diagnosis: it names the missing condition (`지금 낮 · 밤 필요`), the
counter (`1/3`) or the dwell percentage. Common causes:

- **Counters reset when the step starts.** Items you already carry do not count for `ACQUIRE_ITEM`
  unless you picked them up after the step began.
- **Look goals** need you to keep the crosshair on the block or mob; walking away resets the dwell.
- **`REACH_BIOME` with a tag** needs the biome family, not a specific biome.
- **Time conditions** use the world clock: night is night, even underground.

## My own questline does not show up

- The file must end in `.json`, must not start with `_`, and must be inside
  `config/hangulquest/questlines/`.
- The id must be unique. To replace a shipped trail you need `"override": true`; a duplicate id without
  it is ignored on purpose.
- Broken files are logged and skipped. Check `latest.log` for `[hangulquest]` parse warnings — they name
  the file and the field.
- The journal re-reads the folder every two seconds, so a valid file appears without a restart.

## Korean shows as boxes or is unreadable

The mod ships its own font subset. If it fails to load, the log says so and the game falls back to the
vanilla Unicode font, which renders Hangul at 6 pixels. Reinstall the jar, and if it persists, report the
log line — it names the resource path.

## The quiz answers feel random

The quiz picks distractors from words you have already met, so with a small vocabulary the choices look
similar. It gets easier as the dictionary grows. You can also turn **Auto-Quiz** off and practise only in
the journal and the sidequests.

## Progress disappeared after an update

Look in `config/hangulquest/` for `progress.v1-backup-*.json` (migration) or
`progress.json.broken-*` (unreadable file). Rename the backup to `progress.json` with the game closed to
restore it. See [Files and backups](files.md).

## I want to start over

**Optionen** → **Fortschritt zurücksetzen**, twice. It writes a backup first. For a complete reset,
delete `config/hangulquest/`.

## Something else

Open a [bug report](https://github.com/leks2205/HangulQuest/issues/new/choose) with the versions, the
questline id and the log excerpt. If it is reproducible, say what you did twice — once is often luck.
