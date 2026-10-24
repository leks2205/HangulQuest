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
import design_voices  # noqa: E402
import dialogue_texts  # noqa: E402
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


ADULTS = ["v_lazy", "v_jock", "v_cranky", "v_smug", "v_normal", "v_peppy", "v_snooty", "v_sisterly"]


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")


class VillagerVoices(unittest.TestCase):
    def test_ten_villager_voices_are_designed_and_checked(self):
        cfg = common.load_voices()
        mine = {k: v for k, v in cfg["voices"].items() if v.get("lines_only")}
        self.assertEqual(sorted(mine), sorted(ADULTS + ["v_child_m", "v_child_f"]))
        for name, v in mine.items():
            ref = v["reference"]
            self.assertIn("seed", v, name)
            self.assertLessEqual(ref["cer"], design_voices.MAX_CER, name)
            self.assertTrue(design_voices.in_band(v, ref["f0_median_hz"]), name)
        f0 = {k: v["reference"]["f0_median_hz"] for k, v in mine.items()}
        men = [k for k, v in mine.items() if v["gender"] == "male" and v["age"] == "adult"]
        women = [k for k, v in mine.items() if v["gender"] == "female" and v["age"] == "adult"]
        self.assertEqual(min(men, key=f0.get), "v_cranky")
        self.assertEqual(max(women, key=f0.get), "v_peppy")

    def test_the_word_voices_stay_as_they_were(self):
        cfg = common.load_voices()
        self.assertEqual(common.default_voices(cfg), ["sohee", "female2", "male1", "male2"])
        self.assertEqual(cfg["voices"]["male1"]["seed"], 23)

    def test_choose_keeps_cranky_lowest_and_peppy_highest(self):
        voices = {"a": {"gender": "male", "age": "adult", "personality": "CRANKY"},
                  "b": {"gender": "male", "age": "adult", "personality": "LAZY"},
                  "c": {"gender": "female", "age": "adult", "personality": "PEPPY"},
                  "d": {"gender": "female", "age": "adult", "personality": "NORMAL"},
                  "k": {"gender": "male", "age": "child"}}
        cand = {"a": [{"seed": 1, "cer": 0.0, "f0": 120}, {"seed": 2, "cer": 0.0, "f0": 100},
                      {"seed": 3, "cer": 0.2, "f0": 80}],
                "b": [{"seed": 1, "cer": 0.0, "f0": 90}, {"seed": 2, "cer": 0.01, "f0": 140}],
                "c": [{"seed": 1, "cer": 0.0, "f0": 230}, {"seed": 2, "cer": 0.0, "f0": 400},
                      {"seed": 3, "cer": 0.0, "f0": 300}],
                "d": [{"seed": 1, "cer": 0.0, "f0": 260}, {"seed": 2, "cer": 0.0, "f0": 170}],
                "k": [{"seed": 1, "cer": 0.0, "f0": 200}, {"seed": 2, "cer": 0.03, "f0": 300}]}
        got = {v: c and c["seed"] for v, c in design_voices.choose(voices, cand).items()}
        # b's best (90 Hz) is below every passing CRANKY, so b is picked again above CRANKY's lowest
        self.assertEqual(got, {"a": 2, "b": 2, "c": 3, "d": 1, "k": 2})

    def test_nothing_passes_gives_none(self):
        voices = {"x": {"gender": "female", "age": "adult", "personality": "NORMAL"}}
        self.assertIsNone(design_voices.choose(voices, {"x": [{"seed": 1, "cer": 0.0, "f0": 150}]})["x"])


class DialogueLines(unittest.TestCase):
    def repo(self, tmp, engine=True):
        root = Path(tmp)
        a = root / dialogue_texts.ASSETS
        write_json(a / "dialogue/_lexicon.json", {"words": []})
        write_json(a / "dialogue/farm.json", {"id": "farm", "trigger": {"profession": "minecraft:farmer", "baby": False},
                                              "nodes": {"hello": {"speaker": "farmer", "ko": "안녕하세요!", "ko_banmal": "안녕!",
                                                                  "choices": [{"ko": "네.", "next": "bye"}]},
                                                        "bye": {"speaker": "farmer", "ko": "잘 가요.",
                                                                "LAZY": {"ko": "음… 잘 가요."}}}})
        write_json(a / "dialogue/child.json", {"id": "child", "trigger": {"baby": True},
                                               "nodes": {"hi": {"speaker": "child", "ko": "안녕!"}}})
        write_json(a / "dialogue/elder.json", {"id": "elder", "trigger": {"profession": "minecraft:none"},
                                               "nodes": {"hi": {"speaker": "elder", "ko": "안녕하십니까?"}}})
        write_json(a / "dialogue/lazy_plains_intro.json", {"id": "lazy_plains_intro",
                                                           "trigger": {"personality": "LAZY", "region": "PLAINS"},
                                                           "nodes": {"hi": {"speaker": "villager", "ko": "밀밭이 좋아요.",
                                                                            "ko_banmal": "밀밭이 좋아."}}})
        write_json(a / "dialogue/snooty_talk.json", {"id": "snooty_talk", "personalities": ["SNOOTY"],
                                                     "nodes": {"hi": {"speaker": "villager", "ko": "좋습니다.",
                                                                      "ko_banmal": "좋아."}}})
        if engine:
            write_json(a / "requests/bread.json", {
                "id": "bread", "level": 1, "regions": ["PLAINS"], "kind": "GIVE",
                "goal": {"type": "give", "item": "minecraft:bread", "ko": "빵"},
                "offer": {"ko": "빵이 있어요?", "variants": {"JOCK": {"ko": "빵 있어요? 배고파요!"}}},
                "thanks": {"ko": "고마워요."}})
            write_json(a / "data/villager-names.json", {"male": ["민준", {"ko": "하늘", "roman": "haneul"}],
                                                        "female": ["지우"]})
        return root

    def collect(self, tmp, **kw):
        rows, problems = dialogue_texts.collect(self.repo(tmp, **kw.pop("engine", {}) or {}), log=lambda *a, **k: None,
                                                **kw)
        self.assertEqual(problems, [])
        return {r["id"]: r for r in rows}

    def ids(self, rows, prefix):
        return sorted(i for i in rows if i.startswith(prefix))

    def test_shared_lines_go_to_all_eight_adult_voices(self):
        with tempfile.TemporaryDirectory() as tmp:
            rows = self.collect(tmp)
            self.assertEqual([i for i in self.ids(rows, "line.farm.hello.") if ".banmal." not in i],
                             sorted(f"line.farm.hello.{v}" for v in ADULTS))
            self.assertEqual(rows["line.farm.hello.v_jock"]["voices"], ["v_jock"])

    def test_player_choices_are_not_voiced(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertFalse([r for r in self.collect(tmp).values() if r["ko"] == "네."])

    def test_a_personality_variant_replaces_the_shared_line_in_its_voice(self):
        with tempfile.TemporaryDirectory() as tmp:
            rows = self.collect(tmp)
            self.assertEqual(rows["line.farm.bye.v_lazy"]["ko"], "음… 잘 가요.")
            self.assertEqual(rows["line.farm.bye.v_jock"]["ko"], "잘 가요.")
            self.assertEqual(len(self.ids(rows, "line.farm.bye.")), 8)

    def test_personality_dialogue_only_in_that_voice(self):
        with tempfile.TemporaryDirectory() as tmp:
            rows = self.collect(tmp)
            self.assertEqual(self.ids(rows, "line.lazy_plains_intro.hi."), ["line.lazy_plains_intro.hi.banmal.v_lazy",
                                                                            "line.lazy_plains_intro.hi.v_lazy"])
            self.assertEqual(rows["line.lazy_plains_intro.hi.banmal.v_lazy"]["ko"], "밀밭이 좋아.")

    def test_banmal_only_for_the_personalities_that_switch(self):
        with tempfile.TemporaryDirectory() as tmp:
            rows = self.collect(tmp)
            self.assertEqual(self.ids(rows, "line.farm.hello.banmal."),
                             sorted(f"line.farm.hello.banmal.{v}" for v in ("v_lazy", "v_jock", "v_peppy", "v_sisterly")))
            self.assertEqual(self.ids(rows, "line.snooty_talk."), ["line.snooty_talk.hi.v_snooty"])

    def test_babies_speak_in_both_child_voices_and_personas_in_theirs(self):
        with tempfile.TemporaryDirectory() as tmp:
            rows = self.collect(tmp)
            self.assertEqual(self.ids(rows, "line.child."), ["line.child.hi.v_child_f", "line.child.hi.v_child_m"])
            self.assertEqual(self.ids(rows, "line.elder."), ["line.elder.hi.male1"])

    def test_request_lines(self):
        with tempfile.TemporaryDirectory() as tmp:
            rows = self.collect(tmp)
            self.assertEqual(len(self.ids(rows, "line.req_bread.offer.")), 8)
            self.assertEqual(rows["line.req_bread.offer.v_jock"]["ko"], "빵 있어요? 배고파요!")
            self.assertEqual(rows["line.req_bread.offer.v_smug"]["ko"], "빵이 있어요?")
            self.assertEqual(len(self.ids(rows, "line.req_bread.thanks.")), 8)
            self.assertFalse(self.ids(rows, "line.req_bread.goal"))

    def test_self_introductions_per_name_and_voice_of_that_gender(self):
        with tempfile.TemporaryDirectory() as tmp:
            rows = self.collect(tmp)
            self.assertEqual(self.ids(rows, "line.name.minjun."),
                             sorted(f"line.name.minjun.{v}" for v in ("v_lazy", "v_jock", "v_cranky", "v_smug", "v_child_m")))
            self.assertEqual(rows["line.name.minjun.v_lazy"]["ko"], "저는 민준이에요.")
            self.assertEqual(rows["line.name.minjun.v_child_m"]["ko"], "나는 민준이야.")
            self.assertEqual(rows["line.name.jiu.v_normal"]["ko"], "저는 지우예요.")
            self.assertEqual(rows["line.name.jiu.v_snooty"]["ko"], "저는 지우입니다.")
            self.assertEqual(rows["line.name.jiu.v_child_f"]["ko"], "나는 지우야.")
            self.assertIn("line.name.haneul.v_smug", rows)
            self.assertNotIn("line.name.jiu.v_lazy", rows)

    def test_event_ids_are_safe_and_unique(self):
        with tempfile.TemporaryDirectory() as tmp:
            for cid, row in self.collect(tmp).items():
                self.assertRegex(cid, r"^line\.[a-z0-9_]+\.[a-z0-9_]+(\.banmal)?\.[a-z0-9_]+$")
                self.assertTrue(cid.endswith("." + row["voices"][0]))

    def test_without_engine_data_it_says_so_and_strict_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            notes = []
            rows, _ = dialogue_texts.collect(self.repo(tmp, engine=False), log=lambda m, **k: notes.append(m))
            self.assertTrue(any("villager-names.json not found" in n for n in notes))
            self.assertFalse([r for r in rows if r["id"].startswith(("line.name.", "line.req_"))])
            with self.assertRaises(dialogue_texts.ContentError):
                dialogue_texts.collect(Path(tmp), strict=True, log=lambda *a, **k: None)

    def test_not_a_checkout_is_a_clear_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(dialogue_texts.ContentError, "checkout"):
                dialogue_texts.collect(Path(tmp), log=lambda *a, **k: None)

    def test_engine_voice_table_overrides_and_unknown_voices_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.repo(tmp)
            write_json(root / dialogue_texts.ASSETS / "data/voices.json",
                       {"personalities": {"LAZY": {"voice": "v_lazy"}}, "speakers": {"elder": "v_cranky"}})
            rows = self.collect(tmp)
            self.assertEqual(self.ids(rows, "line.elder."), ["line.elder.hi.v_cranky"])
            write_json(root / dialogue_texts.ASSETS / "data/voices.json", {"LAZY": "v_sleepy"})
            with self.assertRaisesRegex(dialogue_texts.ContentError, "v_sleepy"):
                dialogue_texts.collect(root, log=lambda *a, **k: None)

    def test_placeholders_are_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.repo(tmp)
            write_json(root / dialogue_texts.ASSETS / "dialogue/bad.json",
                       {"id": "bad", "nodes": {"hi": {"speaker": "farmer", "ko": "{player}님, 안녕하세요."}}})
            _, problems = dialogue_texts.collect(root, log=lambda *a, **k: None)
            self.assertTrue(any("placeholder" in p for p in problems))

    def test_romanisation(self):
        for ko, roman in (("민준", "minjun"), ("서연", "seoyeon"), ("하늘", "haneul"), ("지우", "jiu"),
                          ("예린", "yerin"), ("다은", "daeun"), ("별라", "byeolla")):
            self.assertEqual(dialogue_texts.romanise(ko), roman)

    def test_the_shipped_dialogues_have_lines(self):
        rows, problems = dialogue_texts.collect(common.REPO, log=lambda *a, **k: None)
        self.assertEqual(problems, [])
        self.assertIn("line.elder.hello.male1", {r["id"] for r in rows})


class LineRendering(unittest.TestCase):
    ROWS = [{"id": "word.stone", "ko": "돌"},
            {"id": "line.farm.hello.v_lazy", "ko": "안녕하세요!", "voices": ["v_lazy"]}]

    def test_a_line_renders_only_in_its_voice(self):
        self.assertEqual([r["id"] for r in render.rows_for("v_lazy", self.ROWS, False)], ["line.farm.hello.v_lazy"])
        self.assertEqual([r["id"] for r in render.rows_for("sohee", self.ROWS, True)], ["word.stone"])

    def test_the_line_voices_join_the_run(self):
        cfg = common.load_voices()
        self.assertEqual(render.voices_to_render(cfg, self.ROWS), ["sohee", "female2", "male1", "male2", "v_lazy"])
        self.assertEqual(render.voices_to_render(cfg, self.ROWS, ["male1"]), ["male1"])

    def test_missing_lines_check_only_their_voice(self):
        m = {"voices": {"sohee": {"entries": {"word.stone": {"text_sha1": common.sha1("돌")}}},
                        "v_lazy": {"entries": {"line.farm.hello.v_lazy": {"text_sha1": common.sha1("안녕하세요!")}}}}}
        self.assertEqual(select_texts.missing(self.ROWS, m, ["sohee"]), [])
        self.assertEqual([r["id"] for r in select_texts.missing(self.ROWS, m, ["sohee", "male1"])], ["word.stone"])

    def test_line_events_have_one_clip_and_words_are_kept(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, dist = Path(tmp) / "out", Path(tmp) / "dist"
            root.mkdir()
            entries = {}
            for voice, cid, ko in (("sohee", "word.stone", "돌"), ("v_lazy", "line.farm.hello.v_lazy", "안녕하세요!"),
                                   ("v_jock", "line.farm.hello.v_jock", "안녕하세요!"),
                                   ("v_jock", "line.farm.hello.v_lazy", "안녕하세요!"),  # stray: wrong voice
                                   ("v_lazy", "sentence.abc", "돌을 캐요.")):
                (root / voice).mkdir(exist_ok=True)
                h = common.sha1(ko, voice, "m")
                (root / voice / f"{h}.ogg").write_bytes(b"OggS")
                entries.setdefault(voice, {"kind": "clone", "entries": {}})["entries"][cid] = {
                    "hash": h, "text_sha1": common.sha1(ko), "ko": ko}
            manifest = {"model": "m", "version": "1", "code_licence": "Apache-2.0", "weights_licence": "Apache-2.0",
                        "licence_urls": ["https://example.invalid/licence"], "rendered": "2026-01-01", "voices": entries}
            (root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            with zipfile.ZipFile(build_pack.build(root, dist, words_only=True)) as zf:
                sounds = json.loads(zf.read("assets/hangulquest/sounds.json"))
                mcmeta = json.loads(zf.read("pack.mcmeta"))
                pack = json.loads(zf.read("manifest.json"))
            self.assertEqual(sounds["line.farm.hello.v_lazy"],
                             {"sounds": [{"name": "hangulquest:ko/v_lazy/line.farm.hello.v_lazy"}]})
            self.assertEqual(len(sounds["line.farm.hello.v_jock"]["sounds"]), 1)
            self.assertIn("word.stone", sounds)
            self.assertNotIn("sentence.abc", sounds)
            self.assertEqual(mcmeta["pack"]["min_format"], 97)
            self.assertIn("1.2.0", mcmeta["pack"]["description"])
            self.assertEqual(pack["packVersion"], "1.2.0")
            self.assertEqual(pack["lines"]["clips"], 2)

    def test_several_text_lists_filter_together(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = Path(tmp) / "a.jsonl", Path(tmp) / "b.jsonl"
            a.write_text('{"id": "word.stone", "ko": "돌"}\n', encoding="utf-8")
            b.write_text('{"id": "line.x.y.v_lazy", "ko": "네.", "voices": ["v_lazy"]}\n', encoding="utf-8")
            self.assertEqual(set(build_pack.current_texts([a, b])), {"word.stone", "line.x.y.v_lazy"})
            self.assertIsNone(build_pack.current_texts(None))


if __name__ == "__main__":
    unittest.main()
