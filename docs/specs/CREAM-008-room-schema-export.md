---
id: CREAM-008
title: Configure Room schema export
status: todo
created: 2026-10-20
---

Every `:local-datasource` build warns: `Schema export directory was not provided to the annotation processor so Room cannot export the schema.` `CreamDatabase` is at `version = 2` with `fallbackToDestructiveMigration(true)`. Today every schema bump silently wipes the local cache. That's acceptable while the cache is fully rebuildable (per ADR 0001, a null cursor triggers a full resync), but there is no schema history to write or test real migrations against when that changes.

## Scope

- Apply the Room Gradle plugin (`androidx.room`) in `:local-datasource` and set `room { schemaDirectory("$projectDir/schemas") }`. Commit the generated schema JSON.
- Build is warning-free for Room.

## Out of scope

- Writing migrations or dropping `fallbackToDestructiveMigration`. Revisit once local data stops being a pure cache.

## Notes

- The alternative is `exportSchema = false` on `@Database`. That silences the warning but gives up the history; prefer the plugin.
- The plugin version must match the `room` version in `libs.versions.toml` (2.8.4 as of CREAM-002's build fix).
