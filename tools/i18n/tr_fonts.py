"""Which characters of a language file the game's default font cannot draw.

The mod's own font (`hangul.ttf`, a Noto Sans CJK KR subset) only carries Hangul, ASCII and punctuation
and is used for Korean text alone. Everything else is drawn with Minecraft's default font, whose last
provider is Unifont (`minecraft/font/unifont.zip`, a hex file with one line `XXXX:bitmap` per character).
Unifont covers the whole Basic Multilingual Plane, so Vietnamese, Thai, Japanese and Chinese render
(in Unifont's pixel style, not Noto's). This module checks that against a local asset cache; the cache
is Mojang's, so nothing from it is stored in the repository.
"""

from __future__ import annotations

import json
import zipfile
from collections import Counter
from pathlib import Path


def unifont_coverage(roots: list[Path], version: str = "26.3") -> set[int] | None:
    """Code points Unifont has a glyph for, or None when no asset cache has the file."""
    for root in roots:
        indexes = Path(root) / "indexes"
        if not indexes.is_dir():
            continue
        files = sorted(indexes.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
        files = [p for p in files if p.name.startswith(version + "-")] or files
        for index in files:
            entry = json.loads(index.read_text(encoding="utf-8"))["objects"].get("minecraft/font/unifont.zip")
            if not entry:
                continue
            path = Path(root) / "objects" / entry["hash"][:2] / entry["hash"]
            if path.is_file():
                return hex_coverage(path)
    return None


def hex_coverage(archive: Path) -> set[int]:
    covered: set[int] = set()
    with zipfile.ZipFile(archive) as zipped:
        for name in zipped.namelist():
            if name.endswith(".hex"):
                for line in zipped.read(name).decode("utf-8").splitlines():
                    head = line.split(":", 1)[0]
                    if head:
                        covered.add(int(head, 16))
    return covered


def missing_characters(texts: list[str], covered: set[int]) -> Counter:
    """Characters of the texts that no glyph covers (spaces and line breaks are not drawn)."""
    missing: Counter = Counter()
    for text in texts:
        for char in text:
            if char not in " \n\t" and ord(char) not in covered:
                missing[char] += 1
    return missing
