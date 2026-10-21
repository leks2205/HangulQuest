#!/usr/bin/env python3
"""Sorts the trails into life-situation chapters and writes data/chapters.json (roadmap WP5.3).

A trail goes into at most one chapter. The script scores every chapter for every trail from three things:
  topic   the trail's "topic" field (the strongest hint)
  title   words in the Korean title
  goals   the items, blocks, entities, biomes and dimensions the steps ask for (a weak hint: the generated
          trails pick decorative blocks at random, so a single item never decides)
A trail is placed in the best chapter whose level range fits (within TOLERANCE levels); a trail with no clear
chapter stays out. Questline files are only read, never changed.

  python3 tools/assign_chapters.py            rewrite chapters.json
  python3 tools/assign_chapters.py --check    exit 1 when chapters.json is not what the script would write
  python3 tools/assign_chapters.py --report   print the chapters and the trails left out
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src" / "main" / "resources" / "assets" / "hangulquest" / "data"
TRAILS = DATA / "questlines"
OUT = DATA / "chapters.json"

# How far outside its range a trail may sit and still join (a level 1 food trail can belong to the meals chapter).
# A chapter can set its own "reach"; the nether and end chapters start below their range with their advancement roots.
TOLERANCE = 1
STRONG_TOLERANCE = 1
MIN_SCORE = 3

# id, Korean title, icon item, level range, topics, title words, goal words (parts of registry ids)
CHAPTERS = [
    dict(id="house", ko="집", icon="minecraft:oak_door", levels=(1, 2),
         topics=["집", "유리", "양탄자", "화분", "울타리", "벽돌"],
         words=["집", "유리창", "양탄자", "울타리", "화분", "벽돌", "이웃의 문"],
         exact=["나무의 길", "우리 집의 길"],
         goals=["oak_door", "glass", "glass_pane", "carpet", "flower_pot", "crafting_table", "oak_planks"]),
    dict(id="fire_light", ko="불과 빛", icon="minecraft:torch", levels=(1, 2),
         topics=["불", "촛불", "밤", "달빛", "달", "별"],
         words=["불의", "불자리", "불꽃", "*횃불", "*촛불", "*모닥불", "*등불", "등대", "초의", "*빛", "밤", "별", "달빛"],
         avoid=["산호초", "불멸", "불가사의"],
         goals=["torch", "campfire", "candle", "lantern", "redstone_torch"]),
    dict(id="field", ko="밭", icon="minecraft:wheat", levels=(1, 2),
         topics=["사과", "호박", "해바라기", "허수아비", "과수원", "정원", "꽃", "꽃밭", "꽃관", "버섯", "씨앗"],
         words=["밭", "*씨앗", "정원", "과수원", "호박", "사과", "해바라기", "허수아비", "꽃", "수확", "약초", "건초"],
         exact=["농사"],
         avoid=["왕관"],
         goals=["wheat_seeds", "carrot", "potato", "beetroot", "pumpkin", "apple", "oak_sapling", "poppy", "dandelion"]),
    dict(id="animals", ko="동물", icon="minecraft:egg", levels=(1, 2),
         topics=["동물", "여우", "개구리", "거북", "새", "염소", "늑대", "말", "우유", "양봉", "husbandry"],
         words=["동물", "목장", "양털", "마구간", "말타기", "말", "여우", "개구리", "거북", "염소", "늑대", "꿀벌", "고양이", "양봉"],
         avoid=["왕관"],
         goals=["cow", "pig", "sheep", "chicken", "horse", "wolf", "cat", "rabbit", "fox", "bee", "goat", "shears", "lead"]),
    dict(id="colors", ko="색깔", icon="minecraft:red_dye", levels=(1, 2),
         topics=["색", "무지개", "염색"],
         words=["색", "무지개", "염색", "염료"],
         goals=["_dye"]),
    dict(id="market", ko="시장", icon="minecraft:emerald", levels=(1, 2),
         topics=["마을", "무역", "시장", "종", "우편", "편지"],
         words=["시장", "장터", "거래", "상단", "무역", "상인", "종의"],
         avoid=["왕관", "정원"],
         goals=["villager", "emerald", "bell", "trader_llama", "wandering_trader"]),
    dict(id="meals", ko="식사", icon="minecraft:bread", levels=(2, 3),
         topics=["음식", "요리", "양조"],
         words=["음식", "잔치", "잔칫", "요리", "빵", "먹을", "산해진미", "씨앗밭"],
         avoid=["왕관"],
         goals=["bread", "cooked_", "cake", "mushroom_stew", "milk_bucket", "egg", "pumpkin_pie"]),
    dict(id="weather", ko="날씨", icon="minecraft:snowball", levels=(2, 3),
         topics=["날씨", "눈", "겨울", "하늘", "바람", "구름", "눈사람", "눈보라", "천둥", "계절", "저녁"],
         words=["날씨", "비의", "눈보라", "첫눈", "얼음", "빙하", "겨울", "바람", "구름", "천둥", "하늘", "계절", "해질녘", "눈싸움", "눈사람"],
         avoid=["엔더"],
         goals=["snow", "snowball", "ice", "lightning_rod"]),
    dict(id="cave", ko="동굴", icon="minecraft:stone_pickaxe", levels=(2, 3),
         topics=["돌", "광산", "동굴", "보석", "대장간", "화로", "석탄", "용광로", "석공"],
         words=["돌", "광산", "동굴", "대장간", "용광로", "화로", "석탄", "채석장", "석공", "보석", "수정", "광맥", "철이"],
         avoid=["구름", "세계"],
         goals=["_ore", "furnace", "coal", "iron_ingot", "diamond", "smithing_table"]),
    dict(id="journey", ko="여행", icon="minecraft:map", levels=(3, 4),
         topics=["산", "사막", "정글", "지도"],
         words=["여행", "나그네", "지도", "산길", "봉우리", "정상", "사막", "정글"],
         avoid=["왕관", "세계", "별자리"],
         goals=["map", "compass", "spyglass"]),
    dict(id="sea", ko="바다", icon="minecraft:nautilus_shell", levels=(3, 4),
         topics=["물", "섬", "해적", "조선"],
         words=["바다", "파도", "해안", "해적", "섬", "항구", "부두", "낚시", "어부", "산호", "항해", "조선", "물결", "호수", "대양", "해변", "밤바다"],
         avoid=["왕관", "세계", "용의"],
         goals=["_ocean", "squid", "dolphin", "turtle", "kelp", "trident", "oak_boat", "fishing_rod", "nautilus_shell"]),
    dict(id="festival", ko="마을 축제", icon="minecraft:cake", levels=(4, 6),
         topics=[],
         words=["축제", "왕관", "*잔치"],
         avoid=["엔더", "지옥"],
         goals=["cake", "firework_rocket"]),
    dict(id="nether", ko="네더", icon="minecraft:netherrack", levels=(4, 6), reach=2,
         topics=["nether", "용암", "화산", "영혼", "해골"],
         words=["네더", "지옥", "마그마", "위더", "가스트", "블레이즈", "피글린", "용암", "화산", "흑요석", "영혼"],
         avoid=["왕관", "공허"],
         goals=["netherrack", "soul_sand", "soul_soil", "blaze_rod", "ghast_tear", "piglin", "glowstone", "crimson_",
                "warped_", "magma_block", "basalt", "wither_skeleton_skull", "ancient_debris", "netherite_", "strider",
                "hoglin", "obsidian", "lava_bucket", "nether_bricks", "red_nether_bricks"]),
    dict(id="end", ko="엔드", icon="minecraft:ender_eye", levels=(4, 6), reach=2,
         topics=["end", "엔드", "용"],
         words=["엔드", "엔더", "드래곤", "용의", "*공허", "끝의 노래"],
         avoid=["왕관"],
         goals=["end_stone", "end_rod", "ender_pearl", "ender_eye", "enderman", "chorus_", "purpur_", "dragon_egg", "dragon_head",
                "elytra", "the_end", "shulker"]),
]


def goal_words(trail: dict) -> list[str]:
    out: list[str] = []
    steps = list(trail.get("steps", []))
    finale = trail.get("finale", {}).get("step")
    if finale:
        steps.append(finale)
    for step in steps:
        goal = step.get("goal", {})
        for key in ("item", "block", "entity", "biome", "dimension", "tag"):
            value = goal.get(key)
            if isinstance(value, str):
                out.append(value.replace("minecraft:", ""))
    return out


def title_hit(word: str, title: str) -> bool:
    """A word with a leading * matches anywhere; any other word must start one of the title's words."""
    if word.startswith("*"):
        return word[1:] in title
    return any(token.startswith(word) for token in title.split())


def goal_hit(pattern: str, goal: str) -> bool:
    """_ore matches the end of an id, cooked_ the start, anything else the whole id."""
    if pattern.startswith("_"):
        return goal.endswith(pattern)
    if pattern.endswith("_"):
        return goal.startswith(pattern)
    return goal == pattern


def score(chapter: dict, trail: dict, goals: list[str]) -> tuple[int, bool]:
    """The score of a trail for a chapter and whether it rests on topic or title (a strong match)."""
    title = trail.get("titleKo", "")
    if any(title_hit(word, title) for word in chapter.get("avoid", [])):
        return 0, False
    total = 0
    strong = False
    topic = trail.get("topic") or ""
    if topic and topic in chapter["topics"]:
        total += 4
        strong = True
    if title in chapter.get("exact", []):
        total += 6
        strong = True
    hits = sum(1 for word in chapter["words"] if title_hit(word, title))
    if hits:
        total += 3 + (hits - 1)
        strong = True
    if goals:
        votes = sum(1 for goal in goals if any(goal_hit(part, goal) for part in chapter["goals"]))
        if votes >= 2 and votes * 2 >= len(goals):
            total += 2 + votes
        elif votes:
            total += 1
    return total, strong


def distance(level: int, low: int, high: int) -> int:
    return low - level if level < low else level - high if level > high else 0


def assign(trails: list[dict]) -> dict[str, list[str]]:
    placed: dict[str, list[str]] = {chapter["id"]: [] for chapter in CHAPTERS}
    for trail in sorted(trails, key=lambda t: (t.get("order", 0), t["id"])):
        if trail["id"].endswith("_demo") or trail["id"] == "vorlage":
            continue
        level = trail.get("koreanLevel", 1)
        goals = goal_words(trail)
        best = None
        for rank, chapter in enumerate(CHAPTERS):
            total, strong = score(chapter, trail, goals)
            gap = distance(level, *chapter["levels"])
            limit = chapter.get("reach", STRONG_TOLERANCE if strong else TOLERANCE)
            if total < MIN_SCORE or gap > limit:
                continue
            key = (-total, gap, rank)
            if best is None or key < best[0]:
                best = (key, chapter["id"])
        if best:
            placed[best[1]].append(trail["id"])
    return placed


def load_trails() -> list[dict]:
    trails = []
    for path in sorted(TRAILS.glob("*.json")):
        if path.name.startswith("_"):
            continue
        trails.append(json.loads(path.read_text(encoding="utf-8")))
    return trails


def build() -> dict:
    trails = load_trails()
    placed = assign(trails)
    return {
        "schema": 1,
        "comment": "Life-situation chapters for the quest tab. Written by tools/assign_chapters.py; the assignment is "
                   "computed from each trail's topic, title and goals, so rerun the script after adding trails. "
                   "A trail is in at most one chapter; the rest stay in the tree only.",
        "chapters": [
            {"id": chapter["id"], "titleKo": chapter["ko"], "icon": chapter["icon"],
             "levels": list(chapter["levels"]), "trails": placed[chapter["id"]]}
            for chapter in CHAPTERS
        ],
    }


def render(data: dict) -> str:
    lines = ['{', '\t"schema": 1,', '\t"comment": ' + json.dumps(data["comment"], ensure_ascii=False) + ',',
             '\t"chapters": [']
    chapters = data["chapters"]
    for index, chapter in enumerate(chapters):
        lines.append('\t\t{')
        lines.append('\t\t\t"id": ' + json.dumps(chapter["id"]) + ',')
        lines.append('\t\t\t"titleKo": ' + json.dumps(chapter["titleKo"], ensure_ascii=False) + ',')
        lines.append('\t\t\t"icon": ' + json.dumps(chapter["icon"]) + ',')
        lines.append('\t\t\t"levels": ' + json.dumps(chapter["levels"]) + ',')
        lines.append('\t\t\t"trails": [')
        trails = chapter["trails"]
        for i, trail in enumerate(trails):
            lines.append('\t\t\t\t' + json.dumps(trail) + (',' if i < len(trails) - 1 else ''))
        lines.append('\t\t\t]')
        lines.append('\t\t}' + (',' if index < len(chapters) - 1 else ''))
    lines.append('\t]')
    lines.append('}')
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--report", action="store_true")
    args = parser.parse_args()
    data = build()
    text = render(data)
    if args.report:
        trails = {t["id"]: t for t in load_trails()}
        used = set()
        for chapter in data["chapters"]:
            print(f"{chapter['id']} {chapter['titleKo']} L{chapter['levels']} ({len(chapter['trails'])})")
            for trail_id in chapter["trails"]:
                used.add(trail_id)
                trail = trails[trail_id]
                print(f"    {trail_id:40s} L{trail['koreanLevel']} {trail.get('topic')} {trail['titleKo']}")
        left = [t for t in trails if t not in used]
        print(f"left out: {len(left)} of {len(trails)}")
        return 0
    if args.check:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            print("chapters.json is out of date: run python3 tools/assign_chapters.py", file=sys.stderr)
            return 1
        return 0
    OUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}: " + ", ".join(
        f"{c['id']} {len(c['trails'])}" for c in data["chapters"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
