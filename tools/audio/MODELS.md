# Korean TTS models for the voice pack

Rule: **code and weights both permissive** (MIT, Apache-2.0, BSD, CC-BY), no non-commercial training data
that the weights inherit, and the generated audio may be redistributed. Checked 2026-10 against the
upstream model cards and repositories.

## Candidates

| Model | Korean? | Code licence | Weights licence | Voices (Korean) | Verdict |
|---|---|---|---|---|---|
| [Qwen3-TTS 12Hz 1.7B](https://github.com/QwenLM/Qwen3-TTS) (CustomVoice, VoiceDesign, Base) | yes, one of 10 languages | Apache-2.0 | Apache-2.0 ([CustomVoice](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice), [VoiceDesign](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign), [Base](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-Base)) | 9 presets incl. **Sohee** (native Korean female); any number of designed voices from a text description | **chosen** |
| [MeloTTS-Korean](https://huggingface.co/myshell-ai/MeloTTS-Korean) | yes | MIT | MIT | exactly one (`KR`) | rejected: one voice only, and the model card names no training data (the common public Korean corpus, KSS, is CC BY-NC-SA) |
| [Piper](https://huggingface.co/rhasspy/piper-voices/tree/main/ko/ko_KR) `ko_KR` | yes | MIT | MIT tag on the repo, but the only Korean voice is `kss` | 1 | rejected: trained on [KSS](https://huggingface.co/datasets/Bingsu/KSS_Dataset), licence CC BY-NC-SA 4.0 (non-commercial, share-alike), which the weights inherit |
| [CosyVoice 2 / 3](https://github.com/FunAudioLLM/CosyVoice) | yes | Apache-2.0 | Apache-2.0 | none built in; voice cloning needs reference clips | not chosen: needs licensed reference speakers, README states "academic purposes only" for demo content, training data undocumented |
| [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) | **no** ([VOICES.md](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md): en, ja, zh, es, fr, hi, it, pt) | Apache-2.0 | Apache-2.0 | 0 | rejected: no Korean |
| [F5-TTS](https://huggingface.co/SWivid/F5-TTS) | not officially | MIT | **CC-BY-NC-4.0** (Emilia is CC-BY-NC) | - | rejected: non-commercial weights |
| XTTS-v2 | yes | MPL-2.0 | CPML (non-commercial) | - | rejected: CPML |
| Fish-Speech | yes | - | NC licence | - | rejected: non-commercial |

Not evaluated in depth (no Korean support found, or no checkpoint with a clear data licence): Zonos, community
VITS Korean checkpoints. The XTTS and Fish-Speech rows follow the task brief's rejection rule and were not
re-verified.

## Decision: Qwen3-TTS 12Hz 1.7B

- Code: Apache-2.0 ([repository](https://github.com/QwenLM/Qwen3-TTS)). Weights: Apache-2.0 (licence field of
  all three model cards above). The `qwen-tts` PyPI package is the same repository.
- Apache-2.0 puts no restriction on output, so the generated audio can be redistributed and used commercially.
  The training data is not itemised in the [technical report](https://arxiv.org/abs/2601.15621) (5 million
  hours, 10 languages), so "no NC data" rests on the licence the authors chose, not on a data audit. If
  upstream ever states otherwise, rebuild the pack with another model: the pipeline is model-agnostic apart
  from `render.py`.
- ASR for the quality gate: faster-whisper (MIT) with the `large-v3` weights (MIT).

### Voices (4)

| Voice | How | Character |
|---|---|---|
| `sohee` | CustomVoice preset `Sohee` | native Korean female, warm |
| `female2` | VoiceDesign once, then cloned with the Base model | Seoul-standard woman, late 20s, teacher-like |
| `male1` | same | Seoul-standard man, 30s, medium-low |
| `male2` | same | Seoul-standard man, 20s, brighter |

A VoiceDesign voice is different on every call, so each designed voice is generated **once** as a
reference clip (`~/hangulquest-audio/models/refs/<voice>.wav`, fixed seed, never committed) and then cloned
with the Base model for every text. The other presets (Vivian, Ryan, ...) are native Chinese/English speakers;
they speak Korean with an accent and are not used.
The reference clip hash is part of the cache key, so changing a voice re-renders only that voice.

### Quality notes

- Compute: about 0.6x real time on an RTX 5070 (bf16, no flash-attn), about 4.5x real time on 16 CPU
  cores (fp32). The pilot rendered on the GPU; CPU would need hours per voice.
- Short single words are the weak spot (the model sometimes lengthens or clips a syllable). `render.py` renders
  a clip again (up to 3 tries) when its length is implausible, and `qa.py` checks every clip with an ASR round trip.
- No native has listened yet. The pack stays optional and marked synthetic until natives review the 5% sample.
- Liaison and nasalisation: `qa.py` reads the bracket forms of `content/korean/pronunciation-review.tsv` (written by
  `PronunciationReviewTest` from the pronunciation generator) and accepts a transcript that matches either form.

## Pilot result (first 300 words + 100 quest sentences, 4 voices)

Python 3.12 venv outside the repo (`requirements.txt`; ffmpeg must be installed). Steps: `select_texts.py`,
`render.py ... --device cuda:0`, `qa.py --device cuda`, `build_pack.py`.

- 1600 clips rendered on the GPU in about 12 minutes of generation (CPU would take hours).
- ASR round trip (faster-whisper large-v3), threshold CER 0.15: mean CER 0.122 over all clips (words 0.152,
  sentences 0.030); 306 clips flagged and left out of the pack; the 1294 kept clips have mean CER 0.006.
- One-syllable words are the weak spot (mean CER 0.40), words of 5+ syllables are fine (0.07). The preset `sohee`
  voice is the least reliable (mean 0.21), the designed `female2` voice the most reliable (0.07).
- Pack: 11.8 MB, 1294 clips, 374 events, about 9 KB per clip.
- Loudness of a 78-clip sample: mean -16.4 LUFS (range -19 to -15.4), peaks at most -0.5 dBFS.

## Update 2026-10 (all words and eligible sentences)

`select_texts.py --all` takes every concept and every authored sentence that passes the length and end-mark
filter (about 1300 words, 1330 sentences) instead of the 300 + 100 sample, and reports how many still lack a
clip. `render.py` skips cached clips, so only new or reworded texts render; `render.py --redo` removes the clips
`qa.py` flagged and renders them once more with other seeds. `qa.py texts.jsonl` checks only those texts and
reuses earlier results. `build_pack.py --texts` leaves out clips of texts that no longer exist.
Memory: batch 4 (`--batch 4`) and `PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True` keep long sentences within
about 8 GB when another model shares the GPU.

**Shipped pack: words only.** The mod plays only `word.*` events so far, so the pack is built with
`build_pack.py --texts texts.jsonl --words-only`: about 41 MB and 4,541 clips, instead of 108 MB with the sentence
clips. Sentence clips stay rendered in the cache. Drop `--words-only` once something plays them (word cards with
example sentences, shadowing).
