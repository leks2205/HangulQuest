# Content brief BATCH-002: village, music and friends (level 1)

**Rules:** everything in `BATCH-001.md` applies unless this brief says otherwise. Read it first, then
`content/batches/BATCH-001/manifest.json` (what worked, and the generator weaknesses that are now fixed).
**Trails (ids kept):** `bogenpfad`, `bergpfad`, `dorfpfad`, `wurzelpfad`, `musikpfad`, `bienenpfad`, `wetterpfad`,
`freundschaftspfad`, `briefpfad`, `redstonepfad`, `schneeballpfad`, `blumenpfad`, `steintiefe`, `waldhaus`,
`kerzenpfad` (15).
**Chapter feel:** the village, sounds and music, friends and weather; still day one to day three of a world.

## What changed since BATCH-001 (use it)

- **General words now accept every item they cover.** 꽃, 침대, 양털, 양탄자, 염료, 통나무, 판자, 문, 묘목, 나뭇잎,
  울타리 take any colour or wood (`covers` in `concepts.json`); 물고기 takes cod, salmon or tropical fish. Teach the
  general word and let the goal stay broad.
- **Counters come from `counters.json`** (종이 세 장, 꽃 세 송이, 책 세 권, 소 두 마리) — no need to avoid counted goals.
- **New level-1 verbs that the player enacts:** 올라가다 (`REACH_HEIGHT` at level 1: "언덕 위로 올라가요"),
  들어가다 (`REACH_BIOME` into a cave biome: "동굴로 들어가요"). Use both at least once.
- **The teaching rule is relaxed:** a step may teach any word the goal involves (object, anchor, recipient, held
  item, material, place), as long as the word appears in the sentence.
- **PLAYER_REL** takes an optional `kind` (`block`, `item`, `entity`); set it when an id is both an item and a mob.
- **Status lines** exist for `BUILD_PATTERN` and `PLACE_ON_PATTERN`; no need to add conditions just for a status.
- **Avoid flavour sentences that add unknown words early** ("해가 떠요." in front of a clue) unless the flavour word
  is one of the target words below; the coverage ratchet counts them.

## Target words (most under-exposed core words; recycle the BATCH-001 words too)

친구, 이야기하다, 올라가다, 마시다, 물고기, 비, 눈, 사과, 시계, 토끼, 종이, 해, 짓다, 유리, 초, 달, 상자, 바다, 물,
꿀벌, 나무, 침대, 말, 지도, 강, 세계 — plus the BATCH-001 words (나무, 돌, 물, 불, 집, 문, 빵, 횃불, 위, 아래, 옆, 안,
밖). Use 친구 and 이야기하다 with villagers (`INTERACT_ENTITY` on a villager is 이야기하다; the farmer dialogue exists).

## Gates and handback

As in BATCH-001, with `content/batches/BATCH-002/` for plan, manifest and `korean-lines.tsv`.
