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
import qa  # noqa: E402
import render  # noqa: E402


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
            for cid, ko in (("word.stone", "돌"), ("word.dirt", "흙")):
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


if __name__ == "__main__":
    unittest.main()
