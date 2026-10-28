"""Style-reviews a PR in CI: posts findings as review comments and records the run in the ledger.

Reads its inputs from the environment (set by .github/workflows/style-review.yml):
  GITHUB_REPOSITORY, PR_NUMBER, BASE_SHA, HEAD_SHA, HEAD_REF, GH_TOKEN, STYLE_REVIEW_MODEL (optional)

On every run:
  1. Reviews `git diff BASE_SHA...HEAD_SHA` with the style-reviewer agent, minus evals/, whose
     fixtures are deliberate violations.
  2. Posts one inline comment per finding that isn't already on the PR. Findings are identified
     by convention, file, and the flagged line's text, so re-reviews after a push don't repeat them.
  3. Creates or updates a single summary comment.
  4. Commits the run to `runs/<branch>/<timestamp>.json` on the ledger branch, which
     scripts/style-review/report.py tallies.

--dry-run prints what would be posted and recorded instead.
"""

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter
from functools import cache

import ledger
from agent import REPO_ROOT, REQUIRED_KEYS, line_range, run_review, sh, span, temp_worktree

SUMMARY_MARKER = "<!-- style-review-summary -->"
FINDING_MARKER = re.compile(r"<!-- style-review:([0-9a-f]{12}) -->")
CLAUDE_TRAILER = re.compile(r"^co-authored-by:.*(claude|anthropic)", re.I | re.M)
BOT_IDENTITY = {
    "GIT_AUTHOR_NAME": "github-actions[bot]",
    "GIT_AUTHOR_EMAIL": "41898282+github-actions[bot]@users.noreply.github.com",
    "GIT_COMMITTER_NAME": "github-actions[bot]",
    "GIT_COMMITTER_EMAIL": "41898282+github-actions[bot]@users.noreply.github.com",
}


def gh_get(path: str) -> list:
    separator = "&" if "?" in path else "?"
    return json.loads(sh("gh", "api", f"{path}{separator}per_page=100", "--paginate").stdout)


def gh_send(path: str, method: str, body: dict) -> bool:
    return sh("gh", "api", path, "--method", method, "--input", "-", input=json.dumps(body), check=False).returncode == 0


@cache
def stripped_lines(file: str) -> list[str]:
    try:
        return [line.strip() for line in (REPO_ROOT / file).read_text().splitlines()]
    except FileNotFoundError:
        return []


def finding_key(finding: dict) -> str:
    """Identifies a finding across pushes by convention, file, and the flagged line's text.

    Line numbers shift between pushes, so they can't be the key. Identical lines (e.g. `}`) are
    told apart by how many identical lines come before them.
    """
    lines = stripped_lines(finding["file"])
    index = finding["line"] - 1
    text = lines[index] if 0 <= index < len(lines) else ""
    occurrence = lines[:index].count(text)
    return hashlib.sha1(f"{finding['convention']}|{finding['file']}|{text}|{occurrence}".encode()).hexdigest()[:12]


def comment_body(finding: dict, key: str) -> str:
    body = f"**[{finding['convention']}]** {finding['message']} _({finding['source']})_"
    if "suggestion" in finding:
        body += f"\n\n```suggestion\n{finding['suggestion']}\n```"
    return f"{body}\n\n<!-- style-review:{key} -->"


def review_comment(finding: dict, key: str) -> dict:
    start, end = span(finding)
    comment = {"path": finding["file"], "line": end, "side": "RIGHT", "body": comment_body(finding, key)}
    if end != start:
        comment |= {"start_line": start, "start_side": "RIGHT"}
    return comment


def post_findings(repo: str, pr: str, head: str, findings: list[dict], dry_run: bool) -> tuple[int, list[dict]]:
    """Posts findings not already on the PR. Returns (number posted, findings that couldn't be anchored)."""
    seen = set()
    if not dry_run:
        for comment in gh_get(f"repos/{repo}/pulls/{pr}/comments"):
            seen.update(FINDING_MARKER.findall(comment["body"]))
    fresh = []
    for finding in findings:
        key = finding_key(finding)
        if key not in seen:
            seen.add(key)
            fresh.append((finding, review_comment(finding, key)))
    if not fresh:
        return 0, []
    if dry_run:
        print(json.dumps([comment for _, comment in fresh], indent=2))
        return len(fresh), []

    def post(comments: list[dict]) -> bool:
        review = {"commit_id": head, "event": "COMMENT", "comments": comments}
        return gh_send(f"repos/{repo}/pulls/{pr}/reviews", "POST", review)

    if post([comment for _, comment in fresh]):
        return len(fresh), []

    # A comment outside the diff rejects the whole review; retry one at a time to find which.
    unanchored = [finding for finding, comment in fresh if not post([comment])]
    return len(fresh) - len(unanchored), unanchored


def summary_body(review: dict, posted: int, unanchored: list[dict], head: str) -> str:
    findings = review["findings"]
    lines = [SUMMARY_MARKER, f"### Style review of `{head[:7]}`", ""]
    if not findings:
        lines.append("No style issues found.")
    else:
        counts = Counter((finding["convention"], finding["source"]) for finding in findings)
        lines.append(f"{len(findings)} finding(s); {posted} new comment(s) posted this run.")
        lines += ["", "| Convention | Source | Count |", "|---|---|---|"]
        lines += [f"| `{convention}` | {source} | {count} |" for (convention, source), count in counts.most_common()]
    if unanchored:
        lines += ["", "Couldn't be attached to the diff:"]
        lines += [f"- `{f['file']}:{line_range(f)}` **[{f['convention']}]** {f['message']}" for f in unanchored]
    if review["notes"]:
        lines += ["", "Reviewer notes (conflicting or unclear conventions):"]
        lines += [f"- {note}" for note in review["notes"]]
    return "\n".join(lines)


def upsert_summary(repo: str, pr: str, body: str, dry_run: bool) -> None:
    if dry_run:
        print(body)
        return
    for comment in gh_get(f"repos/{repo}/issues/{pr}/comments"):
        if comment["user"]["type"] == "Bot" and SUMMARY_MARKER in comment["body"]:
            sent = gh_send(f"repos/{repo}/issues/comments/{comment['id']}", "PATCH", {"body": body})
            break
    else:
        sent = gh_send(f"repos/{repo}/issues/{pr}/comments", "POST", {"body": body})
    if not sent:
        raise RuntimeError("Couldn't post the summary comment")


def commit_counts(base: str, head: str) -> dict:
    messages = sh("git", "log", "--format=%B%x00", f"{base}..{head}").stdout.split("\0")
    messages = [message for message in messages if message.strip()]
    return {"commits": len(messages), "claudeCoAuthored": sum(bool(CLAUDE_TRAILER.search(m)) for m in messages)}


def record_run(run: dict, branch: str, dry_run: bool) -> None:
    path = ledger.run_path(branch, run["reviewedAt"])
    if dry_run:
        print(f"would record {ledger.LEDGER_BRANCH}:{path}\n{json.dumps(run, indent=2)}")
        return

    branch_ref = f"refs/heads/{ledger.LEDGER_BRANCH}"
    has_branch = sh("git", "fetch", "origin", branch_ref, check=False).returncode == 0
    env = os.environ | BOT_IDENTITY
    orphan = f"style-ledger-orphan-{os.getpid()}"
    try:
        with temp_worktree("style-ledger-", "FETCH_HEAD" if has_branch else "HEAD") as worktree:
            if not has_branch:
                sh("git", "checkout", "--orphan", orphan, cwd=worktree)
                sh("git", "rm", "-rfq", ".", cwd=worktree)
            target = worktree / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(run, indent=2) + "\n")
            sh("git", "add", path, cwd=worktree)
            sh("git", "commit", "-qm", f"Record style review of {branch} at {run['head'][:7]}", cwd=worktree, env=env)
            for attempt in range(3):
                if attempt:
                    sh("git", "pull", "--rebase", "origin", branch_ref, cwd=worktree, env=env)
                if sh("git", "push", "origin", f"HEAD:{branch_ref}", cwd=worktree, check=False).returncode == 0:
                    return
            raise RuntimeError(f"Couldn't push to {ledger.LEDGER_BRANCH}")
    finally:
        sh("git", "branch", "-D", orphan, check=False)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    repo, pr, base, head, branch = (os.environ[name] for name in ("GITHUB_REPOSITORY", "PR_NUMBER", "BASE_SHA", "HEAD_SHA", "HEAD_REF"))
    model = os.environ.get("STYLE_REVIEW_MODEL") or None

    review = run_review(f"git diff {base}...{head} -- . ':!evals'", model=model)
    posted, unanchored = post_findings(repo, pr, head, review["findings"], args.dry_run)
    upsert_summary(repo, pr, summary_body(review, posted, unanchored, head), args.dry_run)

    run = {
        "schema": 2,
        "reviewedAt": ledger.now(),
        "pr": int(pr),
        "branch": branch,
        "base": base,
        "head": head,
        "model": model,
        "authorship": commit_counts(base, head),
        "findings": [{key: f[key] for key in REQUIRED_KEYS} for f in review["findings"]],
        "notes": review["notes"],
    }
    record_run(run, branch, args.dry_run)
    return 0


if __name__ == "__main__":
    sys.exit(main())
