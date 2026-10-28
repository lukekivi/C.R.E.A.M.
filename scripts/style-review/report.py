"""Tallies recorded style reviews and suggests where to fix recurring findings at the source.

Reads the run files CI commits to the style-review-ledger branch (see ci.py). A PR's runs are
merged, so a finding that survives several pushes counts once. Findings are grouped by convention
and source (the doc or skill stating the convention, or `codebase` when only the code shows it).
A convention is "recurring" once it has been caught in --threshold or more PRs within the window.

The suggested fix is a starting point, not a verdict:
  Document     the convention is only evident from code: write it down in STYLE.md or a skill.
  Lint         the convention is mechanical enough for ktlint/detekt.
  Skill        mostly caught in PRs with Claude co-authored commits: fix the skill or CLAUDE.md
               guidance Claude followed, or add one.
  Clarify      mostly caught in human-written code: the source may need a clearer example.
Reviewer notes are listed separately; repeated notes on a convention point at conflicting or
unclear documentation.

Usage:
    python3 scripts/style-review/report.py --fetch
    python3 scripts/style-review/report.py --days 30 --threshold 2
    python3 scripts/style-review/report.py --dir path/to/runs   # read run files from disk
"""

import argparse
import json
import sys
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

from agent import sh
from ledger import LEDGER_BRANCH, parse_time

LINT_CANDIDATES = {
    "KDOC-REQUIRED",
    "KDOC-MULTILINE",
    "BACKING-PROPERTY",
    "PARAMS-PER-LINE",
    "EXPRESSION-BODY",
    "METHOD-CHAIN",
    "VM-CONSTRUCTOR",
    "COMPANION-LAST",
    "MODULE-FACTORY",
    "MODULE-APP-DATA-ONLY",
}


def load_runs(ref: str, directory: Path | None) -> list[dict]:
    if directory:
        return [json.loads(path.read_text()) for path in sorted(directory.rglob("*.json"))]
    paths = sh("git", "ls-tree", "-r", "--name-only", ref, "runs/").stdout.split()
    return [json.loads(sh("git", "show", f"{ref}:{path}").stdout) for path in paths if path.endswith(".json")]


def suggested_fix(convention: str, source: str, prs: int, claude_prs: int) -> str:
    if source == "codebase":
        return "Document"
    if convention in LINT_CANDIDATES:
        return "Lint"
    if claude_prs * 2 >= prs:
        return "Skill"
    return f"Clarify {source}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--fetch", action="store_true", help=f"fetch origin/{LEDGER_BRANCH} first")
    parser.add_argument("--ref", default=f"origin/{LEDGER_BRANCH}")
    parser.add_argument("--dir", type=Path)
    parser.add_argument("--days", type=int, default=90)
    parser.add_argument("--threshold", type=int, default=3)
    args = parser.parse_args()

    if args.fetch:
        sh("git", "fetch", "origin", f"{LEDGER_BRANCH}:refs/remotes/origin/{LEDGER_BRANCH}")
    since = datetime.now(timezone.utc) - timedelta(days=args.days)
    runs = [
        run for run in load_runs(args.ref, args.dir)
        if parse_time(run["reviewedAt"]) >= since
    ]

    by_pr: dict[str, list[dict]] = defaultdict(list)
    for run in runs:
        by_pr[f"#{run['pr']}" if run.get("pr") else run["branch"]].append(run)

    category_prs: dict[tuple[str, str], set[str]] = defaultdict(set)
    category_sites: dict[tuple[str, str], set[tuple[str, str]]] = defaultdict(set)
    claude_prs: set[str] = set()
    notes: list[tuple[str, str]] = []
    for pr, pr_runs in by_pr.items():
        latest = max(pr_runs, key=lambda run: run["reviewedAt"])
        if latest.get("authorship", {}).get("claudeCoAuthored"):
            claude_prs.add(pr)
        for run in pr_runs:
            for finding in run["findings"]:
                category = (finding["convention"], finding["source"])
                category_prs[category].add(pr)
                category_sites[category].add((pr, finding["file"]))
            notes += [(pr, note) for note in run.get("notes", [])]

    print("# Style review report\n")
    print(f"Last {args.days} days: {len(runs)} run(s) across {len(by_pr)} PR(s), "
          f"{len(claude_prs)} with Claude co-authored commits.\n")

    ranked = sorted(category_prs, key=lambda c: (-len(category_prs[c]), -len(category_sites[c]), c))
    recurring = [category for category in ranked if len(category_prs[category]) >= args.threshold]
    print(f"## Recurring (caught in {args.threshold}+ PRs)\n")
    if recurring:
        print("| Convention | Source | PRs | Files (per PR) | Claude PRs | Suggested fix |\n|---|---|---|---|---|---|")
        for convention, source in recurring:
            prs, sites = category_prs[convention, source], category_sites[convention, source]
            claude = len(prs & claude_prs)
            fix = suggested_fix(convention, source, len(prs), claude)
            print(f"| `{convention}` | {source} | {len(prs)} | {len(sites)} | {claude} | {fix} |")
    else:
        print("None yet.")

    print("\n## All conventions\n")
    if ranked:
        print("| Convention | Source | PRs | Files (per PR) |\n|---|---|---|---|")
        for convention, source in ranked:
            prs, sites = category_prs[convention, source], category_sites[convention, source]
            print(f"| `{convention}` | {source} | {len(prs)} | {len(sites)} |")
    else:
        print("No findings recorded.")

    print("\n## Reviewer notes\n")
    print("\n".join(f"- {pr}: {note}" for pr, note in notes) if notes else "None.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
