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


class ShippedStoriesInTheRegistryTest(unittest.TestCase):
    """The blind reviews and the native-review site only see what lines.jsonl holds, so every shipped story must be there."""

    def test_every_title_and_paragraph_of_every_story_is_a_registry_line(self):
        import json
        from common import read_lines
        records = {rec["id"]: rec for rec in read_lines()}
        stories = [path for path in sorted((DATA_DIR.parent / "stories").glob("*/*.json")) if not path.name.startswith("_")]
        self.assertGreaterEqual(len(stories), 14, "the four first stories and the graded ones of wave 1")
        for path in stories:
            story = json.loads(path.read_text(encoding="utf-8"))
            expected = {f"story.{story['id']}.title": story["titleKo"]}
            for number, paragraph in enumerate(story["paragraphs"], 1):
                expected[f"story.{story['id']}.p{number}"] = paragraph
            for rid, ko in expected.items():
                self.assertIn(rid, records, f"{path.name}: {rid} is not in lines.jsonl, run export_lines.py")
                self.assertEqual(records[rid]["ko"], ko, rid)
                self.assertTrue(records[rid]["en"], f"{rid} has no English gloss in the registry")


if __name__ == "__main__":
    unittest.main()
