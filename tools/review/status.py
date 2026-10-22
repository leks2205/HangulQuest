#!/usr/bin/env python3
"""Review status of every Korean line (content/korean/review-status.json).

    python3 tools/review/status.py sync     # follow lines.jsonl: new ids, changed text, lint, AI verdicts
    python3 tools/review/status.py check    # fail on drift (CI)

A line moves unreviewed -> lint-ok -> ai-checked -> native-approved, or to native-flagged. The status
is derived from the evidence stored with the line (lint result, AI notes, approvals, flags), so it can
always be recomputed; when the text hash of a line changes, the evidence for the old text is dropped
and the status falls back.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from common import (COUNTING_PROFICIENCY, LINES_PATH, REVIEWERS_PATH, ROOT, STATUS_PATH, read_json,  # noqa: E402
                    read_lines, read_status, text_hash, write_status)

AI_REVIEWERS = ("claude", "gemini")
AUTO_FLAGS = ("needs-native", "ai-error", "ai-disagree", "ai-minor", "lint-known-issue")
APPROVALS_NEEDED = 2
SAMPLE_SIZE = 12


def new_entry(hash_: str) -> dict:
    return {"status": "unreviewed", "textHash": hash_, "approvals": [], "flags": [], "aiNotes": {}}


def trusted_logins(reviewers: dict | None) -> set[str]:
    people = (reviewers or {}).get("reviewers", {})
    return {login.lower() for login, info in people.items() if info.get("trusted")}


def valid_approvals(approvals: list[dict]) -> list[dict]:
    """One counting approval per login."""
    seen, out = set(), []
    for item in approvals:
        login = item.get("login", "").lower()
        if login and login not in seen and item.get("proficiency") in COUNTING_PROFICIENCY:
            seen.add(login)
            out.append(item)
    return out


def is_native_approved(approvals: list[dict], trusted: set[str]) -> bool:
    good = valid_approvals(approvals)
    return len(good) >= APPROVALS_NEEDED or any(a["login"].lower() in trusted for a in good)


def ai_state(notes: dict, hash_: str) -> tuple[str, list[str]]:
    """('checked'|'minor'|'flagged'|'partial'|'none', auto flags) from the AI notes of the current text."""
    current = {name: n for name, n in notes.items() if n.get("textHash", hash_) == hash_}
    verdicts = {name: n.get("verdict") for name, n in current.items()}
    if not verdicts:
        return "none", []
    flags = []
    if "error" in verdicts.values():
        flags += ["ai-error", "needs-native"]
    if len(verdicts) >= 2 and len(set(verdicts.values())) > 1:
        flags += ["ai-disagree", "needs-native"]
    if flags:
        return "flagged", sorted(set(flags))
    if len(verdicts) < len(AI_REVIEWERS):
        return "partial", []
    if set(verdicts.values()) == {"ok"}:
        return "checked", []
    return "minor", ["ai-minor"]


def derive(entry: dict, lint_bad: bool, trusted: set[str]) -> str:
    if any(flag.startswith("native-flagged") for flag in entry["flags"]):
        return "native-flagged"
    if is_native_approved(entry["approvals"], trusted):
        return "native-approved"
    if lint_bad:
        return "unreviewed"
    if ai_state(entry["aiNotes"], entry["textHash"])[0] == "checked":
        return "ai-checked"
    return "lint-ok"


def refresh_entry(entry: dict, lint_bad: bool, trusted: set[str]) -> None:
    """Recompute the automatic flags and the status of one entry."""
    manual = [f for f in entry["flags"] if f not in AUTO_FLAGS]
    _, auto = ai_state(entry["aiNotes"], entry["textHash"])
    if lint_bad:
        auto = sorted(set(auto) | {"lint-known-issue"})
    entry["flags"] = sorted(set(manual) | set(auto))
    entry["status"] = derive(entry, lint_bad, trusted)


def sync_line(status: dict, record: dict, lint_bad: bool, trusted: set[str]) -> None:
    entry = status["lines"].get(record["id"])
    if entry is None or entry.get("textHash") != record["textHash"]:
        # New line, or the text changed: nothing said about the old text applies any more.
        authors = (entry or {}).get("authors", [])  # whoever wrote a version stays an author of the line
        entry = new_entry(record["textHash"])
        if authors:
            entry["authors"] = authors
        status["lines"][record["id"]] = entry
    entry["aiNotes"] = {name: n for name, n in entry["aiNotes"].items()
                        if n.get("textHash", entry["textHash"]) == entry["textHash"]}
    refresh_entry(entry, lint_bad, trusted)


def rule_samples(records: list[dict], rule: str) -> list[dict]:
    """A deterministic spread (all levels, stable ids) of the lines one generator rule produced."""
    group = sorted((r for r in records if r["source"].get("rule") == rule),
                   key=lambda r: (r["context"]["level"], r["id"]))
    if len(group) <= SAMPLE_SIZE:
        return group
    step = len(group) / SAMPLE_SIZE
    return [group[int(i * step)] for i in range(SAMPLE_SIZE)]


def rule_hash(samples: list[dict]) -> str:
    return text_hash("\n".join(f"{r['id']}={r['textHash']}" for r in samples))


def sync_rules(status: dict, records: list[dict], trusted: set[str]) -> None:
    rules = sorted({r["source"]["rule"] for r in records if r["source"].get("rule")})
    for rule in rules:
        samples = rule_samples(records, rule)
        digest = rule_hash(samples)
        entry = status["rules"].get(rule)
        if entry is None or entry.get("textHash") != digest:
            entry = {"status": "unreviewed", "textHash": digest, "approvals": [], "flags": [], "aiNotes": {}}
            status["rules"][rule] = entry
        entry["lines"] = sum(1 for r in records if r["source"].get("rule") == rule)
        entry["status"] = "native-approved" if is_native_approved(entry["approvals"], trusted) else "unreviewed"
    for stale in set(status["rules"]) - set(rules):
        del status["rules"][stale]


def lint_bad_ids(records: list[dict]) -> set[str]:
    """Ids with any error finding, allowlisted or not (an allowlisted error is still a known error)."""
    import lint_korean
    rows = [{"id": r["id"], "level": str(r["context"]["level"]), "source": r["source"]["kind"],
             "ko": r["ko"], "goal": r["context"].get("goalKey", "")} for r in records]
    rules = lint_korean.load_rules()
    return {f["id"] for f in lint_korean.lint_rows(rows, rules) if f["severity"] == "error"}


def sync(records: list[dict], status: dict, reviewers: dict | None, bad: set[str]) -> dict:
    trusted = trusted_logins(reviewers)
    ids = {r["id"] for r in records}
    for record in records:
        sync_line(status, record, record["id"] in bad, trusted)
    for gone in set(status["lines"]) - ids:
        del status["lines"][gone]
    sync_rules(status, records, trusted)
    return status


def summary(status: dict) -> dict[str, int]:
    counts: dict[str, int] = {}
    for entry in status["lines"].values():
        counts[entry["status"]] = counts.get(entry["status"], 0) + 1
    return counts


def check(records: list[dict], status: dict) -> list[str]:
    problems = []
    ids = {r["id"] for r in records}
    for record in records:
        entry = status["lines"].get(record["id"])
        if entry is None:
            problems.append(f"{record['id']}: missing from review-status.json")
        elif entry["textHash"] != record["textHash"]:
            problems.append(f"{record['id']}: text changed, status needs a sync")
    for key in status["lines"]:
        if key not in ids:
            problems.append(f"{key}: in review-status.json but not in lines.jsonl")
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["sync", "check"])
    parser.add_argument("--lines", default=str(LINES_PATH))
    parser.add_argument("--status", default=str(STATUS_PATH))
    parser.add_argument("--reviewers", default=str(REVIEWERS_PATH))
    args = parser.parse_args(argv)
    records = read_lines(Path(args.lines))
    status = read_status(Path(args.status))
    if args.command == "check":
        problems = check(records, status)
        for p in problems[:20]:
            print(p)
        print(f"{len(problems)} problem(s)")
        return 1 if problems else 0
    sync(records, status, read_json(Path(args.reviewers), {}), lint_bad_ids(records))
    write_status(status, Path(args.status))
    print(f"{len(status['lines'])} lines, {len(status['rules'])} rules: {summary(status)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
