# Content brief BATCH-006: weather and seasons, the w2 poems (level 1)

**Rules:**
- Everything in `BATCH-001.md`, `BATCH-004.md` and `BATCH-005.md` applies, including the review lessons and "Leave
  slack for the next batch".
- `content/batches/BATCH-005/` is the closest model: it rebuilt the nine w1 poems. Read its plan and manifest first,
  and avoid every generator weakness listed there.

**Trails (ids kept):** `w2_01`, `w2_02`, `w2_03`, `w2_04`, `w2_05`, `w2_06`, `w2_07`, `w2_08` (8).

**Chapter feel:** weather, time of day and the seasons. Each trail is one mood in three steps. Keep a title when
its theme can be reached on day one, and retitle the others:

| Trail | Title | Notes |
|---|---|---|
| `w2_01` | 비 그친 하늘 | |
| `w2_02` | 첫눈 소리 | |
| `w2_03` | 해질녘 언덕 | |
| `w2_04` | 천둥의 언덕 | thunder is rare; prefer rain, `WEATHER RAIN` |
| `w2_05` | 계절의 문 | |
| `w2_06` | 별이 뜨는 밤 | |
| `w2_07` | 겨울 아침 | |
| `w2_08` | 구름 위 탑 | build high, `REACH_HEIGHT` |

Use 비, 눈, 해, 달, 하늘, 높이, 밤, 낮, 아침 and 저녁 as the core words. The BATCH-005 manifest notes that DUSK and
DAWN are short (50 s and 100 s): use them only where the player is told to wait for that moment. A finale fits
best.

## Target words

These are still under-exposed (`build/reports/content/recycling-gaps.csv`):
- weather and time: 하늘, 높이, 비, 눈, 얼음, 해, 달, 아침, 저녁, 밤, 낮;
- objects: 시계, 종이, 책, 우유, 마시다, 창문, 집, 문, 초, 불;
- the earlier batches' words.

If generator wave 3 has merged into `phase0`, use its new verbs where they fit:
- 열다/닫다 (open or close a window shutter or a door);
- 나가다/들어가다 (step out into the rain);
- 기다리다 (wait for the night);
- 켜다 (light a candle).

## Reachability and coordination

- **Reachability:** no Nether, no End, no structures, no rare biomes. Snow needs a snowy biome or a mountain, so
  give a snow step an `IN_BIOME` condition or an anchor the player can make.
- **phase0 moves:** generator wave 3 may merge into `phase0` while you work. Keep `concepts.json` edits to inserted
  entries; never reformat the file.
- **Scope:** never edit a trail outside your list.

## Gates and handback

As in BATCH-001, with `content/batches/BATCH-006/`. Run
`tools/gates.sh` (add `rebaseline` once your
metrics are final); it stops at the first failure. Run `git merge phase0` again right before the final gates.
