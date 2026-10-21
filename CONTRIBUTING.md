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
   python3 tools/new_questline.py <id> --words stone,apple,cow --after <parent ids> \
     --title-ko "나의 길" --title-de "Mein Pfad" --title-en "My Trail"
   ```

   One step per word, with English clue and hint, `koreanLevel`, `tier` and `order` worked out from
   the tree position. The Korean clues are generated, so the file has none of its own. The finale
   comes from `tools/data/finales.json`: a goal every player can reach in a fresh world (a torch at
   night, a chest, a cow), picked so that no sibling trail and no step of this trail uses it. Pass
   `--finale <id>` to choose one yourself. `QuestScaffoldTest` runs this script and checks the result
   against the content rules.

3. Write the clues. Korean fields must be Hangul-only (`clue.ko`, `hint.ko`, `textKo`, `titleKo`).
   The clue has to name the action *and* the target, and every condition has to be spoken in the
   Korean sentence, or the content tests fail.
4. Link the trail into the tree: a `next` for everything that is not the last level, a `previous`
   for everything that is not a root (`--after`). The scaffold sets `koreanLevel` to the band of the
   depth (`Topik.levelForDepth`); keep it if you move the trail. `tools/quest_tree.py` regenerates
   `docs/book/src/quest-tree.md`, and the trail count asserted in `QuestlineValidationTest` and
   `TrailGraphTest` goes up by one.
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

JDK 25 is required (the wrapper pins Gradle 9.7.1; Loom is pinned to a release in
`gradle.properties` and mapped in `settings.gradle`; the first build downloads Minecraft and takes a
few minutes).

```bash
git clone https://github.com/leks2205/HangulQuest.git
cd HangulQuest
./gradlew test
./gradlew runClient
```

`./gradlew runClient -Dhangulquest.selftest=true` starts the dev client straight into a world, shows
a hint and opens the journal after a few seconds, writing `[selftest]` lines to the log. It is the
fastest way to see the UI without playing.

### Client game tests

`src/gametest` holds scripted sessions that run in a real client through Fabric's client game test
API: create a world, see the welcome screen once, open the journal, learn a word, save and quit, join
the same world again. They take screenshots into `build/gametest/` and fail the run on any assertion.

```bash
./gradlew runClientGameTest                                    # needs a display
# Inside a Wayland session SDL otherwise picks the Wayland driver and finds no display under Xvfb.
xvfb-run -a -s "-screen 0 1280x720x24" env -u WAYLAND_DISPLAY SDL_VIDEODRIVER=x11 \
    ./gradlew runClientGameTest                                  # headless
```

The run directory is `build/run/clientGameTest`, wiped on every start, so nothing touches your own
game folder. It is deliberately not part of `./gradlew test` or `build`; CI only checks that it still
compiles (`./gradlew gametestClasses`). A nightly job on a machine with Xvfb is the place to run it.

### Before a release

- `mod_version` in `gradle.properties` is the single source of the version. The release workflow
  fails when the tag is not exactly `v<mod_version>`.
- Add `src/test/resources/progress-fixtures/v<mod_version>.json` (the `progress.json` that version
  writes, synthetic data) and list it in `ProgressCompatibilityTest`. `ReleaseFixtureTest` and the
  release workflow both refuse a version without it. See the README in that folder.
- The mod makes no network requests. `NetworkUsageTest` fails on `java.net.http`, `HttpClient`,
  `URLConnection`, sockets and `URL.openStream` in `src/main/java`; the only allowed way out of the
  game is a link through Minecraft's `ConfirmLinkScreen`.

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

CI runs the same two steps on Linux with JDK 25, plus the Korean lint, a JSON-schema check on all
questline files and a check that `tools/quest_tree.py` computes the same depths as the mod.

### Content metrics and the ratchet

The content follows one rule: recycle words, not tasks. Two tools measure it, and
`content/metrics-baseline.json` records the value of every metric. A change may improve a metric but
never make it worse: `TaskVarietyTest`, `CoverageTest` and `RecyclingTest` fail on a regression, and
CI runs the same checks. The roadmap targets (bare steps at most 30/20/10% on levels 1/2/3 and up, no
goal type above 25% of a level, 10 encounters in 3 roles for every core word, ...) are only printed
next to the numbers; the tests do not demand them yet.

```bash
./gradlew test --tests '*StepsDumpTest'      # writes build/reports/content/steps.json
python3 tools/variety.py                     # task variety: duplicates, object-only twins, bare steps, novelty
python3 tools/coverage.py                    # unknown words per sentence, hint coverage, word recycling
python3 tools/variety.py --check             # what CI runs; so does coverage.py --check
```

`build/reports/content/recycling-gaps.csv` lists the core words that are met too rarely, in too few
roles or not coming back within 14 days. It is the input for the content briefs.

If a change makes a metric worse on purpose (say, a new level needs plain steps first), rewrite the
baseline and say why in the commit message:

```bash
./gradlew test --tests '*StepsDumpTest'
python3 tools/variety.py --update-baseline   # variety and coverage metrics
```

`*_demo` trails are measured but exempt from the ratchet. A pull request that changes the Korean
text of more than 15 trail files or more than 300 Korean strings fails `tools/content_guard.py`
unless it has the label `mass-change-approved` or a commit message or the PR body references an ADR
in `docs/decisions/`. Korean that stays byte-identical (only en/de text or the structure moved) does
not count.

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
