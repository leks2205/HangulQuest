# BATCH-007 plan: the farm, husbandry advancements (level 1)

Brief: `content/briefs/BATCH-007.md` (on top of BATCH-001, BATCH-004, BATCH-005 and the BATCH-006 model). Fourteen
level-1 `adv_husbandry_*` trails; ids, `order`, `requires` and `koreanLevel` unchanged, step ids kept (s1, s2, finale; s3 is new).
Built on phase0 after generator wave 4. Five trails whose advancement needs a structure, the Nether or a rare item are
retitled around a related farm word (`silk_touch_nest`, `allay_deliver_item_to_player`, `obtain_sniffer_egg`,
`place_dried_ghast_in_water`, `uh_oh`); the other nine keep their official titles.

## Baseline and result

| metric | before | after |
|---|---|---|
| unknownOverOneShare | 0.051133 | 0.050032 |
| meanUnknown | 0.9579 | 0.9513 |
| bare share, level 1 | 0.1305 | 0.0630 |
| top goal-type share, level 1 | 0.1619 | 0.1310 |
| duplicate signatures | 13 | 13 |
| distinct goal types, level 1 | 19 | 25 |
| object-only repeats | 145 | 139 |
| most repeated (verb, object) pair | 6 | 6 |

## Trails

| id | title | steps (goal) |
|---|---|---|
| root | 농사 | HARVEST wheat (clear weather), CRAFT hay bale (roofed), PLACE hay by the fence, SMELT meat teaching 요리하다 (day) |
| breed_an_animal | 아기는 어떻게 태어나? | BUILD fence ring, CRAFT gate, USE gate CLOSE, BREED a cow holding wheat |
| tame_an_animal | 인생의 동반자 | ACQUIRE bones, WATCH wolf, GIVE bones, TRANSIT in |
| plant_seed | 씨앗이 자라나는 곳 | WATCH water, PLANT wheat near water, WAIT beside the crop, HARVEST |
| fishy_business | 강태공이 세월을 낚듯 | GO river with a rod, WATCH water, ACQUIRE fish, EAT fish |
| safely_harvest_honey | 벌집을 내 집처럼 | CRAFT bottle, WATCH bee, WATCH nest, ACQUIRE honey |
| ride_a_boat_with_a_goat | 염소 떴소 | CRAFT boat, WATCH goat on a mountain, GO sea, MOUNT boat |
| tadpole_in_a_bucket | 양동이에 올챙이 한 마리 | GO swamp, WATCH frog, SWIM, ACQUIRE water bucket |
| make_a_sign_glow | 밝은 말 고운 말 | ACQUIRE ink, PLACE sign by a door, WATCH sign holding ink, USE sign (write) |
| silk_touch_nest | 벌들의 정원 | ACQUIRE flowers, PLANT near the nest, WAIT, BREED a bee |
| allay_deliver_item_to_player | 작은 창고 | CRAFT barrel, PLACE roofed, PUT_IN potatoes, TAKE_OUT |
| obtain_sniffer_egg | 닭장의 아침 | BUILD log wall, WAIT near a chicken, ACQUIRE eggs, BREED a chicken |
| place_dried_ghast_in_water | 들판의 말 | GO plains, ACQUIRE hay, GIVE hay to a horse, MOUNT horse |
| uh_oh | 농부와 거래 | ACQUIRE wheat, HOLD_WHILE walking, WATCH villager, TRADE sell wheat |

All new wave-3 goals are used: BREED (3), MOUNT (2), TAKE_OUT, TRADE (sell), WAIT (3), USE_BLOCK CLOSE, SMELT with 요리하다.
TRADE buy (사다) is not used: a buy needs an emerald a day-one player may not have. Authored Korean: root none,
plant_seed s3 and s2 hint, make_a_sign_glow finale (and its finale line), ride_a_boat s3 hint.
