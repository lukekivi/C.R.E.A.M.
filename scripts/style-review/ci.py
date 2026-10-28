"""Style-reviews a PR in CI: posts the top findings as review comments and records every finding.

Reads its inputs from the environment (set by .github/workflows/style-review.yml):
  GITHUB_REPOSITORY, PR_NUMBER, BASE_SHA, HEAD_SHA, HEAD_REF, GH_TOKEN, STYLE_REVIEW_MODEL (optional),
  plus Google Application Default Credentials for the category database (see store.py).

On every run:
  1. Reviews `git diff BASE_SHA...HEAD_SHA`, minus agent.EXCLUDED_PATHS, with the style-reviewer
     agent, giving it the known categories to reuse.
  2. Posts the MAX_COMMENTS most important findings as inline comments, skipping any already on
     the PR. Findings are identified by category, file, and the flagged line's text, so re-reviews
     after a push don't repeat them.
  3. Creates or updates a single summary comment covering every finding.
  4. Records every finding in the category database, which scripts/style-review/report.py tallies.

--dry-run prints what would be posted and recorded instead.
"""

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from functools import cache

import store
from agent import REPO_ROOT, categories_by_id, diff_command, line_range, run_review, sh, span

MAX_COMMENTS = 5
SUMMARY_MARKER = "<!-- style-review-summary -->"
FINDING_MARKER = re.compile(r"<!-- style-review:([0-9a-f]{12}) -->")


def gh_get(path: str) -> list:
    separator = "&" if "?" in path else "?"
    return json.loads(sh("gh", "api", f"{path}{separator}per_page=100", "--paginate").stdout)


def gh_send(path: str, method: str, body: dict) -> bool:
    return sh("gh", "api", path, "--method", method, "--input", "-", input=json.dumps(body), check=False).returncode == 0


@cache
def file_lines(file: str) -> list[str]:
    try:
        return (REPO_ROOT / file).read_text().splitlines()
    except FileNotFoundError:
        return []


def flagged_code(finding: dict) -> str:
    start, end = span(finding)
    return "\n".join(file_lines(finding["file"])[start - 1:end])


def finding_key(finding: dict) -> str:
    """Identifies a finding across pushes by category, file, and the flagged line's text.

    Line numbers shift between pushes, so they can't be the key. Identical lines (e.g. `}`) are
    told apart by how many identical lines come before them.
    """
    lines = [line.strip() for line in file_lines(finding["file"])]
    index = finding["line"] - 1
    text = lines[index] if 0 <= index < len(lines) else ""
    occurrence = lines[:index].count(text)
    return hashlib.sha1(f"{finding['category']}|{finding['file']}|{text}|{occurrence}".encode()).hexdigest()[:12]


def comment_body(finding: dict, category: dict, key: str) -> str:
    body = f"**{finding['category']}** {finding['message']} _({category['source']})_"
    if "suggestion" in finding:
        body += f"\n\n```suggestion\n{finding['suggestion']}\n```"
    return f"{body}\n\n<!-- style-review:{key} -->"


def review_comment(finding: dict, category: dict, key: str) -> dict:
    start, end = span(finding)
    comment = {"path": finding["file"], "line": end, "side": "RIGHT", "body": comment_body(finding, category, key)}
    if end != start:
        comment |= {"start_line": start, "start_side": "RIGHT"}
    return comment


def post_findings(
    repo: str,
    pr: str,
    head: str,
    findings: list[dict],
    categories: dict[str, dict],
    dry_run: bool,
) -> tuple[int, list[dict]]:
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
            fresh.append((finding, review_comment(finding, categories[finding["category"]], key)))
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


def summary_body(
    review: dict,
    categories: dict[str, dict],
    posted: int,
    unanchored: list[dict],
    head: str,
) -> str:
    findings = review["findings"]
    lines = [SUMMARY_MARKER, f"### Style review of `{head[:7]}`", ""]
    if not findings:
        lines.append("No style issues found.")
    else:
        counts = Counter(finding["category"] for finding in findings)
        lines.append(
            f"{len(findings)} finding(s). The {min(len(findings), MAX_COMMENTS)} most important are commented inline; "
            f"{posted} new comment(s) posted this run."
        )
        lines += ["", "| Category | Source | Count |", "|---|---|---|"]
        lines += [f"| `{category}` | {categories[category]['source']} | {count} |" for category, count in counts.most_common()]
        rest = findings[MAX_COMMENTS:]
        if rest:
            lines += ["", "Not commented inline:"]
            lines += [f"- `{f['file']}:{line_range(f)}` **{f['category']}** {f['message']}" for f in rest]
    if unanchored:
        lines += ["", "Couldn't be attached to the diff:"]
        lines += [f"- `{f['file']}:{line_range(f)}` **{f['category']}** {f['message']}" for f in unanchored]
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


def record_run(run: dict, findings: list[dict], categories: dict[str, dict], dry_run: bool) -> None:
    occurrences = {
        f"{run['pr']}-{finding_key(finding)}": {
            "category": finding["category"],
            "pr": run["pr"],
            "head": run["head"],
            "file": finding["file"],
            "line": finding["line"],
            "endLine": span(finding)[1],
            "message": finding["message"],
            "code": flagged_code(finding),
        }
        for finding in findings
    }
    if dry_run:
        print(f"would record in {store.DATABASE}:\n{json.dumps(occurrences, indent=2)}")
        return
    store.record(store.client(), run, categories, occurrences)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    repo, pr, base, head, branch = (os.environ[name] for name in ("GITHUB_REPOSITORY", "PR_NUMBER", "BASE_SHA", "HEAD_SHA", "HEAD_REF"))
    model = os.environ.get("STYLE_REVIEW_MODEL") or None

    known = store.try_load_categories() if args.dry_run else store.load_categories(store.client())
    review = run_review(diff_command(f"{base}...{head}"), known, model=model)
    categories = categories_by_id(review, known)
    posted, unanchored = post_findings(repo, pr, head, review["findings"][:MAX_COMMENTS], categories, args.dry_run)
    upsert_summary(repo, pr, summary_body(review, categories, posted, unanchored, head), args.dry_run)

    run = {
        "reviewedAt": datetime.now(timezone.utc),
        "pr": int(pr),
        "branch": branch,
        "head": head,
        "notes": review["notes"],
    }
    record_run(run, review["findings"], categories, args.dry_run)
    return 0


if __name__ == "__main__":
    sys.exit(main())
