import unittest

import _setup  # noqa: F401
import bot
import export_lines
from common import DATA_DIR

LANG = {"hangulquest.story.house_first.p2": "First I chop down trees.",
        "hangulquest.story.house_first.title": "My first house",
        "hangulquest.culture.ondol.summary": "In an ondol room the floor is warm.",
        "hangulquest.chapter.house.title": "House"}
LANG_DE = {"hangulquest.story.house_first.p2": "Zuerst fälle ich Bäume."}


def row(rid, source, goal, ko="안녕!", level="1"):
    return {"id": rid, "level": level, "source": source, "ko": ko, "goal": goal}


class LibraryRecordTest(unittest.TestCase):
    def test_story_paragraph(self):
        rec = export_lines.library_record(
            row("story.house_first.p2", "authored:stories/house/house_first.json#paragraphs[1]", "Story paragraph 2"),
            LANG, LANG_DE, DATA_DIR)
        self.assertEqual(rec["en"], "First I chop down trees.")
        self.assertEqual(rec["de"], "Zuerst fälle ich Bäume.")
        self.assertEqual(rec["source"], {"kind": "authored", "file": "stories/house/house_first.json",
                                         "pointer": "/paragraphs/1"})
        self.assertEqual(rec["context"]["field"], "story")
        self.assertEqual(rec["context"]["step"], "p2")

    def test_story_title(self):
        rec = export_lines.library_record(
            row("story.house_first.title", "authored:stories/house/house_first.json#titleKo", "Story title"),
            LANG, LANG_DE, DATA_DIR)
        self.assertEqual(rec["en"], "My first house")
        self.assertEqual(rec["source"]["pointer"], "/titleKo")

    def test_culture_summary_uses_the_summary_key_and_the_note_icon(self):
        rec = export_lines.library_record(
            row("culture.ondol.ko", "authored:culture.json#notes[0].ko", "Culture note summary"),
            LANG, LANG_DE, DATA_DIR)
        self.assertEqual(rec["en"], "In an ondol room the floor is warm.")
        self.assertEqual(rec["context"]["icon"], "minecraft:furnace")
        self.assertEqual(rec["source"], {"kind": "authored", "file": "culture.json", "pointer": "/notes/0/ko"})

    def test_chapter_title(self):
        rec = export_lines.library_record(
            row("chapter.house.title", "authored:chapters.json#chapters[0].titleKo", "Chapter title in the quest tab"),
            LANG, LANG_DE, DATA_DIR)
        self.assertEqual(rec["en"], "House")
        self.assertEqual(rec["context"]["icon"], "minecraft:oak_door")
        self.assertEqual(rec["source"]["pointer"], "/chapters/0/titleKo")

    def test_the_shipped_files_are_where_the_records_say(self):
        import json
        story = DATA_DIR.parent / "stories" / "house" / "house_first.json"
        self.assertEqual(json.loads(story.read_text(encoding="utf-8"))["id"], "house_first")
        self.assertTrue((DATA_DIR / "culture.json").is_file())
        self.assertTrue((DATA_DIR / "chapters.json").is_file())


class LibraryContentPathTest(unittest.TestCase):
    def rec(self, file):
        return {"id": "story.house_first.p1", "source": {"kind": "authored", "file": file, "pointer": "/paragraphs/0"}}

    def test_a_story_file_resolves_next_to_the_data_folder(self):
        self.assertEqual(bot.content_path(self.rec("stories/house/house_first.json")),
                         (DATA_DIR.parent / "stories" / "house" / "house_first.json").resolve())

    def test_culture_and_chapters_resolve_in_the_data_folder(self):
        self.assertEqual(bot.content_path(self.rec("culture.json")), (DATA_DIR / "culture.json").resolve())
        self.assertEqual(bot.content_path(self.rec("chapters.json")), (DATA_DIR / "chapters.json").resolve())

    def test_escapes_and_other_story_paths_are_refused(self):
        for file in ("stories/../data/concepts.json", "stories/house.json", "stories/House/a.json",
                     "stories/a/b/c.json", "../stories/house/a.json", "stories/house/_lexicon.json/x"):
            with self.assertRaises(ValueError, msg=file):
                bot.content_path(self.rec(file))


if __name__ == "__main__":
    unittest.main()
