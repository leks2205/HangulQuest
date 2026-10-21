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
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientGameTest   # headless
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
