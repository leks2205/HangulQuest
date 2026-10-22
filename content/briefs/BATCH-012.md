# Content brief BATCH-012: mountains, ice and night (level 2)

**Rules:**
- `BATCH-008.md` applies in full: the level-2 rules (-(으)세요, at least 30% form-dependent steps, the level-2
  grammar, reachability) and everything it points to.
- Read `content/batches/BATCH-011/`, the newest level-2 batch, for the latest generator weaknesses.

**Trails (ids kept):** `wanderpfad`, `schneesturm`, `eiswand`, `gletscherpfad`, `gipfelwaechter`, `alpental`,
`w4_06`, `mondpfad`, `sternwarte`, `sternenhaus`, `w3_09`, `adv_adventure_sleep_in_bed`,
`adv_adventure_fall_from_world_height`, `adv_adventure_spyglass_at_parrot`, `adv_adventure_honey_block_slide` (15).

**Chapter feel:** high places and the night sky.
- A mountain hike and a snowstorm; an ice wall and a glacier; a mountain lake.
- A watchtower on the summit; an observatory with a spyglass; a house under the stars.
- The four `adv_adventure_*` trails follow their advancements, and they are all reachable in the first week:
  - 달콤한 꿈: sleep in a bed.
  - 동굴과 절벽: fall from the world height. This means climbing to build height or using a mountain, without
    dying.
  - 새인가?: a spyglass at a parrot. The parrot needs a jungle.
  - 달콤함에 몸을 맡기다: slide down a honey block.

## Grammar to stress here

| Grammar point | Use it for |
|---|---|
| `L2.euro_direction` | 위로 / 아래로 / 산으로 (올라가세요, 내려가세요) |
| ordered -고 `SEQUENCE` (form-dependent at L2 since wave 6) | 산 위로 올라가고 횃불을 놓으세요 |
| 위/아래/앞/뒤 of creatures and facing blocks | — |
| `L2.eun_neun_contrast` (finale lines only) | 낮은 따뜻해요. 밤은 추워요. |
| `L2.num_sino` | 높이 백 블록 |

## Target words

산, 높다, 낮다, 높이, 위, 아래, 눈, 얼음, 춥다, 차갑다, 밤, 달, 별, 하늘, 구름, 바람, 집, 침대, 자다, 눕다, 일어나다,
올라가다, 내려가다, 떨어지다, 보다, 기다리다.

## Coordination

- BATCH-010 (village, level 2) runs in parallel. Leave slack: add no duplicate signatures, and get
  `unknownOverOneShare` strictly lower than at your start.
- Keep the finale ratchet in mind: at least 70% of finales must be generated, and BATCH-011 left 302 of 422
  against a minimum of 296. Keep the share of 놓다 placements under its limit as well.
- Insert entries only into `concepts.json`.

## Gates and handback

As in BATCH-008, with `content/batches/BATCH-012/`. Give every trail a `grammar` array.
