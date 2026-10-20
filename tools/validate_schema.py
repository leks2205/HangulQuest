#!/usr/bin/env python3
"""Validate every shipped questline against docs/questline.schema.json.

The Java tests already check the content semantically (tree links, levels, Hangul-only text,
conditions spoken in the sentence). This script is the second gate: it checks the *shape* of every
file against the published JSON schema, so the schema cannot drift away from the content.

Usage:
    pip install jsonschema
    python3 tools/validate_schema.py [--schema docs/questline.schema.json] [DIR ...]

Exit code 0 means every file validated.
"""

from __future__ import annotations

import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIR = os.path.join(ROOT, "src/main/resources/assets/hangulquest/data/questlines")
DEFAULT_SCHEMA = os.path.join(ROOT, "docs/questline.schema.json")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dirs", nargs="*", default=[DEFAULT_DIR])
    parser.add_argument("--schema", default=DEFAULT_SCHEMA)
    args = parser.parse_args()

    try:
        import jsonschema
    except ImportError:
        print("jsonschema is missing: pip install jsonschema", file=sys.stderr)
        return 2

    with open(args.schema, encoding="utf-8") as handle:
        schema = json.load(handle)
    validator = jsonschema.Draft7Validator(schema)

    files: list[str] = []
    for directory in args.dirs:
        if not os.path.isdir(directory):
            print(f"not a directory: {directory}", file=sys.stderr)
            return 2
        files.extend(
            os.path.join(directory, name)
            for name in sorted(os.listdir(directory))
            if name.endswith(".json") and not name.startswith("_") and name != "index.json"
        )

    failures = 0
    for path in files:
        try:
            with open(path, encoding="utf-8") as handle:
                data = json.load(handle)
        except json.JSONDecodeError as error:
            failures += 1
            print(f"{os.path.basename(path)}: invalid JSON: {error}")
            continue
        errors = sorted(validator.iter_errors(data), key=lambda error: list(error.path))
        if errors:
            failures += 1
            for error in errors:
                where = "/".join(str(part) for part in error.path) or "(root)"
                print(f"{os.path.basename(path)}: {where}: {error.message}")

    print(f"{len(files) - failures}/{len(files)} questlines match {os.path.relpath(args.schema, ROOT)}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
