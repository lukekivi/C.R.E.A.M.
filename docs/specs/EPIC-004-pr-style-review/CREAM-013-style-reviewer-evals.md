---
id: CREAM-013
title: Check in evals for the style reviewer
status: done
created: 2026-10-27
depends-on: [CREAM-010]
---

The reviewer's output varies between runs, and its rubric (STYLE.md) changes over time, so recall and false positives need measuring rather than spot-checking.

## Scope

- **Cases:** `evals/style-reviewer/cases/<case>/` holds `files/`, laid over the repo, and an `expected.json`.
  - `expected`: findings the reviewer must report.
  - `allowed`: judgment calls it may report.
  - Each entry is `{convention, file, lines}`, or `{source, file, lines}` for conventions the reviewer names freely.
- **Starting cases:**
  - `viewmodel-violations`: 13 planted violations
  - `viewmodel-clean`: a false-positive check
  - `test-structure`: covers the test rules and the KDoc exemption for tests
  - `kdoc-semantics`: covers vendor names, contract versus implementation, and the KDoc exemption for overrides
- **`scripts/style-review/eval.py`:**
  - Runs each case N times, each in a throwaway worktree.
  - Uses the working-tree docs, skills, and agent, so uncommitted rubric changes are what get scored.
  - Reports recall, false positives per run, duplicates, and which expected findings were missed.
  - Fails when recall is below `--min-recall` (default 90%) or there are any false positives.
- Evals are local-only: run `eval.py` when changing the rubric, the agent, the evals, or the scripts. A CI workflow was dropped to keep the federation rule to a single workflow (`Style review`) and to avoid about 8 reviewer runs per rubric PR. Restore it if rubric changes become frequent or start coming from more people.

## Notes

- Baseline (3 runs per case, after the reviewer moved to inferring conventions from docs, skills, and code): all four cases pass with no false positives. A first draft dropped to 80% recall because the reviewer folded several conventions on one line into one finding; the output contract now asks for one finding per convention. A step-by-step procedure in the prompt made no measurable difference and was removed.
  - The companion object's missing KDoc (`KDOC-REQUIRED`) is still missed about 1 run in 3. The reviewer may be reading `object` in the rule as excluding companion objects; making that explicit in STYLE.md would settle it.
  - `kdoc-semantics` allows a `MODULE-FACTORY` finding: its fixture Impl has no factory function.
  - The impl import in `viewmodel-violations` breaks `MODULE-FACTORY` and `MODULE-APP-DATA-ONLY` at once; only one is required.
  - Reading skills surfaced a real gap: neither VM fixture had the companion `factory` the `cream-viewmodel` skill requires. The clean fixture now has one; for the violations fixture it's allowed. Eval entries can match on `source` for conventions the reviewer names freely.
- Built directly in a session, not through kivi-sdlc.
