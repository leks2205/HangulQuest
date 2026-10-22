# Content brief BATCH-010: village, home and market (level 2)

**Rules:**
- `BATCH-008.md` applies in full: the level-2 rules (-(으)세요, at least 30% form-dependent steps, the level-2
  grammar, reachability) and everything it points to.
- Read the newest batch folders for the latest generator weaknesses: `content/batches/BATCH-008/` and `BATCH-009/`.

**Trails (ids kept):** `zuhausepfad`, `familienpfad`, `glockenpfad`, `uhrmacher`, `druckerei`, `karawanepfad`,
`handelsstrasse`, `adv_adventure_trade`, `w3_01`, `w3_02`, `w3_03`, `w3_04`, `w3_05`, `w3_06`, `w3_07` (15).

**Chapter feel:** life in the village. The player builds and furnishes a home, rings the bell, visits neighbours,
and buys and sells at the market. `adv_adventure_trade` follows its advancement (훌륭한 거래군요!): one real
`TRADE`. The seven `w3_*` trails are small neighbourhood scenes; keep their titles where they fit.

## Grammar to stress here

| Grammar | Where it fits |
|---|---|
| `L2.hago_wa_gwa` (하고/와/과) | companions and pairs: 주민하고 이야기하세요, 문과 창문 사이에 |
| `L2.ege` (에게) | gifts and trades: 주민에게 빵을 사세요 / 파세요 |
| `L2.eseo_activity` vs 에 | inside and outside the house: 집 안에서 기다리세요 vs 집 안에 침대를 놓으세요 |
| `L2.counter_more` | 명 for people (주민 두 명), 권 for books, 장 for paper |
| `L2.num_sino` | the clock: 시계 |
| `L2.eun_neun_contrast` | finale lines only, for a comparison: 우리 집은 작아요. 마을은 커요. |

Use the wave-3 verbs where the scene calls for them:
- `TRANSIT`: 들어가다, 나가다
- `USE_BLOCK` OPEN/CLOSE: 열다, 닫다
- `TAKE_OUT`: 꺼내다
- `TRADE`: 사다, 팔다
- `WAIT`: 기다리다
- `HEAR`: 듣다, with the jukebox or the bell

## Target words

사람, 친구, 가족, 아이, 이름, 집, 방, 문, 창문, 시장, 돈 (에메랄드 = 돈 in a flavour line only), 시계, 시간, 책, 종이, 그림,
공책, 사다, 팔다, 주다, 받다, 만나다, 이야기하다, 부르다, 듣다, 읽다, 쓰다 (write).

## Coordination

- Generator wave 6 is due before you start, and it changes how GIVE to villagers is detected; read its commit.
- BATCH-011 may run in parallel. Leave slack: add no duplicate signatures, and get `unknownOverOneShare` strictly
  lower than at your start.
- When you edit `concepts.json`, only insert entries.

## Gates and handback

As in BATCH-008, with `content/batches/BATCH-010/`.
