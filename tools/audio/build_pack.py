#!/usr/bin/env python3
"""Build the optional "Hangul Quest Voices" resource pack from rendered clips.

  build_pack.py [--out <render dir>] [--dist <folder>] [--blocklist extra.json ...]

Layout (resource pack format 97 for Minecraft 26.3):
  pack.mcmeta
  manifest.json                                      model, licences, voices, hashes, date
  assets/hangulquest/sounds.json                     one event per text: word.<conceptId>, sentence.<hash>
  assets/hangulquest/sounds/ko/<voice>/<id>.ogg

The mod only plays "hangulquest:word.<conceptId>"; the game picks one of the listed voices at random.
Clips on a blocklist (qa.py writes one, native reviewers add more) are left out; an event without any
clip left is not written, so the mod stays silent for it.
Zip goes outside the repo, into <dist> (default ~/hangulquest-audio/dist).
"""
import argparse
import json
import re
import zipfile
from pathlib import Path

from common import DIST, OUT

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


def build(render_dir: Path, dist: Path, extra_blocklists=()):
    manifest = json.loads((render_dir / "manifest.json").read_text(encoding="utf-8"))
    blocked = load_blocklists([render_dir / "audio-blocklist.json", *extra_blocklists])

    events, files, kept = {}, [], {}
    for voice, vdata in sorted(manifest["voices"].items()):
        for cid, entry in sorted(vdata["entries"].items()):
            src = render_dir / voice / f"{entry['hash']}.ogg"
            if (voice, cid) in blocked or not src.exists() or not SAFE.fullmatch(cid):
                continue
            events.setdefault(cid, {"sounds": []})["sounds"].append({"name": f"hangulquest:ko/{voice}/{cid}"})
            files.append((src, f"assets/hangulquest/sounds/ko/{voice}/{cid}.ogg"))
            kept.setdefault(voice, {})[cid] = entry
    if not events:
        raise SystemExit("nothing to pack: render first, and check the blocklist")

    pack_manifest = {k: v for k, v in manifest.items() if k != "voices"}
    pack_manifest["voices"] = {v: {"kind": manifest["voices"][v]["kind"], "entries": kept[v]} for v in kept}
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
    args = ap.parse_args()
    build(Path(args.out), Path(args.dist), args.blocklist)


if __name__ == "__main__":
    main()
