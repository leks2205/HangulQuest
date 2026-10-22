# Content brief BATCH-011: sea and harbour (level 2)

**Rules:**
- `BATCH-008.md` applies in full: the level-2 rules (-(으)세요, at least 30% form-dependent steps, the level-2
  grammar, reachability) and everything it points to.
- Read `content/batches/BATCH-009/` for the latest generator weaknesses.

**Trails (ids kept):** `nachthafen`, `riffpfad`, `kuestenwacht`, `meerestiefe`, `ozeanfahrt`, `w3_08`, `w4_01`,
`w4_02`, `w4_03`, `w4_04`, `w4_05`, `w4_07`, `w4_08`, `adv_husbandry_axolotl_in_a_bucket`,
`adv_husbandry_tactical_fishing` (15).

**Chapter feel:**
- The coast: a small harbour with a pier and a lighthouse, fishing at dusk, boats, the beach and a coral reef.
- The two husbandry trails follow their advancements:
  - 귀여운 포식자: an axolotl in a bucket. Axolotls live in lush caves, which are reachable by week one.
  - 이 대신 잇몸으로: a bucket of fish.
- `meerestiefe` and `ozeanfahrt` must not need an ocean monument. Keep them to open sea, a boat trip and an island.

## Grammar to stress here

| Point | How to use it |
|---|---|
| `L2.euro_direction` (으)로, direction | 바다로 가세요, 섬으로 가세요 |
| `L2.euro_material` | build the pier: 판자로 다리를 만드세요; the lighthouse: 돌로 기둥을 쌓으세요 |
| `L2.eseo_activity` vs 에 | fishing from the pier vs placing on it: 다리 위에서 낚시하세요 / 다리 위에 등불을 놓으세요 |
| `PLACE_ON_PATTERN` on a pillar | the lighthouse light: 기둥 위에 등불을 놓으세요 |
| `SWIM`, `MOUNT` (boat), `HOLD_WHILE` (낚싯대를 들고) | as the scenes need them |

## Target words

바다, 강, 호수, 물, 배, 섬, 다리 (bridge, pier), 물고기, 생선, 새, 하늘, 해, 달, 별, 등불, 깊다, 넓다, 멀다, 차갑다, 타다,
수영하다, 잡다, 기다리다.

## Coordination

- BATCH-010 may run in parallel. Leave slack: add no duplicate signatures, and get `unknownOverOneShare` strictly
  lower than at your start.
- Generator wave 6 may land while you work. If it does, run `git merge phase0`, regenerate, and re-read your lines.
- In `concepts.json`, insert entries only.

## Gates and handback

As in BATCH-008, with `content/batches/BATCH-011/`.
