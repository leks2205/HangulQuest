#!/usr/bin/env python3
# MANDATORY: read docs/questline-attributes.md first; every non-optional attribute applies.
"""Scaffold a new questline JSON that passes the content tests.

Usage:
  python3 tools/new_questline.py <id> --words stone,cobblestone,coal \
      --title-ko "나의 길" --title-de "Mein Pfad" --title-en "My Trail" \
      [--after steinpfad,erzpfad] [--topic 돌] [--finale torch-night]

What it writes (src/main/resources/assets/hangulquest/data/questlines/<id>.json):
  - one step per word, with a goal taken from the concept, English clue and hint, and no Korean clue:
    the Korean sentences are generated, so they follow the grammar tables;
  - a finale picked from tools/data/finales.json: a goal a player can always reach in a fresh world,
    one that no sibling trail (same parent) and no step of this trail already uses. The finale's
    Korean text there is the text SentenceBuilder.ceremony produces, which a test keeps in sync;
  - tier, koreanLevel, order and requires.completed from the tree position.

The trail is only a starting point: write German flavour text and check the result with
`./gradlew test` (QuestlineValidationTest, ObtainabilityTest and QuestScaffoldTest).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import quest_tree  # noqa: E402  (shares the depth and TOPIK band logic with the quest tree report)

DATA = os.path.join(ROOT, "src/main/resources/assets/hangulquest/data")
CONCEPTS = os.path.join(DATA, "concepts.json")
TRAILS = os.path.join(DATA, "questlines")
FINALES = os.path.join(ROOT, "tools/data/finales.json")

LATIN = re.compile(r"[A-Za-z]")
DRINKS = ("potion", "milk", "honey_bottle", "water")


def load_concepts() -> dict:
    with open(CONCEPTS, encoding="utf-8") as handle:
        return {entry["id"]: entry for entry in json.load(handle)}


def load_finales() -> list[dict]:
    with open(FINALES, encoding="utf-8") as handle:
        return json.load(handle)


def load_trails(*dirs: str) -> dict[str, dict]:
    trails: dict[str, dict] = {}
    for directory in dict.fromkeys(dirs):
        if not os.path.isdir(directory):
            continue
        for name in sorted(os.listdir(directory)):
            if not name.endswith(".json") or name.startswith("_") or name == "index.json":
                continue
            with open(os.path.join(directory, name), encoding="utf-8") as handle:
                trail = json.load(handle)
            trails[trail["id"]] = trail
    return trails


def english_name(concept: dict) -> str:
    name = concept.get("en") or concept["id"].replace("_", " ")
    return name[:1].upper() + name[1:]


def goal_for(concept: dict, index: int) -> dict:
    """A goal the concept can be checked with: its entity, biome or first item."""
    if concept.get("entity"):
        return {"type": "LOOK_AT_ENTITY", "entity": concept["entity"], "dwellMs": 800}
    if concept.get("biome"):
        return {"type": "REACH_BIOME", "biome": concept["biome"], "dwellMs": 1000}
    item = (concept.get("items") or [None])[0]
    if not item:
        raise ValueError(
            f"concept '{concept['id']}' has no item, entity or biome, so no goal can check it; choose another word"
        )
    category = concept.get("category", "MISC")
    if category == "FOOD":
        return {"type": "CONSUME_ITEM", "item": item, "count": 1}
    if category in ("BLOCKS", "BUILDING"):
        # Alternate, so a trail does not read as a list of "break" steps.
        if index % 2 == 0 and category == "BLOCKS":
            return {"type": "BREAK_BLOCK", "block": item, "count": 2}
        return {"type": "PLACE_BLOCK", "block": item, "count": 2}
    return {"type": "ACQUIRE_ITEM", "item": item, "count": 1}


def target_of(goal: dict) -> str:
    for key in ("block", "item", "entity", "biome", "dimension"):
        if key in goal:
            return goal[key]
    return ""


def goal_key(goal: dict) -> tuple[str, str]:
    """Type and target: what players notice as 'the same task', whatever the count."""
    return goal["type"], target_of(goal)


def english_clue(goal: dict, name: str, conditions: list[dict] | None = None) -> str:
    count = goal.get("count", 1)
    amount = f"{count}× {name}" if count > 1 else name
    kind = goal["type"]
    if kind == "BREAK_BLOCK":
        text = f"Break {amount}."
    elif kind == "PLACE_BLOCK":
        text = f"Place {amount}."
    elif kind == "ACQUIRE_ITEM":
        text = f"Collect {amount}."
    elif kind == "CONSUME_ITEM":
        verb = "Drink" if any(word in goal["item"] for word in DRINKS) else "Eat"
        text = f"{verb} {amount}."
    elif kind in ("LOOK_AT_ENTITY", "LOOK_AT_BLOCK"):
        text = f"Look at {name}."
    elif kind == "REACH_BIOME":
        text = f"Reach {name}."
    else:
        text = f"{name}."
    phases = {"DAY": "During the day.", "NIGHT": "At night.", "DAWN": "At dawn.", "DUSK": "At dusk."}
    for condition in conditions or []:
        if condition["type"] == "TIME":
            text += " " + phases[condition["phase"]]
    return text


def pick_finale(trail_id: str, trails: dict[str, dict], parents: list[str], own_goals: set,
                forced: str | None) -> tuple[dict, list[str]]:
    """Returns the finale entry and warnings. Siblings share a parent (roots are siblings of roots)."""
    finales = load_finales()
    by_id = {entry["id"]: entry for entry in finales}
    used_texts = {t["finale"].get("textKo") for t in trails.values()}
    if forced:
        if forced not in by_id:
            raise ValueError(f"unknown finale '{forced}'; known: {', '.join(sorted(by_id))}")
        chosen = by_id[forced]
        warnings = []
        if chosen["textKo"] in used_texts:
            warnings.append(f"another trail already ends with '{chosen['textKo']}'; "
                            "the content test wants every finale text to be unique")
        return chosen, warnings

    wanted = set(parents)
    siblings = [
        t for t in trails.values()
        if t["id"] != trail_id
        and (set(t["requires"].get("completed") or []) & wanted if wanted else not t["requires"].get("completed"))
    ]
    taken = set(own_goals)
    for sibling in siblings:
        taken.add(goal_key(sibling["finale"]["step"]["goal"]))
    candidates = [f for f in finales if goal_key(f["goal"]) not in taken]
    if not candidates:
        raise ValueError("every finale in tools/data/finales.json is taken by a sibling or a step; "
                         "add a new one or pass --finale to reuse one on purpose")
    fresh = [f for f in candidates if f["textKo"] not in used_texts]
    warnings = []
    pool = fresh
    if not fresh:
        pool = candidates
        warnings.append("every free finale text is already used by another trail; "
                        "edit finale.textKo, the content test wants each one to be unique")
    # Stable per id, so a re-run gives the same file, but different ids spread over the list.
    return pool[zlib.crc32(trail_id.encode("utf-8")) % len(pool)], warnings


def build(args: argparse.Namespace, concepts: dict, trails: dict[str, dict]) -> tuple[dict, list[str]]:
    ids = [part.strip() for part in args.words.split(",") if part.strip()]
    missing = [concept for concept in ids if concept not in concepts]
    if missing:
        raise ValueError(f"unknown concepts: {', '.join(missing)}")
    if len(set(ids)) != len(ids):
        raise ValueError("a word appears twice in --words")
    if len(ids) < 2:
        raise ValueError("a trail needs at least two words (two steps and the finale make three)")
    parents = [part.strip() for part in (args.after or "").split(",") if part.strip()]
    unknown = [parent for parent in parents if parent not in trails]
    if unknown:
        raise ValueError(f"unknown parent trails: {', '.join(unknown)}")
    if len(parents) > 3:
        raise ValueError("a trail has one to three parents")
    if args.id in trails and not args.overwrite:
        raise ValueError(f"a trail called '{args.id}' exists (use --overwrite)")
    if not re.fullmatch(r"[a-z0-9_]+", args.id):
        raise ValueError("the id is lowercase letters, digits and underscores only")
    if LATIN.search(args.title_ko):
        raise ValueError("--title-ko must be Korean only (Hangul, no Latin letters)")
    others = {k: v for k, v in trails.items() if k != args.id}
    if any(t["titleKo"] == args.title_ko for t in others.values()):
        raise ValueError(f"another trail is already called '{args.title_ko}'")

    steps = []
    goals = []
    for index, concept_id in enumerate(ids):
        concept = concepts[concept_id]
        goal = goal_for(concept, index)
        goals.append(goal)
        text = english_clue(goal, english_name(concept))
        steps.append(
            {
                "id": f"s{index + 1}",
                "teach": [concept_id],
                "goal": goal,
                "conditions": [],
                "clue": {"en": text},
                "hint": {"en": text},
            }
        )
    sequence = [json.dumps(goal, sort_keys=True) for goal in goals]
    for other in others.values():
        other_steps = [json.dumps(s["goal"], sort_keys=True) for s in other["steps"]]
        other_steps.append(json.dumps(other["finale"]["step"]["goal"], sort_keys=True))
        if other_steps[:-1] == sequence:
            raise ValueError(f"'{other['id']}' already has exactly these steps; change the words")

    finale, warnings = pick_finale(args.id, others, parents, {goal_key(goal) for goal in goals}, args.finale)
    finale_concept = concepts[finale["concept"]]
    finale_name = english_name(finale_concept)
    finale_conditions = finale["conditions"]
    finale_text = english_clue(finale["goal"], finale_name, finale_conditions)

    # Depth and level come from the tree, the same way the quest tree report computes them.
    scratch = dict(others)
    scratch[args.id] = {"requires": {"completed": parents}}
    depth = quest_tree.depth_of(args.id, scratch, {})
    all_concepts = ids + [finale["concept"]] if finale["concept"] not in ids else list(ids)
    requires: dict = {"minWordsKnown": args.min_known, "dimension": "minecraft:overworld"}
    if parents:
        requires["completed"] = parents
    order = args.order if args.order else max((t.get("order", 0) for t in others.values()), default=0) + 10
    first = concepts[ids[0]]
    trail = {
        "schema": 1,
        "id": args.id,
        "titleKo": args.title_ko,
        "titleDe": args.title_de,
        "tier": min(5, 1 + len(parents)),
        "concepts": all_concepts,
        "requires": requires,
        "steps": steps,
        "finale": {
            "step": {
                "id": "finale",
                "teach": [finale["concept"]],
                "goal": finale["goal"],
                "conditions": finale_conditions,
                "clue": {"en": finale_text},
                "hint": {"en": finale_text},
            },
            "textKo": finale["textKo"],
            "textDe": finale["textDe"],
            "textEn": finale["textEn"],
        },
        "reward": {"badge": args.id, "words": all_concepts},
        "order": order,
        "koreanLevel": quest_tree.band_for(depth),
        "topic": args.topic or first["ko"],
        "titleEn": args.title_en or f"The {english_name(first)} Trail",
    }
    return trail, warnings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("id")
    parser.add_argument("--words", required=True, help="comma separated concept ids, one step each (two or more)")
    parser.add_argument("--after", help="comma separated parent trail ids (1 to 3); leave out for a root trail")
    parser.add_argument("--title-ko", required=True)
    parser.add_argument("--title-de", required=True)
    parser.add_argument("--title-en", help="default: 'The <first word> Trail'")
    parser.add_argument("--topic", help="branch topic in Korean; default: the first word")
    parser.add_argument("--finale", help="id from tools/data/finales.json instead of an automatic pick")
    parser.add_argument("--min-known", type=int, default=0)
    parser.add_argument("--order", type=int, default=0, help="journal sort key; default: after every existing trail")
    parser.add_argument("--tier", type=int, help="ignored: the tier is 1 plus the parent count")
    parser.add_argument("--out-dir", help="write here instead of the shipped questlines (the index is left alone)")
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    out_dir = os.path.abspath(args.out_dir) if args.out_dir else TRAILS
    concepts = load_concepts()
    trails = load_trails(TRAILS, out_dir)
    try:
        trail, warnings = build(args, concepts, trails)
    except ValueError as problem:
        print(f"error: {problem}", file=sys.stderr)
        return 1

    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"{args.id}.json")
    if os.path.exists(out) and not args.overwrite:
        print(f"{out} exists (use --overwrite)", file=sys.stderr)
        return 1
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(trail, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    print(f"wrote {out}")
    for warning in warnings:
        print(f"warning: {warning}", file=sys.stderr)
    if out_dir == TRAILS:
        print("next:")
        if not args.after:
            print("  - this is a root; to put it deeper in the tree, pass --after <parent ids>")
        print("  - link a next trail: a trail below level 18 needs a child (docs/questline-attributes.md)")
        print("  - add German clues if you want riddles, then ./gradlew test")
        print("  - the shipped trail count is asserted in QuestlineValidationTest and TrailGraphTest")
        print("  - python3 tools/quest_tree.py   # refreshes docs/book/src/quest-tree.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
