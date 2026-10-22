# Pronunciation brackets: dual AI review (G2), 2026-10-22

Scope: 801 rows, made up of every bracket that differs from the spelling plus the gold table. Claude and Gemini 3.8
Flash reviewed them blind, each in its own agy project. Gemini's first two runs reused Claude's files; this review
uses only the third run.

## Fixed

| Word | Change | Who found it | Basis |
|---|---|---|---|
| 창살 | [창쌀] | both | dictionary |
| 밀짚 | [밀찝] | both | dictionary |
| 숨결 | [숨껼] | both | dictionary |
| 철사 | [철싸] | both | dictionary |
| 깃털 | [기털] | Gemini | 원칙 form of [기털/긷털] (제30항) |
| 황톳빛 | [황토삗] | Gemini | 원칙 form, as 잔딧빛 [잔디삗] |
| 긁개 | [글깨] | Gemini | 제11항 다만 |
| 흑요석 | [흐교석] | Gemini | no ㄴ-insertion, as in 흑연 [흐견] and 석유 [서규] |

The first four were fixed earlier, in generator wave 2. The remaining four are fixed here.

## Not changed (needs a native decision)

- **Loanwords:** 박스 and 벡스 keep [박쓰] and [벡쓰]. Gemini says loanwords take no tensification. Tensing after an
  obstruent is automatic in speech, and 표준 발음법 does not cover loanwords.
- **Assimilation across a space:** for example 빨간색 현수막 [빨간새켠수막] and 현수막 무늬 [현수망 무니]. Gemini
  calls these errors. Standard rules 제12항 붙임, 제15항 and 제18항 붙임 apply them when a phrase is spoken as one
  unit. Whether item names count as one unit is a style choice. The question is queued for native reviewers.
  Linking across a space is not applied today (진홍빛 울타리 [진홍빋 울타리]), which is inconsistent with the
  other two rules.

## The crossWord switch (generator wave 4)

`pronunciation-exceptions.json` has a `crossWord` key that decides how the sound rules treat two words of a phrase. The
default is `mixed`, which is what the generator did before the switch existed; the native reviewers pick one of the three.

| Value | Liaison (연음) | ㅎ aspiration | Nasalisation (비음화) |
|---|---|---|---|
| `mixed` (default) | only before ㅏ ㅓ ㅗ ㅜ ㅟ, after ㄱ ㄷ ㅂ sounds (표준 발음법 제15항: 밭 아래 [바다래]); a ㅎ final is untouched | ㅎ as the initial of the second word is applied (옷 한 [오탄]) | applied |
| `all` | every final is carried over to a vowel, a ㅎ final aspirates or goes silent, as inside a word | as `mixed`, plus a ㅎ final | applied |
| `none` | not applied: each word keeps its own sounds and only its final is neutralised | not applied | not applied |

Note: the sentence above, that linking across a space is not applied (진홍빛 울타리 [진홍빋 울타리]), no longer holds: under
`mixed` that phrase is [진홍비 둘타리] because 울 starts with ㅜ; the carry-over stays out only before the other vowels
(진홍빛 이끼 [진홍빋 이끼]). So today's mix is the one 제15항 describes, and the three rules are inconsistent only in this sense.

133 concept phrases of the game read differently under the three modes. The 20 that occur most often in the generated
text (clues, hints, finales), with the count of their occurrences:

| Phrase | Count | `mixed` | `all` | `none` |
|---|---|---|---|---|
| 밀랍칠한 약간 녹슨 구리 | 23 | [밀랍칠한 약깐 녹쓴 구리] | [밀랍칠하 냑깐 녹쓴 구리] | [밀랍칠한 약깐 녹쓴 구리] |
| 빨간색 양털 | 13 | [빨간색 양털] | [빨간새 걍털] | [빨간색 양털] |
| 파란색 양털 | 11 | [파란색 양털] | [파란새 걍털] | [파란색 양털] |
| 윤나는 안산암 | 11 | [윤나는 안사남] | [윤나느 난사남] | [윤나는 안사남] |
| 아이템 액자 | 10 | [아이템 액짜] | [아이테 맥짜] | [아이템 액짜] |
| 주황색 양털 | 9 | [주황색 양털] | [주황새 걍털] | [주황색 양털] |
| 연두색 양털 | 9 | [연두색 양털] | [연두새 걍털] | [연두색 양털] |
| 보라색 양털 | 9 | [보라색 양털] | [보라새 걍털] | [보라색 양털] |
| 드래곤 알 | 9 | [드래곤 알] | [드래고 날] | [드래곤 알] |
| 초록색 양털 | 8 | [초록쌕 양털] | [초록쌔 걍털] | [초록쌕 양털] |
| 윤나는 응회암 | 8 | [윤나는 응회암] | [윤나느 능회암] | [윤나는 응회암] |
| 시련 열쇠 | 8 | [시련 열쐬] | [시려 녈쐬] | [시련 열쐬] |
| 빨간색 현수막 | 8 | [빨간새 켠수막] | [빨간새 켠수막] | [빨간색 현수막] |
| 달콤한 열매 | 8 | [달콤한 열매] | [달콤하 녈매] | [달콤한 열매] |
| 껍질 벗긴 아카시아나무 | 8 | [껍찔 벋낀 아카시아나무] | [껍찔 벋끼 나카시아나무] | [껍찔 벋낀 아카시아나무] |
| 청록색 양털 | 7 | [청녹쌕 양털] | [청녹쌔 걍털] | [청녹쌕 양털] |
| 빨간색 양탄자 | 7 | [빨간색 양탄자] | [빨간새 걍탄자] | [빨간색 양탄자] |
| 밀랍칠한 약간 녹슨 깎인 구리 | 7 | [밀랍칠한 약깐 녹쓴 까낀 구리] | [밀랍칠하 냑깐 녹쓴 까낀 구리] | [밀랍칠한 약깐 녹쓴 까낀 구리] |
| 노란색 양털 | 7 | [노란색 양털] | [노란새 걍털] | [노란색 양털] |
| 하얀색 현수막 | 6 | [하얀새 켠수막] | [하얀새 켠수막] | [하얀색 현수막] |
