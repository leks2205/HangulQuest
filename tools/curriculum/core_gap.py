"""Core vocabulary the mod does not teach yet (WP3.1 gap list).

Reads tools/curriculum/core_gap_seed.tsv (hand-picked words that Minecraft can show or let the player do, each with
real Minecraft anchors and suggested roles), keeps those that are in the 초급 band (1-2급) of the standard
curriculum word list and not yet a concept of the mod, checks every anchor against names.json, orders them by
estimated grade and corpus frequency and writes the first 150 to content/curriculum/core-gap.csv.

Columns: word,grade,pos,minecraftAnchor,suggestedRoles,note
  grade   estimated 1 or 2 (the public list only gives the band 초급 = 1-2급, see reflists.py)
  anchors ids separated by |, each one a real item, block, entity or biome id
  roles   separated by ;
  note    the sense meant, where several words share the spelling
"""
from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

import reflists

HERE = Path(__file__).resolve().parent
DATA = HERE.parent.parent / "src" / "main" / "resources" / "assets" / "hangulquest" / "data"
LIMIT = 150
# several unrelated words share these spellings; the anchor shows which one is meant
SENSE = {
    "배": "boat (also belly, pear)", "다리": "leg (also bridge)", "새": "bird (also 'new')",
    "타다": "ride (also burn)", "쓰다": "write (also use, wear, bitter)", "열": "ten (also fever)",
    "손": "hand", "길": "road", "불": "fire", "개": "counter for things (also dog)", "사다": "buy",
    "살다": "live (also be alive)", "받다": "receive", "위": "up, above (also stomach)", "밑": "below",
    "안": "inside (also 'not')", "속": "inside", "밖": "outside", "별": "star (also 'special')",
    "병": "bottle as a counter (also illness)", "잔": "cup as a counter", "장": "sheet as a counter",
    "명": "person as a counter", "한": "one, before a counter", "두": "two, before a counter",
    "세": "three, before a counter", "눕다": "lie down", "달리다": "run (also hang, lack)",
    "그리다": "draw (also long for)", "죽다": "die", "굽다": "bake, roast", "찾다": "look for, find",
    "나오다": "come out", "듣다": "hear", "부르다": "call, sing", "잡다": "catch, hold",
    "높다": "high", "깊다": "deep", "넓다": "wide", "밝다": "bright", "머리": "head (also hair)",
    "얼굴": "face", "몸": "body", "하루": "one day", "지금": "now", "시간": "time (also hour)",
    "돈": "money", "이름": "name", "소리": "sound", "땅": "ground", "바람": "wind (also wish, 'I hope')",
}
POS = {"명사": "noun", "동사": "verb", "형용사": "adjective", "수사": "numeral", "관형사": "determiner",
       "부사": "adverb", "대명사": "pronoun", "의존명사": "bound_noun", "감탄사": "interjection"}


def anchor_ok(anchor: str, names: dict) -> bool:
    ns, _, ident = anchor.partition(":")
    return any(f"{kind}.{ns}.{ident}" in names for kind in ("item", "block", "entity", "biome"))


def build(L: reflists.RefLists, concepts: list[dict], out: Path) -> None:
    names = json.loads((DATA / "names.json").read_text(encoding="utf-8"))
    have = set()
    for c in concepts:
        have.add(c["ko"])
        have.update(c.get("aliases", []))
    rows = []
    problems = []
    seen = set()
    with open(HERE / "core_gap_seed.tsv", encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f, delimiter="\t"):
            word = r["word"]
            if word in seen:
                problems.append(f"{word}: duplicate in seed")
                continue
            seen.add(word)
            if word in have:
                problems.append(f"{word}: already a concept")
                continue
            if L.band_of(word) != "초급":
                problems.append(f"{word}: not in the 초급 band ({L.band_of(word)})")
                continue
            anchors = r["anchors"].split("|")
            bad = [a for a in anchors if not anchor_ok(a, names)]
            if bad:
                problems.append(f"{word}: unknown anchor {bad}")
                continue
            pos = (r.get("pos") or "").strip()
            if not pos:
                ents = sorted(L.basic23.get(word, []), key=lambda e: e.grade5 or 9)
                pos = POS.get(ents[0].pos.split("/")[0], ents[0].pos) if ents else "noun"
            rank = min((e["rank"] for e in L.nikl03.get(word, [])), default=10 ** 6)
            homonyms = len({e.homonym for e in L.basic23.get(word, [])
                            if e.pos.startswith(("명사", "동사", "형용사")) and (e.grade5 or 9) <= 4})
            note = SENSE.get(word, "several words share this spelling" if homonyms >= 2 else "")
            rows.append((L.grade_est[word], rank, word, pos, r["anchors"], r["roles"], note))
    rows.sort()
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["word", "grade", "pos", "minecraftAnchor", "suggestedRoles", "note"])
        for grade, _, word, pos, anchors, roles, note in rows[:LIMIT]:
            w.writerow([word, grade, pos, anchors, roles, note])
    dropped = [r[2] for r in rows[LIMIT:]]
    print(f"core gap: {min(len(rows), LIMIT)} written, {len(dropped)} candidates below the cut, {len(problems)} rejected")
    for p in problems:
        print("  rejected", p, file=sys.stderr)
    if dropped:
        print("  below the cut:", " ".join(dropped), file=sys.stderr)
