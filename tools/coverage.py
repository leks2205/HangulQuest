#!/usr/bin/env python3
"""Curriculum simulator: how well does the content recycle words? (roadmap WP3.5)

A canonical learner walks the trail graph (prerequisites first, lower Korean level first, then `order`) and
meets the Korean text of every step. A word becomes known after KNOWN_AFTER encounters. Reads
build/reports/content/steps.json, which StepsDumpTest writes.

Per sentence (the clue of every step):
  unknown words        words the learner has not met KNOWN_AFTER times yet; target: at most 1
  hint coverage        the hint may only use words the learner knows or has just read in the clue; target: 100%
Per core word (curriculum band 초급 or NIKL grade A in lexicon-tags.json, not mcOnly):
  encounters           steps (and trail titles and finale lines) in which the word occurs, or is taught and then said in
                       the clue, the hint, the title or the finale line (a teach entry alone is none); target: 10
  contexts             distinct roles: object, anchor, tool, recipient, material, condition, verb, mention, finale, title
  retrievals           later days within RETRIEVAL_DAYS of the first encounter on which the word comes back, with
                       TRAILS_PER_DAY trails per simulated day; target: RETRIEVAL_TARGET

  python3 tools/coverage.py                    report; also writes build/reports/content/recycling-gaps.csv
  python3 tools/coverage.py --check            exit 1 if a metric is worse than content/metrics-baseline.json
  python3 tools/coverage.py --update-baseline  rewrite the coverage part of the baseline: deliberate only
  python3 tools/coverage.py --json             the metrics as JSON

The text is cut into words by matching it against the lexicon (concepts, their aliases, the vanilla Korean
names, the common words of content/curriculum/core-gap.csv): a noun with a case particle, a multi-word name,
or a verb stem with an ending. It is a heuristic, not a morphological analyser; words that are in no list
count for nothing. The numbers are for the ratchet and for the briefs, not for linguistics.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import variety  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src" / "main" / "resources" / "assets" / "hangulquest" / "data"
CORE_GAP = ROOT / "content" / "curriculum" / "core-gap.csv"
GAPS_CSV = ROOT / "build" / "reports" / "content" / "recycling-gaps.csv"
# Every surface form the game's Conjugator makes for the verbs it knows; VerbFormsDumpTest writes it.
VERB_FORMS = ROOT / "build" / "reports" / "content" / "verb-forms.json"
# Verbs of the first-steps list that the game enacts but no concept or curriculum row carries yet.
ENACTED_EXTRA = ("쌓다", "들다")

# ---- Parameters of the simulated learner. Change one and the baseline has to be rewritten.
KNOWN_AFTER = 3          # encounters before a word counts as known
TRAILS_PER_DAY = 2       # the pace of the canonical learner
RETRIEVAL_DAYS = 14      # window after the first encounter in which a return counts as a retrieval
ENCOUNTER_TARGET = 10    # roadmap: at least 10 encounters per core word
ROLE_TARGET = 3          # ... in at least 3 roles
RETRIEVAL_TARGET = 3     # returns on different days within the window
MAX_UNKNOWN = 1          # roadmap: at most one unknown word per sentence
COUNT_HINTS = False      # hints are shown on request only, so they are no encounter by default
HINT_SEES_CLUE = True    # the words of the clue the learner has just read are fine in the hint

PARAMS = {
    "knownAfter": KNOWN_AFTER, "trailsPerDay": TRAILS_PER_DAY, "retrievalDays": RETRIEVAL_DAYS,
    "encounterTarget": ENCOUNTER_TARGET, "roleTarget": ROLE_TARGET, "retrievalTarget": RETRIEVAL_TARGET,
    "maxUnknown": MAX_UNKNOWN, "countHints": COUNT_HINTS, "hintSeesClue": HINT_SEES_CLUE,
}

METRIC_SPECS = {
    "coverage.sentences.unknownOverOneShare": ("lower", 0),
    "coverage.sentences.meanUnknown": ("lower", MAX_UNKNOWN),
    "coverage.sentences.hintFullyKnownShare": ("higher", 1.0),
    "coverage.sentences.hintWordCoverage": ("higher", 1.0),
    "coverage.recycling.medianEncounters": ("higher", ENCOUNTER_TARGET),
    "coverage.recycling.targetMetShare": ("higher", 1.0),
    "coverage.recycling.seenShare": ("higher", 1.0),
    "coverage.recycling.medianRetrievals": ("higher", RETRIEVAL_TARGET),
}


def spec(key: str) -> tuple[str, float]:
    return METRIC_SPECS[key]


# ------------------------------------------------------------------------------------------ Hangul

S_BASE, N_V, N_F = 0xAC00, 21, 28
VERB_ENDING = set("다요아어여야지세시셔고는면며니서게기자네죠려라으을은았었였")
L_DROP_ENDING = set("세시셔는니")                  # the endings an ㄹ stem loses its ㄹ in front of: 사세요, 사시는, 사니까
PARTICLES = {
    "", "이", "가", "은", "는", "을", "를", "의", "에", "에서", "에게", "한테", "께", "로", "으로", "와", "과", "랑",
    "이랑", "도", "만", "까지", "부터", "보다", "처럼", "이나", "나", "마다", "조차", "밖에", "이에요", "예요", "입니다",
    "이다", "이고", "이라고", "라고", "에는", "에서는", "으로는", "로는", "에도", "에서도", "와는", "과는", "에게는",
    "이며", "이라", "이든", "든", "들", "들을", "들이", "들은", "들도", "쯤", "께서", "이야", "야", "이죠", "죠", "이세요",
    "이에요", "에다", "에다가", "으로서", "로서", "으로써", "로써", "만큼", "이란", "란", "라는", "이라는", "하고", "이랑은",
    "을요", "를요", "은요", "는요", "에요", "이어서", "이라서", "여서", "이었어요", "였어요", "였다", "이었다",
}
CONTRACT = {8: 9, 13: 14, 20: 6, 18: 4, 11: 10}  # ㅗ>ㅘ ㅜ>ㅝ ㅣ>ㅕ ㅡ>ㅓ ㅚ>ㅙ
MERGE_FINALS = (4, 8, 17, 20)                     # ㄴ ㄹ ㅂ ㅆ


def is_syllable(ch: str) -> bool:
    return 0xAC00 <= ord(ch) <= 0xD7A3


def split(ch: str) -> tuple[int, int, int]:
    code = ord(ch) - S_BASE
    return code // (N_V * N_F), (code % (N_V * N_F)) // N_F, code % N_F


def join(i: int, v: int, f: int) -> str:
    return chr(S_BASE + (i * N_V + v) * N_F + f)


def last_syllable_variants(syllable: str) -> list[tuple[str, bool, bool]]:
    """Forms the last stem syllable takes in front of an ending: (syllable, merged with the ending, ㄹ dropped)."""
    if not is_syllable(syllable):
        return [(syllable, False, False)]
    i, v, f = split(syllable)
    out: list[tuple[str, bool, bool]] = [(syllable, False, False)]
    open_forms = []
    finals = MERGE_FINALS
    if f == 0:
        open_forms.append((i, v))
        if v in CONTRACT:
            out.append((join(i, CONTRACT[v], 0), True, False))
            open_forms.append((i, CONTRACT[v]))
        if i == 18 and v == 0:  # 하 > 해
            out.append((join(i, 1, 0), True, False))
            open_forms.append((i, 1))
    elif f == 8:  # ㄹ stems drop the ㄹ in front of ㄴ ㅂ ㅅ (사는, 삽니다, 사세요), never in front of 아요 or ㅆ
        out.append((join(i, v, 0), False, True))
        open_forms.append((i, v))
        finals = (4, 8, 17)
    for oi, ov in open_forms:
        for final in finals:
            out.append((join(oi, ov, final), True, False))
    return out


def verb_matches(stem: str, word: str) -> bool:
    """Whether the eojeol is the dictionary form or a conjugated form of the verb with this stem."""
    if not word or not stem or not is_syllable(stem[-1]):
        return False
    for syllable, merged, dropped in last_syllable_variants(stem[-1]):
        prefix = stem[:-1] + syllable
        if word.startswith(prefix):
            rest = word[len(prefix):]
            if dropped and not (rest and rest[0] in L_DROP_ENDING):
                continue                            # 팔아요 is not 파요's ㄹ-drop twin: 파다 and 사다 keep their own
            if merged and rest and is_syllable(rest[0]) and split(syllable)[2] in (4, 8, 17) and split(rest[0])[0] == 11:
                continue                            # a final ㄴ ㄹ ㅂ is never followed by a vowel-initial ending: 팔 + 아요
            if (not rest and merged) or (rest and rest[0] in VERB_ENDING):
                return True
    # 으-drop: 모으다 > 모아요
    if len(stem) >= 2 and is_syllable(stem[-1]) and split(stem[-1])[1] == 18 and split(stem[-1])[2] == 0:
        if word.startswith(stem[:-1]) and word[len(stem) - 1:len(stem)] in ("아", "어") and len(word) >= len(stem):
            return True
    return False


# ---------------------------------------------------------------------------------------- lexicon

class Lexicon:
    """Surface forms -> canonical word. Identity of a word is its canonical Korean spelling."""

    def __init__(self) -> None:
        self.forms: dict[str, str] = {}          # surface form (noun, name, alias) -> canonical
        self.verbs: dict[str, str] = {}          # verb stem -> canonical
        self.verb_words: set[str] = set()
        self.verb_forms: dict[str, str] = {}     # whole conjugated eojeol (걸어요) -> dictionary form (걷다)
        self.words: dict[str, dict] = {}         # canonical -> {"concepts": [...], "core": bool, "gap": bool, ...}
        self.by_id: dict[str, str] = {}          # registry id or concept id -> canonical
        self.max_words = 1

    def add(self, canonical: str, forms: list[str], concept: str | None, core: bool = False, gap: bool = False,
            ids: list[str] | None = None, meta: dict | None = None, verb: bool | None = None) -> None:
        word = self.words.get(canonical)
        if word is None:
            word = {"concepts": [], "core": False, "gap": gap, "meta": {}}
            self.words[canonical] = word
        if concept:
            word["concepts"].append(concept)
            self.by_id.setdefault(concept, canonical)
        word["core"] = word["core"] or core
        if meta:
            word["meta"].update({k: v for k, v in meta.items() if k not in word["meta"] or not word["meta"][k]})
        for registry_id in ids or []:
            self.by_id.setdefault(registry_id, canonical)
        is_verb = canonical.endswith("다") and len(canonical) >= 2 if verb is None else verb
        for form in forms:
            if not form:
                continue
            if is_verb and form.endswith("다") and len(form) >= 2:
                self.verbs.setdefault(form[:-1], canonical)
                self.verb_words.add(canonical)
            elif form not in self.forms:
                self.forms[form] = canonical
                self.max_words = max(self.max_words, form.count(" ") + 1)

    @staticmethod
    def from_json(entries: list[dict]) -> "Lexicon":
        lex = Lexicon()
        for entry in entries:
            lex.add(entry["ko"], [entry["ko"], *entry.get("aliases", [])], entry.get("id"), entry.get("core", False),
                    entry.get("gap", False), entry.get("ids", []), entry.get("meta"), entry.get("verb"))
        return lex

    @staticmethod
    def from_repo(data: Path = DATA) -> "Lexicon":
        lex = Lexicon()
        concepts = json.loads((data / "concepts.json").read_text(encoding="utf-8"))
        tags = json.loads((data / "lexicon-tags.json").read_text(encoding="utf-8"))["tags"]
        for concept in concepts:
            tag = tags.get(concept["id"], {})
            core = (tag.get("curriculumBand") == "초급" or tag.get("nikl") == "A") and not tag.get("mcOnly", False)
            ids = list(concept.get("items", []))
            for field in ("entity", "biome"):
                if concept.get(field):
                    ids.append(concept[field])
            is_verb = concept.get("category") == "ACTIONS" and concept["ko"].endswith("다")
            lex.add(concept["ko"], [concept["ko"], *concept.get("aliases", [])], concept["id"], core=core, ids=ids,
                    verb=is_verb, meta={"band": tag.get("curriculumBand") or "", "grade": tag.get("curriculumGrade") or "",
                          "nikl": tag.get("nikl") or "", "mcOnly": tag.get("mcOnly", False)})
        names = json.loads((data / "names.json").read_text(encoding="utf-8"))
        for key, value in names.items():
            parts = key.split(".")
            if len(parts) != 3 or parts[1] != "minecraft" or parts[0] not in ("block", "item", "entity", "biome"):
                continue
            if isinstance(value, list) and value and isinstance(value[0], str) and value[0]:
                registry = f"minecraft:{parts[2]}"
                ko = value[0]
                canonical = lex.forms.get(ko, ko)
                if canonical in lex.words:
                    lex.by_id.setdefault(registry, canonical)
                else:
                    lex.add(ko, [ko], None, ids=[registry], verb=False)
        if CORE_GAP.exists():
            with CORE_GAP.open(encoding="utf-8", newline="") as handle:
                for row in csv.DictReader(handle):
                    word = row["word"]
                    if word in lex.forms or word in lex.words or (word.endswith("다") and word[:-1] in lex.verbs):
                        continue
                    lex.add(word, [word], None, core=True, gap=True, verb=row.get("pos") in ("verb", "adjective"),
                            meta={"grade": row.get("grade", ""), "anchor": row.get("minecraftAnchor", ""),
                                  "roles": row.get("suggestedRoles", ""), "pos": row.get("pos", "")})
        lex.add_verb_forms(VERB_FORMS)
        return lex

    def add_verb_forms(self, path: Path) -> None:
        """Whole-eojeol forms of the verbs the game conjugates, irregular ones included (걷다 > 걸어요).

        Only verbs the lexicon knows get forms, plus ENACTED_EXTRA, which no concept or curriculum row
        carries. Only single-eojeol forms of two syllables or more are kept, and a form that is already
        claimed stays with its first verb.
        """
        if not path.exists():
            return
        for dictionary in ENACTED_EXTRA:
            if dictionary not in self.words and dictionary[:-1] not in self.verbs:
                self.add(dictionary, [dictionary], None, verb=True)
        for dictionary, forms in json.loads(path.read_text(encoding="utf-8")).get("forms", {}).items():
            if dictionary not in self.words:
                continue
            for form in forms:
                if len(form) >= 2 and " " not in form:
                    self.verb_forms.setdefault(form, dictionary)

    # --------------------------------------------------------------------------------- tokenizing

    def tokens(self, text: str) -> tuple[list[str], int, int]:
        """Canonical words in the order they occur (distinct), plus eojeols placed and eojeols seen."""
        eojeols = [re.sub(r"[.,!?~…\"'()\[\]]", "", e) for e in text.split()]
        eojeols = [e for e in eojeols if e]
        found: list[str] = []
        placed = 0
        i = 0
        while i < len(eojeols):
            hit = self._match(eojeols, i)
            if hit is None:
                i += 1
                continue
            canonical, length = hit
            placed += length
            if canonical not in found:
                found.append(canonical)
            i += length
        return found, placed, len(eojeols)

    def _match(self, eojeols: list[str], i: int) -> tuple[str, int] | None:
        for length in range(min(self.max_words, len(eojeols) - i), 0, -1):
            head = eojeols[i:i + length - 1]
            last = eojeols[i + length - 1]
            for cut in range(len(last), 0, -1):
                if last[cut:] in PARTICLES:
                    candidate = " ".join([*head, last[:cut]])
                    if candidate in self.forms:
                        return self.forms[candidate], length
        word = eojeols[i]
        for stem, canonical in self.verbs.items():
            if (len(stem) == 1 or word[:1] == stem[:1]) and verb_matches(stem, word):
                return canonical, 1
        # what the stem rule cannot see: ㄷ- and ㅂ-irregular forms (걸어요, 구워요), 들다 and 쌓다 if not listed
        if word in self.verb_forms:
            return self.verb_forms[word], 1
        return None


# ------------------------------------------------------------------------------------- simulation

def day_of(index: int) -> int:
    return index // TRAILS_PER_DAY


def simulate(trails: list[dict], lexicon: Lexicon, include_demo: bool = False) -> dict:
    path = variety.learner_path(trails, include_demo)
    cache: dict[str, tuple[list[str], int, int]] = {}

    def tokens(text: str) -> list[str]:
        if text not in cache:
            cache[text] = lexicon.tokens(text)
        return cache[text][0]

    encounters: dict[str, int] = defaultdict(int)
    events: dict[str, list[int]] = defaultdict(list)         # canonical -> day of each encounter
    roles: dict[str, set[str]] = defaultdict(set)
    first_at: dict[str, tuple[str, int]] = {}
    sentences: list[dict] = []
    hints: list[dict] = []

    for trail_index, trail in enumerate(path):
        day = day_of(trail_index)
        for step_index, step in enumerate(trail["steps"]):
            clue = tokens(step["clueKo"])
            unknown = [w for w in clue if encounters[w] < KNOWN_AFTER]
            sentences.append({"trail": trail["id"], "step": step["id"], "level": trail["level"], "ko": step["clueKo"],
                              "words": len(clue), "unknown": unknown})

            hint_words = tokens(step["hintKo"]) if step["hintKo"] else []
            if hint_words:
                seen_now = set(clue) if HINT_SEES_CLUE else set()
                missing = [w for w in hint_words if encounters[w] < KNOWN_AFTER and w not in seen_now]
                hints.append({"trail": trail["id"], "step": step["id"], "ko": step["hintKo"],
                              "words": len(hint_words), "missing": missing})

            met: dict[str, set[str]] = defaultdict(set)    # word -> roles at this step
            target_words: dict[str, str] = {}
            for target in step["targets"]:
                canonical = lexicon.by_id.get(target["id"])
                if canonical:
                    target_words.setdefault(canonical, target["role"])
            for word in clue:
                role = target_words.get(word)
                if role is None:
                    role = "verb" if word in lexicon.verb_words else "mention"
                met[word].add(role)
            if COUNT_HINTS:
                for word in hint_words:
                    met[word].add("hint")
            if step_index == 0:
                title_words = tokens(trail["titleKo"])
                for word in title_words:
                    met[word].add("title")
            else:
                title_words = []
            if step_index == len(trail["steps"]) - 1:
                finale_words = tokens(trail["finaleKo"])
                for word in finale_words:
                    met[word].add("finale")
            else:
                finale_words = []
            # a taught word is met only when a sentence of the step says it: clue, hint, title or finale line
            said = {*clue, *hint_words, *title_words, *finale_words}
            for concept_id in step["teach"]:
                canonical = lexicon.by_id.get(concept_id)
                if canonical and canonical in said:
                    met.setdefault(canonical, set())

            for word, word_roles in met.items():       # after the sentence: only earlier steps make a word known
                encounters[word] += 1
                events[word].append(day)
                roles[word].update(word_roles)
                first_at.setdefault(word, (trail["id"], trail["level"]))
    placed = sum(c[1] for c in cache.values())
    seen = sum(c[2] for c in cache.values())
    return {"path": path, "encounters": encounters, "events": events, "roles": roles, "first": first_at,
            "sentences": sentences, "hints": hints, "placed": placed, "eojeols": seen}


def retrievals(days: list[int]) -> int:
    """Later days within the window of the first encounter on which the word came back."""
    if not days:
        return 0
    first = days[0]
    return len({d for d in days[1:] if first < d <= first + RETRIEVAL_DAYS})


def core_rows(sim: dict, lexicon: Lexicon) -> list[dict]:
    rows = []
    for word, info in lexicon.words.items():
        if not info["core"]:
            continue
        enc = sim["encounters"].get(word, 0)
        contexts = len(sim["roles"].get(word, ()))
        ret = retrievals(sim["events"].get(word, []))
        lacks = []
        if enc < ENCOUNTER_TARGET:
            lacks.append(f"encounters {enc}/{ENCOUNTER_TARGET}")
        if contexts < ROLE_TARGET:
            lacks.append(f"roles {contexts}/{ROLE_TARGET}")
        if ret < RETRIEVAL_TARGET:
            lacks.append(f"retrievals {ret}/{RETRIEVAL_TARGET}")
        first = sim["first"].get(word, ("", ""))
        rows.append({"word": word, "concepts": "|".join(info["concepts"]), "gap": info["gap"],
                     "band": info["meta"].get("band", ""), "grade": info["meta"].get("grade", ""),
                     "nikl": info["meta"].get("nikl", ""), "encounters": enc, "contexts": contexts,
                     "roles": "|".join(sorted(sim["roles"].get(word, ()))), "retrievals": ret,
                     "firstTrail": first[0], "firstLevel": first[1], "lacks": "; ".join(lacks),
                     "anchor": info["meta"].get("anchor", ""), "suggestedRoles": info["meta"].get("roles", "")})
    return rows


def compute(trails: list[dict], include_demo: bool = False, lexicon: Lexicon | None = None,
            sim: dict | None = None) -> dict:
    lexicon = lexicon or Lexicon.from_repo()
    sim = sim or simulate(trails, lexicon, include_demo)
    sentences, hints = sim["sentences"], sim["hints"]
    metrics: dict[str, float] = {}
    n = len(sentences)
    metrics["coverage.sentences.unknownOverOneShare"] = (
        sum(1 for s in sentences if len(s["unknown"]) > MAX_UNKNOWN) / n if n else 0.0)
    metrics["coverage.sentences.meanUnknown"] = sum(len(s["unknown"]) for s in sentences) / n if n else 0.0
    metrics["coverage.sentences.hintFullyKnownShare"] = (
        sum(1 for h in hints if not h["missing"]) / len(hints) if hints else 1.0)
    total_words = sum(h["words"] for h in hints)
    metrics["coverage.sentences.hintWordCoverage"] = (
        1.0 - sum(len(h["missing"]) for h in hints) / total_words if total_words else 1.0)

    rows = [r for r in core_rows(sim, lexicon) if not r["gap"]]
    if rows:
        metrics["coverage.recycling.medianEncounters"] = float(statistics.median(r["encounters"] for r in rows))
        metrics["coverage.recycling.targetMetShare"] = sum(
            1 for r in rows if r["encounters"] >= ENCOUNTER_TARGET and r["contexts"] >= ROLE_TARGET) / len(rows)
        metrics["coverage.recycling.seenShare"] = sum(1 for r in rows if r["encounters"] > 0) / len(rows)
        metrics["coverage.recycling.medianRetrievals"] = float(statistics.median(r["retrievals"] for r in rows))
    else:
        for key in METRIC_SPECS:
            if key.startswith("coverage.recycling."):
                metrics[key] = 0.0
    return {"metrics": metrics, "sim": sim, "rows": core_rows(sim, lexicon), "lexicon": lexicon}


# -------------------------------------------------------------------------------------------- output

GAP_COLUMNS = ["word", "concepts", "kind", "band", "grade", "nikl", "encounters", "contexts", "roles", "retrievals",
               "firstTrail", "firstLevel", "lacks", "anchor", "suggestedRoles"]


def write_gaps(rows: list[dict], path: Path) -> int:
    """The under-exposed core words, worst first. Feeds the content briefs."""
    under = [r for r in rows if r["lacks"]]
    under.sort(key=lambda r: (r["encounters"], r["contexts"], r["retrievals"], r["word"]))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(GAP_COLUMNS)
        for r in under:
            writer.writerow([r["word"], r["concepts"], "not-in-lexicon" if r["gap"] else "core", r["band"], r["grade"],
                             r["nikl"], r["encounters"], r["contexts"], r["roles"], r["retrievals"], r["firstTrail"],
                             r["firstLevel"], r["lacks"], r["anchor"], r["suggestedRoles"]])
    return len(under)


def report(result: dict, baseline: dict | None, with_demo: dict | None) -> str:
    sim = result["sim"]
    out = [f"coverage: {len(sim['path'])} trails, {len(sim['sentences'])} sentences, "
           f"{len(sim['hints'])} hints; learner: known after {KNOWN_AFTER} encounters, "
           f"{TRAILS_PER_DAY} trails per day"]
    out.extend(variety.gap_lines(result["metrics"], baseline, "coverage."))
    if with_demo:
        changed = [k for k in result["metrics"] if abs(result["metrics"][k] - with_demo["metrics"][k]) > variety.ROUNDING]
        out.append("")
        out.append("with the demo trails measured too (not ratcheted): "
                   + (", ".join(f"{k.split('.', 2)[2]} {variety.fmt(with_demo['metrics'][k])}" for k in changed)
                      or "no difference"))
    out.append(f"eojeols the lexicon placed: {sim['placed']} of {sim['eojeols']} "
               f"(the rest are function words, numerals and words in no list)")
    worst = sorted(sim["sentences"], key=lambda s: -len(s["unknown"]))[:5]
    out.append("")
    out.append("sentences with the most unknown words:")
    for s in worst:
        out.append(f"  {s['trail']}.{s['step']}  {s['ko']}  ->  {', '.join(s['unknown'])}")
    core = [r for r in result["rows"] if not r["gap"]]
    out.append("")
    out.append(f"core words (초급 or NIKL A, not mcOnly): {len(core)}; with 10+ encounters: "
               f"{sum(1 for r in core if r['encounters'] >= ENCOUNTER_TARGET)}; never met: "
               f"{sum(1 for r in core if r['encounters'] == 0)}; common words missing from the lexicon: "
               f"{sum(1 for r in result['rows'] if r['gap'])} (see content/curriculum/core-gap.csv)")
    return "\n".join(out)


def load_lexicon(args) -> Lexicon:
    if args.lexicon:
        return Lexicon.from_json(json.loads(args.lexicon.read_text(encoding="utf-8")))
    return Lexicon.from_repo(args.data)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--steps", type=Path, default=variety.STEPS)
    parser.add_argument("--baseline", type=Path, default=variety.BASELINE)
    parser.add_argument("--data", type=Path, default=DATA, help="folder with concepts.json, names.json, lexicon-tags.json")
    parser.add_argument("--lexicon", type=Path, help="a JSON list of words instead of the shipped lexicon (tests)")
    parser.add_argument("--csv", type=Path, default=GAPS_CSV, help="where the under-exposed core words go")
    parser.add_argument("--check", action="store_true", help="fail if a metric is worse than the baseline")
    parser.add_argument("--update-baseline", action="store_true", help="rewrite the coverage metrics of the baseline")
    parser.add_argument("--json", action="store_true", help="print the metrics as JSON")
    parser.add_argument("--tokens", help="print the words the lexicon finds in this text, one per line, and stop")
    args = parser.parse_args(argv)

    if args.tokens is not None:
        print("\n".join(load_lexicon(args).tokens(args.tokens)[0]))
        return 0
    trails = variety.load_steps(args.steps)
    lexicon = load_lexicon(args)
    result = compute(trails, False, lexicon)
    under = write_gaps(result["rows"], args.csv)

    if args.json:
        print(json.dumps(result["metrics"], indent=1))
        return 0
    if args.update_baseline:
        variety.write_baseline(args.baseline, {"coverage.": result["metrics"]}, PARAMS)
        print(f"coverage baseline written: {args.baseline}")
        return 0

    baseline = variety.load_baseline(args.baseline) if args.baseline.exists() else None
    print(report(result, baseline, compute(trails, True, lexicon)))
    print(f"\n{under} under-exposed core words -> {args.csv}")
    if args.check:
        if baseline is None:
            print(f"{args.baseline} is missing. Create it with: python3 tools/variety.py --update-baseline")
            return 1
        if baseline.get("params") != PARAMS:
            print("the simulator parameters differ from the ones the baseline was made with:\n"
                  f"  baseline {baseline.get('params')}\n  now      {PARAMS}\n"
                  "Rewrite it on purpose with: python3 tools/variety.py --update-baseline")
            return 1
        problems = variety.regressions(result["metrics"], baseline, "coverage.")
        if problems:
            print("\ncoverage got worse:\n  " + "\n  ".join(problems))
            print("Fix the content, or, if the change is deliberate: python3 tools/variety.py --update-baseline")
            return 1
        print("coverage: no metric is worse than the baseline.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
