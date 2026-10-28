---
id: CREAM-012
title: Build the style-review category database
status: in-progress
created: 2026-10-26
depends-on: [CREAM-011]
---

Catching the same kind of issue in review over and over is a symptom of one of four things:
- the convention is only evident from code, so it should be written down
- it's mechanical, and lint should enforce it
- a skill isn't steering code toward its own convention
- its documentation is unclear and needs better wording

This ticket gives every finding a category and keeps the categories in a database, so the top offenders can be understood well enough to fix at the source.

## Scope

- **Categories:** each has a kebab-case id, a description of the issue general enough to recur across PRs, its source (the doc or skill stating the convention, or `codebase`), and whether it's mechanical. The reviewer is given the known categories and reuses one that fits, or defines a new one. A category is created the first time it's used and never overwritten.
- **Database:** Firestore database `style-review` in `cream-5eeda`, accessed through `scripts/style-review/store.py`:
  - `categories/{id}` holds the definition.
  - `categories/{id}/occurrences/{key}` holds each finding: PR, commit, file, lines, message, and the flagged code. The key comes from the PR and the flagged code, so a finding that survives several pushes counts once.
  - `prs/{number}` holds the reviewer's notes.
  - Counts are computed from occurrences, never stored, so concurrent PRs can't make them drift.
  - **Why Firestore, not git:** an earlier version committed run files to a `style-review-ledger` branch. A curated category list on top of it would have needed a sync step between two copies of the data. Firestore gives one copy with conflict-free concurrent writes.
- **Report:** `scripts/style-review/report.py` ranks categories by the number of PRs they were caught in, flags those caught in at least `--threshold` PRs within `--days` as recurring, and prints each recurring one with its description, recent flagged code, and one suggested fix:
  - Document, when the source is `codebase`
  - Lint, for mechanical categories
  - Fix skill, when the source is a skill
  - Clarify the source, otherwise

  It also lists reviewer notes as signs of ambiguity.
- **Docs:** the loop is described in `docs/style-review.md`.

## Remaining

- Collect real data, run the first report, and file tickets for what it surfaces. Then set `done`.

## Notes

- Verified against the Firestore emulator: categories are created once and not overwritten by a later definition, re-reviewing a PR doesn't double-count, an undefined category gets a stand-in, and the report ranks and prints examples.
- Categories may still fragment if the reviewer defines a near-duplicate instead of reusing one. They can be merged by hand in the Firebase console; if it becomes common, add a merge script.
- Not yet tracked: what happened to each finding (suggestion applied, resolved, or disputed). Disputed findings would show where the reviewer is wrong. Add this if the notes alone prove too weak a signal.
- Known first candidate: the `cream-viewmodel` skill's template breaks the ViewModel constructor, expression-body, and KDoc conventions.
