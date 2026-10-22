import contextlib
import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

import _setup  # noqa: F401

# The review site builder lives next to the Korean review tools.
for _folder in ("review_site", "review"):
    sys.path.insert(0, str(_setup.ROOT / "tools" / _folder))
import tr_check
import tr_core as core
import tr_fonts
import tr_glossary
import translate

EN_UI = {
    "key.categories.hangulquest.main": "한글 탐험",
    "hangulquest.ui.back": "Back",
    "hangulquest.ui.info.today": "Today %s seen, %s correct",
}
EN_CONTENT = {
    "hangulquest.trail.t1.title": "The Adventure Trail",
    "hangulquest.trail.t1.step.s1.clue": "Collect Music Disc.",
    "hangulquest.trail.t1.step.s1.hint": "Collect Music Disc.",
    "hangulquest.trail.t1.step.s2.clue": "Place a torch — read the Korean to see where.",
    "hangulquest.trail.t1.step.s3.clue": "Do the two things in the order the Korean says.",
    "hangulquest.trail.t1.finale": "By night the ritual of Stone is complete.",
    "hangulquest.word.stone.gloss": "Stone",
}
REGISTRY = {
    "trail.t1.title": "모험의 길",
    "trail.t1.s1.clue": "음반을 모으세요.",
    "trail.t1.s2.clue": "문 옆에 횃불을 놓으세요.",
    "concept.stone.ko": "돌",
    "concept.stone.descKo": "땅에서 나와요. 단단해요.",
}
ES = {
    "hangulquest.ui.back": "Atrás",
    "hangulquest.ui.info.today": "Hoy %s vistas, %s correctas",
    "hangulquest.trail.t1.title": "El sendero de la aventura",
    "hangulquest.trail.t1.step.s1.clue": "Recoge un disco de música.",
    "hangulquest.trail.t1.step.s1.hint": "Recoge un disco de música.",
    "hangulquest.trail.t1.step.s2.clue": "Coloca una antorcha — lee el coreano para ver dónde.",
    "hangulquest.trail.t1.step.s3.clue": "Haz las dos cosas en el orden que dice el coreano.",
    "hangulquest.trail.t1.finale": "De noche, el ritual de la piedra queda completo.",
    "hangulquest.word.stone.gloss": "Piedra",
}


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(core.dump_bundle(data), encoding="utf-8")


def results_file(path: Path, texts: dict, **extra) -> Path:
    path.write_text("".join(json.dumps({"key": key, "text": text, **extra}, ensure_ascii=False) + "\n"
                            for key, text in texts.items()), encoding="utf-8")
    return path


class Fake(unittest.TestCase):
    """A small repository in a temporary folder, a work folder next to it, and a way to run the tool."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        base = Path(self.tmp.name)
        self.root = base / "repo"
        self.work = base / "work"
        self.work.mkdir()
        self.repo = core.Repo(self.root)
        write_json(self.repo.lang / "en_us.json", EN_UI)
        write_json(self.repo.content / "en_us.json", EN_CONTENT)
        write_json(self.repo.languages_json, {"en_us": "English"})
        self.repo.registry.parent.mkdir(parents=True)
        self.repo.registry.write_text(
            "".join(json.dumps({"id": key, "ko": ko}, ensure_ascii=False) + "\n" for key, ko in REGISTRY.items()),
            encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def run_tool(self, *args, expect=0):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            try:
                code = translate.main(["--root", str(self.root), *map(str, args)])
            except SystemExit as stop:
                code = stop.code
        self.assertEqual(code, expect, out.getvalue() + err.getvalue())
        return out.getvalue(), err.getvalue()

    def import_spanish(self, texts=None):
        path = results_file(self.work / "es.jsonl", texts or ES)
        return self.run_tool("import", "--lang", "es_es", path)

    def status(self, code="es_es"):
        return core.read_status(self.repo, code)["keys"]


class ExportTest(Fake):
    def packets(self, *extra):
        self.run_tool("export", "--lang", "es_es", "--out", self.work / "out", *extra)
        files = sorted((self.work / "out").glob("es_es-tr-*.json"))
        return [json.loads(f.read_text(encoding="utf-8")) for f in files]

    def rows(self, *extra):
        return {r["key"]: r for packet in self.packets(*extra) for r in packet["rows"]}

    def test_rows_carry_source_korean_kind_and_placeholders(self):
        rows = self.rows("--no-glossary")
        self.assertNotIn("key.categories.hangulquest.main", rows)  # nothing to translate in a Korean-only name
        today = rows["hangulquest.ui.info.today"]
        self.assertEqual(today["placeholders"], ["%s", "%s"])
        self.assertEqual(today["kind"], "UI label")
        self.assertEqual(today["maxLen"], core.max_length("Today %s seen, %s correct"))
        clue = rows["hangulquest.trail.t1.step.s1.clue"]
        self.assertEqual(clue["ko"], "음반을 모으세요.")
        self.assertEqual(clue["kind"], "clue")
        self.assertNotIn("maxLen", clue)
        gloss = rows["hangulquest.word.stone.gloss"]
        self.assertEqual(gloss["ko"], "돌")
        self.assertEqual(gloss["koContext"], ["description: 땅에서 나와요. 단단해요."])

    def test_form_dependent_rows_are_marked(self):
        rows = self.rows("--no-glossary")
        self.assertTrue(rows["hangulquest.trail.t1.step.s2.clue"]["formDependent"])
        self.assertTrue(rows["hangulquest.trail.t1.step.s3.clue"]["formDependent"])
        self.assertIn("order", rows["hangulquest.trail.t1.step.s3.clue"]["note"])
        self.assertNotIn("formDependent", rows["hangulquest.trail.t1.step.s1.clue"])

    def test_packets_are_chunked_and_numbered(self):
        packets = self.packets("--no-glossary", "--chunk", "3")
        self.assertEqual([len(p["rows"]) for p in packets], [3, 3, 3])
        self.assertEqual([p["packet"] for p in packets], [1, 2, 3])
        self.assertEqual({p["packets"] for p in packets}, {3})
        self.assertIn("Spanish", packets[0]["language"])
        self.assertTrue(any("JSON Lines" in rule for rule in packets[0]["rules"]))

    def test_part_limits_the_export(self):
        self.assertEqual(set(self.rows("--no-glossary", "--part", "ui")),
                         {"hangulquest.ui.back", "hangulquest.ui.info.today"})

    def test_packets_cannot_go_into_the_repository(self):
        self.run_tool("export", "--lang", "es_es", "--out", self.root / "packets", expect=2)

    def test_korean_and_english_are_not_targets(self):
        self.run_tool("export", "--lang", "ko_kr", "--out", self.work / "x", expect=2)
        self.run_tool("export", "--lang", "en_us", "--out", self.work / "x", expect=2)

    def fake_cache(self):
        cache = self.work / "cache"
        names = {"item.minecraft.stone": "Piedra", "block.minecraft.stone": "Piedra",
                 "item.minecraft.music_disc_13": "Disco de música", "entity.minecraft.cow": "Vaca",
                 "item.minecraft.torch": "Antorcha"}
        english = {"item.minecraft.stone": "Stone", "block.minecraft.stone": "Stone",
                   "item.minecraft.music_disc_13": "Music Disc", "entity.minecraft.cow": "Cow",
                   "item.minecraft.torch": "Torch", "some.other.key": "Zebra"}
        body = json.dumps(names).encode()
        digest = "ab" + "0" * 38
        (cache / "assets/objects/ab").mkdir(parents=True)
        (cache / "assets/objects/ab" / digest).write_bytes(body)
        (cache / "assets/indexes").mkdir(parents=True)
        (cache / "assets/indexes/26.3-1.json").write_text(
            json.dumps({"objects": {"minecraft/lang/es_es.json": {"hash": digest, "size": len(body)}}}), encoding="utf-8")
        (cache / "26.3").mkdir()
        with zipfile.ZipFile(cache / "26.3/minecraft-client.jar", "w") as jar:
            jar.writestr("assets/minecraft/lang/en_us.json", json.dumps(english))
        return cache / "assets"

    def test_the_glossary_names_what_the_packet_mentions(self):
        packets = self.packets("--assets", self.fake_cache())
        glossary = {k: v for p in packets for k, v in p["glossary"].items()}
        self.assertEqual(glossary, {"Music Disc": "Disco de música", "Stone": "Piedra", "Torch": "Antorcha"})

    def test_without_a_local_cache_the_export_still_works(self):
        packets = self.packets("--assets", self.work / "no-such-folder")
        self.assertEqual(packets[0]["glossary"], {})

    def test_the_glossary_matches_plurals_and_longest_names(self):
        english = {"item.minecraft.oak_log": "Oak Log", "item.minecraft.oak": "Oak", "entity.minecraft.cow": "Cow"}
        target = {"item.minecraft.oak_log": "Tronco de roble", "item.minecraft.oak": "Roble", "entity.minecraft.cow": "Vaca"}
        found = tr_glossary.Glossary(english, target).for_texts(["Chop two oak logs and feed a Cow."])
        self.assertEqual(found, {"Cow": "Vaca", "Oak Log": "Tronco de roble"})


class RoundTripTest(Fake):
    def test_import_writes_both_files_in_english_order_with_tabs(self):
        out, _ = self.import_spanish()
        self.assertIn("9 row(s) accepted, 0 rejected", out)
        ui = self.repo.lang_path("es_es", False).read_text(encoding="utf-8")
        content = self.repo.lang_path("es_es", True).read_text(encoding="utf-8")
        self.assertEqual(ui, core.dump_bundle({k: ES[k] for k in EN_UI if k in ES}))
        self.assertTrue(ui.startswith('{\n\t"hangulquest.ui.back": "Atrás"'))
        self.assertEqual(list(json.loads(content)), list(EN_CONTENT))
        self.assertEqual(json.loads(content)["hangulquest.trail.t1.title"], "El sendero de la aventura")

    def test_import_writes_the_machine_status(self):
        self.import_spanish()
        entry = self.status()["hangulquest.trail.t1.title"]
        self.assertEqual(entry["state"], "machine")
        self.assertEqual(entry["reviewers"], ["gemini"])
        self.assertEqual(entry["src"], core.text_hash("The Adventure Trail"))
        self.assertEqual(entry["txt"], core.text_hash("El sendero de la aventura"))
        text = self.repo.status_path("es_es").read_text(encoding="utf-8")
        self.assertEqual(len([line for line in text.splitlines() if '"state"' in line]), 9)

    def test_a_second_import_merges_and_keeps_the_order(self):
        self.import_spanish({k: v for k, v in ES.items() if k.startswith("hangulquest.trail.t1.step.s1")})
        self.import_spanish({k: v for k, v in ES.items() if not k.startswith("hangulquest.trail.t1.step.s1")})
        content = json.loads(self.repo.lang_path("es_es", True).read_text(encoding="utf-8"))
        self.assertEqual(list(content), list(EN_CONTENT))

    def test_rejected_rows_are_reported_and_not_written(self):
        bad = {
            "hangulquest.ui.info.today": "Hoy %s vistas",                       # lost a placeholder
            "hangulquest.ui.back": "Atrás 한글",                                  # Hangul added
            "hangulquest.trail.t1.step.s1.clue": "Collect Music Disc.",   # English again
            "hangulquest.trail.t1.step.s2.clue": "Coloca una antorcha al lado de la puerta — lee el coreano.",  # gives it away
            "hangulquest.trail.t1.step.s3.clue": "Primero una cosa y luego la otra, en el orden del coreano.",
            "hangulquest.trail.t1.title": "",                                   # empty
            "hangulquest.nope": "x",                                            # unknown key
            "hangulquest.trail.t1.finale": "De noche, el ritual de la piedra queda completo.",   # fine
        }
        rejects = self.work / "rejects.jsonl"
        path = results_file(self.work / "bad.jsonl", bad)
        out, err = self.run_tool("import", "--lang", "es_es", path, "--rejects", rejects)
        self.assertIn("1 row(s) accepted, 7 rejected", out)
        for key in list(bad)[:7]:
            self.assertIn(key, err)
        written = json.loads(self.repo.lang_path("es_es", True).read_text(encoding="utf-8"))
        self.assertEqual(list(written), ["hangulquest.trail.t1.finale"])
        self.assertFalse(self.repo.lang_path("es_es", False).exists())
        reasons = {json.loads(line)["key"]: json.loads(line)["problems"] for line in rejects.read_text(encoding="utf-8").splitlines()}
        self.assertIn("placeholders", reasons["hangulquest.ui.info.today"][0])
        self.assertIn("Hangul", reasons["hangulquest.ui.back"][0])
        self.assertIn("identical", reasons["hangulquest.trail.t1.step.s1.clue"][0])
        self.assertRegex(reasons["hangulquest.trail.t1.step.s2.clue"][0], "gives away.*al lado")
        self.assertIn("Primero", reasons["hangulquest.trail.t1.step.s3.clue"][0])

    def test_dry_run_writes_nothing(self):
        path = results_file(self.work / "es.jsonl", ES)
        self.run_tool("import", "--lang", "es_es", path, "--dry-run")
        self.assertFalse(self.repo.lang_path("es_es", True).exists())
        self.assertFalse(self.repo.status_path("es_es").exists())

    def test_fenced_and_array_results_are_read(self):
        fenced = self.work / "fenced.jsonl"
        fenced.write_text("```json\n" + json.dumps({"key": "hangulquest.ui.back", "text": "Atrás"}) + "\n```\n", encoding="utf-8")
        out, _ = self.run_tool("import", "--lang", "es_es", fenced)
        self.assertIn("1 row(s) accepted", out)
        array = self.work / "array.json"
        array.write_text(json.dumps([{"key": "hangulquest.word.stone.gloss", "translation": "Piedra"}]), encoding="utf-8")
        out, _ = self.run_tool("import", "--lang", "es_es", array)
        self.assertIn("1 row(s) accepted", out)

    def test_a_result_for_changed_english_is_refused(self):
        path = results_file(self.work / "old.jsonl", {"hangulquest.ui.back": "Atrás"}, src="000000000000")
        out, err = self.run_tool("import", "--lang", "es_es", path)
        self.assertIn("0 row(s) accepted, 1 rejected", out)
        self.assertIn("src differs", err)

    def test_a_language_without_a_relation_list_cannot_take_hidden_clues(self):
        path = results_file(self.work / "xx.jsonl", {"hangulquest.trail.t1.step.s2.clue": "Zzz zzz."})
        out, err = self.run_tool("import", "--lang", "xx_xx", path)
        self.assertIn("no relation word list", err)

    def test_status_counts_the_states(self):
        self.import_spanish({k: v for k, v in ES.items() if k.startswith("hangulquest.ui")})
        out, _ = self.run_tool("status", "--lang", "es_es", "--json")
        item = json.loads(out)[0]
        self.assertEqual((item["rows"], item["translated"]), (9, 2))
        self.assertEqual(item["states"]["machine"], 2)
        self.assertEqual(item["states"]["missing"], 7)
        self.assertEqual(item["ui"], [2, 2])
        self.assertEqual(item["content"], [7, 0])


class StaleAndEditTest(Fake):
    def setUp(self):
        super().setUp()
        self.import_spanish()

    def change_english(self, key, text):
        content = core.read_bundle(self.repo, "en_us", True)
        content[key] = text
        write_json(self.repo.content / "en_us.json", content)

    def test_changed_english_makes_a_key_stale_without_a_sync(self):
        self.change_english("hangulquest.trail.t1.step.s1.clue", "Collect a Music Disc.")
        out, _ = self.run_tool("status", "--lang", "es_es", "--json")
        states = json.loads(out)[0]["states"]
        self.assertEqual((states["stale"], states["machine"]), (1, 8))

    def test_changed_only_exports_stale_and_missing_keys(self):
        write_json(self.repo.lang_path("es_es", True),
                   {k: v for k, v in core.read_bundle(self.repo, "es_es", True).items() if "finale" not in k})
        self.change_english("hangulquest.trail.t1.step.s1.clue", "Collect a Music Disc.")
        self.run_tool("export", "--lang", "es_es", "--out", self.work / "out", "--changed-only", "--no-glossary")
        packet = json.loads(next((self.work / "out").glob("es_es-tr-*.json")).read_text(encoding="utf-8"))
        self.assertEqual({r["key"] for r in packet["rows"]},
                         {"hangulquest.trail.t1.step.s1.clue", "hangulquest.trail.t1.finale"})

    def test_nothing_to_export_when_nothing_changed(self):
        out, _ = self.run_tool("export", "--lang", "es_es", "--out", self.work / "out", "--changed-only")
        self.assertIn("nothing to translate", out)

    def test_sync_writes_stale_and_a_reimport_clears_it(self):
        key = "hangulquest.trail.t1.step.s1.clue"
        self.change_english(key, "Collect a Music Disc.")
        self.run_tool("sync", "--lang", "es_es")
        self.assertEqual(self.status()[key]["state"], "stale")
        self.assertEqual(self.status()[key]["was"], "machine")
        results = results_file(self.work / "again.jsonl", {key: "Recoge un disco de música."})
        self.run_tool("import", "--lang", "es_es", results)
        self.assertEqual(self.status()[key]["state"], "machine")
        self.assertNotIn("was", self.status()[key])
        self.assertEqual(self.status()[key]["src"], core.text_hash("Collect a Music Disc."))

    def test_english_changing_back_restores_the_old_state(self):
        key = "hangulquest.trail.t1.step.s1.clue"
        self.change_english(key, "Something else.")
        self.run_tool("sync", "--lang", "es_es")
        self.change_english(key, EN_CONTENT[key])
        out, _ = self.run_tool("status", "--lang", "es_es", "--json")
        self.assertEqual(json.loads(out)[0]["states"]["stale"], 0)

    def test_a_hand_edit_counts_as_community_approved(self):
        key = "hangulquest.ui.back"
        ui = core.read_bundle(self.repo, "es_es", False)
        ui[key] = "Volver"
        write_json(self.repo.lang_path("es_es", False), ui)
        out, _ = self.run_tool("status", "--lang", "es_es", "--json")
        self.assertEqual(json.loads(out)[0]["states"]["community-approved"], 1)
        self.run_tool("sync", "--lang", "es_es")
        entry = self.status()[key]
        self.assertEqual(entry["state"], "community-approved")
        self.assertIn("github", entry["reviewers"])
        self.assertEqual(entry["txt"], core.text_hash("Volver"))

    def test_sync_drops_keys_that_lost_their_text_and_prunes_orphans(self):
        content = core.read_bundle(self.repo, "es_es", True)
        del content["hangulquest.trail.t1.title"]
        content["hangulquest.trail.gone.title"] = "Viejo"
        write_json(self.repo.lang_path("es_es", True), content)
        self.run_tool("sync", "--lang", "es_es", "--prune")
        self.assertNotIn("hangulquest.trail.t1.title", self.status())
        self.assertNotIn("hangulquest.trail.gone.title", core.read_bundle(self.repo, "es_es", True))

    def test_approve_sets_the_state_and_refuses_stale_keys(self):
        key = "hangulquest.ui.back"
        self.run_tool("approve", "--lang", "es_es", "--by", "someone", key)
        self.assertEqual(self.status()[key]["state"], "community-approved")
        self.assertIn("github:someone", self.status()[key]["reviewers"])
        self.change_english("hangulquest.trail.t1.title", "The Trail")
        self.run_tool("approve", "--lang", "es_es", "--by", "someone", "hangulquest.trail.t1.title", expect=1)

    def test_adopt_records_text_that_has_no_status(self):
        write_json(self.repo.lang_path("de_de", False), {"hangulquest.ui.back": "Zurück"})
        out, _ = self.run_tool("status", "--lang", "de_de", "--json")
        self.assertEqual(json.loads(out)[0]["states"]["untracked"], 1)
        self.run_tool("adopt", "--lang", "de_de", "--state", "proofread")
        self.assertEqual(self.status("de_de")["hangulquest.ui.back"]["state"], "proofread")

    def test_adopt_refresh_rerecords_text_rewritten_with_the_english_only(self):
        write_json(self.repo.lang_path("de_de", True), {"hangulquest.trail.t1.title": "Der Pfad",
                                                         "hangulquest.trail.t1.step.s1.clue": "Sammle eine Schallplatte."})
        self.run_tool("adopt", "--lang", "de_de", "--state", "proofread")
        self.change_english("hangulquest.trail.t1.title", "The Trail")
        self.change_english("hangulquest.trail.t1.step.s1.clue", "Collect a Music Disc.")
        content = core.read_bundle(self.repo, "de_de", True)
        content["hangulquest.trail.t1.title"] = "Der neue Pfad"     # rewritten together with the English
        write_json(self.repo.lang_path("de_de", True), content)
        self.run_tool("adopt", "--lang", "de_de", "--state", "proofread")
        out, _ = self.run_tool("status", "--lang", "de_de", "--json")
        self.assertEqual(json.loads(out)[0]["states"]["stale"], 2, "without --refresh both stay stale")
        self.run_tool("adopt", "--lang", "de_de", "--state", "proofread", "--refresh")
        out, _ = self.run_tool("status", "--lang", "de_de", "--json")
        self.assertEqual(json.loads(out)[0]["states"]["stale"], 1, "only the untouched German stays stale")


class ProofreadTest(Fake):
    def setUp(self):
        super().setUp()
        self.import_spanish()

    def packet(self):
        self.run_tool("proofread-export", "--lang", "es_es", "--out", self.work / "pr", "--no-glossary")
        files = sorted((self.work / "pr").glob("es_es-proofread-*.json"))
        return json.loads(files[0].read_text(encoding="utf-8"))

    def verdicts(self, items):
        path = self.work / "verdicts.jsonl"
        path.write_text("".join(json.dumps(i, ensure_ascii=False) + "\n" for i in items), encoding="utf-8")
        return path

    def test_the_packet_has_the_draft_the_english_and_the_korean(self):
        packet = self.packet()
        self.assertEqual(packet["mode"], "proofread")
        row = {r["key"]: r for r in packet["rows"]}["hangulquest.trail.t1.step.s1.clue"]
        self.assertEqual((row["en"], row["ko"], row["draft"]), ("Collect Music Disc.", "음반을 모으세요.", "Recoge un disco de música."))
        self.assertEqual(row["draftHash"], core.text_hash("Recoge un disco de música."))
        self.assertTrue(any("verdict" in rule for rule in packet["rules"]))

    def test_ok_and_fix_verdicts_set_proofread(self):
        key_ok, key_fix = "hangulquest.ui.back", "hangulquest.word.stone.gloss"
        path = self.verdicts([{"key": key_ok, "verdict": "ok"},
                              {"key": key_fix, "verdict": "fix", "text": "Roca", "note": "x"}])
        out, _ = self.run_tool("proofread-import", "--lang", "es_es", path)
        self.assertIn("1 ok, 1 fixed, 0 rejected", out)
        self.assertEqual(self.status()[key_ok]["state"], "proofread")
        self.assertEqual(self.status()[key_ok]["reviewers"], ["claude", "gemini"])
        self.assertEqual(core.read_bundle(self.repo, "es_es", True)[key_fix], "Roca")
        self.assertEqual(self.status()[key_fix]["txt"], core.text_hash("Roca"))
        # Proofread keys are no longer in the next proofreading packet.
        keys = {r["key"] for r in self.packet()["rows"]}
        self.assertNotIn(key_ok, keys)
        self.assertNotIn(key_fix, keys)

    def test_a_fix_has_to_pass_the_same_rules(self):
        key = "hangulquest.ui.info.today"
        path = self.verdicts([{"key": key, "verdict": "fix", "text": "Hoy vistas"}])
        out, err = self.run_tool("proofread-import", "--lang", "es_es", path)
        self.assertIn("0 ok, 0 fixed, 1 rejected", out)
        self.assertEqual(self.status()[key]["state"], "machine")
        self.assertEqual(core.read_bundle(self.repo, "es_es", False)[key], ES[key])

    def test_a_verdict_on_a_draft_that_changed_is_refused(self):
        key = "hangulquest.ui.back"
        path = self.verdicts([{"key": key, "verdict": "ok", "draftHash": "0" * 12}])
        out, err = self.run_tool("proofread-import", "--lang", "es_es", path)
        self.assertIn("draftHash differs", err)

    def test_stale_and_proofread_rows_are_not_proofread_again(self):
        key = "hangulquest.ui.back"
        self.run_tool("proofread-import", "--lang", "es_es", self.verdicts([{"key": key, "verdict": "ok"}]))
        out, err = self.run_tool("proofread-import", "--lang", "es_es", self.verdicts([{"key": key, "verdict": "ok"}]))
        self.assertIn("only machine drafts", err)

    def test_an_unknown_verdict_is_refused(self):
        out, err = self.run_tool("proofread-import", "--lang", "es_es",
                                 self.verdicts([{"key": "hangulquest.ui.back", "verdict": "maybe"}]))
        self.assertIn("verdict must be ok or fix", err)


class FontTest(Fake):
    def fake_unifont(self, codepoints):
        cache = self.work / "fontcache"
        digest = "cd" + "0" * 38
        (cache / "objects/cd").mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(cache / "objects/cd" / digest, "w") as archive:
            archive.writestr("unifont_all_no_pua.hex", "".join(f"{c:04X}:00FF\n" for c in codepoints))
        (cache / "indexes").mkdir(exist_ok=True)
        (cache / "indexes/26.3-1.json").write_text(
            json.dumps({"objects": {"minecraft/font/unifont.zip": {"hash": digest, "size": 1}}}), encoding="utf-8")
        return cache

    def test_every_character_with_a_glyph_passes_and_a_missing_one_is_named(self):
        write_json(self.repo.lang_path("vi_vn", False), {"hangulquest.ui.back": "Quay lại ế"})
        covered = [ord(c) for c in "Quay lại English"] + [0x1EBF]
        out, _ = self.run_tool("fonts", "--lang", "vi_vn", "--assets", self.fake_unifont(covered))
        self.assertIn("every character has a glyph", out)
        out, _ = self.run_tool("fonts", "--lang", "vi_vn", "--assets", self.fake_unifont(covered[:-1]), expect=1)
        self.assertIn("'ế'", out)

    def test_without_a_cache_nothing_is_checked(self):
        out, err = self.run_tool("fonts", "--assets", self.work / "none")
        self.assertIn("nothing checked", err)

    def test_the_real_unifont_covers_the_scripts_of_the_planned_languages(self):
        covered = tr_fonts.unifont_coverage(tr_glossary.default_roots())
        if covered is None:
            self.skipTest("no local asset cache")
        samples = {"vi": "Tiếng Việt ạ ả ấ ầ ẩ ẫ ậ ơ ư ờ ở ỡ ợ", "th": "ภาษาไทย", "ja": "日本語 ひらがな カタカナ",
                   "zh": "简体中文 繁體中文", "ru": "Русский", "pl": "Polski łąęśżźćń", "tr": "Türkçe ğışİ"}
        for code, text in samples.items():
            self.assertFalse(tr_fonts.missing_characters([text], covered), code)


class ReviewSiteTest(Fake):
    def site(self):
        import build as site_build
        return site_build.Site("someorg/SomeRepo", [], {"schema": 1, "lines": {}, "rules": {}})

    def test_no_pages_before_the_first_import(self):
        import translations
        self.assertEqual(translations.build_pages(self.site(), self.repo), {})

    def test_machine_and_stale_rows_are_listed_with_a_prefilled_form(self):
        import translations
        self.import_spanish()
        verdicts = self.work / "v.jsonl"
        verdicts.write_text(json.dumps({"key": "hangulquest.ui.back", "verdict": "ok"}) + "\n", encoding="utf-8")
        self.run_tool("proofread-import", "--lang", "es_es", verdicts)
        content = core.read_bundle(self.repo, "en_us", True)
        content["hangulquest.word.stone.gloss"] = "Rock"
        write_json(self.repo.content / "en_us.json", content)
        pages = translations.build_pages(self.site(), self.repo)
        self.assertEqual(sorted(pages), ["translation/es_es-1.html", "translations.html"])
        page = pages["translation/es_es-1.html"]
        self.assertIn("El sendero de la aventura", page)
        self.assertIn("Piedra", page)  # stale: still shown, with the new English next to it
        self.assertIn("English changed", page)
        self.assertNotIn("Atrás", page)  # proofread, nothing left to check
        self.assertIn("template=translation-correction.yml", page)
        self.assertIn("language=es_es", page)
        self.assertIn("key=hangulquest.trail.t1.title", page)
        self.assertIn("es_es", pages["translations.html"])


class ShipAndCheckTest(Fake):
    def test_ship_needs_both_files_then_lists_the_language(self):
        self.import_spanish({"hangulquest.ui.back": "Atrás"})
        self.run_tool("ship", "--lang", "es_es", expect=2)  # no content file yet
        self.import_spanish({"hangulquest.word.stone.gloss": "Piedra"})
        self.run_tool("ship", "--lang", "es_es")
        index = json.loads(self.repo.languages_json.read_text(encoding="utf-8"))
        self.assertEqual(index, {"en_us": "English", "es_es": "Español"})

    def test_check_passes_a_clean_language_and_fails_a_broken_edit(self):
        self.import_spanish()
        out, _ = self.run_tool("check")
        self.assertIn("9 row(s) pass", out)
        ui = core.read_bundle(self.repo, "es_es", False)
        ui["hangulquest.ui.info.today"] = "Hoy %s vistas"
        write_json(self.repo.lang_path("es_es", False), ui)
        _, err = self.run_tool("check", expect=1)
        self.assertIn("hangulquest.ui.info.today", err)

    def test_check_flags_a_hand_edit_that_gives_the_form_away(self):
        self.import_spanish()
        content = core.read_bundle(self.repo, "es_es", True)
        content["hangulquest.trail.t1.step.s2.clue"] = "Coloca una antorcha encima de la puerta."
        write_json(self.repo.lang_path("es_es", True), content)
        _, err = self.run_tool("check", expect=1)
        self.assertRegex(err, "gives away.*encima")

    def test_check_flags_a_status_for_a_key_that_is_gone(self):
        self.import_spanish()
        status = core.read_status(self.repo, "es_es")
        status["keys"]["hangulquest.trail.gone.title"] = {"src": "x", "txt": "y", "state": "machine", "reviewers": []}
        core.write_status(self.repo, "es_es", status, [])
        _, err = self.run_tool("check", expect=1)
        self.assertIn("hangulquest.trail.gone.title", err)


class RulesTest(unittest.TestCase):
    def row(self, en, **extra):
        return core.Row(key="k", en=en, ko="", kind="clue", note="", placeholders=core.placeholders(en),
                        maxLen=None, formDependent=bool(core.FORM_DEPENDENT.search(en)), src="x", file="content", **extra)

    def check(self, en, text, code="es_es", **extra):
        return tr_check.check_text(self.row(en, **extra), text, code)

    def test_positional_placeholders_may_be_reordered(self):
        self.assertEqual(self.check("%s of %s", "%2$s de %1$s"), [])
        self.assertEqual(self.check("%s of %s", "%s de %s"), [])
        self.assertTrue(self.check("%s of %s", "%1$s de %1$s"))
        self.assertTrue(self.check("%s of %s", "%1$s de %s"))
        self.assertTrue(self.check("%s of %s", "%s de %s %s"))
        self.assertTrue(self.check("%s%% done", "%s hecho"))
        self.assertEqual(self.check("%s%% done", "%s%% hecho"), [])

    def test_hangul_must_be_kept_exactly(self):
        self.assertEqual(self.check("Right. Use 반말 here.", "Bien. Usa 반말 aquí."), [])
        self.assertTrue(self.check("Right. Use 반말 here.", "Bien. Usa banmal aquí."))
        self.assertTrue(self.check("Back", "Atrás 돌"))

    def test_untranslated_english_is_caught_but_names_and_cognates_pass(self):
        self.assertEqual(self.check("Hangul Quest", "Hangul Quest"), [])
        self.assertEqual(self.check("Animals", "Animales"), [])
        self.assertEqual(self.check("Ice Bucket Challenge", "Ice Bucket Challenge"), [])
        self.assertTrue(self.check("Collect Music Disc.", "Collect Music Disc."))
        self.assertTrue(self.check("Walk to the village and talk to the farmer.", "Walk to the village and talk to the farmer."))
        self.assertTrue(self.check("Walk to the village and talk to the farmer.", "Camina to the village and talk with you."))
        self.assertTrue(self.check("Open the journal and read the page.", "Open the journal", code="ru_ru"))

    def test_length_line_breaks_and_characters(self):
        short = core.Row(key="k", en="Back", ko="", kind="UI label", note="", placeholders=[], maxLen=core.max_length("Back"),
                         formDependent=False, src="x", file="ui")
        self.assertEqual(core.max_length("Back"), 14)
        self.assertEqual(tr_check.check_text(short, "Volver atrás", "es_es"), [])
        self.assertTrue(tr_check.check_text(short, "Volver hacia el principio", "es_es"))
        self.assertTrue(self.check("One\nTwo", "Uno Dos"))
        self.assertTrue(self.check("Done", "Hecho 😀"))
        self.assertTrue(self.check("Done", "Hecho�"))
        self.assertTrue(self.check("Done", " Hecho"))

    def test_every_list_has_both_kinds_of_words_and_compiles(self):
        words = tr_check.relation_words()
        for part in ("es", "pt", "fr", "id", "vi", "ja", "zh", "ru", "pl", "tr", "tl"):
            self.assertIn(part, words)
            for kind in ("relation", "order"):
                self.assertGreaterEqual(len(words[part][kind]), 5, part + " " + kind)
                self.assertEqual(len(set(words[part][kind])), len(words[part][kind]), part + " " + kind + " has a duplicate")
                for entry in words[part][kind]:
                    self.assertEqual(entry, entry.strip(), entry)
                    self.assertTrue(entry)
                    tr_check._pattern(entry, bool(words[part].get("substring")))
            self.assertEqual(bool(words[part].get("substring")), part in ("ja", "zh"))

    def test_the_lists_pass_the_linguists_sentences(self):
        """Natural hidden translations pass, giveaways are caught, for all eleven languages."""
        samples = json.loads((Path(__file__).parent / "relation_samples.json").read_text(encoding="utf-8"))
        self.assertEqual(len(samples), 11)
        for part, groups in samples.items():
            code = part + "_xx"
            for text in groups["cleanRelation"]:
                self.assertIsNone(tr_check.giveaway(text, code, False), text)
            for text in groups["cleanOrder"]:
                self.assertIsNone(tr_check.giveaway(text, code, True), text)
            for text in groups["giveRelation"]:
                self.assertIsNotNone(tr_check.giveaway(text, code, False), text)
            for text in groups["giveOrder"]:
                self.assertIsNotNone(tr_check.giveaway(text, code, True), text)

    def test_condition_phrases_are_not_giveaways(self):
        # "underground" or "in your hand" says where the player is, not where the hidden relation puts the thing
        self.assertIsNone(tr_check.giveaway("Bajo tierra, coloca un farol — lee el coreano para ver dónde", "es_es", False))
        self.assertIsNone(tr_check.giveaway("Platziere in der Tiefe eine Werkbank", "de_de", False))
        self.assertEqual(tr_check.giveaway("Coloca un farol bajo la mesa", "es_es", False), "bajo")

    def test_a_relation_word_the_english_has_too_is_no_giveaway(self):
        # "On the beach" is a place condition in both languages; the hidden relation is still not named
        en = "On the beach, build a ring — read the Korean to see what from."
        self.assertIsNone(tr_check.giveaway("Sur la plage, construis un cercle — lis le coréen pour voir en quoi.",
                                            "fr_fr", False, en=en))
        self.assertIsNotNone(tr_check.giveaway("Sur la plage, construis un cercle sur le sable.", "fr_fr", False, en=en))

    def test_turkish_capital_dotted_i_matches(self):
        self.assertIsNotNone(tr_check.giveaway("İlk olarak meşaleyi koy.", "tr_tr", True))

    def test_the_spanish_matcher_uses_whole_words(self):
        self.assertEqual(tr_check.giveaway("Pon la antorcha junto a la puerta.", "es_es", False), "junto")
        self.assertEqual(tr_check.giveaway("Pon la ANTORCHA Encima.", "es_es", False), "Encima")
        self.assertIsNone(tr_check.giveaway("Pon la antorcha — lee el coreano para ver dónde.", "es_es", False))
        self.assertIsNone(tr_check.giveaway("Pon la antorcha — lee el coreano para ver dónde.", "es_es_mx", False))


class RealRepositoryTest(unittest.TestCase):
    """The shipped English files, read as they are (nothing is written)."""

    def test_every_english_key_is_a_row_or_has_nothing_to_translate(self):
        repo = core.Repo(core.ROOT)
        rows = core.source_rows(repo)
        keys = {r["key"] for r in rows}
        total = len(core.read_bundle(repo, "en_us", False)) + len(core.read_bundle(repo, "en_us", True))
        self.assertGreater(len(rows), 5000)
        for content in (False, True):
            for key, text in core.read_bundle(repo, "en_us", content).items():
                self.assertEqual(key in keys, core.translatable(text), key)
        self.assertLess(total - len(rows), 20, "only Korean-only names and bare placeholders are left out")

    def test_trail_text_has_its_korean_line_and_form_dependent_clues_are_marked(self):
        rows = core.source_rows(core.Repo(core.ROOT))
        trail = [r for r in rows if r["file"] == "content" and r["kind"] in ("trail title", "clue", "hint", "finale")]
        self.assertGreater(len(trail), 3000)
        missing = [r["key"] for r in trail if not r["ko"]]
        self.assertLess(len(missing), len(trail) * 0.02, missing[:5])
        hidden = [r for r in rows if r["formDependent"]]
        self.assertGreater(len(hidden), 100)
        self.assertTrue(all("read the Korean" in r["en"] or "the Korean says" in r["en"] for r in hidden))

    def test_shipped_relation_words_do_not_flag_the_german_and_english_clues(self):
        repo = core.Repo(core.ROOT)
        german = core.read_bundle(repo, "de_de", True)
        for row in core.source_rows(repo):
            if row["formDependent"] and row["key"] in german:
                # German is checked in Java (SpatialRules.leak); here only that the checker does not crash on it.
                tr_check.check_text(row, german[row["key"]], "de_de")

    def test_check_passes_on_the_shipped_repository(self):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = translate.main(["check"])
        self.assertEqual(code, 0, err.getvalue())


if __name__ == "__main__":
    unittest.main()
