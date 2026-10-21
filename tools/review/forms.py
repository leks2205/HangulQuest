"""Parse and judge the Korean review issue forms. Pure functions, no network and no shell: the text of
an issue is data, and nothing in here evaluates it.

The headings below are the `label:` values of the forms in .github/ISSUE_TEMPLATE/korean-*.yml;
test_forms checks that the two stay in sync.
"""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, field
from datetime import datetime, timezone

from common import COUNTING_PROFICIENCY, ID_PATTERN, PROFICIENCY, text_hash

HEADINGS = {
    "문장 번호 / Line id": "line-id",
    "확인 코드 / Text hash": "text-hash",
    "지금 문장 / Current text": "current",
    "고친 문장 / Suggested text": "suggestion",
    "이유 / Reason": "reason",
    "한국어 실력 / Your Korean": "proficiency",
    "크레딧에 쓸 이름 (선택) / Name for the credits (optional)": "credit",
    "동의 / Agreement": "consent",
    "확인 / Confirmation": "confirm",
    "페이지 / Page": "page",
    "문장 목록 / Lines (id@hash)": "lines",
    "주제 / Topic": "topic",
    "내용 / Details": "details",
    "관련 문장 번호 (선택) / Related line ids (optional)": "line-ids",
}
KIND_LABELS = {
    "korean:correction": "correction",
    "korean:approve": "approve",
    "korean:batch-approve": "batch",
    "korean:general": "general",
}
MAX_SUGGESTION = 300
MAX_BATCH_LINES = 100
MIN_ACCOUNT_DAYS = 30
HANGUL = re.compile(r"[가-힣]")
ANONYMOUS = {"anonymous", "anon", "익명", "-", "no", "none"}


@dataclass
class Form:
    kind: str | None
    fields: dict[str, str] = field(default_factory=dict)
    checked: dict[str, bool] = field(default_factory=dict)


def parse_form(body: str, labels: list[str] | None = None) -> Form:
    """Issue-form body -> field values. Unknown headings are ignored; the first heading of a name wins."""
    fields: dict[str, str] = {}
    checked: dict[str, bool] = {}
    key = None
    buffer: list[str] = []

    def flush() -> None:
        if key is None or key in fields:
            return
        value = "\n".join(buffer).strip()
        if value == "_No response_":
            value = ""
        if key in ("consent", "confirm"):
            checked[key] = bool(re.search(r"^- \[[xX]\]", value, re.M)) and not re.search(r"^- \[ \]", value, re.M)
            value = ""
        fields[key] = value

    for raw in (body or "").replace("\r\n", "\n").split("\n"):
        match = re.match(r"^### (.+?)\s*$", raw)
        if match:
            flush()
            key = HEADINGS.get(match.group(1))
            buffer = []
        elif key is not None:
            buffer.append(raw)
    flush()
    return Form(kind_of(labels or [], fields), fields, checked)


def kind_of(labels: list[str], fields: dict[str, str]) -> str | None:
    for label in labels:
        if label in KIND_LABELS:
            return KIND_LABELS[label]
    if "suggestion" in fields:
        return "correction"
    if "lines" in fields:
        return "batch"
    if "details" in fields:
        return "general"
    if "line-id" in fields:
        return "approve"
    return None


def clean_line(text: str) -> str:
    """One NFC line without control or zero-width characters."""
    text = unicodedata.normalize("NFC", text or "").strip()
    return "".join(ch for ch in text if unicodedata.category(ch) not in ("Cc", "Cf", "Zl", "Zp"))


def proficiency_of(raw: str) -> str:
    word = (raw or "").strip().split(" ")[0].lower()
    return word if word in PROFICIENCY else ""


def credit_of(raw: str) -> tuple[str, bool]:
    """(display name, opt out)"""
    name = clean_line(raw)[:60]
    if name.lower() in ANONYMOUS:
        return "", True
    return name, False


def account_old_enough(created_at: str, now: datetime | None = None) -> bool:
    created = datetime.fromisoformat(created_at.replace("Z", "+00:00"))
    now = now or datetime.now(timezone.utc)
    return (now - created).days >= MIN_ACCOUNT_DAYS


def code_block(text: str, limit: int = 400) -> str:
    """Fenced block for text that came from an issue: no markdown, no mentions, no links."""
    text = text.replace("\r", "")[:limit]
    longest = max((len(m) for m in re.findall(r"`+", text)), default=0)
    fence = "`" * max(3, longest + 1)
    return f"{fence}\n{text}\n{fence}"


def parse_line_refs(text: str) -> list[tuple[str, str]]:
    """'id@hash' per line -> [(id, hash)]. At most MAX_BATCH_LINES; malformed entries are dropped."""
    refs = []
    for raw in (text or "").splitlines():
        raw = raw.strip().lstrip("-* ").strip()
        if "@" not in raw:
            continue
        line_id, _, digest = raw.partition("@")
        if ID_PATTERN.match(line_id) and re.fullmatch(r"[0-9a-f]{12}", digest):
            refs.append((line_id, digest))
        if len(refs) >= MAX_BATCH_LINES:
            break
    return refs


@dataclass
class Registry:
    lines: dict[str, dict]
    status: dict
    reviewers: dict

    def authors(self, line_id: str) -> set[str]:
        return {a.lower() for a in self.status["lines"].get(line_id, {}).get("authors", [])}


def check_line(line_id: str, claimed_hash: str, claimed_text: str, reg: Registry) -> tuple[str, dict | None]:
    """('ok'|'invalid'|'unknown'|'stale', record). The hash (or the shown text) must match the registry."""
    line_id = (line_id or "").strip()
    if not ID_PATTERN.match(line_id):
        return "invalid", None
    record = reg.lines.get(line_id)
    if record is None:
        return "unknown", None
    claimed_hash = (claimed_hash or "").strip()
    claimed_text = clean_line(claimed_text)
    if not claimed_hash and not claimed_text:
        return "invalid", record
    if claimed_hash and claimed_hash != record["textHash"]:
        return "stale", record
    if claimed_text and text_hash(claimed_text) != record["textHash"]:
        return "stale", record
    return "ok", record


@dataclass
class Result:
    labels: list[str] = field(default_factory=list)
    comment: str = ""
    ok: bool = False
    record: dict | None = None
    findings: list[dict] = field(default_factory=list)


def stale_comment(line_id: str, record: dict | None, site: str) -> str:
    if record is None:
        return (f"I do not know a line with the id {code_block(line_id, 200)}\n"
                f"Please open the form again from the review site: {site}")
    return (f"The line `{record['id']}` has changed since this form was filled in, so I did not use it.\n\n"
            f"Current text:\n\n{code_block(record['ko'])}\n"
            f"If it still needs a change, please open the form again from the review site: {site}")


def assess_correction(form: Form, reg: Registry, lint, site: str) -> Result:
    f = form.fields
    state, record = check_line(f.get("line-id", ""), f.get("text-hash", ""), f.get("current", ""), reg)
    if state in ("unknown", "stale"):
        return Result(["bot:stale" if state == "stale" else "bot:unknown-line"],
                      stale_comment(f.get("line-id", ""), record, site), False, record)
    if state == "invalid":
        return Result(["bot:invalid"], "The line id or the current text is missing, so I cannot tell which line "
                      "this is about. Please open the form from the review site.", False, record)
    suggestion = clean_line(f.get("suggestion", ""))
    problems = []
    if not suggestion:
        problems.append("The suggested text is empty.")
    elif len(suggestion) > MAX_SUGGESTION:
        problems.append(f"The suggested text is longer than {MAX_SUGGESTION} characters.")
    elif not HANGUL.search(suggestion):
        problems.append("The suggested text has no Hangul.")
    elif suggestion == record["ko"]:
        problems.append("The suggestion is the same as the current text.")
    if not form.checked.get("consent"):
        problems.append("The agreement box is not ticked.")
    if proficiency_of(f.get("proficiency", "")) == "":
        problems.append("Your Korean level is missing.")
    if problems:
        return Result(["bot:invalid"], "I could not use this form:\n\n" + "\n".join(f"- {p}" for p in problems),
                      False, record)
    findings = lint(suggestion, record)
    errors = [x for x in findings if x["severity"] == "error"]
    warnings = [x for x in findings if x["severity"] != "error"]
    lines = [f"Thank you! Suggestion for `{record['id']}` (level {record['context']['level']}):", "",
             "Current text:", "", code_block(record["ko"]), "", "Suggested text:", "", code_block(suggestion), ""]
    if errors:
        lines.append("The Korean linter found problems in the suggestion, so a maintainer will look at it first:")
        lines += [f"- `{x['rule']}` {x['name']}: {x['message']}" for x in errors[:8]]
    else:
        lines.append("The Korean linter finds no errors in the suggestion.")
    if warnings:
        lines.append("")
        lines += [f"- warning `{x['rule']}` {x['name']}: {x['message']}" for x in warnings[:5]]
    lines.append("")
    lines.append("A maintainer decides on this suggestion (label `accepted` or `rejected`). "
                 + ("The line is generated by a rule, so an accepted suggestion becomes a task to fix the rule "
                    f"`{record['source'].get('rule')}`." if record["source"]["kind"] == "generated"
                    else "An accepted suggestion becomes a pull request that changes exactly this line."))
    return Result(["bot:lint-error" if errors else "bot:lint-ok", "needs-maintainer"], "\n".join(lines),
                  not errors, record, findings)


def _approval_gate(form: Form, login: str, account_created: str | None, now: datetime | None) -> list[str]:
    """Reasons an approval by this account cannot count (empty when it can)."""
    reasons = []
    if proficiency_of(form.fields.get("proficiency", "")) not in COUNTING_PROFICIENCY:
        reasons.append("Learners are welcome to comment, but only native, fluent or teacher approvals count.")
    if not form.checked.get("confirm"):
        reasons.append("The confirmation box is not ticked.")
    if account_created is not None and not account_old_enough(account_created, now):
        reasons.append(f"Accounts must be at least {MIN_ACCOUNT_DAYS} days old for an approval to count.")
    return reasons


def assess_approve(form: Form, reg: Registry, login: str, account_created: str | None, site: str,
                   now: datetime | None = None) -> Result:
    f = form.fields
    state, record = check_line(f.get("line-id", ""), f.get("text-hash", ""), f.get("current", ""), reg)
    if state != "ok":
        label = {"stale": "bot:stale", "unknown": "bot:unknown-line"}.get(state, "bot:invalid")
        return Result([label], stale_comment(f.get("line-id", ""), record, site)
                      if state in ("stale", "unknown") else "The line id or the text is missing.", False, record)
    reasons = _approval_gate(form, login, account_created, now)
    if login.lower() in reg.authors(record["id"]):
        reasons.append("Nobody can approve a line they wrote themselves.")
    if reasons:
        return Result(["approval:ignored"], "This approval does not count:\n\n" + "\n".join(f"- {r}" for r in reasons),
                      False, record)
    return Result(["approval:pending"], f"Thank you! Your approval of `{record['id']}` will be written to "
                  "`review-status.json` in the next daily batch pull request.", True, record)


def assess_batch(form: Form, reg: Registry, login: str, account_created: str | None, site: str,
                 now: datetime | None = None) -> Result:
    refs = parse_line_refs(form.fields.get("lines", ""))
    if not refs:
        return Result(["bot:invalid"], "I found no `id@hash` lines in this form. Please open it from the review site.")
    reasons = _approval_gate(form, login, account_created, now)
    good, stale, unknown, own = [], [], [], []
    for line_id, digest in refs:
        state, record = check_line(line_id, digest, "", reg)
        if state == "ok":
            (own if login.lower() in reg.authors(line_id) else good).append(line_id)
        elif state == "stale":
            stale.append(line_id)
        else:
            unknown.append(line_id)
    if reasons:
        return Result(["approval:ignored"], "This approval does not count:\n\n" + "\n".join(f"- {r}" for r in reasons))
    lines = [f"Thank you! {len(good)} of {len(refs)} lines will be written to `review-status.json` "
             "in the next daily batch pull request."]
    if stale:
        lines.append(f"- {len(stale)} line(s) changed since the page was built and are skipped: "
                     + ", ".join(f"`{i}`" for i in stale[:8]) + (" ..." if len(stale) > 8 else ""))
    if unknown:
        lines.append(f"- {len(unknown)} unknown id(s) are skipped.")
    if own:
        lines.append(f"- {len(own)} line(s) you wrote yourself are skipped.")
    if not good:
        return Result(["bot:stale"], "\n".join(lines))
    return Result(["approval:pending"], "\n".join(lines), True)


def assess_general(form: Form) -> Result:
    return Result(["needs-maintainer"], "Thank you! A maintainer will read this and answer here.", True)
