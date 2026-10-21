import json
import re
import unittest
from datetime import datetime, timezone

import _setup  # noqa: F401
import forms
from common import ROOT, text_hash

NOW = datetime(2026, 10, 21, tzinfo=timezone.utc)
OLD = "2020-01-01T00:00:00Z"
YOUNG = "2026-10-10T00:00:00Z"
SITE = "https://example.github.io/HangulQuest/review/"


def record(line_id="trail.t.s1.clue", ko="돌을 캐야 해요.", kind="authored", level=1):
    source = {"kind": kind, "file": "questlines/t.json", "pointer": "/steps/0/clue/ko"}
    if kind == "generated":
        source = {"kind": kind, "file": "questlines/t.json", "rule": "clue/BreakBlock/MINE"}
    return {"id": line_id, "ko": ko, "en": "Mine stone.", "de": "", "textHash": text_hash(ko),
            "context": {"level": level, "goalKey": ""}, "source": source}


def registry(*records, authors=None):
    status = {"lines": {r["id"]: {"authors": (authors or {}).get(r["id"], [])} for r in records}, "rules": {}}
    return forms.Registry({r["id"]: r for r in records}, status, {"reviewers": {}})


def body(**fields):
    names = {v: k for k, v in forms.HEADINGS.items()}
    return "\n\n".join(f"### {names[key]}\n\n{value}" for key, value in fields.items())


def correction_body(rec, **over):
    data = {"line-id": rec["id"], "text-hash": rec["textHash"], "current": rec["ko"], "suggestion": "돌을 캐요.",
            "reason": "simpler", "proficiency": "native (원어민)", "credit": "_No response_",
            "consent": "- [x] ok"}
    data.update(over)
    return body(**data)


def no_lint(text, rec):
    return []


class ParseTest(unittest.TestCase):
    def test_parses_a_rendered_form(self):
        rec = record()
        form = forms.parse_form(correction_body(rec), ["korean-review", "korean:correction"])
        self.assertEqual(form.kind, "correction")
        self.assertEqual(form.fields["line-id"], rec["id"])
        self.assertEqual(form.fields["suggestion"], "돌을 캐요.")
        self.assertEqual(form.fields["credit"], "")
        self.assertTrue(form.checked["consent"])

    def test_unticked_box_and_unknown_headings(self):
        form = forms.parse_form("### 동의 / Agreement\n\n- [ ] no\n\n### Something else\n\nx", [])
        self.assertFalse(form.checked["consent"])
        self.assertNotIn("Something else", form.fields)

    def test_markup_in_a_field_stays_data(self):
        evil = "`; rm -rf / #\n$(touch /tmp/x) ${{ secrets.X }} @octocat [a](http://x)"
        form = forms.parse_form(body(**{"line-id": "trail.t.s1.clue", "suggestion": evil}), [])
        self.assertEqual(form.fields["suggestion"], evil)
        quoted = forms.code_block(evil)
        self.assertTrue(quoted.startswith("```\n") and quoted.endswith("\n```"))
        fenced = forms.code_block("a ``` b")
        self.assertTrue(fenced.startswith("````"))

    def test_templates_and_parser_agree_on_labels(self):
        # a renamed label in a form would silently break the bot: compare both sides
        templates = sorted((ROOT / ".github" / "ISSUE_TEMPLATE").glob("korean-*.yml"))
        self.assertEqual(len(templates), 4)
        seen_labels, seen_ids = set(), set()
        for path in templates:
            text = path.read_text(encoding="utf-8")
            for label in re.findall(r'^\s+label: "(.+)"$', text, re.M):
                seen_labels.add(label)
            seen_ids.update(re.findall(r"^\s+id: (\S+)$", text, re.M))
            kinds = [x for x in re.findall(r'"(korean:[a-z-]+)"', text)]
            self.assertTrue(set(kinds) <= set(forms.KIND_LABELS), path.name)
            self.assertEqual(len(kinds), 1, path.name)
        known = set(forms.HEADINGS)
        self.assertEqual(seen_labels - known, set(), "form label the parser does not know")
        self.assertEqual(seen_ids - set(forms.HEADINGS.values()), set())
        self.assertEqual(known - seen_labels, set(), "parser heading no form has")

    def test_every_bot_label_exists_in_labels_json(self):
        labels = {l["name"] for l in json.loads((ROOT / "tools/review/labels.json").read_text(encoding="utf-8"))["labels"]}
        source = (ROOT / "tools/review/forms.py").read_text(encoding="utf-8") + \
            (ROOT / "tools/review/bot.py").read_text(encoding="utf-8")
        used = set(re.findall(r'"((?:bot|approval|korean):[a-z-]+|needs-maintainer|applied|credited|accepted|recheck)"', source))
        for path in (ROOT / ".github/ISSUE_TEMPLATE").glob("korean-*.yml"):
            for group in re.findall(r"^labels: \[(.+)\]$", path.read_text(encoding="utf-8"), re.M):
                used |= set(re.findall(r'"([^"]+)"', group))
        self.assertIn("approval:pending", used)
        self.assertIn("korean:general", used)
        self.assertEqual(used - labels, set())


class HashCheckTest(unittest.TestCase):
    def setUp(self):
        self.rec = record()
        self.reg = registry(self.rec)

    def test_matching_hash_and_text(self):
        self.assertEqual(forms.check_line(self.rec["id"], self.rec["textHash"], self.rec["ko"], self.reg)[0], "ok")

    def test_hash_only_or_text_only(self):
        self.assertEqual(forms.check_line(self.rec["id"], self.rec["textHash"], "", self.reg)[0], "ok")
        self.assertEqual(forms.check_line(self.rec["id"], "", self.rec["ko"], self.reg)[0], "ok")

    def test_changed_hash_is_stale(self):
        self.assertEqual(forms.check_line(self.rec["id"], "0" * 12, self.rec["ko"], self.reg)[0], "stale")

    def test_changed_text_is_stale(self):
        self.assertEqual(forms.check_line(self.rec["id"], "", "다른 문장이에요.", self.reg)[0], "stale")

    def test_unknown_and_malformed_ids(self):
        self.assertEqual(forms.check_line("nope.x", self.rec["textHash"], "", self.reg)[0], "unknown")
        self.assertEqual(forms.check_line("a b; rm", self.rec["textHash"], "", self.reg)[0], "invalid")
        self.assertEqual(forms.check_line(self.rec["id"], "", "", self.reg)[0], "invalid")

    def test_nfd_text_matches_nfc_hash(self):
        import unicodedata
        nfd = unicodedata.normalize("NFD", self.rec["ko"])
        self.assertEqual(forms.check_line(self.rec["id"], "", nfd, self.reg)[0], "ok")

    def test_stale_comment_shows_the_current_text(self):
        result = forms.assess_correction(forms.parse_form(correction_body(self.rec, **{"text-hash": "f" * 12})),
                                         self.reg, no_lint, SITE)
        self.assertIn("bot:stale", result.labels)
        self.assertIn(self.rec["ko"], result.comment)


class CorrectionTest(unittest.TestCase):
    def setUp(self):
        self.rec = record()
        self.reg = registry(self.rec)

    def assess(self, lint=no_lint, **over):
        return forms.assess_correction(forms.parse_form(correction_body(self.rec, **over)), self.reg, lint, SITE)

    def test_clean_suggestion(self):
        result = self.assess()
        self.assertEqual(set(result.labels), {"bot:lint-ok", "needs-maintainer"})
        self.assertTrue(result.ok)

    def test_lint_errors_are_reported(self):
        def lint(text, rec):
            return [{"rule": "KO004", "name": "object-particle-mismatch", "severity": "error", "message": "x"}]
        result = self.assess(lint)
        self.assertIn("bot:lint-error", result.labels)
        self.assertIn("KO004", result.comment)
        self.assertFalse(result.ok)

    def test_real_linter_catches_a_wrong_particle(self):
        from bot import default_lint
        result = self.assess(default_lint, suggestion="돌를 캐요.")
        self.assertIn("bot:lint-error", result.labels)
        self.assertIn("bot:lint-ok", self.assess(default_lint).labels)

    def test_invalid_forms(self):
        for over in ({"suggestion": ""}, {"suggestion": "stone"}, {"suggestion": "돌을 캐야 해요."},
                     {"suggestion": "가" * 400}, {"consent": "- [ ] no"}, {"proficiency": ""}):
            self.assertEqual(self.assess(**over).labels, ["bot:invalid"], over)

    def test_zero_width_characters_are_dropped(self):
        self.assertEqual(forms.clean_line("돌​을 캐요.‮"), "돌을 캐요.")

    def test_credit_and_proficiency(self):
        self.assertEqual(forms.credit_of("anonymous"), ("", True))
        self.assertEqual(forms.credit_of("  민수 "), ("민수", False))
        self.assertEqual(forms.proficiency_of("teacher (한국어 선생님)"), "teacher")
        self.assertEqual(forms.proficiency_of("wizard"), "")


class ApprovalGateTest(unittest.TestCase):
    def setUp(self):
        self.rec = record()
        self.reg = registry(self.rec, authors={self.rec["id"]: ["Writer"]})

    def approve(self, login="reviewer", created=OLD, **over):
        data = {"line-id": self.rec["id"], "text-hash": self.rec["textHash"], "current": self.rec["ko"],
                "proficiency": "native (원어민)", "credit": "", "confirm": "- [x] ok"}
        data.update(over)
        return forms.assess_approve(forms.parse_form(body(**data), ["korean:approve"]), self.reg, login, created,
                                    SITE, NOW)

    def test_counts(self):
        self.assertEqual(self.approve().labels, ["approval:pending"])

    def test_learner_does_not_count(self):
        self.assertEqual(self.approve(proficiency="learner (학습자)").labels, ["approval:ignored"])

    def test_account_age_boundary(self):
        self.assertTrue(forms.account_old_enough("2026-09-21T00:00:00Z", NOW))
        self.assertFalse(forms.account_old_enough("2026-09-22T00:00:00Z", NOW))
        self.assertEqual(self.approve(created=YOUNG).labels, ["approval:ignored"])

    def test_nobody_approves_their_own_line(self):
        self.assertEqual(self.approve(login="writer").labels, ["approval:ignored"])

    def test_unticked_confirmation(self):
        self.assertEqual(self.approve(confirm="- [ ] no").labels, ["approval:ignored"])

    def test_stale_approval(self):
        self.assertEqual(self.approve(**{"text-hash": "a" * 12}).labels, ["bot:stale"])

    def test_batch(self):
        other = record("trail.t.s2.clue", "물을 마셔야 해요.")
        reg = registry(self.rec, other, authors={self.rec["id"]: ["me"]})
        lines = f"{self.rec['id']}@{self.rec['textHash']}\n{other['id']}@{other['textHash']}\nbad@{'0' * 12}"
        form = forms.parse_form(body(page="level-1/1", lines=lines, proficiency="fluent (유창함)",
                                     credit="", confirm="- [x] ok"), ["korean:batch-approve"])
        result = forms.assess_batch(form, reg, "me", OLD, SITE, NOW)
        self.assertEqual(result.labels, ["approval:pending"])
        self.assertIn("1 of 3", result.comment)
        self.assertIn("wrote yourself", result.comment)

    def test_parse_line_refs_drops_junk(self):
        text = f"- a.b@{'a' * 12}\nnot a ref\nx y@{'a' * 12}\nc.d@zzz"
        self.assertEqual(forms.parse_line_refs(text), [("a.b", "a" * 12)])


if __name__ == "__main__":
    unittest.main()
