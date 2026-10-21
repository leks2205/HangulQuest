#!/usr/bin/env python3
"""The Korean review bot. Runs from .github/workflows/korean-review-bot.yml.

    bot.py triage          # an issue was opened (or the maintainer added `recheck`)
    bot.py accept          # the maintainer added `accepted`: open a PR or a generator-rule task
    bot.py daily           # collect approvals and merged corrections into one pull request
    bot.py ensure-labels   # create the labels of tools/review/labels.json

Security rules, all of them on purpose:
  * Issue text is data. It is parsed by forms.py and never evaluated, never put into a shell command
    and never written to a file that is executed. The workflow passes nothing from the issue through
    `${{ }}`; this script reads the event payload from GITHUB_EVENT_PATH and the issue again from the API.
  * Write actions (a pull request, a task) start only from the maintainer labels, and only when the
    account that added the label has write access to the repository.
  * Git runs as an argument list, never through a shell. Branch names are built from numbers and dates.
  * Text quoted in comments goes into code blocks, so nothing in an issue can ping or link anyone.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import credits  # noqa: E402
import forms  # noqa: E402
import pointer as pointer_mod  # noqa: E402
import status as status_mod  # noqa: E402
from common import (DATA_DIR, LABELS_PATH, LINES_PATH, REVIEWERS_PATH, ROOT, STATUS_PATH, lines_by_id,  # noqa: E402
                    read_json, read_lines, read_status, text_hash, write_lines, write_status)

MARKER = "<!-- hangulquest-review-bot -->"
LOGIN = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
SAFE_FILE = re.compile(r"^(?:questlines/[A-Za-z0-9_\-]+\.json|dialogue/[A-Za-z0-9_\-]+\.json|concepts\.json)$")
WRITE_PERMISSIONS = ("admin", "maintain", "write")
TRUST_AFTER = 30


# --------------------------------------------------------------------------- GitHub API

class GitHub:
    def __init__(self, repo: str, token: str):
        self.repo = repo
        self.token = token

    def request(self, method: str, path: str, body: dict | None = None):
        url = path if path.startswith("https://") else f"https://api.github.com{path}"
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json",
            "User-Agent": "hangulquest-review-bot"})
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                payload = response.read()
        except urllib.error.HTTPError as err:
            if err.code == 404 and method == "GET":
                return None
            raise RuntimeError(f"{method} {path}: HTTP {err.code}") from None
        return json.loads(payload) if payload else None

    def pages(self, path: str, limit: int = 300) -> list:
        out, page = [], 1
        while len(out) < limit:
            sep = "&" if "?" in path else "?"
            chunk = self.request("GET", f"{path}{sep}per_page=100&page={page}") or []
            out += chunk
            if len(chunk) < 100:
                break
            page += 1
        return out[:limit]

    def comment(self, number: int, body: str) -> None:
        """Create or update the bot's one comment on an issue."""
        text = f"{MARKER}\n{body}"
        for existing in self.pages(f"/repos/{self.repo}/issues/{number}/comments"):
            if existing["body"].startswith(MARKER):
                self.request("PATCH", f"/repos/{self.repo}/issues/comments/{existing['id']}", {"body": text})
                return
        self.request("POST", f"/repos/{self.repo}/issues/{number}/comments", {"body": text})

    def add_labels(self, number: int, labels: list[str]) -> None:
        if labels:
            self.request("POST", f"/repos/{self.repo}/issues/{number}/labels", {"labels": labels})

    def remove_label(self, number: int, label: str) -> None:
        try:
            self.request("DELETE", f"/repos/{self.repo}/issues/{number}/labels/{urllib.parse.quote(label)}")
        except RuntimeError:
            pass

    def permission(self, login: str) -> str:
        if not LOGIN.match(login):
            return "none"
        data = self.request("GET", f"/repos/{self.repo}/collaborators/{login}/permission")
        return (data or {}).get("permission", "none")

    def user(self, login: str) -> dict:
        return self.request("GET", f"/users/{urllib.parse.quote(login)}") or {}


def site_url(repo: str) -> str:
    override = os.environ.get("REVIEW_SITE_URL")
    if override:
        return override
    owner, _, name = repo.partition("/")
    return f"https://{owner}.github.io/{name}/review/"


# --------------------------------------------------------------------------- shared state

def load_registry() -> forms.Registry:
    return forms.Registry(lines_by_id(read_lines(LINES_PATH)), read_status(STATUS_PATH), read_json(REVIEWERS_PATH, {"reviewers": {}}))


def default_lint(text: str, record: dict) -> list[dict]:
    import lint_korean
    return lint_korean.lint_text(text, record["id"], record["context"]["level"],
                                 goal=record["context"].get("goalKey", ""))


def labels_of(issue: dict) -> list[str]:
    return [label["name"] for label in issue.get("labels", [])]


# --------------------------------------------------------------------------- triage

def triage(api: GitHub, issue: dict, registry: forms.Registry, lint=default_lint, now: datetime | None = None) -> forms.Result | None:
    labels = labels_of(issue)
    if "korean-review" not in labels or (issue.get("user") or {}).get("type") == "Bot":
        return None
    form = forms.parse_form(issue.get("body") or "", labels)
    login = issue["user"]["login"]
    site = site_url(api.repo)
    if form.kind == "correction":
        result = forms.assess_correction(form, registry, lint, site)
    elif form.kind in ("approve", "batch"):
        created = api.user(login).get("created_at") if LOGIN.match(login) else None
        assess = forms.assess_approve if form.kind == "approve" else forms.assess_batch
        result = assess(form, registry, login, created, site, now)
    elif form.kind == "general":
        result = forms.assess_general(form)
    else:
        result = forms.Result(["bot:invalid"], "I could not read this form. Please open it from the review site.")
    number = issue["number"]
    for stale in ("bot:stale", "bot:unknown-line", "bot:invalid", "bot:lint-error", "bot:lint-ok",
                  "approval:pending", "approval:ignored"):
        if stale in labels and stale not in result.labels:
            api.remove_label(number, stale)
    api.add_labels(number, result.labels)
    api.comment(number, result.comment)
    return result


# --------------------------------------------------------------------------- accept

def apply_correction(text: str, pointer: str, old: str, new: str) -> str:
    return pointer_mod.replace_string(text, pointer, old, new)


def content_path(record: dict) -> Path:
    file = record["source"].get("file", "")
    if not SAFE_FILE.match(file):
        raise ValueError(f"unexpected content file {file!r}")
    # Dialogue files sit next to the data folder (assets/hangulquest/dialogue), the rest inside it.
    base = DATA_DIR.parent if file.startswith("dialogue/") else DATA_DIR
    path = (base / file).resolve()
    if base.resolve() not in path.parents:
        raise ValueError(f"content file outside the data directory: {file!r}")
    return path


def rule_issue_body(record: dict, affected: list[dict], suggestion: str, number: int, reason: str, rule: str) -> str:
    samples = "\n".join(f"- `{r['id']}`" for r in affected[:10])
    return (f"<!-- rule-fix:{rule} -->\n"
            f"A reviewer suggested a different text for a line that a generator rule produces "
            f"(see #{number}). The rule `{rule}` makes {len(affected)} line(s).\n\n"
            f"Example line `{record['id']}`:\n\nCurrent:\n\n{forms.code_block(record['ko'])}\n\n"
            f"Suggested:\n\n{forms.code_block(suggestion)}\n\nReason given:\n\n{forms.code_block(reason)}\n\n"
            f"Lines made by this rule (first 10):\n\n{samples}\n\n"
            "Fix: change the generator (`SentenceBuilder`, `KoGrammar` or `verbs.json`), add a gold regression case "
            "and, if useful, a rule in `tools/lint/rules.json`; then rerun `tools/review/export_lines.py`.")


def run_git(*args: str) -> str:
    result = subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True)
    return result.stdout


def commit_and_push(branch: str, message: str, paths: list[Path]) -> None:
    run_git("checkout", "-B", branch)
    run_git("add", "--", *[str(p) for p in paths])
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8") as handle:
        handle.write(message)
        message_file = handle.name
    try:
        run_git("-c", "user.name=github-actions[bot]", "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                "commit", "-F", message_file)
    finally:
        os.unlink(message_file)
    run_git("push", "--force", "origin", f"HEAD:refs/heads/{branch}")


def open_pr(api: GitHub, branch: str, title: str, body: str) -> dict:
    base = (api.request("GET", f"/repos/{api.repo}") or {}).get("default_branch", "main")
    existing = api.request("GET", f"/repos/{api.repo}/pulls?head={api.repo.split('/')[0]}:{branch}&state=open") or []
    if existing:
        return existing[0]
    return api.request("POST", f"/repos/{api.repo}/pulls", {"title": title, "head": branch, "base": base, "body": body})


def accept(api: GitHub, issue: dict, sender: str, registry: forms.Registry, lint=default_lint) -> str:
    if api.permission(sender) not in WRITE_PERMISSIONS:
        return "ignored: sender has no write access"
    number = issue["number"]
    form = forms.parse_form(issue.get("body") or "", labels_of(issue))
    if form.kind != "correction":
        api.comment(number, "Only correction forms can be accepted.")
        return "not a correction"
    result = forms.assess_correction(form, registry, lint, site_url(api.repo))
    if not result.record or (not result.ok and "bot:lint-error" not in result.labels):
        api.add_labels(number, result.labels)
        api.comment(number, result.comment + "\n\nThe label `accepted` was not applied because of this.")
        api.remove_label(number, "accepted")
        return "refused"
    if "bot:lint-error" in result.labels:
        api.comment(number, result.comment + "\n\nA suggestion with linter errors is not applied. "
                    "Ask the reviewer to adjust it, or fix it by hand.")
        api.remove_label(number, "accepted")
        return "lint errors"
    record = result.record
    suggestion = forms.clean_line(form.fields["suggestion"])
    reason = form.fields.get("reason", "")
    login = issue["user"]["login"]
    if record["source"]["kind"] == "generated":
        return accept_generated(api, issue, registry, record, suggestion, reason)
    path = content_path(record)
    path.write_text(apply_correction(path.read_text(encoding="utf-8"), record["source"]["pointer"],
                                     record["ko"], suggestion), encoding="utf-8")
    records = read_lines(LINES_PATH)
    for item in records:
        if item["id"] == record["id"]:
            item["ko"], item["textHash"] = suggestion, text_hash(suggestion)
    write_lines(records, LINES_PATH)
    user = api.user(login)
    trailer = f"Co-authored-by: {login} <{user.get('id', 0)}+{login}@users.noreply.github.com>" if LOGIN.match(login) else ""
    message = (f"content: fix Korean line {record['id']}\n\nSuggested by a reviewer in #{number}.\n\n"
               + (trailer + "\n" if trailer else ""))
    branch = f"korean-review/fix-{number}"
    commit_and_push(branch, message, [path, LINES_PATH])
    pr = open_pr(api, branch, f"content: fix Korean line {record['id']}",
                 f"Changes exactly `{record['source']['pointer']}` in `{record['source']['file']}` "
                 f"(plus the matching record in `content/korean/lines.jsonl`).\n\n"
                 f"Old:\n\n{forms.code_block(record['ko'])}\n\nNew:\n\n{forms.code_block(suggestion)}\n\n"
                 f"Fixes #{number}.")
    api.add_labels(number, ["applied"])
    api.comment(number, f"Thank you! Pull request #{pr['number']} applies this correction. "
                f"It is credited to you when it is merged.\n<!-- applied-pr:{pr['number']} -->")
    return f"pr {pr['number']}"


def accept_generated(api: GitHub, issue: dict, registry: forms.Registry, record: dict, suggestion: str, reason: str) -> str:
    rule = record["source"]["rule"]
    affected = [r for r in registry.lines.values() if r["source"].get("rule") == rule]
    marker = f"<!-- rule-fix:{rule} -->"
    task = None
    for existing in api.pages(f"/repos/{api.repo}/issues?labels=generator-rule&state=open"):
        if marker in (existing.get("body") or ""):
            task = existing
            break
    number = issue["number"]
    body = rule_issue_body(record, affected, suggestion, number, reason, rule)
    if task is None:
        task = api.request("POST", f"/repos/{api.repo}/issues", {
            "title": f"fix generator rule {rule} (affects {len(affected)} lines)", "body": body,
            "labels": ["generator-rule", "korean-review"]})
    else:
        api.request("POST", f"/repos/{api.repo}/issues/{task['number']}/comments", {"body": body})
    api.add_labels(number, ["applied"])
    api.comment(number, f"Thank you! The line is generated by the rule `{rule}`, so the fix goes into the generator: "
                f"see #{task['number']}.\n<!-- applied-issue:{task['number']} -->")
    return f"task {task['number']}"


# --------------------------------------------------------------------------- daily batch

def add_approval(entry: dict, login: str, proficiency: str, issue: int, day: str) -> bool:
    if any(a["login"].lower() == login.lower() for a in entry["approvals"]):
        return False
    entry["approvals"].append({"login": login, "at": day, "proficiency": proficiency, "issue": issue})
    return True


def update_reviewer(reviewers: dict, login: str, credit_raw: str) -> None:
    people = reviewers.setdefault("reviewers", {})
    person = people.setdefault(login, {"display": "", "optOut": False, "accepted": 0, "reverted": 0, "trusted": False})
    name, opt_out = forms.credit_of(credit_raw)
    if credit_raw.strip() and credit_raw.strip() != "_No response_":
        person["display"], person["optOut"] = name, opt_out


def apply_approvals(reg: forms.Registry, issues: list[dict], accounts: dict[str, str],
                    now: datetime) -> dict[int, dict]:
    """Write the valid approvals of the open `approval:pending` issues into reg.status.

    Everything is checked again here, from the current issue body: the line still has the hash, the
    account is old enough, the approver is not an author of the line, one approval per account.
    Returns {issue number: {"counted": n, "skipped": n}}.
    """
    outcome = {}
    day = now.date().isoformat()
    for issue in issues:
        login = issue["user"]["login"]
        form = forms.parse_form(issue.get("body") or "", labels_of(issue))
        counted = skipped = 0
        created = accounts.get(login)
        gate = forms._approval_gate(form, login, created, now) if form.kind in ("approve", "batch") else ["not an approval"]
        if created is None:
            gate = gate + ["unknown account"]
        if gate:
            outcome[issue["number"]] = {"counted": 0, "skipped": 1}
            continue
        prof = forms.proficiency_of(form.fields.get("proficiency", ""))
        if form.kind == "approve":
            refs = [(form.fields.get("line-id", "").strip(), form.fields.get("text-hash", "").strip(),
                     form.fields.get("current", ""))]
        else:
            refs = [(i, h, "") for i, h in forms.parse_line_refs(form.fields.get("lines", ""))]
        page = form.fields.get("page", "").strip()
        approved_ids = []
        for line_id, digest, text in refs:
            state, record = forms.check_line(line_id, digest, text, reg)
            entry = reg.status["lines"].get(line_id)
            if state != "ok" or entry is None or login.lower() in reg.authors(line_id):
                skipped += 1
                continue
            if add_approval(entry, login, prof, issue["number"], day):
                counted += 1
            approved_ids.append(line_id)
        if form.kind == "batch" and page.startswith("rule:"):
            rule = page[len("rule:"):]
            rule_entry = reg.status["rules"].get(rule)
            samples = status_mod.rule_samples(list(reg.lines.values()), rule)
            if rule_entry and {r["id"] for r in samples} == set(approved_ids) \
                    and status_mod.rule_hash(samples) == rule_entry["textHash"]:
                add_approval(rule_entry, login, prof, issue["number"], day)
        update_reviewer(reg.reviewers, login, form.fields.get("credit", ""))
        outcome[issue["number"]] = {"counted": counted, "skipped": skipped}
    return outcome


def credit_merged(api: GitHub, reg: forms.Registry, issue: dict) -> bool:
    """A correction whose pull request is merged: remember the author of the line, count it for the reviewer."""
    comments = api.pages(f"/repos/{api.repo}/issues/{issue['number']}/comments")
    numbers = [int(m.group(1)) for c in comments if c["body"].startswith(MARKER)
               for m in re.finditer(r"<!-- applied-pr:(\d+) -->", c["body"])]
    if not numbers:
        return False
    pr = api.request("GET", f"/repos/{api.repo}/pulls/{numbers[-1]}") or {}
    if not pr.get("merged"):
        return False
    form = forms.parse_form(issue.get("body") or "", labels_of(issue))
    line_id = form.fields.get("line-id", "").strip()
    login = issue["user"]["login"]
    entry = reg.status["lines"].get(line_id)
    if entry is not None and login not in entry.setdefault("authors", []):
        entry["authors"].append(login)
    update_reviewer(reg.reviewers, login, form.fields.get("credit", ""))
    reg.reviewers["reviewers"][login]["accepted"] += 1
    return True


def trust_candidates(reviewers: dict) -> list[str]:
    return sorted(login for login, p in reviewers.get("reviewers", {}).items()
                  if not p.get("trusted") and p.get("accepted", 0) >= TRUST_AFTER and p.get("reverted", 0) == 0)


def daily(api: GitHub, now: datetime | None = None) -> str:
    now = now or datetime.now(timezone.utc)
    reg = load_registry()
    snapshot = json.dumps([reg.status, reg.reviewers], sort_keys=True)
    pending = [i for i in api.pages(f"/repos/{api.repo}/issues?labels=approval:pending&state=open")
               if "pull_request" not in i]
    applied = [i for i in api.pages(f"/repos/{api.repo}/issues?labels=applied&state=all")
               if "pull_request" not in i and "credited" not in labels_of(i)]
    accounts = {}
    for issue in pending:
        login = issue["user"]["login"]
        if login not in accounts and LOGIN.match(login):
            accounts[login] = api.user(login).get("created_at")
    outcome = apply_approvals(reg, pending, accounts, now)
    credited = [i for i in applied if credit_merged(api, reg, i)]
    status_mod.sync(list(reg.lines.values()), reg.status, reg.reviewers, status_mod.lint_bad_ids(list(reg.lines.values())))
    write_status(reg.status, STATUS_PATH)
    REVIEWERS_PATH.write_text(json.dumps(reg.reviewers, ensure_ascii=False, indent=1, sort_keys=False) + "\n", encoding="utf-8")
    credit_files = credits.write_outputs(reg.reviewers, reg.status)
    if snapshot == json.dumps([reg.status, reg.reviewers], sort_keys=True):
        for issue in credited:
            api.add_labels(issue["number"], ["credited"])
        return "nothing to write"
    day = now.date().isoformat()
    branch = f"korean-review/batch-{day}"
    counted = sum(o["counted"] for o in outcome.values())
    candidates = trust_candidates(reg.reviewers)
    body = (f"Daily review batch for {day}.\n\n- approvals written: {counted} (from {len(outcome)} issue(s))\n"
            f"- merged corrections credited: {len(credited)}\n"
            + (f"- eligible to become trusted, for the maintainer to confirm in `reviewers.json`: "
               + ", ".join(f"@{c}" for c in candidates) + "\n" if candidates else "")
            + "\nThe bot checked each approval again against the current text: account age (30 days), "
              "no approval of one's own line, one approval per account.")
    commit_and_push(branch, f"content: Korean review batch {day}\n\n{counted} approvals, {len(credited)} credits.\n",
                    [STATUS_PATH, REVIEWERS_PATH, *credit_files])
    pr = open_pr(api, branch, f"content: Korean review batch {day}", body)
    for issue in pending:
        number = issue["number"]
        info = outcome.get(number, {"counted": 0, "skipped": 0})
        api.add_labels(number, ["approval:counted"])
        api.remove_label(number, "approval:pending")
        api.comment(number, f"Counted in #{pr['number']}: {info['counted']} approval(s), {info['skipped']} skipped. Thank you!")
        api.request("PATCH", f"/repos/{api.repo}/issues/{number}", {"state": "closed", "state_reason": "completed"})
    for issue in credited:
        api.add_labels(issue["number"], ["credited"])
    return f"pr {pr['number']}"


# --------------------------------------------------------------------------- labels and entry point

def ensure_labels(api: GitHub) -> int:
    existing = {l["name"] for l in api.pages(f"/repos/{api.repo}/labels")}
    made = 0
    for label in read_json(LABELS_PATH)["labels"]:
        if label["name"] not in existing:
            api.request("POST", f"/repos/{api.repo}/labels", label)
            made += 1
    return made


def main(argv: list[str]) -> int:
    if len(argv) != 1 or argv[0] not in ("triage", "accept", "daily", "ensure-labels"):
        print(__doc__)
        return 2
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if not re.fullmatch(r"[A-Za-z0-9_.\-]+/[A-Za-z0-9_.\-]+", repo):
        print("GITHUB_REPOSITORY is not set", file=sys.stderr)
        return 2
    api = GitHub(repo, os.environ.get("GITHUB_TOKEN", ""))
    command = argv[0]
    if command == "ensure-labels":
        print(f"created {ensure_labels(api)} label(s)")
        return 0
    if command == "daily":
        print(daily(api))
        return 0
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    issue = api.request("GET", f"/repos/{repo}/issues/{int(event['issue']['number'])}")  # fresh, not the payload
    if not issue:
        print("issue not found")
        return 1
    sender = event.get("sender", {}).get("login", "")
    action, label = event.get("action"), (event.get("label") or {}).get("name", "")
    if command == "triage":
        if action == "labeled" and not (label == "recheck" and api.permission(sender) in WRITE_PERMISSIONS):
            print("ignored")
            return 0
        result = triage(api, issue, load_registry())
        print("triaged" if result else "not a review issue")
        return 0
    if command == "accept":
        if action != "labeled" or label != "accepted":
            print("ignored")
            return 0
        print(accept(api, issue, sender, load_registry()))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
