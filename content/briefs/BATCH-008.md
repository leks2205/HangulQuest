# Content brief BATCH-008: fire and iron, the smithy (level 2, first L2 batch)

**Rules:** `BATCH-001.md` and the lessons in `BATCH-004.md` and `BATCH-005.md` apply ("What the BATCH-002 review
taught us", "Leave slack for the next batch"). This is the **first level-2 batch**, so `content/briefs/L2-PLAN.md`
applies too, and the level-2 rules below replace the level-1 habits. Read `content/batches/BATCH-006/` and
`BATCH-007/` (once merged) for the latest generator weaknesses.

**Trails (ids kept):** `schmelzofen`, `schmelzpfad`, `steinherz`, `kohlenpfad`, `steinbruch`, `tiefenpfad`,
`schmiedepfad`, `schmiedefeuer`, `werkzeugpfad`, `steinmetz`, `ziegelpfad`, `adv_story_upgrade_tools`,
`adv_story_smelt_iron`, `adv_story_iron_tools`, `adv_story_obtain_armor` (15).

**Chapter feel:** the player's first week. Coal and iron, the furnace, stone tools then iron tools, the first
armour, bricks and the stonecutter, and a smithy next to the house. The four `adv_story_*` trails follow their vanilla
advancements (더욱더 좋게, 철이 철철 넘쳐, 이젠 철 좀 들어라, 차려입기); keep those titles.

## Level-2 rules (new)

1. **-(으)세요 is the clue ending.** The generator switches by `koreanLevel`. Authored clues at level 2 use
   -(으)세요 too, while finales stay in the past tense (-았/었어요).
2. **At least 30% of the batch's steps are form-dependent.** The form decides the action.
   `python3 tools/variety.py` reports `variety.formDependentShare.2`, which starts at **0**; your batch is the first
   to raise it. The goal types that carry deciding forms are:
   - `PLACE_REL`: 위/아래/옆/앞/뒤/안/밖/사이/근처 + 에
   - `PLACE_ON_PATTERN` relations
   - `GIVE`: 에게
   - `SEQUENCE`: -고 / -기 전에
   - `AVOID`: -지 마세요
   - `HOLD_WHILE`: 들고
3. **Grammar points for level 2** (ids in `grammar.json`):
   - `L2.eseo_activity` (에서 vs 에): pair them on purpose. In one step "화로 옆에서 빵을 드세요" (where you are
     acting); in another "화로 옆에 상자를 놓으세요" (where the thing goes).
   - `L2.go_sequence` and `L2.go_held`: -고
   - `L2.hago_wa_gwa`: 하고/와/과, as two materials or two anchors (문과 벽 사이에)
   - `L2.euro_material` (으)로 for material: 돌로 기둥을 쌓으세요
   - `L2.ege` 에게
   - `L2.counter_more`: 장, 권, 그루, 병
   - `L2.num_sino` Sino-Korean numbers for heights and seconds: 높이 십 블록. Use them only where the generator
     already prints them.
   - Each trail declares the grammar it practises in `grammar: [...]`. Introduce one new construction per trail at
     most.
4. **Reachability at level 2:**
   - allowed: iron tools, caves, villages, and any overworld biome a player finds in the first week;
   - not allowed: the Nether, the End, strongholds, trial chambers, ancient cities and ocean monuments;
   - if a trail's advancement needs one of these, propose moving the trail to level 3 in the manifest. Do not
     change its `koreanLevel` yourself.
5. **Words.** Level-2 words come from `recycling-gaps.csv`, filtered to grade 1–2. For this batch:
   - things: 철, 금, 석탄, 돌, 벽돌, 도구, 칼, 곡괭이, 도끼, 삽, 갑옷, 투구, 바지, 신발, 불, 화로, 용광로;
   - adjectives: 뜨겁다, 차갑다, 무겁다, 가볍다, 단단하다;
   - verbs: 만들다, 녹이다, 굽다, 캐다, 쓰다 (use), 입다, 벗다, 바꾸다;
   - numbers: 하나…열.

   Adjectives fit flavour lines and the descriptor slot: "뜨거운 용암 옆에…" is level 5 adnominal, so at level 2
   use "용암은 뜨거워요." as a flavour sentence.

## Coordination

BATCH-007 (level-1 husbandry) may still be running; it touches different trails. Keep `concepts.json` edits to
inserted entries. Never edit a trail outside your list.

## Gates and handback

As in BATCH-001, with `content/batches/BATCH-008/`. Run
`/tmp/claude-1000/-home-lenni/79cfd1ae-df38-471c-aaed-b99a5d781be9/scratchpad/gates.sh` from your worktree; it must
end with "ALL GATES GREEN". Run `gates.sh rebaseline` once your metrics are final. Run `git merge phase0` again right
before the final gates. In the handback, report `formDependentShare.2` before and after, and give the share for your
own steps.
