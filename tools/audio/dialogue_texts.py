#!/usr/bin/env python3
"""Write the villager lines of a repo checkout as JSONL {id, ko, voices} for render.py, one row per line and voice.

  dialogue_texts.py [--repo <checkout>] [--out <file>] [--strict]

Reads assets/hangulquest/dialogue/*.json, assets/hangulquest/requests/*.json, data/voices.json (personality ->
voice) and data/villager-names.json (the name pools) of the checkout. The last two come with the 3.1 engine; without
them the voices come from tools/audio/voices.json and no self-introductions are written (a clear message says so;
--strict makes that an error).

Event ids (PLAN "Audio"; they become hangulquest:<id> sound events, one clip each):
  line.<dialogueId>.<nodeId>.<voice>            an NPC line
  line.<dialogueId>.<nodeId>.banmal.<voice>     its ko_banmal variant (villagers who switched to 반말)
  line.req_<requestId>.<nodeId>.<voice>         a request line (offer, accept, decline, reminder, thanks, ...)
  line.name.<nameRomanised>.<voice>             the self-introduction "저는 {name}이에요/예요."

Which voices say a line:
  - the speaker is a child, or the dialogue is for babies          -> both child voices
  - the speaker has a fixed persona voice (elder, trader, golem, librarian; voices.json "personas")
                                                                   -> that voice
  - the dialogue/request/variant is for some personalities         -> their voices
  - anything else (shared, e.g. the profession dialogues)          -> all 8 adult personality voices
  - ko_banmal variants only in the voices of the personalities that can switch to 반말 (LAZY, JOCK, PEPPY, SISTERLY)
  - a per-personality variant replaces the shared line in that personality's voice
Player choices are not voiced. A line with a {placeholder} cannot be rendered ahead and is reported.
"""
import argparse
import json
import re
import sys
from pathlib import Path

from common import REPO, WORK, load_voices

ASSETS = Path("src/main/resources/assets/hangulquest")
PERSONALITIES = ("LAZY", "JOCK", "CRANKY", "SMUG", "NORMAL", "PEPPY", "SNOOTY", "SISTERLY")
MALE = ("LAZY", "JOCK", "CRANKY", "SMUG")
BANMAL_OK = ("LAZY", "JOCK", "PEPPY", "SISTERLY")
CHILD_SPEAKERS = ("child",)
# containers that hold lines but are not a line's name
STRUCTURAL = {"nodes", "lines", "variants", "byPersonality", "personalities", "dialogue", "dialogues", "default",
              "text", "speech"}
# never lines: what the player says or types, goals, effects, trigger
NOT_LINES = {"choices", "typed", "effects", "goal", "trigger", "words", "options", "player"}
SAFE = re.compile(r"[a-z0-9_]+")


class ContentError(Exception):
    pass


# ---------------------------------------------------------------- voices

class VoiceTable:
    """personality -> voice, child voices by gender, speaker -> fixed voice."""

    def __init__(self, personality, child, persona):
        self.personality = personality
        self.child = child
        self.persona = persona

    @property
    def adults(self):
        return [self.personality[p] for p in PERSONALITIES if p in self.personality]

    @property
    def children(self):
        return [self.child[g] for g in ("male", "female") if g in self.child]

    def of(self, personalities):
        return [self.personality[p] for p in PERSONALITIES if p in personalities and p in self.personality]

    def by_gender(self, gender):
        pers = MALE if gender == "male" else tuple(p for p in PERSONALITIES if p not in MALE)
        return self.of(pers) + ([self.child[gender]] if gender in self.child else [])

    def all(self):
        return set(self.adults) | set(self.children) | set(self.persona.values())


def table_from_tools(cfg):
    personality, child = {}, {}
    for vid, v in cfg["voices"].items():
        if v.get("personality"):
            personality[v["personality"]] = vid
        elif v.get("age") == "child":
            child[v["gender"]] = vid
    persona = {name: p["voice"] for name, p in cfg.get("personas", {}).items()}
    return VoiceTable(personality, child, persona)


def _voice_of(value):
    if isinstance(value, str):
        return value
    if isinstance(value, dict):
        return value.get("voice") or value.get("id")
    return None


def table_from_engine(data, fallback):
    """data/voices.json of the engine. Accepts personality ids as keys anywhere at the top or one level down
    ({"personalities": {"LAZY": "v_lazy"}} or {"LAZY": {"voice": "v_lazy"}}), child voices under "child"/"children"/
    "baby" keyed male/female/boy/girl, fixed speakers under "speakers"/"personas". What it leaves out comes from
    tools/audio/voices.json."""
    personality, child, persona = dict(fallback.personality), dict(fallback.child), dict(fallback.persona)
    scopes = [data] + [v for v in data.values() if isinstance(v, dict)]
    for scope in scopes:
        for key, value in scope.items():
            if key.upper() in PERSONALITIES and _voice_of(value):
                personality[key.upper()] = _voice_of(value)
    for key in ("child", "children", "baby", "babies"):
        block = data.get(key)
        if isinstance(block, dict):
            for g, names in (("male", ("male", "boy", "m")), ("female", ("female", "girl", "f"))):
                for n in names:
                    if _voice_of(block.get(n)):
                        child[g] = _voice_of(block[n])
    for key in ("speakers", "personas"):
        block = data.get(key)
        if isinstance(block, dict):
            for name, value in block.items():
                if _voice_of(value):
                    persona[name] = _voice_of(value)
    return VoiceTable(personality, child, persona)


# ---------------------------------------------------------------- names

_INITIAL = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"]
_VOWEL = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi",
          "yu", "eu", "ui", "i"]
_FINAL = ["", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "p", "l", "m", "p", "p", "t", "t",
          "ng", "t", "t", "k", "t", "p", "t"]


def romanise(name: str) -> str:
    """Revised Romanization, syllable by syllable without sound changes (how given names are usually written):
    민준 -> minjun, 서연 -> seoyeon, 하늘 -> haneul. Only [a-z]."""
    out, prev_final = [], ""
    for ch in name:
        code = ord(ch) - 0xAC00
        if not 0 <= code < 11172:
            raise ContentError(f"name {name!r} is not plain Hangul")
        initial, vowel, final = code // 588, (code % 588) // 28, code % 28
        ini = _INITIAL[initial]
        if ini == "r" and (not out or prev_final == "l"):
            ini = "l" if out else "r"
        out.append(ini + _VOWEL[vowel] + _FINAL[final])
        prev_final = _FINAL[final]
    return "".join(out)


def has_batchim(name: str) -> bool:
    return (ord(name[-1]) - 0xAC00) % 28 != 0


def self_intro(name: str, register: str) -> str:
    """The one line with a name. 해요체 이에요/예요, 합쇼체 입니다 (SNOOTY), 반말 이야/야 (children)."""
    if register == "hapsyo":
        return f"저는 {name}입니다."
    if register == "banmal":
        return f"나는 {name}{'이야' if has_batchim(name) else '야'}."
    return f"저는 {name}{'이에요' if has_batchim(name) else '예요'}."


def name_pools(data):
    """data/villager-names.json -> {"male": [(ko, roman)], "female": [...]}. Accepts {"male": [...], "female": [...]}
    at the top or under "pools"/"names"; entries are strings or {"ko"/"name", "roman"/"romanised"/"id"}."""
    pools = data
    for key in ("pools", "names"):
        if isinstance(data.get(key), dict):
            pools = data[key]
    out = {}
    for gender in ("male", "female"):
        rows = []
        for entry in pools.get(gender, []):
            if isinstance(entry, str):
                ko, roman = entry, None
            else:
                ko = entry.get("ko") or entry.get("name")
                roman = entry.get("roman") or entry.get("romanised") or entry.get("romanized") or entry.get("id")
            roman = (roman or romanise(ko)).lower()
            if not SAFE.fullmatch(roman):
                raise ContentError(f"name {ko}: romanisation {roman!r} is not [a-z0-9_]")
            rows.append((ko, roman))
        out[gender] = rows
    if not out["male"] and not out["female"]:
        raise ContentError("villager-names.json has no male/female pools")
    return out


def name_rows(pools, table):
    rows = []
    for gender, names in pools.items():
        for ko, roman in names:
            for voice in table.by_gender(gender):
                register = "banmal" if voice in table.children else (
                    "hapsyo" if voice == table.personality.get("SNOOTY") else "haeyo")
                rows.append({"id": f"line.name.{roman}.{voice}", "ko": self_intro(ko, register), "voices": [voice],
                             "rule": "name"})
    return rows


# ---------------------------------------------------------------- lines

def personalities_of(obj):
    """The personality filter of a dialogue, trigger, request or variant, as a set (empty = no filter)."""
    found = set()
    for holder in (obj, obj.get("trigger") if isinstance(obj.get("trigger"), dict) else None):
        if not holder:
            continue
        for key in ("personality", "personalities"):
            value = holder.get(key)
            if isinstance(value, str):
                value = [value]
            if isinstance(value, list):
                found |= {str(p).upper() for p in value}
    unknown = found - set(PERSONALITIES)
    if unknown:
        raise ContentError(f"unknown personality {sorted(unknown)}")
    return found


def is_baby(obj):
    trigger = obj.get("trigger") if isinstance(obj.get("trigger"), dict) else {}
    return trigger.get("baby") is True or obj.get("baby") is True


def walk_lines(node, path=(), personality=None):
    """Yield (nodeId, personality or None, line dict) for every dict with a Korean "ko" string. The node id is the
    nearest key on the path that is neither structural nor a personality id; a personality key on the path makes
    the line that personality's variant."""
    if isinstance(node, dict):
        if isinstance(node.get("ko"), str) and path:
            names = [k for k in path if k not in STRUCTURAL and k.upper() not in PERSONALITIES]
            if names:
                yield names[-1], personality, node
        for key, value in node.items():
            if key in NOT_LINES or not isinstance(value, (dict, list)):
                continue
            pers = key.upper() if key.upper() in PERSONALITIES else personality
            yield from walk_lines(value, path + (key,), pers)
    elif isinstance(node, list):
        for value in node:
            yield from walk_lines(value, path, personality)


def voices_for(line, table, filt, baby, variant):
    speaker = line.get("speaker")
    if baby or speaker in CHILD_SPEAKERS:
        return table.children, "child"
    if speaker in table.persona:
        return [table.persona[speaker]], "persona"
    if variant:
        return table.of({variant}), "personality"
    if filt:
        return table.of(filt), "personality"
    return table.adults, "shared"


def doc_rows(prefix, doc, body, table, problems):
    """Rows for one dialogue (prefix = its id) or request (prefix = req_<id>)."""
    filt, baby = personalities_of(doc), is_baby(doc)
    lines = list(walk_lines(body))
    # a personality with its own variant of a node never hears the shared one
    own = {}
    for nid, pers, _ in lines:
        if pers:
            own.setdefault(nid, set()).add(pers)
    rows = []
    for nid, pers, line in lines:
        if not SAFE.fullmatch(nid):
            problems.append(f"{prefix}: node id {nid!r} is not [a-z0-9_]")
            continue
        voices, rule = voices_for(line, table, filt, baby, pers)
        if not pers and rule in ("shared", "personality"):
            skip = set(table.of(own.get(nid, set())))
            voices = [v for v in voices if v not in skip]
        for text, suffix, vs in ((line["ko"], "", voices),
                                 (line.get("ko_banmal"), ".banmal", [v for v in voices if v in table.of(BANMAL_OK)])):
            if not isinstance(text, str) or not text.strip():
                continue
            if "{" in text:
                problems.append(f"{prefix}.{nid}: placeholder in {text!r}, cannot be rendered ahead")
                continue
            for v in vs:
                rows.append({"id": f"line.{prefix}.{nid}{suffix}.{v}", "ko": text.strip(), "voices": [v],
                             "rule": rule + ("+banmal" if suffix else "")})
    return rows


def load_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        raise ContentError(f"{path}: {e}")


def collect(repo, cfg=None, strict=False, log=print):
    """All rows of a checkout, plus a list of problems (strings). Raises ContentError on unusable input."""
    repo = Path(repo)
    assets = repo / ASSETS
    if not (assets / "dialogue").is_dir():
        raise ContentError(f"{assets / 'dialogue'} not found: is {repo} a Hangul Quest checkout?")
    cfg = cfg or load_voices()
    problems, notes = [], []
    table = table_from_tools(cfg)
    engine_voices = assets / "data" / "voices.json"
    if engine_voices.exists():
        table = table_from_engine(load_json(engine_voices), table)
    else:
        notes.append(f"{engine_voices.relative_to(repo)} not found (the 3.1 engine adds it): "
                     "personality voices taken from tools/audio/voices.json")
    unknown = table.all() - set(cfg["voices"])
    if unknown:
        raise ContentError(f"voices without a design in tools/audio/voices.json: {sorted(unknown)}")
    if len(table.adults) != 8 or len(table.children) != 2:
        raise ContentError(f"need 8 personality voices and 2 child voices, have {table.adults} {table.children}")

    rows = []
    for path in sorted((assets / "dialogue").glob("*.json")):
        if path.name.startswith("_"):
            continue
        doc = load_json(path)
        did = doc.get("id", path.stem)
        rows += doc_rows(did, doc, doc.get("nodes", {}), table, problems)
    req_dir = assets / "requests"
    if req_dir.is_dir():
        for path in sorted(req_dir.glob("*.json")):
            if path.name.startswith("_"):
                continue
            doc = load_json(path)
            rid = doc.get("id", path.stem)
            body = {k: v for k, v in doc.items() if k not in ("id", "level", "regions", "professions", "kind",
                                                               "personalities", "personality", "schema")}
            rows += doc_rows(f"req_{rid}", doc, body, table, problems)
    else:
        notes.append(f"{req_dir.relative_to(repo)} not found: no request lines")
    names_file = assets / "data" / "villager-names.json"
    if names_file.exists():
        rows += name_rows(name_pools(load_json(names_file)), table)
    else:
        notes.append(f"{names_file.relative_to(repo)} not found (the 3.1 engine adds it): no self-introduction lines")
    for note in notes:
        log("note: " + note, file=sys.stderr)
    if strict and notes:
        raise ContentError("missing engine data (see the notes above)")
    seen = set()
    for r in rows:
        if r["id"] in seen:
            problems.append(f"duplicate event id {r['id']}")
        seen.add(r["id"])
    return rows, problems


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", default=str(REPO), help="Hangul Quest checkout (default: this one)")
    ap.add_argument("--out", default=str(WORK / "dialogue-texts.jsonl"))
    ap.add_argument("--strict", action="store_true", help="missing engine data (voices, names) is an error")
    args = ap.parse_args()
    try:
        rows, problems = collect(args.repo, strict=args.strict)
    except ContentError as e:
        sys.exit(f"error: {e}")
    for p in problems:
        print("problem: " + p, file=sys.stderr)
    if problems:
        sys.exit(f"{len(problems)} problems, nothing written")
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    by_rule = {}
    for r in rows:
        by_rule[r["rule"]] = by_rule.get(r["rule"], 0) + 1
    texts = len({r["ko"] for r in rows})
    print(f"{len(rows)} clips ({texts} distinct texts) -> {out}: "
          + ", ".join(f"{k} {v}" for k, v in sorted(by_rule.items())))


if __name__ == "__main__":
    main()
