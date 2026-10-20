# Contributing

The full contribution guide lives in the repository:
[CONTRIBUTING.md](https://github.com/leks2205/HangulQuest/blob/main/CONTRIBUTING.md).

Short version:

- **Questlines need no Java.** Write a JSON file, run `./gradlew test`, open a pull request. Read the
  [authoring guide](questlines.md) and the [attribute reference](questline-reference.md) first.
- **Code** is Java 25 on Gradle 9.7.1 and Loom 1.18. `./gradlew test` and `./gradlew build` have to pass;
  CI runs the same on Linux, plus a schema check over every questline file.
- **Interface strings** are German today and live in `lang/de_de.json` and `lang/en_us.json`. An English
  UI is the most useful single contribution right now.
- **Bugs** go to the issue tracker with versions, the questline id and a log excerpt. Security problems
  go through the private advisory form, never a public issue.
- **Be decent.** Grammar disagreements are expected; harassment is not.

## Good first contributions

- An English interface (and with it, a language switch).
- More curated concepts: animals, dyes, redstone parts and a few verbs are thin.
- Playing a branch you did not write and reporting the clues that were ambiguous.
- Tests for the validator's edge cases: cycles, missing parents, a condition that is not spoken.

## Building the guide

This site is mdBook. To build it locally you need the `mdbook` binary and the screenshots in place:

```bash
tools/build_book.sh            # copies docs/screenshots, regenerates the tree, builds the book
python3 -m http.server -d docs/book/book 8000
```
