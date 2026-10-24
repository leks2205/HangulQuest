#!/usr/bin/env python3
"""Write the villager lines of a repo checkout as JSONL {id, ko, voices} for render.py, one row per line and voice.

  dialogue_texts.py [--repo <checkout>] [--out <file>] [--strict]

Reads assets/hangulquest/dialogue/*.json, assets/hangulquest/requests/*.json, data/voices.json (personality ->
voice, registers, event names) and data/villager-names.json (names and their self-introduction lines) of the
checkout. The last two come with the 3.1 engine; without them the voices come from tools/audio/voices.json and no
self-introductions are written (a clear message says so; --strict makes that an error).

Event ids (data/voices.json "events"; they become hangulquest:<id> sound events, one clip each):
  line.<dialogueId>.<nodeId>.<voice>                  an NPC line
  line.<dialogueId>.<nodeId>_banmal.<voice>           its ko_banmal text (villagers who switched to 반말)
  line.<requestId>.<personality>_<part>.<voice>       a request line (requestId starts with req_; part is offer,
                                                      accept, decline, reminder or thanks), ..._<part>_banmal. too
  line.name.<nameId>.<voice>                          the self-introduction (villager-names.json lines.<register>)
If the engine's "events" say anything else, the tool stops instead of writing names the game never plays.

Which voices say a line:
  - the speaker is a child, or the dialogue is for babies          -> both child voices
  - the speaker has a fixed persona voice (elder, trader, golem, librarian; tools/audio/voices.json "personas")
                                                                   -> that voice
  - the dialogue is for some personalities (trigger.personalities) -> their voices
  - anything else (shared, e.g. the profession dialogues)          -> all 8 adult personality voices
  - ko_banmal only in the voices of the personalities that can switch to 반말 (LAZY, JOCK, PEPPY, SISTERLY)
  - request lines are per personality -> that personality's voice
  - a name: the 4 adult voices of its gender and the child voice of its gender, in each voice's register
Player choices and answers are not voiced. A line with a {placeholder} cannot be rendered ahead and is reported.
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
REQUEST_PARTS = ("offer", "accept", "decline", "reminder", "thanks")
BANMAL = "_banmal"
# what this tool writes; the engine's data/voices.json "events" must say the same
EVENTS = {
    "line": "hangulquest:line.<dialogueId>.<nodeId>.<voiceId>",
    "banmal": "hangulquest:line.<dialogueId>.<nodeId>_banmal.<voiceId>",
    "request": "hangulquest:line.<requestId>.<personality>_<part>.<voiceId>",
    "selfIntro": "hangulquest:line.name.<nameId>.<voiceId>",
}
SAFE = re.compile(r"[a-z0-9_]+")


class ContentError(Exception):
    pass


# ---------------------------------------------------------------- voices

class VoiceTable:
    """personality -> voice, child voices by gender, speaker -> fixed voice, voice -> register."""

    def __init__(self, personality, child, persona, register=None):
        self.personality = personality
        self.child = child
        self.persona = persona
        self.register = register or {}

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

    def register_of(self, voice):
        if voice in self.register:
            return self.register[voice]
        if voice in self.children:
            return "banmal"
        return "hapsyo" if voice == self.personality.get("SNOOTY") else "haeyo"

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


def table_from_engine(data, fallback):
    """data/voices.json of the engine: {"personalities": {"LAZY": {"voice", "gender", "register"}}, "children":
    {"male", "female", "register"}, "speakers"?: {"elder": "male1"}}. Fixed speakers it leaves out come from
    tools/audio/voices.json "personas"."""
    events = data.get("events")
    if events:
        # a template may carry a remark after it: "hangulquest:line.<...>.<voiceId> (requestId starts with req_)"
        changed = {k: v for k, v in events.items() if k in EVENTS and v.split()[0] != EVENTS[k]}
        if changed or set(EVENTS) - set(events):
            raise ContentError(f"data/voices.json names the sound events differently from this tool: {changed or events}")
    personality, register = {}, {}
    for pid, entry in data.get("personalities", {}).items():
        pid = pid.upper()
        if pid not in PERSONALITIES or not isinstance(entry, dict) or "voice" not in entry:
            raise ContentError(f"data/voices.json: personality {pid} needs a voice")
        personality[pid] = entry["voice"]
        if entry.get("register"):
            register[entry["voice"]] = entry["register"]
    kids = data.get("children", {})
    child = {g: kids[g] for g in ("male", "female") if isinstance(kids.get(g), str)}
    for v in child.values():
        if kids.get("register"):
            register[v] = kids["register"]
    persona = dict(fallback.persona)
    persona.update({k: v if isinstance(v, str) else v["voice"] for k, v in data.get("speakers", {}).items()})
    return VoiceTable(personality or dict(fallback.personality), child or dict(fallback.child), persona, register)


# ---------------------------------------------------------------- names

_INITIAL = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"]
_VOWEL = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi",
          "yu", "eu", "ui", "i"]
_FINAL = ["", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "p", "l", "m", "p", "p", "t", "t",
          "ng", "t", "t", "k", "t", "p", "t"]


def romanise(name: str) -> str:
    """Revised Romanization, syllable by syllable without sound changes (how given names are usually written):
    민준 -> minjun, 서연 -> seoyeon, 하늘 -> haneul. Only used when a name comes without an id."""
    out, prev_final = [], ""
    for ch in name:
        code = ord(ch) - 0xAC00
        if not 0 <= code < 11172:
            raise ContentError(f"name {name!r} is not plain Hangul")
        initial, vowel, final = code // 588, (code % 588) // 28, code % 28
        ini = _INITIAL[initial]
        if ini == "r" and out and prev_final == "l":
            ini = "l"
        out.append(ini + _VOWEL[vowel] + _FINAL[final])
        prev_final = _FINAL[final]
    return "".join(out)


def has_batchim(name: str) -> bool:
    return (ord(name[-1]) - 0xAC00) % 28 != 0


def self_intro(name: str, register: str) -> str:
    """The one line with a name, when villager-names.json does not spell it out: 해요체 이에요/예요, 합쇼체 입니다,
    반말 이야/야."""
    if register == "hapsyo":
        return f"저는 {name}입니다."
    if register == "banmal":
        return f"나는 {name}{'이야' if has_batchim(name) else '야'}."
    return f"저는 {name}{'이에요' if has_batchim(name) else '예요'}."


def name_rows(data, table):
    """villager-names.json {"names": [{"ko", "gender", "id", "lines": {"haeyo", "hapsyo", "banmal"}}]}."""
    rows = []
    for entry in data.get("names", []):
        ko, gender = entry["ko"], entry.get("gender")
        if gender not in ("male", "female"):
            raise ContentError(f"name {ko}: gender must be male or female")
        nid = entry.get("id") or romanise(ko)
        if not SAFE.fullmatch(nid):
            raise ContentError(f"name {ko}: id {nid!r} is not [a-z0-9_]")
        for voice in table.by_gender(gender):
            register = table.register_of(voice)
            text = entry.get("lines", {}).get(register) or self_intro(ko, register)
            rows.append({"id": f"line.name.{nid}.{voice}", "ko": text, "voices": [voice], "rule": "name"})
    if not rows:
        raise ContentError("villager-names.json has no names")
    return rows


# ---------------------------------------------------------------- lines

def personalities_of(doc):
    """The personality filter of a dialogue (trigger.personalities, or "personality"), as a set; empty = shared."""
    found = set()
    for holder in (doc, doc.get("trigger") or {}):
        for key in ("personality", "personalities"):
            value = holder.get(key)
            if isinstance(value, str):
                value = [value]
            if isinstance(value, list):
                found |= {str(p).upper() for p in value}
    unknown = found - set(PERSONALITIES)
    if unknown:
        raise ContentError(f"{doc.get('id')}: unknown personality {sorted(unknown)}")
    return found


def voices_for(speaker, table, filt, baby):
    if baby or speaker in CHILD_SPEAKERS:
        return table.children, "child"
    if speaker in table.persona:
        return [table.persona[speaker]], "persona"
    if filt:
        return table.of(filt), "personality"
    return table.adults, "shared"


def line_rows(prefix, nid, ko, ko_banmal, voices, rule, table, problems):
    """One row per voice for the line, and for its 반말 text one per voice that can switch."""
    rows = []
    switch = table.of(BANMAL_OK)
    for text, suffix, vs, r in ((ko, "", voices, rule),
                                (ko_banmal, BANMAL, [v for v in voices if v in switch], rule + "+banmal")):
        if not isinstance(text, str) or not text.strip():
            continue
        if "{" in text:
            problems.append(f"{prefix}.{nid}: placeholder in {text!r}, cannot be rendered ahead")
            continue
        for v in vs:
            rows.append({"id": f"line.{prefix}.{nid}{suffix}.{v}", "ko": text.strip(), "voices": [v], "rule": r})
    return rows


def dialogue_rows(doc, table, problems):
    did = doc["id"]
    filt = personalities_of(doc)
    baby = (doc.get("trigger") or {}).get("baby") is True
    rows = []
    for nid, node in doc.get("nodes", {}).items():
        if not SAFE.fullmatch(nid):
            problems.append(f"{did}: node id {nid!r} is not [a-z0-9_]")
            continue
        if not isinstance(node.get("ko"), str):
            continue  # the self-introduction node (selfIntro) and the like: voiced from the name list
        voices, rule = voices_for(node.get("speaker"), table, filt, baby)
        rows += line_rows(did, nid, node["ko"], node.get("ko_banmal"), voices, rule, table, problems)
    return rows


def request_rows(doc, table, problems):
    """{"id": "req_...", "lines": {"LAZY": {"offer": "..." | {"ko", "ko_banmal"}, ...}, ...}}; "answers" are the
    player's."""
    rid = doc["id"]
    if not rid.startswith("req_") or not SAFE.fullmatch(rid):
        problems.append(f"request id {rid!r} must be req_[a-z0-9_]+")
        return []
    rows = []
    for pid, parts in doc.get("lines", {}).items():
        if pid.upper() not in PERSONALITIES:
            problems.append(f"{rid}: unknown personality {pid}")
            continue
        for part, line in parts.items():
            if part not in REQUEST_PARTS:
                problems.append(f"{rid}.{pid}: unknown part {part}")
                continue
            ko, banmal = (line, None) if isinstance(line, str) else (line.get("ko"), line.get("ko_banmal"))
            rows += line_rows(rid, f"{pid.lower()}_{part}", ko, banmal, table.of({pid.upper()}), "request",
                              table, problems)
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
        if not path.name.startswith("_"):
            rows += dialogue_rows({"id": path.stem, **load_json(path)}, table, problems)
    req_dir = assets / "requests"
    if req_dir.is_dir():
        for path in sorted(req_dir.glob("*.json")):
            if not path.name.startswith("_"):
                rows += request_rows({"id": path.stem, **load_json(path)}, table, problems)
    else:
        notes.append(f"{req_dir.relative_to(repo)} not found: no request lines")
    names_file = assets / "data" / "villager-names.json"
    if names_file.exists():
        rows += name_rows(load_json(names_file), table)
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
