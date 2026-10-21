import unittest
from datetime import datetime, timezone

import _setup  # noqa: F401
import bot
import forms
import import_ai
import status as st
from common import COUNTING_PROFICIENCY, text_hash

from test_forms import NOW, OLD, YOUNG, body, record, registry


def fresh(*records):
    status = {"schema": 1, "lines": {}, "rules": {}}
    st.sync(list(records), status, {}, set())
    return status


class StatusTest(unittest.TestCase):
    def setUp(self):
        self.rec = record()
        self.status = fresh(self.rec)
        self.entry = self.status["lines"][self.rec["id"]]

    def refresh(self, bad=False, reviewers=None):
        st.sync([self.rec], self.status, reviewers or {}, {self.rec["id"]} if bad else set())
        self.entry = self.status["lines"][self.rec["id"]]
        return self.entry["status"]

    def note(self, reviewer, verdict):
        self.entry["aiNotes"][reviewer] = {"verdict": verdict, "issue": "", "suggestion": "", "textHash": self.rec["textHash"]}

    def test_new_line_is_lint_ok_or_unreviewed(self):
        self.assertEqual(self.entry["status"], "lint-ok")
        self.assertEqual(self.refresh(bad=True), "unreviewed")

    def test_both_ok_is_ai_checked(self):
        self.note("claude", "ok")
        self.note("gemini", "ok")
        self.assertEqual(self.refresh(), "ai-checked")
        self.assertEqual(self.entry["flags"], [])

    def test_one_reviewer_is_not_enough(self):
        self.note("claude", "ok")
        self.assertEqual(self.refresh(), "lint-ok")

    def test_error_or_disagreement_needs_native(self):
        self.note("claude", "ok")
        self.note("gemini", "error")
        self.assertEqual(self.refresh(), "lint-ok")
        self.assertIn("needs-native", self.entry["flags"])
        self.assertIn("ai-error", self.entry["flags"])
        self.note("gemini", "minor")
        self.refresh()
        self.assertIn("ai-disagree", self.entry["flags"])
        self.assertIn("needs-native", self.entry["flags"])

    def test_two_minors_agree_without_needs_native(self):
        self.note("claude", "minor")
        self.note("gemini", "minor")
        self.refresh()
        self.assertEqual(self.entry["flags"], ["ai-minor"])

    def test_two_approvals_make_native_approved(self):
        for login in ("ann", "bo"):
            bot.add_approval(self.entry, login, "native", 1, "2026-10-21")
        self.assertEqual(self.refresh(), "native-approved")

    def test_one_approval_or_a_learner_is_not_enough(self):
        bot.add_approval(self.entry, "ann", "native", 1, "2026-10-21")
        bot.add_approval(self.entry, "cy", "learner", 2, "2026-10-21")
        self.assertEqual(self.refresh(), "lint-ok")
        self.assertNotIn("learner", COUNTING_PROFICIENCY)

    def test_the_same_person_counts_once(self):
        self.assertTrue(bot.add_approval(self.entry, "Ann", "native", 1, "d"))
        self.assertFalse(bot.add_approval(self.entry, "ann", "native", 2, "d"))
        self.assertEqual(self.refresh(), "lint-ok")

    def test_trusted_reviewer_alone_is_enough(self):
        bot.add_approval(self.entry, "Ann", "fluent", 1, "d")
        reviewers = {"reviewers": {"Ann": {"trusted": True}}}
        self.assertEqual(self.refresh(reviewers=reviewers), "native-approved")

    def test_native_flag(self):
        self.entry["flags"].append("native-flagged:#7")
        self.assertEqual(self.refresh(), "native-flagged")

    def test_changed_text_drops_the_status(self):
        self.note("claude", "ok")
        self.note("gemini", "ok")
        for login in ("ann", "bo"):
            bot.add_approval(self.entry, login, "native", 1, "d")
        self.entry["authors"] = ["writer"]
        self.assertEqual(self.refresh(), "native-approved")
        self.rec = record(ko="돌을 캐요.")
        self.assertEqual(self.refresh(), "lint-ok")
        self.assertEqual(self.entry["approvals"], [])
        self.assertEqual(self.entry["aiNotes"], {})
        self.assertEqual(self.entry["textHash"], text_hash("돌을 캐요."))
        self.assertEqual(self.entry["authors"], ["writer"])

    def test_check_finds_drift(self):
        self.assertEqual(st.check([self.rec], self.status), [])
        self.assertEqual(len(st.check([record(ko="다른 문장")], self.status)), 1)
        self.assertEqual(len(st.check([self.rec, record("trail.t.s2.clue")], self.status)), 1)


class ImportAiTest(unittest.TestCase):
    def test_import_and_stale_text(self):
        rec, other = record(), record("trail.t.s2.clue", "물을 마셔요.")
        status = fresh(rec, other)
        rows = [{"id": rec["id"], "verdict": "ok", "issue": "", "suggestion": ""},
                {"id": other["id"], "verdict": "error", "issue": "x", "suggestion": "y"},
                {"id": "ghost", "verdict": "ok"}, {"id": rec["id"], "verdict": "great"}]
        counts = import_ai.import_notes(status, [rec, other], "claude", rows, {other["id"]: "예전 문장"})
        self.assertEqual(counts, {"imported": 1, "unknown-id": 1, "stale-text": 1, "bad-verdict": 1})
        self.assertEqual(status["lines"][rec["id"]]["aiNotes"]["claude"]["verdict"], "ok")
        self.assertNotIn("claude", status["lines"][other["id"]]["aiNotes"])


class DailyApprovalsTest(unittest.TestCase):
    def issue(self, number, login, rec, kind="korean:approve", **over):
        data = {"line-id": rec["id"], "text-hash": rec["textHash"], "current": rec["ko"],
                "proficiency": "native (원어민)", "credit": "민수", "confirm": "- [x] ok"}
        data.update(over)
        return {"number": number, "user": {"login": login}, "body": body(**data), "labels": [{"name": kind}]}

    def test_counts_checks_again_and_dedupes(self):
        rec = record()
        reg = registry(rec, authors={rec["id"]: ["writer"]})
        reg.status["lines"][rec["id"]].update(status="lint-ok", textHash=rec["textHash"], approvals=[], flags=[], aiNotes={})
        issues = [self.issue(1, "ann", rec), self.issue(2, "ann", rec), self.issue(3, "writer", rec),
                  self.issue(4, "kid", rec), self.issue(5, "bo", rec, **{"text-hash": "b" * 12}),
                  self.issue(6, "cy", rec, proficiency="learner (학습자)")]
        accounts = {"ann": OLD, "writer": OLD, "kid": YOUNG, "bo": OLD, "cy": OLD}
        outcome = bot.apply_approvals(reg, issues, accounts, NOW)
        logins = [a["login"] for a in reg.status["lines"][rec["id"]]["approvals"]]
        self.assertEqual(logins, ["ann"])
        self.assertEqual(outcome[1]["counted"], 1)
        self.assertEqual(outcome[2]["counted"], 0)
        for n in (3, 4, 5, 6):
            self.assertEqual(outcome[n]["counted"], 0, n)
        self.assertEqual(reg.reviewers["reviewers"]["ann"]["display"], "민수")

    def test_unknown_account_never_counts(self):
        rec = record()
        reg = registry(rec)
        reg.status["lines"][rec["id"]].update(approvals=[])
        bot.apply_approvals(reg, [self.issue(1, "ghost", rec)], {}, NOW)
        self.assertEqual(reg.status["lines"][rec["id"]]["approvals"], [])


if __name__ == "__main__":
    unittest.main()
