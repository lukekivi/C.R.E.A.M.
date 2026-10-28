---
id: CREAM-010
title: Add the style-reviewer agent
status: done
created: 2026-10-26
---

A project subagent that reviews a diff for style and consistency. It infers conventions at runtime from the repo's docs, relevant skills, and similar existing code, and keeps no rules of its own, so a new STYLE.md rule or skill is enforced without editing the agent.

## Scope

- **`.claude/agents/style-reviewer.md`:**
  - Tools: Read, Grep, Glob, Bash.
  - Read-only: it returns findings and never posts, edits, or commits.
  - Subagent `tools` only accepts bare tool names, so the body instructs read-only Bash and callers narrow it with `--allowedTools`.
- **Categorization:** each finding names a `convention` and its `source`: the doc or skill that states it, or `codebase` when only existing code shows it. Documented conventions use the source's own label, e.g. a STYLE.md rule ID such as `KDOC-MULTILINE`, which are permanent. CREAM-012 tallies findings by these two fields.
- **Output:** kept out of the agent, in `scripts/style-review/findings.schema.json`. The scripts pass it to `claude --json-schema`; the agent lists `StructuredOutput` in `tools` so the CLI can return it. JSON `findings` with `convention`, `source`, `file`, `line`, optional `endLine`, `message`, and optional whole-line `suggestion`, plus `notes`. Notes are limited to conflicting or unclear conventions, since CREAM-012 tallies them.
- **Diff as a parameter:** the caller passes the diff command to review. CI excludes `evals/`, whose fixtures are deliberate violations.

## Notes

- Built directly in a session, before the kivi-sdlc rule was added to CLAUDE.md. It was not recon'd or planned.
- Early runs gave off-by-one line numbers, partial-line suggestions, and knock-on findings for renamed symbols. The current prompt covers all three. The CREAM-013 evals guard against regressions.
