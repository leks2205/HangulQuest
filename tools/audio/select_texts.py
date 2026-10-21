#!/usr/bin/env python3
"""Pick the texts for a pilot render and write them as JSONL {id, ko}.

Words: the concepts of band 초급 first, then the other curriculum bands, then the curated
(non-generated) concepts by tier and frequency.
Sentences: authored quest sentences from build/reports/korean/sentences.tsv
(run `./gradlew test --tests '*SentenceDumpTest'` first), spread over the levels.

Ids become sound event names, so they stay in [a-z0-9_.]:
  word.<conceptId>          one event per word, a voice is picked by the game at random
  sentence.<sha1(text)[:10]>  stable for the same text
"""
import argparse
import csv
import json
from pathlib import Path

from common import DATA, REPO, WORK, sha1

BAND_ORDER = {"초급": 0, "중급": 1, "고급": 2, None: 3}


def pick_words(count):
    concepts = json.loads((DATA / "concepts.json").read_text(encoding="utf-8"))
    tags = json.loads((DATA / "lexicon-tags.json").read_text(encoding="utf-8"))["tags"]

    def key(c):
        tag = tags.get(c["id"], {})
        return (BAND_ORDER.get(tag.get("curriculumBand")), c["id"].startswith("gen_"), c.get("tier", 9), tag.get("freqRank") or 10**9, c["id"])

    ordered = sorted(concepts, key=key)
    rows = []
    for c in ordered:
        if len(rows) >= count:
            break
        rows.append({"id": f"word.{c['id']}", "ko": c["ko"], "concept": c["id"]})
    return rows


def pick_sentences(count, tsv):
    seen, pool = set(), []
    with open(tsv, encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            ko = row["ko"].strip()
            if not row["source"].startswith("authored") or ko in seen:
                continue
            if not 6 <= len(ko) <= 30 or ko[-1] not in ".?!":
                continue
            seen.add(ko)
            pool.append((int(row["level"]), ko))
    pool.sort()
    # an even stride keeps every level in the sample
    step = max(1, len(pool) // count)
    return [{"id": "sentence." + sha1(ko)[:10], "ko": ko} for _, ko in pool[::step][:count]]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--words", type=int, default=300)
    ap.add_argument("--sentences", type=int, default=100)
    ap.add_argument("--tsv", default=str(REPO / "build/reports/korean/sentences.tsv"))
    ap.add_argument("--out", default=str(WORK / "texts.jsonl"))
    args = ap.parse_args()
    rows = pick_words(args.words)
    if args.sentences:
        rows += pick_sentences(args.sentences, args.tsv)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"{len(rows)} texts -> {out}")


if __name__ == "__main__":
    main()
