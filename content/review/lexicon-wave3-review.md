# Lexicon wave 3: dual review decisions

Reviewers: C = Claude review (34 minors), G = Gemini review (1 error, 1 minor).
34 distinct findings (36 reviewer raises: 하나 and 쓰다 were raised by both).
Applied 33, rejected 1. Where the applied text differs from the suggestion, the reason is in the last column.

descKo rule: only grade-1 words, no entry word or word of the same root, identify the word before saying "not X".
Spelling: the existing lang files use American spelling (gray 52x, armor 13x, color/colors 7x, colour 3x, grey 0x),
so the majority wins and "colour" became "color". The brief assumed British.

## Korean fields (concepts.json)

| id | field | old | new | raised by | note |
|---|---|---|---|---|---|
| baby | descKo | 아주 어려요. | 아주 작은 사람이에요. 아직 말을 못 해요. | C | modified: "어린 사람" is the description of 아이 |
| leg | descKo | 걸을 때 써요. 강 위에도 있어요. | 몸 아래에 둘 있어요. 걸을 때 써요. | C | modified: the vague bridge hint is dropped as suggested, and a body cue is added (the bridge sense stays in the gloss) |
| shoes | descKo | 발에 신어요. | 걸을 때 발에 하는 거예요. | C | modified: the suggested 입는 is the wrong verb (신다); 신- is avoided |
| story | descKo | 있었던 일이나 지어낸 일을 말이나 글로 전해요. | 말이나 책으로 알아요. 재미있는 일이 나와요. | C | modified: the suggestion (말하거나 글로 쓰는 거예요) fits any text, not a story |
| picture | descKo | 벽에 걸어요. 예뻐요. | 선과 색으로 만들어요. 벽에 걸어요. | C | as suggested |
| key | descKo | 잠긴 곳을 열어요. | 작은 물건이에요. 문을 열 때 써요. | C | modified: a first sentence so it is more than the sentence itself |
| fruit | descKo | 달고 맛있어요. | 나무에서 나와요. 사과처럼 달고 맛있어요. | C | modified: 밭 is not grade 1; an apple example identifies it |
| lake | descKo | 물이 많이 있는 곳이에요. | 땅 안에 있는 큰 물이에요. 바다가 아니에요. | C | modified: positive identification first, "not the sea" second |
| time | sentenceKo | 시간을 봐요. | 시계로 시간을 봐요. | C | 시계 exists as a concept; DE sentence follows (see below) |
| one_day | descKo | 낮과 밤을 합한 시간이에요. | 낮과 밤이 같이 있는 시간이에요. | C | modified: 아침부터 다음 아침까지 would reuse 아침 as a pointer; this stays closer to the original |
| left | descKo | 오른쪽이 아닌 쪽이에요. | 북쪽을 보고 서요. 해가 지는 쪽이에요. | C | modified: the heart and spoon cues are anatomy or handedness; the compass cue is correct in Minecraft (facing north, west is on the left) and reuses 서쪽's sun cue |
| right | descKo | 왼쪽이 아닌 쪽이에요. | 북쪽을 보고 서요. 해가 뜨는 쪽이에요. | C | modified: as for 왼쪽 (the spoon hand is wrong for left-handers) |
| north | descKo | 남쪽의 반대쪽이에요. | 지도의 위쪽이에요. | C | as suggested |
| south | descKo | 북쪽의 반대쪽이에요. | 지도의 아래쪽이에요. | C | as suggested |
| one | descKo | 둘보다 하나 적어요. | 처음 숫자예요. 1이에요. | G (error), C | G's 가장 첫 번째 수예요 uses 번째, which is harder; C's text taken |
| purple | descKo | 자수정 같은 색이에요. | 포도 같은 색이에요. | C | as suggested |
| meet | descKo | 다른 사람을 봐요. | 다른 사람이 있는 곳에 가요. 얼굴을 봐요. | C | modified: C's "앞에 가서 같이 있어요" is awkward |
| draw | descKo | 선으로 그림을 만들어요. | 손으로 선과 색을 만들어요. | C | modified: 연필 and 붓 are not grade 1 and not Minecraft; parallel to 쓰다's 손으로 |
| catch | descKo | 손으로 꽉 쥐어요. | 움직이는 것을 내 것으로 해요. 손으로 들기도 해요. | C | modified: the second sentence covers the "hold" sense in the gloss |
| bake | descKo | 불에 익혀요. | 불로 음식을 뜨겁게 해요. | C | as suggested |
| wear | descKo | 몸에 옷을 걸쳐요. | 옷을 몸에 해요. | C | modified: the suggestion (몸에 옷이 있게 해요) is not natural Korean |
| take_off | descKo | 입거나 쓴 것을 몸에서 떼어 내요. | 입거나 쓴 것을 몸에서 빼요. | C | as suggested |
| get_up | descKo | 자다가 눈을 떠요. | 누워 있다가 서요. | C | as suggested |
| ride | descKo | 탈것 위에 앉아서 가요. | 말이나 배 위에 앉아서 가요. | C | as suggested |

## EN glosses (en_us.json)

| id | old | new | raised by |
|---|---|---|---|
| head | head | head (also: hair) | C |
| boat | boat | boat, ship (also: belly, pear) | C |
| time | time | time (also: hour) | C |
| morning | morning | morning (also: breakfast) | C |
| evening | evening | evening (also: dinner) | C |
| raise | to raise, to breed | to raise, to keep (pets), to breed | C |
| write | to write, to use | to write, to use (also: to wear on the head) | C, G (modified: the "(also: …)" form as for the other homonyms, no "(a hat)" because 투구 is the course's example) |
| bake | to bake, to roast | to bake, to roast, to grill | C |
| ride | to ride | to ride, to take (a vehicle) | C |
| colour | colour | color | self (spelling majority) |

## DE glosses and sentences (de_de.json)

| id | field | old | new | raised by |
|---|---|---|---|---|
| head | gloss | Kopf | Kopf (auch: Haar) | C |
| sound | gloss | Geräusch | Geräusch, Ton | C |
| boat | gloss | Boot | Boot, Schiff (auch: Bauch, Birne) | C |
| time | gloss | Zeit | Zeit (auch: Stunde) | C |
| time | sentence | Auf die Uhrzeit schauen. | Auf der Uhr die Zeit ablesen. | C (matches the new KO sentence) |
| morning | gloss | Morgen | Morgen (auch: Frühstück) | C |
| evening | gloss | Abend | Abend (auch: Abendessen) | C |
| raise | gloss | aufziehen, züchten | aufziehen, halten (Haustiere), züchten | C |
| write | gloss | schreiben, benutzen | schreiben, benutzen (auch: aufsetzen) | C, G |
| hear | gloss | hören | hören, zuhören | C |
| bake | gloss | backen, braten | backen, braten, grillen | C |
| scary | gloss | unheimlich sein | gruselig, furchteinflößend sein | C |

## Rejected

| id | field | suggestion | raised by | reason |
|---|---|---|---|---|
| long | DE sentence | "Die Schnur ist lang." instead of "Die Leine ist lang." | C | 끈 is the concept `adv_item_lead`; the official Korean name of the Minecraft lead is 끈 and the German one is Leine. The sentence matches the game item. Faden is already the gloss of 실 (string). |

## Notes

- The wave-3 word list holds no English "grey"; the only British spelling in the course text is the trail title "The Colour Trail" (`hangulquest.trail.farbenpfad.title`). It is not a lexicon string and was left alone. Decide whether the trail title should follow the majority too.
- Both 아이 and 아기 use "작은/어린 사람"; 아기 now adds "아직 말을 못 해요" to tell them apart.
