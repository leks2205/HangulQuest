#!/usr/bin/env python3
"""Writes the generated finale sentences into the questline JSON.

The sentences come from SentenceBuilder.ceremony (Java), which SentenceDumpTest dumps to
build/reports/korean/ceremony.tsv. This tool copies them into `finale.textKo` of every trail whose
current text is the broken template ("X라는 길의 의식을 마쳤어요."), of the trails named with --only, or
of every trail with --all. Authored finales of the form "Title — sentence" are never touched.

    ./gradlew test --offline -q --tests '*SentenceDumpTest'
    python3 tools/apply_ceremony_finales.py [--all] [--csv content/review/korean-fixes-2.2.csv]

After a change of the generator, copy ceremony.tsv aside before and after, then rewrite only the finales
that still equal the OLD output (an authored finale never does, so it is never touched):

    python3 tools/apply_ceremony_finales.py --regen-from OLD-ceremony.tsv [--dry-run]

Every change is appended to the CSV (id,file,old,new,rule) for the Korean review.
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
QUESTLINES = ROOT / "src" / "main" / "resources" / "assets" / "hangulquest" / "data" / "questlines"
CEREMONY = ROOT / "build" / "reports" / "korean" / "ceremony.tsv"
BROKEN = "라는 길의 의식을 마쳤어요"


def questline_file(trail_id: str) -> Path:
    """The file of a trail: <id>.json, or the file whose "id" says so (the demo trail is in mill_demo.json)."""
    path = QUESTLINES / f"{trail_id}.json"
    if path.is_file():
        return path
    for other in sorted(QUESTLINES.glob("*.json")):
        if other.name != "index.json" and json.loads(other.read_text(encoding="utf-8")).get("id") == trail_id:
            return other
    return path


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--all", action="store_true", help="rewrite every non-authored finale, not only the broken template")
    parser.add_argument("--only", help="comma-separated trail ids to rewrite regardless of their current text")
    parser.add_argument("--csv", help="append the changes to this review CSV")
    parser.add_argument("--regen-from", help="ceremony.tsv of the OLD generator: rewrite only finales equal to it")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    if not CEREMONY.is_file():
        print(f"missing {CEREMONY}; run the SentenceDumpTest first", file=sys.stderr)
        return 2
    sentences = dict(line.rstrip("\n").split("\t", 1) for line in CEREMONY.read_text(encoding="utf-8").splitlines() if line)

    only = set(args.only.split(",")) if args.only else set()
    previous = {}
    if args.regen_from:
        previous = dict(line.rstrip("\n").split("\t", 1)
                        for line in Path(args.regen_from).read_text(encoding="utf-8").splitlines() if line)
    changes = []
    for trail_id, sentence in sentences.items():
        path = questline_file(trail_id)
        data = json.loads(path.read_text(encoding="utf-8"))
        old = data["finale"].get("textKo", "")
        if args.regen_from:
            if previous.get(trail_id) != old or old == sentence:
                continue
            data["finale"]["textKo"] = sentence
            if not args.dry_run:
                path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
            changes.append((f"trail.{trail_id}.finale", str(path.relative_to(ROOT)), old, sentence,
                            "KO003 ceremony-sentence"))
            continue
        authored = " — " in old and BROKEN not in old and trail_id not in only
        if authored or old == sentence:
            continue
        if BROKEN not in old and not args.all and trail_id not in only:
            continue
        data["finale"]["textKo"] = sentence
        if not args.dry_run:
            path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        changes.append((f"trail.{trail_id}.finale", str(path.relative_to(ROOT)), old, sentence,
                        "KO003 ceremony-sentence"))

    if args.csv and changes and not args.dry_run:
        csv_path = Path(args.csv)
        csv_path.parent.mkdir(parents=True, exist_ok=True)
        new_file = not csv_path.is_file()
        with csv_path.open("a", encoding="utf-8", newline="") as handle:
            writer = csv.writer(handle, lineterminator="\n")
            if new_file:
                writer.writerow(["id", "file", "old", "new", "rule"])
            writer.writerows(changes)
    print(f"{len(changes)} finale line(s) {'would be ' if args.dry_run else ''}rewritten")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
