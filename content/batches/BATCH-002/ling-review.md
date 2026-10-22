# BATCH-002 linguist review (G2)

- `ling-review-claude.jsonl`: Claude, blind. 150 lines: 111 ok, 20 minor, 19 error.
- `ling-review-gemini.jsonl`: Gemini 3.8 Flash. 150 lines: 2 error, the rest ok or minor. Its rubric said which general
  words accept every variant (`covers`).

**Gemini's independence is in doubt.** The first Gemini run shared a folder with Claude's results. The rerun used a
separate folder but the same agy project, and its wording is close to Claude's. Treat this review as one and a half
opinions, not two. Later reviews run in a fresh agy project (`--new-project --sandbox`).

## Reconciliation

| Finding | Decision |
|---|---|
| Generic 꽃, 문, 침대, 묘목, 통나무, 판자 and 물고기 flagged as too broad | No change: `covers` makes these goals accept every variant. Claude's rubric did not say so. |
| Ceremonies name 참나무 원목 while the clue says 통나무 (bienenpfad, wurzelpfad) | Fixed by generator wave 2 (tag words) |
| 소리 블록을 울려요 | Fixed by generator wave 2 (치다) |
| dorfpfad s2 hint 주민과 종을 울려요 | Fixed: 주민 옆에서 종을 울려요 |
| steintiefe s1: 동굴 means only dripstone caves | Generator wave 3: biome covers, so 동굴 accepts dripstone and lush caves |
| redstonepfad s3: 밝혀요 for placing a torch | Generator wave 3: 밝히다 only when the goal lights something |
| wetterpfad s3: 물고기를 구워요 with a cooked-cod goal | Generator wave 3: SMELT widening by covered input |
| 철 for iron ore and ingots, 레드스톤 for the ore | Kept. These are the taught core words, and 철을 캐요 is natural. Queued for native review. |
