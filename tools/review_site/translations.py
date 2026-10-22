"""Translation pages of the review site: one page list per language with the rows no person has approved yet (a machine
draft, stale, or proofread by a second model), each with a button that opens a prefilled translation-correction issue form.

Nothing is built while no language has a status file (content/translations/<code>-status.json), so the
site stays what it was until the first translation is imported. The rows come from tools/i18n (the
English files and the language files); the states are the ones `translate.py status` prints.
"""
from __future__ import annotations

import html
import sys
from pathlib import Path
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "i18n"))
import tr_core as core  # noqa: E402

PAGE_ROWS = 100
LISTED = ("machine", "stale", "proofread")
BADGE = {"machine": "lint-ok", "stale": "unreviewed", "proofread": "ai-checked"}
STATE_TEXT = {"machine": "Machine draft", "stale": "English changed since the translation",
              "proofread": "Proofread by AI, not yet approved by a person"}


def esc(text: object) -> str:
    return html.escape(str(text), quote=True)


def managed_languages(repo: core.Repo) -> list[str]:
    if not repo.status_dir.is_dir():
        return []
    return sorted(path.name.removesuffix("-status.json") for path in repo.status_dir.glob("*-status.json"))


def correction_url(site_repo: str, code: str, key: str, current: str, max_url: int) -> str:
    """The prefilled form. A text too long for a URL is left for the reporter to paste."""
    base = f"https://github.com/{site_repo}/issues/new?template=translation-correction.yml"
    fields = {"title": f"[Translation] {code}: {key}", "language": code, "key": key, "current": current}
    url = base + "".join(f"&{name}={quote(value, safe='')}" for name, value in fields.items())
    if len(url) > max_url:
        fields.pop("current")
        url = base + "".join(f"&{name}={quote(value, safe='')}" for name, value in fields.items())
    return url


def rows_to_review(repo: core.Repo, code: str, rows: list[core.Row]) -> list[tuple[core.Row, str, str]]:
    bundle = core.merged_bundle(repo, code)
    status = core.read_status(repo, code)["keys"]
    out = []
    for row in rows:
        text = bundle.get(row["key"])
        state = core.effective_state(status.get(row["key"]), row, text)
        if state in LISTED:
            out.append((row, text, state))
    return out


def build_pages(site, repo: core.Repo | None = None) -> dict[str, str]:
    """{relative path: html} for the translation index and every language; empty without languages."""
    repo = repo or core.Repo(core.ROOT)
    codes = managed_languages(repo)
    if not codes:
        return {}
    rows = core.source_rows(repo)
    pages: dict[str, str] = {}
    items = []
    for code in codes:
        todo = rows_to_review(repo, code, rows)
        translated = sum(1 for row in rows if core.merged_bundle(repo, code).get(row["key"]))
        chunks = [todo[i:i + PAGE_ROWS] for i in range(0, len(todo), PAGE_ROWS)] or [[]]
        for n, chunk in enumerate(chunks, 1):
            nav = []
            if n > 1:
                nav.append(f'<a href="{code}-{n - 1}.html">Previous</a>')
            nav.append(f"{n} / {len(chunks)}")
            if n < len(chunks):
                nav.append(f'<a href="{code}-{n + 1}.html">Next</a>')
            pager = f'<p class="pager">{" · ".join(nav)}</p>'
            body = (f'<p lang="en">{len(todo)} line(s) of {esc(code)} have not been approved by a person yet: machine drafts, '
                    f"lines whose English changed, and lines only proofread by AI ({translated} of {len(rows)} lines are translated; "
                    "the others show the English text in the game). Read a line and, if it is wrong or unnatural, "
                    "press <strong>Suggest a correction</strong>. Fixing the file by pull request works too.</p>")
            body += pager + "".join(_row(site, code, row, text, state) for row, text, state in chunk) + pager
            pages[f"translation/{code}-{n}.html"] = site.shell(f"번역 {code}", f"Translation {code} {n}", body, 1, "")
        items.append(f'<li><a href="translation/{code}-1.html">{esc(code)}</a> — {len(todo)} to check, '
                     f"{translated} of {len(rows)} translated</li>")
    pages["translations.html"] = site.shell(
        "번역", "Translations",
        '<p lang="en">Machine-made translations that need a native speaker. Lines already proofread by AI are listed too: a person has not approved them yet. English is the source; Korean is never translated.</p>'
        f'<ul class="list">{"".join(items)}</ul>', 0, "")
    return pages


def _row(site, code: str, row: core.Row, text: str, state: str) -> str:
    url = correction_url(site.repo, code, row["key"], text, 7000)
    korean = f'<p class="ko" lang="ko">{esc(row["ko"])}</p>' if row.get("ko") else ""
    return (f'<article class="line" id="t-{esc(row["key"])}">\n{korean}'
            f'<p class="en" lang="en">{esc(row["en"])}</p>\n<p class="en">{esc(text)}</p>\n'
            f'<p class="meta"><span class="badge s-{BADGE[state]}">{STATE_TEXT[state]}</span></p>\n'
            f'<p class="actions"><a class="btn fix" href="{esc(url)}">Suggest a correction</a></p>\n'
            f'<p class="id"><code>{esc(row["key"])}</code></p>\n</article>\n')
