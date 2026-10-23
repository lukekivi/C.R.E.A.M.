# Work tracking

Work is tracked as tickets committed to the repo, one markdown file per ticket in [`docs/specs/`](specs/).

Cross-cutting design decisions are a separate thing — they go in [`docs/decisions/`](decisions/) as ADRs. A ticket may cite an ADR; it never replaces one.

## Ticket files

Named `CREAM-NNN-kebab-slug.md`. Numbers are sequential; never reuse one.

```markdown
---
id: CREAM-NNN
title: Short imperative title
status: todo              # todo | in-progress | done
created: YYYY-MM-DD
depends-on: [CREAM-NNN]   # optional
---

What and why, scope, out of scope, notes.
```

Each ticket should be self-contained. Don't link to gitignored files.

## Lifecycle

1. **Create.** Write the file with `status: todo`. Commit it.
2. **Start.** Set `status: in-progress`.
3. **Finish.** Set `status: done` in the same commit (or PR) as the work.

Work spanning several PRs is split into multiple tickets linked with `depends-on`.

## Finding open tickets

```sh
grep -L '^status: done' docs/specs/CREAM-*.md
```
