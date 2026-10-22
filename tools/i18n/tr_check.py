"""The rules a translated row has to pass, for an import and for the CI check of shipped languages.

A row is rejected, not repaired, so the translator (or the person fixing it) sees why:

    placeholders       the same %s / %1$s / %% as the English (positional ones may be reordered)
    Hangul             the same Hangul characters as the English: none added, none removed
    untranslated       not the English again, not a text that is mostly English function words, and for
                       Cyrillic and CJK languages the script has to be there
    length             interface labels stay within `maxLen`
    line breaks, §     the same number of \\n and the same §-codes
    characters         no emoji or other characters outside the Basic Multilingual Plane (the game font
                       cannot draw them), no replacement character
    form giveaway      a clue that hides where, what or in which order must not name it
                       (tools/i18n/relation_words.json, one list per language)
"""

from __future__ import annotations

import json
import re
import unicodedata
from collections import Counter
from pathlib import Path

from tr_core import HANGUL, ORDER_ROW, PLACEHOLDER, Row, placeholders

HERE = Path(__file__).resolve().parent
# Function words that no language in the list shares with English; a text with several is untranslated.
NAME_PARTICLES = {"of", "the", "and", "in", "on", "a", "an", "to", "for", "de", "la"}
ENGLISH_ONLY = {"the", "and", "you", "your", "with", "this", "that", "are", "from", "have", "will", "when", "then"}
SCRIPTS = {
    "ru": re.compile(r"[Ѐ-ӿ]"),
    "ja": re.compile(r"[぀-ヿ一-鿿]"),
    "zh": re.compile(r"[一-鿿]"),
}

_RELATION_CACHE: dict[str, dict] = {}


def language_part(code: str) -> str:
    return code.split("_", 1)[0].lower()


def relation_words(path: Path | None = None) -> dict:
    key = str(path or "")
    if key not in _RELATION_CACHE:
        _RELATION_CACHE[key] = json.loads((path or HERE / "relation_words.json").read_text(encoding="utf-8"))
    return _RELATION_CACHE[key]


def _pattern(entry: str, substring: bool) -> re.Pattern:
    parts = re.split(r"\s+", unicodedata.normalize("NFC", entry.strip()))
    # Both apostrophes match each other.
    body = r"\s+".join(re.escape(part).replace("’", "'").replace("'", "['’]") for part in parts)
    if substring:
        return re.compile(body, re.IGNORECASE)
    return re.compile(r"(?<!\w)" + body + r"(?!\w)", re.IGNORECASE)


def giveaway(text: str, code: str, order: bool, words: dict | None = None) -> str | None:
    """The word of the language's list that names the hidden relation (or order) in a text, or None."""
    config = (words or relation_words()).get(language_part(code))
    if not config:
        return None
    substring = bool(config.get("substring"))
    normal = unicodedata.normalize("NFC", text)
    if language_part(code) == "tr":
        normal = normal.replace("İ", "i")  # Python lowers İ to i plus a combining dot, which no entry has
    # Longest first, so the message names the whole phrase ("encima de", not "encima").
    for entry in sorted(config.get("order" if order else "relation", []), key=len, reverse=True):
        found = _pattern(entry, substring).search(normal)
        if found:
            return found.group(0)
    return None


def has_list(code: str, words: dict | None = None) -> bool:
    return language_part(code) in (words or relation_words())


def _arg_signature(text: str) -> tuple[Counter, bool, list[str]]:
    """(counter of conversions by argument index, mixed sequential and positional, other problems)."""
    seen: Counter = Counter()
    sequential = 0
    explicit = False
    implicit = False
    other: list[str] = []
    for match in PLACEHOLDER.finditer(text):
        index, conversion = match.group(1), match.group(2)
        if conversion == "%":
            seen["%%"] += 1
        elif conversion == "s" or conversion == "d":
            if index is None:
                sequential += 1
                seen[(sequential, conversion)] += 1
                implicit = True
            else:
                seen[(int(index), conversion)] += 1
                explicit = True
        else:
            other.append(match.group(0))
            seen[match.group(0)] += 1
    return seen, explicit and implicit, other


def check_text(row: Row, text: str, code: str, words: dict | None = None) -> list[str]:
    """Everything wrong with one translation, as short messages; empty when the row passes."""
    problems: list[str] = []
    en = row["en"]
    if not isinstance(text, str) or text.strip() == "":
        return ["empty text"]
    if "�" in text:
        problems.append("contains the replacement character U+FFFD (broken encoding)")
    if any(ord(c) > 0xFFFF for c in text):
        problems.append("contains characters outside the Basic Multilingual Plane (emoji?), the game font cannot draw them")
    if any(unicodedata.category(c) == "Cc" and c != "\n" for c in text):
        problems.append("contains control characters")

    want, _, _ = _arg_signature(en)
    have, mixed, _ = _arg_signature(text)
    if mixed:
        problems.append("mixes %s and %1$s placeholders")
    elif want != have:
        problems.append(f"placeholders {placeholders(text)} do not match the English {placeholders(en)}")

    if Counter(HANGUL.findall(text)) != Counter(HANGUL.findall(en)):
        problems.append("Hangul added or removed (Korean is never translated)")
    if text.count("\n") != en.count("\n"):
        problems.append(f"{text.count(chr(10))} line break(s) instead of {en.count(chr(10))}")
    if Counter(re.findall(r"§.", text)) != Counter(re.findall(r"§.", en)):
        problems.append("formatting codes (§) differ from the English")
    if text[:len(text) - len(text.lstrip())] != en[:len(en) - len(en.lstrip())] or \
            text[len(text.rstrip()):] != en[len(en.rstrip()):]:
        problems.append("leading or trailing whitespace differs from the English")

    limit = row.get("maxLen")
    if limit and len(text) > limit:
        problems.append(f"{len(text)} characters, the limit is {limit}")

    problems += _untranslated(row, text, code)

    if row.get("formDependent"):
        word = giveaway(text, code, bool(ORDER_ROW.search(en)), words)
        if word:
            problems.append(f"gives away what the English hides ('{word}'); read-the-Korean clues must stay hidden")
    return problems


def _normal(text: str) -> str:
    return re.sub(r"[\W_]+", " ", PLACEHOLDER.sub(" ", text), flags=re.UNICODE).casefold().strip()


def _is_name(en: str) -> bool:
    """A title or proper name ('Ice Bucket Challenge'): no closing punctuation, every real word capitalised."""
    if re.search(r"[.!?:;,]\s*$", en.strip()):
        return False
    words = re.findall(r"[^\W\d_]+", PLACEHOLDER.sub(" ", en))
    return all(w[0].isupper() or w.lower() in NAME_PARTICLES for w in words)


def _untranslated(row: Row, text: str, code: str) -> list[str]:
    en = row["en"]
    out: list[str] = []
    words = [w for w in _normal(en).split() if w]
    if _normal(text) == _normal(en) and len(words) >= 3 and not _is_name(en):
        out.append("identical to the English source (not translated)")
        return out
    tokens = re.findall(r"[A-Za-z']+", PLACEHOLDER.sub(" ", text).lower())
    english = [t for t in tokens if t in ENGLISH_ONLY]
    if len(english) >= 2 and len(english) / len(tokens) >= 0.2:
        out.append(f"English left in the text ({', '.join(sorted(set(english)))})")
    script = SCRIPTS.get(language_part(code))
    letters = len(re.findall(r"[A-Za-z]", PLACEHOLDER.sub("", en)))
    if script and letters >= 4 and not script.search(text):
        out.append(f"no {language_part(code)} script in the text (not translated)")
    return out
