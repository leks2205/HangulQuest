#!/usr/bin/env bash
# Write the progress fixture a release tag needs (release.yml refuses a tag without one) and list it in
# ProgressCompatibilityTest. Usage: tools/new_fixture.sh v3.0.0-rc.2
# The file is the synthetic v5 sample loaded and saved by the real ProgressStore, so it is what this build writes.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
tag=${1:?usage: tools/new_fixture.sh <tag, e.g. v3.0.0-rc.2>}
case "$tag" in v[0-9]*) ;; *) echo "tag must look like v3.0.0-rc.2" >&2; exit 1 ;; esac
file="src/test/resources/progress-fixtures/$tag.json"
[ ! -e "$file" ] || { echo "$file exists; released fixtures are never rewritten" >&2; exit 1; }
HANGULQUEST_WRITE_FIXTURE="$tag" ./gradlew test --offline -q --tests '*WriteFixtureTest'
[ -f "$file" ] || { echo "no fixture written" >&2; exit 1; }
test="src/test/java/io/github/leks2205/hangulquest/learn/ProgressCompatibilityTest.java"
grep -q "\"$tag\"" "$test" || sed -i "0,/\"v4-autotrack\"/s//\"v4-autotrack\", \"$tag\"/" "$test"
echo "wrote $file and listed it in ProgressCompatibilityTest; commit both"
