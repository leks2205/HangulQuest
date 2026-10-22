#!/usr/bin/env python3
"""Build the static Korean review site from content/korean/lines.jsonl and review-status.json.

    python3 tools/review_site/build.py                       # docs/book/book/review/
    python3 tools/review_site/build.py --out /tmp/review --repo owner/name

Plain HTML and one CSS file, no scripts, no external requests. Korean comes first, English second.
Every button is a link to a prefilled GitHub issue form:
    https://github.com/<repo>/issues/new?template=<form>.yml&title=...&line-id=...&text-hash=...&current=...
GitHub fills a form field from a query parameter named like the field id (text inputs and text areas);
the URL must stay well below the server limit (about 8 KB), so build() fails if one gets too long.
The repository comes from --repo, then $REVIEW_REPO, then the default in tools/review/common.py.
"""
from __future__ import annotations

import argparse
import html
import os
import shutil
import sys
from pathlib import Path
from urllib.parse import quote

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "review"))
from common import DEFAULT_REPO, LINES_PATH, ROOT, STATUS_PATH, read_lines, read_status, rule_slug  # noqa: E402
from export_lines import pretty  # noqa: E402
from status import rule_samples, valid_approvals  # noqa: E402
from translations import build_pages as translation_pages  # noqa: E402

PAGE_SIZE = 25
QUEUE_PAGES = 20
MAX_URL = 7000
STATUS_TEXT = {
    "unreviewed": ("검토 전", "Unreviewed"),
    "lint-ok": ("자동 검사 통과", "Lint OK"),
    "ai-checked": ("AI 확인", "AI checked"),
    "native-approved": ("원어민 승인", "Native approved"),
    "native-flagged": ("원어민 수정 요청", "Flagged by a native speaker"),
}
VERDICT_TEXT = {"ok": "괜찮아요 / ok", "minor": "사소한 지적 / minor", "error": "오류 / error"}
FIELD_TASK = {
    "title": "Name of the trail (a short chain of quests).",
    "clue": "Clue in the quest list. It tells the player what to do.",
    "hint": "Easier hint the player gets when asking for help with this quest step.",
    "finale": "Message shown when the player finishes the trail.",
    "ko": "Word card: the Korean word.",
    "descKo": "Word card: a short description in simple Korean.",
    "sentenceKo": "Word card: an example sentence.",
    "dialogue": "Villager conversation: a line of the NPC or a reply the player can pick.",
    "story": "Library story for beginners: the title or one paragraph. Simple, natural Korean in the polite 해요체; only level 1-2 grammar.",
    "culture": "Culture note in the Library: a short Korean summary (level 1-2). The longer explanation is in the interface language.",
    "chapter": "Title of a chapter in the quest tab, a life situation such as 집 (house) or 불과 빛 (fire and light).",
}
KIND_ORDER = {"authored": 0, "generated": 1, "lexicon": 2}
NAV = [("index", "처음", "Home"), ("queue", "우선순위", "Priority"), ("levels", "레벨", "Levels"),
       ("trails", "길", "Trails"), ("rules", "규칙", "Rules")]


def esc(text: object) -> str:
    return html.escape(str(text), quote=True)


def trail_order(rec: dict) -> tuple:
    """Title, then the steps in order (clue before hint), then the finale step, then the finale text."""
    ctx = rec["context"]
    step = ctx["step"]
    if ctx["field"] == "title":
        return (0, 0, "")
    if step == "finale":
        return (2, 0, ctx["field"])
    if step:
        return (1, int(step[1:]) if step[1:].isdigit() else 0, ctx["field"])
    return (3, 0, "")


class Site:
    def __init__(self, repo: str, records: list[dict], status: dict):
        self.repo = repo
        self.records = records
        self.by_id = {r["id"]: r for r in records}
        self.status = status
        self.pages: dict[str, str] = {}

    # ---- status helpers
    def entry(self, line_id: str) -> dict:
        return self.status["lines"].get(line_id) or {"status": "unreviewed", "approvals": [], "flags": [], "aiNotes": {}}

    def approvals(self, line_id: str) -> int:
        return len(valid_approvals(self.entry(line_id)["approvals"]))

    def needs_native(self, line_id: str) -> bool:
        return "needs-native" in self.entry(line_id)["flags"]

    def done(self, line_id: str) -> bool:
        return self.entry(line_id)["status"] == "native-approved"

    # ---- GitHub links
    def issue_url(self, template: str, **fields: str) -> str:
        query = "&".join(f"{key}={quote(value, safe='')}" for key, value in fields.items())
        url = f"https://github.com/{self.repo}/issues/new?template={template}&{query}"
        if len(url) > MAX_URL:
            raise ValueError(f"issue form URL too long ({len(url)}): {url[:120]}")
        return url

    def line_url(self, template: str, verb: str, rec: dict) -> str:
        return self.issue_url(template, title=f"[Korean] {verb}: {rec['id']}", **{
            "line-id": rec["id"], "text-hash": rec["textHash"], "current": rec["ko"]})

    def batch_url(self, page: str, ids: list[str], title: str) -> str:
        lines = "\n".join(f"{i}@{self.by_id[i]['textHash']}" for i in ids)
        return self.issue_url("korean-batch-approve.yml", title=f"[Korean] approve page: {title}", page=page,
                              lines=lines)

    # ---- html pieces
    def badge(self, line_id: str) -> str:
        status = self.entry(line_id)["status"]
        ko, en = STATUS_TEXT[status]
        out = f'<span class="badge s-{status}">{ko} · {en}</span>'
        if self.needs_native(line_id):
            out += ' <span class="badge s-needs">원어민 확인 필요 · Needs a native speaker</span>'
        return out

    def task(self, rec: dict) -> str:
        ctx = rec["context"]
        text = FIELD_TASK.get(ctx["field"], "")
        if ctx.get("goal"):
            text += f" Goal: {ctx['goal']}."
        if ctx.get("trailTitle"):
            text += f" Trail: {ctx['trailTitle']}."
        return text

    def ai_notes(self, line_id: str) -> str:
        notes = self.entry(line_id)["aiNotes"]
        if not notes:
            return ""
        items = []
        for name, note in sorted(notes.items()):
            detail = esc(note.get("issue", ""))
            fix = note.get("suggestion", "")
            items.append(f"<li><strong>{esc(name)}</strong>: {VERDICT_TEXT.get(note['verdict'], esc(note['verdict']))}"
                         + (f" — {detail}" if detail else "")
                         + (f' <span lang="ko">→ {esc(fix)}</span>' if fix else "") + "</li>")
        return f'<details class="ai"><summary>AI 메모 · AI notes</summary><ul>{"".join(items)}</ul></details>'

    def line(self, rec: dict) -> str:
        ctx = rec["context"]
        rid = rec["id"]
        meta = [f"레벨 {ctx['level']} · Level {ctx['level']}", f"승인 {self.approvals(rid)}명 · {self.approvals(rid)} approvals"]
        if ctx.get("icon"):
            meta.append(f"아이콘 · Icon: {esc(pretty(ctx['icon']))}")
        if rec["source"]["kind"] == "generated":
            rule = rec["source"]["rule"]
            meta.append(f'자동 생성 · Generated by <a href="../rule/{rule_slug(rule)}.html">{esc(rule)}</a>')
        elif rec["source"]["kind"] == "lexicon":
            meta.append("단어장 · Vocabulary")
        english = f'<p class="en" lang="en">{esc(rec["en"])}</p>' if rec["en"] else ""
        return f"""<article class="line" id="l-{esc(rid)}">
<p class="ko" lang="ko">{esc(rec['ko'])}</p>
{english}<p class="task">{esc(self.task(rec))}</p>
<p class="meta">{self.badge(rid)} <span>{' </span><span>'.join(meta)}</span></p>
{self.ai_notes(rid)}<p class="actions"><a class="btn ok" href="{esc(self.line_url('korean-approve.yml', 'approve', rec))}">맞아요 <small>Looks right</small></a>
<a class="btn fix" href="{esc(self.line_url('korean-correction.yml', 'correction', rec))}">고치기 제안 <small>Suggest a correction</small></a></p>
<p class="id"><code>{esc(rid)}</code></p>
</article>
"""

    def approve_page(self, page_id: str, ids: list[str], title: str) -> str:
        url = self.batch_url(page_id, ids, title)
        return (f'<p class="batch"><a class="btn page" href="{esc(url)}">이 페이지 전체 승인 <small>Approve this page</small></a><br>'
                '<small lang="ko">페이지의 문장을 모두 읽었을 때만 눌러 주세요. '
                '<span lang="en">Use only after reading every line.</span></small></p>')

    def shell(self, title_ko: str, title_en: str, body: str, depth: int, current: str = "") -> str:
        up = "../" * depth
        here = ' aria-current="page"'
        nav = " ".join(
            f'<a href="{up}{name}.html"{here if name == current else ""}>{ko}<span> {en}</span></a>'
            for name, ko, en in NAV)
        return f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>{esc(title_ko)} · {esc(title_en)} · 한글 탐험</title>
<link rel="stylesheet" href="{up}style.css">
</head>
<body>
<header><p class="brand"><a href="{up}index.html">한글 탐험 <span>Hangul Quest</span></a></p>
<nav aria-label="menu">{nav}</nav></header>
<main>
<h1>{esc(title_ko)} <span lang="en">{esc(title_en)}</span></h1>
{body}
</main>
<footer><p lang="ko">이 사이트의 한국어 안내도 어색하면 알려 주세요.
<a href="{esc(self.issue_url('korean-general.yml', title='[Korean] question: review site'))}">의견 보내기</a></p>
<p lang="en">If the Korean on this site reads oddly, please tell us.
<a href="https://github.com/{esc(self.repo)}">Project on GitHub</a></p></footer>
</body>
</html>
"""

    # ---- listings
    def stats_line(self, ids: list[str]) -> str:
        done = sum(1 for i in ids if self.done(i))
        flagged = sum(1 for i in ids if self.needs_native(i) and not self.done(i))
        return (f'<p class="stats">문장 {len(ids)}개 · 원어민 승인 {done}개 · 확인 필요 {flagged}개 '
                f'<span lang="en">({len(ids)} lines, {done} native-approved, {flagged} need a native speaker)</span></p>')

    def paged(self, key: str, folder: str, title_ko: str, title_en: str, records: list[dict], intro: str,
              current: str, size: int = PAGE_SIZE, limit: int | None = None) -> list[str]:
        """Write the pages of one list; returns their relative paths."""
        if limit is not None:
            records = records[:limit]
        chunks = [records[i:i + size] for i in range(0, len(records), size)] or [[]]
        paths = []
        for n, chunk in enumerate(chunks, 1):
            ids = [r["id"] for r in chunk]
            page_id = f"{key}/{n}"
            nav = []
            if n > 1:
                nav.append(f'<a href="{key}-{n - 1}.html">← 이전 <span lang="en">Previous</span></a>')
            nav.append(f"{n} / {len(chunks)}")
            if n < len(chunks):
                nav.append(f'<a href="{key}-{n + 1}.html">다음 → <span lang="en">Next</span></a>')
            pager = f'<p class="pager">{" · ".join(nav)}</p>'
            body = intro + (self.stats_line(ids) if ids else "") + pager
            if ids:
                body += self.approve_page(page_id, ids, f"{title_ko} {n}")
            body += "".join(self.line(r) for r in chunk)
            if ids:
                body += self.approve_page(page_id, ids, f"{title_ko} {n}")
            body += pager
            path = f"{folder}/{key}-{n}.html"
            self.pages[path] = self.shell(f"{title_ko} {n}", f"{title_en} {n}", body, 1, current)
            paths.append(path)
        return paths

    def build_levels(self) -> None:
        rows = []
        for level in sorted({r["context"]["level"] for r in self.records}):
            recs = sorted((r for r in self.records if r["context"]["level"] == level),
                          key=lambda r: (KIND_ORDER[r["source"]["kind"]], r["id"]))
            paths = self.paged(f"L{level}", "level", f"레벨 {level}", f"Level {level}", recs,
                               '<p lang="ko">이 레벨의 문장이에요. 위에서부터 차례로 봐 주세요.</p>', "levels")
            ids = [r["id"] for r in recs]
            done = sum(1 for i in ids if self.done(i))
            rows.append(f'<li><a href="{paths[0]}">레벨 {level} · Level {level}</a> — {len(ids)} / {done} '
                        f'<span lang="ko">(문장 / 원어민 승인)</span><span lang="en"> (lines / native-approved)</span></li>')
        body = '<p lang="ko">한국어 수준에 따라 문장을 나눴어요. 낮은 레벨부터 봐 주세요.</p>' \
               f'<ul class="list">{"".join(rows)}</ul>'
        self.pages["levels.html"] = self.shell("레벨", "Levels", body, 0, "levels")

    def build_queue(self) -> None:
        todo = [r for r in self.records if not self.done(r["id"])]
        todo.sort(key=lambda r: (0 if r["context"]["level"] in (1, 2) else 1,
                                 0 if self.needs_native(r["id"]) else 1, r["context"]["level"],
                                 KIND_ORDER[r["source"]["kind"]], r["id"]))
        intro = ('<p lang="ko">먼저 보면 좋은 문장이에요. 레벨 1과 2가 먼저 나오고, 원어민 확인이 필요한 문장이 그 앞에 와요.</p>'
                 '<p lang="en">Level 1 and 2 first; within them the lines that need a native speaker come first.</p>')
        paths = self.paged("Q", "queue", "우선순위 목록", "Priority queue", todo, intro, "queue", limit=PAGE_SIZE * QUEUE_PAGES)
        self.pages["queue.html"] = (f'<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0; url={paths[0]}">'
                                    f'<a href="{paths[0]}">우선순위 목록 · Priority queue</a>')

    def build_trails(self) -> None:
        trails: dict[str, list[dict]] = {}
        for rec in self.records:
            if rec["id"].startswith("trail."):
                trails.setdefault(rec["context"]["trail"], []).append(rec)
        groups: dict[str, list[tuple]] = {}
        for tid, recs in trails.items():
            recs.sort(key=trail_order)
            title = next((r for r in recs if r["context"]["field"] == "title"), recs[0])
            level = title["context"]["level"]
            ids = [r["id"] for r in recs]
            body = (f'<p lang="en">{esc(title["context"]["trailTitle"])} · level {level}</p>' + self.stats_line(ids)
                    + self.approve_page(f"trail/{tid}", ids, tid) + "".join(self.line(r) for r in recs)
                    + self.approve_page(f"trail/{tid}", ids, tid))
            self.pages[f"trail/{tid}.html"] = self.shell(title["ko"], title["context"]["trailTitle"] or tid, body, 1, "trails")
            groups.setdefault(title["context"]["topic"] or "기타", []).append(
                (level, tid, title["ko"], title["context"]["trailTitle"], ids))
        parts = []
        for topic in sorted(groups, key=lambda t: (not any("가" <= c <= "힣" for c in t), t)):
            items = "".join(
                f'<li><a href="trail/{tid}.html"><span lang="ko">{esc(ko)}</span> <span lang="en">{esc(en)}</span></a> '
                f'— {len(ids)} · {sum(1 for i in ids if self.done(i))} · L{level}</li>'
                for level, tid, ko, en, ids in sorted(groups[topic]))
            parts.append(f'<section><h2 lang="{"ko" if any("가" <= c <= "힣" for c in topic) else "en"}">{esc(topic)}</h2>'
                         f'<ul class="list">{items}</ul></section>')
        intro = ('<p lang="ko">퀘스트 길마다 문장이 모여 있어요. 숫자는 문장 수와 원어민 승인 수예요.</p>'
                 '<p lang="en">One page per trail. Numbers: lines, native-approved, level.</p>')
        self.pages["trails.html"] = self.shell("길", "Trails", intro + "".join(parts), 0, "trails")

    def build_rules(self) -> None:
        rules: dict[str, list[dict]] = {}
        for rec in self.records:
            if rec["source"].get("rule"):
                rules.setdefault(rec["source"]["rule"], []).append(rec)
        rows = []
        for rule in sorted(rules, key=lambda r: (-len(rules[r]), r)):
            samples = rule_samples(self.records, rule)
            ids = [r["id"] for r in samples]
            entry = self.status["rules"].get(rule, {"status": "unreviewed", "approvals": []})
            ko, en = STATUS_TEXT[entry["status"]]
            field, goal, verb = (rule.split("/") + ["", "", ""])[:3]
            about = (f"{field.capitalize()} sentences for goal type {goal or '?'} with the verb group {verb or '?'}. "
                     "The game builds them from a template; they are not written by hand.")
            body = (f'<p lang="ko">이 규칙은 문장 {len(rules[rule])}개를 만들어요. 아래 예시가 자연스러우면 규칙 전체를 승인해 주세요.</p>'
                    f'<p lang="en">{esc(about)} It makes {len(rules[rule])} lines; approving the samples approves the rule.</p>'
                    f'<p><span class="badge s-{entry["status"]}">{ko} · {en}</span> '
                    f'<span>승인 {len(valid_approvals(entry["approvals"]))}명</span></p>'
                    + self.approve_page(f"rule:{rule}", ids, rule) + "".join(self.line(r) for r in samples)
                    + self.approve_page(f"rule:{rule}", ids, rule))
            self.pages[f"rule/{rule_slug(rule)}.html"] = self.shell(f"규칙 {rule}", f"Rule {rule}", body, 1, "rules")
            rows.append(f'<li><a href="rule/{rule_slug(rule)}.html"><code>{esc(rule)}</code></a> — '
                        f'{len(rules[rule])} · <span class="badge s-{entry["status"]}">{ko}</span></li>')
        intro = ('<p lang="ko">게임이 템플릿으로 만드는 문장이에요. 규칙 하나를 확인하면 같은 규칙의 문장이 모두 좋아져요.</p>'
                 '<p lang="en">Generated lines, grouped by the rule that makes them. One check covers every line of a rule.</p>')
        self.pages["rules.html"] = self.shell("규칙", "Rules", intro + f'<ul class="list">{"".join(rows)}</ul>', 0, "rules")

    def build_index(self) -> None:
        total = len(self.records)
        counts: dict[str, int] = {}
        for rec in self.records:
            status = self.entry(rec["id"])["status"]
            counts[status] = counts.get(status, 0) + 1
        rows = "".join(f'<tr><td><span class="badge s-{s}">{STATUS_TEXT[s][0]} · {STATUS_TEXT[s][1]}</span></td>'
                       f"<td>{counts.get(s, 0)}</td></tr>" for s in STATUS_TEXT)
        body = f"""<section lang="ko">
<p>한글 탐험은 마인크래프트로 한국어를 배우는 모드예요. 게임에 나오는 한국어 문장이 자연스러운지 한국어를 잘하시는 분이 봐 주시면 좋겠어요.</p>
<ol class="steps">
<li>문장을 읽어요.</li>
<li>자연스러우면 <strong>맞아요</strong>를 눌러요.</li>
<li>어색하거나 틀리면 <strong>고치기 제안</strong>을 눌러요.</li>
</ol>
<p>GitHub 계정이 필요해요. 만든 지 30일이 지난 계정의 승인만 세어요. 자세한 안내는 <a href="https://github.com/{esc(self.repo)}/blob/main/docs/review/README.ko.md">검토 안내서</a>에 있어요.</p>
<p class="cta"><a class="btn page" href="queue/Q-1.html">먼저 볼 문장 <small>Start with the priority queue</small></a></p>
</section>
<section lang="en">
<h2>Help review the Korean</h2>
<p>Hangul Quest is a Minecraft mod that teaches Korean. Every Korean line below is checked by a linter and two AI reviewers, but only native speakers can say that a line really sounds natural. Read a line, press <strong>Looks right</strong> or <strong>Suggest a correction</strong>. A GitHub account is needed; approvals count from accounts at least 30 days old, and nobody can approve their own line.</p>
</section>
<section><h2>현황 <span lang="en">Status</span></h2>
<table class="status"><tbody>{rows}<tr><th>전체 <span lang="en">Total</span></th><th>{total}</th></tr></tbody></table></section>"""
        self.pages["index.html"] = self.shell("한국어 문장 검토", "Review the Korean", body, 0, "index")

    def build(self) -> dict[str, str]:
        self.build_index()
        self.build_queue()
        self.build_levels()
        self.build_trails()
        self.build_rules()
        self.build_translations()
        return self.pages

    def build_translations(self) -> None:
        """Pages for the machine-translated languages; nothing (and no link) until one has been imported."""
        pages = translation_pages(self)
        if pages:
            self.pages.update(pages)
            link = ('<section lang="en"><h2>Translations</h2><p>Help check the machine translations: '
                    '<a href="translations.html">languages and lines to check</a>.</p></section>\n</main>')
            self.pages["index.html"] = self.pages["index.html"].replace("</main>", link, 1)


def build(out: Path, repo: str, lines: Path = LINES_PATH, status: Path = STATUS_PATH) -> int:
    records = read_lines(lines)
    if not records:
        raise SystemExit(f"{lines} is empty. Run tools/review/export_lines.py first.")
    site = Site(repo, records, read_status(status))
    pages = site.build()
    if out.exists():
        shutil.rmtree(out)
    for rel, text in pages.items():
        path = out / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    shutil.copyfile(HERE / "style.css", out / "style.css")
    return len(pages)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(ROOT / "docs" / "book" / "book" / "review"))
    parser.add_argument("--repo", default=os.environ.get("REVIEW_REPO", DEFAULT_REPO), help="owner/name for the issue links")
    args = parser.parse_args(argv)
    count = build(Path(args.out), args.repo)
    print(f"wrote {count} pages to {args.out} (issue links go to github.com/{args.repo})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
