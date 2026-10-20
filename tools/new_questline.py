#!/usr/bin/env python3
# MANDATORY: read docs/questline-attributes.md first; every non-optional attribute applies.
"""Scaffold a new questline JSON from curated concepts.

Usage:
  python3 tools/new_questline.py <id> --words stone,dirt --tier 1 \
      --title-ko "나의 길" --title-de "Mein Pfad"

Writes assets/hangulquest/data/questlines/<id>.json and refreshes index.json.
"""

from __future__ import annotations

import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src/main/resources/assets/hangulquest/data")
CONCEPTS = os.path.join(DATA, "concepts.json")
TRAILS = os.path.join(DATA, "questlines")


def load_concepts() -> dict:
    with open(CONCEPTS, encoding="utf-8") as handle:
        return {entry["id"]: entry for entry in json.load(handle)}


def goal_for(concept: dict) -> dict:
    category = concept.get("category", "MISC")
    if concept.get("entity"):
        return {"type": "LOOK_AT_ENTITY", "entity": concept["entity"], "dwellMs": 800}
    if concept.get("biome"):
        return {"type": "REACH_BIOME", "biome": concept["biome"], "dwellMs": 1000}
    item = (concept.get("items") or [None])[0]
    if not item:
        return {"type": "LOOK_AT_BLOCK", "block": "minecraft:stone", "dwellMs": 600}
    if category == "FOOD":
        return {"type": "CONSUME_ITEM", "item": item, "count": 1}
    if category == "BLOCKS":
        return {"type": "BREAK_BLOCK", "block": item, "count": 2}
    return {"type": "ACQUIRE_ITEM", "item": item, "count": 1}


def refresh_index() -> None:
    names = sorted(
        name
        for name in os.listdir(TRAILS)
        if name.endswith(".json") and not name.startswith("_") and name != "index.json"
    )
    with open(os.path.join(TRAILS, "index.json"), "w", encoding="utf-8") as handle:
        json.dump(names, handle, ensure_ascii=False, indent=1)
        handle.write("\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("id")
    parser.add_argument("--words", required=True, help="comma separated concept ids")
    parser.add_argument("--tier", type=int, default=1)
    parser.add_argument("--title-ko", required=True)
    parser.add_argument("--title-de", required=True)
    parser.add_argument("--min-known", type=int, default=0)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    concepts = load_concepts()
    ids = [part.strip() for part in args.words.split(",") if part.strip()]
    missing = [concept for concept in ids if concept not in concepts]
    if missing:
        print(f"unknown concepts: {', '.join(missing)}", file=sys.stderr)
        return 1

    steps = []
    for index, concept_id in enumerate(ids, start=1):
        concept = concepts[concept_id]
        steps.append(
            {
                "id": f"s{index}",
                "teach": [concept_id],
                "goal": goal_for(concept),
                "conditions": [],
            }
        )
    trail = {
        "schema": 1,
        "id": args.id,
        "titleKo": args.title_ko,
        "titleDe": args.title_de,
        "tier": args.tier,
        "concepts": ids,
        "requires": {"minWordsKnown": args.min_known, "dimension": "minecraft:overworld"},
        "steps": steps,
        "finale": {
            "step": {
                "id": "finale",
                "teach": [],
                "goal": {"type": "PLACE_BLOCK", "block": "minecraft:torch", "count": 1},
                "conditions": [{"type": "TIME", "phase": "NIGHT"}],
            },
            "textKo": "밤에 빛을 놓았어요. 길이 끝났어요!",
            "textDe": "Ein Licht in der Nacht: Der Pfad ist zu Ende!",
        },
        "reward": {"badge": args.id, "words": ids},
    }

    os.makedirs(TRAILS, exist_ok=True)
    out = os.path.join(TRAILS, f"{args.id}.json")
    if os.path.exists(out) and not args.overwrite:
        print(f"{out} exists (use --overwrite)", file=sys.stderr)
        return 1
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(trail, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    refresh_index()
    print(f"wrote {out}")
    print("next: add German clue texts if you want riddles, then ./gradlew test")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
