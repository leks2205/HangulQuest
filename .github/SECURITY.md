# Security policy

Hangul Quest is a **client-side** Minecraft mod. It opens no sockets, has no server component and
sends nothing anywhere: no telemetry, no update check, no analytics. What it reads are the game's own
classes, your world and three files in your instance (`config/hangulquest/config.json`,
`progress.json`, `vocab/lute-vocab.csv`), all of which it writes only on your machine.

That makes the realistic attack surface small but not empty:

- parsing a **questline JSON** file that someone else gave you (the folder is hot-reloaded, so a
  crafted file is live within two seconds),
- parsing an imported **Lute CSV** and the game's **language files** at start-up,
- rendering text with the bundled font subset.

If you find a crash, a hang, or an input that makes the mod read or write files outside
`config/hangulquest/`, that is a security bug, not a content bug.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private form:
**Security → Report a vulnerability**, or <https://github.com/leks2205/HangulQuest/security/advisories/new>.

A useful report says:

- the mod version (the jar's name) and your Minecraft, Fabric Loader and Fabric API versions,
- the platform and the Java version,
- the smallest file or input that triggers it,
- what an attacker could achieve: a crash, a hang, reading or writing files, or worse,
- the log excerpt around the failure, with your home directory path removed if you like.

## Supported versions

Only the latest release is supported. Fixes ship as a new release; the previous one is not patched.
Content-only problems (a quest that cannot be finished, a wrong particle, an unclear clue) are
regular bugs and belong in a normal issue.
