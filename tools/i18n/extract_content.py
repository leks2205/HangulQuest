#!/usr/bin/env python3
"""Move the English and German quest and word text out of the content JSON into lang files (WP1.4b).

Questline files and concepts.json used to carry `titleEn`/`titleDe`, `clue.en`/`clue.de`,
`hint.en`/`hint.de`, `finale.textEn`/`textDe` and the concepts' `en`, `de`, `sentenceDe`. Translators
should work in one flat file per language, so the text lives in

    src/main/resources/assets/hangulquest/lang/content/en_us.json
    src/main/resources/assets/hangulquest/lang/content/de_de.json

under the keys listed in content_text.py. This script moves it there and removes the fields from the
shipped JSON. The Korean fields, the ids and the structure are not touched: the files are written back
with the same layout (one space of indent, no ASCII escapes), so a diff shows only removed lines.

It is safe to run again. Run it after adding a trail by hand or with new_questline.py (that script
calls it for you). A key that already exists with another text stops the run, unless --force says the
inline text is the newer one.

    python3 tools/i18n/extract_content.py            move all text, rewrite the files
    python3 tools/i18n/extract_content.py --check    exit 1 when shipped JSON still has inline text
                                                     or a key has no trail, step or word (CI)
    python3 tools/i18n/extract_content.py FILE...    only these questline files (no concepts)
    python3 tools/i18n/extract_content.py --against REV
                                                     prove a move: the content files at revision REV,
                                                     minus their English/German fields, are byte-identical
                                                     to the files now, and every removed text is in the
                                                     lang files under its key
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import content_text as ct  # noqa: E402


def filled(value) -> bool:
    return isinstance(value, str) and value.strip() != ""


def take(container: dict, name: str):
    """Removes a field and returns its text when it has any."""
    value = container.pop(name, None)
    return value if filled(value) else None


def extract_trail(trail: dict) -> dict[str, dict[str, str]]:
    """Removes the inline text from a trail dict; returns {code: {key: text}}."""
    found: dict[str, dict[str, str]] = {code: {} for code in ct.LANGUAGES}
    tid = trail["id"]

    def put(code: str, key: str, text) -> None:
        if text is not None:
            found[code][key] = text

    for code, suffix in ct.SUFFIX.items():
        put(code, ct.title_key(tid), take(trail, "title" + suffix))
    finale = trail.get("finale")
    if isinstance(finale, dict):
        for code, suffix in ct.SUFFIX.items():
            put(code, ct.finale_key(tid), take(finale, "text" + suffix))
    # A root "textEn" is the older spelling of the same field; the game accepts it, the files do not use it.
    if "textEn" in trail:
        put("en_us", ct.finale_key(tid), take(trail, "textEn"))

    count = len(trail.get("steps", []))
    for i, step in enumerate(ct.trail_steps(trail)):
        sid = "finale" if i == count else ct.step_id(step)
        for field in ("clue", "hint"):
            block = step.get(field)
            if not isinstance(block, dict):
                continue
            for code, suffix in ct.SUFFIX.items():
                put(code, ct.step_key(tid, sid, field), take(block, suffix.lower()))
            if not block:
                del step[field]
    return found


def extract_concepts(concepts: list[dict]) -> dict[str, dict[str, str]]:
    found: dict[str, dict[str, str]] = {code: {} for code in ct.LANGUAGES}
    for concept in concepts:
        cid = concept["id"]
        for code, suffix in ct.SUFFIX.items():
            gloss = take(concept, suffix.lower())
            if gloss is not None:
                found[code][ct.word_key(cid, "gloss")] = gloss
            sentence = take(concept, "sentence" + suffix)
            if sentence is not None:
                found[code][ct.word_key(cid, "sentence")] = sentence
    return found


def dump_json(data) -> str:
    """The layout the content files already have: one space of indent, UTF-8 as is, final newline."""
    return json.dumps(data, indent=1, ensure_ascii=False) + "\n"


def canonical_order(trails: list[dict], concepts: list[dict], keys: set[str]) -> list[str]:
    """Trails (sorted by file), then words (file order); keys nobody owns come last, sorted."""
    ordered: list[str] = []
    for trail in trails:
        ordered += [key for key in ct.trail_key_list(trail) if key in keys]
    for concept in concepts:
        ordered += [key for key in ct.concept_key_list(concept) if key in keys]
    known = set(ordered)
    return ordered + sorted(keys - known)


def run(files: list[Path] | None, concepts_too: bool, force: bool, check: bool) -> int:
    paths = files if files else ct.trail_files()
    everything = files is None
    bundles = ct.load_bundles()
    existing = {code: dict(bundle) for code, bundle in bundles.items()}
    problems: list[str] = []
    trail_docs: list[dict] = []
    pending_writes: list[tuple[Path, str]] = []
    moved = {code: 0 for code in ct.LANGUAGES}

    def merge(found: dict[str, dict[str, str]], source: str) -> None:
        for code, texts in found.items():
            for key, text in texts.items():
                old = bundles[code].get(key)
                if old is not None and old != text and not force:
                    problems.append(f"{source}: {key} ({code}) is already in the lang file with other text")
                    continue
                if old != text:
                    moved[code] += 1
                bundles[code][key] = text

    for path in paths:
        raw = path.read_text(encoding="utf-8")
        trail = json.loads(raw)
        found = extract_trail(trail)
        inline = sum(len(texts) for texts in found.values())
        if check and inline:
            problems.append(f"{path.name}: {inline} inline English/German text(s) left; run tools/i18n/extract_content.py")
        merge(found, path.name)
        out = dump_json(trail)
        if out != raw:
            pending_writes.append((path, out))
        trail_docs.append(trail)

    concepts: list[dict] = []
    concepts_path = ct.DATA / "concepts.json"
    if concepts_too and everything:
        raw = concepts_path.read_text(encoding="utf-8")
        concepts = json.loads(raw)
        found = extract_concepts(concepts)
        inline = sum(len(texts) for texts in found.values())
        if check and inline:
            problems.append(f"concepts.json: {inline} inline gloss/sentence text(s) left; run tools/i18n/extract_content.py")
        merge(found, "concepts.json")
        out = dump_json(concepts)
        if out != raw:
            pending_writes.append((concepts_path, out))
    else:
        concepts = ct.read_json(concepts_path)

    # Owners: every shipped trail and concept, not only the ones this run touched (ids and steps are all
    # that matters here, so the text still being inline in a file this run skips does not matter).
    owner_trails = trail_docs if everything else [ct.read_json(p) for p in ct.trail_files()]
    all_keys = {key for bundle in bundles.values() for key in bundle}
    order = canonical_order(owner_trails, concepts, all_keys)
    if check:
        owned = {key for trail in owner_trails for key in ct.trail_key_list(trail)}
        owned |= {key for concept in concepts for key in ct.concept_key_list(concept)}
        for key in order:
            if key not in owned:
                problems.append(f"{key}: no trail, step or word has this key")
        for code in ct.LANGUAGES:
            if not bundles[code]:
                problems.append(f"{code}: no content text at all")
        for problem in problems:
            print(problem, file=sys.stderr)
        if problems:
            return 1
        print(f"content text: {len(order)} keys, nothing inline")
        return 0

    if problems:
        for problem in problems:
            print(problem, file=sys.stderr)
        print("nothing written; use --force when the inline text is the newer one", file=sys.stderr)
        return 1

    for code in ct.LANGUAGES:
        ordered = {key: bundles[code][key] for key in order if key in bundles[code]}
        if ordered != existing[code] or list(ordered) != list(existing[code]):
            ct.write_bundle(code, ordered)
    for path, text in pending_writes:
        path.write_text(text, encoding="utf-8")
    print(f"moved {moved['en_us']} English and {moved['de_de']} German texts; "
          f"{len(pending_writes)} content file(s) rewritten; "
          f"lang files hold {len(bundles['en_us'])} English and {len(bundles['de_de'])} German keys")
    return 0


def old_file(rev: str, path: Path) -> str:
    relative = path.relative_to(ct.ROOT).as_posix()
    return subprocess.run(["git", "-C", str(ct.ROOT), "show", f"{rev}:{relative}"],
                          check=True, capture_output=True, encoding="utf-8").stdout


def verify(rev: str) -> int:
    """Compares the content files at `rev` with the files and lang texts now."""
    bundles = ct.load_bundles()
    bad: list[str] = []
    texts_seen = {code: 0 for code in ct.LANGUAGES}
    sources = [(path, False) for path in ct.trail_files()] + [(ct.DATA / "concepts.json", True)]
    for path, is_concepts in sources:
        doc = json.loads(old_file(rev, path))
        found = extract_concepts(doc) if is_concepts else extract_trail(doc)
        if dump_json(doc) != path.read_text(encoding="utf-8"):
            bad.append(f"{path.name}: differs from the old file once its English/German fields are removed")
        for code, texts in found.items():
            for key, text in texts.items():
                texts_seen[code] += 1
                if bundles[code].get(key) != text:
                    bad.append(f"{path.name}: {key} ({code}) is not in the lang file as it was")
    # Nothing new may have appeared: the lang files hold exactly the texts the old files had.
    for code in ct.LANGUAGES:
        if texts_seen[code] != len(bundles[code]):
            bad.append(f"{code}: lang file has {len(bundles[code])} keys, the old files had {texts_seen[code]} texts")
    for problem in bad:
        print(problem, file=sys.stderr)
    if bad:
        return 1
    print(f"{len(sources)} files match revision {rev} apart from the removed text; "
          f"{texts_seen['en_us']} English and {texts_seen['de_de']} German texts are in the lang files unchanged")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", nargs="*", type=Path, help="questline files to extract (default: all, and concepts.json)")
    parser.add_argument("--check", action="store_true", help="change nothing; exit 1 when text is still inline")
    parser.add_argument("--against", metavar="REV", help="verify a move against the content at this revision")
    parser.add_argument("--force", action="store_true", help="inline text replaces a different text under the same key")
    args = parser.parse_args(argv)
    if args.against:
        return verify(args.against)
    files = [path.resolve() for path in args.files] or None
    return run(files, concepts_too=True, force=args.force, check=args.check)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
