"""Readers for the three public reference lists that tag_lexicon.py matches against.

The lists are NOT part of this repository (licence, see NOTICE section in the report of WP3.1).
Download them once into a directory of your choice and pass it with --lists. Expected layout:

    <dir>/nikl-learner-vocab-2017.csv     data.go.kr 15152177, columns: 수준,동형어번호,표출 어휘
    <dir>/nikl-learning-vocab-2003.txt    korean.go.kr etc_seq=70, cp949 text, tab separated
    <dir>/nikl-basic-vocab-2023.xlsx      korean.go.kr reportData report_seq=1160, sheet "all"

Everything here is plain stdlib so the tool runs with `python3 -I`.
"""
from __future__ import annotations

import csv
import re
import zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
M = "{%s}" % NS["m"]
TRAIL_NUM = re.compile(r"\d+$")
HANGUL = re.compile(r"[가-힣]")
BAND_GRADES = {"초급": (1, 2), "중급": (3, 4), "고급": (5, 6)}
# official size of each 급 in the standard curriculum (735 + 1,100 + 1,655 + 2,200 + 2,365 + 2,580)
GRADE_SIZES = {1: 735, 2: 1100, 3: 1655, 4: 2200, 5: 2365, 6: 2580}


def strip_num(word: str) -> str:
    return TRAIL_NUM.sub("", word.strip())


def _col(ref: str) -> int:
    n = 0
    for ch in re.match(r"[A-Z]+", ref).group(0):
        n = n * 26 + ord(ch) - 64
    return n - 1


def read_xlsx_sheet(path: Path, sheet_xml: str) -> list[list[str]]:
    z = zipfile.ZipFile(path)
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS):
            shared.append("".join(t.text or "" for t in si.iter(M + "t")))
    rows = []
    for row in ET.fromstring(z.read(sheet_xml)).iter(M + "row"):
        cells = {}
        for c in row.findall("m:c", NS):
            v = c.find("m:v", NS)
            if c.get("t") == "s" and v is not None:
                val = shared[int(v.text)]
            elif c.get("t") == "inlineStr":
                val = "".join(t.text or "" for t in c.iter(M + "t"))
            else:
                val = v.text if v is not None else ""
            cells[_col(c.get("r"))] = val
        if cells:
            rows.append([cells.get(i, "") for i in range(max(cells) + 1)])
    return rows


@dataclass
class Entry:
    word: str
    grade5: int | None = None       # 2023 basic vocabulary grade 1-5
    pos: str = ""
    origin: str = ""                # 고유어 / 한자어 / 외래어 / 혼종어
    hanja: str = ""                 # 원어 column, hanja for 한자어, source word for 외래어
    homonym: int = 0


@dataclass
class RefLists:
    bands: dict[str, set[str]] = field(default_factory=lambda: defaultdict(set))   # lemma -> {초급,..}
    nikl03: dict[str, list[dict]] = field(default_factory=lambda: defaultdict(list))
    basic23: dict[str, list[Entry]] = field(default_factory=lambda: defaultdict(list))
    grade_est: dict[str, int] = field(default_factory=dict)                        # lemma -> 1..6 (estimate)

    def band_of(self, lemma: str) -> str | None:
        b = self.bands.get(lemma)
        if not b:
            return None
        order = ["초급", "중급", "고급"]
        return min(b, key=order.index)       # a homograph in two bands counts as the easier one


def load_all(d: Path, use_band_list: bool = True) -> RefLists:
    """use_band_list=False leaves the 초급/중급/고급 list out (its terms are the strictest of the three)."""
    r = RefLists()
    # 2017 curriculum word list (three bands: 초급 = 1-2급, 중급 = 3-4급, 고급 = 5-6급)
    if use_band_list:
        with open(d / "nikl-learner-vocab-2017.csv", encoding="utf-8-sig", newline="") as f:
            for row in csv.DictReader(f):
                for part in row["표출 어휘"].split("/"):
                    lemma = strip_num(part)
                    if lemma and HANGUL.search(lemma):
                        r.bands[lemma].add(row["수준"].strip())
    # 2003 learning vocabulary (A/B/C, with frequency rank and sometimes hanja)
    raw = (d / "nikl-learning-vocab-2003.txt").read_bytes().decode("cp949")
    for line in raw.splitlines()[1:]:
        c = line.split("\t")
        if len(c) < 5 or not c[0].strip().isdigit():
            continue
        r.nikl03[strip_num(c[1])].append(
            {"rank": int(c[0]), "pos": c[2], "hanja": c[3].strip(), "level": c[4].strip()})
    # 2023 basic vocabulary (40,000 words, 5 grades, origin and hanja)
    xlsx = d / "nikl-basic-vocab-2023.xlsx"
    # the workbook has one sheet per grade and one with all 40,000 words: the biggest one
    sheets = [i for i in zipfile.ZipFile(xlsx).infolist() if re.fullmatch(r"xl/worksheets/sheet\d+\.xml", i.filename)]
    rows = read_xlsx_sheet(xlsx, max(sheets, key=lambda i: i.file_size).filename)
    for c in rows[1:]:
        if len(c) < 5 or not c[0].strip().endswith("등급"):
            continue
        hom = c[2].strip()
        r.basic23[c[1].strip()].append(Entry(
            word=c[1].strip(), grade5=int(c[0][0]), pos=c[3], origin=c[4].strip(),
            hanja=c[5].strip() if len(c) > 5 else "", homonym=int(hom) if hom.isdigit() else 0))
    _estimate_grades(r)
    return r


def _estimate_grades(r: RefLists) -> None:
    """Split each band into its two (or three) official 급 by an easiness proxy.

    The public file only carries the band. We order the words of a band by the 2023 basic grade and then the 2003
    corpus rank, and cut at the official 급 sizes. This is an estimate and is labelled as such in the output."""
    for band, (lo, hi) in BAND_GRADES.items():
        words = [w for w in r.bands if r.band_of(w) == band]

        def key(w: str):
            g5 = min((e.grade5 for e in r.basic23.get(w, [])), default=6)
            rank = min((e["rank"] for e in r.nikl03.get(w, [])), default=10 ** 6)
            return (g5, rank, w)

        words.sort(key=key)
        # sizes are for the whole band; scale to the number of distinct lemmas we actually have
        sizes = [GRADE_SIZES[g] for g in (lo, hi)]
        total = sum(sizes)
        cut = round(len(words) * sizes[0] / total)
        for i, w in enumerate(words):
            r.grade_est[w] = lo if i < cut else hi
