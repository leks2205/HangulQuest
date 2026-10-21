# Privacy

Hangul Quest is built so that the question "what does it send?" has a short answer: nothing.

## No network

- No telemetry, no analytics, no crash reporting, no update check.
- No sockets are opened, no HTTP client exists in the jar. You can run it with the network cable pulled.
- Quests are verified from the client's own view of the world. Nothing is requested from the server and
  nothing is reported to it.

## Links

The welcome screen and the journal's *About reading Hangul* page list a few external Hangul lessons.
A link opens in your browser only when you click it and confirm Minecraft's own "open this link?"
screen. The mod itself never contacts those sites.

## What is stored locally

| File | Content |
|---|---|
| `config/hangulquest/config.json` | your settings |
| `config/hangulquest/profiles/<account>/progress.json` | words met, review schedule, due dates, streak days, achievements, trail state |
| `config/hangulquest/profiles/<account>/reviews.ndjson` | the review log: one line per answer or encounter (word, time, how you answered, how long it took, where it happened) |
| `config/hangulquest/vocab/lute-vocab.csv` | your Lute export — read, never written |
| `config/hangulquest/questlines/*.json` | the questlines (shipped copies plus your own) |

That is all. There is no account, no identifier, no machine fingerprint.

## The review log

The scheduler needs to know what you answered and when, so the mod keeps a log next to your progress.
It is a plain text file you can open, copy or delete. Each month's lines are packed into
`reviews-YYYY-MM.ndjson.gz` in the same folder. The log stays on your computer: nothing in the mod reads it
to send it anywhere, and it holds no name, no chat and no coordinates. If `progress.json` is ever lost, the
log can rebuild the review schedule. Deleting it only costs you that safety net and your own history.

## Logs

The mod logs with the `[hangulquest]` prefix: which trail started, which file was reloaded, which
questline failed to parse. It does not log chat, player names, server addresses or coordinates. If you
attach a log to an issue, check it anyway — Minecraft's own log lines around it do contain your instance
path.

## Screenshots

The screenshots in this repository and in the guide were taken in a clean test world. If you post a
screenshot of a quest and would rather not show your coordinates or your server's chat, crop them; the
mod's own UI never draws either.

## Lute

The import reads a file you exported and does not talk to Lute or to any server. If you disable
**Lute-Import** in the journal, nothing is read at all.

## Changes

This page describes 1.0.0. If a future version ever needs the network for anything, it will be opt-in,
documented here and in the changelog before it ships.
