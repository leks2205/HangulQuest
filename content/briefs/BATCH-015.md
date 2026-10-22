# Content brief BATCH-015: the adventurer (level 2, last level-2 batch)

**Rules:**
- `BATCH-008.md` applies in full: the level-2 rules (-(으)세요, at least 30% form-dependent steps, level-2 grammar,
  reachability) and everything it points to.
- Read `content/batches/BATCH-014/`, the newest level-2 batch, and its "generator weaknesses".

**Trails (ids kept):** `adv_adventure_root`, `adv_adventure_kill_a_mob`, `adv_adventure_ol_betsy`,
`adv_adventure_avoid_vibration`, `adv_adventure_brush_armadillo`, `adv_adventure_heart_transplanter`,
`adv_adventure_lightning_rod_with_villager_no_fire`, `adv_adventure_minecraft_trials_edition`,
`adv_adventure_salvage_sherd`, `adv_adventure_trim_with_any_armor_pattern`, `adv_adventure_use_lodestone`,
`adv_adventure_voluntary_exile` (12).

**Chapter feel:** the first adventures. Weapons, a crossbow, fighting the first monsters at night, a lightning rod
for the village, an armadillo in the savanna, a lodestone and compass, a pillager patrol.

**Advancements and levels.** These trails follow vanilla advancements. Keep each advancement's title when the
advancement can be reached in week one or two:
- 모험 (root): reachable.
- 몬스터 사냥꾼: kill a mob. `DEFEAT_ENTITY` is allowed from level 2. Monsters are fine: 좀비, 스켈레톤, 거미, 크리퍼.
- 부러진 화살: shoot a crossbow. Reachable.
- 이게 인갑인갑다: brush an armadillo. Savanna or badlands; allowed, as BATCH-013 used the savanna.
- 번개 멈춰!: lightning rod. Needs a thunderstorm and a village; use `WEATHER` RAIN as the condition if thunder is
  too rare.
- 자진 유배: kill a raid captain. A pillager patrol is reachable, but the fight is hard; consider keeping the title
  with a softer path.

These need a structure or are too far for level 2:
- `minecraft_trials_edition`: a trial chamber.
- `salvage_sherd`: suspicious sand or gravel in trail ruins or a desert well.
- `trim_with_any_armor_pattern`: a trim template from a structure.
- `heart_transplanter`: a creaking heart in a pale garden.
- `avoid_vibration`: a sculk sensor in the deep dark, which is not allowed at level 1–2.
- `use_lodestone`: lodestone. Since 1.21 it is crafted from chiseled stone bricks and an iron ingot, so it is
  probably reachable; check the vanilla recipe.

For each trail that needs a structure, either:
- rebuild it as a week-one adventure trail with a new Korean title (keep the id), or
- propose it for level 3 in the manifest, with the reason, but do not change its level yourself.

## Grammar to stress here

| Point | Examples |
|---|---|
| `L2.ege` / `L2.ege_source` | 주민에게서 화살을 사세요, 철 골렘에게 철을 주세요 |
| `L2.euro_material` / instrument | 쇠뇌로 쏘세요, 활로 쏘세요 (check the shooting verb support) |
| `L2.go_held` | 방패를 들고, 횃불을 들고 |
| `L2.go_sequence` (ordered -고) | — |
| `NEAR_ENTITY` with a count | 좀비 두 마리 근처에서 |
| `DEFEAT_ENTITY` (물리치다) | — |

## Target words

밤, 무섭다, 싸우다, 물리치다, 화살, 활, 칼 (검), 방패, 갑옷, 투구, 몬스터 (if it is in the lexicon), 좀비, 거미, 해골
(스켈레톤), 번개, 비, 하늘, 집, 마을, 주민, 지키다 (if supported), 빠르다, 강하다 (if it is in the lexicon).

## Coordination

- Generator wave 9 may land while you work. It brings tool verbs, 입히다, falling, Sino seconds and a strict loader.
  Run `git merge phase0` before the final gates and re-read your generated lines.
- Leave slack: add no duplicate signatures, and leave `unknownOverOneShare` strictly lower than at your start.
- Insert entries only into `concepts.json`.
- Authored finales must be in your `korean-lines.tsv`.

## Gates and handback

As in BATCH-008, with `content/batches/BATCH-015/`. Every trail gets a `grammar` array.
