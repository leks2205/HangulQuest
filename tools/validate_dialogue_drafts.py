#!/usr/bin/env python3
"""Validate a folder of drafted villager conversations and requests before they are copied into the mod.

    python3 tools/validate_dialogue_drafts.py <folder>

The folder holds dialogue/*.json and requests/*.json (any *.json: a file with "nodes" is a conversation, one
with "lines" a request), lang/en_us.json and lang/de_de.json with the glosses, and optionally _lexicon.json with
new words. Two passes, without starting the game:

1. the Java checks (Gradle task validateDrafts): what the game and the content tests check, plus the naming
   scheme of content/briefs/VILLAGERS.md: graph, speech level per personality, kinds, honesty of requests,
   glosses in English and German, plain Hangul, vocabulary coverage (at most one new word per line, every
   word in the catalog or the dialogue lexicon), grammar within the conversation's level, no names;
2. the Korean linter (tools/lint_korean.py, rules in tools/lint/rules.json) on every line.

Every problem is printed as "<file>: <node>: <message>". Exit status 0 when the drafts are clean, 1 when
anything is wrong, 2 when the check itself could not run.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TSV = ROOT / "build" / "reports" / "drafts" / "sentences.tsv"
sys.path.insert(0, str(ROOT / "tools"))


def lint(tsv: Path) -> tuple[list[str], int]:
    """Korean linter findings as "<file>: <node>: [rule] message" lines, and how many are errors."""
    import lint_korean  # noqa: E402  (tools/lint_korean.py)

    rows = lint_korean.read_rows(tsv)
    findings = lint_korean.lint_rows(rows, lint_korean.load_rules(), lexicon_rows=lint_korean.concept_rows())
    lines = []
    errors = 0
    for finding in findings:
        source = finding.get("source", "")
        file = source.partition(":")[2].partition("#")[0] or "?"
        node = finding["id"].split(".", 2)[-1]
        severity = finding["severity"]
        errors += severity == "error"
        lines.append(f"{file}: {node}: [{finding['rule']} {finding['name']}, {severity}] {finding['message']} "
                     f"in \"{finding['ko']}\" (fix: {finding['fix']})")
    return lines, errors


def main(argv: list[str]) -> int:
    if len(argv) != 1 or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 2
    folder = Path(argv[0]).resolve()
    if not folder.is_dir():
        print(f"{folder} is not a folder", file=sys.stderr)
        return 2
    if TSV.exists():
        TSV.unlink()
    java = subprocess.run(["./gradlew", "-q", "--offline", "validateDrafts", f"-Pdrafts={folder}"], cwd=ROOT,
                          capture_output=True, text=True)
    report = [line for line in java.stdout.splitlines() if line.strip()]
    for line in report:
        print(line)
    if not TSV.exists():
        print(java.stderr[-3000:], file=sys.stderr)
        print("the Java checks did not run (see above)", file=sys.stderr)
        return 2
    lint_lines, lint_errors = lint(TSV)
    for line in lint_lines:
        print(line)
    java_ok = java.returncode == 0
    print(f"Korean linter: {len(lint_lines)} findings, {lint_errors} errors")
    ok = java_ok and lint_errors == 0
    print("DRAFTS OK" if ok else "DRAFTS HAVE PROBLEMS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
