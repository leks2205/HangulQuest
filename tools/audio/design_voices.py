#!/usr/bin/env python3
"""Design the villager voices once: try a few seeds per voice, check each reference clip, keep the best.

  design_voices.py generate --voices v_lazy v_jock ... --seeds 7 19 31 43 [--device cuda:0]
  design_voices.py measure  [--device cuda]
  design_voices.py choose   [--write]

generate  VoiceDesign renders the voice's ref_text with its design text, once per seed, to
          <work>/design/<voice>/seed<N>.wav (same seeding as render.make_reference, so the seed is the voice).
measure   ASR round trip (faster-whisper large-v3, CER against ref_text) and the median F0 (librosa pyin) of
          every candidate -> <work>/design/candidates.json.
choose    picks one seed per voice: CER <= 0.05 and the pitch band of its group (adult male under 165 Hz,
          adult female over 175 Hz, children over 230 Hz); CRANKY must be the lowest of the men and PEPPY the
          highest of the adult women. --write stores seed and measurements in voices.json and copies the clip
          to models/refs/<voice>.wav (an existing reference is never replaced).
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path

from common import MODELS, VOICES_FILE, WORK, cer, load_voices
from render import ref_text

os.environ.setdefault("HF_HOME", str(MODELS))

DESIGN = WORK / "design"
MAX_CER = 0.05
MALE_MAX_HZ = 165.0
FEMALE_MIN_HZ = 175.0
CHILD_MIN_HZ = 230.0


def in_band(voice, f0):
    if f0 is None:
        return False
    if voice.get("age") == "child":
        return f0 > CHILD_MIN_HZ
    return f0 < MALE_MAX_HZ if voice.get("gender") == "male" else f0 > FEMALE_MIN_HZ


def choose(voices, candidates):
    """voice id -> chosen candidate (or None). candidates: voice id -> [{seed, cer, f0}, ...] in seed order.

    A candidate passes on CER and on its pitch band. The other voices take their lowest-CER passing candidate
    (the earlier seed on a tie). CRANKY then takes, of its passing candidates below every other man, the
    lowest CER and then the lowest pitch; PEPPY, of those above every other adult woman, the lowest CER and then
    the lowest pitch (the least shrill one that is still the highest). If no CRANKY/PEPPY candidate fits, the
    other men/women are picked again from the candidates above the lowest CRANKY / below the highest PEPPY."""
    passing = {v: [c for c in candidates.get(v, []) if c["cer"] <= MAX_CER and in_band(voices[v], c["f0"])]
               for v in voices}
    by_pers = {voices[v].get("personality"): v for v in voices}
    cranky, peppy = by_pers.get("CRANKY"), by_pers.get("PEPPY")
    adult = lambda v, g: voices[v].get("age") != "child" and voices[v].get("gender") == g and v not in (cranky, peppy)

    def best(pool):
        return min(pool, key=lambda c: c["cer"], default=None)

    chosen = {v: best(passing[v]) for v in voices if v not in (cranky, peppy)}
    for extreme, gender, below in ((cranky, "male", True), (peppy, "female", False)):
        if not extreme:
            continue
        others = [v for v in voices if adult(v, gender)]
        for attempt in range(2):
            picked = [chosen[v]["f0"] for v in others if chosen[v]]
            fits = [c for c in passing[extreme]
                    if not picked or (c["f0"] < min(picked) if below else c["f0"] > max(picked))]
            pick = min(fits, key=lambda c: (c["cer"], c["f0"]), default=None)
            if pick or attempt or not passing[extreme]:
                break
            edge = (min if below else max)(c["f0"] for c in passing[extreme])
            for v in others:
                chosen[v] = best([c for c in passing[v] if (c["f0"] > edge if below else c["f0"] < edge)])
        chosen[extreme] = pick
    return chosen


def generate(args):
    import soundfile as sf
    import torch
    from qwen_tts import Qwen3TTSModel
    cfg = load_voices()
    dtype = torch.float32 if args.device == "cpu" else torch.bfloat16
    model = Qwen3TTSModel.from_pretrained(cfg["model"]["repos"]["design"], device_map=args.device, dtype=dtype)
    for name in args.voices:
        voice = cfg["voices"][name]
        for seed in args.seeds:
            path = DESIGN / name / f"seed{seed}.wav"
            if path.exists():
                continue
            path.parent.mkdir(parents=True, exist_ok=True)
            torch.manual_seed(seed)
            wavs, sr = model.generate_voice_design(text=ref_text(cfg, voice), instruct=voice["design"],
                                                   language="Korean")
            sf.write(str(path), wavs[0], sr)
            print(f"[{name}] seed {seed}: {len(wavs[0]) / sr:.1f}s", flush=True)


def median_f0(path):
    import librosa
    import numpy as np
    y, sr = librosa.load(str(path), sr=16000, mono=True)
    f0, voiced, _ = librosa.pyin(y, fmin=60, fmax=700, sr=sr, frame_length=1024)
    f0 = f0[voiced & ~np.isnan(f0)]
    return (round(float(np.median(f0)), 1), round(float(np.percentile(f0, 10)), 1),
            round(float(np.percentile(f0, 90)), 1), len(y) / sr) if len(f0) else (None, None, None, len(y) / sr)


def measure(args):
    import qa
    from faster_whisper import WhisperModel
    cfg = load_voices()
    device = "cuda" if args.device.startswith("cuda") else "cpu"
    if device == "cuda":
        qa.preload_cuda12()
    asr = WhisperModel("large-v3", device=device, compute_type="float16" if device == "cuda" else "int8",
                       download_root=str(MODELS / "whisper"))
    out = {}
    for vdir in sorted(p for p in DESIGN.iterdir() if p.is_dir()):
        name = vdir.name
        text = ref_text(cfg, cfg["voices"][name])
        rows = []
        for wav in sorted(vdir.glob("seed*.wav"), key=lambda p: int(p.stem[4:])):
            segments, _ = asr.transcribe(qa.decode(wav), language="ko", beam_size=5, temperature=0.0,
                                         condition_on_previous_text=False, vad_filter=False)
            heard = qa.spell_digits(qa.HALLUCINATION.sub("", "".join(s.text for s in segments))).strip()
            f0, p10, p90, seconds = median_f0(wav)
            rows.append({"seed": int(wav.stem[4:]), "cer": round(cer(text, heard), 4), "asr": heard, "f0": f0,
                         "f0_p10": p10, "f0_p90": p90, "seconds": round(seconds, 2)})
            print(name, rows[-1], flush=True)
        out[name] = rows
    (DESIGN / "candidates.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")


def choose_cmd(args):
    cfg = load_voices()
    candidates = json.loads((DESIGN / "candidates.json").read_text(encoding="utf-8"))
    voices = {v: cfg["voices"][v] for v in candidates}
    chosen = choose(voices, candidates)
    for v, c in chosen.items():
        print(f"{v:12} {'-- none passes --' if c is None else c}")
    if not args.write:
        return
    raw = json.loads(VOICES_FILE.read_text(encoding="utf-8"))
    for v, c in chosen.items():
        if c is None:
            continue
        src, ref = DESIGN / v / f"seed{c['seed']}.wav", MODELS / "refs" / f"{v}.wav"
        if ref.exists() and hashlib.sha1(ref.read_bytes()).digest() != hashlib.sha1(src.read_bytes()).digest():
            sys.exit(f"{ref} exists and differs from the chosen clip; move it away first")
        ref.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, ref)
        entry = raw["voices"][v]
        entry["seed"] = c["seed"]
        entry["reference"] = {"cer": c["cer"], "f0_median_hz": c["f0"], "f0_p10_hz": c["f0_p10"],
                              "f0_p90_hz": c["f0_p90"], "seconds": c["seconds"],
                              "seeds_tried": [x["seed"] for x in candidates[v]],
                              "sha1": hashlib.sha1(ref.read_bytes()).hexdigest()[:12]}
    VOICES_FILE.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    g = sub.add_parser("generate")
    g.add_argument("--voices", nargs="+", required=True)
    g.add_argument("--seeds", nargs="+", type=int, required=True)
    g.add_argument("--device", default="cuda:0")
    m = sub.add_parser("measure")
    m.add_argument("--device", default="cuda")
    c = sub.add_parser("choose")
    c.add_argument("--write", action="store_true")
    args = ap.parse_args()
    {"generate": generate, "measure": measure, "choose": choose_cmd}[args.cmd](args)


if __name__ == "__main__":
    main()
