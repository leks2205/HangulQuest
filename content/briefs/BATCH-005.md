# Content brief BATCH-005: first tools, the market, night and the small poems (level 1)

**Rules:** everything in `BATCH-001.md` and `BATCH-004.md` applies (including "What the BATCH-002 review taught
us" and "Leave slack for the next batch"). Read both, then the "generator weaknesses" of
`content/batches/BATCH-004/manifest.json`.

**Trails (ids kept):** `nachtwache`, `firstpfad`, `marktpfad`, `gipfelblick`, `adv_story_root`,
`adv_story_mine_stone`, `w1_01`, `w1_02`, `w1_03`, `w1_04`, `w1_05`, `w1_06`, `w1_07`, `w1_08`, `w1_09` (15).

**Chapter feel:**
- `adv_story_root` and `adv_story_mine_stone` are the first-tools story: a wooden, then a stone pickaxe, the first
  stone, the first furnace. Keep them close to the vanilla "Minecraft" and "Stone Age" advancements.
- `marktpfad` is a village market morning: emeralds, a trade, bread.
- `nachtwache`, `firstpfad` and `gipfelblick` are a night watch and the view from the summit.
- The nine `w1_*` trails are small poems, each a mood in three steps. Keep their titles where the theme is reachable
  on day one: 첫 촛불, 노란 꽃밭, 달빛 산책, 겨울 창가, 작은 발견. Retitle the others: amethyst needs a geode, and
  a sea lantern needs ocean ruins. Make a mood out of ordinary things: a candle on a table at night, glass
  catching the sun, a lantern by the water.

## Target words

These were left over from BATCH-004 or are still under-exposed:

| Word | How to use it |
|---|---|
| 마시다, 우유 | `CONSUME_ITEM` milk; the cow comes first |
| 얼음 | — |
| 시계 | `HOLDING` a clock at night |
| 종이, 책 | — |
| 빵 | 빵을 먹어요 or 빵을 사요 |
| 초 | — |
| 달, 해, 밤, 낮, 하늘 | — |
| 산, 높이 | — |
| 집, 문, 창문 | — |
| 친구, 이야기하다 | with villagers only |
| 돌, 나무, 불 | — |
| 상자 | — |

Reuse the earlier batches' words too. If generator wave 3 has merged into `phase0` when you start (`git log --oneline
phase0 | grep -i "wave 3"`), also use its new verbs where a trail calls for them:
- 열다/닫다 for a door or chest;
- 꺼내다 for taking out of a chest;
- 나가다/들어가다 for leaving or entering the house;
- 요리하다;
- 사다/팔다, if it added them, in the market.

## Reachability

No Nether, no End, no structures, no rare biomes. A village counts as reachable (BATCH-004 used one). Each
`w1_*` trail is a root with no requirements: keep it playable from the first minutes. A new player has a wooden
pickaxe at best, so these trails need no iron.

## Coordination

Generator wave 3 may merge into `phase0` while you work. It adds concepts to `concepts.json`, so keep your
`concepts.json` edits to inserting entries; never reformat the file. Never edit a trail outside your list.

## Gates and handback

As in BATCH-001, with `content/batches/BATCH-005/` for plan, manifest and `korean-lines.tsv`. Run `git merge phase0`
again right before the final gates. If `phase0` moved, regenerate everything and re-read your generated lines.
