#!/usr/bin/env python3
"""Checks that the questline index matches the questline files, and that the jar ships them all.

The old CI check was `at least 414 questline files in the jar`, a number that had to be edited by
hand and let a half-empty jar through. Now the files on disk are the source of truth:

  * every `questlines/*.json` (except `index.json` and `_*.json` templates) is listed in the source
    `index.json`, and nothing else is;
  * with --jar, the index inside the built jar lists the same files and the jar contains each one.

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


def source_files() -> list[str]:
    return sorted(p.name for p in SOURCE.glob("*.json") if p.name != "index.json" and not p.name.startswith("_"))


def compare(label: str, expected: list[str], actual: list[str]) -> list[str]:
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    problems = []
    if missing:
        problems.append(f"{label}: missing {len(missing)} file(s): {', '.join(missing[:8])}{' ...' if len(missing) > 8 else ''}")
    if extra:
        problems.append(f"{label}: {len(extra)} entr(ies) without a source file: {', '.join(extra[:8])}{' ...' if len(extra) > 8 else ''}")
    if len(actual) != len(set(actual)):
        problems.append(f"{label}: duplicate entries")
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", help="also check the built jar")
    args = parser.parse_args(argv)

    files = source_files()
    problems = compare("source index.json", files, json.loads((SOURCE / "index.json").read_text(encoding="utf-8")))
    print(f"{len(files)} questline files on disk")

    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            names = jar.namelist()
            jar_index = json.loads(jar.read(IN_JAR + "index.json").decode("utf-8"))
            in_jar = sorted(n[len(IN_JAR):] for n in names
                            if n.startswith(IN_JAR) and n.endswith(".json") and "/" not in n[len(IN_JAR):]
                            and n != IN_JAR + "index.json" and not n[len(IN_JAR):].startswith("_"))
        problems += compare("jar index.json", files, jar_index)
        problems += compare("jar contents", files, in_jar)
        print(f"{len(in_jar)} questline files in the jar, {len(jar_index)} in its index")

    for problem in problems:
        print("FAIL " + problem)
    if not problems:
        print("questline index matches the files")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
