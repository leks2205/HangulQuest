#!/usr/bin/env python3
"""Tests for the parts of the audio pipeline that need no model: python3 -m unittest tools/audio/test_audio_tools.py"""
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import build_pack  # noqa: E402
import common  # noqa: E402
import hvpt_texts  # noqa: E402
import qa  # noqa: E402
import render  # noqa: E402
import select_texts  # noqa: E402


class TextMetrics(unittest.TestCase):
    def test_cer_ignores_spaces_and_punctuation(self):
        self.assertEqual(common.cer("구리 상자", " 구리상자. "), 0.0)

    def test_cer_counts_wrong_syllables(self):
        self.assertAlmostEqual(common.cer("만들다", "만들자"), 1 / 3)

    def test_cer_is_capped_at_one(self):
        self.assertEqual(common.cer("말", "마을 사람들이"), 1.0)

    def test_digits_are_read_back_as_korean(self):
        self.assertEqual(qa.spell_digits("철 주괴 3개"), "철 주괴 세개")
        self.assertEqual(qa.spell_digits("12번"), "열두번")
        self.assertEqual(qa.spell_digits("5층"), "오층")

    def test_cache_key_depends_on_text_voice_and_model(self):
        a = common.sha1("돌", "sohee", "m")
        self.assertNotEqual(a, common.sha1("돌", "male1", "m"))
        self.assertNotEqual(a, common.sha1("흙", "sohee", "m"))
        self.assertNotEqual(a, common.sha1("돌", "sohee", "m2"))

    def test_pronunciation_forms_come_from_the_review_table(self):
        with tempfile.TemporaryDirectory() as tmp:
            table = Path(tmp) / "pron.tsv"
            table.write_text("id\tko\tpron\trules\tsource\torigin\n"
                             "stone\t돌\t[돌]\t\trule\tnative\n"
                             "soup\t국물\t[궁물]\tnasalisation\trule\tnative\n", encoding="utf-8")
            self.assertEqual(qa.pron_forms(table), {"soup": "궁물"})
            self.assertEqual(qa.pron_forms(Path(tmp) / "missing.tsv"), {})

    def test_shipped_review_table_has_forms(self):
        forms = qa.pron_forms()
        self.assertGreater(len(forms), 100)
        self.assertEqual(forms["dirt"], "흑")

    def test_implausible_lengths_are_caught(self):
        self.assertFalse(render.plausible("돌", 6.0))
        self.assertTrue(render.plausible("돌", 0.5))
        self.assertFalse(render.plausible("모루는 철 블록 세 개와 철 주괴 네 개로 만들어요", 0.5))


class PackBuild(unittest.TestCase):
    def make_render(self, root: Path, blocked=()):
        entries = {}
        for voice in ("sohee", "male1"):
            (root / voice).mkdir()
            ents = {}
            for cid, ko in (("word.stone", "돌"), ("word.dirt", "흙"), ("sentence.abc123", "돌을 캐요.")):
                h = common.sha1(ko, voice, "m")
                (root / voice / f"{h}.ogg").write_bytes(b"OggS" + voice.encode())
                ents[cid] = {"hash": h, "text_sha1": common.sha1(ko), "ko": ko}
            entries[voice] = {"kind": "custom", "entries": ents}
        manifest = {"model": "m", "version": "1", "code_licence": "Apache-2.0", "weights_licence": "Apache-2.0",
                    "licence_urls": ["https://example.invalid/licence"], "rendered": "2026-01-01", "voices": entries}
        (root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        (root / "audio-blocklist.json").write_text(json.dumps({"blocked": list(blocked)}), encoding="utf-8")

    def test_pack_layout_and_blocklist(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, dist = Path(tmp) / "out", Path(tmp) / "dist"
            root.mkdir()
            self.make_render(root, blocked=[{"voice": "sohee", "id": "word.dirt"}])
            target = build_pack.build(root, dist)
            with zipfile.ZipFile(target) as zf:
                names = set(zf.namelist())
                mcmeta = json.loads(zf.read("pack.mcmeta"))
                sounds = json.loads(zf.read("assets/hangulquest/sounds.json"))
            self.assertEqual(mcmeta["pack"]["min_format"], 97)
            self.assertIn("assets/hangulquest/sounds/ko/sohee/word.stone.ogg", names)
            self.assertNotIn("assets/hangulquest/sounds/ko/sohee/word.dirt.ogg", names)
            self.assertEqual(len(sounds["word.stone"]["sounds"]), 2)
            self.assertEqual(sounds["word.dirt"], {"sounds": [{"name": "hangulquest:ko/male1/word.dirt"}]})

    def test_event_without_clips_is_not_written(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, dist = Path(tmp) / "out", Path(tmp) / "dist"
            root.mkdir()
            self.make_render(root, blocked=[{"voice": v, "id": "word.dirt"} for v in ("sohee", "male1")])
            with zipfile.ZipFile(build_pack.build(root, dist)) as zf:
                sounds = json.loads(zf.read("assets/hangulquest/sounds.json"))
            self.assertNotIn("word.dirt", sounds)
            self.assertIn("word.stone", sounds)

    def test_pack_has_no_repo_or_home_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, dist = Path(tmp) / "out", Path(tmp) / "dist"
            root.mkdir()
            self.make_render(root)
            with zipfile.ZipFile(build_pack.build(root, dist)) as zf:
                text = (zf.read("manifest.json") + zf.read("NOTICE.txt")).decode("utf-8")
            self.assertNotIn("/home/", text)

    def test_words_only_pack_leaves_sentence_clips_out(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, dist = Path(tmp) / "out", Path(tmp) / "dist"
            root.mkdir()
            self.make_render(root)
            with zipfile.ZipFile(build_pack.build(root, dist, words_only=True)) as zf:
                sounds = json.loads(zf.read("assets/hangulquest/sounds.json"))
                names = zf.namelist()
            self.assertIn("word.stone", sounds)
            self.assertNotIn("sentence.abc123", sounds)
            self.assertNotIn("assets/hangulquest/sounds/ko/male1/sentence.abc123.ogg", names)
            with zipfile.ZipFile(build_pack.build(root, dist)) as zf:
                self.assertIn("sentence.abc123", json.loads(zf.read("assets/hangulquest/sounds.json")))

    def test_pack_drops_clips_of_texts_that_changed_or_are_gone(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, dist = Path(tmp) / "out", Path(tmp) / "dist"
            root.mkdir()
            self.make_render(root)
            texts = Path(tmp) / "texts.jsonl"
            # dirt was reworded, stone is unchanged
            texts.write_text('{"id": "word.stone", "ko": "돌"}\n{"id": "word.dirt", "ko": "진흙"}\n', encoding="utf-8")
            with zipfile.ZipFile(build_pack.build(root, dist, texts=texts)) as zf:
                sounds = json.loads(zf.read("assets/hangulquest/sounds.json"))
            self.assertIn("word.stone", sounds)
            self.assertNotIn("word.dirt", sounds)
            # dirt is gone from the list altogether
            texts.write_text('{"id": "word.stone", "ko": "돌"}\n', encoding="utf-8")
            with zipfile.ZipFile(build_pack.build(root, dist, texts=texts)) as zf:
                self.assertNotIn("assets/hangulquest/sounds/ko/male1/word.dirt.ogg", zf.namelist())
            with zipfile.ZipFile(build_pack.build(root, dist)) as zf:
                self.assertIn("assets/hangulquest/sounds/ko/male1/word.dirt.ogg", zf.namelist())


class Selection(unittest.TestCase):
    ROWS = [{"id": "word.stone", "ko": "돌"}, {"id": "word.dirt", "ko": "흙"}, {"id": "word.sand", "ko": "모래"}]

    def manifest(self, **voices):
        return {"voices": {v: {"entries": {cid: {"text_sha1": common.sha1(ko)} for cid, ko in ents.items()}}
                           for v, ents in voices.items()}}

    def test_missing_is_what_lacks_a_clip_for_the_same_text_in_any_voice(self):
        m = self.manifest(sohee={"word.stone": "돌", "word.dirt": "흙"}, male1={"word.stone": "돌", "word.dirt": "진흙"})
        self.assertEqual([r["id"] for r in select_texts.missing(self.ROWS, m)], ["word.dirt", "word.sand"])

    def test_everything_is_missing_without_a_manifest(self):
        self.assertEqual(select_texts.missing(self.ROWS, {}), self.ROWS)

    def test_all_sentences_keep_the_same_filter_as_the_sample(self):
        with tempfile.TemporaryDirectory() as tmp:
            tsv = Path(tmp) / "s.tsv"
            lines = ["id\tlevel\tsource\tko\tgoal",
                     "a\t1\tauthored:x\t돌을 캐요.\t",
                     "b\t1\tauthored:x\t돌을 캐요.\t",           # duplicate
                     "c\t1\tgenerated:clue\t흙을 캐세요.\t",     # generated
                     "d\t2\tauthored:x\t모험의 길\t",            # no end mark
                     "e\t2\tauthored:x\t집을 지어요!\t"]
            tsv.write_text("\n".join(lines) + "\n", encoding="utf-8")
            everything = select_texts.pick_sentences(None, tsv)
            self.assertEqual([r["ko"] for r in everything], ["돌을 캐요.", "집을 지어요!"])
            self.assertEqual(len(select_texts.pick_sentences(1, tsv)), 1)
            self.assertTrue(everything[0]["id"].startswith("sentence."))

    def test_a_repeat_render_uses_other_seeds(self):
        self.assertEqual(render.seed_for("sohee", 0, 0), render.seed_for("sohee", 0, 0))
        self.assertNotEqual(render.seed_for("sohee", 0, 0), render.seed_for("sohee", 0, 0, "redo"))


class SoundForge(unittest.TestCase):
    def test_every_text_has_a_safe_unique_id(self):
        rows = hvpt_texts.texts(hvpt_texts.load())
        ids = [r["id"] for r in rows]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertGreater(len(ids), 100)
        for cid in ids:
            self.assertRegex(cid, r"^hvpt\.[a-z0-9_]+\.[a-z]+$")

    def test_held_out_voices_are_separate_and_not_in_the_default_render(self):
        data = hvpt_texts.load()
        cfg = common.load_voices()
        held = set(data["heldOutVoices"])
        self.assertTrue(held)
        self.assertFalse(held & set(data["trainingVoices"]))
        self.assertFalse(held & set(common.default_voices(cfg)))
        for name in held | set(data["trainingVoices"]):
            self.assertIn(name, cfg["voices"])

    def test_every_dialogue_persona_names_a_voice_of_the_pack(self):
        cfg = common.load_voices()
        self.assertIn("elder", cfg["personas"])
        for name, persona in cfg["personas"].items():
            self.assertIn(persona["voice"], cfg["voices"], name)
            self.assertFalse(cfg["voices"][persona["voice"]].get("hvpt_only"), name)

    def test_forms_belong_to_their_contrast(self):
        data = hvpt_texts.load()
        forms = {c["id"]: {f["key"] for f in c["forms"]} for c in data["contrasts"]}
        for item in data["items"]:
            self.assertGreaterEqual(len(item["forms"]), 2)
            self.assertLessEqual(set(item["forms"]), forms[item["contrast"]], item["id"])

    def test_the_stimulus_event_has_one_clip_per_voice(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, dist = Path(tmp) / "out", Path(tmp) / "dist"
            root.mkdir()
            entries = {}
            for voice in ("sohee", "male1"):
                (root / voice).mkdir()
                h = common.sha1("딸", voice, "m")
                (root / voice / f"{h}.ogg").write_bytes(b"OggS")
                entries[voice] = {"kind": "custom", "entries": {"hvpt.dt_w1.t": {"hash": h, "ko": "딸"}}}
            manifest = {"model": "m", "version": "1", "code_licence": "Apache-2.0", "weights_licence": "Apache-2.0",
                        "licence_urls": ["https://example.invalid/licence"], "rendered": "2026-01-01", "voices": entries}
            (root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            with zipfile.ZipFile(build_pack.build(root, dist)) as zf:
                sounds = json.loads(zf.read("assets/hangulquest/sounds.json"))
                pack = json.loads(zf.read("manifest.json"))
            self.assertEqual(sounds["hvpt.dt_w1.t.sohee"], {"sounds": [{"name": "hangulquest:ko/sohee/hvpt.dt_w1.t"}]})
            self.assertEqual(sounds["hvpt.dt_w1.t.male1"], {"sounds": [{"name": "hangulquest:ko/male1/hvpt.dt_w1.t"}]})
            self.assertNotIn("hvpt.dt_w1.t", sounds)
            self.assertEqual(pack["hvpt"]["source"], "tts")
            self.assertEqual(pack["voices"]["sohee"]["entries"]["hvpt.dt_w1.t"]["source"], "tts")


if __name__ == "__main__":
    unittest.main()
