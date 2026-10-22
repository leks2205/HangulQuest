# Content brief BATCH-016: the Nether I, crossing over (first level-3 batch)

**Rules.**
- `BATCH-008.md` applies (it covers everything from BATCH-001 on), but its level-2 rules are replaced by the
  **level-3 rules in `content/briefs/L3-PLAN.md`**. Read that plan first.
- `content/batches/BATCH-014/` is the latest model for how a batch folder looks.

**Trails (ids kept):** `adv_nether_root`, `adv_nether_distract_piglin`, `adv_nether_fast_travel`,
`adv_nether_find_bastion`, `adv_nether_find_fortress`, `adv_nether_obtain_ancient_debris`,
`adv_nether_obtain_crying_obsidian`, `adv_nether_return_to_sender`, `adv_nether_ride_strider`,
`adv_story_enter_the_nether`, `adv_story_form_obsidian`, `adv_nether_explore_nether`, `adv_nether_obtain_blaze_rod`,
`adv_nether_ride_strider_in_overworld_lava`, `vulkanpfad` (15).

**Level change, allowed in this batch only.** The first nine are level 2 today. Set their `koreanLevel` to 3, since
the Nether is out of reach at levels 1–2. Remove them from `reachabilityDebt` in
`src/test/resources/content-baseline.json` as the test demands. In the manifest, record the move for each trail.

**Chapter feel.** The first trip to the Nether:
- Obsidian from water and lava, then the portal and the first step through.
- Netherrack and soul sand; piglins and gold; a fortress and blaze rods; a bastion seen from afar.
- Riding a strider over lava; a ghast's fireball sent back; crying obsidian; ancient debris for the brave.
- `vulkanpfad` is the overworld volcano trail (lava and basalt). It leads into the chapter.

Keep the vanilla advancement titles: 네더, 반짝반짝 눈이 부셔, 천 리 길도 한 걸음, 그때가 좋았지, 끔찍한 요새, 깊이 파묻힌
잔해, 누가 양파를 써나?, 전해지지 않은 러브레터, 두 발 달린 보트, 지옥으로 간다, 아이스 버킷 챌린지 and the others.

## Grammar to stress here (level 3)

| Construction | How to use it |
|---|---|
| -기 전에 / -(으)ㄴ 후에 with SEQUENCE | "포털에 들어가기 전에 금 갑옷을 입으세요", "요새를 찾은 후에 블레이즈를 물리치세요". These are the main form-dependent steps: the order is checked. |
| -지 마세요 with AVOID | "피글린 근처에서 금을 벗지 마세요", "용암에 빠지지 마세요" (check which AVOID actions exist) |
| -(으)ㄹ 수 있어요 | in hints and finales: "이제 네더에 갈 수 있어요." |
| (으)로, instrument and direction | 곡괭이로, 포털로, 위로 |
| location nouns | — |
| 에게 / 에게서 | trading with piglins: 피글린에게 금을 주세요. Check how the generator speaks the piglin barter. |

## Target words

불, 뜨겁다, 무섭다, 위험하다 (if it is in the lexicon), 용암, 금, 문 (portal = 차원문? check `names.json` for 네더 차원문),
들어가다, 나오다, 찾다, 물리치다, 피하다 (if it is in the lexicon), 타다, 조심하다 (if it is in the lexicon), and
numbers with 마리.

## Coordination

- BATCH-015 (level 2) runs in parallel, and generator wave 9 may land while you work.
- Leave slack:
  - add no duplicate step signature;
  - `unknownOverOneShare` must end strictly lower than at your start;
  - the new metric `variety.formDependentShare.3` must rise.
- Insert entries only into `concepts.json`. Authored finales must appear in your `korean-lines.tsv`.

## Gates and handback

As in BATCH-008, with `content/batches/BATCH-016/`. Every trail gets a `grammar` array of level-3 ids from
`grammar.json`. In the handback, list any trail you propose for level 4 or above, with the reason.
