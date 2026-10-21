# Hints and sidequests

The hint is the heart of the mod. It is not a translation: it is the same task said in easier Korean,
plus one sentence about the word that is blocking you. And it does not let you continue until you have
spent thirty seconds with that word.

<img src="screenshots/hint.webp" alt="Hint panel: the clue in simpler Korean, the word explained, and the sidequest line" />

`H` works on the **highlighted** quest in the log — press `V` until the quest you want help with is the
gold one, then ask.

## What `H` gives you

1. **The same goal, at level 1.** A late trail might say
   `돌을 세 개 캐야 해요.` — pressing `H` gives
   `돌을 캐요. 세 개요.` The counters stay, the conditions stay spoken, the grammar drops to the
   politest form. Hints never leave level 1.
2. **The blocking word explained, in Korean.**
   `돌 = 땅에서 나와요. 단단해요.` — "comes out of the ground, is hard". Written with tier-1 vocabulary
   on purpose, and validated as Hangul-only.
3. **A sidequest.** Two questions about that word:
   - Korean word + icon → pick the German meaning,
   - German meaning + icon → pick the Korean word.

## The sidequest

<img src="screenshots/sidequest.webp" alt="Sidequest card with two answer options" />

- The original step is **frozen** while the sidequest runs. You cannot accidentally finish a quest you
  did not understand.
- A wrong answer does not punish you. It shows the word card, reduces the choices to two and lets you
  try again.
- Correct answers give at most **+1 SRS level**. Practice — sidequests and ambient word cards — never
  demotes a word, so guessing costs nothing.
- Both questions answered and the original step returns with a short Korean confirmation and a
  `사이드 퀘스트 완료!` card.

## When you already know the word

The sidequest is skipped and you just get the hint, if the word is:

- **mastered in the mod** (SRS level 6), or
- marked **known in Lute** (status 99).

You asked for a hint, not a lesson — the mod respects that.

## One hint per step

`hintUsed` is stored per step. Pressing `H` again re-shows the hint instead of starting a second
sidequest, and it survives a restart. Abandoning a trail clears it, because the trail starts over.

## The no-spoiler rule

Quizzes and sidequests never show the item icon before you answer. They draw a neutral `?` tile, and
the icon only appears on the result card. The ambient word card — the one that appears when you look at
something — does show icons, because that card is teaching, not testing.

This matters more than it sounds: with the icon visible, `돌` in a multiple-choice list is a picture
puzzle, not a reading exercise.

## Translations in quests

By default the quest text is Korean only: tracker, step cards, sidequest, finale. The journal's
**Optionen** tab has *Übersetzungen in Quests* (*Translations in quests*), which adds one line under each
Korean one — in English or German, whichever *Sprache* is set to. Turn it on for the first week, then off;
the point is to stop needing it.

The **Danger of overuse:** if you leave the German line on, your eyes will read it first. The mod cannot
stop you, but the hint key is the intended crutch, and it is a slower one.

## Reading the hint like a teacher

- Try the quest sentence for ten seconds before pressing `H`. Even a wrong guess primes the word.
- If the hint's explanation itself contains a word you do not know, look it up in the journal's
  dictionary — descriptions are written with early-tree vocabulary, so this should be rare.
- After the sidequest, say the word out loud once. The mod cannot hear you; your brain can.

Next: [the journal](journal.md) is where all of this is recorded.
