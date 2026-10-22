#!/usr/bin/env python3
"""Generate the pseudowords of the placement test and write assets/hangulquest/data/pseudowords.json.

A pseudoword is spelled like a Korean word but is not one. The syllables come from words the game and
the common-word list already use, so the nonwords look Korean; every candidate is checked against the
dictionary (names.json, concepts.json, lexicon-tags.json and tools/research/common_words_ko.txt) with
the same rules as learn/research/Pseudowords.java, which re-checks the shipped list in a test.

The list is fixed: run this once, review the result, commit it. Rerunning with another seed changes it.

    python3 tools/research/make_pseudowords.py [--seed 7] [--count 30] [--print]
"""
from __future__ import annotations

import argparse
import json
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/assets/hangulquest/data"
COMMON = ROOT / "tools/research/common_words_ko.txt"

SUFFIXES = ["이", "가", "은", "는", "을", "를", "에", "도", "의", "와", "과", "로", "다", "하다", "하게", "해요",
            "이다", "이에요", "예요", "들"]
CODAS = {0, 1, 4, 8, 16, 17, 21}
VOWELS = {0, 1, 2, 4, 5, 6, 8, 12, 13, 17, 18, 20}
ONSET_NIEUN, ONSET_MIEUM, RIEUL, IEUNG, HIEUH = 2, 6, 5, 11, 18
PLAIN_STOPS = {0, 3, 7, 9, 12}
TENSE = {1, 4, 8, 10, 13}  # ㄲ ㄸ ㅃ ㅆ ㅉ
CODA_G, CODA_R, CODA_B = 1, 8, 17


def parts(ch: str):
    code = ord(ch) - 0xAC00
    if not 0 <= code < 19 * 21 * 28:
        return None
    return code // 28 // 21, code // 28 % 21, code % 28


def changes_sound(coda: int, onset: int) -> bool:
    stop = coda in (CODA_G, CODA_B)
    if onset in (IEUNG, HIEUH):
        return True
    if stop and onset in (ONSET_NIEUN, ONSET_MIEUM):
        return True
    if coda == CODA_R and onset == ONSET_NIEUN:
        return True
    return (stop or coda == CODA_R) and onset in PLAIN_STOPS


def well_formed(text: str) -> bool:
    if not 2 <= len(text) <= 3:
        return False
    previous = 0
    for i, ch in enumerate(text):
        p = parts(ch)
        if p is None:
            return False
        onset, vowel, coda = p
        if coda not in CODAS or vowel not in VOWELS:
            return False
        if onset == RIEUL and (i == 0 or previous != 0):
            return False
        if i > 0 and previous != 0 and changes_sound(previous, onset):
            return False
        previous = coda
    return True


def is_real(candidate: str, dictionary: set[str]) -> bool:
    if candidate in dictionary:
        return True
    for suffix in SUFFIXES:
        if len(candidate) > len(suffix) and candidate.endswith(suffix) and candidate[:-len(suffix)] in dictionary:
            return True
        if candidate + suffix in dictionary:
            return True
    for cut in range(1, len(candidate)):
        if candidate[:cut] in dictionary and candidate[cut:] in dictionary:
            return True
    return False


def tokens(text: str):
    return [t for t in re.split(r"[^가-힣ᄀ-ᇿ㄰-㆏]+", text) if t]


def load_dictionary() -> set[str]:
    words: set[str] = set()
    names = json.loads((DATA / "names.json").read_text(encoding="utf-8"))
    for value in names.values():
        words.update(tokens(value[0]))
    for concept in json.loads((DATA / "concepts.json").read_text(encoding="utf-8")):
        words.update(tokens(concept.get("ko", "")))
        for alias in concept.get("aliases", []):
            words.update(tokens(alias))
    tags = json.loads((DATA / "lexicon-tags.json").read_text(encoding="utf-8"))["tags"]
    for tag in tags.values():
        words.update(tokens(tag.get("ko", "")))
    for line in COMMON.read_text(encoding="utf-8").splitlines():
        if not line.startswith("#"):
            words.update(tokens(line))
    return words


def sentence_tokens() -> set[str]:
    """Every word form in the game's Korean lines; used only to keep nonwords away from real text."""
    words: set[str] = set()
    path = ROOT / "content/korean/lines.jsonl"
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                for token in tokens(json.loads(line).get("ko", "")):
                    words.add(token)
                    words.add(token[:-1])
                    words.add(token[:-2])
    return {w for w in words if len(w) >= 2}


def generate(dictionary: set[str], seed: int, count: int, spread: bool = True) -> list[str]:
    rng = random.Random(seed)
    # stricter than the Java check: no candidate may appear inside any word or sentence form we know
    seen: set[str] = set()
    for word in dictionary | sentence_tokens():
        for size in (2, 3):
            for i in range(len(word) - size + 1):
                seen.add(word[i:i + size])
    # syllables of real words, so the nonwords sound like Korean
    # every syllable of every real word, so frequent syllables are drawn more often; no tense onsets
    counts: dict[str, int] = {}
    for word in dictionary:
        for ch in word:
            counts[ch] = counts.get(ch, 0) + 1
    inventory = []
    for ch, n in sorted(counts.items()):
        p = parts(ch)
        if p is not None and p[2] in CODAS and p[1] in VOWELS and p[0] not in TENSE and n >= 4:
            inventory.extend([ch] * n)
    chosen: list[str] = []
    firsts: set[str] = set()
    seconds: set[str] = set()
    attempts = 0
    while len(chosen) < count and attempts < 200_000:
        attempts += 1
        length = 3 if len(chosen) % 3 == 2 else 2
        candidate = "".join(rng.choice(inventory) for _ in range(length))
        if candidate in seen or len(set(candidate)) < len(candidate) or candidate in chosen or not well_formed(candidate) or is_real(candidate, dictionary):
            continue
        # spread the list out: no two items share a first or a last syllable
        if spread and (candidate[0] in firsts or candidate[-1] in seconds):
            continue
        firsts.add(candidate[0])
        seconds.add(candidate[-1])
        chosen.append(candidate)
    return chosen


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--count", type=int, default=30)
    parser.add_argument("--print", action="store_true", help="print instead of writing the file")
    parser.add_argument("--words", help="write exactly these space-separated words (after vetting) instead of generating")
    args = parser.parse_args(argv)
    dictionary = load_dictionary()
    if args.words:
        words = args.words.split()
        bad = [w for w in words if not well_formed(w) or is_real(w, dictionary)]
        if bad:
            print(f"rejected by the vetting rules: {' '.join(bad)}", file=sys.stderr)
            return 1
        args.count = len(words)
    else:
        words = generate(dictionary, args.seed, args.count, spread=not args.print)
    if len(words) < args.count:
        print(f"only {len(words)} pseudowords found", file=sys.stderr)
        return 1
    if args.print:
        print(" ".join(words))
        return 0
    document = {
        "schema": 1,
        "comment": "Nonwords for the placement test (Yes/No vocabulary test). Made by tools/research/make_pseudowords.py "
                   "and checked against the game's vocabulary and a common-word list; see PseudowordsTest. "
                   "Waiting for native review: if one of these is a real word, or sounds wrong, replace it.",
        "words": [{"id": f"pseudo.{i}", "ko": ko} for i, ko in enumerate(words, 1)],
    }
    out = DATA / "pseudowords.json"
    lines = ['{', f'\t"schema": 1,', '\t"comment": ' + json.dumps(document["comment"], ensure_ascii=False) + ',',
             '\t"words": [']
    for i, item in enumerate(document["words"]):
        comma = "," if i + 1 < len(document["words"]) else ""
        lines.append("\t\t" + json.dumps(item, ensure_ascii=False) + comma)
    lines += ["\t]", "}", ""]
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {len(words)} pseudowords to {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
