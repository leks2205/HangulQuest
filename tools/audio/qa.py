#!/usr/bin/env python3
"""Automatic quality gate for rendered clips (no native speakers needed, so it only catches gross errors).

  qa.py texts.jsonl [--threshold 0.15] [--device cuda|cpu] [--model large-v3]

For every clip in <out>/manifest.json: decode the Ogg, run an offline ASR round trip (faster-whisper,
MIT; weights MIT), and compute the character error rate against the text after spaces and punctuation
are normalised. Clips above the threshold, clips with an implausible length, and clips whose
pronunciation form disagrees are listed in <out>/audio-blocklist.json, and build_pack.py leaves them out.

Pronunciation check: content/korean/pronunciation-review.tsv holds the standard pronunciation of every
concept (written by PronunciationReviewTest from quest/grammar/Pronunciation, columns id, ko, pron, ...).
Where it differs from the spelling ("[종노]"), the transcript may match either the spelling or that form.
Without the file the check is skipped.

Isolated one-syllable words are hard for any ASR (homophones), so a high CER there does not prove a bad
clip; the blocklist is a conservative filter and the numbers go to the 5% native review sample.
"""
import argparse
import csv
import datetime
import json
import os
import re
import subprocess
import sys
from pathlib import Path

import numpy as np

from common import MODELS, OUT, REPO, cer, normalise, read_jsonl

os.environ.setdefault("HF_HOME", str(MODELS))

NATIVE = ["", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉"]
NATIVE_TENS = ["", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"]
SINO = ["영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"]
NATIVE_COUNTERS = "개|마리|명|번|잔|장|그루|송이|채|자루|켤레|병|권|살|시"


def native(n: int) -> str:
    if n == 20:
        return "스무"
    return NATIVE_TENS[n // 10] + NATIVE[n % 10]


def sino(n: int) -> str:
    if n < 10:
        return SINO[n]
    tens = "" if n // 10 == 1 else SINO[n // 10]
    return tens + "십" + (SINO[n % 10] if n % 10 else "") if n < 100 else str(n)


def spell_digits(text: str) -> str:
    """ASR writes '세 개' as '3개'; read the digits back so they compare with the spelled-out text."""
    def counter(m):
        n = int(m.group(1))
        return (native(n) if 0 < n < 100 else m.group(1)) + m.group(2)
    text = re.sub(rf"(\d+)\s*({NATIVE_COUNTERS})", counter, text)
    return re.sub(r"\d+", lambda m: sino(int(m.group(0))) if int(m.group(0)) < 100 else m.group(0), text)


def preload_cuda12():
    """ctranslate2 wants CUDA 12 libraries; the venv has them as pip packages (nvidia-cublas-cu12, nvidia-cudnn-cu12)."""
    import ctypes
    import glob
    import site
    for root in site.getsitepackages():
        for pattern in ("nvidia/cublas/lib/libcublas*.so.12", "nvidia/cudnn/lib/libcudnn*.so.9"):
            for lib in sorted(glob.glob(os.path.join(root, pattern))):
                try:
                    ctypes.CDLL(lib, mode=ctypes.RTLD_GLOBAL)
                except OSError:
                    pass


# Whisper invents subtitle credits on near-silent tails; they are not in any text we render.
HALLUCINATION = re.compile(r"(자막.*|시청해\s*주셔서.*|구독.*좋아요.*|MBC\s*뉴스.*)", re.IGNORECASE)


def decode(path: Path, pad: float = 0.5) -> np.ndarray:
    """Ogg -> 16 kHz mono float32, padded with silence (Whisper dislikes clips that start on the first sample)."""
    raw = subprocess.run(
        ["ffmpeg", "-nostdin", "-v", "error", "-i", str(path), "-ac", "1", "-ar", "16000", "-f", "f32le", "-"],
        capture_output=True, check=True).stdout
    audio = np.frombuffer(raw, dtype=np.float32)
    silence = np.zeros(int(16000 * pad), dtype=np.float32)
    return np.concatenate([silence, audio, silence])


PRON_REVIEW = REPO / "content/korean/pronunciation-review.tsv"


def pron_forms(path=PRON_REVIEW):
    """concept id -> pronunciation without brackets, for the concepts that are not said as they are written."""
    forms = {}
    if not Path(path).exists():
        return forms
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            pron = re.sub(r"[\[\]]", "", row.get("pron", ""))
            if pron and pron != row.get("ko"):
                forms[row["id"]] = pron
    return forms


def stats(values):
    if not values:
        return {"n": 0}
    a = np.array(values)
    return {"n": len(values), "mean": round(float(a.mean()), 4), "median": round(float(np.median(a)), 4),
            "p95": round(float(np.percentile(a, 95)), 4), "max": round(float(a.max()), 4)}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("texts", nargs="?", help="unused except to check ids; the manifest holds the texts")
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--threshold", type=float, default=0.15)
    ap.add_argument("--model", default="large-v3")
    ap.add_argument("--device", default="cpu")
    args = ap.parse_args()

    out = Path(args.out)
    manifest = json.loads((out / "manifest.json").read_text(encoding="utf-8"))
    prons = pron_forms()

    from faster_whisper import WhisperModel
    device = "cuda" if args.device.startswith("cuda") else "cpu"
    if device == "cuda":
        preload_cuda12()
    asr = WhisperModel(args.model, device=device, compute_type="float16" if device == "cuda" else "int8",
                       download_root=str(MODELS / "whisper"))

    report, block = [], []
    for voice, vdata in manifest["voices"].items():
        for cid, entry in vdata["entries"].items():
            ogg = out / voice / f"{entry['hash']}.ogg"
            if not ogg.exists():
                continue
            audio = decode(ogg)
            seconds = len(audio) / 16000 - 1.0
            segments, _ = asr.transcribe(audio, language="ko", beam_size=5, temperature=0.0,
                                         condition_on_previous_text=False, vad_filter=False)
            heard = spell_digits(HALLUCINATION.sub("", "".join(s.text for s in segments)))
            ko = entry["ko"]
            score = cer(ko, heard)
            row = {"voice": voice, "id": cid, "hash": entry["hash"], "ko": ko, "asr": heard,
                   "cer": round(score, 4), "seconds": round(seconds, 2)}
            reasons = []
            concept = cid[5:] if cid.startswith("word.") else None
            if concept in prons:
                spoken = cer(prons[concept], heard)
                row["pron"] = prons[concept]
                row["cer_pron"] = round(spoken, 4)
                score = min(score, spoken)
            if score > args.threshold:
                reasons.append(f"cer {score:.2f} > {args.threshold}")
            chars = max(1, len(normalise(ko)))
            if seconds > 0.9 * chars + 1.5 or seconds < 0.1 * chars:
                reasons.append(f"length {seconds:.1f}s for {chars} syllables")
            if reasons:
                row["flag"] = "; ".join(reasons)
                block.append({"voice": voice, "id": cid, "hash": entry["hash"], "reason": row["flag"]})
            report.append(row)
        print(f"[{voice}] checked", flush=True)

    summary = {"threshold": args.threshold, "asr_model": f"faster-whisper {args.model} (MIT)",
               "date": datetime.date.today().isoformat(), "clips": len(report), "flagged": len(block),
               "cer": stats([r["cer"] for r in report]),
               "cer_words": stats([r["cer"] for r in report if r["id"].startswith("word.")]),
               "cer_sentences": stats([r["cer"] for r in report if r["id"].startswith("sentence.")]),
               "by_voice": {v: stats([r["cer"] for r in report if r["voice"] == v]) for v in manifest["voices"]}}
    (out / "qa-report.json").write_text(
        json.dumps({"summary": summary, "clips": report}, ensure_ascii=False, indent=1), encoding="utf-8")
    (out / "audio-blocklist.json").write_text(
        json.dumps({"schema": 1, "blocked": block}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
