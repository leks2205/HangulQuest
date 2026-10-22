import json
import unittest

import _setup  # noqa: F401
import pointer
from common import DATA_DIR, lines_by_id, read_lines


class PointerTest(unittest.TestCase):
    DOC = '{\n "a": ["돌", "돌"],\n "b": {"x": "돌", "y": [{"z": "물"}]},\n "n": 1, "e": {}, "l": []\n}\n'

    def test_changes_only_the_addressed_occurrence(self):
        out = pointer.replace_string(self.DOC, "/a/1", "돌", "바위")
        self.assertEqual(out, self.DOC.replace('["돌", "돌"]', '["돌", "바위"]'))
        out = pointer.replace_string(self.DOC, "/b/x", "돌", "바위")
        self.assertEqual(json.loads(out)["a"], ["돌", "돌"])
        self.assertEqual(json.loads(out)["b"]["x"], "바위")

    def test_deep_pointer_and_everything_else_untouched(self):
        out = pointer.replace_string(self.DOC, "/b/y/0/z", "물", "강")
        self.assertEqual(out, self.DOC.replace('"물"', '"강"'))

    def test_refuses_when_the_old_text_is_not_there(self):
        with self.assertRaises(pointer.PointerError):
            pointer.replace_string(self.DOC, "/a/0", "물", "강")
        with self.assertRaises(pointer.PointerError):
            pointer.replace_string(self.DOC, "/missing", "돌", "강")
        with self.assertRaises(pointer.PointerError):
            pointer.replace_string(self.DOC, "/a/9", "돌", "강")

    def test_escapes_and_ascii_files(self):
        doc = '{"k": "a\\"b", "u": "\\ub3cc"}'
        self.assertEqual(json.loads(pointer.replace_string(doc, "/k", 'a"b', 'c"d\n'))["k"], 'c"d\n')
        out = pointer.replace_string(doc, "/u", "돌", "물")
        self.assertIn("\\ubb3c", out)  # a file that escapes Hangul keeps doing so

    def test_pointer_escapes(self):
        doc = '{"a/b": {"~": "x"}}'
        self.assertEqual(json.loads(pointer.replace_string(doc, "/a~1b/~0", "x", "y"))["a/b"]["~"], "y")

    def test_real_content_edit_is_a_one_line_diff(self):
        records = lines_by_id(read_lines())
        for line_id in ("concept.stone.ko", "trail.abenteuerpfad.title"):
            record = records[line_id]
            path = DATA_DIR / record["source"]["file"]
            text = path.read_text(encoding="utf-8")
            out = pointer.replace_string(text, record["source"]["pointer"], record["ko"], "시험")
            changed = [(a, b) for a, b in zip(text.splitlines(), out.splitlines()) if a != b]
            self.assertEqual(len(changed), 1, line_id)
            self.assertEqual(len(text.splitlines()), len(out.splitlines()))
            self.assertEqual(len(out) - len(text), len("시험") - len(record["ko"]))


if __name__ == "__main__":
    unittest.main()
