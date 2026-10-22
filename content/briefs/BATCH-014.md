# Content brief BATCH-014: colours, garden and treasure (level 2)

**Rules:** `BATCH-008.md` applies in full: the level-2 rules (-(으)세요, at least 30% form-dependent steps, the level-2
grammar and reachability), plus everything it points to. Read `content/batches/BATCH-012/` and the generator wave 8
merge commit (`git log -3`).

**Trails (ids kept):** `farbenpfad`, `faerberei`, `gerberei`, `gartenzaun`, `gartenkunst`, `gartenpfad`, `kartenpfad`,
`festungpfad`, `schatzkammer`, `glueckspfad`, `abenteuerpfad`, `adv_husbandry_obtain_netherite_hoe`,
`adv_story_lava_bucket`, `adv_adventure_read_power_of_chiseled_bookshelf`, `adv_adventure_crafters_crafting_crafters`
(15).

**Chapter feel:** colour and craft. Dyes and dyed wool, a dye workshop, a tannery, and a garden with a fence and
flower beds. Then a map, a small "fortress" the player builds (not a Nether fortress), and a treasure room.
- `adv_husbandry_obtain_netherite_hoe` needs netherite. Rebuild it as a garden-tool trail with a new title, or propose
  it for level 3. Keep the id either way.
- `adv_story_lava_bucket` (화끈한 화제) can be reached in week one. Keep it.
- `chiseled_bookshelf` (책의 힘) and `crafters_crafting_crafters` (제작기 제 자신 제작기) are craftable in week one.
  Check the recipes with the vanilla data.

## Grammar to stress here

| Construction | Example |
|---|---|
| colours as descriptors | 빨간색 양털, 파란색 염료 |
| colour contrast in finales | `L2.eun_neun_contrast`: 꽃은 빨간색이에요. 하늘은 파란색이에요. |
| (으)로 for material and instrument | 염료로 양털을 물들이세요, if the generator has a dye verb; otherwise record it as a weakness |
| 하고/와/과 for two colours or two materials | — |
| location nouns in the garden | 울타리 안에, 꽃 사이에, 문 앞에 |

## Target words

색, 빨간색, 주황색, 노란색, 초록색, 파란색, 보라색, 분홍색, 검은색, 하얀색, 회색, 갈색, 꽃, 정원, 지도, 책, 상자, 금, 보물
(only if it is in the lexicon), 예쁘다 (only if it is in the lexicon), 만들다, 칠하다 / 물들이다 (only if supported).

## Coordination

BATCH-013 runs in parallel. Leave slack: add no duplicate signatures, and get `unknownOverOneShare` strictly lower
than when you started. Insert entries only in `concepts.json`. Authored finales must be listed in your batch's
`korean-lines.tsv`.

## Gates and handback

As in BATCH-008, with `content/batches/BATCH-014/`. Every trail gets a `grammar` array.
