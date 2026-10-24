import unittest

import _setup  # noqa: F401
import bot
import export_lines
from common import DATA_DIR

LANG = {"hangulquest.dialogue.child.ask.c2": "Yes, I like them, too. (very formal)",
        "hangulquest.dialogue.child.hello": "Hi! I like apples."}
LANG_DE = {"hangulquest.dialogue.child.hello": "Hallo! Ich mag Äpfel."}


def row(rid, source, goal, ko="안녕!", level="1"):
    return {"id": rid, "level": level, "source": source, "ko": ko, "goal": goal}


class DialogueRecordTest(unittest.TestCase):
    def test_npc_line(self):
        rec = export_lines.dialogue_record(
            row("dialogue.child.hello", "authored:dialogue/child.json#nodes.hello.ko", "Dialogue child banmal"),
            LANG, LANG_DE)
        self.assertEqual(rec["en"], "Hi! I like apples.")
        self.assertEqual(rec["de"], "Hallo! Ich mag Äpfel.")
        self.assertEqual(rec["source"], {"kind": "authored", "file": "dialogue/child.json", "pointer": "/nodes/hello/ko"})
        self.assertEqual(rec["context"]["step"], "hello")
        self.assertIn("반말", rec["context"]["goal"])
        self.assertEqual(rec["context"]["goalKey"], "Dialogue child banmal")

    def test_reply_points_into_the_choice_list(self):
        rec = export_lines.dialogue_record(
            row("dialogue.child.ask.c2", "authored:dialogue/child.json#nodes.ask.choices[1].ko",
                "Dialogue player hapsyo wrong", "네, 저도 좋아합니다."), LANG, LANG_DE)
        self.assertEqual(rec["source"]["pointer"], "/nodes/ask/choices/1/ko")
        self.assertEqual(rec["en"], "Yes, I like them, too. (very formal)")
        self.assertEqual(rec["de"], "")
        self.assertIn("wrong form", rec["context"]["goal"])
        self.assertIn("합쇼체", rec["context"]["goal"])

    def test_a_banmal_version_shares_the_gloss_of_its_line(self):
        rec = export_lines.dialogue_record(
            row("dialogue.child.hello.banmal", "authored:dialogue/child.json#nodes.hello.ko_banmal",
                "Dialogue villager banmal", "안녕! 나는 사과를 좋아해."), LANG, LANG_DE)
        self.assertEqual(rec["en"], "Hi! I like apples.")
        self.assertEqual(rec["source"]["pointer"], "/nodes/hello/ko_banmal")
        self.assertIn("casually", rec["context"]["goal"])

    def test_a_request_line_points_into_the_request_file(self):
        rec = export_lines.dialogue_record(
            row("dialogue.req_plains_01.lazy_offer", "authored:requests/req_plains_01.json#lines.LAZY.offer",
                "Dialogue villager haeyo", "음, 빵이 필요해요."),
            {"hangulquest.dialogue.req_plains_01.lazy_offer": "Um, I need bread."}, {})
        self.assertEqual(rec["en"], "Um, I need bread.")
        self.assertEqual(rec["source"], {"kind": "authored", "file": "requests/req_plains_01.json",
                                         "pointer": "/lines/LAZY/offer"})

    def test_the_shipped_files_are_where_the_records_say(self):
        import json
        for name in ("greetings", "child", "library"):
            path = DATA_DIR.parent / "dialogue" / f"{name}.json"
            self.assertTrue(path.is_file(), name)
            self.assertEqual(json.loads(path.read_text(encoding="utf-8"))["id"], name)


class DialogueContentPathTest(unittest.TestCase):
    def rec(self, file):
        return {"id": "dialogue.child.hello", "source": {"kind": "authored", "file": file, "pointer": "/nodes/hello/ko"}}

    def test_a_dialogue_file_resolves_next_to_the_data_folder(self):
        path = bot.content_path(self.rec("dialogue/child.json"))
        self.assertEqual(path, (DATA_DIR.parent / "dialogue" / "child.json").resolve())

    def test_other_folders_and_escapes_are_still_refused(self):
        for file in ("dialogue/../data/concepts.json", "dialogue/a/b.json", "dialogue/.json", "../dialogue/child.json"):
            with self.assertRaises(ValueError, msg=file):
                bot.content_path(self.rec(file))

    def test_questline_files_still_resolve_in_the_data_folder(self):
        self.assertEqual(bot.content_path(self.rec("questlines/w1_01.json")),
                         (DATA_DIR / "questlines" / "w1_01.json").resolve())


if __name__ == "__main__":
    unittest.main()
