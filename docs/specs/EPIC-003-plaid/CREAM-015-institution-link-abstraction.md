---
id: CREAM-015
title: Put the institution link launcher behind an abstraction we own
status: todo
created: 2026-11-14
depends-on: [CREAM-004]
---

`rememberInstitutionLinkLauncher` carries too much for the composable layer: it handles the Plaid SDK wiring (context, sessions, Plaid's activity-result contract) and the `LinkResult` → `InstitutionLinkResult` mapping. Move that behind a class we own, so composables only ask to link an institution and get back an `InstitutionLinkResult`.

## Out of scope

- Changing how link results are mapped (CREAM-003's rules stay).

## Done when

- No composable touches Plaid types, sessions, or the result mapping.
- The mapping stays unit-tested.
- Linking still works on a device (success, cancel, error).
