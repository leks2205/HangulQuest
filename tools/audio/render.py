#!/usr/bin/env python3
"""Render Korean TTS clips offline with Qwen3-TTS (Apache-2.0 code and weights).

  render.py texts.jsonl --voices sohee female2 male1 male2 [--device cuda:0|cpu]

Input is JSONL, one {"id": ..., "ko": ...} per line (see select_texts.py).
Output, all outside the repo:
  <out>/<voice>/<sha1(text+voice+model)>.ogg   mono, 24 kHz, Ogg Vorbis ~32 kbps, -16 LUFS, silence trimmed
  <out>/manifest.json                          model, licences, voices, id -> hash map, dates
Clips that already exist are skipped, so a changed text only renders again for that text.
--redo <audio-blocklist.json> deletes the clips that qa.py flagged and renders them again with other random
seeds (same text, same voice, so the file name stays the same); run qa.py afterwards.
Nothing here uses the network except the first model download from Hugging Face
(set HF_HUB_OFFLINE=1 afterwards).
"""
import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from common import MODELS, OUT, default_voices, load_voices, normalise, read_jsonl, sha1

os.environ.setdefault("HF_HOME", str(MODELS))

LANGUAGE = "Korean"
SAMPLE_RATE = 24000
TARGET_LUFS = -16.0
MAX_TRIES = 3


# ---------------------------------------------------------------- audio post-processing

TRIM = (
    "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.04,"
    "areverse,"
    "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,"
    "areverse"
)


def ffmpeg(*args, capture=False):
    cmd = ["ffmpeg", "-hide_banner", "-nostdin", "-y", *args]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"ffmpeg failed: {res.stderr[-400:]}")
    return res.stderr if capture else None


def encode(wav_path: Path, ogg_path: Path):
    """Trim, normalise to -16 LUFS (two passes: short clips need the measured gain), encode Vorbis."""
    with tempfile.TemporaryDirectory() as tmp:
        trimmed = Path(tmp) / "trimmed.wav"
        ffmpeg("-i", str(wav_path), "-af", TRIM, "-ar", str(SAMPLE_RATE), "-ac", "1", str(trimmed))
        # Loudness needs 400 ms blocks; pad the measurement copy so a 300 ms word still has a reading.
        report = ffmpeg(
            "-i", str(trimmed), "-af",
            f"adelay=400:all=1,apad=pad_dur=0.4,loudnorm=I={TARGET_LUFS}:TP=-1.5:LRA=11:print_format=json",
            "-f", "null", "-", capture=True)
        stats = json.loads(report[report.rindex("{"):report.rindex("}") + 1])
        measured = float(stats["input_i"])
        gain = TARGET_LUFS - measured if measured > -70 else 0.0
        # a true-peak limiter after the gain keeps loud single syllables from clipping
        ffmpeg("-i", str(trimmed), "-af", f"volume={gain:.2f}dB,alimiter=limit=0.8:attack=2:release=40:level=disabled",
               "-ar", str(SAMPLE_RATE), "-ac", "1", "-c:a", "libvorbis", "-b:a", "32k",
               str(ogg_path))


def write_wav(path: Path, samples, sr: int):
    import soundfile as sf
    sf.write(str(path), samples, sr)


def plausible(text: str, seconds: float) -> bool:
    """TTS models sometimes babble or cut a word; catch the extremes so those clips are rendered again."""
    chars = max(1, len(normalise(text)))
    return 0.12 * chars <= seconds <= 0.9 * chars + 1.5


# ---------------------------------------------------------------- models

class Engine:
    def __init__(self, device: str):
        import torch
        self.torch = torch
        self.device = device
        self.dtype = torch.float32 if device == "cpu" else torch.bfloat16
        self._loaded = {}

    def load(self, kind: str, repo: str):
        if kind not in self._loaded:
            # one model resident at a time keeps the shared GPU light
            self.release()
            from qwen_tts import Qwen3TTSModel
            self._loaded[kind] = Qwen3TTSModel.from_pretrained(repo, device_map=self.device, dtype=self.dtype)
        return self._loaded[kind]

    def release(self):
        self._loaded.clear()
        import gc
        gc.collect()
        if self.device != "cpu":
            self.torch.cuda.empty_cache()

    def seed(self, value: int):
        self.torch.manual_seed(value)


def make_reference(engine, cfg, name, voice):
    """Design a voice once (VoiceDesign), keep the clip as that voice's reference for cloning."""
    ref = MODELS / "refs" / f"{name}.wav"
    if ref.exists():
        return ref
    ref.parent.mkdir(parents=True, exist_ok=True)
    model = engine.load("design", cfg["model"]["repos"]["design"])
    engine.seed(voice["seed"])
    wavs, sr = model.generate_voice_design(text=cfg["ref_text"], instruct=voice["design"], language=LANGUAGE)
    write_wav(ref, wavs[0], sr)
    return ref


def file_sha1(path: Path) -> str:
    import hashlib
    return hashlib.sha1(path.read_bytes()).hexdigest()


def seed_for(name, batch_index, attempt, salt=""):
    """A new salt gives other seeds, so rendering a flagged clip again does not repeat the same mistake."""
    return int(sha1(name, str(batch_index), str(attempt), *([salt] if salt else []))[:8], 16)


def render_voice(engine, cfg, name, voice, rows, out_dir, batch, salt=""):
    repos = cfg["model"]["repos"]
    model_id = cfg["model"]["name"]
    if voice["kind"] == "clone":
        ref = make_reference(engine, cfg, name, voice)
        voice_key = f"{name}:{file_sha1(ref)[:12]}"
    else:
        voice_key = f"{name}:{voice['speaker']}"
    vdir = out_dir / name
    vdir.mkdir(parents=True, exist_ok=True)

    todo, entries = [], {}
    for row in rows:
        h = sha1(row["ko"], voice_key, model_id)
        entries[row["id"]] = {"hash": h, "text_sha1": sha1(row["ko"]), "ko": row["ko"]}
        if not (vdir / f"{h}.ogg").exists():
            todo.append((row["id"], row["ko"], h))
    print(f"[{name}] {len(rows) - len(todo)} cached, {len(todo)} to render", flush=True)
    if not todo:
        return entries, 0.0, 0

    if voice["kind"] == "custom":
        model = engine.load("custom", repos["custom"])
        generate = lambda texts: model.generate_custom_voice(
            text=texts, speaker=[voice["speaker"]] * len(texts), language=[LANGUAGE] * len(texts))
    else:
        model = engine.load("base", repos["base"])
        prompt = model.create_voice_clone_prompt(ref_audio=str(ref), ref_text=cfg["ref_text"])
        generate = lambda texts: model.generate_voice_clone(
            text=texts, language=[LANGUAGE] * len(texts), voice_clone_prompt=prompt * len(texts))

    # similar lengths together keep padding waste low
    todo.sort(key=lambda t: len(t[1]))
    started, audio_seconds, retries = time.time(), 0.0, 0
    for i in range(0, len(todo), batch):
        chunk = todo[i:i + batch]
        pending = chunk
        for attempt in range(MAX_TRIES):
            engine.seed(seed_for(name, i, attempt, salt))
            wavs, sr = generate([t[1] for t in pending])
            failed = []
            for (cid, text, h), wav in zip(pending, wavs):
                seconds = len(wav) / sr
                if not plausible(text, seconds) and attempt < MAX_TRIES - 1:
                    failed.append((cid, text, h))
                    continue
                with tempfile.TemporaryDirectory() as tmp:
                    raw = Path(tmp) / "raw.wav"
                    write_wav(raw, wav, sr)
                    encode(raw, vdir / f"{h}.ogg")
                audio_seconds += seconds
            retries += len(failed)
            pending = failed
            if not pending:
                break
        print(f"[{name}] {min(i + batch, len(todo))}/{len(todo)}", flush=True)
    return entries, time.time() - started, retries


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("texts")
    ap.add_argument("--voices", nargs="+", default=None)
    ap.add_argument("--device", default="cpu", help="cpu (default) or cuda:0; CPU is about 4x slower than real time")
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--redo", help="audio-blocklist.json from qa.py: delete those clips and render them again")
    ap.add_argument("--seed-salt", default="", help="changes the random seeds, so a repeat render sounds different")
    args = ap.parse_args()

    cfg = load_voices()
    names = args.voices or default_voices(cfg)
    rows = read_jsonl(args.texts)
    ids = [r["id"] for r in rows]
    if len(set(ids)) != len(ids) or any(not re.fullmatch(r"[a-z0-9_.]+", i) for i in ids):
        sys.exit("ids must be unique and match [a-z0-9_.]+ (they become sound event names)")

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    salt = args.seed_salt
    if args.redo:
        salt = salt or "redo"
        redone = 0
        for item in json.loads(Path(args.redo).read_text(encoding="utf-8")).get("blocked", []):
            clip = out_dir / item["voice"] / f"{item['hash']}.ogg"
            if item["voice"] in names and clip.exists():
                clip.unlink()
                redone += 1
        print(f"{redone} flagged clips removed, rendering them again")
    manifest_path = out_dir / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {"voices": {}}
    manifest.update({
        "model": cfg["model"]["name"],
        "version": cfg["model"]["version"],
        "repos": cfg["model"]["repos"],
        "code_licence": cfg["model"]["code_licence"],
        "weights_licence": cfg["model"]["weights_licence"],
        "licence_urls": cfg["model"]["licence_urls"],
        "audio": {"codec": "vorbis", "bitrate": "32k", "channels": 1, "sample_rate": SAMPLE_RATE,
                  "loudness_lufs": TARGET_LUFS},
        "device": args.device,
        "rendered": datetime.date.today().isoformat(),
    })
    engine = Engine(args.device)
    # clone voices need the design model first, the custom voice needs its own; group by kind
    for name in sorted(names, key=lambda n: cfg["voices"][n]["kind"]):
        voice = cfg["voices"][name]
        entries, seconds, retries = render_voice(engine, cfg, name, voice, rows, out_dir, args.batch, salt)
        previous = manifest["voices"].get(name, {}).get("entries", {})
        previous.update(entries)
        manifest["voices"][name] = {"kind": voice["kind"], "about": voice.get("about", voice.get("design", "")),
                                    "entries": previous, "last_render_seconds": round(seconds, 1),
                                    "last_retries": retries}
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    engine.release()
    print("done ->", manifest_path)


if __name__ == "__main__":
    main()
