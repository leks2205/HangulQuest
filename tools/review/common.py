"""Shared helpers of the native-review pipeline: paths, text hash, JSON reading and writing."""
from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
CONTENT = ROOT / "content" / "korean"
LINES_PATH = CONTENT / "lines.jsonl"
STATUS_PATH = CONTENT / "review-status.json"
REVIEWERS_PATH = CONTENT / "reviewers.json"
LABELS_PATH = ROOT / "tools" / "review" / "labels.json"
DATA_DIR = ROOT / "src" / "main" / "resources" / "assets" / "hangulquest" / "data"
SENTENCES_TSV = ROOT / "build" / "reports" / "korean" / "sentences.tsv"

DEFAULT_REPO = "leks2205/HangulQuest"
STATUSES = ("unreviewed", "lint-ok", "ai-checked", "native-approved", "native-flagged")
# Self-declared levels that count towards an approval. Learners are welcome to comment, not to approve.
COUNTING_PROFICIENCY = ("native", "fluent", "teacher")
PROFICIENCY = ("native", "fluent", "teacher", "learner")
ID_PATTERN = re.compile(r"^[A-Za-z0-9_.\-]{1,200}$")


def text_hash(text: str) -> str:
    """12 hex characters of SHA-256 over the NFC text. Short enough for a prefilled URL."""
    return hashlib.sha256(unicodedata.normalize("NFC", text).encode("utf-8")).hexdigest()[:12]


def read_lines(path: Path = LINES_PATH) -> list[dict]:
    if not path.is_file():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_lines(records: list[dict], path: Path = LINES_PATH) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    body = "".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) + "\n" for r in records)
    path.write_text(body, encoding="utf-8")


def lines_by_id(records: list[dict]) -> dict[str, dict]:
    return {r["id"]: r for r in records}


def read_json(path: Path, default=None):
    if not path.is_file():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def empty_status() -> dict:
    return {"schema": 1, "lines": {}, "rules": {}}


def read_status(path: Path = STATUS_PATH) -> dict:
    data = read_json(path, None)
    if data is None:
        return empty_status()
    data.setdefault("lines", {})
    data.setdefault("rules", {})
    return data


def _entry_json(entry: dict) -> str:
    return json.dumps(entry, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def write_status(status: dict, path: Path = STATUS_PATH) -> None:
    """One line per id, sorted: a status change is a one-line diff."""
    out = ["{", ' "schema": %d,' % status.get("schema", 1), ' "lines": {']
    ids = sorted(status["lines"])
    for i, key in enumerate(ids):
        out.append("  %s: %s%s" % (json.dumps(key), _entry_json(status["lines"][key]),
                                   "," if i < len(ids) - 1 else ""))
    out.append(" },")
    out.append(' "rules": {')
    rules = sorted(status["rules"])
    for i, key in enumerate(rules):
        out.append("  %s: %s%s" % (json.dumps(key, ensure_ascii=False), _entry_json(status["rules"][key]),
                                   "," if i < len(rules) - 1 else ""))
    out.append(" }")
    out.append("}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def rule_slug(rule_id: str) -> str:
    return re.sub(r"[^A-Za-z0-9]+", "-", rule_id).strip("-").lower()
