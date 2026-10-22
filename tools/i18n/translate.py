#!/usr/bin/env python3
"""The in-repo translation pipeline: Gemini drafts, Claude proofreads, GitHub corrects.

English is the source language and Korean is never translated. The text of a language is two flat
files, `lang/<code>.json` (interface, 592 keys) and `lang/content/<code>.json` (trail, clue, hint,
finale and word text), written in the key order of the English files.

    translate.py export  --lang es_es --out DIR [--changed-only] [--chunk 250] [--part ui|content]
        Packets of rows to translate, in DIR (outside the repository): <code>-001.json, ...
        --changed-only: only the keys that are missing or stale.
    translate.py import  --lang es_es RESULTS.jsonl [...] [--rejects FILE] [--dry-run]
        Checks every row ({"key":..., "text":...} per line), writes the good ones as state `machine`.
    translate.py proofread-export --lang es_es --out DIR [--chunk 250]
        Packets of the `machine` rows, each with its draft.
    translate.py proofread-import --lang es_es VERDICTS.jsonl [--by claude]
        {"key":..., "verdict":"ok"} or {"key":..., "verdict":"fix", "text":...}; state becomes `proofread`.
    translate.py status [--lang es_es] [--json]     coverage and state counts
    translate.py sync   [--lang es_es]              write what a status derives from the files (stale, hand edits)
    translate.py adopt  --lang de_de --state proofread    record text that has no status yet
    translate.py approve --lang es_es --by LOGIN KEY...   community approval of keys
    translate.py ship   --lang es_es [--name NAME]        list the language in data/languages.json
    translate.py fonts  [--lang es_es]              characters the default font cannot draw (needs a local asset cache)
    translate.py check  [--lang es_es]              CI: status files and shipped rows pass the import rules

States of a key: machine (Gemini draft) -> proofread (Claude checked) -> community-approved (approved or
edited through GitHub). `stale` is a translation whose English changed; `untracked` is text with no
status yet; `missing` has no text. See tr_core.py for how the states are derived.
"""

from __future__ import annotations

import argparse
import json
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import tr_check  # noqa: E402
import tr_core as core  # noqa: E402
import tr_fonts  # noqa: E402
import tr_glossary  # noqa: E402

HERE = Path(__file__).resolve().parent
NOT_TARGETS = ("en_us", "ko_kr")
PACKET_RULES = [
    "Translate the English text in `en` into the target language. Korean is never translated: keep any Hangul "
    "exactly as it is and do not add any.",
    "`ko` is the Korean line the game shows next to this text. It is context only: do not copy or translate it. "
    "`koContext` explains a word (its description and an example sentence).",
    "Keep every placeholder (%s, %1$s, %%) and every line break (\\n). You may reorder placeholders with "
    "%1$s, %2$s when the grammar needs it, but use each exactly once.",
    "Use the official Minecraft names from `glossary` for items, blocks, mobs and biomes.",
    "Interface labels (`maxLen`) must stay within that many characters; abbreviate rather than overflow.",
    "A row with `formDependent` hides on purpose where, what or in which order. Say the same thing in the same "
    "way and do not name a position, a relation or an order.",
    "Natural, everyday wording for a game; no explanations, no notes inside the text.",
]
OUTPUT_RULES = {
    "translate": 'Answer with JSON Lines only, one object per row and nothing else: {"key": "...", "text": "..."}. '
                 "Copy `key` exactly. Optionally copy `src` too.",
    "proofread": 'Answer with JSON Lines only, one object per row: {"key": "...", "verdict": "ok"} when the draft is '
                 'correct and natural, or {"key": "...", "verdict": "fix", "text": "the corrected text", '
                 '"note": "why"}. Copy `key` exactly. Fix only real errors (meaning, grammar, wrong Minecraft name, '
                 "given-away form), not taste.",
}


def die(message: str) -> "NoReturn":
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(2)


def language_info(code: str) -> dict:
    return core.read_json(HERE / "language_names.json", {}).get(code, {"english": code, "native": code})


def target_code(value: str) -> str:
    code = value.strip().lower().replace("-", "_")
    if code in NOT_TARGETS:
        die(f"{code} is not a translation target (English is the source, Korean is never translated)")
    if not code or not all(part.isalnum() for part in code.split("_")):
        die(f"not a language code: {value}")
    return code


def outside_repo(repo: core.Repo, out: Path) -> Path:
    out = out.resolve()
    if out == repo.root.resolve() or repo.root.resolve() in out.parents:
        die(f"{out} is inside the repository; packets are working files, put them outside")
    out.mkdir(parents=True, exist_ok=True)
    return out


def strip_row(row: core.Row) -> dict:
    """A row as it goes into a packet: no empty fields and not the file it comes from."""
    return {k: v for k, v in row.items() if k != "file" and v not in ("", None, [], False)}


def write_packets(out: Path, code: str, mode: str, rows: list[dict], chunk: int,
                  glossary: tr_glossary.Glossary | None) -> list[Path]:
    info = language_info(code)
    chunks = [rows[i:i + chunk] for i in range(0, len(rows), chunk)]
    paths = []
    for n, part in enumerate(chunks, 1):
        names = glossary.for_texts([row["en"] for row in part]) if glossary else {}
        packet = {
            "schema": 1, "mode": mode, "lang": code, "language": info["english"], "packet": n, "packets": len(chunks),
            "rules": PACKET_RULES + [OUTPUT_RULES[mode]], "kinds": {k: core.KIND_NOTES[k] for k in sorted({r["kind"] for r in part})},
            "glossary": names, "rows": part,
        }
        path = out / f"{code}-{mode if mode == 'proofread' else 'tr'}-{n:03d}.json"
        path.write_text(json.dumps(packet, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        paths.append(path)
    return paths


def load_glossary(args, code: str) -> tr_glossary.Glossary | None:
    if args.no_glossary:
        return None
    roots = [Path(args.assets).expanduser()] if args.assets else None
    return tr_glossary.load(code, roots, args.mc_version)


# Export ---------------------------------------------------------------------------------------

def cmd_export(args) -> int:
    repo = core.Repo(args.root)
    code = target_code(args.lang)
    out = outside_repo(repo, Path(args.out))
    rows = core.source_rows(repo)
    bundle = core.merged_bundle(repo, code)
    status = core.read_status(repo, code)["keys"]
    todo = []
    for row in rows:
        if args.part and row["file"] != args.part:
            continue
        state = core.effective_state(status.get(row["key"]), row, bundle.get(row["key"]))
        if args.changed_only and state not in ("missing", "stale"):
            continue
        todo.append(strip_row(row))
    if not todo:
        print(f"nothing to translate for {code}")
        return 0
    paths = write_packets(out, code, "translate", todo, args.chunk, load_glossary(args, code))
    no_ko = sum(1 for row in todo if "ko" not in row)
    print(f"{len(todo)} row(s) in {len(paths)} packet(s) in {out} ({no_ko} without a Korean line)")
    return 0


# Reading result files -------------------------------------------------------------------------

def read_results(paths: list[str]) -> tuple[list[dict], list[str]]:
    """JSON Lines (or one JSON array) of objects; fenced code blocks are tolerated. (rows, errors)"""
    rows: list[dict] = []
    errors: list[str] = []
    for name in paths:
        text = Path(name).read_text(encoding="utf-8").strip()
        if text.startswith("["):
            try:
                loaded = json.loads(text)
            except ValueError as failed:
                errors.append(f"{name}: not valid JSON ({failed})")
                continue
            rows += [item for item in loaded if isinstance(item, dict)]
            continue
        for number, line in enumerate(text.splitlines(), 1):
            line = line.strip()
            if not line or line.startswith("```"):
                continue
            try:
                item = json.loads(line)
            except ValueError:
                errors.append(f"{name}:{number}: not a JSON object, skipped")
                continue
            if isinstance(item, dict):
                rows.append(item)
            else:
                errors.append(f"{name}:{number}: not a JSON object, skipped")
    return rows, errors


def result_text(item: dict) -> str | None:
    for name in ("text", "translation"):
        value = item.get(name)
        if isinstance(value, str):
            return unicode_nfc(value)
    return None


def unicode_nfc(text: str) -> str:
    return unicodedata.normalize("NFC", text)


def report_rejects(rejects: list[dict], path: str | None) -> None:
    for item in rejects[:40]:
        print(f"  rejected {item['key']}: {'; '.join(item['problems'])}", file=sys.stderr)
    if len(rejects) > 40:
        print(f"  ... and {len(rejects) - 40} more", file=sys.stderr)
    if path:
        Path(path).write_text("".join(json.dumps(item, ensure_ascii=False) + "\n" for item in rejects), encoding="utf-8")


def validate_item(item: dict, rows: dict[str, core.Row], code: str, words: dict) -> tuple[str | None, list[str]]:
    """(text to write, problems) for one result row."""
    key = item.get("key")
    row = rows.get(key) if isinstance(key, str) else None
    if row is None:
        return None, ["unknown key (not a translatable key of the English files)"]
    text = result_text(item)
    if text is None:
        return None, ["no `text`"]
    if item.get("src") and item["src"] != row["src"]:
        return None, ["the English changed since the packet was exported (src differs)"]
    problems = tr_check.check_text(row, text, code, words)
    if row["formDependent"] and not tr_check.has_list(code, words):
        problems.append(f"no relation word list for {code}; add one to tools/i18n/relation_words.json")
    return (None if problems else text), problems


def apply_texts(repo: core.Repo, code: str, texts: dict[str, str], rows: dict[str, core.Row]) -> None:
    """Writes the texts into the two language files, in English key order."""
    for content in (False, True):
        mine = {key: text for key, text in texts.items() if rows[key].content == content}
        if not mine:
            continue
        bundle = core.read_bundle(repo, code, content)
        bundle.update(mine)
        core.write_bundle(repo, code, content, bundle, core.english_order(repo, content))


def write_status_for(repo: core.Repo, code: str, status: dict) -> None:
    order = core.english_order(repo, False) + core.english_order(repo, True)
    core.write_status(repo, code, status, order)


def cmd_import(args) -> int:
    repo = core.Repo(args.root)
    code = target_code(args.lang)
    rows = core.row_index(core.source_rows(repo))
    words = tr_check.relation_words()
    items, errors = read_results(args.results)
    for error in errors:
        print(error, file=sys.stderr)
    accepted: dict[str, str] = {}
    rejects: list[dict] = []
    for item in items:
        text, problems = validate_item(item, rows, code, words)
        if text is None:
            rejects.append({"key": item.get("key"), "text": item.get("text"), "problems": problems})
        else:
            if item["key"] in accepted:
                print(f"note: {item['key']} appears twice, the last one wins", file=sys.stderr)
            accepted[item["key"]] = text
    print(f"{code}: {len(accepted)} row(s) accepted, {len(rejects)} rejected, {len(items)} read")
    report_rejects(rejects, args.rejects)
    if args.dry_run or not accepted:
        return 0
    apply_texts(repo, code, accepted, rows)
    status = core.read_status(repo, code)
    for key, text in accepted.items():
        status["keys"][key] = core.new_entry(rows[key], text, "machine", [args.by])
    write_status_for(repo, code, status)
    return 0


# Proofreading ---------------------------------------------------------------------------------

def cmd_proofread_export(args) -> int:
    repo = core.Repo(args.root)
    code = target_code(args.lang)
    out = outside_repo(repo, Path(args.out))
    bundle = core.merged_bundle(repo, code)
    status = core.read_status(repo, code)["keys"]
    todo = []
    for row in core.source_rows(repo):
        text = bundle.get(row["key"])
        if core.effective_state(status.get(row["key"]), row, text) != "machine":
            continue
        packet_row = strip_row(row)
        packet_row["draft"] = text
        packet_row["draftHash"] = core.text_hash(text)
        todo.append(packet_row)
    if not todo:
        print(f"nothing to proofread for {code}")
        return 0
    paths = write_packets(out, code, "proofread", todo, args.chunk, load_glossary(args, code))
    print(f"{len(todo)} draft(s) in {len(paths)} packet(s) in {out}")
    return 0


def cmd_proofread_import(args) -> int:
    repo = core.Repo(args.root)
    code = target_code(args.lang)
    rows = core.row_index(core.source_rows(repo))
    words = tr_check.relation_words()
    bundle = core.merged_bundle(repo, code)
    status = core.read_status(repo, code)
    items, errors = read_results(args.results)
    for error in errors:
        print(error, file=sys.stderr)
    oks, fixes, rejects = 0, 0, []
    new_texts: dict[str, str] = {}
    for item in items:
        key = item.get("key")
        row = rows.get(key) if isinstance(key, str) else None
        verdict = item.get("verdict")
        problems: list[str] = []
        if row is None:
            problems.append("unknown key")
        elif verdict not in ("ok", "fix"):
            problems.append("verdict must be ok or fix")
        else:
            entry = status["keys"].get(key)
            state = core.effective_state(entry, row, bundle.get(key))
            if state != "machine" and not args.force:
                problems.append(f"state is {state}, only machine drafts are proofread")
            elif item.get("draftHash") and item["draftHash"] != core.text_hash(bundle.get(key, "")):
                problems.append("the draft changed since the packet was exported (draftHash differs)")
        text = None
        if not problems and verdict == "fix":
            text, problems = validate_item(item, rows, code, words)
        if problems:
            rejects.append({"key": key, "verdict": verdict, "problems": problems})
            continue
        entry = status["keys"].get(key) or core.new_entry(row, bundle[key], "machine", [])
        entry["reviewers"] = sorted(set(entry.get("reviewers", [])) | {args.by})
        entry["state"] = "proofread"
        entry.pop("was", None)
        if verdict == "fix":
            fixes += 1
            new_texts[key] = text
            entry["txt"] = core.text_hash(text)
        else:
            oks += 1
            entry["txt"] = core.text_hash(bundle[key])
        entry["src"] = row["src"]
        status["keys"][key] = entry
    print(f"{code}: {oks} ok, {fixes} fixed, {len(rejects)} rejected, {len(items)} read")
    report_rejects(rejects, args.rejects)
    if args.dry_run or not (oks or fixes):
        return 0
    apply_texts(repo, code, new_texts, rows)
    write_status_for(repo, code, status)
    return 0


# Status, sync, adopt, approve -----------------------------------------------------------------

def state_counts(repo: core.Repo, code: str, rows: list[core.Row]) -> dict:
    bundle = core.merged_bundle(repo, code)
    status = core.read_status(repo, code)["keys"]
    counts = {name: 0 for name in core.STATES + core.EXTRA_STATES}
    by_file = {"ui": [0, 0], "content": [0, 0]}  # [rows, translated]
    for row in rows:
        text = bundle.get(row["key"])
        state = core.effective_state(status.get(row["key"]), row, text)
        counts[state] += 1
        by_file[row["file"]][0] += 1
        by_file[row["file"]][1] += 1 if state != "missing" else 0
    total = len(rows)
    present = total - counts["missing"]
    orphans = [key for key in bundle if key not in {row["key"] for row in rows}
               and not core.SENTENCE_KEY.match(key)]
    return {"lang": code, "rows": total, "translated": present, "coverage": round(100 * present / total, 1) if total else 0.0,
            "states": counts, "ui": by_file["ui"], "content": by_file["content"], "orphans": len(orphans)}


def cmd_status(args) -> int:
    repo = core.Repo(args.root)
    rows = core.source_rows(repo)
    codes = [target_code(args.lang)] if args.lang else core.languages_in_repo(repo)
    results = [state_counts(repo, code, rows) for code in codes]
    if args.json:
        print(json.dumps(results, ensure_ascii=False, indent=1))
        return 0
    if not results:
        print("no translations yet")
        return 0
    states = core.STATES + core.EXTRA_STATES
    head = f"{'lang':8} {'rows':>6} {'done':>6} {'%':>6} " + " ".join(f"{name[:9]:>9}" for name in states) + "  ui/content done"
    print(head)
    for item in results:
        counts = " ".join(f"{item['states'][name]:>9}" for name in states)
        ui, content = item["ui"], item["content"]
        print(f"{item['lang']:8} {item['rows']:>6} {item['translated']:>6} {item['coverage']:>6} {counts}  "
              f"{ui[1]}/{ui[0]} {content[1]}/{content[0]}" + (f"  ({item['orphans']} orphan key(s))" if item["orphans"] else ""))
    return 0


def sync_language(repo: core.Repo, code: str, rows: dict[str, core.Row], prune: bool) -> dict:
    """Makes the status file say what the files say: stale after an English change, community-approved
    after a hand edit, entries without text dropped. Returns the number of changes per kind."""
    bundle = core.merged_bundle(repo, code)
    status = core.read_status(repo, code)
    changes = {"stale": 0, "edited": 0, "dropped": 0, "pruned": 0}
    for key in list(status["keys"]):
        entry = status["keys"][key]
        row = rows.get(key)
        text = bundle.get(key)
        if row is None or not text:
            del status["keys"][key]
            changes["dropped"] += 1
            continue
        state = core.effective_state(entry, row, text)
        if state == "stale" and entry.get("state") != "stale":
            entry["was"] = entry.get("state", "machine")
            entry["state"] = "stale"
            changes["stale"] += 1
        elif state == "community-approved" and (entry.get("txt") != core.text_hash(text) or entry.get("state") != state):
            entry["state"] = state
            entry["txt"] = core.text_hash(text)
            entry["reviewers"] = sorted(set(entry.get("reviewers", [])) | {"github"})
            entry.pop("was", None)
            changes["edited"] += 1
        elif entry.get("state") == "stale" and state != "stale":
            entry["state"] = state
            entry.pop("was", None)
    if prune:
        for content in (False, True):
            texts = core.read_bundle(repo, code, content)
            keep = {key: text for key, text in texts.items() if key in rows or core.SENTENCE_KEY.match(key)}
            if len(keep) != len(texts):
                changes["pruned"] += len(texts) - len(keep)
                core.write_bundle(repo, code, content, keep, core.english_order(repo, content))
    write_status_for(repo, code, status)
    return changes


def cmd_sync(args) -> int:
    repo = core.Repo(args.root)
    rows = core.row_index(core.source_rows(repo))
    codes = [target_code(args.lang)] if args.lang else [c for c in core.languages_in_repo(repo) if repo.status_path(c).is_file()]
    for code in codes:
        changes = sync_language(repo, code, rows, args.prune)
        print(f"{code}: " + ", ".join(f"{count} {name}" for name, count in changes.items()))
    return 0


def cmd_adopt(args) -> int:
    repo = core.Repo(args.root)
    code = target_code(args.lang)
    if args.state not in core.STATES[:3]:
        die("state must be machine, proofread or community-approved")
    rows = core.row_index(core.source_rows(repo))
    bundle = core.merged_bundle(repo, code)
    status = core.read_status(repo, code)
    added = 0
    for key, row in rows.items():
        if bundle.get(key) and key not in status["keys"]:
            status["keys"][key] = core.new_entry(row, bundle[key], args.state, [args.by] if args.by else [])
            added += 1
    write_status_for(repo, code, status)
    print(f"{code}: {added} key(s) recorded as {args.state}")
    return 0


def cmd_approve(args) -> int:
    repo = core.Repo(args.root)
    code = target_code(args.lang)
    rows = core.row_index(core.source_rows(repo))
    bundle = core.merged_bundle(repo, code)
    status = core.read_status(repo, code)
    bad = 0
    for key in args.keys:
        row = rows.get(key)
        entry = status["keys"].get(key)
        state = core.effective_state(entry, row, bundle.get(key)) if row else "unknown"
        if state in ("stale", "missing", "untracked", "unknown"):
            print(f"{key}: cannot approve a {state} key", file=sys.stderr)
            bad += 1
            continue
        entry = entry or core.new_entry(row, bundle[key], state, [])
        entry["state"] = "community-approved"
        entry["src"] = row["src"]
        entry["txt"] = core.text_hash(bundle[key])
        entry["reviewers"] = sorted(set(entry.get("reviewers", [])) | {f"github:{args.by}"})
        entry.pop("was", None)
        status["keys"][key] = entry
    write_status_for(repo, code, status)
    print(f"{code}: {len(args.keys) - bad} approved, {bad} refused")
    return 1 if bad else 0


# Ship and check -------------------------------------------------------------------------------

def cmd_ship(args) -> int:
    repo = core.Repo(args.root)
    code = target_code(args.lang)
    for content in (False, True):
        if not core.read_bundle(repo, code, content):
            die(f"{repo.lang_path(code, content).name} in {'lang/content' if content else 'lang'} is missing or empty; "
                "a listed language needs both files")
    index = core.read_json(repo.languages_json, {})
    name = args.name or language_info(code)["native"]
    if name == code:
        die("no native name known for this code; pass --name")
    index[code] = name
    repo.languages_json.write_text(core.dump_bundle(index), encoding="utf-8")
    print(f"{code} is listed as {name!r} in data/languages.json")
    return 0


def cmd_fonts(args) -> int:
    repo = core.Repo(args.root)
    roots = [Path(args.assets).expanduser()] if args.assets else tr_glossary.default_roots()
    covered = tr_fonts.unifont_coverage(roots, args.mc_version)
    if covered is None:
        print("note: no local asset cache with minecraft/font/unifont.zip; nothing checked", file=sys.stderr)
        return 0
    codes = [target_code(args.lang)] if args.lang else core.languages_in_repo(repo)
    names = list((core.read_json(repo.languages_json, {}) or {}).values())
    bad = 0
    for code in codes:
        missing = tr_fonts.missing_characters(list(core.merged_bundle(repo, code).values()), covered)
        print(f"{code}: " + (", ".join(f"{c!r} x{n}" for c, n in missing.most_common(20)) if missing else "every character has a glyph"))
        bad += bool(missing)
    missing = tr_fonts.missing_characters(names, covered)
    if missing:
        print("language names: " + ", ".join(f"{c!r} x{n}" for c, n in missing.items()))
        bad += 1
    return 1 if bad else 0


def cmd_check(args) -> int:
    repo = core.Repo(args.root)
    rows = core.row_index(core.source_rows(repo))
    words = tr_check.relation_words()
    codes = [target_code(args.lang)] if args.lang else [c for c in core.languages_in_repo(repo) if repo.status_path(c).is_file()]
    problems: list[str] = []
    checked = 0
    for code in codes:
        status = core.read_status(repo, code)
        bundle = core.merged_bundle(repo, code)
        for key, entry in status["keys"].items():
            if key not in rows:
                problems.append(f"{code}: status for {key}, which is not a translatable key")
            if entry.get("state") not in core.STATES:
                problems.append(f"{code}: {key} has the state {entry.get('state')!r}")
        for key, text in bundle.items():
            row = rows.get(key)
            if row is None:
                if not core.SENTENCE_KEY.match(key):
                    problems.append(f"{code}: {key} is not a key of the English files")
                continue
            checked += 1
            found = tr_check.check_text(row, text, code, words)
            if row["formDependent"] and not tr_check.has_list(code, words):
                found.append(f"no relation word list for {code}")
            problems += [f"{code}: {key}: {problem}" for problem in found]
    for problem in problems[:60]:
        print(problem, file=sys.stderr)
    if len(problems) > 60:
        print(f"... and {len(problems) - 60} more", file=sys.stderr)
    if problems:
        return 1
    print(f"translations: {len(codes)} managed language(s), {checked} row(s) pass")
    return 0


# Command line ---------------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=str(core.ROOT), help=argparse.SUPPRESS)
    sub = parser.add_subparsers(dest="command", required=True)

    def packets(p):
        p.add_argument("--lang", required=True)
        p.add_argument("--out", required=True, help="folder for the packets, outside the repository")
        p.add_argument("--chunk", type=int, default=250, help="rows per packet")
        p.add_argument("--assets", help="asset cache with the game's language files (default: Loom cache, ~/.minecraft/assets)")
        p.add_argument("--mc-version", default="26.3")
        p.add_argument("--no-glossary", action="store_true")

    p = sub.add_parser("export", help="packets of rows to translate")
    packets(p)
    p.add_argument("--changed-only", action="store_true", help="only missing and stale keys")
    p.add_argument("--part", choices=("ui", "content"), help="only the interface file or only the content file")
    p.set_defaults(run=cmd_export)

    p = sub.add_parser("import", help="merge translated rows")
    p.add_argument("--lang", required=True)
    p.add_argument("results", nargs="+")
    p.add_argument("--by", default="gemini", help="who wrote the draft")
    p.add_argument("--rejects", help="write the rejected rows and their reasons here (JSON Lines)")
    p.add_argument("--dry-run", action="store_true")
    p.set_defaults(run=cmd_import)

    p = sub.add_parser("proofread-export", help="packets of machine drafts to proofread")
    packets(p)
    p.set_defaults(run=cmd_proofread_export)

    p = sub.add_parser("proofread-import", help="apply ok / fix verdicts")
    p.add_argument("--lang", required=True)
    p.add_argument("results", nargs="+")
    p.add_argument("--by", default="claude")
    p.add_argument("--force", action="store_true", help="also proofread rows that are not in state machine")
    p.add_argument("--rejects")
    p.add_argument("--dry-run", action="store_true")
    p.set_defaults(run=cmd_proofread_import)

    p = sub.add_parser("status", help="coverage and state counts")
    p.add_argument("--lang")
    p.add_argument("--json", action="store_true")
    p.set_defaults(run=cmd_status)

    p = sub.add_parser("sync", help="write stale and edited states into the status files")
    p.add_argument("--lang")
    p.add_argument("--prune", action="store_true", help="also delete keys that are no longer in the English files")
    p.set_defaults(run=cmd_sync)

    p = sub.add_parser("adopt", help="record existing text that has no status")
    p.add_argument("--lang", required=True)
    p.add_argument("--state", required=True)
    p.add_argument("--by")
    p.set_defaults(run=cmd_adopt)

    p = sub.add_parser("approve", help="community approval of keys")
    p.add_argument("--lang", required=True)
    p.add_argument("--by", required=True, help="GitHub login of the approver")
    p.add_argument("keys", nargs="+")
    p.set_defaults(run=cmd_approve)

    p = sub.add_parser("ship", help="list a language in data/languages.json")
    p.add_argument("--lang", required=True)
    p.add_argument("--name", help="the language's own name (default from language_names.json)")
    p.set_defaults(run=cmd_ship)

    p = sub.add_parser("fonts", help="characters the game's default font (Unifont) cannot draw, from a local asset cache")
    p.add_argument("--lang")
    p.add_argument("--assets")
    p.add_argument("--mc-version", default="26.3")
    p.set_defaults(run=cmd_fonts)

    p = sub.add_parser("check", help="CI: managed languages pass the rules")
    p.add_argument("--lang")
    p.set_defaults(run=cmd_check)
    return parser


def main(argv: list[str]) -> int:
    args = build_parser().parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
