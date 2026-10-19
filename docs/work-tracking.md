# Work tracking

Work is tracked as epics and tickets committed to the repo in [`docs/specs/`](specs/). Each epic is a directory; its tickets live inside it.

```
docs/specs/
  EPIC-NNN-kebab-slug/
    README.md                  # the epic
    CREAM-NNN-kebab-slug.md    # one file per ticket
```

Cross-cutting design decisions are a separate thing — they go in [`docs/decisions/`](decisions/) as ADRs. An epic or ticket may cite an ADR; it never replaces one.

## Epics

An epic is a siloed piece of work that can be handed to one developer or one agent workflow and finished with little coordination with other in-flight epics. Use them liberally: a feature is usually several epics, and a refactor or cleanup effort is an epic of its own.

A good epic:

- Has a single goal that can be stated in a sentence.
- Mostly owns its boundaries — the modules or files it touches overlap little with another epic that could run at the same time. If two epics would heavily touch the same code, sequence them with `depends-on` or merge them.
- Has a concrete "done when" that someone outside the work can check.
- Carries everything needed to start: context, constraints, and links to the ADRs and code it builds on.

The epic's directory is named `EPIC-NNN-kebab-slug/` and its `README.md` describes it. Numbers are sequential; never reuse one.

```markdown
---
id: EPIC-NNN
title: Short imperative title
status: todo              # todo | in-progress | done | ongoing
created: YYYY-MM-DD
depends-on: [EPIC-NNN]    # optional
---

Goal and why.

## Scope

Modules and files this epic owns.

## Out of scope

## Done when

- Checkable outcomes.
```

### Catch-all epics

Everything belongs to an epic. Work that doesn't fit a goal-driven epic goes in a long-lived catch-all, such as Platform (build, tooling, infrastructure) or Dependency updates. A catch-all has `status: ongoing`, no "Done when", and is never closed. Add a new catch-all only when a recurring kind of work has no home.

## Tickets

A ticket is one PR-sized step inside an epic. Every ticket belongs to exactly one epic: the directory it lives in. Moving a ticket to another epic means moving its file.

Named `CREAM-NNN-kebab-slug.md`. Numbers are sequential across all epics; never reuse one.

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

Each ticket should be self-contained.

## Lifecycle

Epics (other than catch-alls) and tickets share one lifecycle:

1. **Create.** Write the file with `status: todo`. Commit it.
2. **Start.** Set `status: in-progress`.
3. **Finish.** Set `status: done` in the same commit (or PR) as the work. An epic is done in the PR that finishes its last ticket.

Work spanning several PRs is split into multiple tickets linked with `depends-on`.
