# Content brief BATCH-009: the kitchen and the feast (level 2)

**Rules:** `BATCH-008.md` applies in full: the level-2 rules (-(으)세요, at least 30% form-dependent steps, the
level-2 grammar points, reachability) and everything it points to. `content/briefs/L2-PLAN.md` gives the plan.

**Trails (ids kept):** `saatfeld`, `festmahl`, `schmauspfad`, `kochkunst`, `milchpfad`, `brauerei`, `bienenzucht`,
`obstgarten`, `alchemiepfad`, `adv_husbandry_balanced_diet`, `adv_husbandry_wax_on`, `adv_husbandry_wax_off`,
`waldfest`, `kuestenfest`, `gartenfest` (15).

**Chapter feel:** growing, cooking and sharing food.
- Fields, the orchard and the beehive.
- Smoker, furnace and campfire cooking (요리하다, 굽다).
- A village feast where the player gives dishes to villagers (에게) and eats with friends (하고/와 같이).
- The two `wax_*` trails follow their advancements (밀랍을 칠하자 / 밀랍을 벗기자: honeycomb on copper, then an
  axe). Copper is reachable by the first week, so keep their titles.
- `alchemiepfad` and `brauerei` mean brewing, which needs Nether wart and blaze powder. Rebuild them as an
  overworld kitchen or cellar trail (pickling and storing, barrels, honey, berries) with new titles, or propose
  moving them to level 3. Keep the ids.

## Grammar to stress here

| Construction | Use it for | Example |
|---|---|---|
| `L2.eseo_activity` 에서 vs 에 | eating or cooking somewhere vs putting something somewhere | 모닥불 옆에서 고기를 구우세요 / 상자 안에 빵을 넣으세요 |
| `L2.ege` 에게 | giving food to villagers and animals | |
| `L2.hago_wa_gwa` 하고/와/과 | two ingredients | 밀과 설탕으로 케이크를 만드세요 (`with` ingredients in CRAFT) |
| `L2.go_sequence` -고 | SEQUENCE: cook, then eat | 고기를 굽고 드세요 |
| `L2.counter_more` | 그릇, 병, 개 | |

**드세요.** At level 2, 먹다 becomes 드세요 in the polite request. Check that the generator does this. If it
gives "먹으세요", record it as a generator weakness rather than authoring around it everywhere.

## Target words

음식, 고기, 생선, 채소, 과일, 사과, 감자, 당근, 빵, 케이크, 우유, 꿀, 설탕, 그릇, 접시, 냄비, 맛있다, 달다, 뜨겁다,
요리하다, 굽다, 먹다, 마시다, 주다, 받다, 사다, 팔다, 친구, 같이.

## Coordination

BATCH-008 (the smithy, level 2) runs in parallel. Both rewrite the generated registry and baseline files. Leave
slack: add no duplicate signatures, and get `unknownOverOneShare` strictly lower than the baseline you start from.
Keep `concepts.json` edits to inserted entries.

## Gates and handback

As in BATCH-008, with `content/batches/BATCH-009/`.
