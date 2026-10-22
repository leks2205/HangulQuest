# Content brief BATCH-004: water, mountains, night sky and the village home (level 1)

**Rules:** everything in `BATCH-001.md` applies unless this brief says otherwise. Read it first, then
`content/briefs/BATCH-002.md` and `BATCH-003.md`, and the "generator weaknesses" sections of
`content/batches/BATCH-002/manifest.json` and `content/batches/BATCH-003/manifest.json`.
**Trails (ids kept):** `wasserpfad`, `lavapfad`, `blumentopf`, `schildkroete`, `blumenkranz`, `sternenpfad`,
`muschelpfad`, `vogelscheuche`, `ziegenpfad`, `fischerpfad`, `gipfelpfad`, `handelspfad`, `dorfhaus`, `weidepfad`,
`brandung` (15).
**Chapter feel:** the water's edge and the sea, the climb to the summit, a night under the stars, and a first real
house in the village. Still days one to five of a world; no structure hunts, no Nether, no rare biomes.

## What the BATCH-002 review taught us (use it)

The blind review of BATCH-002 found these problems. Do not repeat them:

- **A general word is right only when it covers the goal.** The words with `covers` in `concepts.json` (꽃, 침대,
  양털, 양탄자, 염료, 통나무, 판자, 문, 묘목, 나뭇잎, 울타리; 물고기 for raw cod, salmon and tropical fish) accept
  every variant. Any other goal on one concrete id needs that id's official name from `names.json`:
  - 철 광석 for ore, 철 주괴 for the ingot (never bare 철 for either)
  - 레드스톤 광석 for the ore
  - 익힌 대구 for cooked cod; 물고기 does not cover cooked fish
  - 점적석 동굴 for that biome, because bare 동굴 does not match any other cave
- **The ceremony line must name what the clue named.** The ceremony is generated from the goal. When the goal is
  a general word, check that the finale does not name a specific variant such as 참나무 원목. If it does, change the
  goal or the teach list.
- **Verb fit:** 밝히다 is for lighting up, not for placing a torch. 울리다 is for bells; note blocks are 쳐요
  (generator wave 2 fixes the generated forms).
- **Authored hints:** "주민과 종을 울려요" reads as "ring the bell together with the villager". Read every authored line
  once as a native would.

## What BATCH-003 taught us

- One taught word per step works best. A multi-word teach list needs an authored hint that names every noun.
- On a biome step, the teach list must start with the biome's concept.
- `IN_WATER` prints 물속에서 and does not give 수영해요. Do not pair it with `REACH_BIOME` (that gives
  "물속에서 바다로 가요"). Use it with a goal that reads well "underwater": look at a turtle or a fish, or collect
  sand or clay.
- Fluids cannot be anchors yet.

## Leave slack for the next batch (new)

Two batches merged in parallel each used the whole ratchet slack, and the merge needed fixes. Your batch must:

- add **no** step whose full signature (`variety.py` duplicate check) repeats an existing step anywhere on the
  learner path;
- leave `coverage.sentences.unknownOverOneShare` **lower** than the baseline you start from, not just equal.

`python3 tools/variety.py --duplicates` lists every repeated signature, after the `StepsDumpTest` has written
`build/reports/content/steps.json`.

## Target words

Most under-exposed core words (from `build/reports/content/recycling-gaps.csv`). Recycle the earlier batches'
words too:

물, 바다, 강, 물고기, 하늘, 달, 산, 높이, 마을, 집, 말, 소, 돼지, 고양이, 우유, 마시다, 시계, 지도, 종이, 책, 얼음,
소고기 (cook beef; no `DEFEAT_ENTITY` at level 1), 돼지고기, 감자, 빵, 친구, 이야기하다 (with villagers only).

- **올라가다:** use it at least once, in `gipfelpfad`, the summit trail.
- **마시다:** comes from `CONSUME_ITEM` on milk or a water bottle.
- **The village home (`dorfhaus`)** should use several relations: 안, 밖, 앞, 옆 and 사이 (BETWEEN with `anchor2`).
  It builds on the BATCH-002/003 house pieces (door, glass pane, carpet).

## Coordination

BATCH-005 may run in parallel on other trails. Both batches rewrite `content/metrics-baseline.json`,
`content/korean/lines.jsonl` and `review-status.json`; the merge regenerates them. Never edit a trail that is not in
your list.

## Gates and handback

As in BATCH-001, with `content/batches/BATCH-004/` for plan, manifest and `korean-lines.tsv`.

Run `git merge phase0` again right before the final gates. If phase0 moved (generator wave 2 may land meanwhile),
regenerate everything and re-run the gates. A generator change can change your generated lines: read them again.
