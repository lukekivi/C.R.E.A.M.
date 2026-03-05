# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

Three-layer architecture with manual dependency injection (no Hilt/Koin — dependencies are provided to ViewModels via factories):

- **UI layer** (`ui/`): Jetpack Compose screens and state holders (ViewModels, remembered objects). Adaptive layouts via Compose.
- **Data layer**: Repositories containing business logic. One repository per data type.
- **Datasource layer**: Split into `remote-datasource` (network) and `local-datasource` (storage) modules. Repositories depend on datasources, not the other way around.

Data flows up: Datasource → Repository → ViewModel → Composable.
