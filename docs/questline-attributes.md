# Questline attributes — the complete checklist

> **MANDATORY.** Read this note before creating or editing any questline. Every **non-optional**
> item below must hold, even when you deliberately go outside the note's examples, branches or goal
> patterns. Content and style may deviate; structure, clarity and the previous/next links may not.
> The content tests enforce the mechanical parts (`QuestlineValidationTest`), and a failed test means
> the questline is not ready.

## 0. Mandatory vs optional

| Area | Mandatory (never skip) | Optional / free |
|---|---|---|
| Identity | unique `id`, non-blank `titleKo`, `titleDe`, `order`, badge | wording, topic name |
| Tree | valid `requires.completed`; **previous** unless level 1; **next** unless last level; no cycles | number of parents (1–3), branch shape |
| Korean level | `koreanLevel == min(5, depth)` and equal to peers on that level | which grammar examples are used |
| Steps | at least 3 steps, a finale, one clear `goal` per step, `teach` concepts, conditions spoken in Korean | riddle vs. plain clue, number of steps, extra German flavour |
| Clarity | the clue names the action and target; the text matches what the engine checks | exact phrasing, poetry |
| Reward | badge + words | reward size, extra celebration text |
| Validation | `./gradlew test` green | anything the tests do not check |


Every trail (questline) and every step has a fixed set of attributes. This note is the reference for
authoring and for the journal: what each field means, which are required, and what the UI must show.

## 0.1 Creating a quest — mandatory procedure

1. Read this note (and `docs/questlines.md`).
2. Pick a branch and its topic; choose 1–3 parents that already exist.
3. Compute the new depth and set `koreanLevel = min(5, depth)`.
4. Give the new trail a **next** if it is not on the last level, and a **previous** if it is not level 1.
   A trail that stops the tree early is invalid — the tests reject dead ends.
5. Write one `goal` per step and put every condition into the Korean sentence.
6. Make the clue name the action (place/use/collect/look/travel/fight/talk) and the target.
7. Add the finale and reward; run `./gradlew test`.
8. Only then add German flavour text and extras.

## 1. Identity

| Field | Where | Required | Meaning |
|---|---|---|---|
| `id` | trail | yes | Stable lowercase id; also the file name and the badge id. Never change it after release. |
| `titleKo` | trail | yes | Korean title, e.g. `봉우리의 길`. Shown in the tree, journal, banners. |
| `titleDe` | trail | yes | German title for the German toggle. |
| `topic` | trail | yes (authored, may be derived) | Overarching branch theme, e.g. `산`, `밤`, `바다`, `집`, `handwerk`. Must fit the whole branch, not just one step. |
| `tier` | trail | yes | `min(5, 1 + parent count)`. Kept for sorting/grouping. |

## 2. Position in the tree

| Field | Where | Required | Meaning |
|---|---|---|---|
| `requires.completed` | trail | yes, may be empty | Parent ids. Empty = root (level 1). 1–3 entries. |
| `order` | trail | yes | Sort key inside the journal and for auto-start; branch-consistent, gaps allowed. |
| **level / depth** | derived | — | `1` for roots, otherwise `1 + max(parent depth)`. Never stored. |
| **previous** | derived | — | The parent trails (`requires.completed`). **Empty only on level 1.** |
| **next** | derived | — | All trails that list this trail as a parent. **Empty only on the last level.** |
| `requires.minWordsKnown` | trail | yes | Vocabulary gate (number of known words). |
| `requires.dimension` | trail | yes | Dimension the trail starts in, e.g. `minecraft:overworld`. |

Rules:
- No cycles. A trail may have several parents (merges are the interesting nodes).
- A child must be deeper than every parent.
- First level: no previous. Last level: no next. Every other node must have both, otherwise the tree has a dead or floating branch.
- The journal tree draws exactly these edges; it does not invent extra links.

## 3. Korean level

| Field | Where | Required | Meaning |
|---|---|---|---|
| `koreanLevel` | trail | yes | TOPIK grammar band. **Must equal `Topik.levelForDepth(depth)`** and therefore match every other trail on the same tree level. |

TOPIK bands over 18 tree levels (gradual, not one TOPIK stage per tree level):

| Depth | TOPIK |
|---|---|
| 1–3 | 1 |
| 4–6 | 2 |
| 7–9 | 3 |
| 10–12 | 4 |
| 13–15 | 5 |
| 16–18 | 6 |

Ladder:
1. `-아/어요`
2. `-아/어야 해요`
3. `-아/어야만 해요`
4. `-지 않으면 안 돼요`
5. `-지 않고서는 … 수 없어요`
6. `-(으)ㄹ 수밖에 없어요` (level 6)

The finale uses level 5 (`… 이 길을 끝낼 수 없어요`). Hints always stay at level 1.

## 4. Theme / branch topic

- Each branch has one topic that all its trails share (mountain branch → `산`, night branch → `밤`,
  coast branch → `바다`, home branch → `집`, crafting branch → `손`, etc.).
- A trail's steps should all serve that topic; a step that does not fit belongs in another branch.
- The journal detail shows the title and level, and the tree shows the branch structure, so a
  mismatched topic is immediately visible as a stray node.

## 5. Steps — the goal (the condition that finishes the step)

Every step (`steps[]` and `finale.step`) has:

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | `s1`, `s2`, … / `finale`. |
| `teach` | yes | 1–3 concept ids; the first one supplies the noun in the sentence. |
| `goal` | yes | What must happen; see the goal table below. |
| `conditions` | no | Extra requirements that must hold *while* the goal happens. |
| `clue.ko` | no | Authored Korean clue; if absent, generated. |
| `clue.de` | no | German clue line (only with the German toggle). |

Goal types and their live status marker (what the tracker shows):

| Goal | Fields | Marker / evidence |
|---|---|---|
| `BREAK_BLOCK` | `block`, `count` | `x/n`; block families (ores incl. deepslate, logs, wool, …). |
| `PLACE_BLOCK` | `block`, `count` | `x/n`; volume diff around the click, block families. |
| `USE_BLOCK` | `block` | bed: in-bed state; others: use event. |
| `ACQUIRE_ITEM` | `item`, `count` | held/needed (`빵 1/2`), item families (beds, doors, wool, fish, …). |
| `CONSUME_ITEM` | `item`, `count` | `x/n`; inventory drop within 15 s of use. |
| `LOOK_AT_BLOCK` | `block`, `dwellMs` | dwell `%`; 1 s or 0.6 s. |
| `LOOK_AT_ENTITY` | `entity`, `dwellMs` | dwell `%`, explicitly “살아 있는 …”. |
| `REACH_BIOME` | `biome` (id or tag), `dwellMs` | current vs target biome. |
| `REACH_DIMENSION` | `dimension` | current vs target dimension. |
| `REACH_HEIGHT` | `minY`, `maxY` | `Y 62 / 0 이하` (negative) or `Y 62 / 140 위`. |
| `DEFEAT_ENTITY` | `entity`, `count` | `x/n`; attacked + died within 5 s. |
| `INTERACT_ENTITY` | `entity` | use/entity interaction. |

## 6. Conditions — current and needed markers

Conditions are checked continuously and must all be true for the goal to count:

| Condition | Korean marker | Live status |
|---|---|---|
| `TIME` (`DAY/NIGHT/DAWN/DUSK`) | `밤에`, `낮에`, … | `지금 낮 · 밤 필요` |
| `WEATHER` (`CLEAR/RAIN/THUNDER`) | `비가 올 때` | `날씨가 맞지 않아요 · 비` |
| `Y_RANGE` | `높이 0 이하에서` / `140블록 위에서` | `Y 62 / 0 이하` |
| `IN_BIOME` | `해변에서`, `산에서` | `지금 평원 · 산 필요` |
| `IN_DIMENSION` | `네더에서` | `지금 오버월드 · 네더 필요` |
| `NEAR_ENTITY` | `좀비 근처에서` | `가까이에 좀비 필요` |
| `NEAR_BLOCK` | `… 근처에서` | `가까이에 … 필요` |

Rules:
- **Every condition must appear in the Korean sentence** (generated or authored).
- Conditions are spoken with the same ladder level.
- Night actions are latched at event time, so a clock jump (sleep) does not discard them.
- The status line always answers “why is it not done yet?” or “how far am I?”.

## 7. Fluff and presentation

| Item | Rule |
|---|---|
| Clue | One Korean sentence; may be a riddle on level 1, but must name the action and target clearly. |
| German clue | Optional; shown only when the journal German toggle is on. |
| Hint | Generated from the same goal/conditions at level 1; always solvable. |
| Banner | Step/side/trail celebration; German only with the toggle. |
| Journal tree | Node = trail; colour = done/active/open/locked; hover = title; click = detail. |
| Detail card | Title, level, Korean level, state, previous, next, progress, live status, Start button. |
| Tracker | Clue + live status; content-sized; height shown as `Y …`, look as `%`, counts as `x/n`. |

## 8. Finale and reward

| Field | Required | Meaning |
|---|---|---|
| `finale.textKo` / `textDe` | yes | Closing line, spoken at level 5. |
| `finale.step` | yes | Last step; same goal/condition rules. |
| `reward.badge` | yes | Badge id (usually the trail id). |
| `reward.words` | yes | Concept ids rewarded; must be taught or used. |

## 9. Validation checklist (enforced by tests)

`QuestlineValidationTest` now fails on:
- a non-last-level trail without a child (dead end) or a non-first-level trail without a parent
- a missing German title, order, concept list or badge
- an authored clue that does not name the goal's action
- `koreanLevel` not equal to `min(5, depth)`
- unknown concepts/parents, cycles, non-Hangul clues, conditions missing from the Korean text.

- 100 trails, depth 18, every level 1–18 populated, no cycles, no orphan branches.
- `koreanLevel == Topik.levelForDepth(depth)`.
- Every step teaches concepts that exist; every goal item/entity/biome is covered by a taught concept.
- Every condition appears in the Korean clue.
- Finale exists and is level 5.
- First level has no previous; every other level has a next until level 18.
- Authored Korean clues are Hangul-only (digits/punctuation allowed).
- Tree edges match `requires.completed` exactly.
