# Contributing

The full contribution guide lives in the repository:
[CONTRIBUTING.md](https://github.com/leks2205/HangulQuest/blob/main/CONTRIBUTING.md).

Short version:

- **Questlines need no Java.** Write a JSON file, run `./gradlew test`, open a pull request. Read the
  [authoring guide](questlines.md) and the [attribute reference](questline-reference.md) first.
- **Code** is Java 25 on Gradle 9.7.1 and Loom 1.18. `./gradlew test` and `./gradlew build` have to pass;
  CI runs the same on Linux, plus a schema check over every questline file.
- **Interface strings** are keys named `hangulquest.ui.<area>.<name>` in `lang/en_us.json` (the reference);
  arguments are `%s`. A key a language lacks falls back to English.
- **Quest and word text** (trail titles, clues, hints, finale lines, word glosses) is keyed too, in
  `lang/content/en_us.json`. The questline JSON holds the Korean only; see the [authoring guide](questlines.md).
- **Translations** (German, Spanish, French so far) are made in the repository, with no outside platform. English is
  the source and Korean is never translated. A model drafts a language, a second model proofreads it
  (`tools/i18n/translate.py export`, `import`, `proofread-export`, `proofread-import`), and every key carries a
  state: `machine`, `proofread`, `community-approved` or `stale` (the English changed). `translate.py check` runs the
  same rules as CI (placeholders, Hangul, untranslated English, length). To fix a line, use the **Translation
  correction** issue form or edit the key in a pull request; the review site has a page per language. To add a
  language: translate with the pipeline, then `translate.py ship --lang <code>` lists it in `data/languages.json`.
- **Bugs** go to the issue tracker with versions, the questline id and a log excerpt. Security problems
  go through the private advisory form, never a public issue.
- **Be decent.** Grammar disagreements are expected; harassment is not.

## Good first contributions

- Checking the Spanish and French text, or starting another language (see above).
- Reading the Korean lines on the [review site](help-review.md), if you speak Korean.
- More curated concepts: animals, dyes, redstone parts and a few verbs are thin.
- Playing a branch you did not write and reporting the clues that were ambiguous.
- Tests for the validator's edge cases: cycles, missing parents, a condition that is not spoken.

## Building the guide

This site is mdBook. To build it locally you need the `mdbook` binary and the screenshots in place:

```bash
tools/build_book.sh            # copies docs/screenshots, regenerates the tree, builds the book
python3 -m http.server -d docs/book/book 8000
```
