#!/usr/bin/env python3
"""Lexicon spine tagger (WP3.1): curriculum tags for every concept in concepts.json.

    python3 -I tools/curriculum/tag_lexicon.py --lists <dir with the three reference lists>
    python3 -I tools/curriculum/tag_lexicon.py --lists <dir> --core-gap     # also rebuilds content/curriculum/core-gap.csv

Reads concepts.json and writes data/lexicon-tags.json (one object per concept id) plus the review sheet
content/review/lexicon-tags-review.tsv. The reference lists themselves are never copied into the repository, only the
per-concept tags (grade, origin, hanja, ...) of the words the mod actually uses. reflists.py documents the expected
file names, tools/curriculum/README.txt the sources and terms.

Matching order for each concept (ko first, then aliases):
  1. exact match of the whole head word            -> matchType "exact" / "alias"
  2. known derivation (X하다 -> X, particles, adnominal forms) -> "derived"
  3. head noun / parts of a compound                -> "head" (curriculumGrade stays null, the parts go to coreParts)
  4. nothing found                                  -> "none"

Accuracy beats coverage: hanja is only written when a reference list (or the hand-checked table in overrides.py)
gives it for a single, unambiguous word, and never for a native word.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import overrides as ov  # noqa: E402
import reflists  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent.parent
DATA = ROOT / "src" / "main" / "resources" / "assets" / "hangulquest" / "data"
CJK = re.compile(r"[一-鿿]")
ORIGIN_KO = {"고유어": "native", "한자어": "sino", "외래어": "loan", "혼종어": "mixed"}
CONTENT_POS = ("명사", "동사", "형용사", "부사", "대명사", "관형사", "수사")
PARTICLES = ("이", "가", "을", "를", "에", "의", "와", "과")


def cjk(s: str) -> str:
    """Hanja of a list entry; when the list gives variants (沙漠/砂漠) the first one is the standard form."""
    return "".join(CJK.findall(s.split("/")[0]))


# --------------------------------------------------------------------------- facts about one spelling
class Facts:
    """Everything the lists say about one spelling (merged over its homographs)."""

    def __init__(self, lemma: str):
        self.lemma = lemma
        self.band = None
        self.grade = None
        self.nikl = None
        self.rank = None
        self.groups: set[tuple[str, str]] = set()      # (origin, hanja) over the content-word homographs
        self.basic = None                              # easiest grade (1-5) in the 2023 basic vocabulary
        self.homonyms = 0                              # distinct noun homographs of grade <= 4 in the 2023 list
        self.hanja03: set[str] = set()
        self.found = False


def facts_for(L: reflists.RefLists, lemma: str) -> Facts:
    f = Facts(lemma)
    band = L.band_of(lemma)
    if band:
        f.band, f.grade, f.found = band, L.grade_est.get(lemma), True
    n03 = L.nikl03.get(lemma)
    if n03:
        f.found = True
        f.nikl = min((e["level"] for e in n03), key="ABC".index)
        f.rank = min(e["rank"] for e in n03)
        f.hanja03 = {cjk(e["hanja"]) for e in n03 if cjk(e["hanja"])}
    ents = L.basic23.get(lemma)
    if ents:
        f.found = True
        content = [e for e in ents if e.pos.startswith(CONTENT_POS)] or ents
        f.groups = {(e.origin, cjk(e.hanja)) for e in content}
        f.basic = min(e.grade5 for e in content if e.grade5)
        f.homonyms = len({e.homonym for e in ents if e.pos.startswith("명사") and (e.grade5 or 9) <= 4})
    return f


# --------------------------------------------------------------------------- one word
class Word:
    def __init__(self, surface: str):
        self.surface = surface
        self.lemma = surface
        self.source = "none"             # list | manual | derived | none
        self.facts: Facts | None = None
        self.origin: str | None = None   # native | sino | loan | mixed | mc_only
        self.hanja = ""
        self.hanja_ok = True             # False when a Sino part has no certain hanja
        self.mc = False
        self.ambiguous = False
        self.modifier = False            # adnominal / colour modifier: kept in coreParts, ignored for origin and hanja
        self.homophone: list[str] = []
        self.notes: list[str] = []

    @property
    def grade(self):
        return self.facts.grade if self.facts and self.facts.found else None

    @property
    def listed(self) -> bool:
        return bool(self.facts and self.facts.found)


def label(origin_ko: str, hanja: str) -> str:
    return hanja or {"고유어": "native", "외래어": "loan", "한자어": "sino", "혼종어": "mixed"}.get(origin_ko, origin_ko)


def apply_facts(w: Word, f: Facts) -> None:
    w.facts = f
    if f.homonyms >= 2:
        labels = sorted({label(o, h) for o, h in f.groups})
        w.homophone = labels if len(labels) > 1 else [f"{labels[0] if labels else 'native'} homonyms"]
    if len(f.groups) == 1:
        o, h = next(iter(f.groups))
        w.origin = ORIGIN_KO.get(o)
        w.hanja = h
        w.hanja_ok = o not in ("한자어",) or bool(h)
    elif len(f.groups) > 1:
        w.ambiguous = True
        w.homophone = sorted({label(o, h) for o, h in f.groups})
    elif f.hanja03:
        # only the 2003 list knows this word: take its hanja when all homographs agree
        if len(f.hanja03) == 1:
            w.origin, w.hanja, w.hanja_ok = "sino", next(iter(f.hanja03)), True


def derivations(tok: str) -> list[tuple[str, str]]:
    """Candidate dictionary forms of a surface token with the kind of derivation:
    X하다 -> X, trailing particle (바다의 -> 바다), adnominal verb form (녹슨 -> 녹슬다)."""
    out: list[tuple[str, str]] = []
    if tok in ov.DERIVED:
        out.append((ov.DERIVED[tok], "adnominal"))
    if tok.endswith("하다") and len(tok) > 2:
        out.append((tok[:-2], "하다"))
    for p in PARTICLES:
        if tok.endswith(p) and len(tok) > len(p):
            out.append((tok[: -len(p)], "particle"))
    last, stem = tok[-1], tok[:-1]
    if last == "한":
        out += [(stem + "하다", "adnominal"), (stem, "adnominal")]
    elif last == "된":
        out += [(stem + "되다", "adnominal"), (stem, "adnominal")]
    elif last in ("은", "는"):
        out.append((stem + "다", "adnominal"))
    elif last == "인":
        out += [(stem + "이다", "adnominal"), (stem + "다", "adnominal")]
    else:
        code = ord(last) - 0xAC00
        if 0 <= code < 11172 and code % 28 == 4:       # final ㄴ: 큰 -> 크다, 낀 -> 끼다
            out.append((stem + chr(ord(last) - 4) + "다", "adnominal"))
    return out


def resolve(L: reflists.RefLists, surface: str, derive: bool = True) -> Word:
    """Look one spelling up: hand-checked table first for origin/hanja/mc, lists for grades."""
    w = Word(surface)
    man = ov.WORDS.get(surface)
    f = facts_for(L, man.get("lemma", surface) if man else surface)
    if man:
        w.source = "manual"
        w.origin = man.get("origin")
        w.hanja = man.get("hanja", "")
        w.hanja_ok = True
        w.mc = man.get("mc", False)
        w.modifier = man.get("mod", False) or surface in ov.COLOURS
        w.homophone = list(man.get("homophone", []))
        if man.get("note"):
            w.notes.append(man["note"])
        w.facts = f if f.found else None
        if f.found and w.origin is None:
            apply_facts(w, f)
        if w.facts is None and man.get("grade_from"):
            g = facts_for(L, man["grade_from"])
            w.facts = g if g.found else None
        return w
    if f.found:
        w.source = "list"
        w.modifier = surface in ov.COLOURS
        apply_facts(w, f)
        return w
    if derive:
        for cand, kind in derivations(surface):
            w2 = resolve(L, cand, derive=False)
            if w2.source != "none":
                w2.surface, w2.source = surface, "derived"
                w2.modifier = kind == "adnominal"
                w2.notes.append(f"{surface} < {cand}")
                return w2
    return w


# --------------------------------------------------------------------------- concepts
class Result:
    def __init__(self, cid: str, ko: str):
        self.id, self.ko = cid, ko
        self.match = "none"
        self.whole: Word | None = None
        self.parts: list[Word] = []
        self.origin: str | None = None
        self.hanja: list[str] = []
        self.notes: list[str] = []
        self.conf = "high"
        self.base: Word | None = None
        self.alias_hits: list[Word] = []


def split_tokens(L: reflists.RefLists, form: str) -> list[Word]:
    parts: list[Word] = []
    toks = form.split()
    i = 0
    while i < len(toks):
        # fixed phrases from the table ("금 간"), longest first
        for n in (3, 2):
            phrase = " ".join(toks[i:i + n])
            if len(toks[i:i + n]) == n and phrase in ov.WORDS:
                parts.append(resolve(L, phrase))
                i += n
                break
        else:
            parts.extend(_split_one(L, toks[i]))
            i += 1
    return parts


def _split_one(L: reflists.RefLists, tok: str) -> list[Word]:
    w = resolve(L, tok)
    if w.source == "none":
        # compound without a space: peel a productive head suffix (포플러나무 = 포플러 + 나무)
        for suf in sorted(ov.HEAD_SUFFIXES, key=len, reverse=True):
            if tok.endswith(suf) and len(tok) > len(suf):
                head = resolve(L, tok[: -len(suf)], derive=False)
                tail = resolve(L, suf, derive=False)
                if head.source != "none":
                    return [head, tail]
    return [w]


def combine(parts: list[Word]) -> tuple[str | None, list[str], bool]:
    """Origin, hanja and certainty of a compound from its parts."""
    origins = set()
    hanja = ""
    ok = True
    for p in parts:
        if p.modifier or p.surface in ov.QUALIFIERS:
            continue
        o = p.origin
        if o is None:
            return None, [], False
        origins.add(o)
        if o in ("sino", "mixed"):
            if p.hanja:
                hanja += p.hanja
            else:
                ok = False
        # for mixed parts that have no hanja we simply cannot say; flagged via ok below
        if o in ("sino",) and not p.hanja:
            ok = False
    if not origins:
        return None, [], False
    if origins == {"mc_only"}:
        origin = "mc_only"
    else:
        origins = {"loan" if o == "mc_only" else o for o in origins}
        origin = origins.pop() if len(origins) == 1 else "mixed"
    return origin, list(hanja) if ok else [], ok


def whole_lookup(L: reflists.RefLists, form: str) -> Word | None:
    """Whole-word match on the form as written. Joining the words (철 창 -> 철창) would find a different word."""
    w = resolve(L, form)
    if w.source != "none" and (w.listed or form in ov.WORDS):
        return w
    return None


def analyse(L: reflists.RefLists, c: dict) -> Result:
    r = Result(c["id"], c["ko"])
    ko = c["ko"]
    # 1. exact match of the head word; 2. derivation X하다 -> X
    w = whole_lookup(L, ko)
    if w is not None:
        r.whole = w
        r.match = "derived" if w.source == "derived" else ("exact" if w.listed else "none")
        if w.listed and w.grade is None and ko.endswith("하다"):
            base = resolve(L, ko[:-2], derive=False)
            if base.grade is not None:
                r.base = base
                r.match = "derived"
                r.notes.append(f"grade via {base.surface} + 하다")
    # aliases only add information, the learner sees ko
    for a in c.get("aliases", []):
        if a == ko:
            continue
        wa = whole_lookup(L, a)
        if wa is not None and wa.listed:
            r.alias_hits.append(wa)
    if r.whole is None and r.alias_hits:
        r.match = "alias"
    # 3. parts of a compound
    needs_parts = r.whole is None or len(ko.split()) > 1 or r.match == "derived" or r.whole.origin is None \
        or r.whole.ambiguous
    if needs_parts:
        r.parts = [r.base, resolve(L, "하다")] if r.base else split_tokens(L, ko)
    # origin and hanja
    if r.whole is not None and r.whole.origin and not r.whole.ambiguous and len(ko.split()) == 1:
        r.origin = r.whole.origin
        r.hanja = list(r.whole.hanja) if r.origin in ("sino", "mixed") else []
        if r.origin in ("sino", "mixed") and not r.whole.hanja:
            r.notes.append("hanja not checked")
    elif r.parts:
        o, h, ok = combine(r.parts)
        r.origin, r.hanja = o, h
        if r.match in ("none", "alias") and any(p.listed for p in r.parts):
            r.match = "head"
        if not ok and o in ("sino", "mixed"):
            r.notes.append("hanja of at least one Sino part not certain, left out")
            r.conf = "medium"
    if r.origin is None:
        r.conf = "low"
        r.notes.append("origin not determined")
    return r


# --------------------------------------------------------------------------- semantic sets and mcOnly
SHAPES = {"블록", "계단", "담장", "기둥", "가루", "판", "반"}


def sem_set(c: dict, toks: list[str]) -> tuple[str, str]:
    """Returns (semSet, how)."""
    cid = c["id"]
    if cid in ov.CONCEPTS and "semSet" in ov.CONCEPTS[cid]:
        return ov.CONCEPTS[cid]["semSet"], "table"
    key = c["ko"]
    if key in ov.SEM_WHOLE:
        return ov.SEM_WHOLE[key], "whole"
    for pat, sem in ov.SEM_ID:
        if re.search(pat, cid):
            return sem, "id"
    heads = [t for t in toks if t not in SHAPES]
    for t in reversed(heads):
        if t in ov.SEM_HEADS:
            return ov.SEM_HEADS[t], "head"
        for suf in sorted(ov.SEM_SUFFIX, key=len, reverse=True):
            if t.endswith(suf) and len(t) > len(suf):
                return ov.SEM_SUFFIX[suf], "suffix"
    # fall back to the shape word itself, then the category
    for t in reversed(toks):
        if t in ov.SEM_HEADS:
            return ov.SEM_HEADS[t], "shape"
    return ov.CATEGORY_SEM.get(c.get("category", ""), "misc"), "category"


def mc_only(c: dict, r: Result, toks: list[str]) -> tuple[bool, str]:
    cid = c["id"]
    if cid in ov.CONCEPTS and "mcOnly" in ov.CONCEPTS[cid]:
        return ov.CONCEPTS[cid]["mcOnly"], "table"
    if r.origin == "mc_only":
        return True, "origin"
    if c["ko"] in ov.WORDS and ov.WORDS[c["ko"]].get("mc"):
        return True, "word"
    if any(p.mc for p in r.parts) or (r.whole and r.whole.mc):
        return True, "part"
    if any(m in c["ko"] for m in ov.MC_MODIFIERS):
        return True, "modifier"
    if any(re.search(pat, cid) for pat in ov.MC_ID):
        return True, "id"
    if c["ko"] in ov.MC_PHRASES:
        return True, "phrase"
    return False, ""


def tokens_of(c: dict) -> list[str]:
    toks = []
    for t in c["ko"].split():
        toks.append(t)
    return toks


def tag_concept(L: reflists.RefLists, c: dict) -> tuple[dict, dict]:
    r = analyse(L, c)
    toks = tokens_of(c)
    ovr = ov.CONCEPTS.get(c["id"], {})
    sem, sem_how = sem_set(c, toks)
    mc, mc_how = mc_only(c, r, toks)

    origin = ovr.get("origin", r.origin)
    hanja = list(ovr["hanja"]) if "hanja" in ovr else r.hanja
    if origin == "mc_only" and "mcOnly" not in ovr:
        mc = True
    if origin not in ("sino", "mixed"):
        hanja = []

    # curriculum facts come from the head word (or, for X하다, from X); aliases are reported separately
    src = r.base or (r.whole if r.match in ("exact", "derived") else None)
    facts = src.facts if src is not None and src.listed else None
    grade = src.grade if facts else None
    band = facts.band if facts else None
    whole_facts = r.whole.facts if r.whole is not None and r.whole.facts else None
    nikl = whole_facts.nikl if whole_facts and whole_facts.nikl else (facts.nikl if facts else None)
    rank = whole_facts.rank if whole_facts and whole_facts.rank else (facts.rank if facts else None)

    core = []
    if r.parts and (len(r.parts) > 1 or r.match == "derived"):
        for p in r.parts:
            word = p.lemma if p.source == "derived" else p.surface
            core.append({"word": word, "grade": p.grade})
    alias_grades = [{"word": a.surface, "grade": a.grade} for a in r.alias_hits if a.grade is not None]

    homophones = []
    if r.whole and len(c["ko"].split()) == 1:
        homophones = list(r.whole.homophone)
    if "homophone" in ovr:
        homophones = ovr["homophone"]

    conf = ovr.get("confidence", r.conf)
    notes = list(r.notes)
    if r.whole is not None:
        notes += r.whole.notes
    if ovr.get("note"):
        notes.append(ovr["note"])
    if r.whole and r.whole.ambiguous and r.origin is None and "origin" not in ovr:
        conf = "low"
        notes.append("homograph readings differ (" + " / ".join(r.whole.homophone) + "), origin not decided")
    if r.match == "derived" and conf == "high":
        conf = "medium"
    if homophones:
        notes.append("homophones: " + ", ".join(homophones))
        if grade is not None and r.match == "exact":
            notes.append("grade is the easiest homograph's")
            if conf == "high":
                conf = "medium"

    tag = {
        "ko": c["ko"],
        "curriculumBand": band,
        "curriculumGrade": grade,
        "nikl": nikl,
        "basicGrade": whole_facts.basic if whole_facts else None,
        "freqRank": rank,
        "coreParts": core,
        "aliasGrades": alias_grades,
        "origin": origin,
        "hanja": hanja,
        "semSet": sem,
        "mcOnly": mc,
        "homophones": homophones,
        "matchType": r.match,
        "confidence": conf,
        "note": "; ".join(dict.fromkeys(n for n in notes if n)),
    }
    aux = {"sem_how": sem_how, "mc_how": mc_how, "result": r}
    return tag, aux


# --------------------------------------------------------------------------- driver
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lists", type=Path, help="directory with the three downloaded reference lists")
    ap.add_argument("--no-band-list", action="store_true",
                    help="leave out the 초급/중급/고급 list (KOGL type 4: attribution, non-commercial, no changes); "
                         "curriculumBand and curriculumGrade stay null, nikl and basicGrade (type 1) are kept")
    ap.add_argument("--check", action="store_true",
                    help="only compare ids and Korean words of concepts.json with lexicon-tags.json (needs no lists)")
    ap.add_argument("--concepts", type=Path, default=DATA / "concepts.json")
    ap.add_argument("--out", type=Path, default=DATA / "lexicon-tags.json")
    ap.add_argument("--review", type=Path, default=ROOT / "content" / "review" / "lexicon-tags-review.tsv")
    ap.add_argument("--core-gap", action="store_true", help="also rebuild content/curriculum/core-gap.csv")
    ap.add_argument("--core-gap-out", type=Path, default=ROOT / "content" / "curriculum" / "core-gap.csv")
    ap.add_argument("--debug", action="store_true", help="print unresolved words and ambiguities")
    args = ap.parse_args()

    concepts = json.loads(args.concepts.read_text(encoding="utf-8"))
    if args.check:
        return check(concepts, args.out)
    if args.lists is None:
        ap.error("--lists is required unless --check is given")
    L = reflists.load_all(args.lists, use_band_list=not args.no_band_list)
    tags: dict[str, dict] = {}
    aux: dict[str, dict] = {}
    for c in concepts:
        tags[c["id"]], aux[c["id"]] = tag_concept(L, c)

    out = {"_comment": "Curriculum tags per concept id (WP3.1). Generated by tools/curriculum/tag_lexicon.py; "
                       "do not edit by hand. curriculumBand 초급/중급/고급 = 1-2/3-4/5-6급 of the standard curriculum "
                       "word list; curriculumGrade is an estimate inside the band. Sources and terms: tools/curriculum/README.txt.",
           "schema": 1,
           "tags": tags}
    write_tags(args.out, out)
    write_review(args.review, concepts, tags)
    report(concepts, tags, aux, args.debug)
    if args.core_gap:
        if args.no_band_list:
            ap.error("--core-gap needs the band list")
        import core_gap
        core_gap.build(L, concepts, args.core_gap_out)
    return 0


def write_tags(path: Path, out: dict) -> None:
    """One concept per line: small, and a re-run shows up as a readable diff."""
    lines = ["{", f'"_comment": {json.dumps(out["_comment"], ensure_ascii=False)},', f'"schema": {out["schema"]},',
             '"tags": {']
    items = list(out["tags"].items())
    for i, (cid, tag) in enumerate(items):
        body = json.dumps(tag, ensure_ascii=False, separators=(",", ":"))
        lines.append(f'{json.dumps(cid)}:{body}{"," if i < len(items) - 1 else ""}')
    lines += ["}", "}"]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def check(concepts: list[dict], tags_path: Path) -> int:
    """Drift check: a concept whose id is new or whose Korean word changed needs a re-run of the tagger."""
    tags = json.loads(tags_path.read_text(encoding="utf-8"))["tags"]
    bad = [f"{c['id']}: no tags" for c in concepts if c["id"] not in tags]
    bad += [f"{c['id']}: tagged as {tags[c['id']]['ko']} but concepts.json says {c['ko']}"
            for c in concepts if c["id"] in tags and tags[c["id"]]["ko"] != c["ko"]]
    ids = {c["id"] for c in concepts}
    bad += [f"{i}: tags for a concept that is gone" for i in tags if i not in ids]
    for line in bad:
        print(line)
    print("lexicon tags up to date" if not bad else f"{len(bad)} problems, re-run tag_lexicon.py --lists ...")
    return 1 if bad else 0


def write_review(path: Path, concepts: list[dict], tags: dict[str, dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(["id", "ko", "origin", "hanja", "grade", "semSet", "note"])
        for c in concepts:
            t = tags[c["id"]]
            grade = "" if t["curriculumGrade"] is None else f"{t['curriculumGrade']}({t['curriculumBand']})"
            parts = "+".join(p["word"] for p in t["coreParts"])
            note = f"[{t['matchType']}/{t['confidence']}{'/mcOnly' if t['mcOnly'] else ''}] " \
                   f"{'parts ' + parts + ('; ' if t['note'] else '') if parts else ''}{t['note']}".strip()
            w.writerow([c["id"], c["ko"], t["origin"] or "", "".join(t["hanja"]), grade, t["semSet"], note])


def report(concepts, tags, aux, debug):
    n = len(concepts)
    g12 = sum(1 for t in tags.values() if t["curriculumGrade"] in (1, 2))
    graded = sum(1 for t in tags.values() if t["curriculumGrade"] is not None)
    mc = sum(1 for t in tags.values() if t["mcOnly"])
    print(f"concepts {n}  graded {graded}  grade1-2 {g12}  mcOnly {mc} ({100 * mc / n:.1f}%)")
    print("origin", dict(Counter(t["origin"] for t in tags.values())))
    print("match", dict(Counter(t["matchType"] for t in tags.values())))
    print("confidence", dict(Counter(t["confidence"] for t in tags.values())))
    print("semSet", dict(Counter(t["semSet"] for t in tags.values()).most_common()))
    if debug:
        for cid, t in tags.items():
            if t["origin"] is None or t["confidence"] == "low":
                print("LOW", cid, next(c["ko"] for c in concepts if c["id"] == cid), t["note"])


if __name__ == "__main__":
    sys.exit(main())
