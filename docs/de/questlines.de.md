# Questlinien — Format und Workflow

Eine Questlinie ist eine kleine Schnitzeljagd: mehrere Hinweise, die aufeinander aufbauen, und ein
Ritual am Ende. Alles steht in einer JSON-Datei unter
`src/main/resources/assets/hangulquest/data/questlines/`.

## Schnellstart

```bash
python3 tools/new_questline.py steinpfad2 --words stone,cobblestone --tier 1 \
  --title-ko "돌의 두 번째 길" --title-de "Der zweite Steinpfad"
./gradlew test
```

Das Skript legt die Datei an und aktualisiert `index.json`. Der Build erzeugt den Index beim
`processResources`-Schritt ohnehin neu; `index.json` wird also nicht von Hand gepflegt.

## Externer Ordner (ohne Neustart)

Die Linien liegen als einzelne JSON-Dateien in `config/hangulquest/questlines/` (beim ersten Start
aus dem Jar kopiert). Der Mod liest den Ordner alle zwei Sekunden neu; neue/geänderte Dateien
erscheinen sofort im Spiel. Gleiche `id` wird ignoriert, solange die Datei nicht `"override": true` setzt; opt-in-Dateien überschreiben die mitgelieferte Linie. Kaputte Dateien werden
ignoriert (die eingebaute Version bleibt). Vorlage: `_template.json`.

## Bedingungen im koreanischen Satz

`conditions` werden immer mitgesprochen: `TIME` (밤에/낮에/새벽에/저녁에), `WEATHER`, `Y_RANGE`
(„땅 아래에서", „120블록보다 높은 곳에서"), `IN_BIOME` („해변에서"), `IN_DIMENSION` („지옥에서",
„엔더에서"), `NEAR_ENTITY`/`NEAR_BLOCK` („좀비 근처에서"). Schritte mit Bedingungen bekommen ihren
Satz generiert, damit keine Bedingung im Text fehlt; `REACH_HEIGHT` unter der Erde benutzt „내려가다".

## Aufbau einer Datei

```json
{
  "schema": 1,
  "id": "steinpfad",
  "titleKo": "돌의 길",
  "titleDe": "Der Steinpfad",
  "tier": 1,
  "concepts": ["stone", "cobblestone", "coal", "torch"],
  "requires": { "minWordsKnown": 0, "dimension": "minecraft:overworld",
                "completed": ["steinpfad"] },
  "steps": [
    {
      "id": "s1",
      "teach": ["stone"],
      "goal": { "type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 3 },
      "conditions": [],
      "clue": { "ko": "단단하고 회색인 것을 세 개 캐요.", "de": "Baue drei harte, graue Dinge ab." },
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

- `clue` und `hint` sind optional. Fehlen sie, wird der koreanische Satz aus dem Ziel erzeugt
  (korrekte Partikel, Zählwörter, Uhrzeit-Präfixe). Eigene Sätze sind für Rätselstimmung da.
- `teach` nennt die Wörter, die der Schritt einführt; sie müssen in `concepts.json` existieren.
- Das `finale` ist der letzte Schritt. Es wird immer gebraucht.
- `requires.minWordsKnown` vergleicht mit der Zahl der **entdeckten** Wörter (damit die Kette spielbar bleibt).
- `koreanLevel` (1–5) steuert die Schwierigkeit der generierten Sätze. Er steigt mit der Tiefe im Baum:
  1 höflich (`…캐요.`), 2 Pflicht (`…캐야 해요.`), 3 nachdrücklich (`…캐야만 해요.`),
  4 doppelte Verneinung (`…캐지 않으면 안 돼요.`), 5 literarisch
  (`…캐지 않고서는 다음 단서를 찾을 수 없어요.`, im Finale `…이 길을 끝낼 수 없어요.`).
  Der Hint bleibt immer auf Stufe 1.
- `order` bestimmt die Reihenfolge im Journal und welche Linie beim Weltstart automatisch beginnt
  (kleinere Zahl zuerst; Fortsetzungen liegen höher als ihre Eltern).
- `requires.completed` ist eine Liste von Questlinien-IDs: die Linie ist erst frei, wenn diese abgeschlossen
  sind. Beispiel: `waldhaus` verlangt `holzpfad`, `abenteuerpfad` verlangt `steintiefe` und `gipfelpfad`.
  Gesperrte Linien zeigen im Journal, worauf sie warten.

## Zieltypen

| Typ | Felder | wird geprüft durch |
|---|---|---|
| `BREAK_BLOCK` | `block`, `count`, optional `verb` (`MINE`/`CHOP`/`HARVEST`) | Block-Abbau des Spielers |
| `PLACE_BLOCK` | `block`, `count` | Blockänderung nach Rechtsklick |
| `ACQUIRE_ITEM` | `item`, `count` | Inventar-Zuwachs seit Schrittbeginn |
| `CONSUME_ITEM` | `item`, `count` | Verbrauch nach Benutzen |
| `LOOK_AT_BLOCK` | `block`, `dwellMs` | Blick für die Dauer |
| `LOOK_AT_ENTITY` | `entity`, `dwellMs` | Blick für die Dauer |
| `DEFEAT_ENTITY` | `entity`, `count` | Angriff + Tod in 5 s |
| `REACH_BIOME` | `biome`, `dwellMs` | Biomen-ID oder `#tag` |
| `REACH_DIMENSION` | `dimension` | Dimension |
| `REACH_HEIGHT` | `minY`, `maxY` | Spielerhöhe |
| `USE_BLOCK` | `block` | Rechtsklick auf den Block |
| `INTERACT_ENTITY` | `entity` | Interaktion mit dem Mob |

## Bedingungen (Gates)

`TIME` (`DAY`/`NIGHT`/`DAWN`/`DUSK`), `WEATHER` (`CLEAR`/`RAIN`/`THUNDER`), `Y_RANGE`, `NEAR_ENTITY`,
`NEAR_BLOCK`, `IN_DIMENSION`, `IN_BIOME`. Ein Ziel zählt nur, solange alle Bedingungen erfüllt sind.

## Regeln

- Koreanische Felder (`clue.ko`, `hint.ko`, `textKo`, `titleKo`) sind **reines Hangul** (Ziffern und
  Satzzeichen sind erlaubt). Der Test schlägt sonst fehl.
- Deutsche Texte sind die Übersetzung, nicht die Quest selbst: der Spieler sieht im Spiel zuerst nur
  Koreanisch.
- Jeder Schritt braucht genau ein Ziel; das Finale ebenfalls.
- Schritt-IDs sind innerhalb einer Linie eindeutig.
- `concepts` ist die Liste der Wörter, die im Journal unter dieser Linie stehen.

## Prüfen

```bash
./gradlew test
```

`QuestlineValidationTest` lädt jede Datei, prüft Verweise, IDs, Hangul und das Finale. Zur Laufzeit
überspringt der Loader fehlerhafte Schritte mit einer Log-Warnung, statt das Spiel abzustürzen.


Attribut-Referenz: [docs/questline-attributes.md](questline-attributes.md).
