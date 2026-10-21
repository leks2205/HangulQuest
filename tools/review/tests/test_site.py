import html
import re
import tempfile
import unittest
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import _setup  # noqa: F401
from common import ROOT, read_lines, read_status

sys_path = str(ROOT / "tools" / "review_site")
import sys  # noqa: E402

sys.path.insert(0, sys_path)
import build as site_build  # noqa: E402


class SiteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls.tmp.name)
        site_build.build(cls.out, "someorg/SomeRepo")
        cls.files = {p.relative_to(cls.out).as_posix(): p.read_text(encoding="utf-8")
                     for p in cls.out.rglob("*") if p.is_file()}

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def hrefs(self, text):
        return [html.unescape(h) for h in re.findall(r'href="([^"]*)"', text)]

    def test_internal_links_resolve(self):
        broken = []
        for name, text in self.files.items():
            if not name.endswith(".html"):
                continue
            for href in self.hrefs(text):
                if href.startswith(("https://", "mailto:")):
                    continue
                target = (Path(name).parent / href.split("#")[0]).as_posix()
                target = str(Path(target)) if ".." not in target else Path(self.out, target).resolve().relative_to(self.out.resolve()).as_posix()
                if target not in self.files:
                    broken.append((name, href))
        self.assertEqual(broken[:5], [])

    def test_no_scripts_and_no_external_resources(self):
        for name, text in self.files.items():
            self.assertNotIn("<script", text, name)
            self.assertNotRegex(text, r'(src|href)="https?://(?!github\.com/)', name)
            self.assertNotIn("@import", text)
        self.assertNotIn("url(", self.files["style.css"])

    def test_every_line_is_on_a_level_page_and_a_page_has_the_three_buttons(self):
        ids = {r["id"] for r in read_lines()}
        on_pages = set()
        for name, text in self.files.items():
            if name.startswith("level/"):
                on_pages |= {html.unescape(i) for i in re.findall(r'<article class="line" id="l-([^"]+)"', text)}
        self.assertEqual(ids - on_pages, set())
        page = self.files["level/L1-1.html"]
        self.assertIn("맞아요", page)
        self.assertIn("고치기 제안", page)
        self.assertIn("이 페이지 전체 승인", page)
        self.assertLess(page.index("class=\"ko\""), page.index("class=\"en\""))  # Korean first

    def test_issue_links_use_the_configured_repo_and_real_form_fields(self):
        forms = {}
        for path in (ROOT / ".github" / "ISSUE_TEMPLATE").glob("korean-*.yml"):
            forms[path.name] = set(re.findall(r"^\s+id: (\S+)$", path.read_text(encoding="utf-8"), re.M))
        longest = 0
        seen = set()
        for name, text in self.files.items():
            for href in self.hrefs(text):
                if "issues/new" not in href:
                    continue
                url = urlparse(href)
                self.assertEqual(url.netloc + url.path, "github.com/someorg/SomeRepo/issues/new", href)
                query = parse_qs(url.query)
                template = query["template"][0]
                seen.add(template)
                self.assertIn(template, forms)
                for key in query:
                    self.assertIn(key, forms[template] | {"template", "title"}, (template, key))
                longest = max(longest, len(href))
        self.assertEqual(seen, set(forms) )
        self.assertLess(longest, site_build.MAX_URL)

    def test_prefilled_hash_matches_the_registry(self):
        records = {r["id"]: r for r in read_lines()}
        page = self.files["trail/abenteuerpfad.html"]
        urls = [h for h in self.hrefs(page) if "korean-approve" in h]
        self.assertGreater(len(urls), 3)
        for href in urls:
            query = parse_qs(urlparse(href).query)
            rec = records[query["line-id"][0]]
            self.assertEqual(query["text-hash"][0], rec["textHash"])
            self.assertEqual(query["current"][0], rec["ko"])

    def test_batch_link_lists_the_ids_of_the_page(self):
        page = self.files["trail/abenteuerpfad.html"]
        href = [h for h in self.hrefs(page) if "korean-batch-approve" in h][0]
        lines = parse_qs(urlparse(href).query)["lines"][0].splitlines()
        shown = re.findall(r'<article class="line" id="l-([^"]+)"', page)
        self.assertEqual([l.split("@")[0] for l in lines], shown)

    def test_markup_in_text_is_escaped(self):
        records = read_lines()[:1]
        records[0] = dict(records[0], ko="<b>x</b>&")
        s = site_build.Site("a/b", records, {"lines": {}, "rules": {}})
        self.assertIn("&lt;b&gt;x&lt;/b&gt;&amp;", s.line(records[0]))
        self.assertNotIn("<b>x", s.line(records[0]))

    def test_dark_theme_and_viewport(self):
        self.assertIn("prefers-color-scheme: dark", self.files["style.css"])
        self.assertIn('name="viewport"', self.files["index.html"])

    def test_priority_queue_puts_levels_1_and_2_first(self):
        first = self.files["queue/Q-1.html"]
        levels = [int(l) for l in re.findall(r"레벨 (\d) · Level", first)]
        self.assertTrue(levels and all(l in (1, 2) for l in levels))


if __name__ == "__main__":
    unittest.main()
