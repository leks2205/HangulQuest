"""Source rows, language files and the status file of the translation pipeline.

English is the source language. A *row* is one translatable key of `lang/en_us.json` (interface) or
`lang/content/en_us.json` (trail, step and word text) with what a translator needs next to it: the
Korean line it accompanies, what kind of text it is, its placeholders and, for interface labels, a
length limit. `translate.py` exports rows as packets and imports the translated rows again.

Status file `content/translations/<code>-status.json`, one line per key:

    "<key>": {"reviewers":["gemini"],"src":"<hash of the English>","state":"machine","txt":"<hash of the text>"}

`src` is the hash of the English the translation was made from and `txt` the hash of the translation
as the tool wrote it. They are what makes the states honest without a database:

    English differs from `src`         -> stale   (the translation answers an older sentence)
    translation differs from `txt`     -> community-approved (someone edited the file by hand; in
                                          practice a merged pull request)
    otherwise                          -> the stored state: machine, proofread or community-approved
"""

from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

STATES = ("machine", "proofread", "community-approved", "stale")
# What a key looks like once a status is worked out from the files.
EXTRA_STATES = ("untracked", "missing")

PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?([a-zA-Z%])")
HANGUL = re.compile(r"[ᄀ-ᇿ㄰-㆏가-힣]")
FORM_DEPENDENT = re.compile(r"\bread the Korean\b|\bthe Korean says\b", re.IGNORECASE)
ORDER_ROW = re.compile(r"\bin the order\b", re.IGNORECASE)

# Keys the code needs in German only: the English translation of an example sentence is not written yet.
SENTENCE_KEY = re.compile(r"^hangulquest\.word\..+\.sentence$")


class Repo:
    """Where the files are. Tests build a small fake one; the tools default to this checkout."""

    def __init__(self, root: Path | str = ROOT):
        self.root = Path(root)
        self.assets = self.root / "src/main/resources/assets/hangulquest"
        self.lang = self.assets / "lang"
        self.content = self.lang / "content"
        self.languages_json = self.assets / "data/languages.json"
        self.status_dir = self.root / "content/translations"
        self.registry = self.root / "content/korean/lines.jsonl"

    def lang_path(self, code: str, content: bool) -> Path:
        return (self.content if content else self.lang) / f"{code}.json"

    def status_path(self, code: str) -> Path:
        return self.status_dir / f"{code}-status.json"


# Hashes and JSON ------------------------------------------------------------------------------

def text_hash(text: str) -> str:
    """12 hex characters of SHA-256 over the NFC text (the same scheme as the Korean review pipeline)."""
    return hashlib.sha256(unicodedata.normalize("NFC", text).encode("utf-8")).hexdigest()[:12]


def read_json(path: Path, default=None):
    if not path.is_file():
        return default
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def dump_bundle(texts: dict[str, str]) -> str:
    """The layout of the shipped lang files: tabs, one key per line, UTF-8 as is, final newline."""
    return json.dumps(texts, indent="\t", ensure_ascii=False) + "\n"


def read_bundle(repo: Repo, code: str, content: bool) -> dict[str, str]:
    return read_json(repo.lang_path(code, content), {}) or {}


def write_bundle(repo: Repo, code: str, content: bool, texts: dict[str, str], order: list[str]) -> None:
    """Writes texts in the order of `order` (the English file); keys it does not know follow in their own order."""
    known = set(order)
    ordered = {key: texts[key] for key in order if key in texts}
    ordered.update({key: value for key, value in texts.items() if key not in known})
    path = repo.lang_path(code, content)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(dump_bundle(ordered), encoding="utf-8")


# Rows -----------------------------------------------------------------------------------------

def placeholders(text: str) -> list[str]:
    return [match.group(0) for match in PLACEHOLDER.finditer(text)]


def translatable(text: str) -> bool:
    """False for values with nothing to translate: Korean-only names, bare placeholders, symbols."""
    bare = PLACEHOLDER.sub("", text)
    return re.search(r"[A-Za-z]{2}", bare) is not None


def max_length(text: str) -> int:
    """Longest translation an interface label may have: English plus 60%, and at least 10 characters more."""
    return max(-(-len(text) * 8 // 5), len(text) + 10)


KIND_NOTES = {
    "trail title": "Name of a quest trail, a chain of quest steps. Short, like a chapter title.",
    "clue": "Quest clue in the quest list: it tells the player what to do. The Korean sentence is shown next to it; "
            "do not copy it.",
    "hint": "Easier hint the player gets when asking for help with a quest step. The Korean sentence is shown next "
            "to it; do not copy it.",
    "finale": "Message shown when the player finishes the trail.",
    "word gloss": "Meaning of the Korean word on its word card: the plain dictionary meaning, as short as the "
                  "English. A comma separates synonyms.",
    "descKo gloss": "Translation of the short Korean description on a word card.",
    "sentence translation": "Translation of the Korean example sentence on a word card.",
    "chapter title": "Title of a chapter in the quest tab, a life situation.",
    "story": "Part of a short beginner story in the Library.",
    "culture note": "Text of a short note on Korean culture in the Library.",
    "dialogue": "Line of a villager conversation or feedback on a reply.",
    "keybinding label": "Name of a key binding in the controls screen. Very short.",
    "UI text": "Text in the mod's screens (help, explanation or message).",
    "UI label": "Label or short message in the mod's screens (button, tab, status line).",
    "text": "Text in the mod.",
}


def kind_of(key: str, en: str) -> str:
    """The kind of text a key holds (a key of KIND_NOTES)."""
    last = key.rsplit(".", 1)[-1]
    if key.startswith("hangulquest.trail."):
        return {"title": "trail title", "clue": "clue", "hint": "hint", "finale": "finale"}.get(last, "text")
    if key.startswith("hangulquest.word."):
        return {"gloss": "word gloss", "desc": "descKo gloss", "sentence": "sentence translation"}.get(last, "text")
    for prefix, kind in (("hangulquest.chapter.", "chapter title"), ("hangulquest.story.", "story"),
                         ("hangulquest.culture.", "culture note"), ("hangulquest.dialogue.", "dialogue"),
                         ("key.", "keybinding label")):
        if key.startswith(prefix):
            return kind
    if key.startswith("hangulquest.ui."):
        return "UI text" if len(en) > 60 else "UI label"
    return "text"


class Row(dict):
    """A source row as a plain dict (it goes straight into a packet): key, en, ko, kind, note (only for
    form-dependent rows; the other notes are per kind), placeholders, maxLen, formDependent, src.
    `file` ('ui' or 'content') is not exported."""

    @property
    def content(self) -> bool:
        return self["file"] == "content"


def registry_ids(key: str) -> tuple[str | None, list[tuple[str, str]]]:
    """(id of the Korean line next to a key, [(label, id)] of lines that explain it more) in the Korean registry."""
    parts = re.fullmatch(r"hangulquest\.trail\.(.+?)\.step\.([^.]+)\.(clue|hint)", key)
    if parts:
        return f"trail.{parts[1]}.{parts[2]}.{parts[3]}", []
    parts = re.fullmatch(r"hangulquest\.trail\.(.+?)\.(title|finale)", key)
    if parts:
        return f"trail.{parts[1]}.{parts[2]}", []
    parts = re.fullmatch(r"hangulquest\.word\.(.+)\.(gloss|desc|sentence)", key)
    if parts:
        word, field = parts[1], parts[2]
        if field == "gloss":
            return f"concept.{word}.ko", [("description", f"concept.{word}.descKo"), ("example", f"concept.{word}.sentenceKo")]
        return f"concept.{word}.{'descKo' if field == 'desc' else 'sentenceKo'}", [("word", f"concept.{word}.ko")]
    return key.removeprefix("hangulquest."), []


def read_registry(repo: Repo) -> dict[str, dict]:
    if not repo.registry.is_file():
        return {}
    out = {}
    for line in repo.registry.read_text(encoding="utf-8").splitlines():
        if line.strip():
            record = json.loads(line)
            out[record["id"]] = record
    return out


def source_rows(repo: Repo, registry: dict[str, dict] | None = None) -> list[Row]:
    """Every translatable key of the English files in file order: the interface file, then the content file."""
    registry = read_registry(repo) if registry is None else registry
    rows: list[Row] = []
    for content in (False, True):
        for key, en in read_bundle(repo, "en_us", content).items():
            if not isinstance(en, str) or not translatable(en):
                continue
            kind = kind_of(key, en)
            ko_id, more = registry_ids(key)
            korean = (registry.get(ko_id) or {}).get("ko", "")
            row = Row(key=key, en=en, ko=korean, kind=kind, note="", placeholders=placeholders(en),
                      maxLen=max_length(en) if kind in ("UI label", "UI text", "keybinding label") else None,
                      formDependent=bool(FORM_DEPENDENT.search(en)), src=text_hash(en),
                      file="content" if content else "ui")
            extra = [f"{label}: {registry[i]['ko']}" for label, i in more if i in registry and registry[i].get("ko")]
            if extra:
                row["koContext"] = extra
            if row["formDependent"]:
                row["note"] = ("Hides the order of the two actions on purpose; keep it hidden." if ORDER_ROW.search(en)
                               else "Hides where or what on purpose so that the player reads the Korean; keep it hidden.")
            rows.append(row)
    return rows


def row_index(rows: list[Row]) -> dict[str, Row]:
    return {row["key"]: row for row in rows}


def english_order(repo: Repo, content: bool) -> list[str]:
    return list(read_bundle(repo, "en_us", content))


# Status ---------------------------------------------------------------------------------------

def empty_status(code: str) -> dict:
    return {"schema": 1, "lang": code, "keys": {}}


def read_status(repo: Repo, code: str) -> dict:
    status = read_json(repo.status_path(code), None)
    if status is None:
        return empty_status(code)
    status.setdefault("keys", {})
    return status


def write_status(repo: Repo, code: str, status: dict, order: list[str]) -> None:
    """One line per key in English file order, so a state change is a one-line diff."""
    keys = status["keys"]
    position = {key: i for i, key in enumerate(order)}
    names = sorted(keys, key=lambda key: (position.get(key, len(order)), key))
    out = ["{", f' "schema": {status.get("schema", 1)},', f' "lang": {json.dumps(code)},', ' "keys": {']
    for i, key in enumerate(names):
        entry = json.dumps(keys[key], ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        out.append(f"  {json.dumps(key)}: {entry}{',' if i < len(names) - 1 else ''}")
    out += [" }", "}"]
    repo.status_dir.mkdir(parents=True, exist_ok=True)
    repo.status_path(code).write_text("\n".join(out) + "\n", encoding="utf-8")


def new_entry(row: Row, text: str, state: str, reviewers: list[str]) -> dict:
    return {"src": row["src"], "txt": text_hash(text), "state": state, "reviewers": list(reviewers)}


def effective_state(entry: dict | None, row: Row, text: str | None) -> str:
    """The state of one key worked out from the files, whatever the status file last said."""
    if text is None or text == "":
        return "missing"
    if entry is None:
        return "untracked"
    if entry.get("src") != row["src"]:
        return "stale"
    if entry.get("state") == "stale":
        return entry.get("was", "machine")  # the English went back to what the translation answers
    if entry.get("txt") != text_hash(text):
        return "community-approved"
    return entry.get("state", "machine")


def merged_bundle(repo: Repo, code: str) -> dict[str, str]:
    """Interface and content text of a language in one dict (the two files have no key in common)."""
    return {**read_bundle(repo, code, False), **read_bundle(repo, code, True)}


def languages_in_repo(repo: Repo) -> list[str]:
    """Codes with a lang file, a content file or a status file, except English; the shipped ones first."""
    codes = []
    index = read_json(repo.languages_json, {}) or {}
    codes += [code for code in index if code != "en_us"]
    for folder in (repo.lang, repo.content, repo.status_dir):
        if folder.is_dir():
            for path in sorted(folder.glob("*.json")):
                code = path.name.removesuffix("-status.json").removesuffix(".json")
                if code != "en_us" and code not in codes:
                    codes.append(code)
    return codes
