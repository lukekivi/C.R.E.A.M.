---
id: CREAM-012
title: Track style-review findings and propose fixes for recurring ones
status: in-progress
created: 2026-10-26
depends-on: [CREAM-011]
---

Catching the same violation in review over and over is a symptom of one of four things:
- the convention is only evident from code, so it should be written down
- it's mechanical, and lint should enforce it
- Claude isn't following it when it writes code, so a skill or CLAUDE.md is missing guidance
- its documentation is unclear and needs better wording

This ticket tracks what the reviewer catches so the top offenders get fixed at the source.

## Scope

- **Record:** `ci.py` commits each run to `runs/<branch>/<timestamp>.json` on the `style-review-ledger` branch. Each file holds the findings, the notes, and the count of commits with a Claude co-author trailer.
  - **Why not the PR branch:** a bot commit on the PR branch would be lost or rejected on the next force-push.
  - **Why one file per run:** concurrent PRs never conflict.
  - **Local runs aren't recorded:** they would double-count work that CI records anyway.
- **Report:** `scripts/style-review/report.py` merges each PR's runs, so a finding that survives several pushes counts once. It groups findings by convention and source, ranks the groups by the number of PRs they were caught in, and flags groups caught in at least `--threshold` PRs within `--days` as recurring. For each recurring group it suggests one fix:
  - Document, when the source is `codebase`
  - Lint, for mechanical conventions
  - Skill, when mostly caught in Claude co-authored PRs
  - Clarify the source, otherwise

  It also lists reviewer notes as signs of ambiguity.
- **Docs:** the loop is described in `docs/style-review.md`.

## Remaining

- Collect real data, run the first report, and file tickets for what it surfaces. Then set `done`.

## Notes

- Conventions inferred from code have free-form names, so the same one can be tallied under several names. If that fragments the report, give the reviewer the names already in the ledger to reuse.
- Only 8 of the last 30 commits on `main` carry a Claude co-author trailer. The Skill-vs-STYLE.md split is only as reliable as those trailers.
- Not yet tracked: what happened to each finding (suggestion applied, resolved, or disputed). Disputed findings would show where the reviewer is wrong. Add this if the notes alone prove too weak a signal.
- Known first candidate: the `cream-viewmodel` skill's template breaks `VM-CONSTRUCTOR`, `EXPRESSION-BODY`, and `KDOC-REQUIRED`.
