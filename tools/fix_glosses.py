#!/usr/bin/env python3
"""Repair the English glosses and the icon/referent split of the curated concepts.

Commit 90ec5d3 copied every curated concept's English gloss from the English name
of its first item, and the catalogue indexed that item as if it were the thing the
word denotes.  This script

  1. flags every curated concept whose ``en`` equals the vanilla English name of one
     of its items although the concept is not that item (the Korean word differs
     from the vanilla Korean name of the item),
  2. writes the corrected gloss into ``concepts.json`` (the ``en`` field only),
  3. moves items that merely illustrate a word (a drop for an animal, a tool for a
     verb, a bucket for a liquid, an item for a place or a time) from ``items`` to
     the new ``icon`` field, and
  4. writes the review lists ``content/review/gloss-fix-2.2.csv`` and
     ``content/review/icon-split-2.2.csv``.

The two tables below (FIXES, ICON_ONLY) are the hand-reviewed decisions.  The script
refuses to continue when a flagged concept has no decision, so new bad glosses cannot
slip in.  The file is rewritten with the formatting it already has (one space of
indent, key order untouched).  Running it twice changes nothing.

Usage:
    python3 tools/fix_glosses.py                      apply the fixes
    python3 tools/fix_glosses.py --check              report only, write nothing, exit 1 when work is left
    python3 tools/fix_glosses.py --names-from PATH    rebuild tools/data/names_en_26.3.json first;
                                                      PATH is an en_us.json or a Minecraft client jar
"""

from __future__ import annotations

import csv
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONCEPTS = os.path.join(ROOT, "src/main/resources/assets/hangulquest/data/concepts.json")
NAMES_KO_DE = os.path.join(ROOT, "src/main/resources/assets/hangulquest/data/names.json")
NAMES_EN = os.path.join(ROOT, "tools/data/names_en_26.3.json")
GLOSS_CSV = os.path.join(ROOT, "content/review/gloss-fix-2.2.csv")
ICON_CSV = os.path.join(ROOT, "content/review/icon-split-2.2.csv")

# ---------------------------------------------------------------------------
# Decisions, reviewed by hand.  id -> (new English gloss, reason)
# ---------------------------------------------------------------------------

ANIMAL = "gloss was the name of the mob's drop (icon item); the word is the animal"
MOB = "gloss was the name of the mob's drop (icon item); the word is the monster"
PLACE = "gloss was the name of an illustrating item; the word is a place or landscape"
TIME = "gloss was the name of an illustrating item; the word is a time or weather phenomenon"
ABSTRACT = "gloss was the name of an illustrating item; the word is an abstract noun"
VERB = "gloss was the name of the tool used as illustration; the word is a verb"
SUBSTANCE = "gloss was the name of the container item; the word is the substance"
VARIANT = "gloss was one specific variant of the item; the word is the generic term"

FIXES: dict[str, tuple[str, str]] = {
    # animals and monsters
    "cow": ("cow", ANIMAL), "pig": ("pig", ANIMAL), "sheep": ("sheep", ANIMAL),
    "chicken": ("chicken", ANIMAL), "horse": ("horse", ANIMAL), "wolf": ("wolf", ANIMAL),
    "cat": ("cat", ANIMAL), "rabbit": ("rabbit", ANIMAL), "fox": ("fox", ANIMAL),
    "bee": ("bee", ANIMAL), "turtle": ("turtle", ANIMAL), "squid": ("squid", ANIMAL),
    "goat": ("goat", ANIMAL), "parrot": ("parrot", ANIMAL), "bat": ("bat", ANIMAL),
    "frog": ("frog", ANIMAL), "villager": ("villager", ANIMAL), "slime": ("slime", MOB),
    "zombie": ("zombie", MOB), "skeleton": ("skeleton", MOB), "creeper": ("Creeper", MOB),
    "spider": ("spider", MOB), "enderman": ("Enderman", MOB), "witch": ("witch", MOB),
    "iron_golem": ("Iron Golem", MOB), "ender_dragon": ("Ender Dragon", MOB),
    "ghast": ("Ghast", MOB),
    # landscape, places, time
    "forest": ("forest", PLACE), "ocean": ("ocean", PLACE), "river": ("river", PLACE),
    "mountain": ("mountain", PLACE), "desert": ("desert", PLACE), "beach": ("beach", PLACE),
    "cave": ("cave", PLACE), "village": ("village", PLACE), "nether": ("the Nether", PLACE),
    "end": ("the End", PLACE), "jungle": ("jungle", PLACE), "house": ("house", PLACE),
    "world": ("world", PLACE), "sky": ("sky", TIME), "sun": ("sun", TIME),
    "moon": ("moon", TIME), "rain": ("rain", TIME), "thunder": ("thunder", TIME),
    "night": ("night", TIME), "day": ("day", TIME),
    "height": ("height", ABSTRACT), "eternity": ("eternity", ABSTRACT),
    "creation": ("creation", ABSTRACT), "ritual": ("ritual", ABSTRACT),
    "friend": ("friend", ABSTRACT), "crown": ("crown", ABSTRACT),
    "tree": ("tree", "gloss was 'Oak Sapling'; a sapling is not a tree (the word 묘목 has its own entry)"),
    # verbs
    "mine": ("to mine", VERB), "chop": ("to chop down", VERB), "place": ("to place", VERB),
    "collect": ("to collect", VERB), "eat": ("to eat", VERB), "drink": ("to drink", VERB),
    "look": ("to look at", VERB), "walk": ("to walk", VERB), "swim": ("to swim", VERB),
    "sleep": ("to sleep", VERB), "make": ("to make", VERB), "open": ("to open", VERB),
    "fight": ("to fight", VERB), "climb": ("to climb", VERB), "dig": ("to dig", VERB),
    "build": ("to build", VERB), "cook": ("to cook", VERB), "talk": ("to talk", VERB),
    "fish": ("fish", "gloss was 'Raw Cod'; the word is fish in general (cod, salmon, tropical fish)"),
    # substances shown by their container
    "water": ("water", SUBSTANCE), "lava": ("lava", SUBSTANCE), "milk": ("milk", SUBSTANCE),
    "honey": ("honey", SUBSTANCE),
    # generic term, gloss was one specific variant
    "grass": ("grass", "gloss was 'Grass Block'; the word 잔디 is grass or lawn"),
    "log": ("log", VARIANT), "planks": ("planks", VARIANT),
    "iron": ("iron", "gloss was 'Iron Ingot'; the word 철 is the metal"),
    "gold": ("gold", "gloss was 'Gold Ingot'; the word 금 is the metal"),
    "snow": ("snow", "gloss was 'Snow Block'; the word 눈 is snow"),
    "bed": ("bed", VARIANT), "door": ("door", VARIANT), "flower": ("flower", VARIANT),
    "mushroom": ("mushroom", VARIANT), "pickaxe": ("pickaxe", VARIANT),
    "axe": ("axe", VARIANT), "shovel": ("shovel", VARIANT), "hoe": ("hoe", VARIANT),
    "sword": ("sword", VARIANT), "wool": ("wool", VARIANT), "fence": ("fence", VARIANT),
    "carpet": ("carpet", VARIANT), "gate": ("gate", VARIANT), "dye": ("dye", VARIANT),
    "leaves": ("leaves", VARIANT), "sapling": ("sapling", VARIANT),
    "mushroom_block": ("mushroom block", VARIANT),
    "beef": ("beef", "gloss was 'Steak' (the cooked item); the word 소고기 is beef"),
    "pork": ("pork", "gloss was 'Cooked Porkchop'; the word 돼지고기 is pork"),
    "chicken_meat": ("chicken meat", "gloss was 'Cooked Chicken'; also keeps it apart from 닭 'chicken'"),
    "melon": ("watermelon", "gloss was 'Melon Slice'; 수박 is the watermelon"),
    "map": ("map", "gloss was 'Empty Map'; the word 지도 is a map"),
    "redstone": ("redstone", "gloss was 'Redstone Dust'; the word is redstone"),
    "shell": ("shell", "gloss was 'Nautilus Shell'; the word 조개 is a shell"),
    "prismarine": ("prismarine", "gloss was 'Prismarine Shard'; the word is prismarine"),
    "clay": ("clay", "gloss was 'Clay Ball'; the word 점토 is clay"),
    "salmon": ("salmon", "gloss was 'Raw Salmon'; the word 연어 is salmon"),
}

# Flagged by the rule but already a correct English gloss for the same thing; only the
# vanilla Korean spelling differs.  id -> reason
KEEP: dict[str, str] = {
    "crafting_table": "same block; the concept uses 작업대, vanilla says 제작대",
    "note_block": "same block; 음표 블록 vs vanilla 소리 블록",
    "ink_sac": "same item; spelling differs from vanilla 먹물 주머니",
    "cocoa_beans": "same item; 카카오 콩 vs vanilla 코코아 콩",
    "turtle_egg": "same item; spacing differs (거북 알 vs 거북알)",
    "honey_bottle": "same item; 꿀병 is the short form of vanilla 꿀이 든 병",
    "bone": "same item; 뼈 vs vanilla 뼈다귀",
    "jungle_log": "same block; 정글 나무 vs vanilla 정글나무 원목",
    "adv_item_tnt": "same block; the concept transliterates TNT",
}

# Concepts whose items are only an illustration.  Everything in ``items`` moves to
# ``icon`` (the first item) except what is listed in ICON_KEEP_ITEMS.  id -> reason
DROP = "item is a drop of the mob, not the mob; the mob is referenced by its entity id"
PLACE_ICON = "item only illustrates the place or landscape"
TIME_ICON = "item only illustrates the time or weather"
ABSTRACT_ICON = "item only illustrates an abstract word"
VERB_ICON = "tool or object only illustrates the verb"
SUBSTANCE_ICON = "container item only illustrates the substance"

ICON_ONLY: dict[str, str] = {
    **{key: DROP for key in (
        "cow", "pig", "sheep", "chicken", "horse", "wolf", "cat", "rabbit", "fox", "bee",
        "turtle", "squid", "zombie", "skeleton", "creeper", "spider", "enderman", "witch",
        "villager", "goat", "iron_golem", "ender_dragon", "ghast", "parrot", "bat", "frog",
        "slime")},
    **{key: PLACE_ICON for key in (
        "forest", "ocean", "river", "mountain", "desert", "beach", "cave", "village",
        "nether", "end", "jungle", "house", "world")},
    **{key: TIME_ICON for key in ("sky", "sun", "moon", "rain", "thunder", "night", "day")},
    **{key: ABSTRACT_ICON for key in (
        "height", "eternity", "creation", "ritual", "friend", "crown")},
    "tree": "a sapling is not a tree",
    **{key: VERB_ICON for key in (
        "mine", "chop", "place", "collect", "eat", "drink", "look", "walk", "swim", "sleep",
        "make", "open", "fight", "climb", "dig", "build", "cook", "talk")},
    **{key: SUBSTANCE_ICON for key in ("water", "lava", "milk", "honey")},
}

# Items that stay referents although they are listed on an ICON_ONLY concept.
ICON_KEEP_ITEMS: dict[str, list[str]] = {
    "lava": ["minecraft:lava"],  # the fluid block is lava itself
}


# ---------------------------------------------------------------------------


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def dump_concepts(concepts) -> str:
    # Verified byte-identical to the stored file for unchanged data.
    return json.dumps(concepts, indent=1, ensure_ascii=False) + "\n"


def item_key(item_id: str, kind: str) -> str:
    namespace, path = item_id.split(":", 1)
    return f"{kind}.{namespace}.{path}"


def build_names(source: str, concepts) -> None:
    if source.endswith(".jar"):
        with zipfile.ZipFile(source) as jar:
            table = json.loads(jar.read("assets/minecraft/lang/en_us.json").decode("utf-8"))
    else:
        table = load_json(source)
    wanted = sorted({i for c in concepts for i in c.get("items", []) + ([c["icon"]] if c.get("icon") else [])})
    names = {}
    for item_id in wanted:
        for kind in ("item", "block"):
            value = table.get(item_key(item_id, kind))
            if value:
                names[item_id] = value
                break
    os.makedirs(os.path.dirname(NAMES_EN), exist_ok=True)
    with open(NAMES_EN, "w", encoding="utf-8") as handle:
        json.dump(names, handle, indent=1, ensure_ascii=False, sort_keys=True)
        handle.write("\n")
    print(f"wrote {NAMES_EN}: {len(names)} of {len(wanted)} referenced ids")


def vanilla_ko(table, item_id):
    for kind in ("item", "block"):
        entry = table.get(item_key(item_id, kind))
        if entry:
            return entry[0]
    return None


def main(argv) -> int:
    check = "--check" in argv
    concepts = load_json(CONCEPTS)
    if "--names-from" in argv:
        build_names(argv[argv.index("--names-from") + 1], concepts)
    names_en = load_json(NAMES_EN)
    names_ko = load_json(NAMES_KO_DE)

    by_id = {c["id"]: c for c in concepts}
    curated = [c for c in concepts if "sentenceKo" in c]

    # 1. flag
    flagged = []
    for concept in curated:
        shown = list(concept.get("items", []))
        if concept.get("icon"):
            shown.append(concept["icon"])
        for item in shown:
            if names_en.get(item) == concept["en"] and vanilla_ko(names_ko, item) != concept["ko"] \
                    and vanilla_ko(names_ko, item) is not None:
                flagged.append((concept, item))
                break
    undecided = [c["id"] for c, _ in flagged if c["id"] not in FIXES and c["id"] not in KEEP]
    if undecided:
        print("flagged concepts without a decision:", ", ".join(undecided))
        return 2
    unknown = [k for k in list(FIXES) + list(KEEP) + list(ICON_ONLY) if k not in by_id]
    if unknown:
        print("decisions for unknown concept ids:", ", ".join(unknown))
        return 2
    stale = [k for k in FIXES if k not in {c["id"] for c, _ in flagged}
             and by_id[k]["en"] != FIXES[k][0]]
    if stale:
        print("fix for a concept that is not flagged and differs from the target:", ", ".join(stale))
        return 2

    # old resolution of item -> concept, as the catalogue did it (first concept's first item wins)
    old_index: dict[str, str] = {}
    for concept in concepts:
        if concept.get("icon"):
            continue  # already migrated; the old index is only needed for the report
        if concept.get("items"):
            old_index.setdefault(concept["items"][0], concept["id"])

    gloss_rows = []
    icon_rows = []
    changed = False

    for concept, _item in flagged:
        cid = concept["id"]
        if cid in KEEP:
            continue
        new_en, reason = FIXES[cid]
        old_en = concept["en"]
        gloss_rows.append([cid, concept["ko"], concept["de"], old_en, new_en, reason])
        concept["en"] = new_en
        changed = True

    for cid, reason in ICON_ONLY.items():
        concept = by_id[cid]
        items = concept.get("items", [])
        if concept.get("icon") or not items:
            continue
        keep = ICON_KEEP_ITEMS.get(cid, [])
        icon = items[0]
        remaining = [i for i in items if i in keep]
        shown_for = old_index.get(icon)
        icon_rows.append([
            cid, concept["ko"], concept["de"], ";".join(items), icon, ";".join(remaining), reason,
            "wrong" if shown_for == cid else "",
        ])
        rebuilt = {}
        for key, value in concept.items():
            if key == "items":
                rebuilt["items"] = remaining
                rebuilt["icon"] = icon
            else:
                rebuilt[key] = value
        concept.clear()
        concept.update(rebuilt)
        changed = True

    print(f"flagged {len(flagged)} curated concepts: {len(gloss_rows)} glosses fixed, "
          f"{sum(1 for c, _ in flagged if c['id'] in KEEP)} kept; {len(icon_rows)} icon splits")

    # 2. collisions inside a category (after the fixes)
    seen: dict[tuple[str, str], list[str]] = {}
    for concept in curated:
        seen.setdefault((concept["category"], concept["en"].strip().lower()), []).append(concept["id"])
    for (category, gloss), ids in sorted(seen.items()):
        if len(ids) > 1:
            print(f"  collision in {category}: '{gloss}' -> {', '.join(ids)}")

    if check:
        return 1 if changed else 0
    if not changed:
        return 0

    with open(CONCEPTS, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(dump_concepts(concepts))
    os.makedirs(os.path.dirname(GLOSS_CSV), exist_ok=True)
    with open(GLOSS_CSV, "w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["id", "ko", "de", "old_en", "new_en", "reason"])
        writer.writerows(gloss_rows)
    with open(ICON_CSV, "w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["id", "ko", "de", "old_items", "icon", "new_items", "reason", "was_shown_for_icon_item"])
        writer.writerows(icon_rows)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
