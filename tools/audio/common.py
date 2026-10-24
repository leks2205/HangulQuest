"""Shared paths and helpers for the offline audio pipeline (nothing here touches the network)."""
import hashlib
import json
import os
import re
import unicodedata
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DATA = REPO / "src/main/resources/assets/hangulquest/data"

# Everything big lives outside the repo. Override with HQ_AUDIO_HOME if you keep it elsewhere.
HOME = Path(os.environ.get("HQ_AUDIO_HOME", Path.home() / "hangulquest-audio"))
MODELS = HOME / "models"
OUT = HOME / "out"
DIST = HOME / "dist"
WORK = HOME / "work"

VOICES_FILE = Path(__file__).with_name("voices.json")
# Held-out voices of the sound forge; they are marked "hvpt_only" and stay out of the default voice list.
HVPT_VOICES_FILE = Path(__file__).with_name("voices_hvpt.json")
HVPT_DATA = DATA / "hvpt.json"


def sha1(*parts: str) -> str:
    h = hashlib.sha1()
    for part in parts:
        h.update(part.encode("utf-8"))
        h.update(b"\x00")
    return h.hexdigest()


def read_jsonl(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def load_voices():
    cfg = json.loads(VOICES_FILE.read_text(encoding="utf-8"))
    if HVPT_VOICES_FILE.exists():
        cfg["voices"].update(json.loads(HVPT_VOICES_FILE.read_text(encoding="utf-8"))["voices"])
    return cfg


def default_voices(cfg):
    """The voices a plain render uses: the word voices (not the held-out ones, not the villager voices)."""
    return [name for name, voice in cfg["voices"].items() if not voice.get("hvpt_only") and not voice.get("lines_only")]


_PUNCT = re.compile(r"[\s​.,!?~…'\"“”‘’()\[\]{}:;·\-–—/]+")


def normalise(text: str) -> str:
    """Spaces and punctuation out, NFC, so 'ASR said it differently spaced' is not an error."""
    return _PUNCT.sub("", unicodedata.normalize("NFC", text))


def edit_distance(a: str, b: str) -> int:
    if a == b:
        return 0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def cer(reference: str, hypothesis: str) -> float:
    ref, hyp = normalise(reference), normalise(hypothesis)
    if not ref:
        return 0.0 if not hyp else 1.0
    return min(1.0, edit_distance(ref, hyp) / len(ref))
