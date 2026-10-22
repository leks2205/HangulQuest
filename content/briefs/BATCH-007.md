# Content brief BATCH-007: the farm (husbandry advancements, level 1)

**Rules:**
- Everything in `BATCH-001.md`, `BATCH-004.md` and `BATCH-005.md` applies, including the review lessons and "Leave
  slack for the next batch".
- `content/batches/BATCH-006/` is the most recent model: read its plan and manifest.

**Trails (ids kept):** `adv_husbandry_root`, `adv_husbandry_breed_an_animal`, `adv_husbandry_tame_an_animal`,
`adv_husbandry_plant_seed`, `adv_husbandry_fishy_business`, `adv_husbandry_safely_harvest_honey`,
`adv_husbandry_ride_a_boat_with_a_goat`, `adv_husbandry_tadpole_in_a_bucket`, `adv_husbandry_make_a_sign_glow`,
`adv_husbandry_silk_touch_nest`, `adv_husbandry_allay_deliver_item_to_player`, `adv_husbandry_obtain_sniffer_egg`,
`adv_husbandry_place_dried_ghast_in_water`, `adv_husbandry_uh_oh` (14).

**Chapter feel:** a first farm. Fields, animals, the beehive, a fishing rod and a pet.

**Titles.** Each title is the official Korean name of a vanilla advancement. Keep the title, and lead the trail
towards that advancement, when the advancement is reachable on day one:

| Reachable on day one | Advancement |
|---|---|
| root | 농사 |
| breed | 아기는 어떻게 태어나? |
| tame | 인생의 동반자 |
| plant_seed | 씨앗이 자라나는 곳 |
| fishy_business | 강태공이 세월을 낚듯 |
| safely_harvest_honey | 벌집을 내 집처럼 |
| ride_a_boat_with_a_goat | 염소 떴소: goats live on mountains; allowed |
| tadpole_in_a_bucket | needs a swamp; allowed |
| make_a_sign_glow | glow ink needs a glow squid; replace it with plain ink from a squid, or retitle |

These advancements need a structure, the Nether or a rare item:
- `silk_touch_nest`: silk touch
- `allay_deliver_item_to_player`: an allay from a pillager outpost or a woodland mansion
- `obtain_sniffer_egg`: ocean ruins
- `place_dried_ghast_in_water`: the Nether
- `uh_oh`: needs checking

For these, build a day-one farm trail around a related word and give it a new Korean title. Keep the id.

## Use the new wave-3 goals

This batch exists for them; use each at least once:

| Goal | Korean |
|---|---|
| `BREED` | 키우다: 소 두 마리를 키워요 |
| `MOUNT` | 타다: 말/돼지/보트 |
| `TAKE_OUT` | 꺼내다: from a chest or barrel |
| `TRADE` | 사다/팔다, with a farmer villager, if a village is in the chapter's story |
| `WAIT` | 기다리다: wait for the wheat or the baby animal |
| `USE_BLOCK` with `change` OPEN/CLOSE | 열다/닫다: the pen gate |
| SMELT food teaching 요리하다 | — |

Animals: 소, 돼지, 닭, 양, 말, 토끼, 고양이, 강아지 (wolf, puppy), 꿀벌, 염소, 물고기.

## Target words

Most under-exposed after wave 3 (see `build/reports/content/recycling-gaps.csv`):
- verbs: 키우다, 타다, 꺼내다, 넣다, 사다, 팔다, 기다리다, 자라다, 심다;
- animals and food: 고기, 생선, 채소, 과일, 계란 (달걀), 우유, 강아지, 새;
- counters and numbers: 마리, 둘, 셋, 다섯;
- others: 밭 (if in the lexicon), 땅, 물, 집.

The (verb, object) pair limit is tight for torches, campfires, candles, milk, bread and glass panes; avoid those.

## Coordination

Keep `concepts.json` edits to inserted entries. Never edit a trail outside your list.

## Gates and handback

As in BATCH-001, with `content/batches/BATCH-007/`. Run
`tools/gates.sh` from your worktree; it must
end with "ALL GATES GREEN". Run `gates.sh rebaseline` once your metrics are final. Run `git merge phase0` again right
before the final gates.
