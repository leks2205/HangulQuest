import unittest

import _setup  # noqa: F401
import credits


class CreditsTest(unittest.TestCase):
    def test_collects_counts_names_and_opt_outs(self):
        reviewers = {"reviewers": {"ann": {"display": "민수", "accepted": 2}, "bo": {"optOut": True, "accepted": 5},
                                   "cy": {"display": "", "accepted": 0}}}
        status = {"lines": {"a": {"approvals": [{"login": "ann"}, {"login": "bo"}, {"login": "dee"}]}},
                  "rules": {"r": {"approvals": [{"login": "dee"}]}}}
        data = credits.collect(reviewers, status)
        self.assertEqual([(p["name"], p["accepted"], p["approvals"]) for p in data["reviewers"]],
                         [("민수", 2, 1), ("dee", 0, 2)])
        self.assertEqual(data["anonymous"], 1)
        self.assertNotIn("bo", credits.render_md(data))
        self.assertNotIn("bo", credits.render_json(data))

    def test_names_cannot_inject_markdown(self):
        data = {"reviewers": [{"name": "[x](http://evil) <b>", "github": "", "accepted": 1, "approvals": 0,
                               "trusted": False}], "anonymous": 0}
        md = credits.render_md(data)
        self.assertNotIn("](http://evil)", md)
        self.assertNotIn("<b>", md)

    def test_committed_files_are_current(self):
        from common import REVIEWERS_PATH, STATUS_PATH, read_json, read_status
        data = credits.collect(read_json(REVIEWERS_PATH, {}), read_status(STATUS_PATH))
        self.assertEqual(credits.MD_PATH.read_text(encoding="utf-8"), credits.render_md(data))
        self.assertEqual(credits.JSON_PATH.read_text(encoding="utf-8"), credits.render_json(data))


if __name__ == "__main__":
    unittest.main()
