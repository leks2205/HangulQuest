# Contributing to Hangul Quest

Thanks for wanting to help. There are three ways in, and only one of them needs Java.

## Reporting a bug

Open an issue with **Bug report**. The template asks for the mod version, your Minecraft, Fabric
Loader and Fabric API versions, the questline id if a quest misbehaves, and your log. Please
reproduce it once more after a restart before you file it — the quest folder is re-read every two
seconds, so half the "it didn't reload" reports are a file that was never valid JSON.

Security problems go through [the private advisory form](https://github.com/leks2205/HangulQuest/security/advisories/new),
not a public issue. See [.github/SECURITY.md](.github/SECURITY.md).

## Contributing a questline

Questlines are data. You do not need to compile anything.

1. Read [`docs/questline-attributes.md`](docs/questline-attributes.md) — it is the mandatory
   checklist — and skim [`docs/questlines.md`](docs/questlines.md) for the format and the tooling.
2. Scaffold a file:

   ```bash
   python3 tools/new_questline.py <id> --words stone,dirt --tier 1 \
     --title-ko "나의 길" --title-de "Mein Pfad"
   ```

3. Write the clues. Korean fields must be Hangul-only (`clue.ko`, `hint.ko`, `textKo`, `titleKo`).
   The clue has to name the action *and* the target, and every condition has to be spoken in the
   Korean sentence, or the content tests fail.
4. Link the trail into the tree: a `next` for everything that is not the last level, a `previous`
   for everything that is not a root. Set `koreanLevel` to the band of your trail's depth
   (`Topik.levelForDepth`), not to a guess.
5. Run `./gradlew test` until it is green. A red `QuestlineValidationTest` means the content is
   broken, not the code.
6. Open a pull request with the JSON file and a one-paragraph note on what the trail teaches and
   where it sits in the tree.

Two rules that are easy to break and hard to notice:

- **Every trail must be finishable in a fresh world.** Nothing may require a specific structure,
  chest, coordinate or rare biome. That is what the ritual finale is for.
- **The clue must match the check.** If the goal is `PLACE_BLOCK minecraft:torch`, the sentence
  says to place a torch; it must not ask the player to "make light".

Translations are welcome too: the German `clue.de` / `textDe` lines are optional in the schema, and
the interface strings live in `src/main/resources/assets/hangulquest/lang/`.

## Contributing code

### Setting up

JDK 25 is required (the wrapper pins Gradle 9.7.1 and Loom 1.18; the first build downloads
Minecraft and takes a few minutes).

```bash
git clone https://github.com/leks2205/HangulQuest.git
cd HangulQuest
./gradlew test
./gradlew runClient
```

`./gradlew runClient -Dhangulquest.selftest=true` starts the dev client straight into a world, shows
a hint and opens the journal after a few seconds, writing `[selftest]` lines to the log. It is the
fastest way to see the UI without playing.

### Style

- Java 25, tabs for indentation, UTF-8. The build sets `options.release = 25` and
  `options.encoding = 'UTF-8'`; don't add a second formatting convention on top.
- The client classes are the only place that touches Minecraft. Quest logic (`quest/`, `learn/`,
  `data/`) stays pure so it can be tested without a game.
- New goal or condition types need a Java change, a unit test and a schema update. The set is closed
  on purpose: every goal must be verifiable on the client alone.
- User-facing text is German today. Add new strings to `lang/de_de.json` and `lang/en_us.json`,
  keeping the existing keys.

### Before a pull request

```bash
./gradlew test          # unit tests plus validation of every shipped questline
./gradlew build         # also builds the jar
```

CI runs the same two steps on Linux with JDK 25, plus a JSON-schema check on all questline files.

### Commit messages

Short imperative subject, then a body if the *why* is not obvious:

```
quest: link wollpfad to weidepfad

wollpfad ended as a dead end after the wave-2 merge, so the tree could not be finished
from the wool branch. Adds the parent/child pair and the missing badge word.
```

Prefixes in use: `quest:` content, `ui:` interface, `engine:` quest logic, `docs:`, `build:`, `fix:`,
`test:`.

## Good first contributions

- An English interface (the strings are already collected in `lang/`).
- More curated concepts — the vocabulary catalog is 293 words; animals, dyes and redstone parts are
  thin.
- Questline reviews: play a branch you did not write and report the steps whose clue was ambiguous.
- Tests for `QuestValidator` edge cases (cycles, missing parents, a condition that is not spoken).

## Code of conduct

Be decent. Disagreements about grammar, quest design and Minecraft versions are expected; personal
attacks, harassment and bigotry are not, and will get a pull request closed and a comment removed.

## Help review Korean

The game shows thousands of Korean lines: quest clues, hints, finale messages, word cards. A linter
and two AI reviewers check them, but only a person who speaks Korean can say that a line sounds
natural. You can help without git and without installing anything. A Korean guide is in
[`docs/review/README.ko.md`](docs/review/README.ko.md).

1. Open the [review site](https://leks2205.github.io/HangulQuest/review/). It shows the Korean first and
   starts with the lines that matter most (levels 1 and 2, then lines flagged `needs-native`).
2. Read a line. Press **맞아요 / Looks right**, or **고치기 제안 / Suggest a correction** and write the
   better line. After reading a whole page you can press **이 페이지 전체 승인 / Approve this page**.
3. Each button opens a prefilled GitHub issue form (a GitHub account is needed). Leave the prefilled
   fields as they are, tick the box, submit.

What happens next:

- A bot checks that the line still exists with the same text (every line has a hash, so a form for an
  older text is answered with the current text), runs the Korean linter on your suggestion and labels
  the issue.
- **Approvals** are written to `content/korean/review-status.json` in one pull request per day. An
  approval counts only from a native, fluent or teacher account at least 30 days old; nobody can
  approve a line they wrote; one approval per person. A line is *native approved* after two different
  people approve it, or one **trusted reviewer** (30 accepted suggestions, none reverted, confirmed by
  the maintainer in `content/korean/reviewers.json`). If the text of a line changes, its approvals
  and AI verdicts drop and it starts again.
- **Corrections** wait for the maintainer. With the label `accepted` the bot opens a pull request that
  changes exactly that one value (JSON pointer) with a `Co-authored-by` credit for you. Many lines are
  produced by generator rules (`SentenceBuilder`, `verbs.json`); for those the bot opens the task "fix
  generator rule X (affects N lines)" instead, because a single line cannot be edited by hand.
- Your name appears on the [credits page](https://leks2205.github.io/HangulQuest/credits.html). Write
  `anonymous` in the credit field to stay out of it.

Status of a line: `unreviewed` → `lint-ok` → `ai-checked` (Claude and Gemini both said ok) →
`native-approved`, or `native-flagged`. Lines where an AI said "error" or the two disagreed carry the
flag `needs-native`. The registry is `content/korean/lines.jsonl` (ids come from the place in the
source, never from the text).

For maintainers:

```bash
./gradlew test --offline --tests '*SentenceDumpTest'    # writes build/reports/korean/sentences.tsv
python3 tools/review/export_lines.py                    # lines.jsonl (add --check in CI)
python3 tools/review/status.py sync                     # follow text changes, lint, AI verdicts
python3 tools/review/import_ai.py --reviewer claude review-1.jsonl review-2.jsonl
python3 tools/review_site/build.py --repo owner/name    # docs/book/book/review/
python3 tools/review/credits.py                         # credits page and in-game credits.json
python3 -m unittest discover -s tools/review/tests      # parser, hash check, approvals, site
```

AI review files are JSONL, one `{"id","verdict":"ok|minor|error","issue","suggestion"}` per line. Set
the repository once in `--repo` or `REVIEW_REPO` (it is a parameter because the project may move to an
organisation). One-time setup: run the workflow *Korean review bot* with `ensure-labels`, and allow
Actions to create pull requests (Settings, Actions, General). Only the maintainer labels `accepted`,
`rejected` and `recheck` start anything that writes, and the bot verifies the label's author has write
access. Issue text is only ever data: it is never evaluated or put into a shell command.
