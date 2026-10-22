#!/usr/bin/env python3
"""Generate the credits of the Korean reviewers.

    python3 tools/review/credits.py            # docs/book/src/credits.md and the in-game credits.json
    python3 tools/review/credits.py --check    # fail when either file is out of date

Sources: content/korean/reviewers.json (accepted corrections, display name, opt-out) and the approvals
in content/korean/review-status.json. A reviewer who wrote "anonymous" in the form only appears in the
anonymous total.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import DATA_DIR, REVIEWERS_PATH, ROOT, STATUS_PATH, read_json, read_status  # noqa: E402

MD_PATH = ROOT / "docs" / "book" / "src" / "credits.md"
JSON_PATH = DATA_DIR / "credits.json"


def collect(reviewers: dict, status: dict) -> dict:
    people: dict[str, dict] = {}
    for login, info in reviewers.get("reviewers", {}).items():
        people[login] = {"login": login, "display": info.get("display", ""), "optOut": bool(info.get("optOut")),
                         "accepted": int(info.get("accepted", 0)), "approvals": 0, "trusted": bool(info.get("trusted"))}
    for entry in list(status.get("lines", {}).values()) + list(status.get("rules", {}).values()):
        for approval in entry.get("approvals", []):
            login = approval.get("login", "")
            if login:
                person = people.setdefault(login, {"login": login, "display": "", "optOut": False, "accepted": 0,
                                                  "approvals": 0, "trusted": False})
                person["approvals"] += 1
    named = []
    anonymous = 0
    for person in people.values():
        if person["optOut"]:
            anonymous += 1
        elif person["accepted"] or person["approvals"]:
            named.append(person)
    named.sort(key=lambda p: (-(p["accepted"] + p["approvals"]), -p["accepted"], (p["display"] or p["login"]).lower()))
    return {"schema": 1, "reviewers": [
        {"name": p["display"] or p["login"], "github": p["login"] if not p["display"] else "",
         "accepted": p["accepted"], "approvals": p["approvals"], "trusted": p["trusted"]} for p in named],
        "anonymous": anonymous}


def md_escape(text: str) -> str:
    return re.sub(r"([\\`*_{}\[\]<>()#+\-.!|~])", r"\\\1", text)


def render_md(data: dict) -> str:
    lines = ["# Credits", "",
             "Hangul Quest teaches Korean, and its Korean is only as good as the people who check it. "
             "Thank you to everyone who reviewed a line, suggested a correction or approved a page.", "",
             "한글 탐험의 한국어는 검토해 주신 분들 덕분에 좋아졌어요. 모두 고맙습니다.", ""]
    if data["reviewers"]:
        lines += ["| Reviewer | Accepted corrections | Approvals | |", "|---|---:|---:|---|"]
        for p in data["reviewers"]:
            name = f"[@{md_escape(p['github'])}](https://github.com/{p['github']})" if p["github"] else md_escape(p["name"])
            lines.append(f"| {name} | {p['accepted']} | {p['approvals']} | {'trusted reviewer' if p['trusted'] else ''} |")
        lines.append("")
    else:
        lines += ["No reviewer has been credited yet. Be the first: see [Help review Korean](help-review.md).", ""]
    if data["anonymous"]:
        lines += [f"{data['anonymous']} more reviewer(s) chose to stay anonymous.", ""]
    lines += ["Reviewers can pick the name shown here, or leave it out, in the last field of every review form.", ""]
    return "\n".join(lines)


def render_json(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, indent=1) + "\n"


def write_outputs(reviewers: dict, status: dict) -> list[Path]:
    """Regenerate both files; returns their paths (the daily batch commits them with the status)."""
    data = collect(reviewers, status)
    paths = []
    for path, text in ((MD_PATH, render_md(data)), (JSON_PATH, render_json(data))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        paths.append(path)
    return paths


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    data = collect(read_json(REVIEWERS_PATH, {}), read_status(STATUS_PATH))
    outputs = {MD_PATH: render_md(data), JSON_PATH: render_json(data)}
    if args.check:
        stale = [str(p.relative_to(ROOT)) for p, text in outputs.items()
                 if not p.is_file() or p.read_text(encoding="utf-8") != text]
        if stale:
            print("out of date, run python3 tools/review/credits.py:", ", ".join(stale), file=sys.stderr)
            return 1
        print("credits are up to date")
        return 0
    for path, text in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(f"{len(data['reviewers'])} credited, {data['anonymous']} anonymous")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
