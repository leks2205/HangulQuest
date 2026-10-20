# The journal

`K` opens the journal. It is not a vanilla screen with a book texture: the mod draws a leather cover,
a sewn spine, a ribbon, a cream page with ruled lines and a flat ink font, so Hangul stays crisp at
small GUI scales.

<img src="screenshots/journal.webp" alt="Journal, info tab" />

Six tabs, switched with the row at the top or with the mouse wheel while the list has focus:

| Tab | What is on it |
|---|---|
| **Info** | words discovered and mastered, due today, streak, level, the word of the day, and buttons to review or open the quest list |
| **Wörter** | the dictionary — every word you have met, with Korean, German, status and origin |
| **Lernen** | due words as flashcards, plus the review trail |
| **Erfolge** | the fifteen achievements with progress |
| **Quests** | the trail tree and the detail card of the selected trail |
| **Optionen** | HUD, German in quests, quiz size, hints, sound, the quest folder |

## Info

The dashboard. Counters are deliberately plain: *entdeckt* (met at least once), *beherrscht* (SRS level
6), *fällig* (due now), the streak in days with your best streak, today's reviews and the number of
correct answers. Clicking **Wiederholen** starts a review, **Quests öffnen** jumps to the tree.

## Wörter

A full-text list of the vocabulary the mod knows: Korean word, German meaning, status, and where the
word came from —

- **curated** — a concept from `concepts.json`, with a hand-written easy-Korean description,
- **vanilla** — an item, block, entity or biome name taken from the game's language files,
- **lute** — imported from your Lute export.

The mouse wheel scrolls, the search field filters by Korean or German. Words you have not met yet are
not shown, so the list is a record of your own journey rather than a dictionary dump.

<img src="screenshots/journal-dictionary.webp" alt="Dictionary tab with Korean words, German meanings and status" />

## Lernen

Due words, one card at a time: the Korean word and an icon first, the German meaning on the reveal.
Answer honestly; a card you mark as known moves up an SRS level and comes back later, a card you did not
know comes back in a few minutes.

The review trail (**복습의 길**) is the same words as a quest: it is generated from today's due words and
changes daily. Use it when a flashcard session feels too abstract.

## Erfolge

Fifteen achievements across vocabulary, mastery, reviews, streak, animals, blocks, questlines and
sidequests. They unlock silently when their condition is met and are listed with their Korean name,
German name and progress. They are a nudge, not a gate: nothing in the mod requires them.

## Quests

The tree. Each node is one trail, coloured by state:

- **done** — finished, badge earned,
- **active** — one of the trails in your quest log (the HUD shows up to six),
- **open** — unlocked but not started,
- **locked** — shows what it is waiting for (`nach 돌의 길`, or `ab 2 Wörtern`).

Selecting a node shows the detail card: Korean and German title, level, TOPIK band, state, parents,
children, progress (`2/4`), the live status of the current step, a checkbox and a start button.

- **Im Log** (checkbox) is the important one: ticked quests are drawn on the HUD and their goals are
  checked against the world. Unticked quests keep their progress but are paused.
- **Starten** ticks the quest and starts it; **Fokus** highlights a quest that is already in the log.
- State reads `▶ aktiv` for the highlighted quest, `▸ im Log` for the other ticked ones, `○ offen` for a
  quest that is unlocked but not in the log, and `· gesperrt` for a locked branch.

Parent trails are listed before their children, and the list scrolls.

<img src="screenshots/journal-quest-log.webp" alt="Quest tab: trail tree, detail card and the Im Log checkbox" />

## Optionen

| Option | Effect |
|---|---|
| Quests | turn the quest system on or off |
| Quest-Tracker | show the sentence and status in the corner |
| HUD-Karten | step, sidequest and trail cards |
| Tooltips | word tooltips on items |
| Auto-Quiz | the ambient quiz that appears while you stand still |
| Ton | celebration sounds |
| Deutsch in Quests | add the German line to quest text, cards and celebrations |
| Lute-Import | read `vocab/lute-vocab.csv` at start-up |
| Fortschritt zurücksetzen | wipe the journey — asks for a second click and writes a backup first |

Underneath, the tab reports what the Lute import did (`12 Wörter importiert, 3 übersprungen`). The
corners, card duration and quiz interval are in `config/hangulquest/config.json`; see
[Files and backups](files.md).
