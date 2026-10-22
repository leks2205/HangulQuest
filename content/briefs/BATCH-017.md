# Content brief BATCH-017: the Nether II, brewing, beacons and treasure (level 3)

**Rules:**
- `BATCH-016.md` and `content/briefs/L3-PLAN.md` apply: the level-3 rules and everything they point to.
- Read `content/batches/BATCH-016/` once it is merged, as the first level-3 model. If it is not merged yet, read
  `BATCH-015/`, the latest batch.

**Trails (ids kept):** `adv_nether_all_potions`, `adv_nether_brew_potion`, `adv_nether_charge_respawn_anchor`,
`adv_nether_create_beacon`, `adv_nether_get_wither_skull`, `adv_nether_loot_bastion`, `adv_nether_netherite_armor`,
`adv_nether_summon_wither`, `adv_nether_uneasy_alliance`, `adv_story_mine_diamond`, `kristallhoehle`,
`kristallschmiede`, `schmuckpfad`, `schmuckgilde`, `bergwerk` (15).

**Chapter feel:**
- Deep mines and the first diamonds.
- The jeweller's guild with amethyst and gems.
- Brewing the first potion at a brewing stand.
- A respawn anchor.
- Building a beacon.

**End-game advancements.** `all_potions`, `netherite_armor`, `summon_wither`, `get_wither_skull`, `uneasy_alliance`,
the full beacon and similar ones belong to level 4–6. For each of them, either:
- rebuild it as a level-3 step towards the goal (a first potion, a first piece of netherite gear, finding a wither
  skeleton), keeping the vanilla title only if the trail still earns that advancement; or
- propose it for level 4+ in the manifest.

Do not change `koreanLevel`.

## Grammar (level 3)

| Construction | Examples |
|---|---|
| -기 전에 / -(으)ㄴ 후에 (ordered SEQUENCE), the main form-dependent tool | 물병을 채운 후에 양조기에 넣으세요; 다이아몬드를 캐기 전에 횃불을 놓으세요 |
| -지 마세요 (AVOID) | — |
| -(으)ㄹ 수 있어요 in finales | 이제 물약을 만들 수 있어요 |
| (으)로 for instrument and direction | 철 곡괭이로 캐세요, 아래로 내려가세요 |

## Target words

- Materials: 보석 (if in the lexicon), 다이아몬드, 금, 철, 구리, 자수정
- Brewing: 물약, 물병, 마시다, 끓이다 (if supported)
- Light and depth: 빛, 밝다, 어둡다, 깊다, 아래, 내려가다
- Verbs and adjectives: 캐다, 찾다, 강하다 (if in the lexicon)
- Numbers: 일, 이, 삼 … for heights

## Coordination

- BATCH-016 (the Nether I) may still be running. Do not touch its trails.
- Leave slack: add no new duplicate signature. `unknownOverOneShare` must end strictly lower than at your start, and
  `formDependentShare.3` must rise.
- Only insert entries into `concepts.json`.
- Authored finales go in your `korean-lines.tsv`.
- Use `tools/gates.sh`, run unpiped.

## Gates and handback

As in BATCH-016, with `content/batches/BATCH-017/`. Every trail gets a `grammar` array.
