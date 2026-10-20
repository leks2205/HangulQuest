#!/usr/bin/env bash
# Build the guide locally: screenshots in place, tree report up to date, mdBook output in docs/book/book.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

rm -rf docs/book/src/screenshots
cp -r docs/screenshots docs/book/src/screenshots

python3 tools/quest_tree.py

if ! command -v mdbook >/dev/null 2>&1; then
  echo "mdbook is missing: cargo install mdbook --version 0.5.4" >&2
  exit 1
fi

mdbook build docs/book
echo "open docs/book/book/index.html"
