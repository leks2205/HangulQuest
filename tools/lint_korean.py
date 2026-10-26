#!/usr/bin/env python3
"""Korean linter v0 for Hangul Quest.

Reads the sentence dump written by SentenceDumpTest
(build/reports/korean/sentences.tsv: id, level, source, ko, goal), applies the data-driven
rules in tools/lint/rules.json and prints a report. A machine-readable copy goes to
build/reports/korean/lint.json.

    python3 tools/lint_korean.py build/reports/korean/sentences.tsv --fail-on error
    python3 tools/lint_korean.py --self-test          # every rule must fire on its 'bad' example

Known findings that are waiting for a fix in another file (the concept table) are listed in
tools/lint/allowlist.json; they are reported but do not fail the build, and an entry that no
longer matches anything is reported as stale so the list can only shrink.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RULES_PATH = ROOT / "tools" / "lint" / "rules.json"
ALLOWLIST_PATH = ROOT / "tools" / "lint" / "allowlist.json"
DATA_DIR = ROOT / "src" / "main" / "resources" / "assets" / "hangulquest" / "data"
DEFAULT_OUT = ROOT / "build" / "reports" / "korean" / "lint.json"
REASONS = {
    "concept": "waits for content/review/concept-ko-fixes-2.2.csv (concepts.json belongs to another work package)",
    "trail": "waits for the icon/referent split (WP0.2): the catalog still resolves the goal item to the verb concept "
             "that merely uses it as its picture",
}

HANGUL_RUN = re.compile(r"[가-힣]+")


# --------------------------------------------------------------------------- Hangul helpers

def is_syllable(ch: str) -> bool:
    return "가" <= ch <= "힣"


def jong(ch: str) -> int:
    """Final consonant index of a syllable (0 = none, 8 = ㄹ)."""
    return (ord(ch) - 0xAC00) % 28 if is_syllable(ch) else 0


def has_batchim(ch: str) -> bool:
    return jong(ch) != 0


def split_clauses(text: str) -> list[str]:
    return [c.strip() for c in re.split(r"[.!?\n]| — |, ", text) if c.strip()]


# --------------------------------------------------------------------------- lexicon

class Lexicon:
    """Known Korean nouns (vanilla names plus concept words); makes particle checks precise."""

    def __init__(self, extra: set[str]):
        self.nouns: set[str] = set()
        names = DATA_DIR / "names.json"
        if names.is_file():
            data = json.loads(names.read_text(encoding="utf-8"))
            for key, value in data.items():
                if key.split(".")[0] in ("item", "block", "entity", "biome", "effect", "enchantment"):
                    if isinstance(value, list) and value:
                        self._add(value[0])
        for word in extra:
            if not word.endswith("다"):  # concept words like 보다 / 짓다 are verbs, not nouns
                self._add(word)

    def _add(self, name: str) -> None:
        words = name.split()
        for start in range(len(words)):
            tail = "".join(w for w in words[start:] if HANGUL_RUN.fullmatch(w))
            if tail and all(HANGUL_RUN.fullmatch(w) for w in words[start:]):
                self.nouns.add(words[-1] if start == len(words) - 1 else tail)
        for word in words:
            if HANGUL_RUN.fullmatch(word):
                self.nouns.add(word)

    def __contains__(self, word: str) -> bool:
        return word in self.nouns


# --------------------------------------------------------------------------- checks

class Ctx:
    def __init__(self, lists: dict, lexicon: Lexicon, rows: dict[str, dict]):
        self.lists = lists
        self.lex = lexicon
        self.rows = rows


def check_noop(row, text, params, ctx):
    return []


def check_particle_mismatch(row, text, params, ctx):
    with_final, without_final = params["pair"]
    stoplist = set(ctx.lists.get("noun_stoplist", []))
    out = []
    for token in HANGUL_RUN.findall(text):
        if len(token) < 2 or token[-1] not in (with_final, without_final):
            continue
        stem = token[:-1]
        if token in ctx.lex or token in stoplist:
            continue
        known = stem in ctx.lex
        generic = {with_final, without_final} == {"을", "를"} and len(token) >= 3 and stem not in stoplist
        if not (known or generic):
            continue
        expected = with_final if has_batchim(stem[-1]) else without_final
        if token[-1] != expected:
            out.append(f"'{token}' should be '{stem}{expected}'")
    return out


def check_euro_mismatch(row, text, params, ctx):
    out = []
    for token in HANGUL_RUN.findall(text):
        if token in ctx.lex or len(token) < 2:
            continue
        if token.endswith("으로") and len(token) >= 3:
            stem = token[:-2]
            if stem in ctx.lex and (not has_batchim(stem[-1]) or jong(stem[-1]) == 8):
                out.append(f"'{token}' should be '{stem}로'")
        elif token.endswith("로"):
            stem = token[:-1]
            if stem in ctx.lex and has_batchim(stem[-1]) and jong(stem[-1]) != 8:
                out.append(f"'{token}' should be '{stem}으로'")
    return out


def check_ira_mismatch(row, text, params, ctx):
    out = []
    for match in re.finditer(r"([가-힣]+?)(이라는|라는)(?![가-힣])", text):
        stem, form = match.group(1), match.group(2)
        word = stem + form
        quoted_name = stem in ctx.lex or re.match(r"\s*길", text[match.end():]) is not None
        if form == "라는" and has_batchim(stem[-1]) and quoted_name:
            out.append(f"'{word}' should be '{stem}이라는'")
        elif form == "이라는" and not has_batchim(stem[-1]) and quoted_name:
            out.append(f"'{word}' should be '{stem}라는'")
    return out


def check_dictionary_verb_object(row, text, params, ctx):
    out = []
    for token in HANGUL_RUN.findall(text):
        if len(token) >= 3 and token[-1] in "를을" and token[-2] == "다":
            if token[:-1] in ctx.lex or token in ctx.lex or token[:-1] in ctx.lists.get("noun_stoplist", []):
                continue
            out.append(f"'{token}' looks like a dictionary-form verb used as an object")
    return out


MINE_FORM = re.compile(r"캐(?:요|야|어|지|고|서|면|는|려|자)|캐(?![가-힣])|캘(?![가-힣])|캤")
PLACE_FORM = re.compile(r"놓(?:아|을|지|았|고|으|는)|놓(?![가-힣치])")
COLLECT_FORM = re.compile(r"모(?:아|으|을|았)")


def _object_phrase(clause: str, verb_start: int, lex: "Lexicon") -> str | None:
    """Words of the object (or a topicalised known noun) in front of the verb in one clause."""
    before = clause[:verb_start]
    objects = list(re.finditer(r"([가-힣]+)(?:을|를)(?![가-힣])", before))
    if objects:
        tail = before[: objects[-1].end()]
        words = tail.split()
        return " ".join(words[-3:])
    # 은/는 also ends adjectives and verbs (깊은, 먹는), so a topic must be a known noun.
    topics = [m for m in re.finditer(r"([가-힣]+)(?:은|는)(?![가-힣])", before) if m.group(1) in lex]
    if topics:
        words = before[: topics[-1].end()].split()
        return " ".join(words[-3:])
    return None


def check_mine_non_mineral(row, text, params, ctx):
    ok = ctx.lists["mineral_keywords"] + ctx.lists["root_crops"]
    out = []
    for clause in split_clauses(text):
        for match in MINE_FORM.finditer(clause):
            phrase = _object_phrase(clause, match.start(), ctx.lex)
            if phrase and not any(k in phrase for k in ok):
                out.append(f"'{phrase}' is not a mineral, ore or root crop (캐다)")
                break
    return out


def check_place_wrong_object(row, text, params, ctx):
    wrong = ctx.lists["place_wrong_objects"]
    hints = ctx.lists["place_wrong_hint"]
    out = []
    for clause in split_clauses(text):
        for match in PLACE_FORM.finditer(clause):
            phrase = _object_phrase(clause, match.start(), ctx.lex)
            # the placed thing is the last word before 을/를; a banner or carpet earlier in the clause is an anchor
            noun = phrase.split()[-1] if phrase else None
            hit = next((w for w in wrong if noun and w in noun), None)
            if hit:
                out.append(f"'{phrase}' + 놓다: use {hints.get(hit, '심다/깔다/걸다')}")
                break
    return out


COUNT_ONE = re.compile(r"(?<![가-힣\d])(?:한 개|하나|1개)(?![가-힣])")
# the counters of data/counters.json (개 for things, 마리 for animals, 송이 for flowers ...)
COUNTERS = "개|마리|명|그루|송이|병|자루|장|권|잔|그릇|켤레|척|대|벌|채"
NATIVE_COUNTS = "|".join(sorted((json.loads(Path(__file__).with_name("lint").joinpath("rules.json").read_text(encoding="utf-8"))
                                 ["lists"]["native_numerals"]), key=len, reverse=True))
ANY_COUNT = re.compile(rf"\d+\s?(?:{COUNTERS})|(?<![가-힣])(?:{NATIVE_COUNTS})\s?(?:{COUNTERS})")


def check_collect_count(row, text, params, ctx):
    out = []
    goal = row.get("goal", "") or ""
    for clause in split_clauses(text):
        if not COLLECT_FORM.search(clause):
            continue
        if COUNT_ONE.search(clause):
            out.append("모으다 with a count of 1: use 구하다")
            continue
        if goal.startswith("AcquireItem") and re.search(r" x1$", goal):
            out.append("모으다 for a goal of 1 item: use 구하다")
        elif (row["id"].endswith(".clue") and goal.startswith("AcquireItem")
              and not ANY_COUNT.search(clause)):
            out.append("모으다 without a count")
    return out


def check_talk_non_person(row, text, params, ctx):
    ok = ctx.lists["talk_ok_targets"]
    out = []
    for clause in split_clauses(text):
        match = re.search(r"((?:[가-힣]+ ){0,2}[가-힣]*?)(?:와|과) 이야기", clause)
        if not match:
            continue
        phrase = match.group(1).strip() + clause[match.end(1): match.end(1) + 1]
        words = clause[: match.end(1) + 1]
        if not any(t in words for t in ok):
            out.append(f"이야기하다 with '{match.group(1).strip()}': only villagers and traders talk")
    goal = row.get("goal", "") or ""
    if "이야기" in text and goal.startswith("InteractEntity"):
        entity = goal.split()[1] if len(goal.split()) > 1 else ""
        if entity not in ("minecraft:villager", "minecraft:wandering_trader") and not out:
            out.append(f"이야기하다 for {entity}")
    return out


def check_alive_undead(row, text, params, ctx):
    names = sorted(ctx.lists["undead"], key=len, reverse=True)
    pattern = re.compile(r"살아 ?있는\s+(?:[가-힣]+\s+)?(?:%s)" % "|".join(map(re.escape, names)))
    return [f"'{m.group(0)}': undead mobs are not alive" for m in pattern.finditer(text)]


def check_mixed_numerals(row, text, params, ctx):
    counters = "|".join(ctx.lists["native_counters"])
    digits = re.search(rf"\d+\s?(?:{counters})(?![가-힣])", text)
    numerals = "|".join(sorted(ctx.lists["native_numerals"], key=len, reverse=True))
    native = re.search(rf"(?<![가-힣])(?:{numerals}) ?(?:{counters})(?![가-힣])", text)
    if digits and native:
        return [f"'{digits.group(0)}' mixed with '{native.group(0)}'"]
    return []


def check_digit_native_counter(row, text, params, ctx):
    counters = "|".join(ctx.lists["native_counters"])
    out = []
    for m in re.finditer(rf"(\d+)\s?({counters})(?![가-힣])", text):
        if int(m.group(1)) <= 99:
            out.append(f"'{m.group(0)}' should use a native numeral")
    return out


def _ending_class(sentence: str) -> str | None:
    s = sentence.strip().rstrip(".!?…~ ")
    if not s:
        return None
    # The Busan and Jeju dialects close 합쇼체 with -심더 / -수다 and 해요체 with -예 / -우다.
    if re.search(r"(?:습니다|ㅂ니다|입니다|니다|습니까|니까|심더|니더|수다|수꽈|수과)$", s):
        return "합쇼체"
    if re.search(r"(?:요|예|우다|우꽈|우과)$", s):
        return "해요체"
    if re.search(r"[^니]다$", s) or re.search(r"[는ㄴ]다$", s):
        return "한다체"
    if re.search(r"(?:해|어|아|야|지|네|래|니|냐|자|라)$", s) and len(s) > 3:
        return "반말"
    return None


def check_mixed_speech_levels(row, text, params, ctx):
    body = text.split(" — ")[-1]
    classes = Counter()
    for sentence in re.split(r"(?<=[.!?])\s*", body):
        if not sentence.endswith((".", "!", "?")):
            continue
        cls = _ending_class(sentence)
        if cls:
            classes[cls] += 1
    if len(classes) > 1:
        return ["mixed speech levels: " + ", ".join(sorted(classes))]
    return []


def check_desc_is_word(row, text, params, ctx):
    if not row["id"].endswith(".descKo"):
        return []
    sibling = ctx.rows.get(row["id"][: -len("descKo")] + "ko")
    if not sibling:
        return []
    word = sibling["ko"].strip()
    plain = text.strip().strip(".!? ")
    if plain == word or (plain.startswith(word) and len(plain) <= len(word) + 2):
        return [f"descKo only repeats '{word}'"]
    return []


CHECKS = {
    "noop": check_noop,
    "particle_mismatch": check_particle_mismatch,
    "euro_mismatch": check_euro_mismatch,
    "ira_mismatch": check_ira_mismatch,
    "dictionary_verb_object": check_dictionary_verb_object,
    "mine_non_mineral": check_mine_non_mineral,
    "place_wrong_object": check_place_wrong_object,
    "collect_count": check_collect_count,
    "talk_non_person": check_talk_non_person,
    "alive_undead": check_alive_undead,
    "mixed_numerals": check_mixed_numerals,
    "digit_native_counter": check_digit_native_counter,
    "mixed_speech_levels": check_mixed_speech_levels,
    "desc_is_word": check_desc_is_word,
}


# --------------------------------------------------------------------------- engine

def load_rules(path: Path = RULES_PATH) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    for rule in data["rules"]:
        if rule["type"] == "check" and rule["check"] not in CHECKS:
            raise SystemExit(f"rule {rule['id']}: unknown check function {rule['check']!r}")
        if rule["type"] == "regex":
            rule["_re"] = re.compile(rule["pattern"])
    return data


def row_scope(row: dict) -> str:
    return "concepts" if row["id"].startswith("concept.") else "sentences"


def lint_rows(rows: list[dict], rules: dict, selected: set[str] | None = None,
              lexicon_rows: list[dict] | None = None) -> list[dict]:
    # lexicon_rows lets a caller lint a few rows against the full concept table (see lint_text)
    source = rows if lexicon_rows is None else lexicon_rows
    lexicon = Lexicon({r["ko"] for r in source if r["id"].startswith("concept.") and r["id"].endswith(".ko")
                       and HANGUL_RUN.fullmatch(r["ko"].replace(" ", ""))})
    by_id = {r["id"]: r for r in rows}
    ctx = Ctx(rules["lists"], lexicon, by_id)
    findings = []
    for row in rows:
        if row["id"].startswith("pseudo."):
            continue  # a nonword of the placement test: there is no sentence to lint
        scope = row_scope(row)
        text = row["ko"]
        for rule in rules["rules"]:
            if selected and rule["id"] not in selected:
                continue
            if rule["scope"] not in ("all", scope):
                continue
            if rule["type"] == "regex":
                messages = [f"'{m.group(0)}'" for m in rule["_re"].finditer(text)][:3]
            else:
                messages = CHECKS[rule["check"]](row, text, rule.get("params", {}), ctx)
            for message in messages:
                findings.append({
                    "rule": rule["id"], "name": rule["name"], "severity": rule["severity"],
                    "id": row["id"], "level": row.get("level", ""), "source": row.get("source", ""),
                    "ko": text, "message": message, "fix": rule["fix"], "allowlisted": False,
                })
    return findings


_CONCEPT_ROWS: list[dict] | None = None


def concept_rows() -> list[dict]:
    """The concept words as lint rows, so a lone sentence is checked against the real noun list."""
    global _CONCEPT_ROWS
    if _CONCEPT_ROWS is None:
        path = DATA_DIR / "concepts.json"
        data = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else []
        _CONCEPT_ROWS = [{"id": f"concept.{c['id']}.ko", "level": "0", "source": "", "ko": c["ko"], "goal": ""}
                         for c in data if c.get("ko")]
    return _CONCEPT_ROWS


def lint_text(text: str, row_id: str = "suggestion.clue", level: int | str = "", rules: dict | None = None,
              goal: str = "") -> list[dict]:
    """Lint one piece of Korean text with the shipped rules; returns the findings (same shape as lint_rows).

    The scope follows the id the way row_scope does: ids starting with "concept." use the concept
    rules, everything else the sentence rules. Allowlist entries are not applied.
    """
    row = {"id": row_id, "level": str(level), "source": "", "ko": text, "goal": goal}
    return lint_rows([row], rules or load_rules(), lexicon_rows=concept_rows())


def read_rows(path: Path) -> list[dict]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t", quoting=csv.QUOTE_NONE)
        rows = []
        for row in reader:
            row["ko"] = row.get("ko") or ""
            rows.append(row)
    return rows


def apply_allowlist(findings: list[dict], path: Path) -> list[dict]:
    if not path.is_file():
        return []
    entries = json.loads(path.read_text(encoding="utf-8")).get("entries", [])
    keys = {(e["rule"], e["id"]): e for e in entries}
    used = set()
    for f in findings:
        key = (f["rule"], f["id"])
        if key in keys:
            f["allowlisted"] = True
            used.add(key)
    return [keys[k] for k in keys if k not in used]


def self_test(rules: dict) -> int:
    lexicon = Lexicon(set())
    failures = 0
    for rule in rules["rules"]:
        for kind, text in (("bad", rule.get("bad", "")), ("good", rule.get("good", ""))):
            if not text:
                print(f"FAIL {rule['id']} has no {kind} example")
                failures += 1
                continue
            rid = "concept.t.descKo" if rule["scope"] == "concepts" else "selftest.t.clue"
            row = {"id": rid, "level": "1", "source": "selftest", "ko": text, "goal": ""}
            rows = [row]
            if rule["scope"] == "concepts":
                rows.append({"id": "concept.t.ko", "level": "0", "source": "selftest",
                             "ko": "엔더의 눈" if kind == "bad" else "눈", "goal": ""})
            hits = [f for f in lint_rows(rows, rules, {rule["id"]}) if f["id"] == rid]
            if kind == "bad" and not hits:
                print(f"FAIL {rule['id']} {rule['name']}: did not flag {text!r}")
                failures += 1
            if kind == "good" and hits:
                print(f"FAIL {rule['id']} {rule['name']}: flagged the good example {text!r}: "
                      f"{hits[0]['message']}")
                failures += 1
    # Cross-check the particle helpers against hand-checked pairs.
    for stem, expected in (("돌", True), ("나무", False), ("숲", True), ("강", True), ("물", True)):
        if has_batchim(stem[-1]) != expected:
            print(f"FAIL has_batchim({stem})")
            failures += 1
    if failures:
        print(f"self-test: {failures} failure(s)")
        return 1
    print(f"self-test: {len(rules['rules'])} rules ok")
    return 0


def print_report(findings: list[dict], rules: dict, rows: list[dict], stale: list[dict],
                 examples: int) -> None:
    by_rule: dict[str, list[dict]] = defaultdict(list)
    for f in findings:
        by_rule[f["rule"]].append(f)
    sentences = sum(1 for r in rows if not r["id"].startswith("concept."))
    print(f"Korean lint: {len(rows)} rows ({sentences} sentences, {len(rows) - sentences} concept fields), "
          f"{len(rules['rules'])} rules")
    print()
    print(f"{'rule':7} {'severity':8} {'open':>5} {'allowed':>7}  name")
    for rule in rules["rules"]:
        items = by_rule.get(rule["id"], [])
        open_count = sum(1 for f in items if not f["allowlisted"])
        allowed = sum(1 for f in items if f["allowlisted"])
        if items or "--all" in sys.argv:
            print(f"{rule['id']:7} {rule['severity']:8} {open_count:5d} {allowed:7d}  {rule['name']}")
    shown_any = False
    for rule in rules["rules"]:
        items = [f for f in by_rule.get(rule["id"], []) if not f["allowlisted"]]
        if not items:
            continue
        if not shown_any:
            print("\nExamples (open findings):")
            shown_any = True
        print(f"\n[{rule['id']}] {rule['name']} - {rule['description']}")
        print(f"    fix: {rule['fix']}")
        for f in items[:examples]:
            print(f"    {f['id']}: {f['ko']}")
            print(f"        {f['message']}")
        if len(items) > examples:
            print(f"    ... and {len(items) - examples} more")
    if stale:
        print(f"\n{len(stale)} stale allowlist entries (they no longer match anything; delete them):")
        for e in stale[:10]:
            print(f"    {e['rule']} {e['id']}")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tsv", nargs="?", help="sentences.tsv from SentenceDumpTest")
    parser.add_argument("--rules", default=str(RULES_PATH))
    parser.add_argument("--allowlist", default=str(ALLOWLIST_PATH))
    parser.add_argument("--out", default=str(DEFAULT_OUT), help="lint.json path")
    parser.add_argument("--fail-on", choices=["error", "warning", "never"], default="never")
    parser.add_argument("--rule", help="comma-separated rule ids to run")
    parser.add_argument("--examples", type=int, default=5)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--all", action="store_true", help="list rules without findings too")
    parser.add_argument("--write-allowlist", metavar="PREFIXES",
                        help="write current findings whose id starts with one of the comma-separated PREFIXES "
                             "(e.g. concept.,trail.) to the allowlist")
    args = parser.parse_args(argv)

    rules = load_rules(Path(args.rules))
    if args.self_test:
        return self_test(rules)
    if not args.tsv:
        parser.error("a sentences.tsv path is required (or use --self-test)")
    rows = read_rows(Path(args.tsv))
    selected = set(args.rule.split(",")) if args.rule else None
    findings = lint_rows(rows, rules, selected)

    if args.write_allowlist:
        prefixes = tuple(args.write_allowlist.split(","))
        picked = [f for f in findings if f["id"].startswith(prefixes)]
        entries = [{"rule": f["rule"], "id": f["id"], "reason": REASONS["concept" if f["id"].startswith("concept.") else "trail"]}
                   for f in picked]
        Path(args.allowlist).write_text(
            json.dumps({"comment": "Findings that wait for a fix in another file. Delete entries as they are fixed; "
                                   "the linter reports entries that no longer match.",
                        "entries": entries}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"wrote {len(entries)} allowlist entries to {args.allowlist}")
        return 0

    stale = apply_allowlist(findings, Path(args.allowlist)) if not selected else []
    print_report(findings, rules, rows, stale, args.examples)

    open_errors = sum(1 for f in findings if f["severity"] == "error" and not f["allowlisted"])
    open_warnings = sum(1 for f in findings if f["severity"] == "warning" and not f["allowlisted"])
    allowed = sum(1 for f in findings if f["allowlisted"])
    summary = {"rows": len(rows), "errors": open_errors, "warnings": open_warnings, "allowlisted": allowed,
               "staleAllowlist": len(stale)}
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"summary": summary,
                               "rules": [{k: v for k, v in r.items() if not k.startswith("_")} for r in rules["rules"]],
                               "findings": findings}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"\nsummary: {open_errors} errors, {open_warnings} warnings, {allowed} allowlisted "
          f"(details: {out})")
    if args.fail_on == "error" and open_errors:
        return 1
    if args.fail_on == "warning" and (open_errors or open_warnings):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
