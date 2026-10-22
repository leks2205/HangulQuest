# Content brief BATCH-013: animals and pets (level 2)

**Rules:** `BATCH-008.md` applies in full: the level-2 rules (-(으)세요, at least 30% form-dependent steps, the level-2
grammar and reachability), plus everything it points to. Read `content/batches/BATCH-012/`, the newest level-2 batch,
and the generator wave 8 merge commit (`git log -3`).

**Trails (ids kept):** `pferderennen`, `reitpfad`, `pferdehof`, `gestuet`, `adv_husbandry_bred_all_animals`,
`adv_husbandry_complete_catalogue`, `adv_husbandry_whole_pack`, `adv_husbandry_remove_wolf_armor`,
`adv_husbandry_repair_wolf_armor`, `adv_husbandry_leash_all_frog_variants`, `adv_husbandry_froglights`,
`adv_husbandry_kill_axolotl_target`, `adv_husbandry_allay_deliver_cake_to_note_block`, `adv_husbandry_feed_snifflet`,
`adv_husbandry_plant_any_sniffer_seed` (15).

**Chapter feel:** horses and stables, a pack of tamed wolves, cats and their catalogue, frogs in the swamp, and
breeding every animal on a farm.
- Keep each advancement's title when it can be reached in the first one to two weeks.
- Five advancements need things a level-2 player has no access to: `feed_snifflet` and `plant_any_sniffer_seed` need
  sniffer eggs, which come from ocean ruins; `kill_axolotl_target` is a fight; `froglights` needs magma cubes; `allay`
  needs an allay. For each of these:
  - rebuild it as a day-week animal trail with a new title, or
  - propose it for level 3 in the manifest. Do not change `koreanLevel` yourself.

## Use the new tools from wave 8

| Feature | What it gives you |
|---|---|
| Creature anchors | 말 옆에, 늑대 앞에, 고양이 뒤에. They are playtest-driven now, so they can ship. |
| `NEAR_ENTITY` with `count` | 소 세 마리 근처에서, with the 마리 counter. Use it at least twice. |
| `BREED`, `MOUNT`, `GIVE` (feed), `EQUIP` | wolf armour is EQUIP on a wolf? Check `docs/questlines.md`. Otherwise GIVE or USE. |
| 물고기 (fish) | covers the fish entities now, so watching or feeding fish works without a biome condition. |

## Target words

동물, 말, 소, 돼지, 양, 닭, 개 / 강아지 (wolf), 고양이, 개구리, 토끼, 새, 마리, 키우다, 타다, 주다, 먹이, 달리다,
빠르다, 크다, 작다, 귀엽다 (only if it is in the lexicon), 같이, 친구.

## Coordination

BATCH-014 runs in parallel. Leave slack: add no duplicate signatures, and get `unknownOverOneShare` strictly lower
than when you started. Insert entries only in `concepts.json`. Authored finales must be listed in your batch's
`korean-lines.tsv`; that is the new finale rule from wave 8.

## Gates and handback

As in BATCH-008, with `content/batches/BATCH-013/`. Every trail gets a `grammar` array.
