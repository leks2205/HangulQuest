#!/usr/bin/env python3
"""Mass-change guard for pull requests (roadmap WP3.5, "no mass-applied Korean").

Fails a PR that changes the Korean text of more than MAX_TRAILS trail files or of more than MAX_STRINGS
Korean strings. Text that is byte-identical before and after (only en/de or the structure moved) does not
count: the guard compares the set of Korean strings of each file in the base and in the head.

It passes anyway when
  - the PR has the label `mass-change-approved` (--labels or $PR_LABELS: a JSON list or a comma list), or
  - the PR body or a commit message references an ADR that exists in docs/decisions/
    (`docs/decisions/0007-title.md` or `ADR-0007`).

  python3 tools/content_guard.py --base origin/main          the CI call, on pull requests only
  python3 tools/content_guard.py --self-test
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = "src/main/resources/assets/hangulquest/data/"
TRAIL_DIR = DATA_DIR + "questlines/"
DECISIONS_DIR = "docs/decisions/"
LABEL = "mass-change-approved"
MAX_TRAILS = 15
MAX_STRINGS = 300

HANGUL = re.compile("[ᄀ-ᇿ㄰-㆏가-힣]")
ADR_PATH = re.compile(r"docs/decisions/([\w.\-]+\.md)")
ADR_NUMBER = re.compile(r"\bADR[- ]?(\d+)\b", re.IGNORECASE)


# ----------------------------------------------------------------------------------------- strings

def korean_strings(text: str | None) -> set[str]:
    """Every JSON string value (not key) with Hangul in it. A file that is not JSON has none."""
    if not text:
        return set()
    try:
        data = json.loads(text)
    except ValueError:
        return set()
    found: set[str] = set()

    def walk(node) -> None:
        if isinstance(node, str):
            if HANGUL.search(node):
                found.add(node)
        elif isinstance(node, list):
            for item in node:
                walk(item)
        elif isinstance(node, dict):
            for value in node.values():
                walk(value)

    walk(data)
    return found


@dataclass
class Change:
    path: str
    added: int      # Korean strings in the head that the base did not have
    removed: int    # Korean strings of the base that the head no longer has

    @property
    def strings(self) -> int:
        """An edited string is one removed and one added: it counts once."""
        return max(self.added, self.removed)


@dataclass
class Verdict:
    ok: bool
    reason: str
    trails: list[Change] = field(default_factory=list)
    strings: int = 0
    lines: list[str] = field(default_factory=list)


def judge(changes: list[Change], labels: set[str], text: str, decisions: set[str],
          max_trails: int = MAX_TRAILS, max_strings: int = MAX_STRINGS) -> Verdict:
    """Pure decision. `text` is the PR body and the commit messages, `decisions` the file names in docs/decisions."""
    touched = [c for c in changes if c.added or c.removed]
    trails = [c for c in touched if c.path.startswith(TRAIL_DIR)]
    strings = sum(c.strings for c in touched)
    verdict = Verdict(True, "", trails, strings)
    verdict.lines.append(f"Korean text changed in {len(trails)} trail files (limit {max_trails}) "
                         f"and {strings} Korean strings (limit {max_strings}).")
    if len(trails) <= max_trails and strings <= max_strings:
        verdict.reason = "within the limits"
        return verdict
    if LABEL in labels:
        verdict.reason = f"approved by the label {LABEL}"
        return verdict
    adr = referenced_decision(text, decisions)
    if adr:
        verdict.reason = f"approved by {DECISIONS_DIR}{adr}"
        return verdict
    verdict.ok = False
    verdict.reason = (f"mass change without approval: add the label `{LABEL}`, or reference an ADR from "
                      f"{DECISIONS_DIR} in the PR body or a commit message")
    worst = sorted(trails, key=lambda c: -c.strings)[:5]
    for change in worst:
        verdict.lines.append(f"  {change.path}: +{change.added} -{change.removed}")
    verdict.lines.append("Rewrites go in batches of at most 15 trails; a change that only moves en/de text or the "
                         "structure does not count, because the Korean stays byte-identical.")
    return verdict


def referenced_decision(text: str, decisions: set[str]) -> str | None:
    for name in ADR_PATH.findall(text or ""):
        if name in decisions:
            return name
    for number in ADR_NUMBER.findall(text or ""):
        for name in sorted(decisions):
            stem = re.match(r"(\d+)", name)
            if stem and int(stem.group(1)) == int(number):
                return name
    return None


# ------------------------------------------------------------------------------------------- git

def git(*args: str, cwd: Path = ROOT, check: bool = True) -> str:
    result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8")
    if check and result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def show(ref: str, path: str, cwd: Path) -> str | None:
    result = subprocess.run(["git", "show", f"{ref}:{path}"], cwd=cwd, capture_output=True, text=True, encoding="utf-8")
    return result.stdout if result.returncode == 0 else None


def collect(base: str, head: str, cwd: Path = ROOT) -> list[Change]:
    """Compares the Korean strings of every changed data file between the merge base and the head."""
    merge_base = git("merge-base", base, head, cwd=cwd).strip()
    listing = git("diff", "--name-status", "-M", "--no-color", f"{merge_base}", head, "--", DATA_DIR, cwd=cwd)
    changes: list[Change] = []
    for line in listing.splitlines():
        parts = line.split("\t")
        status = parts[0]
        old, new = (parts[1], parts[2]) if status.startswith("R") else (parts[1], parts[1])
        if status.startswith("D"):
            new = None
        if not (new or old).endswith(".json"):
            continue
        before = korean_strings(show(merge_base, old, cwd)) if not status.startswith("A") else set()
        after = korean_strings(show(head, new, cwd)) if new else set()
        changes.append(Change(new or old, len(after - before), len(before - after)))
    return changes


def decisions_in(head: str, cwd: Path = ROOT) -> set[str]:
    listing = git("ls-tree", "--name-only", head, DECISIONS_DIR, cwd=cwd, check=False)
    return {Path(p).name for p in listing.splitlines() if p.endswith(".md")}


def parse_labels(raw: str | None) -> set[str]:
    if not raw:
        return set()
    raw = raw.strip()
    if raw.startswith("["):
        try:
            return {str(x) for x in json.loads(raw)}
        except ValueError:
            pass
    return {part.strip() for part in raw.split(",") if part.strip()}


# ----------------------------------------------------------------------------------------- self-test

def self_test() -> int:
    failures: list[str] = []

    def expect(name: str, condition: bool) -> None:
        if not condition:
            failures.append(name)

    one = '{"steps":[{"clue":{"ko":"돌을 캐요.","de":"Stein abbauen.","en":"Mine stone."}}],"한국어 키":"x"}'
    expect("strings are values, not keys", korean_strings(one) == {"돌을 캐요."})
    expect("not json", korean_strings("돌 {") == set())
    expect("en/de only", korean_strings(one.replace("Stein", "Fels")) == korean_strings(one))

    def change(n: int, trail: bool = True) -> Change:
        return Change((TRAIL_DIR if trail else DATA_DIR) + f"t{n}.json", 1, 1)

    sixteen = [change(i) for i in range(16)]
    fifteen = sixteen[:15]
    expect("15 trail files pass", judge(fifteen, set(), "", set()).ok)
    expect("16 trail files fail", not judge(sixteen, set(), "", set()).ok)
    expect("label approves", judge(sixteen, {LABEL}, "", set()).ok)
    expect("another label does not", not judge(sixteen, {"content"}, "", set()).ok)
    expect("ADR path approves", judge(sixteen, set(), "see docs/decisions/0007-rewrite.md", {"0007-rewrite.md"}).ok)
    expect("ADR number approves", judge(sixteen, set(), "per ADR-0007", {"0007-rewrite.md"}).ok)
    expect("ADR that does not exist does not", not judge(sixteen, set(), "see docs/decisions/0009-x.md", {"0007-rewrite.md"}).ok)
    expect("300 strings pass", judge([Change(DATA_DIR + "concepts.json", 300, 0)], set(), "", set()).ok)
    expect("301 strings fail", not judge([Change(DATA_DIR + "concepts.json", 301, 0)], set(), "", set()).ok)
    expect("an edit counts once", judge([Change(DATA_DIR + "concepts.json", 300, 300)], set(), "", set()).ok)
    expect("untouched Korean is no change", judge([Change(TRAIL_DIR + f"t{i}.json", 0, 0) for i in range(40)],
                                                  set(), "", set()).ok)
    expect("labels as JSON", parse_labels('["a","mass-change-approved"]') == {"a", LABEL})
    expect("labels as list", parse_labels("a, mass-change-approved") == {"a", LABEL})
    if failures:
        print("content_guard self-test failed: " + ", ".join(failures))
        return 1
    print("content_guard self-test passed.")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--base", help="the base ref (default: origin/$GITHUB_BASE_REF)")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--labels", default=os.environ.get("PR_LABELS"), help="JSON list or comma list (default $PR_LABELS)")
    parser.add_argument("--body", default=None, help="the PR body (default $PR_BODY)")
    parser.add_argument("--repo", type=Path, default=ROOT, help="the repository to look at")
    parser.add_argument("--max-trails", type=int, default=MAX_TRAILS)
    parser.add_argument("--max-strings", type=int, default=MAX_STRINGS)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()

    base = args.base or (f"origin/{os.environ['GITHUB_BASE_REF']}" if os.environ.get("GITHUB_BASE_REF") else None)
    if not base:
        print("no base ref: pass --base or run on a pull request (GITHUB_BASE_REF)")
        return 2
    try:
        changes = collect(base, args.head, args.repo)
        merge_base = git("merge-base", base, args.head, cwd=args.repo).strip()
        messages = git("log", "--format=%B", f"{merge_base}..{args.head}", cwd=args.repo)
        decisions = decisions_in(args.head, args.repo)
    except RuntimeError as problem:
        print(problem)
        return 2
    body = args.body if args.body is not None else os.environ.get("PR_BODY", "")
    verdict = judge(changes, parse_labels(args.labels), f"{body}\n{messages}", decisions,
                    args.max_trails, args.max_strings)
    print("\n".join(verdict.lines))
    print(("content guard: ok, " if verdict.ok else "content guard FAILED: ") + verdict.reason)
    return 0 if verdict.ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
