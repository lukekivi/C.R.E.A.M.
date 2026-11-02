---
id: EPIC-004
title: Automated style review on every PR
status: in-progress
created: 2026-10-26
---

Every PR gets an automatic Claude review focused only on style and consistency, with conventions inferred from the repo's docs, skills, and existing code. Each finding is filed under a category in a Firestore database, so categories that keep recurring get fixed at the source (a lint rule, a skill, or documentation) rather than caught again and again in review.

Correctness, design, and security review are not this reviewer's job.

## Scope

- `.claude/agents/style-reviewer.md` — the reviewer, runnable locally and in CI.
- `.github/workflows/` — the PR-triggered workflow.
- The category database and the report that turns recurring categories into remediation proposals.
- A small pointer in AGENTS.md.

See [docs/style-review.md](../../style-review.md) for how the pieces fit together.

## Out of scope

- Writing ktlint/detekt rules or new skills. The report proposes them; each one becomes its own ticket.

## Done when

- Opening a PR produces inline style comments and a summary comment without any manual step.
- At most the 5 most important findings are commented inline, each naming its category and where the convention comes from.
- A report can be generated listing the most frequently caught categories, with a description, examples of the flagged code, and a suggested remediation for each.
