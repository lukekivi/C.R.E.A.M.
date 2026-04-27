# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

Three-layer architecture with manual dependency injection (no Hilt/Koin — dependencies are provided to ViewModels via factories):

- **UI layer** (`ui/`): Jetpack Compose screens and state holders (ViewModels, remembered objects). Adaptive layouts via Compose.
- **Data layer**: Repositories containing business logic. One repository per data type.
- **Datasource layer**: Split into `remote-datasource` (network) and `local-datasource` (storage) modules. Each module owns its interface, implementation, and a factory function returning the interface type. Consumers call the factory — never import impl classes directly. `:data` depends on these modules; `:app` only depends on `:data`.

Data flows up: Datasource → Repository → ViewModel → Composable.

## Code style

See [STYLE.md](STYLE.md) for code conventions (documentation, formatting, module boundaries). Follow it for every change.
