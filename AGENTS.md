# AGENTS.md

This file provides guidance to coding agents working in this repository.

## Architecture

Three-layer architecture with manual dependency injection (no Hilt/Koin — dependencies are provided to ViewModels via factories):

- **UI layer** (`ui/`): Jetpack Compose screens and state holders (ViewModels, remembered objects). Adaptive layouts via Compose.
- **Data layer**: Repositories containing business logic. One repository per data type.
- **Datasource layer**: Split into `remote-datasource` (network) and `local-datasource` (storage) modules. Each module owns its interface, implementation, and a factory function returning the interface type. Consumers call the factory — never import impl classes directly. `:data` depends on these modules; `:app` only depends on `:data`.

Data flows up: Datasource → Repository → ViewModel → Composable.

## Code style

See [STYLE.md](STYLE.md) for code conventions (documentation, formatting, module boundaries). Follow it for every change. Every PR is checked against it by an automated style reviewer; see [docs/style-review.md](docs/style-review.md).

## Work tracking

Epics and tickets live in `docs/specs/` as committed markdown files. See [docs/work-tracking.md](docs/work-tracking.md) for format and lifecycle. ADRs live in `docs/decisions/`.

## Commits

A commit message is an imperative subject line, a blank line, then a 3-line haiku (5-7-5) as the whole body. Never credit Claude in a commit: no `Co-Authored-By: Claude` trailer and no other mention. The `.githooks/commit-msg` hook strips Claude trailers and rejects any other body shape; enable it once per clone with `git config core.hooksPath .githooks`.

Each commit adds only code it uses. Don't land a repository method, model, or helper in one commit for a later commit to call; ship it with its first caller. Split a commit only where each half stands on its own.

Review fixes amend the commit that introduced the issue (`git commit --fixup` + autosquash), not a new commit on top.

## Pull requests

Split work into PRs a reviewer can read in one sitting, and split by feature slice, not by layer. Each PR is a working increment: a screen it adds is reachable in the app, and everything it adds is used. A UI-only PR or a data-only PR that nothing calls yet doesn't count.

When one slice builds on another, stack the PRs: each branches from the one below and targets its branch, then retargets to `main` once that merges. Cross-cutting style changes (a new STYLE.md rule and the reformat it causes) go in their own PR at the bottom of the stack.
