# ADR 0003 — Composables own their data; UI transformers fill it

- **Status:** Accepted
- **Date:** 2026-11-14
- **Affected components:** `:app` screens (ViewModels, composables, UiState)
- **Supersedes:** the earlier reading of STYLE.md "Declarative UiState", under which ViewModels defined per-component display types and formatted their contents.

## Context

ViewModels defined the types their composables consumed. `InstitutionsViewModel` declared `AccountItem` (pre-rendered strings) and built it in a private `toAccountItem`, joining `"•••• 1234 · Depository"` and formatting the balance. The composable `AccountRow` just dropped those strings into `Text`s. Elsewhere composables read domain models directly: `InstitutionHeader` took `LinkedInstitution`; `ProfileSection` took `User?` and derived its initial and name itself.

Problems:

- **Ownership is inverted.** A component's input shape lived in the ViewModel file, so changing a row meant editing the ViewModel.
- **Inconsistent.** Some components got pre-formatted strings, others raw domain models, with formatting split between ViewModel and composable.
- **Shared domain models couple screens.** If two screens' composables both read a domain model, their data needs can't diverge without breaking each other.
- **Testing is tangled.** ViewModel tests asserted on formatted strings, mixing orchestration and presentation.
- **Not agent-friendly.** "Use your judgment" rules produce inconsistent output from coding agents.

## Decision

1. **The ViewModel owns the screen's master `UiState`**: which content, dialogs, and flags are present.
2. **Each composable that takes structured data owns a `<Name>Data` type**, declared below it in its own file `<Name>.kt`. Composables are named as nouns (`AccountItem`), and their data after them (`AccountItemData`). No exceptions; primitives and lambdas stay plain parameters.
3. **`*Data` holds display-ready values.** Composables don't format.
4. **No UI is built from domain models.** A domain model may ride in `*Data` or the `UiState` only as a callback payload, never read for display.
5. **Each screen that shows domain data has a `<Screen>UiTransformer`**, constructor-injected into the ViewModel, that performs every domain → UI conversion, including formatting (it takes `CurrencyFormatter`). ViewModel tests mock it; it has its own unit test. This mirrors the datasource transformer pattern.
6. **A list section contributing several lazy entries** is a `LazyListScope.<name>` extension taking `<Name>Data`, keeping per-child laziness.

Conventions are spelled out in STYLE.md ("Declarative UiState", "Composable data", "UI transformers") and the `cream-viewmodel` skill.

## Consequences

### Wins

- One deterministic rule set for agents and humans: every structured composable input is a `*Data`, every conversion is a transformer method.
- Screens can't couple through domain models; a domain refactor stops at the transformers.
- Formatting is unit-tested in isolation; ViewModel tests cover orchestration only.

### Tradeoffs accepted

- More types and files: a `*Data` per composable and a transformer per screen, even when the mapping is a passthrough.
- Payload fields duplicate data (e.g. `InstitutionItemData.itemId` alongside `institution`).
- The ViewModel depends on composable files' types. Acceptable: they're plain data classes in the same package.

## Triggers to revisit

- Passthrough `*Data` types dominate and the boilerplate outweighs the consistency.
- A composable is reused across modules, needing its `*Data` somewhere other than its file.

## Implementation references

- `app/.../screens/institutions/InstitutionsUiTransformer.kt`, `AccountItem.kt`, `InstitutionItem.kt`
- `app/.../screens/home/HomeUiTransformer.kt`, `ProfileSection.kt`
