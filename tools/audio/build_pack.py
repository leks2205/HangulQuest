#!/usr/bin/env python3
"""Build the optional "Hangul Quest Voices" resource pack from rendered clips.

  build_pack.py [--out <render dir>] [--dist <folder>] [--blocklist extra.json ...] [--hvpt <render dir>]

Layout (resource pack format 97 for Minecraft 26.3):
  pack.mcmeta
  manifest.json                                      model, licences, voices, hashes, date
  assets/hangulquest/sounds.json                     one event per text: word.<conceptId>, sentence.<hash>
  assets/hangulquest/sounds/ko/<voice>/<id>.ogg

The mod only plays "hangulquest:word.<conceptId>"; the game picks one of the listed voices at random.
The sound forge stimuli (hvpt.<item>.<form>, see hvpt_texts.py) are different: the mod picks the voice itself,
so every voice gets its own event, hvpt.<item>.<form>.<voice>, with exactly one clip. They may come from the
same render dir or from a second one (--hvpt); both are added to the one pack. The pack manifest marks them
"source": "tts" so they can be replaced by natural recordings later without a mod change.
Clips on a blocklist (qa.py writes one, native reviewers add more) are left out; an event without any
clip left is not written, so the mod stays silent for it.
Zip goes outside the repo, into <dist> (default ~/hangulquest-audio/dist).
"""
import argparse
import json
import re
import zipfile
from pathlib import Path

from common import DIST, OUT, load_voices

HVPT_PREFIX = "hvpt."
PACK_FORMAT = 97  # resource_major in the 26.3 version.json
NAME = "Hangul Quest Voices"
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
    """Sound forge stimuli get one event per voice (the mod chooses the voice); everything else shares one event."""
    return f"{cid}.{voice}" if cid.startswith(HVPT_PREFIX) else cid


def build(render_dir: Path, dist: Path, extra_blocklists=(), hvpt_dir: Path = None):
    sources = [Path(render_dir)] + ([Path(hvpt_dir)] if hvpt_dir else [])
    manifests = [json.loads((d / "manifest.json").read_text(encoding="utf-8")) for d in sources]
    manifest = manifests[0]
    blocked = load_blocklists([d / "audio-blocklist.json" for d in sources] + list(extra_blocklists))

    events, files, kept, kinds = {}, [], {}, {}
    for render_path, rendered in zip(sources, manifests):
        for voice, vdata in sorted(rendered["voices"].items()):
            kinds[voice] = vdata["kind"]
            for cid, entry in sorted(vdata["entries"].items()):
                src = render_path / voice / f"{entry['hash']}.ogg"
                if (voice, cid) in blocked or not src.exists() or not SAFE.fullmatch(cid):
                    continue
                events.setdefault(event_name(cid, voice), {"sounds": []})["sounds"].append(
                    {"name": f"hangulquest:ko/{voice}/{cid}"})
                files.append((src, f"assets/hangulquest/sounds/ko/{voice}/{cid}.ogg"))
                if cid.startswith(HVPT_PREFIX):
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
    mcmeta = {"pack": {"description": f"{NAME}: spoken Korean for Hangul Quest (synthetic voices)",
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
    ap.add_argument("--blocklist", nargs="*", default=[])
    ap.add_argument("--hvpt", default=None, help="a second render dir with the sound forge stimuli (hvpt.*)")
    args = ap.parse_args()
    build(Path(args.out), Path(args.dist), args.blocklist, Path(args.hvpt) if args.hvpt else None)


if __name__ == "__main__":
    main()
