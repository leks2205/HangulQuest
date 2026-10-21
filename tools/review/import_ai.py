#!/usr/bin/env python3
"""Import an AI review (JSONL) into the aiNotes of content/korean/review-status.json.

    python3 tools/review/import_ai.py --reviewer claude claude-1.jsonl claude-2.jsonl
    python3 tools/review/import_ai.py --reviewer gemini gemini-*.jsonl --packet packet-1.tsv packet-2.tsv

Each JSONL line is {"id", "verdict": "ok|minor|error", "issue", "suggestion"}. The review is taken
to be about the line's current text. With --packet (review packets whose last column is the text that
was reviewed) a verdict is only taken when that text is still the current text.

The status of the imported lines is recomputed: both reviewers "ok" gives ai-checked; an "error", or
two different verdicts, gives the needs-native flag.
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import LINES_PATH, REVIEWERS_PATH, STATUS_PATH, lines_by_id, read_json, read_lines, read_status, write_status  # noqa: E402
import status as status_mod  # noqa: E402

VERDICTS = ("ok", "minor", "error")


def read_jsonl(paths: list[Path]) -> list[dict]:
    rows = []
    for path in paths:
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                rows.append(json.loads(line))
    return rows


def read_packets(paths: list[Path]) -> dict[str, str]:
    """id -> reviewed text, from packet TSVs (id, rule, context, old, new)."""
    reviewed = {}
    for path in paths:
        with path.open(encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle, delimiter="\t", quoting=csv.QUOTE_NONE):
                reviewed[row["id"]] = row["new"]
    return reviewed


def import_notes(status: dict, records: list[dict], reviewer: str, rows: list[dict],
                 reviewed: dict[str, str] | None = None) -> dict[str, int]:
    """Store the verdicts in status['lines'][id]['aiNotes'][reviewer]. Returns counters."""
    by_id = lines_by_id(records)
    counts = {"imported": 0, "unknown-id": 0, "stale-text": 0, "bad-verdict": 0}
    for row in rows:
        rid = row.get("id", "")
        record = by_id.get(rid)
        if record is None or rid not in status["lines"]:
            counts["unknown-id"] += 1
            continue
        verdict = str(row.get("verdict", "")).strip().lower()
        if verdict not in VERDICTS:
            counts["bad-verdict"] += 1
            continue
        if reviewed is not None and rid in reviewed and reviewed[rid] != record["ko"]:
            counts["stale-text"] += 1
            continue
        entry = status["lines"][rid]
        if entry["textHash"] != record["textHash"]:
            counts["stale-text"] += 1
            continue
        entry["aiNotes"][reviewer] = {
            "verdict": verdict, "issue": str(row.get("issue", ""))[:500],
            "suggestion": str(row.get("suggestion", ""))[:200], "textHash": record["textHash"],
        }
        counts["imported"] += 1
    return counts


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", nargs="+", type=Path, help="review JSONL files")
    parser.add_argument("--reviewer", required=True, help="claude, gemini, or another reviewer name")
    parser.add_argument("--packet", nargs="*", type=Path, default=[], help="packet TSVs to check the reviewed text")
    parser.add_argument("--lines", default=str(LINES_PATH))
    parser.add_argument("--status", default=str(STATUS_PATH))
    parser.add_argument("--reviewers", default=str(REVIEWERS_PATH))
    args = parser.parse_args(argv)

    records = read_lines(Path(args.lines))
    status = read_status(Path(args.status))
    status_mod.sync(records, status, read_json(Path(args.reviewers), {}), status_mod.lint_bad_ids(records))
    rows = read_jsonl([p for p in args.files if p.is_file() and p.stat().st_size > 0])
    counts = import_notes(status, records, args.reviewer, rows, read_packets(args.packet) if args.packet else None)
    status_mod.sync(records, status, read_json(Path(args.reviewers), {}), status_mod.lint_bad_ids(records))
    write_status(status, Path(args.status))
    print(f"{args.reviewer}: {counts}; {status_mod.summary(status)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
