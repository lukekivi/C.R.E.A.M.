"""Style-reviews local changes. Nothing is posted or recorded.

Usage:
    python3 scripts/style-review/review.py                # branch vs main
    python3 scripts/style-review/review.py --base develop
    python3 scripts/style-review/review.py --uncommitted

Exits 1 when there are findings.
"""

import argparse
import sys
import textwrap

from agent import line_range, run_review


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="main")
    parser.add_argument("--uncommitted", action="store_true")
    args = parser.parse_args()

    review = run_review("git diff HEAD" if args.uncommitted else f"git diff {args.base}...HEAD")

    for finding in review["findings"]:
        print(f"{finding['file']}:{line_range(finding)}  [{finding['convention']} ({finding['source']})]  {finding['message']}")
        if "suggestion" in finding:
            print(textwrap.indent(finding["suggestion"], "    | "))
    for note in review["notes"]:
        print(f"note: {note}")
    if not review["findings"]:
        print("No style issues found.")
    return 1 if review["findings"] else 0


if __name__ == "__main__":
    sys.exit(main())
