---
id: CREAM-011
title: Run the style reviewer on every PR
status: in-progress
created: 2026-10-26
depends-on: [CREAM-010]
---

The `.github/workflows/style-review.yml` workflow runs `scripts/style-review/ci.py` on every non-draft PR when it's opened, reopened, marked ready, or pushed to.

## Scope

- **Runs the `claude` CLI directly** (`--agent style-reviewer`, with a pinned npm version) instead of `anthropics/claude-code-action`. This avoids depending on whether the action loads project agents, whether `gh` is authenticated inside it, or how its inline-comment MCP tool behaves. Posting is plain Python over `gh api`.
- **Inline comments:**
  - Only the 5 most important findings, as ranked by the reviewer. The summary lists the rest.
  - Format: `**category** message _(source)_`, plus a `suggestion` block when one is given.
  - Each comment carries a hidden key built from the category, the file, and the flagged line's text. A re-review after a push skips findings that are already posted.
  - If a comment can't attach to the diff, the comments are posted one at a time, and any that still fail are listed in the summary.
- **Summary:** one comment covering every finding, updated in place on every run.
- **Skipped paths:** docs of any kind, tickets, ADRs, Claude configuration, eval fixtures, and generated or lock files are left out of the diff.
- **Concurrency:** a new push cancels the in-flight review for that PR.
- **Model:** `claude-sonnet-5-5`.

## Remaining

- Confirm a live run on this epic's own PR, now that federation, the `style-review` database, and the repository variables are set up: inline comments and the summary appear, and the findings land in the category database. Then set `done`.

## Notes

- Verified locally: a `--dry-run` against a throwaway commit range produced correctly anchored comments (multi-line ones and suggestions included) and the summary.
- Verified live, before the category database: the Claude federation and the summary comment. No live run has had findings yet, so inline review posting is unverified.
- Not verified until the next live run: the Google federation and the Firestore writes.
- Advisory only: the review is not a required check.
