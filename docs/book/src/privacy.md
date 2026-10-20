# Privacy

Hangul Quest is built so that the question "what does it send?" has a short answer: nothing.

## No network

- No telemetry, no analytics, no crash reporting, no update check.
- No sockets are opened, no HTTP client exists in the jar. You can run it with the network cable pulled.
- Quests are verified from the client's own view of the world. Nothing is requested from the server and
  nothing is reported to it.

## What is stored locally

| File | Content |
|---|---|
| `config/hangulquest/config.json` | your settings |
| `config/hangulquest/progress.json` | words met, SRS levels, due dates, streak days, achievements, trail state |
| `config/hangulquest/vocab/lute-vocab.csv` | your Lute export — read, never written |
| `config/hangulquest/questlines/*.json` | the questlines (shipped copies plus your own) |

That is all. There is no account, no identifier, no machine fingerprint.

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
