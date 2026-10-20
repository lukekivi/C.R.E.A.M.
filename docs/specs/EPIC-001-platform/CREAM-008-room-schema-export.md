---
id: CREAM-008
title: Configure Room schema export
status: todo
created: 2026-10-20
---

Every `:local-datasource` build warns: `Schema export directory was not provided to the annotation processor so Room cannot export the schema.` `CreamDatabase` is at `version = 1` with no migrations and no `fallbackToDestructiveMigration`, so the first schema bump will crash on open for any existing install. There is no schema history to write or test migrations against.

## Scope

- Apply the Room Gradle plugin (`androidx.room`) in `:local-datasource` and set `room { schemaDirectory("$projectDir/schemas") }`. Commit the generated schema JSON.
- Build is warning-free for Room.

## Out of scope

- Choosing how the first schema bump is handled. While the local data is a fully rebuildable cache (per ADR 0001, a null cursor triggers a full resync), destructive fallback is an option; real migrations are needed once it isn't. Decide in the change that bumps the version.

## Notes

- The alternative is `exportSchema = false` on `@Database`. That silences the warning but gives up the history; prefer the plugin.
- The plugin version must match the `room` version in `libs.versions.toml` (2.8.4 as of the AGP 9 build fix).
