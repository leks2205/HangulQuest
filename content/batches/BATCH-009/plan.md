# BATCH-009 plan: the kitchen and the feast (level 2)

Brief: `content/briefs/BATCH-009.md` on top of BATCH-008. Fifteen level-2 trails; ids, `order`, `requires` and
`koreanLevel` unchanged, step ids kept (new steps: `s3`/`s4`). Built on phase0 after BATCH-008 landed.

## Result

| metric | start | after merge with BATCH-008 | final |
|---|---|---|---|
| unknownOverOneShare | 0.053881 | 0.054313 | 0.0540 |
| sentences with 2+ unknown words in this batch | n/a | n/a | 0 |
| duplicateSignatures | 13 | 12 | 10 |
| formDependentShare.2 | 0 | 0.0458 | 0.0940 |
| this batch: form-dependent steps | 0 | | 23 of 65 (35.4%) |
| bareShare.2 | 0.6446 | | 0.4487 |
| topTypeShare.2 | 0.3620 | | 0.2799 |

## Trails (grammar)

| id | title | steps | grammar |
|---|---|---|---|
| saatfeld | 씨앗밭의 길 | CRAFT hoe, USE soil holding it, PLANT potatoes by water, GIVE potato to a pig | -세요, -고 (들고), 에게, 옆에 |
| festmahl | 잔치의 길 | ROW of planks, CRAFT cake (밀과 설탕으로), PLACE cake on the row, bake meat, eat by the cake | 에서/에, 와/과, (으)로, 위에 |
| schmauspfad | 산해진미의 길 | berries, pumpkin pie, GIVE berries to a fox, eat pie by a campfire | 에서, 에게, (으)로 |
| kochkunst | 요리의 길 | CRAFT smoker, place it, KINDLE campfire in front, cook meat by the smoker | 옆에/앞에, 에서 |
| milchpfad | 우유의 길 | CRAFT bucket, GIVE wheat to a cow outside, milk by the fence, PUT milk IN a chest | 에서, 에게, 안에 |
| brauerei | 지하 저장고의 길 (new title) | CRAFT barrel, lantern BETWEEN barrel and furnace underground, PUT_IN / TAKE_OUT berries | 사이에, 와/과, 에서 |
| bienenzucht | 양봉의 길 | CRAFT beehive, place by flowers, plant a flower by it, drink honey by the hive | 에서, (으)로, 옆에 |
| obstgarten | 과수원의 길 | fence RING, sapling INSIDE it, sapling by the fence, apples, eat by the sapling | 안에, 옆에, 에서 |
| alchemiepfad | 꿀단지의 길 (new title) | CRAFT glass bottle, harvest hive, CRAFT sugar, GIVE honey to a villager | 에서, 에게 |
| adv_husbandry_balanced_diet | 균형 잡힌 식단 | eat fruit, vegetable, fish, meat in four settings | 에서 vs time words |
| adv_husbandry_wax_on | 밀랍을 칠하자 | honeycomb, copper block, place twice (옆에, 위에), use it holding honeycomb | 들고, 옆에/위에 |
| adv_husbandry_wax_off | 밀랍을 벗기자 | stone axe, waxed copper block, place twice (앞에, 위에), use it holding the axe | 들고, (으)로, 앞에/위에 |
| waldfest | 숲의 잔치 | plank RING, mushroom INSIDE, CRAFT stew (그릇으로), GIVE stew to a villager | 안에, (으)로, 에게 |
| kuestenfest | 바닷가 잔치 | go to the beach, carpet on sand, GIVE seagrass to a turtle, bake fish, eat on the carpet | 위에, 에게, 에서 |
| gartenfest | 정원 잔치 | CRAFT cake, place it on planks, BUY apples, GIVE an apple (친구하고 같이 in the finale) | 에게, 위에, 하고 |

Reachability: all steps are overworld day-one to week-one (copper, honeycomb, smoker, barrel). `bienenzucht`,
`gartenfest` and `saatfeld` left `reachabilityDebt`. No trail moved to level 3: `brauerei` and `alchemiepfad` became overworld
cellar and honey trails.
