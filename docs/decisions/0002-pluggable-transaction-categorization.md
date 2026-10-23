# ADR 0002 — Transaction categorization is a pluggable, task-level datasource

- **Status:** Proposed
- **Date:** 2026-10-19
- **Affected components:** new `:inference-datasource` module, `:data` (new `CategorizationRepository`), `:local-datasource` (category columns), `:core` (category model)

## Context

We want to categorize transactions with an LLM, starting with on-device Gemini Nano (ML Kit GenAI Prompt API / AICore) on a Pixel. We expect to experiment, and may later swap to a hosted model (OpenAI or similar). The swap must not ripple into repositories, ViewModels, or UI.

Models differ in ways a thin abstraction can't hide:

- **Context size.** Gemini Nano's window is small; a hosted model can take hundreds of transactions at once. Batching strategy is model-specific.
- **Output control.** Structured/JSON output support, reliability, and the parsing needed to recover from malformed output all vary.
- **Prompting.** A prompt tuned for one model is rarely optimal for another.
- **Where it runs.** On-device needs no network or keys. A hosted model's API key cannot ship in the APK, so it must be proxied through a Firebase Function — the same constraint that put Plaid behind Functions.

An interface at the level of `generate(prompt: String): String` would leak all of the above into the repository: the repository would own prompts, batching, and parsing for every model.

## Decision

**Abstract at the task level, in a dedicated `:inference-datasource` module.**

- `:inference-datasource` follows the datasource-module convention: it owns the interface, the implementations, and a factory returning the interface type. Depends on `:core` only.
- The interface is shaped by the task, not the model:

  ```kotlin
  interface TransactionCategorizer {
      suspend fun categorize(
          transactions: List<Transaction>,
          categories: List<Category>,
      ): NetworkResult<Map<String, CategoryId>> // transactionId → categoryId
  }
  ```

  Each implementation owns its prompt, batching, output parsing, and validation (e.g. discarding answers that aren't in `categories`).
- **The category set is business logic** and belongs to `:data`. The categorizer receives it as input; it never decides what categories exist.
- First implementation: `OnDeviceTransactionCategorizer` (Gemini Nano). A later `RemoteTransactionCategorizer` would call a Firebase Function that holds the provider key.
- **One active implementation**, chosen in the factory. No runtime fallback chain — if the active categorizer fails, transactions stay uncategorized and can be retried.
- `CategorizationRepository` in `:data` decides *when* to categorize (e.g. after a sync, for uncategorized transactions only) and persists results to Room.
- **Results are persisted with provenance.** Each categorized transaction stores the category and which categorizer produced it. Plaid's own `category` field is kept untouched as raw data. Transactions are categorized once; switching models does not silently re-label history.
- Future inference tasks (summaries, budget insights, …) get their own task-level interfaces in the same module rather than sharing a generic one.

## Consequences

### Wins

- **Swapping models is a factory change.** Nothing above `:inference-datasource` knows which model runs.
- **Model quirks stay contained.** Prompt tuning and output repair happen inside one implementation and can be iterated freely.
- **Tests stay simple.** The repository is tested against a fake `TransactionCategorizer`; no model needed.
- **Inference cost is paid once per transaction**, not per screen load.

### Tradeoffs accepted

- **Some duplication across implementations.** Each one rewrites prompt construction and parsing. We accept that over a shared prompt layer that fits no model well.
- **Device support is narrow.** Gemini Nano runs only on recent Pixels and some flagships. Fine while the only user is the author.
- **No automatic fallback.** If on-device inference is unavailable, nothing is categorized until a different implementation is selected.
- **Privacy posture is implementation-dependent.** On-device keeps transaction data on the phone; a hosted implementation sends it to a third party. Acceptable for a single-user app; revisit before anyone else uses it.

### Reversibility

- **Adding a hosted implementation** is additive: a new class, a new Firebase Function, a factory change.
- **Adding a fallback chain** is additive: a composite `TransactionCategorizer` that delegates in order, selected in the factory.
- **Re-categorizing history** after a model swap is an explicit operation (clear categories by provenance, re-run), never an accident.

## Triggers to revisit

- Someone other than the author uses the app (privacy, device support, fallback).
- A second inference task appears whose needs suggest a shared lower-level abstraction after all.
- On-device quality is too poor to be useful and a hosted model becomes the default.

## Open questions

- Where the category set comes from: a hardcoded starter list, user-defined categories, or seeded from Plaid's taxonomy.
- Whether users can manually override a category (would add a `USER` provenance that the categorizer never overwrites).

