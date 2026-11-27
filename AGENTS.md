# AGENTS.md

This file provides guidance to coding agents working in this repository.

## Architecture

Three layers with manual dependency injection (no Hilt/Koin): UI (`:app`) → Data (`:data`, repositories + `AppContainer`) → Datasource (`:remote-datasource`, `:local-datasource`, each exposing one container of datasource interfaces). `:app` depends only on `:data`. Composables own their input types; see [ADR 0003](docs/decisions/0003-composables-own-their-data.md).

Read [docs/architecture.md](docs/architecture.md) before adding a module, repository, datasource, or ViewModel. The `cream-viewmodel` skill has the ViewModel factory template.

## Code style

See [STYLE.md](STYLE.md) for code conventions (documentation, formatting, module boundaries). Follow it for every change. Every PR is checked against it by an automated style reviewer; see [docs/style-review.md](docs/style-review.md).

## Work tracking

Epics and tickets live in `docs/specs/` as committed markdown files. See [docs/work-tracking.md](docs/work-tracking.md) for format and lifecycle. ADRs live in `docs/decisions/`.

## Commits

A commit message is an imperative subject line, a blank line, then a 3-line haiku (5-7-5) as the whole body. Never credit Claude in a commit: no `Co-Authored-By: Claude` trailer and no other mention. The `.githooks/commit-msg` hook strips Claude trailers and rejects any other body shape; enable it once per clone with `git config core.hooksPath .githooks`.

See [docs/contributing.md](docs/contributing.md) for how to scope commits and PRs.
