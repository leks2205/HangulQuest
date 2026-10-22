#!/bin/bash
# Regenerate the derived content files and run every content gate (what CI runs for content); stop at the first
# failure. Usage: tools/gates.sh [rebaseline]. Run it unpiped and commit only when it ends with ALL GATES GREEN.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"   # the checkout or worktree it is run from
mode=${1:-check}   # "rebaseline" also rewrites content/metrics-baseline.json
./gradlew test --offline -q --tests '*SentenceDumpTest' --tests '*StepsDumpTest' --tests '*QuestDepthDumpTest' --tests '*VerbFormsDumpTest' > build/gates-dump.log 2>&1 || { tail -30 build/gates-dump.log; exit 1; }
python3 tools/review/export_lines.py > /dev/null
python3 tools/review/status.py sync > /dev/null
python3 tools/quest_tree.py > /dev/null
python3 tools/assign_chapters.py > /dev/null
python3 tools/lint_korean.py --self-test > /dev/null
python3 tools/lint_korean.py build/reports/korean/sentences.tsv --fail-on error | tail -1
[ "$mode" = rebaseline ] && python3 tools/variety.py --update-baseline
python3 tools/variety.py --check | tail -1
python3 tools/coverage.py --check | tail -1
./gradlew test --offline -q > build/gates-test.log 2>&1 || { grep -h -o 'AssertionFailedError: [^"]*' build/test-results/test/*.xml | head -10; exit 1; }
python3 tools/review/export_lines.py --check | tail -1
python3 tools/review/status.py check | tail -1
python3 tools/i18n/extract_content.py --check
python3 tools/quest_tree.py --check
python3 tools/assign_chapters.py --check
python3 tools/validate_schema.py | tail -1
python3 tools/quest_tree.py --check-depths build/reports/quest-depths.json | tail -1
python3 tools/i18n/translate.py check | tail -1
python3 -m unittest discover -s tools/i18n/tests -q 2>&1 | tail -1
echo "ALL GATES GREEN"
