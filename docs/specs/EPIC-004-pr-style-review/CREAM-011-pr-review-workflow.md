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
  - Format: `**[RULE-ID]** message`, plus a `suggestion` block when one is given.
  - Each comment carries a hidden key built from the convention, the file, and the flagged line's text. A re-review after a push skips findings that are already posted.
  - If a comment can't attach to the diff, the comments are posted one at a time, and any that still fail are listed in the summary.
- **Summary:** one comment, updated in place on every run.
- **Concurrency:** a new push cancels the in-flight review for that PR.
- **Model:** `claude-sonnet-5-5`.

## Remaining

- Configure workload identity federation in the Claude Console and set the repository variables. See the Setup section of `docs/style-review.md`. Federation is used instead of an API key secret.
- Confirm the first live run on this epic's own PR: inline comments and the summary appear, and the ledger branch is created. Then set `done`.

## Notes

- Verified locally: a `--dry-run` against a throwaway commit range produced 12 correctly anchored comments (3 multi-line, 7 with suggestions), the summary, and the ledger record.
- Not verified until the first live run: the `gh api` review-posting path and the ledger push.
- Advisory only: the review is not a required check.
