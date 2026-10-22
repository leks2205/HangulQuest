# Content brief BATCH-019: bow, crossbow and trident, the hunter (level 3)

**Rules:** `content/briefs/L3-PLAN.md`, `BATCH-016.md` and `BATCH-017.md` apply. Read
`content/batches/BATCH-017/` (the newest level-3 model) and its generator weaknesses.

**Level follows depth.** `koreanLevel` must equal the level that the trail's tree depth gives (`Topik.levelForDepth`,
checked by `QuestlineValidationTest`). If a trail's depth says level 4, rebuild it at level 4 with the
-아/어야 해요 ending.

**Trails (ids kept):** `adv_adventure_arbalistic`, `adv_adventure_blowback`, `adv_adventure_bullseye`,
`adv_adventure_kill_all_mobs`, `adv_adventure_kill_mob_near_sculk_catalyst`, `adv_adventure_overoverkill`,
`adv_adventure_shoot_arrow`, `adv_adventure_sniper_duel`, `adv_adventure_spear_many_mobs`,
`adv_adventure_two_birds_one_arrow`, `adv_adventure_very_very_frightening`, `adv_adventure_throw_trident`,
`adv_adventure_revaulting`, `adv_adventure_lighten_up`, `wolfpfad` (15).

**Chapter feel:** the hunter's craft. The first arrow at a target, a crossbow, a long shot, a trident, the wolf
companion and a night hunt.

**Out of reach at this level:**
- The sculk catalyst (deep dark)
- Trial chambers (`revaulting`, `blowback`, `overoverkill`, `lighten_up` use trial or vault content)
- Channeling lightning (`very_very_frightening`)
- `kill_all_mobs`, the end-game sweep

Rebuild each of these as a level-appropriate hunter trail with a new title, or propose it for level 4+ in the
manifest. Do not change `koreanLevel` unless the depth demands it.

**Shooting.** No goal type "shoot at" exists yet. Use what does: DEFEAT_ENTITY with a weapon held (HOLDING), LOOK_AT
a target, PLACE_REL for a target range, and SEQUENCE. List what a SHOOT goal would add as a generator weakness.

## Grammar to stress here (level 3)

| Construction | Example |
|---|---|
| -기 전에 / -(으)ㄴ 후에 | 화살을 만든 후에 과녁을 맞히세요 (if supported) |
| -지 마세요 | 주민을 맞히지 마세요 |
| (으)로 for the instrument | 활로, 쇠뇌로, 삼지창으로 |
| -(으)ㄹ 수 있어요 | — |
| 멀리 / 가까이 | if they are in the lexicon |

## Target words

활, 화살, 쇠뇌, 과녁, 늑대, 개, 멀다, 가깝다 (if supported), 빠르다, 사냥, 물리치다, 밤, 숲, 산.

## Coordination

- BATCH-018 runs in parallel.
- Leave slack: no new duplicate signatures, and `unknownOverOneShare` strictly lower than at your start.
- Insert entries only in `concepts.json`; put authored finales in your `korean-lines.tsv`.
- Use `tools/gates.sh`, run unpiped.

## Gates and handback

As in BATCH-016, with `content/batches/BATCH-019/`.
