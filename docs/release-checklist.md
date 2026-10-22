# Release checklist (gate G5)

A release (a pre-release `3.0.0-rc.N` as much as `3.0.0`) goes out when every box below is ticked. The first three
parts are commands; the rest is play in a real client. Nobody publishes without the maintainer's approval: this list
ends where the tag is pushed.

Record the result next to the tag (a short comment in the release PR or the snapshot commit is enough): the commit,
the date, who played, and what failed.

## 1. Version and notes

- [ ] `mod_version` in `gradle.properties` is the version you release (`3.0.0-rc.2`, ...). The tag is `v` plus that
      string. `release.yml` stops when they differ.
- [ ] `CHANGELOG.md` has a section for the version, or for the version it leads up to (an rc uses the `## [3.0.0]`
      section). `python3 tools/changelog_section.py <version>` prints the release text; read it once.
- [ ] The progress fixture for the tag exists: `tools/new_fixture.sh v<version>` writes
      `src/test/resources/progress-fixtures/v<version>.json` and lists it in `ProgressCompatibilityTest`. Commit both.
      `release.yml` refuses a tag without the file.
- [ ] The migration notes in the CHANGELOG still match the code: where progress lives, what is backed up, how to roll
      back. Change them whenever `ProgressData`, `WordProgress`, `Config` or the file layout changed.
- [ ] No personal data in the diff: no names, home paths, hosts or addresses (`git diff <previous tag>.. | grep -n`
      for your own user name and home directory).

## 2. Automatic gates

Run unpiped. A pipe hides the exit code.

```bash
tools/gates.sh > gates.log 2>&1; echo "exit=$?"; tail -3 gates.log      # ends with ALL GATES GREEN
xvfb-run -a -s "-screen 0 1280x720x24" env -u WAYLAND_DISPLAY SDL_VIDEODRIVER=x11 \
    ./gradlew runClientGameTest --offline                               # headless; or on a desktop without xvfb-run
./gradlew build --offline                                               # build/libs/hangulquest-<version>.jar
```

- [ ] `tools/gates.sh`: unit tests (engine, `ProgressCompatibilityTest` for every fixture, migrations, Korean linter,
      schema, ratchets, review registry, translation checks, quest tree).
- [ ] Every fixture loads and saves without loss. The list is in `ProgressCompatibilityTest` and
      `src/test/resources/progress-fixtures/README.md`: one per released tag from `v1.0.0`, the schema samples
      `v3-sample`, `v4-sample`, `v4-placed`, `v4-autotrack`, the release-candidate fixtures, and the edge cases
      `corrupt`, `bom`, `future-v99`, `unknown-fields`.
- [ ] `runClientGameTest` passes (see below for what it covers) and the screenshots in `build/gametest/` look right.
- [ ] `./gradlew build` produces the jar. `unzip -p build/libs/hangulquest-<version>.jar fabric.mod.json` shows the
      version, no `${version}` is left, and the jar holds no `.ogg` files and no `progress` data.
- [ ] CI is green on the commit that gets the tag.

### What the client game test covers

New world, welcome screen once, journal open and close, a review card in its own screen without recording anything,
a HUD window that stays well past the old timeout and is closed by exactly one press of the hint key (and that press
is not a hint), a wide card beside the quest log, the quest tab (tracked trails, trails that can start, greyed
follow-ups, titles readable at GUI scale 2), one finished trail folding into "Completed", the situational slot reacting
to night, progress on disk after save and quit, and the same world coming back without a second welcome screen.

## 3. Smoke checklist (manual, about 20 minutes, a fresh profile)

Use a new Minecraft profile or an empty `config/hangulquest/` so you see what a new player sees.

**First start**

- [ ] The welcome screen appears once on the first world join and asks whether you can read Hangul. A link opens only
      through Minecraft's own "open this link?" screen. Skip, remind-me-once and never-again all work, and
      the next join does not show it again (unless you chose to be reminded). It is in the journal's Info tab too.
- [ ] The quest log shows up to four quests without you ticking anything (three regular, one situational) and a
      message says the game picked them.
- [ ] Language: with the game in German, Spanish and French the interface follows; any other language gets English;
      *Settings → Language* steps through all four. A missing text falls back to English, never German.

**Windows that stay until `H`**

- [ ] Finish a step: the step banner stays. Wait 10 seconds. It is still there. Press the hint key once: it closes, and
      the step is not marked as helped (the hint is still available, no sidequest started).
- [ ] Trigger two windows at once (a banner and a word card). One press closes the top one only; a "+N" badge shows
      the rest; the second press closes the next.
- [ ] A hint window, a rule card, a culture note and the quiz prompt also stay, show their key, and do not cover the
      crosshair or the hotbar.
- [ ] With nothing open, the hint key is a hint: simpler Korean, the word explained, a sidequest.
- [ ] *Settings → Auto-close windows* on: windows close after a few seconds again. Off again for the rest of the test.
- [ ] Pressing `1`-`3` with a quiz only showing a prompt changes the hotbar and answers nothing.

**Auto-tracked quests**

- [ ] Finish the highlighted quest: the next one is offered or started, and the log fills up again.
- [ ] Tick a quest in the journal: the game never replaces it. Untick one: it stays out for the next 30 minutes of play
      and keeps its progress.
- [ ] Walk into snow or underground for a minute: the situational slot changes. At night it can change, too.
- [ ] *Pick quests automatically* off: the log stops filling. On again: it fills.
- [ ] `V` moves the highlight and the order of the list stays.

**Readable quest tree**

- [ ] The Quests tab shows only the trails you can start, those in the log and the greyed ones right behind them.
      A locked trail says what it waits for.
- [ ] A finished trail folds into the "완료 · Completed (N)" button; the button opens and hides the list.
- [ ] *Show all trails* shows the whole map; the scroll wheel and drag work; no title is cut off at GUI scales 2, 3 and
      auto, in a 1280x720 window and fullscreen.
- [ ] The chapter view (by life situation) lists the same trails with their progress.

**Learning**

- [ ] Complete a deep trail: the game does not freeze (note the longest hitch).
- [ ] A review card: four options, a wrong answer shows the word again, nothing is penalised for a hint or a guess
      among two options. Typing a word works (letters become jamo, Enter checks, Tab shows the key map).
- [ ] The gunpowder tooltip shows 화약; looking at a cat shows 고양이 and "Cat" in the gloss.
- [ ] The Grammar and Books tabs open. A locked story lists
      the words to learn first.
- [ ] A villager (sneak and right-click) starts a Korean conversation. Esc leaves it.

**Saving**

- [ ] Quit to the title and back: quests, counters and the sidequest state are where you left them.
- [ ] Kill the game with `kill -9` while a step is half done: the next start loads the last saved file, and no
      `.tmp` files are left in the profile folder.
- [ ] Update test: copy a real 2.1.0 `config/hangulquest/` folder, start 3.0 with it. Your words, finished trails and
      streak are there; `profiles/<id>/progress.json` exists; the old `progress.json` is unchanged (compare checksums);
      a `progress.v*-backup-*.json` exists. Then roll back with the 2.1.0 jar as the CHANGELOG says.
- [ ] No network: run with the network cable pulled. Nothing changes.

**Voices and links (only if the pack is part of this release)**

- [ ] Without the voices pack the listen key does nothing and nothing errors. With the pack, a word plays; with `audio`
      off in `config.json` it does not.

## 4. Manual test list: goals that have never run in a real client

These goals pass the unit tests and the simulated playtest, but no real client has run them yet. Each line says what
to do, what must happen and what must not. Use a creative world with cheats for the items and a survival world for the
last check. Write down the trail id when one fails. If a trail that uses the goal is not in the current content, make a
test file in `config/hangulquest/questlines/` (`tools/new_questline.py`) or use the unit-tested case as a guide.

| Goal | Do | Must happen | Must not happen |
|---|---|---|---|
| **Candle flame** (`USE_BLOCK`, change `LIT`) | place a candle, right-click it with flint and steel or a fire charge | the step completes when the flame appears | completes when you only place the candle, or when you click an already lit one; extinguishing does not complete it |
| **BREED** | put two animals of one kind in a pen, feed both | the step completes when the baby is born within 4 blocks of the two fed parents | completes for a baby you did not breed, for a baby spawned far away, or for feeding without a baby |
| **FALL** | stand on a high block, jump onto powder snow, then a hay bale or water, then fall without a soft landing from 2 blocks | completes after a landing that cost no health or a jump onto a soft block | completes on a normal fall with damage; a rolled-back position on a server counts as a fall |
| **Sign writing** (`USE_BLOCK`, `WRITE`) | place a sign and type a word, close the edit screen | completes when the screen closes with text | completes when you close it with nothing written, or when you only open it |
| **The bell** (`USE_BLOCK` on a bell, `HEAR`) | ring a bell by right-click; in the village, ring it with a villager nearby | the step completes on the ring, and a hearing step on a bell within its radius | completes by walking past a bell; a bell far outside the radius counts |
| **EQUIP** | put on a chestplate, helmet, leggings, boots; then take them off | completes when the item is in its slot (the general word accepts any material; an exact tier needs that tier) | completes when the item is only in the inventory |
| **TRADE** | buy something from a villager; sell something to one | the direction in the sentence (에게서 buy, 에게 sell) decides; the right direction completes | the wrong direction completes; trading with a wandering trader is treated like a villager only if the trail says so |
| **Tool verbs** | shear a sheep (가위로 양털을 깎아요), tie a cow with a lead (끈으로 소를 묶어요), brush an armadillo, remove wolf armour with shears, put wolf armour on, dye a sheep | each action completes the step whose verb names it | the wrong tool completes the step; shearing with the bare hand, or a lead on the wrong animal, counts |
| **Sequences** (`SEQUENCE`) | do the parts out of order, then in order | only the right order completes | the second part counts before the first |
| **Counting people and animals** (`NEAR_ENTITY`) | stand near two villagers and three cows | the status line counts up (`2/2`, `3/3`) | a baby or a distant animal is counted twice |

Also for each: the sentence on screen says the same as the check (a clue that says 켜요 must check the flame), the
status line explains a miss, and the completion card appears once.

## 5. Publish (the maintainer only)

Nothing in this part is run by an agent.

- [ ] The maintainer has read the CHANGELOG section and this checklist's notes and approves the release.
- [ ] One snapshot commit on `main`, then the tag `v<mod_version>`, then the push of both. `release.yml` then
      verifies tag and version, checks the fixture, builds, writes `SHA256SUMS`, and creates the GitHub release with the
      CHANGELOG section as its text. A version with a hyphen is created as a **pre-release**.
- [ ] The release page shows the jar and `SHA256SUMS` and the pre-release flag (for an rc). Download the jar and
      `sha256sum -c SHA256SUMS`.
- [ ] Nothing goes to Modrinth or CurseForge from this workflow. A store upload is a separate, manual decision with its
      own project pages, and the voices pack ("Hangul Quest Voices", built by `tools/audio/build_pack.py`) is always
      its own project, never a file of the mod's release.
- [ ] After a final release (not an rc): move the CHANGELOG heading to the release date, update the version in
      `docs/book/src/index.md`, and republish the guide (the Guide workflow runs on a push to `main`).
