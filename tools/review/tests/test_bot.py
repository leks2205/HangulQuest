import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import _setup  # noqa: F401
import bot
import forms
from common import text_hash, write_lines

from test_forms import NOW, OLD, YOUNG, body, correction_body, no_lint, record, registry


class FakeApi:
    repo = "o/r"

    def __init__(self, permissions=None, users=None):
        self.calls = []
        self.permissions = permissions or {}
        self.users = users or {}

    def request(self, method, path, data=None):
        self.calls.append((method, path, data))
        if path == "/repos/o/r":
            return {"default_branch": "main"}
        if method == "POST" and path.endswith("/pulls"):
            return {"number": 7}
        if method == "POST" and path.endswith("/issues"):
            return {"number": 9}
        return []

    def pages(self, path, limit=300):
        self.calls.append(("PAGES", path, None))
        return []

    def comment(self, number, text):
        self.calls.append(("COMMENT", number, text))

    def add_labels(self, number, labels):
        self.calls.append(("LABELS", number, labels))

    def remove_label(self, number, label):
        self.calls.append(("UNLABEL", number, label))

    def permission(self, login):
        return self.permissions.get(login, "none")

    def user(self, login):
        return self.users.get(login, {})

    def of(self, kind):
        return [c for c in self.calls if c[0] == kind]


def issue(number, login, text, labels):
    return {"number": number, "user": {"login": login, "type": "User"}, "body": text,
            "labels": [{"name": l} for l in labels]}


class TriageTest(unittest.TestCase):
    def test_correction_gets_labels_and_one_comment(self):
        rec = record()
        api = FakeApi()
        result = bot.triage(api, issue(1, "ann", correction_body(rec), ["korean-review", "korean:correction"]),
                            registry(rec), no_lint, NOW)
        self.assertEqual(result.labels, ["bot:lint-ok", "needs-maintainer"])
        self.assertEqual(api.of("LABELS")[0][2], ["bot:lint-ok", "needs-maintainer"])
        self.assertEqual(len(api.of("COMMENT")), 1)

    def test_stale_line_gets_the_current_text(self):
        rec = record()
        api = FakeApi()
        text = correction_body(rec, **{"text-hash": "0" * 12})
        bot.triage(api, issue(1, "ann", text, ["korean-review", "korean:correction"]), registry(rec), no_lint, NOW)
        self.assertIn("bot:stale", api.of("LABELS")[0][2])
        self.assertIn(rec["ko"], api.of("COMMENT")[0][2])

    def test_other_issues_and_bots_are_ignored(self):
        rec = record()
        api = FakeApi()
        self.assertIsNone(bot.triage(api, issue(1, "ann", "Crash on start", ["bug"]), registry(rec)))
        bad = issue(2, "robot", correction_body(rec), ["korean-review"])
        bad["user"]["type"] = "Bot"
        self.assertIsNone(bot.triage(api, bad, registry(rec)))
        self.assertEqual(api.calls, [])

    def test_young_account_approval_is_ignored(self):
        rec = record()
        api = FakeApi(users={"kid": {"created_at": YOUNG}})
        text = body(**{"line-id": rec["id"], "text-hash": rec["textHash"], "current": rec["ko"],
                       "proficiency": "native", "confirm": "- [x] ok"})
        result = bot.triage(api, issue(1, "kid", text, ["korean-review", "korean:approve"]), registry(rec), no_lint, NOW)
        self.assertEqual(result.labels, ["approval:ignored"])


def git(cwd, *args):
    return subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@example.org", *args], cwd=cwd,
                          check=True, capture_output=True, text=True).stdout


class AcceptTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        tmp = Path(self.tmp.name)
        self.origin = tmp / "origin.git"
        self.work = tmp / "work"
        git(tmp, "init", "--bare", "-b", "main", str(self.origin))
        git(tmp, "clone", str(self.origin), str(self.work))
        data = self.work / "data" / "questlines"
        data.mkdir(parents=True)
        self.doc = ('{\n "id": "t",\n "steps": [\n  {"id": "s1", "clue": {"ko": "돌을 캐야 해요.", "en": "x"}},\n'
                    '  {"id": "s2", "clue": {"ko": "돌을 캐야 해요.", "en": "x"}}\n ]\n}\n')
        (data / "t.json").write_text(self.doc, encoding="utf-8")
        self.rec = record()
        self.gen = record("trail.t.s2.clue", "돌을 캐야 해요.", kind="generated")
        self.lines = self.work / "content" / "korean" / "lines.jsonl"
        write_lines([self.rec, self.gen], self.lines)
        git(self.work, "add", "-A")
        git(self.work, "commit", "-m", "init")
        git(self.work, "push", "origin", "HEAD:main")
        self.patches = [mock.patch.object(bot, "ROOT", self.work), mock.patch.object(bot, "DATA_DIR", self.work / "data"),
                        mock.patch.object(bot, "LINES_PATH", self.lines)]
        for p in self.patches:
            p.start()

    def tearDown(self):
        for p in self.patches:
            p.stop()
        self.tmp.cleanup()

    def reg(self):
        return registry(self.rec, self.gen)

    def test_maintainer_label_opens_a_one_line_pr_with_co_author(self):
        api = FakeApi(permissions={"maint": "admin"}, users={"ann": {"id": 42}})
        form = issue(5, "ann", correction_body(self.rec), ["korean-review", "korean:correction", "accepted"])
        outcome = bot.accept(api, form, "maint", self.reg(), no_lint)
        self.assertEqual(outcome, "pr 7")
        branch = "korean-review/fix-5"
        show = git(self.origin, "show", "--stat", "--format=%B", branch)
        self.assertIn("Co-authored-by: ann <42+ann@users.noreply.github.com>", show)
        self.assertIn("data/questlines/t.json", show)
        self.assertIn("content/korean/lines.jsonl", show)
        diff = git(self.origin, "diff", "main", branch, "--", "data/questlines/t.json")
        removed = [l for l in diff.splitlines() if l.startswith("-") and not l.startswith("---")]
        self.assertEqual(len(removed), 1)
        self.assertIn('"s1"', removed[0])  # s1 only; the identical text in s2 is untouched
        text = git(self.origin, "show", f"{branch}:data/questlines/t.json")
        self.assertEqual(json.loads(text)["steps"][1]["clue"]["ko"], "돌을 캐야 해요.")
        self.assertEqual(json.loads(text)["steps"][0]["clue"]["ko"], "돌을 캐요.")
        new_line = json.loads(git(self.origin, "show", f"{branch}:content/korean/lines.jsonl").splitlines()[0])
        self.assertEqual(new_line["textHash"], text_hash("돌을 캐요."))
        post = [c for c in api.calls if c[0] == "POST" and c[1].endswith("/pulls")][0]
        self.assertEqual(post[2]["head"], branch)
        self.assertIn("Fixes #5", post[2]["body"])

    def test_without_write_access_nothing_happens(self):
        api = FakeApi(permissions={"stranger": "read"})
        form = issue(5, "ann", correction_body(self.rec), ["korean-review", "korean:correction", "accepted"])
        self.assertTrue(bot.accept(api, form, "stranger", self.reg(), no_lint).startswith("ignored"))
        self.assertEqual(git(self.origin, "branch", "--format=%(refname:short)").split(), ["main"])
        self.assertEqual(api.of("COMMENT"), [])

    def test_stale_suggestion_is_refused(self):
        api = FakeApi(permissions={"maint": "admin"})
        form = issue(5, "ann", correction_body(self.rec, **{"text-hash": "0" * 12}), ["korean-review", "accepted"])
        self.assertEqual(bot.accept(api, form, "maint", self.reg(), no_lint), "refused")
        self.assertEqual(git(self.origin, "branch", "--format=%(refname:short)").split(), ["main"])

    def test_lint_errors_are_never_applied(self):
        api = FakeApi(permissions={"maint": "admin"})
        form = issue(5, "ann", correction_body(self.rec), ["korean-review", "accepted"])
        bad = lambda text, rec: [{"rule": "KO004", "name": "n", "severity": "error", "message": "m"}]
        self.assertEqual(bot.accept(api, form, "maint", self.reg(), bad), "lint errors")
        self.assertEqual(git(self.origin, "branch", "--format=%(refname:short)").split(), ["main"])

    def test_generated_line_becomes_a_rule_task(self):
        api = FakeApi(permissions={"maint": "admin"})
        form = issue(6, "ann", correction_body(self.gen), ["korean-review", "accepted"])
        self.assertEqual(bot.accept(api, form, "maint", self.reg(), no_lint), "task 9")
        post = [c for c in api.calls if c[0] == "POST" and c[1] == "/repos/o/r/issues"][0]
        self.assertEqual(post[2]["title"], "fix generator rule clue/BreakBlock/MINE (affects 1 lines)")
        self.assertIn("generator-rule", post[2]["labels"])
        self.assertEqual(git(self.origin, "branch", "--format=%(refname:short)").split(), ["main"])
        self.assertEqual((self.work / "data" / "questlines" / "t.json").read_text(encoding="utf-8"), self.doc)

    def test_unsafe_content_file_is_refused(self):
        evil = dict(self.rec, source={"kind": "authored", "file": "../../etc/passwd", "pointer": "/x"})
        with self.assertRaises(ValueError):
            bot.content_path(evil)


class DailyApi(FakeApi):
    def __init__(self, pending, applied, comments, merged):
        super().__init__()
        self.pending, self.applied, self.comments, self.merged = pending, applied, comments, merged

    def pages(self, path, limit=300):
        self.calls.append(("PAGES", path, None))
        if "approval:pending" in path:
            return self.pending
        if "labels=applied" in path:
            return self.applied
        if path.endswith("/comments"):
            return self.comments
        return []

    def request(self, method, path, data=None):
        if method == "GET" and "/pulls/" in path:
            return {"merged": self.merged}
        return super().request(method, path, data)

    def user(self, login):
        return {"created_at": OLD}


class DailyTest(unittest.TestCase):
    def test_trust_candidates(self):
        reviewers = {"reviewers": {"a": {"accepted": 30, "reverted": 0, "trusted": False},
                                   "b": {"accepted": 30, "reverted": 1}, "c": {"accepted": 29},
                                   "d": {"accepted": 90, "trusted": True}}}
        self.assertEqual(bot.trust_candidates(reviewers), ["a"])

    def setUp(self):
        import credits
        import status as st
        from common import write_status
        self.tmp = tempfile.TemporaryDirectory()
        tmp = Path(self.tmp.name)
        self.origin = tmp / "origin.git"
        self.work = tmp / "work"
        git(tmp, "init", "--bare", "-b", "main", str(self.origin))
        git(tmp, "clone", str(self.origin), str(self.work))
        self.rec = record()
        korean = self.work / "content" / "korean"
        korean.mkdir(parents=True)
        write_lines([self.rec], korean / "lines.jsonl")
        status = {"schema": 1, "lines": {}, "rules": {}}
        st.sync([self.rec], status, {}, set())
        write_status(status, korean / "review-status.json")
        (korean / "reviewers.json").write_text('{"reviewers": {}}\n', encoding="utf-8")
        git(self.work, "add", "-A")
        git(self.work, "commit", "-m", "init")
        git(self.work, "push", "origin", "HEAD:main")
        self.patches = [mock.patch.object(bot, "ROOT", self.work),
                        mock.patch.object(bot, "STATUS_PATH", korean / "review-status.json"),
                        mock.patch.object(bot, "LINES_PATH", korean / "lines.jsonl"),
                        mock.patch.object(bot, "REVIEWERS_PATH", korean / "reviewers.json"),
                        mock.patch.object(credits, "MD_PATH", self.work / "credits.md"),
                        mock.patch.object(credits, "JSON_PATH", self.work / "credits.json")]
        for p in self.patches:
            p.start()

    def tearDown(self):
        for p in self.patches:
            p.stop()
        self.tmp.cleanup()

    def approve_issue(self, number, login, **over):
        data = {"line-id": self.rec["id"], "text-hash": self.rec["textHash"], "current": self.rec["ko"],
                "proficiency": "native (원어민)", "credit": login.upper(), "confirm": "- [x] ok"}
        data.update(over)
        return issue(number, login, body(**data), ["korean-review", "korean:approve", "approval:pending"])

    def test_approvals_become_one_pull_request(self):
        api = DailyApi([self.approve_issue(1, "ann"), self.approve_issue(2, "bo"), self.approve_issue(3, "ann")],
                       [], [], False)
        self.assertEqual(bot.daily(api, NOW), "pr 7")
        branch = "korean-review/batch-2026-10-21"
        status = json.loads(git(self.origin, "show", f"{branch}:content/korean/review-status.json"))
        entry = status["lines"][self.rec["id"]]
        self.assertEqual(entry["status"], "native-approved")
        self.assertEqual([a["login"] for a in entry["approvals"]], ["ann", "bo"])
        self.assertIn("ANN", git(self.origin, "show", f"{branch}:credits.md"))
        counted = [c for c in api.of("LABELS") if c[2] == ["approval:counted"]]
        self.assertEqual({c[1] for c in counted}, {1, 2, 3})
        closed = [c for c in api.calls if c[0] == "PATCH" and c[2] and c[2].get("state") == "closed"]
        self.assertEqual(len(closed), 3)

    def test_nothing_to_do_makes_no_branch(self):
        api = DailyApi([], [], [], False)
        self.assertEqual(bot.daily(api, NOW), "nothing to write")
        self.assertEqual(git(self.origin, "branch", "--format=%(refname:short)").split(), ["main"])

    def test_merged_correction_makes_the_reviewer_an_author(self):
        applied = [issue(4, "ann", correction_body(self.rec), ["korean-review", "korean:correction", "applied"])]
        comments = [{"body": f"{bot.MARKER}\nThanks\n<!-- applied-pr:7 -->", "id": 1}]
        api = DailyApi([], applied, comments, True)
        self.assertEqual(bot.daily(api, NOW), "pr 7")
        branch = "korean-review/batch-2026-10-21"
        status = json.loads(git(self.origin, "show", f"{branch}:content/korean/review-status.json"))
        self.assertEqual(status["lines"][self.rec["id"]]["authors"], ["ann"])
        reviewers = json.loads(git(self.origin, "show", f"{branch}:content/korean/reviewers.json"))
        self.assertEqual(reviewers["reviewers"]["ann"]["accepted"], 1)

    def test_unmerged_correction_is_not_credited(self):
        applied = [issue(4, "ann", correction_body(self.rec), ["korean-review", "korean:correction", "applied"])]
        comments = [{"body": f"{bot.MARKER}\nThanks\n<!-- applied-pr:7 -->", "id": 1}]
        self.assertEqual(bot.daily(DailyApi([], applied, comments, False), NOW), "nothing to write")


if __name__ == "__main__":
    unittest.main()
