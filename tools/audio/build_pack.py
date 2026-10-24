#!/usr/bin/env python3
"""Build the optional "Hangul Quest Voices" resource pack from rendered clips.

  build_pack.py [--out <render dir>] [--dist <folder>] [--texts texts.jsonl [lines.jsonl ...]] [--words-only]
               [--blocklist extra.json ...] [--hvpt <render dir>]

Layout (resource pack format 97 for Minecraft 26.3):
  pack.mcmeta
  manifest.json                                      model, licences, voices, hashes, date
  assets/hangulquest/sounds.json                     one event per text: word.<conceptId>, sentence.<hash>,
                                                     line.<dialogue>.<node>.<voice>
  assets/hangulquest/sounds/ko/<voice>/<id>.ogg

The mod plays "hangulquest:word.<conceptId>"; the game picks one of the listed voices at random.
Villager lines (dialogue_texts.py) carry their voice in the id already, line.<dialogue>.<node>.<voice>: the mod
picks the villager's voice, so each such event has exactly one clip.
The sound forge stimuli (hvpt.<item>.<form>, see hvpt_texts.py) are different: the mod picks the voice itself,
so every voice gets its own event, hvpt.<item>.<form>.<voice>, with exactly one clip. They may come from the
same render dir or from a second one (--hvpt); both are added to the one pack. The pack manifest marks them
"source": "tts" so they can be replaced by natural recordings later without a mod change.
Clips on a blocklist (qa.py writes one, native reviewers add more) are left out; an event without any
clip left is not written, so the mod stays silent for it.
With --texts (JSONL from select_texts.py) only clips whose id is in the list and whose text still matches are
packed; a clip of a text that was removed or reworded is dropped, the rest of the render folder is kept as cache.
Zip goes outside the repo, into <dist> (default ~/hangulquest-audio/dist).
"""
import argparse
import json
import re
import zipfile
from pathlib import Path

from common import DIST, OUT, load_voices, read_jsonl, sha1

HVPT_PREFIX = "hvpt."
PACK_FORMAT = 97  # resource_major in the 26.3 version.json
NAME = "Hangul Quest Voices"
PACK_VERSION = "1.2.0"
LINE_PREFIX = "line."
SAFE = re.compile(r"[a-z0-9_.]+")

NOTICE = """Hangul Quest Voices
===================
Spoken Korean for the Hangul Quest mod. Optional: the mod works without this pack.

The audio was generated offline with {model} ({code} code, {weights} weights).
The model licences ({code} code / {weights} weights) place no restriction on the generated audio,
so it can be redistributed freely. Licence texts:
{urls}

The voices are synthetic. They are not recordings of real people.
Rendered: {date}
"""


def load_blocklists(paths):
    blocked = set()
    for p in paths:
        p = Path(p)
        if p.exists():
            for item in json.loads(p.read_text(encoding="utf-8")).get("blocked", []):
                blocked.add((item["voice"], item["id"]))
    return blocked


def event_name(cid: str, voice: str) -> str:
    """Sound forge stimuli get one event per voice (the mod chooses the voice); villager lines name their voice
    already; words share one event over all voices."""
    return f"{cid}.{voice}" if cid.startswith(HVPT_PREFIX) else cid


def misplaced_line(cid: str, voice: str) -> bool:
    """A villager line rendered in a voice its id does not name (a stray --voices run) is never packed."""
    return cid.startswith(LINE_PREFIX) and not cid.endswith("." + voice)


def current_texts(texts):
    """id -> sha1(text) of one or more JSONL files, or None for no filter."""
    if not texts:
        return None
    paths = [texts] if isinstance(texts, (str, Path)) else texts
    return {r["id"]: sha1(r["ko"]) for p in paths for r in read_jsonl(p)}


def build(render_dir: Path, dist: Path, extra_blocklists=(), texts=None, words_only=False, hvpt_dir: Path = None):
    sources = [Path(render_dir)] + ([Path(hvpt_dir)] if hvpt_dir else [])
    manifests = [json.loads((d / "manifest.json").read_text(encoding="utf-8")) for d in sources]
    manifest = manifests[0]
    blocked = load_blocklists([d / "audio-blocklist.json" for d in sources] + list(extra_blocklists))

    # --texts and --words-only filter the voices pack's own texts; sound forge stimuli have their own list (hvpt.json)
    current = current_texts(texts)
    events, files, kept, kinds = {}, [], {}, {}
    for render_path, rendered in zip(sources, manifests):
        for voice, vdata in sorted(rendered["voices"].items()):
            kinds[voice] = vdata["kind"]
            for cid, entry in sorted(vdata["entries"].items()):
                src = render_path / voice / f"{entry['hash']}.ogg"
                if (voice, cid) in blocked or not src.exists() or not SAFE.fullmatch(cid) or misplaced_line(cid, voice):
                    continue
                stimulus = cid.startswith(HVPT_PREFIX)
                if not stimulus and current is not None and current.get(cid) != entry.get("text_sha1"):
                    continue
                if not stimulus and words_only and not cid.startswith(("word.", LINE_PREFIX)):
                    continue
                events.setdefault(event_name(cid, voice), {"sounds": []})["sounds"].append(
                    {"name": f"hangulquest:ko/{voice}/{cid}"})
                files.append((src, f"assets/hangulquest/sounds/ko/{voice}/{cid}.ogg"))
                if stimulus:
                    entry = {**entry, "source": "tts"}
                kept.setdefault(voice, {})[cid] = entry
    if not events:
        raise SystemExit("nothing to pack: render first, and check the blocklist")

    pack_manifest = {k: v for k, v in manifest.items() if k != "voices"}
    pack_manifest["voices"] = {v: {"kind": kinds[v], "entries": kept[v]} for v in kept}
    forge = sorted({cid for entries in kept.values() for cid in entries if cid.startswith(HVPT_PREFIX)})
    if forge:
        held_out = [n for n, v in load_voices()["voices"].items() if v.get("hvpt_only")]
        pack_manifest["hvpt"] = {"source": "tts", "stimuli": len(forge), "heldOutVoices": held_out,
                                 "note": "synthetic; replace with natural recordings of several talkers, same ids"}
    pack_manifest["blocked"] = len(blocked)
    notice = NOTICE.format(model=manifest["model"], code=manifest["code_licence"], weights=manifest["weights_licence"],
                           urls="\n".join("  " + u for u in manifest["licence_urls"]), date=manifest["rendered"])
    pack_manifest["packVersion"] = PACK_VERSION
    lines = sum(cid.startswith(LINE_PREFIX) for entries in kept.values() for cid in entries)
    if lines:
        pack_manifest["lines"] = {"clips": lines, "note": "villager lines, one event per line and voice"}
    mcmeta = {"pack": {"description": f"{NAME} {PACK_VERSION}: spoken Korean for Hangul Quest (synthetic voices)",
                       "min_format": PACK_FORMAT, "max_format": PACK_FORMAT}}

    dist.mkdir(parents=True, exist_ok=True)
    target = dist / (NAME.replace(" ", "-") + ".zip")
    fixed = (2026, 1, 1, 0, 0, 0)

    def put(zf, name, data, method=zipfile.ZIP_DEFLATED):
        info = zipfile.ZipInfo(name, fixed)
        info.compress_type = method
        info.external_attr = 0o644 << 16
        zf.writestr(info, data)

    with zipfile.ZipFile(target, "w") as zf:
        put(zf, "pack.mcmeta", json.dumps(mcmeta, indent=1))
        put(zf, "NOTICE.txt", notice)
        put(zf, "manifest.json", json.dumps(pack_manifest, ensure_ascii=False, indent=1))
        put(zf, "assets/hangulquest/sounds.json", json.dumps(dict(sorted(events.items())), indent=1))
        for src, name in files:  # Vorbis is compressed already
            put(zf, name, src.read_bytes(), zipfile.ZIP_STORED)
    size = target.stat().st_size
    print(f"{target}: {size / 1e6:.1f} MB, {len(files)} clips, {len(events)} events, "
          f"{len(kept)} voices, {len(blocked)} blocked")
    return target


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--dist", default=str(DIST))
    ap.add_argument("--texts", nargs="+", help="JSONL of the current texts (words, sentences, villager lines); "
                                               "clips of other texts are left out")
    ap.add_argument("--blocklist", nargs="*", default=[])
    ap.add_argument("--words-only", action="store_true",
                    help="leave sentence clips out (the mod plays word.* and line.* events, not sentences)")
    ap.add_argument("--hvpt", default=None, help="a second render dir with the sound forge stimuli (hvpt.*)")
    args = ap.parse_args()
    build(Path(args.out), Path(args.dist), args.blocklist, args.texts, args.words_only,
          Path(args.hvpt) if args.hvpt else None)


if __name__ == "__main__":
    main()
