# BATCH-004 plan: water, mountains, night sky and the village home (level 1)

Brief: `content/briefs/BATCH-004.md` (on top of BATCH-001 to 003). Fifteen level-1 trails; ids, `order`, `requires` and
`koreanLevel` unchanged, step ids kept (s1 to s3 and finale). Built on phase0 at generator wave 2 (authored clues on
conditioned steps, covers in the validator, water and lava as anchors, 밖에서, 쳐요).

## Baseline and result

| metric | before | after |
|---|---|---|
| unknownOverOneShare | 0.062865 | 0.061528 |
| meanUnknown | 1.007129 | 0.996114 |
| bare share, level 1 | 0.3543 | 0.2644 |
| top goal-type share, level 1 | 0.2362 | 0.2173 |
| duplicate signatures | 17 | 15 |
| novelty share | 0.4429 | 0.4571 |
| boredom index | 0.0837 | 0.0817 |
| object-only repeats | 157 | 152 |
| most repeated (verb, object) pair | 3 | 3 (nothing above 2 added) |

The old fifteen trails asked for one random block per step ("break a gray glazed terracotta, place a cracked nether
brick"). No step of the batch repeats the signature of another step; the two duplicates the old trails made
(brandung against inselpfad and riffpfad) are gone.

## Trails

| id | project | steps (goal) | relations / mechanics |
|---|---|---|---|
| wasserpfad 물의 길 | water at a river | REACH_BIOME river, ACQUIRE water bucket, PLACE_REL sapling next to water, LOOK_AT_ENTITY fish (+IN_BIOME river) | 물 옆에, bucket |
| lavapfad 용암의 길 | wall off a lava pool | BREAK coal (+Y_RANGE), ACQUIRE cobblestone (+HOLDING torch), PLACE_REL next to lava, PLACE_REL on top of lava (+HOLDING torch) | 옆, 위, water/lava anchors |
| blumentopf 화분의 길 | a flower pot at the door | ACQUIRE flowers, CRAFT pot, PLACE_REL next to door, GIVE pot to a villager friend | 옆, GIVE |
| schildkroete 거북의 길 | feed a sea turtle | REACH_BIOME beach, LOOK_AT_ENTITY turtle, ACQUIRE seagrass, GIVE seagrass to the turtle | GIVE |
| blumenkranz 꽃밭의 길 | a flower bed | BREAK flowers (SNAP), BUILD RING dirt, PLACE_ON_PATTERN flower inside, CRAFT yellow dye | 안, RING |
| sternenpfad 별의 길 | a night under the stars | PLACE campfire (authored 하늘이 어두워요), PLACE_REL bed next to campfire (authored 달이 떠요), CONSUME pork (+PLAYER_REL, night), USE bed at night | 옆, 하늘, 달 |
| muschelpfad 모래성의 길 | a sandcastle | BUILD PYRAMID sand, BREAK sand, CRAFT sign, PLACE_ON_PATTERN sign on top | PYRAMID, 위 |
| vogelscheuche 허수아비의 길 | a scarecrow | CRAFT hay, BUILD PILLAR hay, ACQUIRE carved pumpkin, PLACE_ON_PATTERN pumpkin on top | PILLAR, 위 |
| ziegenpfad 염소의 길 | a goat on the mountain | REACH_BIOME mountain, LOOK_AT_ENTITY goat, BUILD RING planks, INTERACT_ENTITY (feed) | RING |
| fischerpfad 물고기의 길 | catch, cook, share a fish | ACQUIRE fish (+IN_BIOME, day), SMELT (+night), PUT_IN chest, GIVE two fish to a cat | PUT_IN, GIVE, 마리 |
| gipfelpfad 꼭대기의 길 | the summit flag | REACH_HEIGHT at dawn (올라가요), BUILD PILLAR dirt, CRAFT banner, PLACE_ON_PATTERN banner on top | 올라가다, 위 |
| handelspfad 거래의 길 | trade with a villager | BREAK potatoes, GIVE potato (authored 이야기해요), ACQUIRE emerald (+PLAYER_REL), PUT_IN chest | 이야기하다, 옆 |
| dorfhaus 마을 집의 길 | the village house | BUILD RING (authored), PLACE_ON_PATTERN door next to wall, PLACE_REL carpet in front of door, PLACE_REL pot between door and wall, PLACE_ON_PATTERN bed inside | 옆, 앞, 사이, 안 |
| weidepfad 목장의 길 | the pasture | INTERACT horse (타요), GIVE wheat to a cow, SMELT beef, CONSUME beef | 말, 소, 소고기 |
| brandung 파도의 길 | a boat trip | ACQUIRE boat (+beach), REACH_BIOME ocean, LOOK_AT_ENTITY fish (+ocean), CRAFT map | 바다, 지도 |

## Constraints checked

- 61 steps, 1 bare (1.6 %); the top goal type is ACQUIRE_ITEM with 13 %; no SEQUENCE, AVOID, DEFEAT_ENTITY; overworld only.
- Inside a trail every step has a different goal type or relation.
- (verb, object) pairs: every pair the batch uses ends at 2 or fewer on the whole path (checked with a script against
  all other trails); colours of bed, carpet, banner and dye were picked so that no pair reaches 3.
- Sentences with more than one unknown word in the batch: schildkroete finale, muschelpfad s2, gipfelpfad s1 (before: 7).
  Two downstream sentences (glockenpfad s1) lost an encounter and are over one; net the share falls.
- Authored Korean: a few clues (the flavour lines 하늘이 어두워요 and 달이 떠요, 도착해요, 담아요, 이야기해요, the house lines, the villager gift), one finale, and authored hints that name the tool or the covered object (see the manifest for the exact list).
- Authored finale: dorfhaus only (generated would read "참나무 판자 울타리 안에 침대를 놓았어요").

## Known limits

- 마시다, 우유, 얼음, 시계, 종이, 책 and 빵 do not occur in a sentence of this batch: every pair that would teach them
  (DRINK milk bucket, CRAFT paper/book/clock, EAT bread, WATCH ice) was already at two, and a third would use the slack
  the next batch needs.
- Day-one reachability: lava (cave), seagrass (shears), goat (mountain), cat and villager (village) need a walk.
