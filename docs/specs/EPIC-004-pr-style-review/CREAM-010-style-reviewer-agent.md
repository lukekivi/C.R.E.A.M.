---
id: CREAM-010
title: Add the style-reviewer agent and its evals
status: done
created: 2026-10-26
---

A project subagent that reviews a diff for style and consistency. It infers conventions at runtime from the repo's docs, relevant skills, and similar existing code, and keeps no rules of its own, so a new STYLE.md rule or skill is enforced without editing the agent. Its output varies between runs and its rubric (STYLE.md) changes over time, so checked-in evals measure recall and false positives rather than relying on spot checks.

## Scope

### Agent

- **`.claude/agents/style-reviewer.md`:**
  - Tools: Read, Grep, Glob, Bash.
  - Read-only: it returns findings and never posts, edits, or commits.
  - Subagent `tools` only accepts bare tool names, so the body instructs read-only Bash and callers narrow it with `--allowedTools`.
- **Categorization:** each finding is filed under a category. The caller passes the known ones; the reviewer reuses one that fits or defines a new one with an id, a general description, its `source` (the doc or skill that states the convention, or `codebase` when only existing code shows it), and whether it's mechanical. CREAM-012 stores and tallies them.
- **Output:** kept out of the agent, in `scripts/style-review/findings.schema.json`. The scripts pass it to `claude --json-schema`; the agent lists `StructuredOutput` in `tools` so the CLI can return it. JSON `findings` ordered by importance, each with `category`, `file`, `line`, optional `endLine`, `message`, and optional whole-line `suggestion`; `newCategories`; and `notes`. Notes are limited to conflicting or unclear conventions, since CREAM-012 tallies them.
- **Diff as a parameter:** the caller passes the diff command to review, built by `diff_command`, which excludes docs of any kind, tickets, ADRs, Claude configuration, eval fixtures, and generated or lock files.

### Evals

- **Cases:** `evals/style-reviewer/cases/<case>/` holds `files/`, laid over the repo, and an `expected.json`.
  - `expected`: findings the reviewer must report.
  - `allowed`: judgment calls it may report.
  - Each entry is `{what, file, lines}`, plus `source` to also require the convention's source. Categories aren't matched, since the reviewer names them freely; `what` only labels the entry.
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

- Built directly in a session. It was not recon'd or planned.
- Early runs gave off-by-one line numbers, partial-line suggestions, and knock-on findings for renamed symbols. The current prompt covers all three, and the evals guard against regressions.
- Baseline (3 runs per case, after the reviewer moved to inferring conventions from docs, skills, and code): all four cases pass with no false positives. A first draft dropped to 80% recall because the reviewer folded several conventions on one line into one finding; the output contract now asks for one finding per convention. A step-by-step procedure in the prompt made no measurable difference and was removed.
  - The companion object's missing KDoc is still missed about 1 run in 3. The reviewer may be reading `object` in the rule as excluding companion objects; making that explicit in STYLE.md would settle it.
  - `kdoc-semantics` allows a missing-factory finding: its fixture Impl has no factory function.
  - The impl import in `viewmodel-violations` breaks the factory rule and the `:app`-depends-only-on-`:data` rule at once; only one is required.
  - Reading skills surfaced a real gap: neither VM fixture had the companion `factory` the `cream-viewmodel` skill requires. The clean fixture now has one; for the violations fixture it's allowed. Eval entries can require a `source`.
