#!/usr/bin/env python3
"""Print the CHANGELOG section for a version, for the text of a GitHub release.

    python3 tools/changelog_section.py 3.0.0-rc.2      # section "## [3.0.0-rc.2]", else the one of "3.0.0"

A release candidate has no section of its own while the next release is being written: it falls back to the
section of the version it leads up to. Exit code 1 (and a message on stderr) when neither exists, so a release
can never go out with empty notes.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

CHANGELOG = Path(__file__).resolve().parents[1] / "CHANGELOG.md"


def section(text: str, version: str) -> str | None:
    lines = text.splitlines()
    head = re.compile(r"^## \[" + re.escape(version) + r"\]")
    for start, line in enumerate(lines):
        if head.match(line):
            end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("## [")), len(lines))
            body = [l for l in lines[start + 1:end] if not re.match(r"^\[[^\]]+\]: https?://", l)]
            return "\n".join(body).strip() + "\n"
    return None


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    version = argv[1].removeprefix("v")
    text = CHANGELOG.read_text(encoding="utf-8")
    found = section(text, version)
    if found is None and "-" in version:
        found = section(text, version.split("-", 1)[0])
    if not found or not found.strip():
        print(f"CHANGELOG.md has no section for {version}", file=sys.stderr)
        return 1
    sys.stdout.write(found)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
