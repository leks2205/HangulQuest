#!/usr/bin/env python3
"""Build the bundled names.json fallback from the downloaded 26.3 language assets.

Runtime prefers the client ResourceManager tables; this file keeps item/entity
names available when the asset pack is incomplete.
"""

from __future__ import annotations

import json
import os
import re
import sys

ASSETS = os.path.expanduser("~/.minecraft/assets")
OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src/main/resources/assets/hangulquest/data/names.json",
)

HANGUL = re.compile(r"[\uac00-\ud7a3\u1100-\u11ff\u3130-\u318f]")


def objects(index_dir: str) -> dict:
    indexes = sorted(
        (os.path.join(index_dir, name) for name in os.listdir(index_dir) if name.endswith(".json")),
        key=os.path.getmtime,
        reverse=True,
    )
    with open(indexes[0], encoding="utf-8") as handle:
        return json.load(handle)["objects"]


def read_lang(table: dict, key: str) -> dict:
    entry = table[key]
    path = os.path.join(ASSETS, "objects", entry["hash"][:2], entry["hash"])
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def main() -> int:
    table = objects(os.path.join(ASSETS, "indexes"))
    try:
        korean = read_lang(table, "minecraft/lang/ko_kr.json")
        german = read_lang(table, "minecraft/lang/de_de.json")
    except (KeyError, OSError) as failed:
        print(f"language assets unavailable: {failed}", file=sys.stderr)
        return 1

    names = {}
    for key, ko in korean.items():
        de = german.get(key)
        if not isinstance(ko, str) or not isinstance(de, str) or not ko or not de:
            continue
        if not HANGUL.search(ko):
            continue
        # Hangul-only display rule: skip names with Latin letters or digits.
        if re.search(r"[A-Za-z0-9]", ko):
            continue
        if len(ko) > 40 or len(de) > 60:
            continue
        names[key] = [ko, de]

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(names, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")
    print(f"wrote {OUT}: {len(names)} names, {os.path.getsize(OUT) / 1024:.0f} KiB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
