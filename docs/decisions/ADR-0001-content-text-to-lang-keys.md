# ADR-0001: English and German content text moves to lang keys

Status: accepted (WP1.4b)

## Context

The English and German text of the quest content was written into the content JSON itself: a trail's
`titleEn`/`titleDe`, every step's `clue.en`/`clue.de` and `hint.en`/`hint.de`, `finale.textEn`/`textDe`,
and the concepts' `en`, `de` and `sentenceDe`. That is 422 questline files and `concepts.json`.

A translator (or Weblate, or Crowdin) cannot work like that. A new language would mean editing 423
files, each with Korean and structure around the text. The interface text had already moved to one flat
file per language in WP1.4a (`assets/hangulquest/lang/<code>.json`, keys `hangulquest.ui.*`).

## Decision

The shipped English and German content text lives in two more flat files, one per language:

```
assets/hangulquest/lang/content/en_us.json    the reference, must be complete
assets/hangulquest/lang/content/de_de.json    may have gaps; a missing key falls back to English
```

Keys:

| Key | Replaces |
|---|---|
| `hangulquest.trail.<id>.title` | `titleEn`, `titleDe` |
| `hangulquest.trail.<id>.step.<stepId>.clue` / `.hint` | `clue.en`/`de`, `hint.en`/`de` (the finale step is `finale`) |
| `hangulquest.trail.<id>.finale` | `finale.textEn`/`textDe` |
| `hangulquest.word.<conceptId>.gloss` | concept `en`, `de` |
| `hangulquest.word.<conceptId>.sentence` | concept `sentenceDe` (`sentenceEn`) |

`I18n` reads `lang/content/<code>.json` next to `lang/<code>.json` and merges both. `Questline`,
`QuestStep` and `Concept`/`Word` resolve their text through it for the current language and fall back to
English. The accessors `titleEn()`, `titleDe()`, `clueEn()`, `finaleDe()` and the rest stay and delegate.

Player questline files in `config/hangulquest/questlines/` and in resource packs have no lang file, so
the loader still reads their inline fields. An inline field wins over a key. A field that carries any
inline text hides the shipped keys for that field in every language, so a rewritten English clue is not
shown next to the shipped German one.

The Korean text, the ids and the structure of the content files do not change.

## The mass change

The move touches 423 files (422 questlines and `concepts.json`), and the roadmap's content-diff guard
asks for a decision record for a change that size. It is mechanical: no text was reworded, reordered or
corrected.

`tools/i18n/extract_content.py` does the move. It reads each file, takes the English and German
fields out, writes them to the lang files and writes the file back with the layout it had (one space of
indent, no ASCII escapes), so the diff of a content file is removed lines only. It is idempotent.

### Proof that nothing changed

Two checks, both repeatable.

1. **The files.** `python3 tools/i18n/extract_content.py --against 08ecd43` reads every content file at
   the revision before the move, strips its English and German fields with the same code, and requires
   the result to equal the file now byte for byte. It also requires every removed text to be in the
   lang file under its key, unchanged, and the lang files to hold no other key. Result: 423 files match,
   5,044 English and 3,930 German texts are in the lang files unchanged. Since the Korean fields are
   part of what must match, the Korean is unchanged byte for byte.

2. **The rendered text.** `ContentTextDumpTest` writes the text as the game resolves it (`title`, `clue`,
   `hint`, `finale` and the `*En`/`*De` accessors of every trail and step, the `gloss`, `glossFull`,
   `de`, `en`, `deShort`, `sentenceDe` and `sentenceEn` of every word) to
   `build/reports/content-text/<code>.tsv`, about 1.1 MB per language. It was run on the code and
   content before the move and again after it:

   ```bash
   ./gradlew test --offline --tests '*ContentTextDumpTest'
   sha256sum build/reports/content-text/en_us.tsv build/reports/content-text/de_de.tsv
   ```

   | Dump | SHA-256, before and after |
   |---|---|
   | `en_us.tsv` | `186186f26ccb32f94780595b45c0dbcf5246157d75a8f1bccfd8fea113240d91` |
   | `de_de.tsv` | `835033f34f8448c3c666fe45bb81245f3c5492ffb459fb55e108f6265d719e4a` |

   `cmp` finds no difference between the two runs for either language. A language without a file
   (`fr_fr`) renders as English; its dump equals `en_us.tsv`.

### Guards that stay

- `ContentTextTest` fails when a shipped trail, step or word has no English text, when a content key
  belongs to nothing (a typo), when argument counts differ from English, and when a shipped file carries
  inline English or German again. German coverage is printed and written to
  `build/reports/content-text/coverage.txt`; gaps do not fail.
- `python3 tools/i18n/extract_content.py --check` runs in CI with the schema check.
- `tools/new_questline.py` calls the extractor for the trail it writes.
- `tools/review/export_lines.py` and `tools/quest_tree.py` read the text back through
  `tools/i18n/content_text.py`; the review registry and the quest tree report are unchanged.

## Consequences

- A translator adds `lang/content/<code>.json` and nothing else. A language that lacks a key shows
  English for it, never another language. The old fallback from English to German for a line that has
  only German text is kept as the last resort.
- The schema no longer requires `titleDe` and `finale.textDe`; the inline fields stay documented for
  files of your own.
- Sentence translations (`...word.<id>.sentence`) exist only in German; there is no English text for
  them yet. That gap was already there and is not part of this move.
- Resource packs cannot ship lang files for the mod today (the lang files are read from the mod jar,
  like the interface text). A pack that replaces a questline or `concepts.json` carries inline fields.

## Alternatives considered

- Keep the text inline and generate the lang files for translators. Rejected: two sources of truth, and
  the translators' edits would have to be merged back into 423 files.
- One lang file per trail. Rejected: the point is one flat file per language for the tooling.
- Change the wording while moving it. Rejected on purpose; a mass move with no text change can be
  proven, and wording fixes belong in small reviewed changes to the lang files.
