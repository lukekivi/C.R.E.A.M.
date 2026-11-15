---
id: CREAM-016
title: Add a BaseViewModel for what every ViewModel repeats
status: todo
created: 2026-11-14
depends-on: [CREAM-004]
---

Every ViewModel repeats the same scaffolding: a `mutableUiState` / `uiState` pair, the `update { }` calls on it, and now (since CREAM-004) an `events` flow backed by a `Channel`. Move what's common into a `BaseViewModel` that the ViewModels extend, so each one only declares its own state, events, and behavior.

## Scope

- A `BaseViewModel` holding the state and events plumbing, plus anything else every ViewModel shares.
- Move the existing ViewModels onto it.
- Screens handle events with `EventsEffect`; make it pair naturally with whatever events API the base class exposes.
- Update STYLE.md and the `cream-viewmodel` skill to match.

## Out of scope

- Changing what any screen does.

## Done when

- No ViewModel declares its own state or events backing fields.
- Existing ViewModel tests pass unchanged, apart from construction.
