Lexicon spine tagger (roadmap WP3.1)

What it does
  tag_lexicon.py tags every concept of concepts.json with curriculum data and writes
    src/main/resources/assets/hangulquest/data/lexicon-tags.json   (the only output that ships)
    content/review/lexicon-tags-review.tsv                          (sheet for the human / second-model review)
    content/curriculum/core-gap.csv                                 (with --core-gap: common words the mod lacks)

Run
  tools/curriculum/fetch_lists.sh /somewhere/outside/the/repo
  python3 -I tools/curriculum/tag_lexicon.py --lists /somewhere/outside/the/repo --core-gap
  python3 -I tools/curriculum/tag_lexicon.py --check          # no lists needed: ids and Korean words still in sync?

Files
  tag_lexicon.py      matching (exact, alias, derivation, compound parts), origin, hanja, mcOnly, output
  reflists.py         readers for the three downloaded lists (stdlib only)
  overrides.py        hand-checked words: homographs, Minecraft coinages, words no list contains, per-concept pins
  semsets.py          semantic sets and the Minecraft-only rules
  core_gap.py         builds core-gap.csv from core_gap_seed.tsv (anchors are checked against names.json)

Sources (downloaded, not committed)
  1. data.go.kr 15152177, "한국어교수학습샘터 어휘 기본정보", 국립국어원 (registered 2025-11-12). 10,634 rows with the level
     초급 / 중급 / 고급, which is the standard curriculum word list (1,836 + 3,855 + 4,943; the curriculum counts
     735 + 1,100 | 1,655 + 2,200 | 2,365 + 2,580 = 10,635). The split into the single 급 is not published in this file.
     Terms: KOGL type 4 (attribution, no commercial use, no changes).
  2. 한국어 학습용 어휘 목록, 국립국어연구원 2003, 5,965 words graded A/B/C, with frequency rank and some hanja.
     korean.go.kr 자료실 etc_seq=70 (txt) / 71 (xls). Terms: KOGL type 1 (attribution).
  3. 2023년 국어 기초 어휘 선정 및 어휘 등급화 연구, 국립국어원, list of 40,000 words in 5 grades with word origin
     (고유어/한자어/외래어/혼종어) and hanja. korean.go.kr reportData 1160. Terms: spreadsheet KOGL type 1.

Fields of lexicon-tags.json (per concept id)
  ko               the Korean word the tags were made for (--check compares it with concepts.json)
  curriculumBand   초급/중급/고급 of the standard curriculum word list = 1-2/3-4/5-6급 (null: not in the list)
  curriculumGrade  1-6; an ESTIMATE inside the band (easier first, by 2023 grade and 2003 frequency rank, cut at the
                   official 급 sizes). The band is the reliable part.
  nikl             A/B/C of the 2003 list
  basicGrade       1-5 of the 2023 basic vocabulary (frequency based, for native speakers)
  freqRank         rank in the 2003 list (corpus frequency, smaller is more frequent)
  coreParts        [{word, grade}] parts of a compound or of an X하다 verb
  aliasGrades      grades found for aliases of the concept (the learner sees ko, so they do not set the grade)
  origin           native | sino | loan | mixed | mc_only (follows the standard dictionary's 고유어/한자어/외래어/혼종어
                   label; mc_only = coined for the game or transliterated game term without everyday use)
  hanja            one character per entry, only for sino and mixed words, only when certain
  semSet           semantic set, see semsets.py
  mcOnly           the word or phrase is not met outside Minecraft
  homophones       other words with the same spelling (hanja or "native")
  matchType        exact | alias | derived | head | none
  confidence, note why a tag is not certain

Re-running after concepts.json changed: tag_lexicon.py --check names the concepts that need it.
