"""Scores the style-reviewer agent against the checked-in cases in evals/style-reviewer/cases/.

Each case is `files/` (laid over the repo at the same paths) plus `expected.json`:
  expected  findings the reviewer must report; each one missed lowers recall
  allowed   findings that are acceptable but not required (judgment calls)
Extra findings on an expected entry that's already matched are duplicates: reported, never
failing. Any other finding is a false positive. A finding matches an entry when the file and the
entry's `convention` and/or `source` are equal and its line range overlaps the entry's `lines`,
with one line of slack. Match code- or skill-derived findings on `source`, since the reviewer
names those conventions freely.

The reviewer runs against the working-tree docs, skills, and agent definition, so uncommitted
changes to them are what get scored.

Usage:
    python3 scripts/style-review/eval.py                     # every case, 3 runs each
    python3 scripts/style-review/eval.py --runs 5 test-structure
"""

import argparse
import json
import shutil
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from agent import REPO_ROOT, ReviewError, line_range, run_review, sh, span, temp_worktree

CASES_DIR = REPO_ROOT / "evals" / "style-reviewer" / "cases"
RUBRIC_PATHS = ("STYLE.md", "CLAUDE.md", "docs", ".claude/agents/style-reviewer.md", ".claude/skills")
SLACK = 1


def run_case_once(case_dir: Path, model: str | None) -> dict:
    """Reviews one case in a throwaway worktree and returns the reviewer's output."""
    with temp_worktree(f"style-eval-{case_dir.name}-") as worktree:
        for rubric in RUBRIC_PATHS:
            source, target = REPO_ROOT / rubric, worktree / rubric
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target, dirs_exist_ok=True)
            else:
                shutil.copy2(source, target)
        sh("git", "add", *RUBRIC_PATHS, cwd=worktree)
        sh(
            "git", "-c", "user.name=style-eval", "-c", "user.email=style-eval@localhost", "-c", "commit.gpgsign=false",
            "commit", "--allow-empty", "-m", "eval rubric", cwd=worktree,
        )
        shutil.copytree(case_dir / "files", worktree, dirs_exist_ok=True)
        sh("git", "add", "--intent-to-add", ".", cwd=worktree)
        return run_review("git diff HEAD", cwd=worktree, model=model)


def run_case_safely(case_dir: Path, model: str | None) -> dict | ReviewError:
    try:
        return run_case_once(case_dir, model)
    except ReviewError as error:
        return error
    except Exception as error:
        return ReviewError(f"{type(error).__name__}: {error}")


def overlaps(finding: dict, entry: dict) -> bool:
    if finding["file"] != entry["file"]:
        return False
    if "convention" in entry and finding["convention"] != entry["convention"]:
        return False
    if "source" in entry and finding["source"] != entry["source"]:
        return False
    start, end = span(finding)
    low, high = entry["lines"]
    return start <= high + SLACK and end >= low - SLACK


def score(review: dict, expected: list[dict], allowed: list[dict]) -> tuple[list[bool], list[dict], list[dict]]:
    """Returns (hit flag per expected entry, duplicates, false positives).

    A duplicate is an extra finding for an expected entry that's already been hit, e.g. one
    violation reported on several lines. It's noise, not a misapplied rule.
    """
    hits = [False] * len(expected)
    duplicates, false_positives = [], []
    for finding in review["findings"]:
        match = next((i for i, entry in enumerate(expected) if not hits[i] and overlaps(finding, entry)), None)
        if match is not None:
            hits[match] = True
        elif any(overlaps(finding, entry) for entry in expected):
            duplicates.append(finding)
        elif not any(overlaps(finding, entry) for entry in allowed):
            false_positives.append(finding)
    return hits, duplicates, false_positives


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("cases", nargs="*", help="case names (default: all)")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--model")
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--min-recall", type=float, default=0.9)
    parser.add_argument("--max-false-positives", type=float, default=0.0, help="mean per run, per case")
    args = parser.parse_args()

    case_dirs = [CASES_DIR / name for name in args.cases] if args.cases else sorted(p for p in CASES_DIR.iterdir() if p.is_dir())
    jobs = [(case_dir, run) for case_dir in case_dirs for run in range(args.runs)]
    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        results = list(pool.map(lambda job: run_case_safely(job[0], args.model), jobs))

    failed = False
    total_hits = total_expected = 0
    for case_dir in case_dirs:
        spec = json.loads((case_dir / "expected.json").read_text())
        expected, allowed = spec["expected"], spec.get("allowed", [])
        case_results = [result for (dir_, _), result in zip(jobs, results) if dir_ == case_dir]
        hit_counts = [0] * len(expected)
        duplicates, false_positives = [], []
        errors = 0
        for result in case_results:
            if isinstance(result, ReviewError):
                errors += 1
                continue
            hits, dups, fps = score(result, expected, allowed)
            hit_counts = [count + hit for count, hit in zip(hit_counts, hits)]
            duplicates.extend(dups)
            false_positives.extend(fps)

        scored_runs = len(case_results) - errors
        recall = sum(hit_counts) / (len(expected) * scored_runs) if expected and scored_runs else 1.0
        fp_rate = len(false_positives) / scored_runs if scored_runs else 0.0
        total_hits += sum(hit_counts)
        total_expected += len(expected) * scored_runs
        case_failed = errors > 0 or recall < args.min_recall or fp_rate > args.max_false_positives
        failed |= case_failed

        print(f"{'FAIL' if case_failed else 'pass'}  {case_dir.name}: recall {recall:.0%}, "
              f"false positives {fp_rate:.1f}/run, duplicates {len(duplicates)}, "
              f"{scored_runs}/{len(case_results)} runs scored")
        for entry, count in zip(expected, hit_counts):
            if count < scored_runs:
                print(f"      missed {scored_runs - count}/{scored_runs}  [{entry.get('convention') or entry['source']}] {entry['file']}:{entry['lines']}")
        for label, findings in (("duplicate", duplicates), ("false positive", false_positives)):
            for finding in findings:
                print(f"      {label}  [{finding['convention']} ({finding['source']})] {finding['file']}:{line_range(finding)}  {finding['message']}")
        for result in case_results:
            if isinstance(result, ReviewError):
                print(f"      error  {str(result).splitlines()[0]}")

    if total_expected:
        print(f"\noverall recall {total_hits / total_expected:.0%}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
