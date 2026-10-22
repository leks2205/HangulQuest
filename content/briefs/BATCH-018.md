# Content brief BATCH-018: heroes of the village (story, guilds and trade; level 3)

**Rules:**
- `content/briefs/L3-PLAN.md`, `BATCH-016.md` and `BATCH-017.md` apply.
- Read `content/batches/BATCH-017/`, the newest level-3 model, and its generator weaknesses.
- **Depth decides the level.** `koreanLevel` must equal the level the trail's tree depth gives
  (`Topik.levelForDepth`, `QuestlineValidationTest`). If a trail's depth says level 4, rebuild it at level 4 with the
  ending -아/어야 해요. Never change `requires` to dodge the rule without a reason a player would understand.

**Trails (ids kept):** `adv_story_cure_zombie_villager`, `adv_story_deflect_arrow`, `adv_story_enchant_item`,
`adv_story_follow_ender_eye`, `adv_story_shiny_gear`, `adv_adventure_hero_of_the_village`,
`adv_adventure_whos_the_pillager_now`, `adv_adventure_summon_iron_golem`, `adv_adventure_under_lock_and_key`,
`adv_adventure_totem_of_undying`, `handelsgilde`, `postpfad`, `fischergilde`, `karawane`, `village_errands_demo` (15).

**Chapter feel:**
- The village in danger, and the player as its hero. Cure a zombie villager (a splash potion of weakness and a golden
  apple), build an iron golem, defend against a raid, enchant the first item, follow an eye of ender towards a
  stronghold, and get diamond armour.
- The guild trails cover trade, mail and fishing.
- `village_errands_demo` is a demo trail. Rebuild it as a short village-errands sampler, and keep it marked as a demo.

## Grammar to stress here (level 3)

| Construction | Examples |
|---|---|
| -기 전에 / -(으)ㄴ 후에 | 주민을 치료한 후에 거래하세요, 습격 전에 철 골렘을 만드세요 |
| -지 마세요 | 주민을 때리지 마세요, 밤에 문을 열지 마세요 |
| -(으)ㄹ 수 있어요 | in finales |
| -(으)ㄹ 거예요 | in finales: 내일 요새를 찾을 거예요 |
| 에게 / 에게서 / 하고 | trade and companions |
| (으)로 | instrument: 활로, 방패로 |

## Target words

마을, 주민, 친구, 돕다 (if supported), 지키다 (if supported), 고치다 / 치료하다 (if supported), 편지 (if supported),
책, 사다, 팔다, 주다, 받다, 금, 사과, 물약, 약하다 / 강하다 (if supported), 무섭다, 밤.

## Coordination

- BATCH-019 runs in parallel.
- Leave slack: add no new duplicate signatures, and keep `unknownOverOneShare` strictly lower than at your start.
  `formDependentShare.3` must rise.
- In `concepts.json`, insert entries only. Put authored finales in your `korean-lines.tsv`.
- Use `tools/gates.sh`, run unpiped.

## Gates and handback

As in BATCH-016, with `content/batches/BATCH-018/`.
