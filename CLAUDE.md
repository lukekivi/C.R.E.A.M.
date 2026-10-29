# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

All agent-driven development goes through kivi-sdlc: split an epic with `/kivi-sdlc:conductor`, then take each ticket through `/kivi-sdlc:recon` → `/kivi-sdlc:plan` → `/kivi-sdlc:work`. Don't implement a ticket without a recon'd and planned spec.
