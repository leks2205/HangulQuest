#!/usr/bin/env python3
"""Export every Korean line a player can see to content/korean/lines.jsonl.

Input is build/reports/korean/sentences.tsv (written by SentenceDumpTest; columns id, level,
source, ko, goal) plus the questline and concept JSON for the English and German meaning and the
context. Ids are the TSV ids, which come from the source location (trail.<trail>.<step>.clue,
concept.<id>.descKo), never from the text.

    ./gradlew test --offline --tests '*SentenceDumpTest'
    python3 tools/review/export_lines.py            # write content/korean/lines.jsonl
    python3 tools/review/export_lines.py --check    # fail when the file is out of date

Record: {id, ko, en, de, context{trail, trailTitle, topic, step, goal, goalKey, level, icon, field},
         source{kind: authored|generated|lexicon, file, pointer, rule?, inputs?}, textHash}
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "i18n"))
from common import DATA_DIR, LINES_PATH, SENTENCES_TSV, read_json, text_hash, write_lines  # noqa: E402
import content_text  # noqa: E402  (the English and German text lives in lang/content/*.json)

GOAL_VERB = {
    "ACQUIRE_ITEM": "Get", "PLACE_BLOCK": "Place", "BREAK_BLOCK": "Break", "LOOK_AT_ENTITY": "Look at",
    "LOOK_AT_BLOCK": "Look at", "USE_BLOCK": "Use", "REACH_BIOME": "Go to the biome", "DEFEAT_ENTITY": "Defeat",
    "CONSUME_ITEM": "Eat or drink", "INTERACT_ENTITY": "Interact with", "REACH_DIMENSION": "Travel to",
}
GOAL_KEY = {
    "ACQUIRE_ITEM": "item", "PLACE_BLOCK": "block", "BREAK_BLOCK": "block", "LOOK_AT_ENTITY": "entity",
    "LOOK_AT_BLOCK": "block", "USE_BLOCK": "block", "REACH_BIOME": "biome", "DEFEAT_ENTITY": "entity",
    "CONSUME_ITEM": "item", "INTERACT_ENTITY": "entity", "REACH_DIMENSION": "dimension",
}
CONDITION_TEXT = {
    ("TIME", "NIGHT"): "at night", ("TIME", "DAY"): "during the day", ("TIME", "NOON"): "at noon",
    ("TIME", "DUSK"): "at dusk", ("TIME", "DAWN"): "at dawn",
    ("WEATHER", "RAIN"): "while it rains", ("WEATHER", "CLEAR"): "in clear weather",
    ("WEATHER", "THUNDER"): "during a thunderstorm",
}


def pretty(identifier: str) -> str:
    """minecraft:exposed_copper_golem_statue -> Exposed Copper Golem Statue"""
    name = identifier.split(":", 1)[-1].lstrip("#")
    return " ".join(part.capitalize() for part in name.split("_"))


def describe_goal(step: dict) -> tuple[str, str]:
    """(plain English task, main item/block/entity id) for a questline step."""
    goal = step["goal"]
    kind = goal["type"]
    target = goal.get(GOAL_KEY.get(kind, ""), "")
    count = goal.get("count", 0)
    text = f"{GOAL_VERB.get(kind, kind)} {pretty(target)}" if target else kind
    if count > 1:
        text += f" (x{count})"
    for cond in step.get("conditions", []):
        extra = CONDITION_TEXT.get((cond.get("type"), cond.get("phase") or cond.get("weather")))
        if extra:
            text += f", {extra}"
        elif cond.get("type") == "IN_BIOME":
            text += f", in the biome {pretty(cond.get('biome', ''))}"
        elif cond.get("type") == "IN_DIMENSION":
            text += f", in {pretty(cond.get('dimension', ''))}"
        elif cond.get("type") == "Y_RANGE":
            text += f", between height {cond.get('minY')} and {cond.get('maxY')}"
    return text, target


def to_pointer(path: str) -> str:
    """steps[0].clue.ko -> /steps/0/clue/ko"""
    out = []
    for part in path.replace("[", ".").replace("]", "").split("."):
        if part:
            out.append(part.replace("~", "~0").replace("/", "~1"))
    return "/" + "/".join(out)


QUESTLINE_FILES: dict[str, str] = {}  # trail id -> file name, filled by load_questlines


def load_questlines(data_dir: Path) -> dict[str, dict]:
    """The trails with their English and German text put back as inline fields (from the lang files)."""
    result = {}
    bundles = content_text.load_bundles(data_dir.parent / "lang" / "content")
    for file in sorted((data_dir / "questlines").glob("*.json")):
        if file.name == "index.json":
            continue
        trail = json.loads(file.read_text(encoding="utf-8"))
        # Keyed by the trail id; the file is named like it, except for the demo trail (mill_demo.json).
        trail_id = trail.get("id", file.stem)
        QUESTLINE_FILES[trail_id] = file.name
        result[trail_id] = content_text.merge_trail(trail, bundles)
    return result


def concept_levels(trails: dict[str, dict], concepts: list[dict]) -> dict[str, tuple[int, str]]:
    """Concept id -> (lowest Korean level of a trail that teaches it, that trail's id)."""
    best: dict[str, tuple[int, str]] = {}
    for tid, trail in sorted(trails.items()):
        ids = set(trail.get("concepts", []))
        for step in trail["steps"] + [trail["finale"]["step"]]:
            ids.update(step.get("teach", []))
        for cid in ids:
            level = trail.get("koreanLevel", 1)
            if cid not in best or level < best[cid][0]:
                best[cid] = (level, tid)
    return best


def step_by_id(trail: dict, step_id: str):
    """(step, pointer to it)"""
    if step_id == "finale":
        return trail["finale"]["step"], "/finale/step"
    for i, step in enumerate(trail["steps"]):
        if step["id"] == step_id:
            return step, f"/steps/{i}"
    return None, ""


def read_tsv(path: Path) -> list[dict]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t", quoting=csv.QUOTE_NONE))


SPEECH_LEVELS = {"haeyo": "해요체 (haeyo)", "banmal": "반말 (banmal)", "hapsyo": "합쇼체 (hapsyo)"}


def dialogue_record(row: dict, en: dict, de: dict) -> dict:
    """A villager conversation line. Ids are dialogue.<file>.<node>[.c<N>]; the English and German
    gloss are the lang keys hangulquest.<id>; the goal column is 'Dialogue <speaker> <level> [right|wrong|ungraded]'."""
    rid = row["id"]
    _, file, node, *reply = rid.split(".")
    _, speaker, level, *grade = row["goal"].split()
    if reply:
        goal = f"Reply the player can pick in speech level {SPEECH_LEVELS[level]}"
        goal += {"right": ": the right form here.", "wrong": ": the wrong form here (the lesson is the speech level).",
                 "ungraded": "."}[grade[0]]
    else:
        goal = f"Line of the {speaker} in a conversation, speech level {SPEECH_LEVELS[level]}."
    path = row["source"].partition(":")[2]
    key = "hangulquest." + rid
    return {
        "id": rid, "ko": row["ko"], "en": en.get(key, ""), "de": de.get(key, ""),
        "context": {"trail": "", "trailTitle": "", "topic": "", "step": node, "goal": goal,
                    "level": int(row["level"] or 1), "icon": "", "field": "dialogue", "goalKey": row["goal"]},
        "source": {"kind": "authored", "file": path.split("#", 1)[0], "pointer": to_pointer(path.split("#", 1)[1])},
    }


LIBRARY_PREFIXES = ("story.", "culture.", "chapter.")


def library_record(row: dict, en: dict, de: dict, data_dir: Path) -> dict:
    """A line of the Library: a story title or paragraph (story.<id>.title / .p<N>), the Korean summary of a culture
    note (culture.<id>.ko) or a chapter title (chapter.<id>.title). The English and German text are lang keys: the
    id with the prefix hangulquest., except that a culture summary is hangulquest.culture.<id>.summary."""
    rid = row["id"]
    kind, ident, part = rid.split(".")
    key = f"hangulquest.culture.{ident}.summary" if kind == "culture" else "hangulquest." + rid
    path, _, pointer = row["source"].partition(":")[2].partition("#")
    icon = ""
    if kind in ("culture", "chapter"):
        listing = "notes" if kind == "culture" else "chapters"
        entries = read_json(data_dir / path, {}).get(listing, [])
        icon = next((entry.get("icon", "") for entry in entries if entry.get("id") == ident), "")
    return {
        "id": rid, "ko": row["ko"], "en": en.get(key, ""), "de": de.get(key, ""),
        "context": {"trail": "", "trailTitle": "", "topic": "", "step": part, "goal": row["goal"],
                    "level": int(row["level"] or 1), "icon": icon, "field": kind, "goalKey": row["goal"]},
        "source": {"kind": "authored", "file": path, "pointer": to_pointer(pointer)},
    }


def build_records(rows: list[dict], data_dir: Path = DATA_DIR) -> list[dict]:
    trails = load_questlines(data_dir)
    en_lang = read_json(data_dir.parent / "lang" / "en_us.json", {})
    de_lang = read_json(data_dir.parent / "lang" / "de_de.json", {})
    concepts = content_text.merge_concepts(
        read_json(data_dir / "concepts.json", []), content_text.load_bundles(data_dir.parent / "lang" / "content"))
    concept_index = {c["id"]: i for i, c in enumerate(concepts)}
    levels = concept_levels(trails, concepts)
    records = []
    for row in rows:
        rid = row["id"]
        ko = row["ko"]
        source_raw = row["source"]
        kind_raw, _, rest = source_raw.partition(":")
        record: dict
        if rid.startswith("pseudo."):
            # a nonword of the placement test (assets/.../pseudowords.json); reviewers confirm it is NOT a word
            index = int(rest.split("words[", 1)[1].split("]", 1)[0])
            record = {
                "id": rid, "ko": ko, "en": "", "de": "",
                "context": {"trail": "", "trailTitle": "", "step": "",
                            "goal": "Nonword of the placement test: a made-up word that must not be a real Korean word",
                            "level": 0, "icon": "", "field": "ko", "nonword": True},
                "source": {"kind": "lexicon", "file": "pseudowords.json", "pointer": f"/words/{index}/ko",
                           "nonword": True},
            }
        elif rid.startswith("concept."):
            cid, _, field = rid[len("concept."):].rpartition(".")
            concept = concepts[concept_index[cid]]
            level, first_trail = levels.get(cid, (min(int(concept.get("tier", 1)), 6), ""))
            en = concept.get("en", "") if field == "ko" else ""
            de = {"ko": concept.get("de", ""), "sentenceKo": concept.get("sentenceDe", "")}.get(field, "")
            icon = concept.get("icon") or (concept.get("items") or [""])[0]
            record = {
                "id": rid, "ko": ko, "en": en, "de": de,
                "context": {
                    "trail": first_trail, "trailTitle": "", "step": "",
                    "goal": f"Vocabulary entry '{concept.get('en', cid)}' ({concept.get('category', '').lower()})",
                    "level": level, "icon": icon, "field": field,
                },
                "source": {"kind": "lexicon", "file": "concepts.json",
                           "pointer": f"/{concept_index[cid]}/{field}"},
            }
        elif rid.startswith("dialogue."):
            record = dialogue_record(row, en_lang, de_lang)
        elif rid.startswith(LIBRARY_PREFIXES):
            record = library_record(row, en_lang, de_lang, data_dir)
        else:
            _, tid, *middle = rid.split(".")
            trail = trails[tid]
            field = middle[-1]
            step_id = middle[0] if len(middle) > 1 else ""
            level = trail.get("koreanLevel", int(row["level"] or 1))
            title_en = trail.get("titleEn", "")
            file = f"questlines/{QUESTLINE_FILES.get(tid, tid + '.json')}"
            context = {"trail": tid, "trailTitle": title_en, "topic": trail.get("topic", ""), "step": step_id,
                       "goal": "", "level": level,
                       "icon": "", "field": field}
            step = None
            pointer_to_step = ""
            if step_id:
                step, pointer_to_step = step_by_id(trail, step_id)
            elif field == "finale":
                step, pointer_to_step = trail["finale"]["step"], "/finale/step"
            if step is not None:
                context["goal"], context["icon"] = describe_goal(step)
            if row["goal"]:
                context["goalKey"] = row["goal"]  # the linter reads this compact form
            en = de = ""
            if field == "title":
                en, de = trail.get("titleEn", ""), trail.get("titleDe", "")
            elif field == "finale":
                en, de = trail["finale"].get("textEn", ""), trail["finale"].get("textDe", "")
            elif step is not None:
                en = step.get(field, {}).get("en", "")
                de = step.get(field, {}).get("de", "")
            if kind_raw == "authored":
                path = rest.split("#", 1)[1]
                source = {"kind": "authored", "file": file, "pointer": to_pointer(path)}
            else:
                rule = rest.split("@", 1)[0]
                source = {"kind": "generated", "file": file, "rule": rule}
                if "@" in rest:  # the ceremony text is stored in the JSON by tools/apply_ceremony_finales.py
                    source["pointer"] = to_pointer(rest.split("#", 1)[1])
                if pointer_to_step:
                    source["inputs"] = pointer_to_step + "/goal"
            record = {"id": rid, "ko": ko, "en": en, "de": de, "context": context, "source": source}
        record["textHash"] = text_hash(ko)
        records.append(record)
    records.sort(key=lambda r: r["id"])
    return records


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tsv", default=str(SENTENCES_TSV))
    parser.add_argument("--out", default=str(LINES_PATH))
    parser.add_argument("--check", action="store_true", help="exit 1 when the output file would change")
    args = parser.parse_args(argv)
    tsv = Path(args.tsv)
    if not tsv.is_file():
        print(f"{tsv} is missing. Run: ./gradlew test --offline --tests '*SentenceDumpTest'", file=sys.stderr)
        return 2
    records = build_records(read_tsv(tsv))
    out = Path(args.out)
    if args.check:
        from common import read_lines
        if read_lines(out) != records:
            print(f"{out} is out of date. Run: python3 tools/review/export_lines.py", file=sys.stderr)
            return 1
        print(f"{out}: up to date ({len(records)} lines)")
        return 0
    write_lines(records, out)
    kinds: dict[str, int] = {}
    for r in records:
        kinds[r["source"]["kind"]] = kinds.get(r["source"]["kind"], 0) + 1
    print(f"wrote {len(records)} lines to {out} {kinds}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
