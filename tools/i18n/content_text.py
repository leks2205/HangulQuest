"""Where the English and German quest and word text lives, and how to read it.

Shipped text is in `assets/hangulquest/lang/content/<code>.json` (flat key/value files, one per
language), not in the questline and concept JSON:

    hangulquest.trail.<id>.title
    hangulquest.trail.<id>.step.<sid>.clue          (the finale step is <sid> = finale)
    hangulquest.trail.<id>.step.<sid>.hint
    hangulquest.trail.<id>.finale                   (the ceremony text)
    hangulquest.word.<conceptId>.gloss              (the word's gloss: concepts.json `en` / `de`)
    hangulquest.word.<conceptId>.sentence           (translation of sentenceKo: `sentenceEn` / `sentenceDe`)

Tools that want the old, self-contained shape of a trail or concept call `merge_trail` and
`merge_concepts`: they put the lang text back as inline fields (an inline field that is already there
wins, as it does in the game).
"""

from __future__ import annotations

import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/hangulquest"
DATA = ASSETS / "data"
TRAILS = DATA / "questlines"
LANG = ASSETS / "lang"
CONTENT = LANG / "content"
LANGUAGES = ("en_us", "de_de")
# Inline field name per language, for each place text can sit.
SUFFIX = {"en_us": "En", "de_de": "De"}


def trail_files(directory: Path = TRAILS) -> list[Path]:
    """The shipped trail files, sorted; templates (_...) and the index are not trails."""
    return [path for path in sorted(directory.glob("*.json")) if not path.name.startswith("_") and path.name != "index.json"]


def read_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def read_bundle(code: str, directory: Path = CONTENT) -> dict[str, str]:
    path = directory / f"{code}.json"
    return read_json(path) if path.is_file() else {}


def write_bundle(code: str, texts: dict[str, str], directory: Path = CONTENT) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    # Tabs and a final newline, like the interface files next to it.
    (directory / f"{code}.json").write_text(
        json.dumps(texts, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")


def load_bundles(directory: Path = CONTENT) -> dict[str, dict[str, str]]:
    return {code: read_bundle(code, directory) for code in LANGUAGES}


# Keys -----------------------------------------------------------------------------------------

def title_key(trail_id: str) -> str:
    return f"hangulquest.trail.{trail_id}.title"


def step_key(trail_id: str, step_id: str, field: str) -> str:
    return f"hangulquest.trail.{trail_id}.step.{step_id}.{field}"


def finale_key(trail_id: str) -> str:
    return f"hangulquest.trail.{trail_id}.finale"


def word_key(concept_id: str, field: str) -> str:
    return f"hangulquest.word.{concept_id}.{field}"


def trail_steps(trail: dict) -> list[dict]:
    """Clue steps, then the finale step; the finale step always has the id `finale`."""
    steps = list(trail.get("steps", []))
    finale = (trail.get("finale") or {}).get("step")
    return steps + ([finale] if finale else [])


def step_id(step: dict) -> str:
    return step.get("id") or "finale"


def trail_key_list(trail: dict) -> list[str]:
    """Every content key a trail can have, in reading order: title, steps (clue, hint), finale text."""
    tid = trail["id"]
    keys = [title_key(tid)]
    for i, step in enumerate(trail_steps(trail)):
        sid = "finale" if i == len(trail.get("steps", [])) else step_id(step)
        keys += [step_key(tid, sid, "clue"), step_key(tid, sid, "hint")]
    keys.append(finale_key(tid))
    return keys


def concept_key_list(concept: dict) -> list[str]:
    return [word_key(concept["id"], "gloss"), word_key(concept["id"], "sentence")]


# Merging back ---------------------------------------------------------------------------------

def merge_trail(trail: dict, bundles: dict[str, dict[str, str]] | None = None) -> dict:
    """A copy of a trail with the lang text as inline fields (titleEn/De, clue/hint en/de, textEn/De)."""
    bundles = bundles if bundles is not None else load_bundles()
    out = copy.deepcopy(trail)
    tid = out["id"]
    for code, suffix in SUFFIX.items():
        bundle = bundles.get(code, {})
        text = bundle.get(title_key(tid))
        if text and not out.get("title" + suffix):
            out["title" + suffix] = text
        finale = out.get("finale")
        text = bundle.get(finale_key(tid))
        if text and finale is not None and not finale.get("text" + suffix):
            finale["text" + suffix] = text
        steps = trail_steps(out)
        for i, step in enumerate(steps):
            sid = "finale" if i == len(out.get("steps", [])) else step_id(step)
            for field in ("clue", "hint"):
                text = bundle.get(step_key(tid, sid, field))
                if text and not (step.get(field) or {}).get(suffix.lower()):
                    step.setdefault(field, {})[suffix.lower()] = text
    return out


def merge_concepts(concepts: list[dict], bundles: dict[str, dict[str, str]] | None = None) -> list[dict]:
    """A copy of the concepts with `en`, `de`, `sentenceEn` and `sentenceDe` filled from the lang files."""
    bundles = bundles if bundles is not None else load_bundles()
    out = copy.deepcopy(concepts)
    for concept in out:
        for code, suffix in SUFFIX.items():
            bundle = bundles.get(code, {})
            gloss = bundle.get(word_key(concept["id"], "gloss"))
            if gloss and not concept.get(suffix.lower()):
                concept[suffix.lower()] = gloss
            sentence = bundle.get(word_key(concept["id"], "sentence"))
            if sentence and not concept.get("sentence" + suffix):
                concept["sentence" + suffix] = sentence
    return out
