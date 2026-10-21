#!/usr/bin/env python3
"""Checks the questline files, and that the jar ships them all.

The game finds questlines by listing the resource folder, so there is no index file any more.
The files on disk are the source of truth:

  * every `questlines/*.json` (except `index.json` and `_*.json` templates) parses and has an
    "id" that matches nothing else (no two files share a trail id);
  * with --jar, the jar contains exactly those files.

The script keeps its old name because CI calls it.

    python3 tools/check_questline_index.py [--jar build/libs/hangulquest-X.jar]
"""
from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src" / "main" / "resources" / "assets" / "hangulquest" / "data" / "questlines"
IN_JAR = "assets/hangulquest/data/questlines/"


def is_trail(name: str) -> bool:
    return name.endswith(".json") and name != "index.json" and not name.startswith("_")


def source_files() -> list[str]:
    return sorted(p.name for p in SOURCE.glob("*.json") if is_trail(p.name))


def compare(label: str, expected: list[str], actual: list[str]) -> list[str]:
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    problems = []
    if missing:
        problems.append(f"{label}: missing {len(missing)} file(s): {', '.join(missing[:8])}{' ...' if len(missing) > 8 else ''}")
    if extra:
        problems.append(f"{label}: {len(extra)} file(s) without a source file: {', '.join(extra[:8])}{' ...' if len(extra) > 8 else ''}")
    if len(actual) != len(set(actual)):
        problems.append(f"{label}: duplicate entries")
    return problems


def check_ids(files: list[str]) -> list[str]:
    problems = []
    seen: dict[str, str] = {}
    for name in files:
        try:
            trail_id = json.loads((SOURCE / name).read_text(encoding="utf-8")).get("id")
        except (ValueError, AttributeError) as broken:
            problems.append(f"{name}: not a questline object ({broken})")
            continue
        if not isinstance(trail_id, str) or not trail_id.strip():
            problems.append(f"{name}: no \"id\"")
        elif trail_id in seen:
            problems.append(f"{name}: id \"{trail_id}\" is also used by {seen[trail_id]}")
        else:
            seen[trail_id] = name
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", help="also check the built jar")
    args = parser.parse_args(argv)

    files = source_files()
    problems = check_ids(files)
    print(f"{len(files)} questline files on disk")

    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            in_jar = sorted(n[len(IN_JAR):] for n in jar.namelist()
                            if n.startswith(IN_JAR) and "/" not in n[len(IN_JAR):] and is_trail(n[len(IN_JAR):]))
        problems += compare("jar contents", files, in_jar)
        print(f"{len(in_jar)} questline files in the jar")

    for problem in problems:
        print("FAIL " + problem)
    if not problems:
        print("questline files are consistent")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
