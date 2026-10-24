#!/usr/bin/env python3
"""Pick the texts for a pilot render and write them as JSONL {id, ko}.

Words: the concepts of band 초급 first, then the other curriculum bands, then the curated
(non-generated) concepts by tier and frequency.
Sentences: authored quest sentences from build/reports/korean/sentences.tsv
(run `./gradlew test --tests '*SentenceDumpTest'` first), spread over the levels.

--all takes every concept and every sentence that passes the filter instead of a sample. With --manifest it
also reports how many of them already have a clip in every voice, and --missing-out writes just the others.

Villager lines have their own tool, dialogue_texts.py.

Ids become sound event names, so they stay in [a-z0-9_.]:
  word.<conceptId>          one event per word, a voice is picked by the game at random
  sentence.<sha1(text)[:10]>  stable for the same text
"""
import argparse
import csv
import json
from pathlib import Path

from common import DATA, OUT, REPO, WORK, default_voices, load_voices, sha1

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
        if count is not None and len(rows) >= count:
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
    if count is None:
        return [{"id": "sentence." + sha1(ko)[:10], "ko": ko} for _, ko in pool]
    # an even stride keeps every level in the sample
    step = max(1, len(pool) // count)
    return [{"id": "sentence." + sha1(ko)[:10], "ko": ko} for _, ko in pool[::step][:count]]


def missing(rows, manifest, voices=None):
    """Rows without a clip for the same text in every voice they need (a changed text counts as missing).

    A villager line names its voices; any other row needs every voice of the list given, or else every voice
    of the manifest."""
    rendered = manifest.get("voices", {})
    if not rendered:
        return list(rows)
    plain = list(voices or rendered)
    out = []
    for r in rows:
        want = sha1(r["ko"])
        need = r.get("voices", plain)
        if any(rendered.get(v, {}).get("entries", {}).get(r["id"], {}).get("text_sha1") != want for v in need):
            out.append(r)
    return out


def write_jsonl(path, rows):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--words", type=int, default=300)
    ap.add_argument("--sentences", type=int, default=100)
    ap.add_argument("--all", action="store_true", help="every concept and every eligible sentence")
    ap.add_argument("--tsv", default=str(REPO / "build/reports/korean/sentences.tsv"))
    ap.add_argument("--out", default=str(WORK / "texts.jsonl"))
    ap.add_argument("--manifest", default=str(OUT / "manifest.json"), help="render manifest, to count what is already done")
    ap.add_argument("--missing-out", help="write only the texts that still need a clip here")
    args = ap.parse_args()
    if args.all:
        words, sentences = pick_words(None), pick_sentences(None, args.tsv)
    else:
        words = pick_words(args.words)
        sentences = pick_sentences(args.sentences, args.tsv) if args.sentences else []
    rows = words + sentences
    write_jsonl(args.out, rows)
    print(f"{len(rows)} texts ({len(words)} words, {len(sentences)} sentences) -> {args.out}")
    manifest_path = Path(args.manifest)
    if manifest_path.exists():
        todo = missing(rows, json.loads(manifest_path.read_text(encoding="utf-8")), default_voices(load_voices()))
        n_words = sum(r["id"].startswith("word.") for r in todo)
        print(f"{len(todo)} without a clip yet ({n_words} words, {len(todo) - n_words} sentences)")
        if args.missing_out:
            write_jsonl(args.missing_out, todo)
            print(f"-> {args.missing_out}")


if __name__ == "__main__":
    main()
