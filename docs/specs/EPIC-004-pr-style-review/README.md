---
id: EPIC-004
title: Automated style review on every PR
status: in-progress
created: 2026-10-26
---

Every PR gets an automatic Claude review focused only on style and consistency, with conventions inferred from the repo's docs, skills, and existing code. Its findings are tracked over time so that recurring ones get fixed at the source (a lint rule, a skill, or documentation) rather than caught again and again in review.

Correctness, design, and security review are not this reviewer's job.

## Scope

- `.claude/agents/style-reviewer.md` — the reviewer, runnable locally and in CI.
- `.github/workflows/` — the PR-triggered workflow.
- Findings tracking and the periodic report that turns findings into remediation proposals.
- Small pointers in CLAUDE.md and STYLE.md.

See [docs/style-review.md](../../style-review.md) for how the pieces fit together.

## Out of scope

- Writing ktlint/detekt rules or new skills. The report proposes them; each one becomes its own ticket.

## Done when

- Opening a PR produces inline style comments and a summary comment without any manual step.
- Every comment names the convention it enforces and where that convention comes from.
- A report can be generated listing the most frequently caught conventions, with a suggested remediation for each.
