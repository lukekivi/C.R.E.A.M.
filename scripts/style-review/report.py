"""Ranks the style-review categories and suggests where to fix recurring ones at the source.

Reads the category database ci.py writes (see store.py). Each category is counted by the PRs it
was caught in within the window and by its occurrences. A category is "recurring" once it has
been caught in --threshold or more PRs. Recurring categories are printed with their description
and recent examples of the flagged code, which is what a lint rule or doc fix is written from.

The suggested fix is a starting point, not a verdict:
  Document     the convention is only evident from code: write it down in STYLE.md or a skill.
  Lint         the issue is mechanical enough for ktlint/detekt.
  Fix skill    the convention comes from a skill that isn't steering code toward it.
  Clarify      the convention is documented but still broken: the doc may need a clearer example.
Reviewer notes are listed separately; repeated notes on a convention point at conflicting or
unclear documentation.

Usage:
    python3 scripts/style-review/report.py
    python3 scripts/style-review/report.py --days 30 --threshold 2 --examples 5
"""

import argparse
import sys
from datetime import datetime, timedelta, timezone

import store
from google.cloud.firestore import FieldFilter


def suggested_fix(category: dict) -> str:
    if category["source"] == "codebase":
        return "Document"
    if category["mechanical"]:
        return "Lint"
    if category["source"].startswith(".claude/skills/"):
        return f"Fix skill {category['source']}"
    return f"Clarify {category['source']}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--days", type=int, default=90)
    parser.add_argument("--threshold", type=int, default=3)
    parser.add_argument("--examples", type=int, default=3)
    args = parser.parse_args()

    db = store.client()
    since = datetime.now(timezone.utc) - timedelta(days=args.days)
    rows = []
    for category in store.load_categories(db):
        occurrences = [
            doc.to_dict()
            for doc in db.collection("categories").document(category["id"]).collection("occurrences")
            .where(filter=FieldFilter("reviewedAt", ">=", since))
            .stream()
        ]
        if occurrences:
            occurrences.sort(key=lambda occurrence: occurrence["reviewedAt"], reverse=True)
            rows.append((category, {occurrence["pr"] for occurrence in occurrences}, occurrences))
    rows.sort(key=lambda row: (-len(row[1]), -len(row[2]), row[0]["id"]))
    notes = [
        (doc.id, note)
        for doc in db.collection("prs").where(filter=FieldFilter("lastReviewedAt", ">=", since)).stream()
        for note in doc.to_dict().get("notes", [])
    ]

    print("# Style review report\n")
    print(f"Last {args.days} days: {len(rows)} categories caught.\n")

    recurring = [row for row in rows if len(row[1]) >= args.threshold]
    print(f"## Recurring (caught in {args.threshold}+ PRs)\n")
    if not recurring:
        print("None yet.\n")
    for category, prs, occurrences in recurring:
        print(f"### `{category['id']}`: {suggested_fix(category)}\n")
        print(f"{category['description']}\n")
        print(f"Source: {category['source']}. Caught in {len(prs)} PRs, {len(occurrences)} occurrences.\n")
        for occurrence in occurrences[:args.examples]:
            print(f"#{occurrence['pr']} `{occurrence['file']}:{occurrence['line']}`: {occurrence['message']}")
            print(f"```\n{occurrence['code']}\n```\n")

    print("## All categories\n")
    if rows:
        print("| Category | Source | PRs | Occurrences |\n|---|---|---|---|")
        for category, prs, occurrences in rows:
            print(f"| `{category['id']}` | {category['source']} | {len(prs)} | {len(occurrences)} |")
    else:
        print("No findings recorded.")

    print("\n## Reviewer notes\n")
    print("\n".join(f"- #{pr}: {note}" for pr, note in notes) if notes else "None.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
