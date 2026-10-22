#!/usr/bin/env python3
"""Write the texts of the sound forge (HVPT) stimuli as JSONL {id, ko}, for render.py.

  hvpt_texts.py [--out <file>]

The stimuli are defined in src/main/resources/assets/hangulquest/data/hvpt.json (the mod reads the same
file): minimal pairs and triplets of syllables and words for the Korean three-way stop contrast,
the sibilant contrast and the vowel pair. Every form of every item becomes one text:

  hvpt.<item>.<form>     e.g. hvpt.dt_w1.t  (딸)

Render them with the training voices and the held-out voices into their own folder, so the voices pack's
manifest is left alone:

  render.py hvpt-texts.jsonl --out ~/hangulquest-audio/out-hvpt --device cuda:0 \\
      --voices sohee female2 male1 male2 female3 male3
  qa.py --out ~/hangulquest-audio/out-hvpt --device cuda --threshold 0.15
  build_pack.py --hvpt ~/hangulquest-audio/out-hvpt

The set is synthetic. build_pack.py marks it "source: tts" in the pack manifest so it can be replaced by
natural recordings later: keep the ids, and the mod needs no change.
"""
import argparse
import json
from pathlib import Path

from common import HVPT_DATA, WORK


def load(path=HVPT_DATA):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def stimulus_id(item_id: str, form: str) -> str:
    return f"hvpt.{item_id}.{form}"


def texts(data):
    rows = []
    for item in data["items"]:
        for form, spec in item["forms"].items():
            rows.append({"id": stimulus_id(item["id"], form), "ko": spec["ko"]})
    return rows


def voices(data):
    return data["trainingVoices"] + data["heldOutVoices"]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=str(WORK / "hvpt-texts.jsonl"))
    ap.add_argument("--data", default=str(HVPT_DATA))
    args = ap.parse_args()
    data = load(args.data)
    rows = texts(data)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"{len(rows)} texts x {len(voices(data))} voices ({' '.join(voices(data))}) -> {out}")


if __name__ == "__main__":
    main()
