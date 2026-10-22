# Review, streak and achievements

A word you met once is not a word you know. Hangul Quest keeps a schedule for every word and quietly turns the
words you are about to forget back into cards and quests.

## The schedule

Reviews use **FSRS-6**, an open spaced-repetition algorithm. Every word has a card with a stability (how many days
until you are likely to forget it) and a difficulty. When you answer, the card is updated and the next due time is
set so that you still remember the word with the probability you chose under *Settings → Desired retention*
(88% by default, between 80% and 95%).

- A four-option answer is graded **Good**, **Hard** when it was slow, or **Again** when it was wrong. Two- and
  three-option questions, exposures, hints and probes never move the schedule, so guessing costs nothing.
- A step that teaches a due word and is done without help counts as **Good**; after a hint it counts as **Hard**.
- A wrong answer comes back within the same session.
- A word is **mastered** when you recalled it on three different study days and its stability reached 21 days.
- Words from an older version keep their old level for display; their new card starts from it, so an update does not
  make everything due at once.

## What counts as a review

1. **Cards** in the journal's **Study** tab, and the `J` key: four options, a recall you grade yourself, syllable
   tiles, and typing for stable words.
2. **Quest steps**: finishing a step that teaches a word counts as a review of it.
3. **The review trail** (below), which is the same review in quest form.

*Settings → New words per day* (5, 10 or 15) limits how many new words a session adds, and a session adds only one
new word per group of related words.

## The review trail

Once a day the mod builds a trail called **복습의 길**, *the path of review*, out of the words that are due and that
map to something you can do in the world. It is seeded by world name and date, so it is the same trail all day and
changes tomorrow (the study day turns over at 04:00). It sits outside the main tree and is always available.

## Streak

The streak counts days on which you did at least one review or finished a step. The goal is four days a week, and
two freezes a month are applied automatically. The **Info** tab shows the current and the best streak. Opening the
journal does not make a day.

## Achievements

Fifteen, all unlocked quietly:

| Group | Achievements |
|---|---|
| Vocabulary | 1, 10, 25, 50 and 100 words discovered |
| Mastery | 10 words mastered |
| Reviews | 100 reviews |
| Streak | 3, 7 and 30 days in a row |
| Categories | 10 animals, 25 blocks |
| Quests | first trail, three trails, five sidequests |

The **Achievements** tab lists them with progress; unlocked ones are stamped with the date.

## Why the mod is quiet about numbers

There is no XP bar for Korean, no daily goal you can fail and no notification asking you to come back. The counters
exist because they help you see progress, not because the mod wants a habit loop. If you prefer it even quieter,
switch off **Auto quiz** and the HUD cards in the settings and use only `K` and `H`.

## The review log

Every answer is also appended to a local file, `reviews.ndjson`, in your profile folder (rotated monthly). It lets
the mod rebuild the card states and nothing else. It never leaves your machine; the optional research export is a zip
you create yourself and send yourself.

Next: [Files and backups](files.md), what all of this is stored in.
